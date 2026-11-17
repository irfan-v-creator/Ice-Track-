package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AuditRepository
import com.example.data.repository.CreditDebitNoteRepository
import com.example.data.repository.CustomerRepository
import com.example.data.repository.PurchaseRepository
import com.example.data.repository.RefundRepository
import com.example.data.repository.SalesRepository
import com.example.data.repository.SubledgerRepository
import com.example.data.repository.SupplierRepository
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.model.AdvanceReversalStrategy
import com.example.domain.model.AuditOperationType
import com.example.domain.model.CreditDebitNote
import com.example.domain.model.CreditDebitNoteType
import com.example.domain.model.Customer
import com.example.domain.model.FinancialAuditLog
import com.example.domain.model.PartyType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PurchaseInvoice
import com.example.domain.model.RefundRecord
import com.example.domain.model.SalesInvoice
import com.example.domain.model.SubledgerEntry
import com.example.domain.model.Supplier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FinanceLifecycleUiState(
  val subledgerEntries: List<SubledgerEntry> = emptyList(),
  val notes: List<CreditDebitNote> = emptyList(),
  val refunds: List<RefundRecord> = emptyList(),
  val auditLogs: List<FinancialAuditLog> = emptyList(),
  val customers: List<Customer> = emptyList(),
  val suppliers: List<Supplier> = emptyList(),
  val salesInvoices: List<SalesInvoice> = emptyList(),
  val purchaseInvoices: List<PurchaseInvoice> = emptyList(),
  val isSubmitting: Boolean = false,
  val statusMessage: String? = null
)

