package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.IceTrackDatabase
import com.example.data.local.entity.CreditDebitNoteEntity
import com.example.data.local.entity.FinancialAuditLogEntity
import com.example.data.local.entity.PaymentAllocationEntity
import com.example.data.local.entity.PurchaseReturnEntity
import com.example.data.local.entity.PurchaseReturnItemEntity
import com.example.data.local.entity.RefundRecordEntity
import com.example.data.local.entity.SalesReturnEntity
import com.example.data.local.entity.SalesReturnItemEntity
import com.example.data.local.entity.SubledgerEntryEntity
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.model.AuditOperationType
import com.example.domain.model.CreditDebitNote
import com.example.domain.model.CreditDebitNoteType
import com.example.domain.model.FinancialAuditLog
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.NoteStatus
import com.example.domain.model.PartyType
import com.example.domain.model.PaymentAllocationType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentStatus
import com.example.domain.model.PurchaseReturn
import com.example.domain.model.PurchaseReturnItem
import com.example.domain.model.RefundExecutionStatus
import com.example.domain.model.RefundRecord
import com.example.domain.model.RefundSourceType
import com.example.domain.model.SalesReturn
import com.example.domain.model.SalesReturnItem
import com.example.domain.model.SubledgerEntryType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AuditRepository(private val db: IceTrackDatabase) {
  private val auditDao = db.financialAuditLogDao()

  val allAuditLogs: Flow<List<FinancialAuditLog>> = auditDao.observeAll().map { list ->
    list.map { it.toDomain() }
  }

  fun observeForEntity(entityType: String, entityId: Long): Flow<List<FinancialAuditLog>> {
    return auditDao.observeForEntity(entityType, entityId).map { list ->
      list.map { it.toDomain() }
    }
  }

  fun observeForParty(partyType: PartyType, partyId: Long): Flow<List<FinancialAuditLog>> {
    return auditDao.observeForParty(partyType, partyId).map { list ->
      list.map { it.toDomain() }
    }
  }

  suspend fun recordAuditLog(
    operationType: AuditOperationType,
    entityType: String,
    entityId: Long,
    relatedPartyType: PartyType? = null,
    relatedPartyId: Long? = null,
    reason: String,
    beforeJson: String = "",
    afterJson: String = "",
    operationReference: String = "",
    createdAt: Long = System.currentTimeMillis()
  ): Long {
    return auditDao.insert(
      FinancialAuditLogEntity(
        operationType = operationType,
        entityType = entityType,
        entityId = entityId,
        relatedPartyType = relatedPartyType,
        relatedPartyId = relatedPartyId,
        reason = reason.trim().ifBlank { operationType.displayName },
        beforeJson = beforeJson,
        afterJson = afterJson,
        operationReference = operationReference,
        createdAt = createdAt
      )
    )
  }
}

class CreditDebitNoteRepository(private val db: IceTrackDatabase) {
  private val noteDao = db.creditDebitNoteDao()
  private val subledgerDao = db.subledgerDao()
  private val allocationDao = db.paymentAllocationDao()
  private val salesDao = db.salesInvoiceDao()
  private val purchaseDao = db.purchaseInvoiceDao()
  private val refundDao = db.refundRecordDao()
  private val auditDao = db.financialAuditLogDao()

  val allNotes: Flow<List<CreditDebitNote>> = noteDao.observeAll().map { list ->
    list.map { it.toDomain() }
  }

  fun observeForParty(partyType: PartyType, partyId: Long): Flow<List<CreditDebitNote>> {
    return noteDao.observeForParty(partyType, partyId).map { list ->
      list.map { it.toDomain() }
    }
  }

