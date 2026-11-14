package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.InventoryRepository
import com.example.data.repository.ProductRepository
import com.example.data.repository.SupplierRepository
import com.example.domain.engine.FefoInventoryEngine
import com.example.domain.engine.InventoryValuationSummary
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.engine.UnitConversionEngine
import com.example.domain.model.Product
import com.example.domain.model.ProductSupplier
import com.example.domain.model.Supplier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProductFormState(
  val id: Long = 0,
  val createdAt: Long = System.currentTimeMillis(),
  val name: String = "",
  val sku: String = "",
  val category: String = "Sticks/Bars",
  val purchasePriceRupees: String = "",
  val sellingPriceRupees: String = "",
  val minStockPieces: String = "10",
  val piecesPerBox: String = "10",
  val boxesPerCarton: String = "10",
  val supplierCosts: List<ProductSupplier> = emptyList(),
  val isLoadingDetails: Boolean = false,
  val errorMessage: String? = null,
  val isSaving: Boolean = false
)

class ProductViewModel(
  private val productRepository: ProductRepository,
  private val supplierRepository: SupplierRepository,
  private val inventoryRepository: InventoryRepository? = null
) : ViewModel() {

  val searchQuery = MutableStateFlow("")
  val selectedCategory = MutableStateFlow("All")

  val allSuppliers: StateFlow<List<Supplier>> = supplierRepository.allActiveSuppliers
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val inventoryValuation: StateFlow<InventoryValuationSummary> =
    (inventoryRepository?.allStockedBatches ?: flowOf(emptyList()))
      .map { batches -> FefoInventoryEngine.calculateInventoryValuation(batches) }
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        InventoryValuationSummary(0, 0, 0L, 0L)
      )

  @OptIn(ExperimentalCoroutinesApi::class)
  val products: StateFlow<List<Product>> = searchQuery.flatMapLatest { query ->
    productRepository.searchProducts(query)
  }.combine(selectedCategory) { list, category ->
    if (category == "All") list else list.filter { it.category == category }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _formState = MutableStateFlow(ProductFormState())
  val formState: StateFlow<ProductFormState> = _formState.asStateFlow()

  fun onSearchQueryChanged(newQuery: String) {
    searchQuery.value = newQuery
  }

  fun onCategorySelected(category: String) {
    selectedCategory.value = category
  }

  fun openAddDialog() {
    _formState.value = ProductFormState()
  }

  fun openEditDialog(product: Product) {
    _formState.value = ProductFormState(
      id = product.id,
      createdAt = product.createdAt,
      name = product.name,
      sku = product.sku,
      category = product.category,
      purchasePriceRupees = SubledgerCalculationEngine.formatPaiseForInput(product.defaultPurchasePricePaise),
      sellingPriceRupees = SubledgerCalculationEngine.formatPaiseForInput(product.defaultSellingPricePaise),
      minStockPieces = product.minStockLevelPieces.toString(),
      isLoadingDetails = true
    )

    viewModelScope.launch {
      try {
        val existingUnit = productRepository.getUnitForProductSync(product.id)
        val existingSuppliers = productRepository.getSuppliersForProductSync(product.id)
        if (_formState.value.id == product.id) {
          _formState.value = _formState.value.copy(
            piecesPerBox = (existingUnit?.piecesPerBox ?: 10).toString(),
            boxesPerCarton = (existingUnit?.boxesPerCarton ?: 10).toString(),
            supplierCosts = existingSuppliers,
            isLoadingDetails = false
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        if (_formState.value.id == product.id) {
          _formState.value = _formState.value.copy(
            isLoadingDetails = false,
            errorMessage = "Failed to load unit conversions: ${e.message}"
          )
        }
      }
    }
  }

  fun updateForm(transform: (ProductFormState) -> ProductFormState) {
    _formState.value = transform(_formState.value)
  }

  fun saveProduct(onSuccess: () -> Unit) {
    val state = _formState.value
    if (state.isSaving || state.isLoadingDetails) return

    if (state.name.isBlank()) {
      _formState.value = state.copy(errorMessage = "Product name cannot be empty")
      return
    }
    if (state.sku.isBlank()) {
      _formState.value = state.copy(errorMessage = "SKU / Code cannot be empty")
      return
    }

    val piecesPerBox = state.piecesPerBox.trim().toIntOrNull()
    val boxesPerCarton = state.boxesPerCarton.trim().toIntOrNull()
    if (piecesPerBox == null || boxesPerCarton == null) {
      _formState.value = state.copy(errorMessage = "Unit ratios must be valid whole numbers (>= 1)")
      return
    }

    try {
      UnitConversionEngine.validateRatios(piecesPerBox, boxesPerCarton)
    } catch (e: Exception) {
      _formState.value = state.copy(errorMessage = e.message ?: "Invalid unit conversion ratios")
      return
    }

    val purchasePaise = SubledgerCalculationEngine.parseRupeesToPaiseOrNull(
      state.purchasePriceRupees,
      allowNegative = false,
      allowEmptyAsZero = true
    )
    if (purchasePaise == null) {
      _formState.value = state.copy(errorMessage = "Invalid purchase price. Enter a non-negative amount with up to 2 decimal places.")
      return
    }

    val sellingPaise = SubledgerCalculationEngine.parseRupeesToPaiseOrNull(
      state.sellingPriceRupees,
      allowNegative = false,
      allowEmptyAsZero = true
    )
    if (sellingPaise == null) {
      _formState.value = state.copy(errorMessage = "Invalid selling price. Enter a non-negative amount with up to 2 decimal places.")
      return
    }

    val minStock = if (state.minStockPieces.isBlank()) 0 else state.minStockPieces.trim().toIntOrNull()
    if (minStock == null || minStock < 0) {
      _formState.value = state.copy(errorMessage = "Minimum stock alert must be 0 or greater")
      return
    }

    viewModelScope.launch {
      _formState.value = _formState.value.copy(isSaving = true, errorMessage = null)
      try {
        val product = Product(
          id = state.id,
          name = state.name.trim(),
          sku = state.sku.trim(),
          category = state.category,
          defaultPurchasePricePaise = purchasePaise,
          defaultSellingPricePaise = sellingPaise,
          minStockLevelPieces = minStock,
          createdAt = state.createdAt
        )
        productRepository.saveProductWithUnit(
          product = product,
          piecesPerBox = piecesPerBox,
          boxesPerCarton = boxesPerCarton,
          supplierCosts = state.supplierCosts
        )
        _formState.value = ProductFormState()
        onSuccess()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _formState.value = _formState.value.copy(isSaving = false, errorMessage = "Error saving product: ${e.message}")
      }
    }
  }

  fun deleteProduct(productId: Long) {
    viewModelScope.launch {
      try {
        productRepository.softDelete(productId)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _formState.value = _formState.value.copy(errorMessage = "Error deleting product: ${e.message}")
      }
    }
  }

  class Factory(
    private val productRepository: ProductRepository,
    private val supplierRepository: SupplierRepository,
    private val inventoryRepository: InventoryRepository? = null
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return ProductViewModel(productRepository, supplierRepository, inventoryRepository) as T
    }
  }
}
