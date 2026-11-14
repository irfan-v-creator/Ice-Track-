package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.SubledgerRepository
import com.example.data.repository.SupplierRepository
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.model.PartyType
import com.example.domain.model.PaymentAllocationType
import com.example.domain.model.Supplier
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

data class SupplierWithBalance(
  val supplier: Supplier,
  val outstandingPayablePaise: Long,
  val availableAdvancePaise: Long = 0L
)

data class SupplierFormState(
  val id: Long = 0,
  val createdAt: Long = System.currentTimeMillis(),
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

data class SupplierListSummary(
  val totalActivePayablesPaise: Long = 0L,
  val totalArchivedPayablesPaise: Long = 0L,
  val totalAllPayablesPaise: Long = 0L,
  val totalAllAdvancesPaise: Long = 0L,
  val archivedCount: Int = 0
)

class SupplierViewModel(
  private val supplierRepository: SupplierRepository,
  private val subledgerRepository: SubledgerRepository
) : ViewModel() {

  val searchQuery = MutableStateFlow("")
  val includeArchived = MutableStateFlow(false)

  val suppliersWithBalance: StateFlow<List<SupplierWithBalance>> = combine(
    supplierRepository.allSuppliersIncludingArchived,
    searchQuery,
    includeArchived,
    subledgerRepository.allEntries,
    subledgerRepository.getAllActiveAllocationsByPartyType(PartyType.SUPPLIER)
  ) { allSuppliers, query, showArchived, entries, allocations ->
    val activeEntries = entries.filter { !it.isReversed && it.partyType == PartyType.SUPPLIER }
    val entriesBySupplier = activeEntries.groupBy { it.partyId }
    val advancesBySupplier = allocations
      .filter { !it.isReversed && it.allocationType == PaymentAllocationType.ADVANCE }
      .groupBy { it.partyId }
      .mapValues { (_, list) -> list.sumOf { it.allocatedAmountPaise } }

    val q = query.trim().lowercase()
    allSuppliers
      .filter { sup ->
        (showArchived || sup.isActive) &&
          (q.isEmpty() ||
            sup.name.lowercase().contains(q) ||
            sup.contactPerson.lowercase().contains(q) ||
            sup.phoneNumber.lowercase().contains(q))
      }
      .map { supplier ->
        val partyEntries = entriesBySupplier[supplier.id] ?: emptyList()
        val payable = SubledgerCalculationEngine.calculateSupplierBalance(partyEntries)
        val advance = advancesBySupplier[supplier.id] ?: 0L
        SupplierWithBalance(supplier, payable, advance)
      }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val listSummary: StateFlow<SupplierListSummary> = combine(
    supplierRepository.allSuppliersIncludingArchived,
    subledgerRepository.allEntries,
    subledgerRepository.getAllActiveAllocationsByPartyType(PartyType.SUPPLIER)
  ) { allSuppliers, entries, allocations ->
    val activeSupplierIds = allSuppliers.filter { it.isActive }.map { it.id }.toSet()
    val entriesBySupplier = entries
      .filter { !it.isReversed && it.partyType == PartyType.SUPPLIER }
      .groupBy { it.partyId }

    var activePay = 0L
    var archivedPay = 0L
    entriesBySupplier.forEach { (supId, partyEntries) ->
      val due = SubledgerCalculationEngine.calculateSupplierBalance(partyEntries).coerceAtLeast(0L)
      if (supId in activeSupplierIds) {
        activePay += due
      } else {
        archivedPay += due
      }
    }
    val totalAdvances = allocations
      .filter { !it.isReversed && it.allocationType == PaymentAllocationType.ADVANCE }
      .sumOf { it.allocatedAmountPaise }

    SupplierListSummary(
      totalActivePayablesPaise = activePay,
      totalArchivedPayablesPaise = archivedPay,
      totalAllPayablesPaise = activePay + archivedPay,
      totalAllAdvancesPaise = totalAdvances,
      archivedCount = allSuppliers.count { !it.isActive }
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SupplierListSummary())

  private val _formState = MutableStateFlow(SupplierFormState())
  val formState: StateFlow<SupplierFormState> = _formState.asStateFlow()

  fun onSearchQueryChanged(query: String) {
    searchQuery.value = query
  }

  fun toggleIncludeArchived() {
    includeArchived.value = !includeArchived.value
  }

  fun openAddDialog() {
    _formState.value = SupplierFormState()
  }

  fun openEditDialog(supplier: Supplier) {
    _formState.value = SupplierFormState(
      id = supplier.id,
      createdAt = supplier.createdAt,
      name = supplier.name,
      contactPerson = supplier.contactPerson,
      phoneNumber = supplier.phoneNumber,
      address = supplier.address,
      openingBalanceRupees = SubledgerCalculationEngine.formatPaiseForInput(supplier.openingBalancePaise),
      openingBalanceDate = supplier.openingBalanceDate,
      creditTermsDays = supplier.creditTermsDays.toString(),
      creditLimitRupees = SubledgerCalculationEngine.formatPaiseForInput(supplier.creditLimitPaise)
    )
  }

  fun updateForm(transform: (SupplierFormState) -> SupplierFormState) {
    _formState.value = transform(_formState.value)
  }

  fun saveSupplier(onSuccess: () -> Unit) {
    val state = _formState.value
    if (state.isSaving) return

    if (state.name.isBlank()) {
      _formState.value = state.copy(errorMessage = "Supplier name cannot be empty")
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

    val termsDays = if (state.creditTermsDays.isBlank()) 15 else state.creditTermsDays.trim().toIntOrNull()
    if (termsDays == null || termsDays < 0) {
      _formState.value = state.copy(errorMessage = "Credit terms must be 0 or greater days")
      return
    }

    viewModelScope.launch {
      _formState.value = _formState.value.copy(isSaving = true, errorMessage = null)
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
          creditLimitPaise = creditLimitPaise,
          createdAt = state.createdAt
        )
        supplierRepository.saveSupplier(supplier)
        _formState.value = SupplierFormState()
        onSuccess()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _formState.value = _formState.value.copy(isSaving = false, errorMessage = "Error saving supplier: ${e.message}")
      }
    }
  }

  fun deleteSupplier(supplierId: Long) {
    viewModelScope.launch {
      try {
        supplierRepository.softDelete(supplierId)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _formState.value = _formState.value.copy(errorMessage = "Error deleting supplier: ${e.message}")
      }
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
