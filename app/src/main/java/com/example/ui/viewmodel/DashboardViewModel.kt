package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.CustomerRepository
import com.example.data.repository.InventoryRepository
import com.example.data.repository.ProductRepository
import com.example.data.repository.PurchaseRepository
import com.example.data.repository.SalesRepository
import com.example.data.repository.SubledgerRepository
import com.example.data.repository.SupplierRepository
import com.example.domain.engine.BusinessReportEngine
import com.example.domain.engine.FefoInventoryEngine
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.model.PartyType
import com.example.domain.model.PaymentAllocationType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
  val productCount: Int = 0,
  val supplierCount: Int = 0,
  val customerCount: Int = 0,
  val totalReceivablesPaise: Long = 0L,
  val totalPayablesPaise: Long = 0L,
  val totalCustomerAdvancesPaise: Long = 0L,
  val totalSupplierAdvancesPaise: Long = 0L,
  val todaySalesPaise: Long = 0L,
  val todaySalesCount: Int = 0,
  val todayPurchasesPaise: Long = 0L,
  val sellableStockValuePaise: Long = 0L,
  val expiredStockValuePaise: Long = 0L,
  val lowStockCount: Int = 0,
  val expiringBatchCount: Int = 0,
  val businessName: String = "IceTrack Wholesale"
)

private data class MasterCounts(
  val products: Int,
  val suppliers: Int,
  val customers: Int
)

class DashboardViewModel(
  productRepository: ProductRepository,
  supplierRepository: SupplierRepository,
  customerRepository: CustomerRepository,
  subledgerRepository: SubledgerRepository,
  salesRepository: SalesRepository,
  purchaseRepository: PurchaseRepository,
  inventoryRepository: InventoryRepository
) : ViewModel() {

  private val masterCountsFlow = combine(
    productRepository.activeProductCount,
    supplierRepository.activeSupplierCount,
    customerRepository.activeCustomerCount
  ) { p, s, c -> MasterCounts(p, s, c) }

  private val advancesFlow = combine(
    subledgerRepository.getAllActiveAllocationsByPartyType(PartyType.CUSTOMER),
    subledgerRepository.getAllActiveAllocationsByPartyType(PartyType.SUPPLIER)
  ) { custAllocs, supAllocs ->
    val custAdv = custAllocs
      .filter { !it.isReversed && it.allocationType == PaymentAllocationType.ADVANCE }
      .sumOf { it.allocatedAmountPaise }
    val supAdv = supAllocs
      .filter { !it.isReversed && it.allocationType == PaymentAllocationType.ADVANCE }
      .sumOf { it.allocatedAmountPaise }
    custAdv to supAdv
  }

  private val catalogAndStockFlow = combine(
    masterCountsFlow,
    productRepository.allActiveProducts,
    inventoryRepository.allStockedBatches
  ) { counts, activeProducts, batches ->
    Triple(counts, activeProducts, batches)
  }

  val uiState: StateFlow<DashboardUiState> = combine(
    catalogAndStockFlow,
    subledgerRepository.allEntries,
    salesRepository.allSales,
    purchaseRepository.allPurchases,
    advancesFlow
  ) { (counts, activeProducts, batches), entries, sales, purchases, (custAdv, supAdv) ->
    val now = System.currentTimeMillis()
    val activeEntries = entries.filter { !it.isReversed }

    val customerEntriesByParty = activeEntries.filter { it.partyType == PartyType.CUSTOMER }.groupBy { it.partyId }
    val totalReceivables = customerEntriesByParty.values.sumOf { partyEntries ->
      SubledgerCalculationEngine.calculateCustomerBalance(partyEntries).coerceAtLeast(0L)
    }

    val supplierEntriesByParty = activeEntries.filter { it.partyType == PartyType.SUPPLIER }.groupBy { it.partyId }
    val totalPayables = supplierEntriesByParty.values.sumOf { partyEntries ->
      SubledgerCalculationEngine.calculateSupplierBalance(partyEntries).coerceAtLeast(0L)
    }

    val todayStart = BusinessReportEngine.computeStartDate("TODAY", now) ?: (now - 86400000L)
    val todaySalesList = BusinessReportEngine.filterSalesByDateRange(sales, todayStart, null)
    val todaySalesTotal = todaySalesList.sumOf { it.totalAmountPaise }

    val todayPurchasesList = BusinessReportEngine.filterPurchasesByDateRange(purchases, todayStart, null)
    val todayPurchasesTotal = todayPurchasesList.sumOf { it.totalAmountPaise }

    val valuation = FefoInventoryEngine.calculateInventoryValuation(batches, now)
    val lowStock = FefoInventoryEngine.computeLowStockCount(activeProducts, batches, now)
    val expiringBatches = FefoInventoryEngine.getExpiringBatches(batches, 7, now).size

    DashboardUiState(
      productCount = counts.products,
      supplierCount = counts.suppliers,
      customerCount = counts.customers,
      totalReceivablesPaise = totalReceivables,
      totalPayablesPaise = totalPayables,
      totalCustomerAdvancesPaise = custAdv,
      totalSupplierAdvancesPaise = supAdv,
      todaySalesPaise = todaySalesTotal,
      todaySalesCount = todaySalesList.size,
      todayPurchasesPaise = todayPurchasesTotal,
      sellableStockValuePaise = valuation.sellableValuePaise,
      expiredStockValuePaise = valuation.expiredValuePaise,
      lowStockCount = lowStock,
      expiringBatchCount = expiringBatches
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = DashboardUiState()
  )

  class Factory(
    private val productRepository: ProductRepository,
    private val supplierRepository: SupplierRepository,
    private val customerRepository: CustomerRepository,
    private val subledgerRepository: SubledgerRepository,
    private val salesRepository: SalesRepository,
    private val purchaseRepository: PurchaseRepository,
    private val inventoryRepository: InventoryRepository
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return DashboardViewModel(
        productRepository,
        supplierRepository,
        customerRepository,
        subledgerRepository,
        salesRepository,
        purchaseRepository,
        inventoryRepository
      ) as T
    }
  }
}
