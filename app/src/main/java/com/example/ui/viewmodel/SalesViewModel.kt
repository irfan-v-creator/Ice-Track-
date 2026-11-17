package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.CreditLimitExceededException
import com.example.data.repository.CustomerRepository
import com.example.data.repository.InventoryRepository
import com.example.data.repository.InvoiceLifecycleRepository
import com.example.data.repository.ProductRepository
import com.example.data.repository.ReturnRepository
import com.example.data.repository.SalesRepository
import com.example.data.repository.SubledgerRepository
import com.example.domain.engine.CreditLimitAssessment
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.model.Customer
import com.example.domain.model.InvoiceCancellationPaymentMode
import com.example.domain.model.PartyType
import com.example.domain.model.PaymentAllocationType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.Product
import com.example.domain.model.ProductUnit
import com.example.domain.model.ReturnSettlementType
import com.example.domain.model.SalesInvoice
import com.example.domain.model.SalesInvoiceDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SalesItemDraft(
  val product: Product,
  val quantityPieces: Int,
  val unitPricePaise: Long
) {
  val lineTotalPaise: Long get() = quantityPieces.toLong() * unitPricePaise
}

class SalesViewModel(
  private val salesRepo: SalesRepository,
  private val customerRepo: CustomerRepository,
  private val productRepo: ProductRepository,
  private val inventoryRepo: InventoryRepository,
  private val subledgerRepo: SubledgerRepository? = null,
  private val lifecycleRepo: InvoiceLifecycleRepository? = null,
  private val returnRepo: ReturnRepository? = null
) : ViewModel() {

  private val _isSubmitting = MutableStateFlow(false)
  val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

  private val _selectedSalesDetail = MutableStateFlow<SalesInvoiceDetail?>(null)
  val selectedSalesDetail: StateFlow<SalesInvoiceDetail?> = _selectedSalesDetail.asStateFlow()

  private val _isLoadingDetail = MutableStateFlow(false)
  val isLoadingDetail: StateFlow<Boolean> = _isLoadingDetail.asStateFlow()

  val customers = customerRepo.allActiveCustomers.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = emptyList()
  )

  val allCustomersIncludingArchived = customerRepo.allCustomersIncludingArchived.stateIn(
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

  val salesInvoices = salesRepo.allSales.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = emptyList()
  )

  val stockedBatches = inventoryRepo.allStockedBatches.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = emptyList()
  )

  val customerBalances: StateFlow<Map<Long, Long>> =
    (subledgerRepo?.allEntries ?: flowOf(emptyList()))
      .map { entries ->
        entries
          .filter { !it.isReversed && it.partyType == PartyType.CUSTOMER }
          .groupBy { it.partyId }
          .mapValues { (_, list) -> SubledgerCalculationEngine.calculateCustomerBalance(list) }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

  val customerAdvances: StateFlow<Map<Long, Long>> =
    (subledgerRepo?.getAllActiveAllocationsByPartyType(PartyType.CUSTOMER) ?: flowOf(emptyList()))
      .map { allocs ->
        allocs
          .filter { !it.isReversed && it.allocationType == PaymentAllocationType.ADVANCE }
          .groupBy { it.partyId }
          .mapValues { (_, list) -> list.sumOf { it.allocatedAmountPaise } }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

  suspend fun getSellingPriceForShop(customerId: Long, productId: Long, defaultPricePaise: Long): Long {
    val customPrice = customerRepo.getPriceForCustomerProduct(customerId, productId)
    return customPrice ?: defaultPricePaise
  }

  suspend fun getProductUnit(productId: Long): ProductUnit? {
    return productRepo.getUnitForProductSync(productId)
  }

  suspend fun generateNextInvoiceNumber(): String {
    return salesRepo.generateNextInvoiceNumber()
  }

  fun loadSalesDetail(invoiceId: Long) {
    val repo = lifecycleRepo ?: return
    viewModelScope.launch {
      _isLoadingDetail.value = true
      try {
        _selectedSalesDetail.value = repo.getSalesInvoiceDetail(invoiceId)
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _selectedSalesDetail.value = null
      } finally {
        _isLoadingDetail.value = false
      }
    }
  }

  fun dismissSalesDetail() {
    _selectedSalesDetail.value = null
  }

  fun formatSalesInvoiceSummary(detail: SalesInvoiceDetail): String {
    val inv = detail.invoice
    val party = inv.partyNameSnapshot.ifBlank { detail.customer?.shopName ?: "Customer #${inv.customerId}" }
    return buildString {
      appendLine("IceTrack Sales Invoice #${inv.invoiceNumber}")
      appendLine("Shop: $party")
      appendLine("Date: ${SubledgerCalculationEngine.formatDate(inv.invoiceDate)}")
      appendLine("Status: ${inv.invoiceStatus.displayName} • ${inv.paymentStatus.displayName}")
      appendLine("Items:")
      detail.items.forEach { item ->
        val name = item.productNameSnapshot.ifBlank { "Product #${item.productId}" }
        appendLine("- $name: ${item.quantityPieces} pcs = ${SubledgerCalculationEngine.formatPaise(item.lineTotalPaise)}")
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
      append("Balance Due: ${SubledgerCalculationEngine.formatPaise(inv.balanceDuePaise)}")
    }
  }

  fun editSalesInvoice(
    request: InvoiceLifecycleRepository.EditSalesInvoiceRequest,
    onCreditLimitExceeded: ((CreditLimitAssessment) -> Unit)? = null,
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
        repo.editSalesInvoice(request)
        _selectedSalesDetail.value = repo.getSalesInvoiceDetail(request.invoiceId)
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
        onError(e.message ?: "Failed to update sales invoice")
      } finally {
        _isSubmitting.value = false
      }
    }
  }

  fun cancelSalesInvoice(
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
        repo.cancelSalesInvoice(invoiceId = invoiceId, reason = reason, paymentMode = paymentMode)
        _selectedSalesDetail.value = repo.getSalesInvoiceDetail(invoiceId)
        onSuccess()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        onError(e.message ?: "Failed to cancel sales invoice")
      } finally {
        _isSubmitting.value = false
      }
    }
  }

  fun createSalesReturn(
    salesInvoiceId: Long,
    lines: List<ReturnRepository.RequestedSalesReturnLine>,
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
        repo.createSalesReturn(
          salesInvoiceId = salesInvoiceId,
          requestedLines = lines,
          settlementType = settlementType,
          refundPaymentMethod = refundPaymentMethod,
          reason = reason,
          notes = notes
        )
        _selectedSalesDetail.value = lifecycleRepo?.getSalesInvoiceDetail(salesInvoiceId)
        onSuccess()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        onError(e.message ?: "Failed to record sales return")
      } finally {
        _isSubmitting.value = false
      }
    }
  }

  fun createSalesInvoice(
    customerId: Long,
    invoiceNumber: String,
    invoiceDate: Long,
    dueDate: Long,
    items: List<SalesItemDraft>,
    paidAmountPaise: Long,
    paymentMethod: PaymentMethod?,
    notes: String,
    allowExpired: Boolean = false,
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
        val requestedLines = items.map { draft ->
          SalesRepository.RequestedSaleLine(
            productId = draft.product.id,
            quantityPieces = draft.quantityPieces,
            unitPricePaise = draft.unitPricePaise
          )
        }

        salesRepo.createSalesInvoice(
          customerId = customerId,
          invoiceNumber = invoiceNumber,
          invoiceDate = invoiceDate,
          dueDate = dueDate,
          requestedLines = requestedLines,
          paidAmountPaise = paidAmountPaise,
          paymentMethod = paymentMethod,
          notes = notes,
          allowExpired = allowExpired,
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
        onError(e.message ?: "Failed to generate sales invoice")
      } finally {
        _isSubmitting.value = false
      }
    }
  }

  fun recordCustomerPayment(
    customerId: Long,
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
        salesRepo.recordCustomerPayment(
          customerId = customerId,
          amountPaise = amountPaise,
          paymentDate = paymentDate,
          paymentMethod = paymentMethod,
          notes = notes
        )
        onSuccess()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        onError(e.message ?: "Failed to record customer payment")
      } finally {
        _isSubmitting.value = false
      }
    }
  }

  fun applyCustomerAdvance(
    customerId: Long,
    onSuccess: (Long) -> Unit,
    onError: (String) -> Unit
  ) {
    if (_isSubmitting.value) return
    viewModelScope.launch {
      _isSubmitting.value = true
      try {
        val applied = salesRepo.applyAvailableCustomerAdvance(customerId)
        onSuccess(applied)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        onError(e.message ?: "Failed to apply customer advance")
      } finally {
        _isSubmitting.value = false
      }
    }
  }

  class Factory(
    private val salesRepo: SalesRepository,
    private val customerRepo: CustomerRepository,
    private val productRepo: ProductRepository,
    private val inventoryRepo: InventoryRepository,
    private val subledgerRepo: SubledgerRepository? = null,
    private val lifecycleRepo: InvoiceLifecycleRepository? = null,
    private val returnRepo: ReturnRepository? = null
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return SalesViewModel(salesRepo, customerRepo, productRepo, inventoryRepo, subledgerRepo, lifecycleRepo, returnRepo) as T
    }
  }
}