class FinanceLifecycleViewModel(
  private val subledgerRepo: SubledgerRepository,
  private val noteRepo: CreditDebitNoteRepository,
  private val refundRepo: RefundRepository,
  private val auditRepo: AuditRepository,
  private val customerRepo: CustomerRepository,
  private val supplierRepo: SupplierRepository,
  private val salesRepo: SalesRepository,
  private val purchaseRepo: PurchaseRepository
) : ViewModel() {

  private val _isSubmitting = MutableStateFlow(false)
  private val _statusMessage = MutableStateFlow<String?>(null)
  val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

  private val financialRecordsFlow = combine(
    subledgerRepo.allEntries,
    noteRepo.allNotes,
    refundRepo.allRefunds,
    auditRepo.allAuditLogs
  ) { entries, notes, refunds, logs ->
    FinanceRecordsQuad(entries, notes, refunds, logs)
  }

  private val partyAndInvoiceFlow = combine(
    customerRepo.allCustomersIncludingArchived,
    supplierRepo.allSuppliersIncludingArchived,
    salesRepo.allSales,
    purchaseRepo.allPurchases
  ) { custs, sups, sales, purchases ->
    PartyInvoiceQuad(custs, sups, sales, purchases)
  }

  val uiState: StateFlow<FinanceLifecycleUiState> = combine(
    financialRecordsFlow,
    partyAndInvoiceFlow,
    _isSubmitting,
    _statusMessage
  ) { records, parties, busy, msg ->
    FinanceLifecycleUiState(
      subledgerEntries = records.entries,
      notes = records.notes,
      refunds = records.refunds,
      auditLogs = records.logs,
      customers = parties.customers,
      suppliers = parties.suppliers,
      salesInvoices = parties.sales,
      purchaseInvoices = parties.purchases,
      isSubmitting = busy,
      statusMessage = msg
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FinanceLifecycleUiState())

  fun clearStatusMessage() {
    _statusMessage.value = null
  }

  fun reversePayment(
    entryId: Long,
    reason: String,
    strategy: AdvanceReversalStrategy,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
  ) {
    if (_isSubmitting.value) return
    viewModelScope.launch {
      _isSubmitting.value = true
      try {
        subledgerRepo.reversePaymentTransaction(
          entryId = entryId,
          reason = reason,
          advanceStrategy = strategy
        )
        _statusMessage.value = "Payment #$entryId reversed cleanly."
        onSuccess()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        onError(e.message ?: "Failed to reverse payment")
      } finally {
        _isSubmitting.value = false
      }
    }
  }

  fun createCreditDebitNote(
    noteType: CreditDebitNoteType,
    partyType: PartyType,
    partyId: Long,
    amountPaise: Long,
    linkedInvoiceId: Long?,
    reason: String,
    notes: String,
    applyToDues: Boolean,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
  ) {
    if (_isSubmitting.value) return
    viewModelScope.launch {
      _isSubmitting.value = true
      try {
        noteRepo.createNote(
          noteType = noteType,
          partyType = partyType,
          partyId = partyId,
          amountPaise = amountPaise,
          linkedInvoiceId = linkedInvoiceId,
          reason = reason,
          notes = notes,
          applyToDues = applyToDues
        )
        _statusMessage.value = "${noteType.displayName} issued (${SubledgerCalculationEngine.formatPaise(amountPaise)})."
        onSuccess()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        onError(e.message ?: "Failed to issue note")
      } finally {
        _isSubmitting.value = false
      }
    }
  }

  fun applyNoteToInvoices(noteId: Long) {
    if (_isSubmitting.value) return
    viewModelScope.launch {
      _isSubmitting.value = true
      try {
        val applied = noteRepo.applyOpenNoteToInvoices(noteId)
        _statusMessage.value = if (applied > 0L) {
          "Applied ${SubledgerCalculationEngine.formatPaise(applied)} to unpaid invoices."
        } else {
          "No unpaid invoices available to apply this note."
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _statusMessage.value = e.message ?: "Failed to apply note"
      } finally {
        _isSubmitting.value = false
      }
    }
  }

  fun settleNoteAsRefund(
    noteId: Long,
    paymentMethod: PaymentMethod,
    reason: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
  ) {
    if (_isSubmitting.value) return
    viewModelScope.launch {
      _isSubmitting.value = true
      try {
        refundRepo.let { /* recorded inside noteRepo */ }
        noteRepo.settleOpenNoteAsRefundRecord(noteId, paymentMethod, reason)
        _statusMessage.value = "Remaining note converted to refund record."
        onSuccess()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        onError(e.message ?: "Failed to convert note to refund")
      } finally {
        _isSubmitting.value = false
      }
    }
  }

  fun cancelUnusedNote(
    noteId: Long,
    reason: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
  ) {
    if (_isSubmitting.value) return
    viewModelScope.launch {
      _isSubmitting.value = true
      try {
        noteRepo.cancelUnusedNote(noteId, reason)
        _statusMessage.value = "Unused note cancelled cleanly."
        onSuccess()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        onError(e.message ?: "Failed to cancel note")
      } finally {
        _isSubmitting.value = false
      }
    }
  }

  fun executeRefundPayout(
    refundRecordId: Long,
    paymentMethod: PaymentMethod,
    referenceNumber: String,
    reason: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
  ) {
    if (_isSubmitting.value) return
    viewModelScope.launch {
      _isSubmitting.value = true
      try {
        refundRepo.executeRefundPayout(
          refundRecordId = refundRecordId,
          paymentMethod = paymentMethod,
          referenceNumber = referenceNumber,
          reason = reason
        )
        _statusMessage.value = "Refund payout executed and posted to subledger."
        onSuccess()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        onError(e.message ?: "Failed to execute refund payout")
      } finally {
        _isSubmitting.value = false
      }
    }
  }

  private data class FinanceRecordsQuad(
    val entries: List<SubledgerEntry>,
    val notes: List<CreditDebitNote>,
    val refunds: List<RefundRecord>,
    val logs: List<FinancialAuditLog>
  )

  private data class PartyInvoiceQuad(
    val customers: List<Customer>,
    val suppliers: List<Supplier>,
    val sales: List<SalesInvoice>,
    val purchases: List<PurchaseInvoice>
  )

  class Factory(
    private val subledgerRepo: SubledgerRepository,
    private val noteRepo: CreditDebitNoteRepository,
    private val refundRepo: RefundRepository,
    private val auditRepo: AuditRepository,
    private val customerRepo: CustomerRepository,
    private val supplierRepo: SupplierRepository,
    private val salesRepo: SalesRepository,
    private val purchaseRepo: PurchaseRepository
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return FinanceLifecycleViewModel(
        subledgerRepo = subledgerRepo,
        noteRepo = noteRepo,
        refundRepo = refundRepo,
        auditRepo = auditRepo,
        customerRepo = customerRepo,
        supplierRepo = supplierRepo,
        salesRepo = salesRepo,
        purchaseRepo = purchaseRepo
      ) as T
    }
  }
}