  suspend fun generateNextNoteNumber(
    noteType: CreditDebitNoteType,
    nowMillis: Long = System.currentTimeMillis()
  ): String {
    val prefix = if (noteType == CreditDebitNoteType.CREDIT_NOTE) "CN" else "DN"
    val dateTag = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(nowMillis))
    var seq = noteDao.countByType(noteType) + 1
    var candidate = "$prefix-$dateTag-${seq.toString().padStart(4, '0')}"
    while (noteDao.existsByNoteNumber(candidate) > 0) {
      seq++
      candidate = "$prefix-$dateTag-${seq.toString().padStart(4, '0')}"
    }
    return candidate
  }

  /**
   * Issues a standalone or invoice/return-linked Credit Note or Debit Note.
   * - Customer CREDIT_NOTE or Supplier DEBIT_NOTE reduces the party's balance (and optionally settles unpaid invoices).
   * - Customer DEBIT_NOTE or Supplier CREDIT_NOTE increases party balance.
   */
  suspend fun createNote(
    noteType: CreditDebitNoteType,
    partyType: PartyType,
    partyId: Long,
    amountPaise: Long,
    issueDate: Long = System.currentTimeMillis(),
    linkedInvoiceId: Long? = null,
    linkedReturnId: Long? = null,
    reason: String,
    notes: String = "",
    applyToDues: Boolean = true,
    idempotencyKey: String? = null
  ): Long = db.withTransaction {
    if (!idempotencyKey.isNullOrBlank()) {
      val existing = noteDao.getByIdempotencyKey(idempotencyKey.trim())
      if (existing != null) return@withTransaction existing.id
    }

    val cleanReason = reason.trim()
    require(cleanReason.isNotBlank()) { "Reason is mandatory when issuing a ${noteType.displayName}" }
    require(amountPaise > 0L) { "Note amount must be greater than zero" }

    // Validate party exists
    if (partyType == PartyType.CUSTOMER) {
      db.customerDao().getCustomerSync(partyId) ?: throw IllegalArgumentException("Customer #$partyId not found")
      if (linkedInvoiceId != null) {
        val inv = salesDao.getById(linkedInvoiceId)
          ?: throw IllegalArgumentException("Linked sales invoice #$linkedInvoiceId not found")
        require(inv.customerId == partyId) { "Linked invoice belongs to a different customer" }
        require(inv.invoiceStatus == InvoiceStatus.ACTIVE) { "Cannot link note to a cancelled invoice" }
      }
    } else {
      db.supplierDao().getSupplierSync(partyId) ?: throw IllegalArgumentException("Supplier #$partyId not found")
      if (linkedInvoiceId != null) {
        val inv = purchaseDao.getById(linkedInvoiceId)
          ?: throw IllegalArgumentException("Linked purchase invoice #$linkedInvoiceId not found")
        require(inv.supplierId == partyId) { "Linked invoice belongs to a different supplier" }
        require(inv.invoiceStatus == InvoiceStatus.ACTIVE) { "Cannot link note to a cancelled invoice" }
      }
    }

    val noteNumber = generateNextNoteNumber(noteType, issueDate)
    val now = System.currentTimeMillis()
    val reducesPartyBalance = (partyType == PartyType.CUSTOMER && noteType == CreditDebitNoteType.CREDIT_NOTE) ||
      (partyType == PartyType.SUPPLIER && noteType == CreditDebitNoteType.DEBIT_NOTE)

    val (debit, credit) = when (partyType) {
      PartyType.CUSTOMER -> if (noteType == CreditDebitNoteType.CREDIT_NOTE) 0L to amountPaise else amountPaise to 0L
      PartyType.SUPPLIER -> if (noteType == CreditDebitNoteType.DEBIT_NOTE) amountPaise to 0L else 0L to amountPaise
    }

    val entryType = if (noteType == CreditDebitNoteType.CREDIT_NOTE) {
      SubledgerEntryType.CREDIT_NOTE
    } else {
      SubledgerEntryType.DEBIT_NOTE
    }

    val subledgerId = subledgerDao.insert(
      SubledgerEntryEntity(
        partyType = partyType,
        partyId = partyId,
        entryType = entryType,
        referenceType = noteType.name,
        referenceId = linkedInvoiceId ?: linkedReturnId ?: partyId,
        debitPaise = debit,
        creditPaise = credit,
        entryDate = issueDate,
        notes = "$noteNumber: $cleanReason${if (notes.isNotBlank()) " ($notes)" else ""}",
        operationRef = noteNumber
      )
    )

    var appliedPaise = 0L
    if (reducesPartyBalance && applyToDues) {
      appliedPaise = applyReductionToPartyInvoices(
        partyType = partyType,
        partyId = partyId,
        subledgerId = subledgerId,
        availablePaise = amountPaise,
        preferredInvoiceId = linkedInvoiceId,
        noteNumber = noteNumber,
        appliedAt = issueDate
      )
    }

    val initialStatus = when {
      !reducesPartyBalance -> NoteStatus.SETTLED
      appliedPaise >= amountPaise -> NoteStatus.SETTLED
      appliedPaise > 0L -> NoteStatus.APPLIED
      else -> NoteStatus.OPEN
    }

    val noteId = noteDao.insert(
      CreditDebitNoteEntity(
        noteNumber = noteNumber,
        noteType = noteType,
        partyType = partyType,
        partyId = partyId,
        linkedInvoiceId = linkedInvoiceId,
        linkedReturnId = linkedReturnId,
        subledgerEntryId = subledgerId,
        amountPaise = amountPaise,
        appliedAmountPaise = appliedPaise,
        status = initialStatus,
        issueDate = issueDate,
        reason = cleanReason,
        notes = notes.trim(),
        idempotencyKey = idempotencyKey?.trim()?.ifBlank { null },
        createdAt = now,
        updatedAt = now
      )
    )

    val auditOp = if (noteType == CreditDebitNoteType.CREDIT_NOTE) {
      AuditOperationType.CREDIT_NOTE
    } else {
      AuditOperationType.DEBIT_NOTE
    }
    auditDao.insert(
      FinancialAuditLogEntity(
        operationType = auditOp,
        entityType = "CREDIT_DEBIT_NOTE",
        entityId = noteId,
        relatedPartyType = partyType,
        relatedPartyId = partyId,
        reason = cleanReason,
        beforeJson = "",
        afterJson = JSONObject()
          .put("noteNumber", noteNumber)
          .put("amountPaise", amountPaise)
          .put("appliedPaise", appliedPaise)
          .put("status", initialStatus.name)
          .toString(),
        operationReference = noteNumber,
        createdAt = now
      )
    )

    noteId
  }

  /**
   * Applies remaining balance of an OPEN/APPLIED Credit Note (customer) or Debit Note (supplier)
   * toward unpaid invoices for that party.
   */
  suspend fun applyOpenNoteToInvoices(
    noteId: Long,
    targetInvoiceId: Long? = null,
    reason: String = "Applied note to unpaid dues"
  ): Long = db.withTransaction {
    val note = noteDao.getByIdSync(noteId) ?: throw IllegalArgumentException("Note #$noteId not found")
    require(note.status == NoteStatus.OPEN || note.status == NoteStatus.APPLIED) {
      "Note ${note.noteNumber} is ${note.status.displayName} and cannot be applied"
    }
    val remaining = (note.amountPaise - note.appliedAmountPaise).coerceAtLeast(0L)
    if (remaining <= 0L) return@withTransaction 0L

    val now = System.currentTimeMillis()
    val appliedNow = applyReductionToPartyInvoices(
      partyType = note.partyType,
      partyId = note.partyId,
      subledgerId = note.subledgerEntryId ?: note.id,
      availablePaise = remaining,
      preferredInvoiceId = targetInvoiceId ?: note.linkedInvoiceId,
      noteNumber = note.noteNumber,
      appliedAt = now
    )
    if (appliedNow <= 0L) return@withTransaction 0L

    val totalApplied = note.appliedAmountPaise + appliedNow
    val newStatus = if (totalApplied >= note.amountPaise) NoteStatus.SETTLED else NoteStatus.APPLIED
    noteDao.update(
      note.copy(
        appliedAmountPaise = totalApplied,
        status = newStatus,
        updatedAt = now
      )
    )

    auditDao.insert(
      FinancialAuditLogEntity(
        operationType = if (note.noteType == CreditDebitNoteType.CREDIT_NOTE) {
          AuditOperationType.CREDIT_NOTE
        } else {
          AuditOperationType.DEBIT_NOTE
        },
        entityType = "CREDIT_DEBIT_NOTE",
        entityId = note.id,
        relatedPartyType = note.partyType,
        relatedPartyId = note.partyId,
        reason = reason.trim().ifBlank { "Applied note to unpaid invoices" },
        beforeJson = JSONObject().put("appliedAmountPaise", note.appliedAmountPaise).put("status", note.status.name).toString(),
        afterJson = JSONObject().put("appliedAmountPaise", totalApplied).put("status", newStatus.name).toString(),
        operationReference = note.noteNumber,
        createdAt = now
      )
    )
    appliedNow
  }

  /**
   * Converts remaining unapplied Credit/Debit Note value into a recorded cash/bank refund liability.
   */
  suspend fun settleOpenNoteAsRefundRecord(
    noteId: Long,
    paymentMethod: PaymentMethod,
    reason: String
  ): Long = db.withTransaction {
    val cleanReason = reason.trim()
    require(cleanReason.isNotBlank()) { "Settlement reason is mandatory" }
    val note = noteDao.getByIdSync(noteId) ?: throw IllegalArgumentException("Note #$noteId not found")
    require(note.status == NoteStatus.OPEN || note.status == NoteStatus.APPLIED) {
      "Only open notes can be settled as refund"
    }
    val remaining = (note.amountPaise - note.appliedAmountPaise).coerceAtLeast(0L)
    require(remaining > 0L) { "Note ${note.noteNumber} has no remaining balance" }

    val now = System.currentTimeMillis()
    val refundId = refundDao.insert(
      RefundRecordEntity(
        partyType = note.partyType,
        partyId = note.partyId,
        sourceType = RefundSourceType.CREDIT_DEBIT_NOTE,
        sourceId = note.id,
        amountPaise = remaining,
        paymentMethod = paymentMethod,
        executionStatus = RefundExecutionStatus.RECORDED_PENDING_PAYOUT,
        reason = cleanReason,
        referenceNumber = note.noteNumber,
        subledgerEntryId = note.subledgerEntryId,
        recordedAt = now
      )
    )

    noteDao.update(
      note.copy(
        appliedAmountPaise = note.amountPaise,
        status = NoteStatus.SETTLED,
        updatedAt = now
      )
    )

    auditDao.insert(
      FinancialAuditLogEntity(
        operationType = AuditOperationType.REFUND_PAYOUT,
        entityType = "CREDIT_DEBIT_NOTE",
        entityId = note.id,
        relatedPartyType = note.partyType,
        relatedPartyId = note.partyId,
        reason = cleanReason,
        beforeJson = JSONObject().put("remainingPaise", remaining).toString(),
        afterJson = JSONObject().put("refundRecordId", refundId).put("status", NoteStatus.SETTLED.name).toString(),
        operationReference = note.noteNumber,
        createdAt = now
      )
    )

    refundId
  }

  suspend fun cancelUnusedNote(noteId: Long, reason: String) = db.withTransaction {
    val cleanReason = reason.trim()
    require(cleanReason.isNotBlank()) { "Cancellation reason is mandatory" }
    val note = noteDao.getByIdSync(noteId) ?: throw IllegalArgumentException("Note #$noteId not found")
    require(note.status == NoteStatus.OPEN && note.appliedAmountPaise == 0L) {
      "Cannot cancel note ${note.noteNumber} once applied or settled"
    }
    val now = System.currentTimeMillis()
    note.subledgerEntryId?.let { subledgerDao.markReversed(it, now, cleanReason) }
    noteDao.update(note.copy(status = NoteStatus.CANCELLED, updatedAt = now))
    auditDao.insert(
      FinancialAuditLogEntity(
        operationType = if (note.noteType == CreditDebitNoteType.CREDIT_NOTE) {
          AuditOperationType.CREDIT_NOTE
        } else {
          AuditOperationType.DEBIT_NOTE
        },
        entityType = "CREDIT_DEBIT_NOTE",
        entityId = note.id,
        relatedPartyType = note.partyType,
        relatedPartyId = note.partyId,
        reason = cleanReason,
        beforeJson = JSONObject().put("status", note.status.name).toString(),
        afterJson = JSONObject().put("status", NoteStatus.CANCELLED.name).toString(),
        operationReference = note.noteNumber,
        createdAt = now
      )
    )
  }

  private suspend fun applyReductionToPartyInvoices(
    partyType: PartyType,
    partyId: Long,
    subledgerId: Long,
    availablePaise: Long,
    preferredInvoiceId: Long?,
    noteNumber: String,
    appliedAt: Long
  ): Long {
    var remaining = availablePaise
    var appliedTotal = 0L

    if (partyType == PartyType.CUSTOMER) {
      val unpaid = salesDao.getUnpaidForCustomer(partyId).sortedBy { if (it.id == preferredInvoiceId) 0 else 1 }
      for (inv in unpaid) {
        if (remaining <= 0L) break
        val due = (inv.totalAmountPaise - inv.paidAmountPaise).coerceAtLeast(0L)
        if (due <= 0L) continue
        val take = kotlin.math.min(remaining, due)
        allocationDao.insert(
          PaymentAllocationEntity(
            paymentId = subledgerId,
            partyType = PartyType.CUSTOMER,
            partyId = partyId,
            allocationType = PaymentAllocationType.INVOICE,
            invoiceId = inv.id,
            allocatedAmountPaise = take,
            allocatedAt = appliedAt,
            notes = "$noteNumber applied to Sale #${inv.invoiceNumber}"
          )
        )
        val newPaid = inv.paidAmountPaise + take
        val newStatus = if (newPaid >= inv.totalAmountPaise) PaymentStatus.PAID else PaymentStatus.PARTIALLY_PAID
        salesDao.update(inv.copy(paidAmountPaise = newPaid, paymentStatus = newStatus, updatedAt = appliedAt))
        remaining -= take
        appliedTotal += take
      }
    } else {
      val unpaid = purchaseDao.getUnpaidForSupplier(partyId).sortedBy { if (it.id == preferredInvoiceId) 0 else 1 }
      for (inv in unpaid) {
        if (remaining <= 0L) break
        val due = (inv.totalAmountPaise - inv.paidAmountPaise).coerceAtLeast(0L)
        if (due <= 0L) continue
        val take = kotlin.math.min(remaining, due)
        allocationDao.insert(
          PaymentAllocationEntity(
            paymentId = subledgerId,
            partyType = PartyType.SUPPLIER,
            partyId = partyId,
            allocationType = PaymentAllocationType.INVOICE,
            invoiceId = inv.id,
            allocatedAmountPaise = take,
            allocatedAt = appliedAt,
            notes = "$noteNumber applied to Purchase #${inv.invoiceNumber}"
          )
        )
        val newPaid = inv.paidAmountPaise + take
        val newStatus = if (newPaid >= inv.totalAmountPaise) PaymentStatus.PAID else PaymentStatus.PARTIALLY_PAID
        purchaseDao.update(inv.copy(paidAmountPaise = newPaid, paymentStatus = newStatus, updatedAt = appliedAt))
        remaining -= take
        appliedTotal += take
      }
    }
    return appliedTotal
  }
}

