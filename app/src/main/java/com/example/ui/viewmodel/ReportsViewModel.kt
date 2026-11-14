package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.CustomerRepository
import com.example.data.repository.ExpenseRepository
import com.example.data.repository.InventoryRepository
import com.example.data.repository.ProductRepository
import com.example.data.repository.PurchaseRepository
import com.example.data.repository.SalesRepository
import com.example.data.repository.SubledgerRepository
import com.example.data.repository.SupplierRepository
import com.example.domain.engine.BusinessReportEngine
import com.example.domain.engine.ProductSalesPerformance
import com.example.domain.engine.ProfitLossSummary
import com.example.domain.engine.ShopSalesPerformance
import com.example.domain.engine.SupplierPurchaseSummary
import com.example.domain.model.Customer
import com.example.domain.model.Expense
import com.example.domain.model.Product
import com.example.domain.model.ProductBatch
import com.example.domain.model.PurchaseInvoice
import com.example.domain.model.SalesInvoice
import com.example.domain.model.SubledgerEntry
import com.example.domain.model.Supplier
import com.example.service.ExportDocumentService
import com.example.service.ExportFormat
import com.example.service.ExportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ReportExportType(val title: String) {
  PROFIT_LOSS("Profit & Loss Statement"),
  SALES("Sales Report"),
  PURCHASE("Purchase Report"),
  CUSTOMER_STATEMENT("Customer Account Statement"),
  SUPPLIER_STATEMENT("Supplier Account Statement"),
  INVENTORY_FEFO("Inventory & FEFO Report")
}

data class ReportsUiState(
  val selectedTab: Int = 0, // 0: P&L, 1: Sales, 2: Purchases, 3: Products, 4: Shops, 5: Exports
  val selectedDateRange: String = "ALL", // "ALL", "TODAY", "WEEK", "MONTH"
  val filterStartDateMillis: Long? = null,
  val profitLossSummary: ProfitLossSummary = ProfitLossSummary(0, 0, 0, 0, 0, 0, 0),
  val productSales: List<ProductSalesPerformance> = emptyList(),
  val shopSales: List<ShopSalesPerformance> = emptyList(),
  val supplierPurchases: List<SupplierPurchaseSummary> = emptyList(),
  val salesInvoices: List<SalesInvoice> = emptyList(),
  val purchaseInvoices: List<PurchaseInvoice> = emptyList(),
  val products: List<Product> = emptyList(),
  val batches: List<ProductBatch> = emptyList(),
  val customers: List<Customer> = emptyList(),
  val suppliers: List<Supplier> = emptyList(),
  val expenses: List<Expense> = emptyList(),
  val subledgerEntries: List<SubledgerEntry> = emptyList(),
  val selectedCustomerId: Long? = null,
  val selectedSupplierId: Long? = null,
  val isExporting: Boolean = false,
  val latestExport: ExportResult.Success? = null,
  val exportMessage: String? = null
)

