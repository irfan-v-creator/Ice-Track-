package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.CustomerRepository
import com.example.data.repository.HandoffPreparationResult
import com.example.data.repository.PaymentReminderRepository
import com.example.data.repository.PrepareDraftResult
import com.example.data.repository.SalesRepository
import com.example.data.repository.SubledgerRepository
import com.example.domain.engine.ComposedReminder
import com.example.domain.engine.PaymentReminderEngine
import com.example.domain.engine.PhoneValidationResult
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.model.Customer
import com.example.domain.model.CustomerPaymentReminder
import com.example.domain.model.PartyType
import com.example.domain.model.ReminderStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

enum class ReminderStatusFilter(val label: String) {
  ALL("All"),
  NOT_REMINDED("Not Reminded"),
  DRAFT("Draft"),
  WHATSAPP_OPENED("Opened"),
  USER_CONFIRMED_SENT("Sent"),
  FAILED("Failed")
}

data class CustomerReminderRowUi(
  val customer: Customer,
  val netOutstandingPaise: Long,
  val unpaidInvoiceCount: Int,
  val phoneValidation: PhoneValidationResult,
  val latestReminder: CustomerPaymentReminder?
) {
  val isEligibleForReminder: Boolean
    get() = netOutstandingPaise > 0L && phoneValidation.isValid
}

data class PaymentReminderDashboardUiState(
  val rows: List<CustomerReminderRowUi> = emptyList(),
  val totalEligibleCount: Int = 0,
  val totalOutstandingReceivablesPaise: Long = 0L,
  val searchQuery: String = "",
  val outstandingOnly: Boolean = true,
  val statusFilter: ReminderStatusFilter = ReminderStatusFilter.ALL
)

data class ReminderPreviewUiState(
  val customer: Customer,
  val reminder: CustomerPaymentReminder,
  val composed: ComposedReminder,
  val balanceChangeNotice: String? = null,
  val isLaunching: Boolean = false
)

data class LaunchWhatsAppCommand(
  val reminderId: Long,
  val whatsappPhoneDigits: String,
  val message: String,
  val targetPackage: String?
)

