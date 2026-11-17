package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.CreditLimitExceededException
import com.example.data.repository.InvoiceLifecycleRepository
import com.example.data.repository.ProductRepository
import com.example.data.repository.PurchaseRepository
import com.example.data.repository.ReturnRepository
import com.example.data.repository.SubledgerRepository
import com.example.data.repository.SupplierRepository
import com.example.domain.engine.CreditLimitAssessment
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.model.InvoiceCancellationPaymentMode
import com.example.domain.model.PartyType
import com.example.domain.model.PaymentAllocationType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.Product
import com.example.domain.model.ProductSupplier
import com.example.domain.model.ProductUnit
import com.example.domain.model.PurchaseInvoice
import com.example.domain.model.PurchaseInvoiceDetail
import com.example.domain.model.PurchaseInvoiceItem
import com.example.domain.model.ReturnSettlementType
import com.example.domain.model.Supplier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PurchaseItemDraft(
  val product: Product,
  val batchNumber: String,
  val expiryDate: Long,
  val quantityPieces: Int,
  val costPerPiecePaise: Long
) {
  val lineTotalPaise: Long get() = quantityPieces.toLong() * costPerPiecePaise
}

data class PurchaseUiState(
  val invoices: List<PurchaseInvoice> = emptyList(),
  val suppliers: List<Supplier> = emptyList(),
  val products: List<Product> = emptyList(),
  val supplierProductPrices: Map<Long, List<ProductSupplier>> = emptyMap(),
  val isCreatingInvoice: Boolean = false,
  val isRecordingPayment: Boolean = false,
  val errorMessage: String? = null
)