class ReportsViewModel(
  private val salesRepo: SalesRepository,
  private val purchaseRepo: PurchaseRepository,
  private val productRepo: ProductRepository,
  private val customerRepo: CustomerRepository,
  private val supplierRepo: SupplierRepository,
  private val inventoryRepo: InventoryRepository,
  private val expenseRepo: ExpenseRepository,
  private val subledgerRepo: SubledgerRepository
) : ViewModel() {

  private val _selectedTab = MutableStateFlow(0)
  val selectedTab = _selectedTab.asStateFlow()

  private val _dateRange = MutableStateFlow("ALL")
  val dateRange = _dateRange.asStateFlow()

  private val _selectedCustomerId = MutableStateFlow<Long?>(null)
  val selectedCustomerId = _selectedCustomerId.asStateFlow()

  private val _selectedSupplierId = MutableStateFlow<Long?>(null)
  val selectedSupplierId = _selectedSupplierId.asStateFlow()

  private val _isExporting = MutableStateFlow(false)
  private val _latestExport = MutableStateFlow<ExportResult.Success?>(null)
  private val _exportMessage = MutableStateFlow<String?>(null)
  val exportMessage = _exportMessage.asStateFlow()

  val uiState: StateFlow<ReportsUiState> = combine(
    combine(salesRepo.allSales, salesRepo.allSalesItems, expenseRepo.allExpenses) { sales, items, expenses ->
      Triple(sales, items, expenses)
    },
    combine(productRepo.allActiveProducts, inventoryRepo.allActiveBatches, purchaseRepo.allPurchases) { prods, allBatches, purchases ->
      Triple(prods, allBatches, purchases)
    },
    combine(customerRepo.allActiveCustomers, supplierRepo.allActiveSuppliers, subledgerRepo.allEntries) { custs, sups, ledger ->
      Triple(custs, sups, ledger)
    },
    combine(_selectedTab, _dateRange, _selectedCustomerId, _selectedSupplierId) { tab, range, custId, supId ->
      listOf(tab, range, custId, supId)
    },
    combine(_isExporting, _latestExport, _exportMessage) { exporting, latest, msg ->
      Triple(exporting, latest, msg)
    }
  ) { (sales, items, expenses), (prods, allBatches, purchases), (custs, sups, ledger), navList, exportTuple ->
    val tab = navList[0] as Int
    val range = navList[1] as String
    val custId = navList[2] as Long?
    val supId = navList[3] as Long?

    val startDate = BusinessReportEngine.computeStartDate(range)

    // Use ALL active batches (including sold-out batches) for true batch-level COGS lookup
    val batchCostMap = allBatches.associate { it.id to it.purchaseCostPerPiecePaise }
    // Current stocked batches (qty > 0) for inventory valuation & FEFO batch report
    val stockedBatches = allBatches.filter { it.currentQuantityPieces > 0 }

    val filteredSales = BusinessReportEngine.filterSalesByDateRange(sales, startDate, null)
    val filteredSaleIds = filteredSales.map { it.id }.toSet()
    val filteredSalesItems = items.filter { it.salesInvoiceId in filteredSaleIds }
    val filteredPurchases = BusinessReportEngine.filterPurchasesByDateRange(purchases, startDate, null)

    val plSummary = BusinessReportEngine.calculateProfitAndLoss(
      salesInvoices = sales,
      salesItems = items,
      batchCostMap = batchCostMap,
      expenses = expenses,
      startDate = startDate,
      endDate = null
    )

    val prodSales = BusinessReportEngine.calculateProductSales(
      products = prods,
      salesItems = filteredSalesItems,
      batchCostMap = batchCostMap
    )

    val custMap = custs.associate { it.id to it.shopName }
    val shpSales = BusinessReportEngine.calculateShopSales(
      customers = custMap,
      salesInvoices = filteredSales
    )

    val supMap = sups.associate { it.id to it.name }
    val supPurchases = BusinessReportEngine.calculateSupplierPurchases(
      suppliers = supMap,
      purchaseInvoices = filteredPurchases
    )

    ReportsUiState(
      selectedTab = tab,
      selectedDateRange = range,
      filterStartDateMillis = startDate,
      profitLossSummary = plSummary,
      productSales = prodSales,
      shopSales = shpSales,
      supplierPurchases = supPurchases,
      salesInvoices = filteredSales,
      purchaseInvoices = filteredPurchases,
      products = prods,
      batches = stockedBatches,
      customers = custs,
      suppliers = sups,
      expenses = expenses,
      subledgerEntries = ledger,
      selectedCustomerId = custId,
      selectedSupplierId = supId,
      isExporting = exportTuple.first,
      latestExport = exportTuple.second,
      exportMessage = exportTuple.third
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = ReportsUiState()
  )

  fun setTab(tab: Int) {
    _selectedTab.value = tab
  }

  fun setDateRange(range: String) {
    _dateRange.value = range
  }

  fun selectCustomerForStatement(customerId: Long?) {
    _selectedCustomerId.value = customerId
  }

  fun selectSupplierForStatement(supplierId: Long?) {
    _selectedSupplierId.value = supplierId
  }

  fun dismissLatestExport() {
    _latestExport.value = null
  }

  fun generateExport(context: Context, type: ReportExportType, format: ExportFormat) {
    if (_isExporting.value) return
    val state = uiState.value
    val dateLabel = BusinessReportEngine.formatDateRangeLabel(state.selectedDateRange)
    val startDate = state.filterStartDateMillis
    val custMap = state.customers.associate { it.id to it.shopName }
    val supMap = state.suppliers.associate { it.id to it.name }

    viewModelScope.launch {
      _isExporting.value = true
      try {
        val result = withContext(Dispatchers.IO) {
          val file = when (type) {
            ReportExportType.PROFIT_LOSS -> when (format) {
              ExportFormat.CSV -> ExportDocumentService.exportProfitLossCsvFile(context, state.profitLossSummary, dateLabel)
              ExportFormat.XLSX -> ExportDocumentService.generateProfitLossXlsx(context, state.profitLossSummary, dateLabel)
              ExportFormat.PDF -> ExportDocumentService.generateProfitLossPdf(context, state.profitLossSummary, dateLabel)
            }
            ReportExportType.SALES -> when (format) {
              ExportFormat.CSV -> ExportDocumentService.exportSalesReportCsvFile(context, state.salesInvoices, custMap, state.productSales, state.shopSales, dateLabel)
              ExportFormat.XLSX -> ExportDocumentService.generateSalesReportXlsx(context, state.salesInvoices, custMap, state.productSales, state.shopSales, dateLabel)
              ExportFormat.PDF -> ExportDocumentService.generateSalesReportPdf(context, state.salesInvoices, custMap, state.productSales, dateLabel)
            }
            ReportExportType.PURCHASE -> when (format) {
              ExportFormat.CSV -> ExportDocumentService.exportPurchaseReportCsvFile(context, state.purchaseInvoices, supMap, state.supplierPurchases, dateLabel)
              ExportFormat.XLSX -> ExportDocumentService.generatePurchaseReportXlsx(context, state.purchaseInvoices, supMap, state.supplierPurchases, dateLabel)
              ExportFormat.PDF -> ExportDocumentService.generatePurchaseReportPdf(context, state.purchaseInvoices, supMap, dateLabel)
            }
            ReportExportType.CUSTOMER_STATEMENT -> {
              val selectedCust = state.selectedCustomerId?.let { id -> state.customers.find { it.id == id } }
              when (format) {
                ExportFormat.CSV -> ExportDocumentService.exportCustomerStatementCsvFile(context, selectedCust, state.subledgerEntries, state.customers, startDate, null, dateLabel)
                ExportFormat.XLSX -> ExportDocumentService.generateCustomerStatementXlsx(context, selectedCust, state.subledgerEntries, state.customers, startDate, null, dateLabel)
                ExportFormat.PDF -> ExportDocumentService.generateCustomerStatementPdf(context, selectedCust, state.subledgerEntries, state.customers, startDate, null, dateLabel)
              }
            }
            ReportExportType.SUPPLIER_STATEMENT -> {
              val selectedSup = state.selectedSupplierId?.let { id -> state.suppliers.find { it.id == id } }
              when (format) {
                ExportFormat.CSV -> ExportDocumentService.exportSupplierStatementCsvFile(context, selectedSup, state.subledgerEntries, state.suppliers, startDate, null, dateLabel)
                ExportFormat.XLSX -> ExportDocumentService.generateSupplierStatementXlsx(context, selectedSup, state.subledgerEntries, state.suppliers, startDate, null, dateLabel)
                ExportFormat.PDF -> ExportDocumentService.generateSupplierStatementPdf(context, selectedSup, state.subledgerEntries, state.suppliers, startDate, null, dateLabel)
              }
            }
            ReportExportType.INVENTORY_FEFO -> when (format) {
              ExportFormat.CSV -> ExportDocumentService.exportInventoryCsvFile(context, state.products, state.batches)
              ExportFormat.XLSX -> ExportDocumentService.generateInventoryXlsx(context, state.products, state.batches)
              ExportFormat.PDF -> ExportDocumentService.generateInventoryPdf(context, state.products, state.batches)
            }
          }

          val recordCount = when (type) {
            ReportExportType.PROFIT_LOSS -> state.profitLossSummary.invoiceCount
            ReportExportType.SALES -> state.salesInvoices.size
            ReportExportType.PURCHASE -> state.purchaseInvoices.size
            ReportExportType.CUSTOMER_STATEMENT -> state.selectedCustomerId?.let { id ->
              state.subledgerEntries.count { !it.isReversed && it.partyId == id }
            } ?: state.customers.size
            ReportExportType.SUPPLIER_STATEMENT -> state.selectedSupplierId?.let { id ->
              state.subledgerEntries.count { !it.isReversed && it.partyId == id }
            } ?: state.suppliers.size
            ReportExportType.INVENTORY_FEFO -> state.batches.size
          }

          ExportResult.Success(
            file = file,
            format = format,
            reportTitle = type.title,
            recordCount = recordCount
          )
        }
        _latestExport.value = result
        _exportMessage.value = "${result.reportTitle} (${result.format.label}) ready (${result.file.length()} bytes)"
      } catch (e: Exception) {
        _exportMessage.value = "Export failed: ${e.message ?: "Unknown error"}"
      } finally {
        _isExporting.value = false
      }
    }
  }

  fun shareLatestExport(context: Context) {
    val artifact = _latestExport.value ?: return
    val shareResult = ExportDocumentService.shareFile(
      context = context,
      file = artifact.file,
      mimeType = artifact.format.mimeType,
      title = "IceTrack - ${artifact.reportTitle}"
    )
    shareResult.onFailure { err ->
      _exportMessage.value = "Unable to open share sheet: ${err.message}"
    }
  }

  fun saveLatestExportToUri(context: Context, uri: Uri) {
    val artifact = _latestExport.value ?: return
    viewModelScope.launch {
      val result = withContext(Dispatchers.IO) {
        ExportDocumentService.copyFileToDestinationUri(context, artifact.file, uri)
      }
      result.onSuccess { bytesWritten ->
        _exportMessage.value = "Saved ${artifact.file.name} to device ($bytesWritten bytes)"
      }.onFailure { err ->
        _exportMessage.value = "Save failed: ${err.message}"
      }
    }
  }

  // Direct string exports for backward compatibility and unit tests
  fun exportProfitLossCsv(): String {
    val state = uiState.value
    val dateLabel = BusinessReportEngine.formatDateRangeLabel(state.selectedDateRange)
    val csv = ExportDocumentService.generateProfitLossCsv(state.profitLossSummary, dateLabel)
    _exportMessage.value = "Profit & Loss CSV exported successfully (${csv.length} bytes)"
    return csv
  }

  fun exportInventoryCsv(): String {
    val state = uiState.value
    val csv = ExportDocumentService.generateInventoryCsv(state.products, state.batches)
    _exportMessage.value = "Inventory CSV exported successfully (${csv.length} bytes)"
    return csv
  }

  fun clearExportMessage() {
    _exportMessage.value = null
  }

  class Factory(
    private val salesRepo: SalesRepository,
    private val purchaseRepo: PurchaseRepository,
    private val productRepo: ProductRepository,
    private val customerRepo: CustomerRepository,
    private val supplierRepo: SupplierRepository,
    private val inventoryRepo: InventoryRepository,
    private val expenseRepo: ExpenseRepository,
    private val subledgerRepo: SubledgerRepository
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return ReportsViewModel(
        salesRepo,
        purchaseRepo,
        productRepo,
        customerRepo,
        supplierRepo,
        inventoryRepo,
        expenseRepo,
        subledgerRepo
      ) as T
    }
  }
}