class PaymentReminderViewModel(
  private val customerRepository: CustomerRepository,
  private val salesRepository: SalesRepository,
  private val subledgerRepository: SubledgerRepository,
  private val reminderRepository: PaymentReminderRepository
) : ViewModel() {

  val searchQuery = MutableStateFlow("")
  val outstandingOnly = MutableStateFlow(true)
  val statusFilter = MutableStateFlow(ReminderStatusFilter.ALL)

  private val _previewState = MutableStateFlow<ReminderPreviewUiState?>(null)
  val previewState: StateFlow<ReminderPreviewUiState?> = _previewState.asStateFlow()

  private val _historyCustomer = MutableStateFlow<Customer?>(null)
  val historyCustomer: StateFlow<Customer?> = _historyCustomer.asStateFlow()

  @OptIn(ExperimentalCoroutinesApi::class)
  val customerHistory: StateFlow<List<CustomerPaymentReminder>> = _historyCustomer
    .flatMapLatest { customer ->
      if (customer == null) flowOf(emptyList())
      else reminderRepository.observeHistoryForCustomer(customer.id)
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val pendingConfirmation: StateFlow<CustomerPaymentReminder?> = reminderRepository.pendingConfirmationReminder
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  val customerShopNamesById: StateFlow<Map<Long, String>> = customerRepository.allCustomersIncludingArchived
    .map { customers -> customers.associate { it.id to it.shopName } }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private val _feedbackMessage = MutableStateFlow<String?>(null)
  val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

  private val _launchEvents = MutableSharedFlow<LaunchWhatsAppCommand>(extraBufferCapacity = 1)
  val launchEvents: SharedFlow<LaunchWhatsAppCommand> = _launchEvents.asSharedFlow()

  private val actionMutex = Mutex()
  private var lastPrepareTimeMs: Long = 0L
  private var lastLaunchTimeMs: Long = 0L

  private val rawCustomerInputs = combine(
    customerRepository.allCustomersIncludingArchived,
    subledgerRepository.allEntries,
    salesRepository.allSales,
    reminderRepository.latestReminderByCustomer
  ) { customers, entries, sales, latestMap ->
    val activeEntriesByCustomer = entries
      .filter { !it.isReversed && it.partyType == PartyType.CUSTOMER }
      .groupBy { it.partyId }
    val unpaidSalesCountByCustomer = sales
      .filter {
        it.invoiceStatus == com.example.domain.model.InvoiceStatus.ACTIVE &&
          it.paymentStatus != com.example.domain.model.PaymentStatus.PAID &&
          it.balanceDuePaise > 0L
      }
      .groupingBy { it.customerId }
      .eachCount()

    customers.map { customer ->
      val partyEntries = activeEntriesByCustomer[customer.id] ?: emptyList()
      val netBalance = SubledgerCalculationEngine.calculateCustomerBalance(partyEntries)
      val phoneValidation = PaymentReminderEngine.normalizeAndValidatePhone(customer.phoneNumber)
      CustomerReminderRowUi(
        customer = customer,
        netOutstandingPaise = netBalance,
        unpaidInvoiceCount = unpaidSalesCountByCustomer[customer.id] ?: 0,
        phoneValidation = phoneValidation,
        latestReminder = latestMap[customer.id]
      )
    }
  }

  val dashboardState: StateFlow<PaymentReminderDashboardUiState> = combine(
    rawCustomerInputs,
    searchQuery,
    outstandingOnly,
    statusFilter
  ) { allRows, query, onlyOutstanding, status ->
    val q = query.trim().lowercase()
    val filtered = allRows
      .filter { row ->
        val visibleArchive = row.customer.isActive || row.netOutstandingPaise > 0L || row.latestReminder != null
        val matchesOutstanding = !onlyOutstanding || row.netOutstandingPaise > 0L
        val matchesQuery = q.isEmpty() ||
          row.customer.shopName.lowercase().contains(q) ||
          row.customer.ownerName.lowercase().contains(q) ||
          row.customer.phoneNumber.lowercase().contains(q)
        val matchesStatus = when (status) {
          ReminderStatusFilter.ALL -> true
          ReminderStatusFilter.NOT_REMINDED -> row.latestReminder == null
          ReminderStatusFilter.DRAFT -> row.latestReminder?.status == ReminderStatus.DRAFT
          ReminderStatusFilter.WHATSAPP_OPENED -> row.latestReminder?.status == ReminderStatus.WHATSAPP_OPENED
          ReminderStatusFilter.USER_CONFIRMED_SENT -> row.latestReminder?.status == ReminderStatus.USER_CONFIRMED_SENT
          ReminderStatusFilter.FAILED -> row.latestReminder?.status == ReminderStatus.FAILED
        }
        visibleArchive && matchesOutstanding && matchesQuery && matchesStatus
      }
      .sortedWith(compareByDescending<CustomerReminderRowUi> { it.netOutstandingPaise }.thenBy { it.customer.shopName.lowercase() })

    val eligibleCount = allRows.count { it.isEligibleForReminder }
    val totalOutstanding = allRows
      .filter { it.netOutstandingPaise > 0L }
      .sumOf { it.netOutstandingPaise }

    PaymentReminderDashboardUiState(
      rows = filtered,
      totalEligibleCount = eligibleCount,
      totalOutstandingReceivablesPaise = totalOutstanding,
      searchQuery = query,
      outstandingOnly = onlyOutstanding,
      statusFilter = status
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PaymentReminderDashboardUiState())

  fun onSearchQueryChanged(query: String) {
    searchQuery.value = query
  }

  fun onToggleOutstandingOnly(onlyOutstanding: Boolean) {
    outstandingOnly.value = onlyOutstanding
  }

  fun onStatusFilterSelected(filter: ReminderStatusFilter) {
    statusFilter.value = filter
  }

  fun clearFeedbackMessage() {
    _feedbackMessage.value = null
  }

  fun openHistory(customer: Customer) {
    _historyCustomer.value = customer
  }

  fun closeHistory() {
    _historyCustomer.value = null
  }

  fun dismissPreview() {
    if (_previewState.value?.isLaunching == true) return
    _previewState.value = null
  }

  fun prepareReminderPreview(customer: Customer, nowMs: Long = System.currentTimeMillis()) {
    if (!actionMutex.tryLock()) return
    if (_isBusy.value || (nowMs - lastPrepareTimeMs) < 350L) {
      actionMutex.unlock()
      return
    }
    lastPrepareTimeMs = nowMs

    viewModelScope.launch {
      _isBusy.value = true
      _feedbackMessage.value = null
      try {
        when (val result = reminderRepository.createDraftReminder(customer.id)) {
          is PrepareDraftResult.Blocked -> {
            _feedbackMessage.value = result.reason
          }
          is PrepareDraftResult.Created -> {
            _previewState.value = ReminderPreviewUiState(
              customer = customer,
              reminder = result.reminder,
              composed = result.composed
            )
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _feedbackMessage.value = e.message ?: "Unable to prepare payment reminder."
      } finally {
        _isBusy.value = false
        actionMutex.unlock()
      }
    }
  }

  fun openWhatsAppFromPreview(targetPackage: String?, nowMs: Long = System.currentTimeMillis()) {
    val currentPreview = _previewState.value ?: return
    if (currentPreview.isLaunching || !actionMutex.tryLock()) return
    if ((nowMs - lastLaunchTimeMs) < 350L) {
      actionMutex.unlock()
      return
    }
    lastLaunchTimeMs = nowMs

    viewModelScope.launch {
      _previewState.value = currentPreview.copy(isLaunching = true, balanceChangeNotice = null)
      try {
        when (val prep = reminderRepository.verifyAndMarkWhatsAppOpened(currentPreview.reminder.id, targetPackage)) {
          is HandoffPreparationResult.Blocked -> {
            _previewState.value = null
            _feedbackMessage.value = prep.reason
          }
          is HandoffPreparationResult.BalanceChangedNeedsReview -> {
            val prevFormatted = SubledgerCalculationEngine.formatPaise(prep.previousOutstandingPaise)
            val newFormatted = SubledgerCalculationEngine.formatPaise(prep.updatedComposed.netOutstandingPaise)
            _previewState.value = ReminderPreviewUiState(
              customer = currentPreview.customer,
              reminder = prep.updatedReminder,
              composed = prep.updatedComposed,
              balanceChangeNotice = "Live ledger updated ($prevFormatted -> $newFormatted). Please review the refreshed reminder before opening WhatsApp.",
              isLaunching = false
            )
          }
          is HandoffPreparationResult.ReadyToLaunch -> {
            _previewState.value = null
            _launchEvents.emit(
              LaunchWhatsAppCommand(
                reminderId = prep.reminder.id,
                whatsappPhoneDigits = prep.whatsappPhoneDigits,
                message = prep.reminder.renderedMessage,
                targetPackage = targetPackage
              )
            )
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _previewState.value = currentPreview.copy(isLaunching = false)
        _feedbackMessage.value = e.message ?: "Failed to prepare WhatsApp handoff."
      } finally {
        actionMutex.unlock()
      }
    }
  }

  fun onWhatsAppLaunchFailed(reminderId: Long, reason: String) {
    viewModelScope.launch {
      try {
        reminderRepository.markReminderFailed(reminderId, reason)
        _feedbackMessage.value = reason
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _feedbackMessage.value = "WhatsApp failed to open: ${e.message}"
      }
    }
  }

  fun confirmPendingReminderSent(reminderId: Long) {
    viewModelScope.launch {
      try {
        reminderRepository.confirmReminderSent(reminderId)
        _feedbackMessage.value = "Reminder marked as confirmed sent."
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _feedbackMessage.value = "Could not confirm reminder: ${e.message}"
      }
    }
  }

  fun markPendingReminderNotSent(reminderId: Long) {
    viewModelScope.launch {
      try {
        reminderRepository.dismissPendingConfirmationAsNotSent(reminderId)
        _feedbackMessage.value = "Kept as WhatsApp Opened (not marked sent)."
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _feedbackMessage.value = "Error updating confirmation state: ${e.message}"
      }
    }
  }

  fun markPendingReminderFailed(reminderId: Long, reason: String = "Marked failed by user during confirmation") {
    viewModelScope.launch {
      try {
        reminderRepository.markReminderFailed(reminderId, reason)
        _feedbackMessage.value = "Reminder recorded as Failed."
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _feedbackMessage.value = "Error marking reminder failed: ${e.message}"
      }
    }
  }

  class Factory(
    private val customerRepository: CustomerRepository,
    private val salesRepository: SalesRepository,
    private val subledgerRepository: SubledgerRepository,
    private val reminderRepository: PaymentReminderRepository
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return PaymentReminderViewModel(
        customerRepository = customerRepository,
        salesRepository = salesRepository,
        subledgerRepository = subledgerRepository,
        reminderRepository = reminderRepository
      ) as T
    }
  }
}
