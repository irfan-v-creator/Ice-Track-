package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.InventoryRepository
import com.example.data.repository.ProductRepository
import com.example.domain.engine.FefoInventoryEngine
import com.example.domain.model.Product
import com.example.domain.model.ProductBatch
import com.example.domain.model.ProductUnit
import com.example.domain.model.StockMovement
import com.example.domain.model.StockMovementType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProductStockSummary(
  val product: Product,
  val unit: ProductUnit?,
  val totalPiecesInStock: Int,
  val batches: List<ProductBatch>,
  val isLowStock: Boolean,
  val hasExpiredStock: Boolean,
  val nearExpiryBatchCount: Int
)

data class InventoryUiState(
  val stockSummaries: List<ProductStockSummary> = emptyList(),
  val recentMovements: List<StockMovement> = emptyList(),
  val totalStockValuePaise: Long = 0L,
  val lowStockItemCount: Int = 0,
  val expiringBatchCount: Int = 0,
  val searchQuery: String = "",
  val selectedTab: Int = 0 // 0: Stock by Product, 1: Batches & Expiry, 2: Movement History
)

class InventoryViewModel(
  private val inventoryRepo: InventoryRepository,
  private val productRepo: ProductRepository
) : ViewModel() {

  private val _searchQuery = MutableStateFlow("")
  val searchQuery = _searchQuery.asStateFlow()

  private val _selectedTab = MutableStateFlow(0)
  val selectedTab = _selectedTab.asStateFlow()

  val uiState: StateFlow<InventoryUiState> = combine(
    productRepo.allActiveProducts,
    inventoryRepo.allStockedBatches,
    inventoryRepo.recentMovements,
    _searchQuery,
    _selectedTab
  ) { products, allBatches, movements, query, tab ->
    val now = System.currentTimeMillis()
    val batchesByProduct = allBatches.groupBy { it.productId }

    val summaries = products.map { product ->
      val batches = batchesByProduct[product.id] ?: emptyList()
      val totalPieces = batches.sumOf { it.currentQuantityPieces }
      val isLowStock = totalPieces <= product.minStockLevelPieces
      val hasExpired = batches.any { it.isExpired }
      val nearExpiryCount = FefoInventoryEngine.getExpiringBatches(batches, 7, now).size

      ProductStockSummary(
        product = product,
        unit = null,
        totalPiecesInStock = totalPieces,
        batches = batches,
        isLowStock = isLowStock,
        hasExpiredStock = hasExpired,
        nearExpiryBatchCount = nearExpiryCount
      )
    }

    val filteredSummaries = if (query.isBlank()) {
      summaries
    } else {
      summaries.filter {
        it.product.name.contains(query, ignoreCase = true) ||
        it.product.sku.contains(query, ignoreCase = true) ||
        it.product.category.contains(query, ignoreCase = true)
      }
    }

    val totalStockValue = allBatches.fold(0L) { acc, b ->
      acc + (b.currentQuantityPieces.toLong() * b.purchaseCostPerPiecePaise)
    }

    val lowStockCount = summaries.count { it.isLowStock }
    val totalExpiring = summaries.sumOf { it.nearExpiryBatchCount }

    InventoryUiState(
      stockSummaries = filteredSummaries,
      recentMovements = movements,
      totalStockValuePaise = totalStockValue,
      lowStockItemCount = lowStockCount,
      expiringBatchCount = totalExpiring,
      searchQuery = query,
      selectedTab = tab
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = InventoryUiState()
  )

  fun setSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun setSelectedTab(tab: Int) {
    _selectedTab.value = tab
  }

  fun adjustStock(batchId: Long, quantityPieces: Int, reason: String, onDone: () -> Unit = {}) {
    viewModelScope.launch {
      inventoryRepo.adjustStock(
        batchId = batchId,
        adjustmentPieces = quantityPieces,
        reason = reason,
        movementType = StockMovementType.STOCK_ADJUSTMENT
      )
      onDone()
    }
  }

  class Factory(
    private val inventoryRepo: InventoryRepository,
    private val productRepo: ProductRepository
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return InventoryViewModel(inventoryRepo, productRepo) as T
    }
  }
}
