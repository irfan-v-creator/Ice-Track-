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
import com.example.domain.model.Product
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
  val outstandingReceivablePaise: Long
)

data class CustomerFormState(
  val id: Long = 0,
  val shopName: String = "",
  val ownerName: String = "",
  val phoneNumber: String = "",
  val address: String = "",
  val openingBalanceRupees: String = "0",
  val openingBalanceDate: Long = System.currentTimeMillis(),
  val creditLimitRupees: String = "0",
  val paymentTermsDays: String = "7",
  val customPrices: Map<Long, String> = emptyMap(), // productId -> priceInRupees
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
  val customersWithBalance: StateFlow<List<CustomerWithBalance>> = searchQuery.flatMapLatest { query ->
    customerRepository.searchCustomers(query)
  }.combine(subledgerRepository.allEntries) { customers, entries ->
    val activeEntries = entries.filter { !it.isReversed && it.partyType == PartyType.CUSTOMER }
    val entriesByCustomer = activeEntries.groupBy { it.partyId }

    customers.map { customer ->
      val partyEntries = entriesByCustomer[customer.id] ?: emptyList()
      val receivable = SubledgerCalculationEngine.calculateCustomerBalance(partyEntries)
      CustomerWithBalance(customer, receivable)
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
    viewModelScope.launch {
      _formState.value = CustomerFormState(
        id = customer.id,
        shopName = customer.shopName,
        ownerName = customer.ownerName,
        phoneNumber = customer.phoneNumber,
        address = customer.address,
        openingBalanceRupees = (customer.openingBalancePaise / 100.0).toString(),
        openingBalanceDate = customer.openingBalanceDate,
        creditLimitRupees = (customer.creditLimitPaise / 100.0).toString(),
        paymentTermsDays = customer.paymentTermsDays.toString()
      )
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
    if (state.shopName.isBlank()) {
      _formState.value = state.copy(errorMessage = "Shop name cannot be empty")
      return
    }

    val openingPaise = SubledgerCalculationEngine.parseRupeesToPaise(state.openingBalanceRupees)
    val creditLimitPaise = SubledgerCalculationEngine.parseRupeesToPaise(state.creditLimitRupees)
    val termsDays = state.paymentTermsDays.toIntOrNull() ?: 7

    val customPriceEntities = state.customPrices.mapNotNull { (productId, priceStr) ->
      val paise = SubledgerCalculationEngine.parseRupeesToPaise(priceStr)
      if (paise > 0L) {
        CustomerProductPrice(customerId = state.id, productId = productId, customSellingPricePaise = paise)
      } else null
    }

    viewModelScope.launch {
      _formState.value = state.copy(isSaving = true, errorMessage = null)
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
          paymentTermsDays = termsDays
        )
        customerRepository.saveCustomer(customer, customPriceEntities)
        _formState.value = CustomerFormState()
        onSuccess()
      } catch (e: Exception) {
        _formState.value = state.copy(isSaving = false, errorMessage = "Error saving customer: ${e.message}")
      }
    }
  }

  fun deleteCustomer(customerId: Long) {
    viewModelScope.launch {
      customerRepository.softDelete(customerId)
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
