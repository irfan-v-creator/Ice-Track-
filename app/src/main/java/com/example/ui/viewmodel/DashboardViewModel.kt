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
  val archivedReceivablesPaise: Long = 0L,
  val archivedPayablesPaise: Long = 0L,
  val archivedCustomerAdvancesPaise: Long = 0L,
  val archivedSupplierAdvancesPaise: Long = 0L,
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

private data class PartiesAndAllocationsSnapshot(
  val archivedCustomerIds: Set<Long>,
  val archivedSupplierIds: Set<Long>,
  val totalCustAdv: Long,
  val archivedCustAdv: Long,
  val totalSupAdv: Long,
  val archivedSupAdv: Long
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

  private val partySnapshotFlow = combine(
    customerRepository.allCustomersIncludingArchived,
    supplierRepository.allSuppliersIncludingArchived,
    subledgerRepository.getAllActiveAllocationsByPartyType(PartyType.CUSTOMER),
    subledgerRepository.getAllActiveAllocationsByPartyType(PartyType.SUPPLIER)
  ) { allCusts, allSups, custAllocs, supAllocs ->
    val archivedCustIds = allCusts.filter { !it.isActive }.map { it.id }.toSet()
    val archivedSupIds = allSups.filter { !it.isActive }.map { it.id }.toSet()

    val activeCustAdvAllocs = custAllocs.filter { !it.isReversed && it.allocationType == PaymentAllocationType.ADVANCE }
    val activeSupAdvAllocs = supAllocs.filter { !it.isReversed && it.allocationType == PaymentAllocationType.ADVANCE }

    PartiesAndAllocationsSnapshot(
      archivedCustomerIds = archivedCustIds,
      archivedSupplierIds = archivedSupIds,
      totalCustAdv = activeCustAdvAllocs.sumOf { it.allocatedAmountPaise },
      archivedCustAdv = activeCustAdvAllocs.filter { it.partyId in archivedCustIds }.sumOf { it.allocatedAmountPaise },
      totalSupAdv = activeSupAdvAllocs.sumOf { it.allocatedAmountPaise },
      archivedSupAdv = activeSupAdvAllocs.filter { it.partyId in archivedSupIds }.sumOf { it.allocatedAmountPaise }
    )
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
    partySnapshotFlow
  ) { (counts, activeProducts, batches), entries, sales, purchases, partySnapshot ->
    val now = System.currentTimeMillis()
    val activeEntries = entries.filter { !it.isReversed }

    val customerEntriesByParty = activeEntries.filter { it.partyType == PartyType.CUSTOMER }.groupBy { it.partyId }
    var totalReceivables = 0L
    var archivedReceivables = 0L
    customerEntriesByParty.forEach { (custId, partyEntries) ->
      val bal = SubledgerCalculationEngine.calculateCustomerBalance(partyEntries).coerceAtLeast(0L)
      totalReceivables += bal
      if (custId in partySnapshot.archivedCustomerIds) {
        archivedReceivables += bal
      }
    }

    val supplierEntriesByParty = activeEntries.filter { it.partyType == PartyType.SUPPLIER }.groupBy { it.partyId }
    var totalPayables = 0L
    var archivedPayables = 0L
    supplierEntriesByParty.forEach { (supId, partyEntries) ->
      val bal = SubledgerCalculationEngine.calculateSupplierBalance(partyEntries).coerceAtLeast(0L)
      totalPayables += bal
      if (supId in partySnapshot.archivedSupplierIds) {
        archivedPayables += bal
      }
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
      totalCustomerAdvancesPaise = partySnapshot.totalCustAdv,
      totalSupplierAdvancesPaise = partySnapshot.totalSupAdv,
      archivedReceivablesPaise = archivedReceivables,
      archivedPayablesPaise = archivedPayables,
      archivedCustomerAdvancesPaise = partySnapshot.archivedCustAdv,
      archivedSupplierAdvancesPaise = partySnapshot.archivedSupAdv,
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
