package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.SubledgerRepository
import com.example.data.repository.SupplierRepository
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.model.PartyType
import com.example.domain.model.Supplier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SupplierWithBalance(
  val supplier: Supplier,
  val outstandingPayablePaise: Long
)

data class SupplierFormState(
  val id: Long = 0,
  val name: String = "",
  val contactPerson: String = "",
  val phoneNumber: String = "",
  val address: String = "",
  val openingBalanceRupees: String = "0",
  val openingBalanceDate: Long = System.currentTimeMillis(),
  val creditTermsDays: String = "15",
  val creditLimitRupees: String = "0",
  val errorMessage: String? = null,
  val isSaving: Boolean = false
)

class SupplierViewModel(
  private val supplierRepository: SupplierRepository,
  private val subledgerRepository: SubledgerRepository
) : ViewModel() {

  val searchQuery = MutableStateFlow("")

  @OptIn(ExperimentalCoroutinesApi::class)
  val suppliersWithBalance: StateFlow<List<SupplierWithBalance>> = searchQuery.flatMapLatest { query ->
    supplierRepository.searchSuppliers(query)
  }.combine(subledgerRepository.allEntries) { suppliers, entries ->
    val activeEntries = entries.filter { !it.isReversed && it.partyType == PartyType.SUPPLIER }
    val entriesBySupplier = activeEntries.groupBy { it.partyId }

    suppliers.map { supplier ->
      val partyEntries = entriesBySupplier[supplier.id] ?: emptyList()
      val payable = SubledgerCalculationEngine.calculateSupplierBalance(partyEntries)
      SupplierWithBalance(supplier, payable)
    }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _formState = MutableStateFlow(SupplierFormState())
  val formState: StateFlow<SupplierFormState> = _formState.asStateFlow()

  fun onSearchQueryChanged(query: String) {
    searchQuery.value = query
  }

  fun openAddDialog() {
    _formState.value = SupplierFormState()
  }

  fun openEditDialog(supplier: Supplier) {
    _formState.value = SupplierFormState(
      id = supplier.id,
      name = supplier.name,
      contactPerson = supplier.contactPerson,
      phoneNumber = supplier.phoneNumber,
      address = supplier.address,
      openingBalanceRupees = (supplier.openingBalancePaise / 100.0).toString(),
      openingBalanceDate = supplier.openingBalanceDate,
      creditTermsDays = supplier.creditTermsDays.toString(),
      creditLimitRupees = (supplier.creditLimitPaise / 100.0).toString()
    )
  }

  fun updateForm(transform: (SupplierFormState) -> SupplierFormState) {
    _formState.value = transform(_formState.value)
  }

  fun saveSupplier(onSuccess: () -> Unit) {
    val state = _formState.value
    if (state.name.isBlank()) {
      _formState.value = state.copy(errorMessage = "Supplier name cannot be empty")
      return
    }

    val openingPaise = SubledgerCalculationEngine.parseRupeesToPaise(state.openingBalanceRupees)
    val creditLimitPaise = SubledgerCalculationEngine.parseRupeesToPaise(state.creditLimitRupees)
    val termsDays = state.creditTermsDays.toIntOrNull() ?: 15

    viewModelScope.launch {
      _formState.value = state.copy(isSaving = true, errorMessage = null)
      try {
        val supplier = Supplier(
          id = state.id,
          name = state.name.trim(),
          contactPerson = state.contactPerson.trim(),
          phoneNumber = state.phoneNumber.trim(),
          address = state.address.trim(),
          openingBalancePaise = openingPaise,
          openingBalanceDate = state.openingBalanceDate,
          creditTermsDays = termsDays,
          creditLimitPaise = creditLimitPaise
        )
        supplierRepository.saveSupplier(supplier)
        _formState.value = SupplierFormState()
        onSuccess()
      } catch (e: Exception) {
        _formState.value = state.copy(isSaving = false, errorMessage = "Error saving supplier: ${e.message}")
      }
    }
  }

  fun deleteSupplier(supplierId: Long) {
    viewModelScope.launch {
      supplierRepository.softDelete(supplierId)
    }
  }

  class Factory(
    private val supplierRepository: SupplierRepository,
    private val subledgerRepository: SubledgerRepository
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return SupplierViewModel(supplierRepository, subledgerRepository) as T
    }
  }
}
