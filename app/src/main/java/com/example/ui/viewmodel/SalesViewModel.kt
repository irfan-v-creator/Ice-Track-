package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.CustomerRepository
import com.example.data.repository.InventoryRepository
import com.example.data.repository.ProductRepository
import com.example.data.repository.SalesRepository
import com.example.domain.model.Customer
import com.example.domain.model.PaymentMethod
import com.example.domain.model.Product
import com.example.domain.model.SalesInvoice
import kotlinx.coroutines.flow.SharingStarted
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
  private val inventoryRepo: InventoryRepository
) : ViewModel() {

  val customers = customerRepo.allActiveCustomers.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = emptyList()
  )

  val products = productRepo.allActiveProducts.stateIn(
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

  suspend fun getSellingPriceForShop(customerId: Long, productId: Long, defaultPricePaise: Long): Long {
    val customPrice = customerRepo.getPriceForCustomerProduct(customerId, productId)
    return customPrice ?: defaultPricePaise
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
    onSuccess: () -> Unit,
    onError: (String) -> Unit
  ) {
    viewModelScope.launch {
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
          allowExpired = allowExpired
        )
        onSuccess()
      } catch (e: Exception) {
        onError(e.message ?: "Failed to generate sales invoice")
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
    viewModelScope.launch {
      try {
        salesRepo.recordCustomerPayment(
          customerId = customerId,
          amountPaise = amountPaise,
          paymentDate = paymentDate,
          paymentMethod = paymentMethod,
          notes = notes
        )
        onSuccess()
      } catch (e: Exception) {
        onError(e.message ?: "Failed to record customer payment")
      }
    }
  }

  class Factory(
    private val salesRepo: SalesRepository,
    private val customerRepo: CustomerRepository,
    private val productRepo: ProductRepository,
    private val inventoryRepo: InventoryRepository
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return SalesViewModel(salesRepo, customerRepo, productRepo, inventoryRepo) as T
    }
  }
}
