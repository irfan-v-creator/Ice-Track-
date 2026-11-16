package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.IceTrackDatabase
import com.example.data.local.entity.CustomerPaymentReminderEntity
import com.example.data.local.entity.CustomerPaymentReminderInvoiceEntity
import com.example.domain.engine.ComposedReminder
import com.example.domain.engine.PaymentReminderEngine
import com.example.domain.engine.ReminderCompositionResult
import com.example.domain.model.CustomerPaymentReminder
import com.example.domain.model.CustomerPaymentReminderInvoice
import com.example.domain.model.PartyType
import com.example.domain.model.ReminderStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed class PrepareDraftResult {
  data class Created(
    val reminder: CustomerPaymentReminder,
    val composed: ComposedReminder
  ) : PrepareDraftResult()

  data class Blocked(
    val reason: String,
    val netOutstandingPaise: Long
  ) : PrepareDraftResult()
}

sealed class HandoffPreparationResult {
  data class ReadyToLaunch(
    val reminder: CustomerPaymentReminder,
    val whatsappPhoneDigits: String
  ) : HandoffPreparationResult()

  data class BalanceChangedNeedsReview(
    val updatedReminder: CustomerPaymentReminder,
    val updatedComposed: ComposedReminder,
    val previousOutstandingPaise: Long
  ) : HandoffPreparationResult()

  data class Blocked(val reason: String) : HandoffPreparationResult()
}

class PaymentReminderRepository(private val db: IceTrackDatabase) {
  private val reminderDao = db.customerPaymentReminderDao()
  private val customerDao = db.customerDao()
  private val subledgerDao = db.subledgerDao()
  private val salesDao = db.salesInvoiceDao()
  private val appSettingDao = db.appSettingDao()

  val allReminders: Flow<List<CustomerPaymentReminder>> = reminderDao.observeAll().map { list ->
    list.map { it.toDomain() }
  }

  val latestReminderByCustomer: Flow<Map<Long, CustomerPaymentReminder>> = allReminders.map { list ->
    val result = LinkedHashMap<Long, CustomerPaymentReminder>()
    for (reminder in list) {
      if (!result.containsKey(reminder.customerId)) {
        result[reminder.customerId] = reminder
      }
    }
    result
  }

  val pendingConfirmationReminder: Flow<CustomerPaymentReminder?> =
    reminderDao.observePendingConfirmation().map { it?.toDomain() }

  fun observeHistoryForCustomer(customerId: Long): Flow<List<CustomerPaymentReminder>> {
    return reminderDao.observeHistoryForCustomer(customerId).map { list ->
      list.map { it.toDomain() }
    }
  }

  suspend fun getPendingConfirmationReminderSync(): CustomerPaymentReminder? {
    return reminderDao.getPendingConfirmationSync()?.toDomain()
  }

  suspend fun getInvoicesForReminderSync(reminderId: Long): List<CustomerPaymentReminderInvoice> {
    return reminderDao.getInvoicesForReminderSync(reminderId).map { it.toDomain() }
  }

  private suspend fun computeLiveComposition(customerId: Long): ReminderCompositionResult {
    val customer = customerDao.getCustomerSync(customerId)?.toDomain()
      ?: throw IllegalArgumentException("Customer #$customerId not found")
    val entries = subledgerDao.getEntriesForPartySync(PartyType.CUSTOMER, customerId).map { it.toDomain() }
    val unpaidInvoices = salesDao.getUnpaidForCustomer(customerId).map { it.toDomain() }
    val businessName = appSettingDao.getSync("business_name")?.value ?: "IceTrack Wholesale"
    return PaymentReminderEngine.composeReminder(
      customer = customer,
      businessName = businessName,
      subledgerEntries = entries,
      customerSalesInvoices = unpaidInvoices
    )
  }

