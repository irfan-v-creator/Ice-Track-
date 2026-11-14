package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.CreditLimitExceededException
import com.example.data.repository.ProductRepository
import com.example.data.repository.PurchaseRepository
import com.example.data.repository.SubledgerRepository
import com.example.data.repository.SupplierRepository
import com.example.domain.engine.CreditLimitAssessment
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.model.PartyType
import com.example.domain.model.PaymentAllocationType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.Product
import com.example.domain.model.ProductSupplier
import com.example.domain.model.PurchaseInvoice
import com.example.domain.model.PurchaseInvoiceItem
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
  private val subledgerRepo: SubledgerRepository? = null
) : ViewModel() {

  private val _isSubmitting = MutableStateFlow(false)
  val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

  val suppliers = supplierRepo.allActiveSuppliers.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = emptyList()
  )

  val products = productRepo.allActiveProducts.stateIn(
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
    private val subledgerRepo: SubledgerRepository? = null
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return PurchaseViewModel(purchaseRepo, supplierRepo, productRepo, subledgerRepo) as T
    }
  }
}