class RefundRepository(private val db: IceTrackDatabase) {
  private val refundDao = db.refundRecordDao()
  private val subledgerDao = db.subledgerDao()
  private val auditDao = db.financialAuditLogDao()

  val allRefunds: Flow<List<RefundRecord>> = refundDao.observeAll().map { list ->
    list.map { it.toDomain() }
  }

  val pendingRefunds: Flow<List<RefundRecord>> =
    refundDao.observeByStatus(RefundExecutionStatus.RECORDED_PENDING_PAYOUT).map { list ->
      list.map { it.toDomain() }
    }

  /**
   * Marks a recorded refund liability as physically paid out (Customer refund paid) or
   * physically received back (Supplier refund collected) and posts the settling subledger entry.
   */
  suspend fun executeRefundPayout(
    refundRecordId: Long,
    paymentMethod: PaymentMethod,
    referenceNumber: String,
    reason: String
  ) = db.withTransaction {
    val cleanReason = reason.trim().ifBlank { "Refund payout executed" }
    val record = refundDao.getByIdSync(refundRecordId)
      ?: throw IllegalArgumentException("Refund record #$refundRecordId not found")
    require(record.executionStatus == RefundExecutionStatus.RECORDED_PENDING_PAYOUT) {
      "Refund #$refundRecordId has already been executed"
    }

    val now = System.currentTimeMillis()
    val (debit, credit) = if (record.partyType == PartyType.CUSTOMER) {
      // Cash paid back to customer reduces our refund liability toward customer (debit AR)
      record.amountPaise to 0L
    } else {
      // Cash received back from supplier reduces supplier's refund balance owed to us (credit AP)
      0L to record.amountPaise
    }

    val settlementEntryId = subledgerDao.insert(
      SubledgerEntryEntity(
        partyType = record.partyType,
        partyId = record.partyId,
        entryType = SubledgerEntryType.ADJUSTMENT,
        referenceType = "REFUND_PAYOUT",
        referenceId = record.id,
        debitPaise = debit,
        creditPaise = credit,
        entryDate = now,
        notes = "Refund ${if (record.partyType == PartyType.CUSTOMER) "paid out" else "collected"} (${paymentMethod.displayName})${if (referenceNumber.isNotBlank()) " Ref:$referenceNumber" else ""}: $cleanReason",
        operationRef = referenceNumber.ifBlank { "REFUND#${record.id}" }
      )
    )

    refundDao.update(
      record.copy(
        paymentMethod = paymentMethod,
        executionStatus = RefundExecutionStatus.PAYOUT_EXECUTED,
        referenceNumber = referenceNumber.trim(),
        subledgerEntryId = settlementEntryId,
        executedAt = now
      )
    )

    auditDao.insert(
      FinancialAuditLogEntity(
        operationType = AuditOperationType.REFUND_PAYOUT,
        entityType = "REFUND_RECORD",
        entityId = record.id,
        relatedPartyType = record.partyType,
        relatedPartyId = record.partyId,
        reason = cleanReason,
        beforeJson = JSONObject().put("executionStatus", RefundExecutionStatus.RECORDED_PENDING_PAYOUT.name).toString(),
        afterJson = JSONObject()
          .put("executionStatus", RefundExecutionStatus.PAYOUT_EXECUTED.name)
          .put("paymentMethod", paymentMethod.name)
          .put("amountPaise", record.amountPaise)
          .toString(),
        operationReference = referenceNumber.ifBlank { "REFUND#${record.id}" },
        createdAt = now
      )
    )
  }
}