  /**
   * Freshly fetches live SSOT ledger balance and unpaid invoices, validates eligibility and phone,
   * and persists a DRAFT audit snapshot in a single database transaction.
   */
  suspend fun createDraftReminder(customerId: Long): PrepareDraftResult = db.withTransaction {
    when (val composition = computeLiveComposition(customerId)) {
      is ReminderCompositionResult.Blocked -> {
        PrepareDraftResult.Blocked(
          reason = composition.reason,
          netOutstandingPaise = composition.netOutstandingPaise
        )
      }
      is ReminderCompositionResult.Ready -> {
        val composed = composition.reminder
        val now = System.currentTimeMillis()
        val draftEntity = CustomerPaymentReminderEntity(
          customerId = composed.customerId,
          targetPhoneE164 = composed.targetPhoneE164,
          dueKind = composed.dueKind,
          status = ReminderStatus.DRAFT,
          snapshotPendingInvoiceCount = composed.pendingInvoices.size,
          snapshotTotalInvoicePaise = composed.totalInvoiceAmountPaise,
          snapshotTotalInvoicePendingPaise = composed.totalInvoicePendingPaise,
          snapshotNetOutstandingPaise = composed.netOutstandingPaise,
          renderedMessage = composed.renderedMessage,
          awaitingUserConfirmation = false,
          createdAt = now,
          updatedAt = now
        )
        val reminderId = reminderDao.insertReminder(draftEntity)
        if (composed.pendingInvoices.isNotEmpty()) {
          reminderDao.insertReminderInvoices(
            composed.pendingInvoices.map { inv ->
              CustomerPaymentReminderInvoiceEntity(
                reminderId = reminderId,
                salesInvoiceId = inv.salesInvoiceId,
                invoiceDateSnapshot = inv.invoiceDate,
                totalAmountPaiseSnapshot = inv.totalAmountPaise,
                pendingAmountPaiseSnapshot = inv.pendingAmountPaise
              )
            }
          )
        }
        val persisted = draftEntity.copy(id = reminderId).toDomain()
        PrepareDraftResult.Created(reminder = persisted, composed = composed)
      }
    }
  }

  /**
   * Re-verifies live SSOT balance immediately before opening WhatsApp.
   * - If balance became <= 0 or phone invalid: marks draft FAILED and blocks launch.
   * - If live balance or pending invoice composition changed since preview: refreshes draft snapshot and asks user to review.
   * - If live balance matches preview: transitions DRAFT -> WHATSAPP_OPENED with awaitingUserConfirmation = true.
   */
  suspend fun verifyAndMarkWhatsAppOpened(
    draftReminderId: Long,
    whatsappPackageUsed: String?
  ): HandoffPreparationResult = db.withTransaction {
    val existing = reminderDao.getByIdSync(draftReminderId)
      ?: throw IllegalArgumentException("Reminder #$draftReminderId not found")
    check(existing.status == ReminderStatus.DRAFT) {
      "Reminder #${existing.id} cannot open WhatsApp from status ${existing.status}"
    }

    val now = System.currentTimeMillis()
    when (val live = computeLiveComposition(existing.customerId)) {
      is ReminderCompositionResult.Blocked -> {
        reminderDao.updateReminder(
          existing.copy(
            status = ReminderStatus.FAILED,
            failureReason = live.reason,
            awaitingUserConfirmation = false,
            updatedAt = now
          )
        )
        HandoffPreparationResult.Blocked(live.reason)
      }
      is ReminderCompositionResult.Ready -> {
        val composed = live.reminder
        val contentChanged = composed.netOutstandingPaise != existing.snapshotNetOutstandingPaise ||
          composed.totalInvoicePendingPaise != existing.snapshotTotalInvoicePendingPaise ||
          composed.pendingInvoices.size != existing.snapshotPendingInvoiceCount ||
          composed.targetPhoneE164 != existing.targetPhoneE164 ||
          composed.renderedMessage != existing.renderedMessage

        if (contentChanged) {
          val updatedDraft = CustomerPaymentReminderEntity(
            customerId = composed.customerId,
            targetPhoneE164 = composed.targetPhoneE164,
            dueKind = composed.dueKind,
            status = ReminderStatus.DRAFT,
            snapshotPendingInvoiceCount = composed.pendingInvoices.size,
            snapshotTotalInvoicePaise = composed.totalInvoiceAmountPaise,
            snapshotTotalInvoicePendingPaise = composed.totalInvoicePendingPaise,
            snapshotNetOutstandingPaise = composed.netOutstandingPaise,
            renderedMessage = composed.renderedMessage,
            awaitingUserConfirmation = false,
            createdAt = now,
            updatedAt = now
          )
          // Mark superseded draft as failed/cancelled audit trail, then insert refreshed live draft
          reminderDao.updateReminder(
            existing.copy(
              status = ReminderStatus.FAILED,
              failureReason = "Superseded by live ledger balance update before WhatsApp handoff",
              awaitingUserConfirmation = false,
              updatedAt = now
            )
          )
          val newId = reminderDao.insertReminder(updatedDraft)
          if (composed.pendingInvoices.isNotEmpty()) {
            reminderDao.insertReminderInvoices(
              composed.pendingInvoices.map { inv ->
                CustomerPaymentReminderInvoiceEntity(
                  reminderId = newId,
                  salesInvoiceId = inv.salesInvoiceId,
                  invoiceDateSnapshot = inv.invoiceDate,
                  totalAmountPaiseSnapshot = inv.totalAmountPaise,
                  pendingAmountPaiseSnapshot = inv.pendingAmountPaise
                )
              }
            )
          }
          return@withTransaction HandoffPreparationResult.BalanceChangedNeedsReview(
            updatedReminder = updatedDraft.copy(id = newId).toDomain(),
            updatedComposed = composed,
            previousOutstandingPaise = existing.snapshotNetOutstandingPaise
          )
        }

        val opened = existing.copy(
          status = ReminderStatus.WHATSAPP_OPENED,
          whatsappPackageUsed = whatsappPackageUsed,
          awaitingUserConfirmation = true,
          whatsappOpenedAt = now,
          updatedAt = now
        )
        reminderDao.updateReminder(opened)
        HandoffPreparationResult.ReadyToLaunch(
          reminder = opened.toDomain(),
          whatsappPhoneDigits = composed.whatsappPhoneDigits
        )
      }
    }
  }

