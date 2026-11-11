package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ProductRepository
import com.example.data.repository.SupplierRepository
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.engine.UnitConversionEngine
import com.example.domain.model.Product
import com.example.domain.model.ProductSupplier
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

data class ProductFormState(
  val id: Long = 0,
  val name: String = "",
  val sku: String = "",
  val category: String = "Sticks/Bars",
  val purchasePriceRupees: String = "",
  val sellingPriceRupees: String = "",
  val minStockPieces: String = "10",
  val piecesPerBox: String = "10",
  val boxesPerCarton: String = "10",
  val supplierCosts: List<ProductSupplier> = emptyList(),
  val errorMessage: String? = null,
  val isSaving: Boolean = false
)

class ProductViewModel(
  private val productRepository: ProductRepository,
  private val supplierRepository: SupplierRepository
) : ViewModel() {

  val searchQuery = MutableStateFlow("")
  val selectedCategory = MutableStateFlow("All")

  val allSuppliers: StateFlow<List<Supplier>> = supplierRepository.allActiveSuppliers
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    viewModelScope.launch {
      _formState.value = ProductFormState(
        id = product.id,
        name = product.name,
        sku = product.sku,
        category = product.category,
        purchasePriceRupees = (product.defaultPurchasePricePaise / 100.0).toString(),
        sellingPriceRupees = (product.defaultSellingPricePaise / 100.0).toString(),
        minStockPieces = product.minStockLevelPieces.toString()
      )
    }
  }

  fun updateForm(transform: (ProductFormState) -> ProductFormState) {
    _formState.value = transform(_formState.value)
  }

  fun saveProduct(onSuccess: () -> Unit) {
    val state = _formState.value
    if (state.name.isBlank()) {
      _formState.value = state.copy(errorMessage = "Product name cannot be empty")
      return
    }
    if (state.sku.isBlank()) {
      _formState.value = state.copy(errorMessage = "SKU / Code cannot be empty")
      return
    }

    val piecesPerBox = state.piecesPerBox.toIntOrNull() ?: 0
    val boxesPerCarton = state.boxesPerCarton.toIntOrNull() ?: 0

    try {
      UnitConversionEngine.validateRatios(piecesPerBox, boxesPerCarton)
    } catch (e: Exception) {
      _formState.value = state.copy(errorMessage = e.message ?: "Invalid unit conversion ratios")
      return
    }

    val purchasePaise = SubledgerCalculationEngine.parseRupeesToPaise(state.purchasePriceRupees)
    val sellingPaise = SubledgerCalculationEngine.parseRupeesToPaise(state.sellingPriceRupees)
    val minStock = state.minStockPieces.toIntOrNull() ?: 0

    viewModelScope.launch {
      _formState.value = state.copy(isSaving = true, errorMessage = null)
      try {
        val product = Product(
          id = state.id,
          name = state.name.trim(),
          sku = state.sku.trim(),
          category = state.category,
          defaultPurchasePricePaise = purchasePaise,
          defaultSellingPricePaise = sellingPaise,
          minStockLevelPieces = minStock
        )
        productRepository.saveProductWithUnit(
          product = product,
          piecesPerBox = piecesPerBox,
          boxesPerCarton = boxesPerCarton,
          supplierCosts = state.supplierCosts
        )
        _formState.value = ProductFormState()
        onSuccess()
      } catch (e: Exception) {
        _formState.value = state.copy(isSaving = false, errorMessage = "Error saving product: ${e.message}")
      }
    }
  }

  fun deleteProduct(productId: Long) {
    viewModelScope.launch {
      productRepository.softDelete(productId)
    }
  }

  class Factory(
    private val productRepository: ProductRepository,
    private val supplierRepository: SupplierRepository
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return ProductViewModel(productRepository, supplierRepository) as T
    }
  }
}