// Phase 3 Entity <-> Domain Mappers
fun SalesReturnEntity.toDomain() = SalesReturn(
  id = id,
  returnNumber = returnNumber,
  salesInvoiceId = salesInvoiceId,
  customerId = customerId,
  returnDate = returnDate,
  totalReturnAmountPaise = totalReturnAmountPaise,
  settlementType = settlementType,
  creditNoteId = creditNoteId,
  refundRecordId = refundRecordId,
  subledgerEntryId = subledgerEntryId,
  reason = reason,
  notes = notes,
  idempotencyKey = idempotencyKey,
  createdAt = createdAt
)

fun SalesReturnItemEntity.toDomain() = SalesReturnItem(
  id = id,
  salesReturnId = salesReturnId,
  originalSalesInvoiceItemId = originalSalesInvoiceItemId,
  productId = productId,
  batchId = batchId,
  productNameSnapshot = productNameSnapshot,
  quantityPieces = quantityPieces,
  unitPricePaise = unitPricePaise,
  lineTotalPaise = lineTotalPaise,
  condition = condition,
  restoredToSaleableStock = restoredToSaleableStock
)

fun PurchaseReturnEntity.toDomain() = PurchaseReturn(
  id = id,
  returnNumber = returnNumber,
  purchaseInvoiceId = purchaseInvoiceId,
  supplierId = supplierId,
  returnDate = returnDate,
  totalReturnAmountPaise = totalReturnAmountPaise,
  settlementType = settlementType,
  debitNoteId = debitNoteId,
  refundRecordId = refundRecordId,
  subledgerEntryId = subledgerEntryId,
  reason = reason,
  notes = notes,
  idempotencyKey = idempotencyKey,
  createdAt = createdAt
)