  /**
   * Marks reminder as USER_CONFIRMED_SENT strictly upon explicit user confirmation after returning from WhatsApp.
   */
  suspend fun confirmReminderSent(reminderId: Long) = db.withTransaction {
    val existing = reminderDao.getByIdSync(reminderId)
      ?: throw IllegalArgumentException("Reminder #$reminderId not found")
    check(existing.status == ReminderStatus.WHATSAPP_OPENED) {
      "Only WHATSAPP_OPENED reminders can be confirmed as sent (current: ${existing.status})"
    }
    val now = System.currentTimeMillis()
    reminderDao.updateReminder(
      existing.copy(
        status = ReminderStatus.USER_CONFIRMED_SENT,
        awaitingUserConfirmation = false,
        confirmedSentAt = now,
        updatedAt = now
      )
    )
  }

  /**
   * Records that the user returned without sending (or cancelled confirmation).
   * Never promotes status to USER_CONFIRMED_SENT; preserves WHATSAPP_OPENED while dismissing the prompt.
   */
  suspend fun dismissPendingConfirmationAsNotSent(reminderId: Long) = db.withTransaction {
    val existing = reminderDao.getByIdSync(reminderId) ?: return@withTransaction
    if (existing.status == ReminderStatus.WHATSAPP_OPENED) {
      reminderDao.updateReminder(
        existing.copy(
          awaitingUserConfirmation = false,
          confirmedSentAt = null,
          updatedAt = System.currentTimeMillis()
        )
      )
    }
  }

  /**
   * Marks a DRAFT or WHATSAPP_OPENED reminder as FAILED. Confirmed sent reminders remain immutable.
   */
  suspend fun markReminderFailed(reminderId: Long, reason: String) = db.withTransaction {
    val existing = reminderDao.getByIdSync(reminderId) ?: return@withTransaction
    if (existing.status == ReminderStatus.USER_CONFIRMED_SENT) {
      return@withTransaction
    }
    reminderDao.updateReminder(
      existing.copy(
        status = ReminderStatus.FAILED,
        failureReason = reason.trim().ifEmpty { "Failed to send reminder via WhatsApp" },
        awaitingUserConfirmation = false,
        updatedAt = System.currentTimeMillis()
      )
    )
  }
}

fun CustomerPaymentReminderEntity.toDomain() = CustomerPaymentReminder(
  id = id,
  customerId = customerId,
  targetPhoneE164 = targetPhoneE164,
  dueKind = dueKind,
  status = status,
  snapshotPendingInvoiceCount = snapshotPendingInvoiceCount,
  snapshotTotalInvoicePaise = snapshotTotalInvoicePaise,
  snapshotTotalInvoicePendingPaise = snapshotTotalInvoicePendingPaise,
  snapshotNetOutstandingPaise = snapshotNetOutstandingPaise,
  renderedMessage = renderedMessage,
  whatsappPackageUsed = whatsappPackageUsed,
  failureReason = failureReason,
  awaitingUserConfirmation = awaitingUserConfirmation,
  createdAt = createdAt,
  whatsappOpenedAt = whatsappOpenedAt,
  confirmedSentAt = confirmedSentAt,
  updatedAt = updatedAt
)

fun CustomerPaymentReminderInvoiceEntity.toDomain() = CustomerPaymentReminderInvoice(
  id = id,
  reminderId = reminderId,
  salesInvoiceId = salesInvoiceId,
  invoiceDateSnapshot = invoiceDateSnapshot,
  totalAmountPaiseSnapshot = totalAmountPaiseSnapshot,
  pendingAmountPaiseSnapshot = pendingAmountPaiseSnapshot
)
