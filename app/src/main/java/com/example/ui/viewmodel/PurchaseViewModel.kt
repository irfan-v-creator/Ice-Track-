package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ProductRepository
import com.example.data.repository.PurchaseRepository
import com.example.data.repository.SupplierRepository
import com.example.domain.model.PaymentMethod
import com.example.domain.model.Product
import com.example.domain.model.ProductSupplier
import com.example.domain.model.PurchaseInvoice
import com.example.domain.model.PurchaseInvoiceItem
import com.example.domain.model.Supplier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
  private val productRepo: ProductRepository
) : ViewModel() {

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

  fun createPurchaseInvoice(
    supplierId: Long,
    invoiceNumber: String,
    invoiceDate: Long,
    dueDate: Long,
    items: List<PurchaseItemDraft>,
    paidAmountPaise: Long,
    paymentMethod: PaymentMethod?,
    notes: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
  ) {
    viewModelScope.launch {
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
          notes = notes
        )
        onSuccess()
      } catch (e: Exception) {
        onError(e.message ?: "Failed to record purchase invoice")
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
    viewModelScope.launch {
      try {
        purchaseRepo.recordSupplierPayment(
          supplierId = supplierId,
          amountPaise = amountPaise,
          paymentDate = paymentDate,
          paymentMethod = paymentMethod,
          notes = notes
        )
        onSuccess()
      } catch (e: Exception) {
        onError(e.message ?: "Failed to record payment")
      }
    }
  }

  class Factory(
    private val purchaseRepo: PurchaseRepository,
    private val supplierRepo: SupplierRepository,
    private val productRepo: ProductRepository
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return PurchaseViewModel(purchaseRepo, supplierRepo, productRepo) as T
    }
  }
}