class PurchaseViewModel(
  private val purchaseRepo: PurchaseRepository,
  private val supplierRepo: SupplierRepository,
  private val productRepo: ProductRepository,
  private val subledgerRepo: SubledgerRepository? = null,
  private val lifecycleRepo: InvoiceLifecycleRepository? = null,
  private val returnRepo: ReturnRepository? = null
) : ViewModel() {

  private val _isSubmitting = MutableStateFlow(false)
  val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

  private val _selectedPurchaseDetail = MutableStateFlow<PurchaseInvoiceDetail?>(null)
  val selectedPurchaseDetail: StateFlow<PurchaseInvoiceDetail?> = _selectedPurchaseDetail.asStateFlow()

  private val _isLoadingDetail = MutableStateFlow(false)
  val isLoadingDetail: StateFlow<Boolean> = _isLoadingDetail.asStateFlow()

  val suppliers = supplierRepo.allActiveSuppliers.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = emptyList()
  )

  val allSuppliersIncludingArchived = supplierRepo.allSuppliersIncludingArchived.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = emptyList()
  )

  val products = productRepo.allActiveProducts.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = emptyList()
  )

  val allProductsIncludingArchived = productRepo.allProductsIncludingArchived.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = emptyList()
  )

  val purchases = purchaseRepo.allPurchases.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = emptyList()
  )

  val supplierBalances: StateFlow<Map<Long, Long>> =
    (subledgerRepo?.allEntries ?: flowOf(emptyList()))
      .map { entries ->
        entries
          .filter { !it.isReversed && it.partyType == PartyType.SUPPLIER }
          .groupBy { it.partyId }
          .mapValues { (_, list) -> SubledgerCalculationEngine.calculateSupplierBalance(list) }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

  val supplierAdvances: StateFlow<Map<Long, Long>> =
    (subledgerRepo?.getAllActiveAllocationsByPartyType(PartyType.SUPPLIER) ?: flowOf(emptyList()))
      .map { allocs ->
        allocs
          .filter { !it.isReversed && it.allocationType == PaymentAllocationType.ADVANCE }
          .groupBy { it.partyId }
          .mapValues { (_, list) -> list.sumOf { it.allocatedAmountPaise } }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

  suspend fun generateNextInvoiceNumber(): String {
    return purchaseRepo.generateNextInvoiceNumber()
  }

  suspend fun getProductUnit(productId: Long): ProductUnit? {
    return productRepo.getUnitForProductSync(productId)
  }

  fun loadPurchaseDetail(invoiceId: Long) {
    val repo = lifecycleRepo ?: return
    viewModelScope.launch {
      _isLoadingDetail.value = true
      try {
        _selectedPurchaseDetail.value = repo.getPurchaseInvoiceDetail(invoiceId)
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _selectedPurchaseDetail.value = null
      } finally {
        _isLoadingDetail.value = false
      }
    }
  }

  fun dismissPurchaseDetail() {
    _selectedPurchaseDetail.value = null
  }

  fun formatPurchaseInvoiceSummary(detail: PurchaseInvoiceDetail): String {
    val inv = detail.invoice
    val supName = inv.partyNameSnapshot.ifBlank { detail.supplier?.name ?: "Supplier #${inv.supplierId}" }
    return buildString {
      appendLine("IceTrack Purchase Invoice #${inv.invoiceNumber}")
      appendLine("Supplier: $supName")
      appendLine("Date: ${SubledgerCalculationEngine.formatDate(inv.invoiceDate)}")
      appendLine("Status: ${inv.invoiceStatus.displayName} • ${inv.paymentStatus.displayName}")
      appendLine("Batches / Items:")
      detail.items.forEach { item ->
        val name = item.productNameSnapshot.ifBlank { "Product #${item.productId}" }
        appendLine("- $name [${item.batchNumber}]: ${item.quantityPieces} pcs = ${SubledgerCalculationEngine.formatPaise(item.lineTotalPaise)}")
      }
      appendLine("Subtotal: ${SubledgerCalculationEngine.formatPaise(inv.subtotalPaise)}")
      if (inv.discountAmountPaise > 0L) {
        appendLine("Discount: -${SubledgerCalculationEngine.formatPaise(inv.discountAmountPaise)}")
      }
      if (inv.additionalChargesPaise > 0L) {
        appendLine("Additional Charges: ${SubledgerCalculationEngine.formatPaise(inv.additionalChargesPaise)}")
      }
      appendLine("Total: ${SubledgerCalculationEngine.formatPaise(inv.totalAmountPaise)}")
      appendLine("Paid: ${SubledgerCalculationEngine.formatPaise(inv.paidAmountPaise)}")
      append("Balance Payable: ${SubledgerCalculationEngine.formatPaise(inv.balanceDuePaise)}")
    }
  }

  fun editPurchaseInvoice(
    request: InvoiceLifecycleRepository.EditPurchaseInvoiceRequest,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
  ) {
    val repo = lifecycleRepo ?: run {
      onError("Lifecycle repository unavailable.")
      return
    }
    if (_isSubmitting.value) return
    viewModelScope.launch {
      _isSubmitting.value = true
      try {
        repo.editPurchaseInvoice(request)
        _selectedPurchaseDetail.value = repo.getPurchaseInvoiceDetail(request.invoiceId)
        onSuccess()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        onError(e.message ?: "Failed to update purchase invoice")
      } finally {
        _isSubmitting.value = false
      }
    }
  }

  fun cancelPurchaseInvoice(
    invoiceId: Long,
    reason: String,
    paymentMode: InvoiceCancellationPaymentMode,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
  ) {
    val repo = lifecycleRepo ?: run {
      onError("Lifecycle repository unavailable.")
      return
    }
    if (_isSubmitting.value) return
    viewModelScope.launch {
      _isSubmitting.value = true
      try {
        repo.cancelPurchaseInvoice(invoiceId = invoiceId, reason = reason, paymentMode = paymentMode)
        _selectedPurchaseDetail.value = repo.getPurchaseInvoiceDetail(invoiceId)
        onSuccess()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        onError(e.message ?: "Failed to cancel purchase invoice")
      } finally {
        _isSubmitting.value = false
      }
    }
  }

  fun createPurchaseReturn(
    purchaseInvoiceId: Long,
    lines: List<ReturnRepository.RequestedPurchaseReturnLine>,
    settlementType: ReturnSettlementType,
    refundPaymentMethod: PaymentMethod,
    reason: String,
    notes: String = "",
    onSuccess: () -> Unit,
    onError: (String) -> Unit
  ) {
    val repo = returnRepo ?: run {
      onError("Return repository unavailable.")
      return
    }
    if (_isSubmitting.value) return
    viewModelScope.launch {
      _isSubmitting.value = true
      try {
        repo.createPurchaseReturn(
          purchaseInvoiceId = purchaseInvoiceId,
          requestedLines = lines,
          settlementType = settlementType,
          refundPaymentMethod = refundPaymentMethod,
          reason = reason,
          notes = notes
        )
        _selectedPurchaseDetail.value = lifecycleRepo?.getPurchaseInvoiceDetail(purchaseInvoiceId)
        onSuccess()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        onError(e.message ?: "Failed to record purchase return")
      } finally {
        _isSubmitting.value = false
      }
    }
  }

  fun createPurchaseInvoice(
    supplierId: Long,
    invoiceNumber: String,
    invoiceDate: Long,
    dueDate: Long,
    items: List<PurchaseItemDraft>,
    paidAmountPaise: Long,
    paymentMethod: PaymentMethod?,
    notes: String,
    applyAvailableAdvance: Boolean = true,
    allowCreditLimitOverride: Boolean = false,
    onCreditLimitExceeded: ((CreditLimitAssessment) -> Unit)? = null,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
  ) {
    if (_isSubmitting.value) return
    viewModelScope.launch {
      _isSubmitting.value = true
      try {
        val invoiceItems = items.map { draft ->
          PurchaseInvoiceItem(
            productId = draft.product.id,
            batchNumber = draft.batchNumber,
            expiryDate = draft.expiryDate,
            quantityPieces = draft.quantityPieces,
            costPerPiecePaise = draft.costPerPiecePaise,
            lineTotalPaise = draft.lineTotalPaise
          )
        }

        purchaseRepo.createPurchaseInvoice(
          supplierId = supplierId,
          invoiceNumber = invoiceNumber,
          invoiceDate = invoiceDate,
          dueDate = dueDate,
          items = invoiceItems,
          paidAmountPaise = paidAmountPaise,
          paymentMethod = paymentMethod,
          notes = notes,
          applyAvailableAdvance = applyAvailableAdvance,
          allowCreditLimitOverride = allowCreditLimitOverride
        )
        onSuccess()
      } catch (e: CancellationException) {
        throw e
      } catch (e: CreditLimitExceededException) {
        if (onCreditLimitExceeded != null) {
          onCreditLimitExceeded(e.assessment)
        } else {
          onError(e.message)
        }
      } catch (e: Exception) {
        onError(e.message ?: "Failed to record purchase invoice")
      } finally {
        _isSubmitting.value = false
      }
    }
  }

  fun recordSupplierPayment(
    supplierId: Long,
    amountPaise: Long,
    paymentDate: Long,
    paymentMethod: PaymentMethod,
    notes: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
  ) {
    if (_isSubmitting.value) return
    viewModelScope.launch {
      _isSubmitting.value = true
      try {
        purchaseRepo.recordSupplierPayment(
          supplierId = supplierId,
          amountPaise = amountPaise,
          paymentDate = paymentDate,
          paymentMethod = paymentMethod,
          notes = notes
        )
        onSuccess()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        onError(e.message ?: "Failed to record payment")
      } finally {
        _isSubmitting.value = false
      }
    }
  }

  fun applySupplierAdvance(
    supplierId: Long,
    onSuccess: (Long) -> Unit,
    onError: (String) -> Unit
  ) {
    if (_isSubmitting.value) return
    viewModelScope.launch {
      _isSubmitting.value = true
      try {
        val applied = purchaseRepo.applyAvailableSupplierAdvance(supplierId)
        onSuccess(applied)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        onError(e.message ?: "Failed to apply supplier advance")
      } finally {
        _isSubmitting.value = false
      }
    }
  }

  class Factory(
    private val purchaseRepo: PurchaseRepository,
    private val supplierRepo: SupplierRepository,
    private val productRepo: ProductRepository,
    private val subledgerRepo: SubledgerRepository? = null,
    private val lifecycleRepo: InvoiceLifecycleRepository? = null,
    private val returnRepo: ReturnRepository? = null
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return PurchaseViewModel(purchaseRepo, supplierRepo, productRepo, subledgerRepo, lifecycleRepo, returnRepo) as T
    }
  }
}
