package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.CreditLimitExceededException
import com.example.data.repository.CustomerRepository
import com.example.data.repository.InventoryRepository
import com.example.data.repository.ProductRepository
import com.example.data.repository.SalesRepository
import com.example.data.repository.SubledgerRepository
import com.example.domain.engine.CreditLimitAssessment
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.model.Customer
import com.example.domain.model.PartyType
import com.example.domain.model.PaymentAllocationType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.Product
import com.example.domain.model.SalesInvoice
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
  private val subledgerRepo: SubledgerRepository? = null
) : ViewModel() {

  private val _isSubmitting = MutableStateFlow(false)
  val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

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

  suspend fun generateNextInvoiceNumber(): String {
    return salesRepo.generateNextInvoiceNumber()
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
    private val subledgerRepo: SubledgerRepository? = null
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return SalesViewModel(salesRepo, customerRepo, productRepo, inventoryRepo, subledgerRepo) as T
    }
  }
}
