package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.CustomerRepository
import com.example.data.repository.ProductRepository
import com.example.data.repository.SubledgerRepository
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.model.Customer
import com.example.domain.model.CustomerProductPrice
import com.example.domain.model.PartyType
import com.example.domain.model.PaymentAllocationType
import com.example.domain.model.Product
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CustomerWithBalance(
  val customer: Customer,
  val outstandingReceivablePaise: Long,
  val availableAdvancePaise: Long = 0L
)

data class CustomerFormState(
  val id: Long = 0,
  val createdAt: Long = System.currentTimeMillis(),
  val shopName: String = "",
  val ownerName: String = "",
  val phoneNumber: String = "",
  val address: String = "",
  val openingBalanceRupees: String = "0",
  val openingBalanceDate: Long = System.currentTimeMillis(),
  val creditLimitRupees: String = "0",
  val paymentTermsDays: String = "7",
  val customPrices: Map<Long, String> = emptyMap(), // productId -> priceInRupees
  val isLoadingDetails: Boolean = false,
  val errorMessage: String? = null,
  val isSaving: Boolean = false
)

class CustomerViewModel(
  private val customerRepository: CustomerRepository,
  private val productRepository: ProductRepository,
  private val subledgerRepository: SubledgerRepository
) : ViewModel() {

  val searchQuery = MutableStateFlow("")

  val allProducts: StateFlow<List<Product>> = productRepository.allActiveProducts
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  @OptIn(ExperimentalCoroutinesApi::class)
  val customersWithBalance: StateFlow<List<CustomerWithBalance>> = combine(
    searchQuery.flatMapLatest { query -> customerRepository.searchCustomers(query) },
    subledgerRepository.allEntries,
    subledgerRepository.getAllActiveAllocationsByPartyType(PartyType.CUSTOMER)
  ) { customers, entries, allocations ->
    val activeEntries = entries.filter { !it.isReversed && it.partyType == PartyType.CUSTOMER }
    val entriesByCustomer = activeEntries.groupBy { it.partyId }
    val advancesByCustomer = allocations
      .filter { !it.isReversed && it.allocationType == PaymentAllocationType.ADVANCE }
      .groupBy { it.partyId }
      .mapValues { (_, list) -> list.sumOf { it.allocatedAmountPaise } }

    customers.map { customer ->
      val partyEntries = entriesByCustomer[customer.id] ?: emptyList()
      val receivable = SubledgerCalculationEngine.calculateCustomerBalance(partyEntries)
      val advance = advancesByCustomer[customer.id] ?: 0L
      CustomerWithBalance(customer, receivable, advance)
    }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _formState = MutableStateFlow(CustomerFormState())
  val formState: StateFlow<CustomerFormState> = _formState.asStateFlow()

  fun onSearchQueryChanged(query: String) {
    searchQuery.value = query
  }

  fun openAddDialog() {
    _formState.value = CustomerFormState()
  }

  fun openEditDialog(customer: Customer) {
    _formState.value = CustomerFormState(
      id = customer.id,
      createdAt = customer.createdAt,
      shopName = customer.shopName,
      ownerName = customer.ownerName,
      phoneNumber = customer.phoneNumber,
      address = customer.address,
      openingBalanceRupees = SubledgerCalculationEngine.formatPaiseForInput(customer.openingBalancePaise),
      openingBalanceDate = customer.openingBalanceDate,
      creditLimitRupees = SubledgerCalculationEngine.formatPaiseForInput(customer.creditLimitPaise),
      paymentTermsDays = customer.paymentTermsDays.toString(),
      isLoadingDetails = true
    )

    viewModelScope.launch {
      try {
        val loadedPrices = customerRepository.getCustomPricesSync(customer.id)
          .associate { cp ->
            cp.productId to SubledgerCalculationEngine.formatPaiseForInput(cp.customSellingPricePaise)
          }
        if (_formState.value.id == customer.id) {
          _formState.value = _formState.value.copy(
            customPrices = loadedPrices,
            isLoadingDetails = false
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        if (_formState.value.id == customer.id) {
          _formState.value = _formState.value.copy(
            isLoadingDetails = false,
            errorMessage = "Failed to load shop custom prices: ${e.message}"
          )
        }
      }
    }
  }

  fun updateForm(transform: (CustomerFormState) -> CustomerFormState) {
    _formState.value = transform(_formState.value)
  }

  fun setCustomPrice(productId: Long, priceRupees: String) {
    val current = _formState.value.customPrices.toMutableMap()
    if (priceRupees.isBlank()) {
      current.remove(productId)
    } else {
      current[productId] = priceRupees
    }
    _formState.value = _formState.value.copy(customPrices = current)
  }

  fun saveCustomer(onSuccess: () -> Unit) {
    val state = _formState.value
    if (state.isSaving || state.isLoadingDetails) return

    if (state.shopName.isBlank()) {
      _formState.value = state.copy(errorMessage = "Shop name cannot be empty")
      return
    }

    val openingPaise = SubledgerCalculationEngine.parseRupeesToPaiseOrNull(
      state.openingBalanceRupees,
      allowNegative = true,
      allowEmptyAsZero = true
    )
    if (openingPaise == null) {
      _formState.value = state.copy(errorMessage = "Invalid opening balance amount. Enter a valid number with up to 2 decimal places.")
      return
    }

    val creditLimitPaise = SubledgerCalculationEngine.parseRupeesToPaiseOrNull(
      state.creditLimitRupees,
      allowNegative = false,
      allowEmptyAsZero = true
    )
    if (creditLimitPaise == null) {
      _formState.value = state.copy(errorMessage = "Invalid credit limit amount. Enter a non-negative number.")
      return
    }

    val termsDays = if (state.paymentTermsDays.isBlank()) 7 else state.paymentTermsDays.trim().toIntOrNull()
    if (termsDays == null || termsDays < 0) {
      _formState.value = state.copy(errorMessage = "Payment terms must be 0 or greater days")
      return
    }

    val customPriceEntities = mutableListOf<CustomerProductPrice>()
    for ((productId, priceStr) in state.customPrices) {
      if (priceStr.isBlank()) continue
      val paise = SubledgerCalculationEngine.parseRupeesToPaiseOrNull(
        priceStr,
        allowNegative = false,
        allowEmptyAsZero = false
      )
      if (paise == null || paise <= 0L) {
        _formState.value = state.copy(errorMessage = "Invalid custom price '$priceStr'. Enter a positive amount or leave blank.")
        return
      }
      customPriceEntities.add(
        CustomerProductPrice(
          customerId = state.id,
          productId = productId,
          customSellingPricePaise = paise
        )
      )
    }

    viewModelScope.launch {
      _formState.value = _formState.value.copy(isSaving = true, errorMessage = null)
      try {
        val customer = Customer(
          id = state.id,
          shopName = state.shopName.trim(),
          ownerName = state.ownerName.trim(),
          phoneNumber = state.phoneNumber.trim(),
          address = state.address.trim(),
          openingBalancePaise = openingPaise,
          openingBalanceDate = state.openingBalanceDate,
          creditLimitPaise = creditLimitPaise,
          paymentTermsDays = termsDays,
          createdAt = state.createdAt
        )
        customerRepository.saveCustomer(
          customer = customer,
          customPrices = customPriceEntities,
          replaceCustomPrices = true
        )
        _formState.value = CustomerFormState()
        onSuccess()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _formState.value = _formState.value.copy(isSaving = false, errorMessage = "Error saving customer: ${e.message}")
      }
    }
  }

  fun deleteCustomer(customerId: Long) {
    viewModelScope.launch {
      try {
        customerRepository.softDelete(customerId)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _formState.value = _formState.value.copy(errorMessage = "Error deleting customer: ${e.message}")
      }
    }
  }

  class Factory(
    private val customerRepository: CustomerRepository,
    private val productRepository: ProductRepository,
    private val subledgerRepository: SubledgerRepository
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return CustomerViewModel(customerRepository, productRepository, subledgerRepository) as T
    }
  }
}