fun PurchaseReturnItemEntity.toDomain() = PurchaseReturnItem(
  id = id,
  purchaseReturnId = purchaseReturnId,
  originalPurchaseInvoiceItemId = originalPurchaseInvoiceItemId,
  productId = productId,
  batchId = batchId,
  productNameSnapshot = productNameSnapshot,
  batchNumberSnapshot = batchNumberSnapshot,
  quantityPieces = quantityPieces,
  costPerPiecePaise = costPerPiecePaise,
  lineTotalPaise = lineTotalPaise,
  condition = condition
)

fun CreditDebitNoteEntity.toDomain() = CreditDebitNote(
  id = id,
  noteNumber = noteNumber,
  noteType = noteType,
  partyType = partyType,
  partyId = partyId,
  linkedInvoiceId = linkedInvoiceId,
  linkedReturnId = linkedReturnId,
  subledgerEntryId = subledgerEntryId,
  amountPaise = amountPaise,
  appliedAmountPaise = appliedAmountPaise,
  status = status,
  issueDate = issueDate,
  reason = reason,
  notes = notes,
  idempotencyKey = idempotencyKey,
  createdAt = createdAt,
  updatedAt = updatedAt
)

fun RefundRecordEntity.toDomain() = RefundRecord(
  id = id,
  partyType = partyType,
  partyId = partyId,
  sourceType = sourceType,
  sourceId = sourceId,
  amountPaise = amountPaise,
  paymentMethod = paymentMethod,
  executionStatus = executionStatus,
  reason = reason,
  notes = notes,
  referenceNumber = referenceNumber,
  subledgerEntryId = subledgerEntryId,
  recordedAt = recordedAt,
  executedAt = executedAt,
  idempotencyKey = idempotencyKey
)

fun FinancialAuditLogEntity.toDomain() = FinancialAuditLog(
  id = id,
  operationType = operationType,
  entityType = entityType,
  entityId = entityId,
  relatedPartyType = relatedPartyType,
  relatedPartyId = relatedPartyId,
  actorLabel = actorLabel,
  reason = reason,
  beforeJson = beforeJson,
  afterJson = afterJson,
  operationReference = operationReference,
  createdAt = createdAt
)
