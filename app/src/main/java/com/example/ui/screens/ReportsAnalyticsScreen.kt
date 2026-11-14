package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.engine.BusinessReportEngine
import com.example.domain.engine.ProductSalesPerformance
import com.example.domain.engine.ProfitLossSummary
import com.example.domain.engine.ShopSalesPerformance
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.model.Customer
import com.example.domain.model.PaymentStatus
import com.example.domain.model.ProductBatch
import com.example.domain.model.PurchaseInvoice
import com.example.domain.model.SalesInvoice
import com.example.domain.model.Supplier
import com.example.service.ExportFormat
import com.example.service.ExportResult
import com.example.ui.components.EmptyStateView
import com.example.ui.theme.FigmaAmber
import com.example.ui.theme.FigmaBackground
import com.example.ui.theme.FigmaInk
import com.example.ui.theme.FigmaLine
import com.example.ui.theme.FigmaMuted
import com.example.ui.theme.FigmaNavy
import com.example.ui.theme.FigmaRed
import com.example.ui.theme.FigmaSoftTeal
import com.example.ui.theme.FigmaTeal
import com.example.ui.theme.FigmaWhite
import com.example.ui.viewmodel.ReportExportType
import com.example.ui.viewmodel.ReportsViewModel

@Composable
fun ReportsAnalyticsScreen(
  viewModel: ReportsViewModel,
  modifier: Modifier = Modifier
) {
  val state by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }

  val saveDocumentLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("*/*")
  ) { uri: Uri? ->
    if (uri != null) {
      viewModel.saveLatestExportToUri(context, uri)
    }
  }

  LaunchedEffect(state.exportMessage) {
    state.exportMessage?.let {
      snackbarHostState.showSnackbar(it)
      viewModel.clearExportMessage()
    }
  }

  Scaffold(
    containerColor = FigmaBackground,
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    modifier = modifier.fillMaxSize()
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(FigmaBackground)
        .padding(padding)
        .padding(horizontal = 24.dp, vertical = 12.dp)
        .testTag("reports_screen"),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Header Headline
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Reports & Analytics",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = FigmaInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = "Profit & Loss, Sales, Purchases & Statements",
            style = MaterialTheme.typography.bodySmall,
            color = FigmaMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      // Date Range Filters: ALL, TODAY, WEEK, MONTH
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        listOf(
          "ALL" to "All Time",
          "TODAY" to "Today",
          "WEEK" to "7 Days",
          "MONTH" to "30 Days"
        ).forEach { (key, label) ->
          val selected = state.selectedDateRange == key
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (selected) FigmaNavy else FigmaWhite,
            modifier = Modifier
              .weight(1f)
              .defaultMinSize(minHeight = 40.dp)
              .border(1.dp, if (selected) FigmaNavy else FigmaLine, RoundedCornerShape(8.dp))
              .clickable { viewModel.setDateRange(key) }
              .testTag("date_filter_$key")
          ) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            ) {
              Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) FigmaWhite else FigmaInk,
                maxLines = 1,
                softWrap = false
              )
            }
          }
        }
      }

      // Scrollable Tab navigation
      val tabTitles = listOf("Profit & Loss", "Sales", "Purchases", "Products", "Shops", "Exports")
      ScrollableTabRow(
        selectedTabIndex = state.selectedTab.coerceIn(0, tabTitles.lastIndex),
        containerColor = FigmaWhite,
        contentColor = FigmaTeal,
        edgePadding = 0.dp,
        indicator = { tabPositions ->
          val idx = state.selectedTab.coerceIn(0, tabPositions.lastIndex)
          TabRowDefaults.SecondaryIndicator(
            Modifier.tabIndicatorOffset(tabPositions[idx]),
            color = FigmaTeal
          )
        },
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, FigmaLine, RoundedCornerShape(8.dp))
      ) {
        tabTitles.forEachIndexed { index, title ->
          Tab(
            selected = state.selectedTab == index,
            onClick = { viewModel.setTab(index) },
            modifier = Modifier.testTag("report_tab_$index"),
            text = {
              Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
              )
            }
          )
        }
      }

      if (state.isExporting) {
        LinearProgressIndicator(
          color = FigmaTeal,
          trackColor = FigmaSoftTeal,
          modifier = Modifier.fillMaxWidth()
        )
      }

      // Tab content
      Box(modifier = Modifier.weight(1f)) {
        when (state.selectedTab) {
          0 -> ProfitLossReportTab(summary = state.profitLossSummary)
          1 -> SalesOverviewTab(
            salesInvoices = state.salesInvoices,
            customerNames = state.customers.associate { it.id to it.shopName }
          )
          2 -> PurchasesOverviewTab(
            purchaseInvoices = state.purchaseInvoices,
            supplierNames = state.suppliers.associate { it.id to it.name }
          )
          3 -> ProductPerformanceTab(products = state.productSales)
          4 -> ShopPerformanceTab(shops = state.shopSales)
          5 -> ExportDocumentsTab(
            dateRangeLabel = BusinessReportEngine.formatDateRangeLabel(state.selectedDateRange),
            profitLossSummary = state.profitLossSummary,
            salesInvoices = state.salesInvoices,
            purchaseInvoices = state.purchaseInvoices,
            customers = state.customers,
            suppliers = state.suppliers,
            batches = state.batches,
            sellableStockValuePaise = state.sellableStockValuePaise,
            expiredStockValuePaise = state.expiredStockValuePaise,
            selectedCustomerId = state.selectedCustomerId,
            selectedSupplierId = state.selectedSupplierId,
            onSelectCustomer = viewModel::selectCustomerForStatement,
            onSelectSupplier = viewModel::selectSupplierForStatement,
            onExport = { type, format ->
              viewModel.generateExport(context, type, format)
            }
          )
        }
      }
    }
  }

  // Export Ready Save & Share Dialog
  state.latestExport?.let { artifact ->
    ExportReadyDialog(
      artifact = artifact,
      onShare = { viewModel.shareLatestExport(context) },
      onSaveToDevice = { saveDocumentLauncher.launch(artifact.file.name) },
      onDismiss = { viewModel.dismissLatestExport() }
    )
  }
}

@Composable
fun ExportReadyDialog(
  artifact: ExportResult.Success,
  onShare: () -> Unit,
  onSaveToDevice: () -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = FigmaWhite,
    modifier = Modifier.testTag("export_ready_dialog"),
    icon = {
      Icon(
        imageVector = Icons.Default.CheckCircle,
        contentDescription = null,
        tint = FigmaTeal,
        modifier = Modifier.size(32.dp)
      )
    },
    title = {
      Text(
        text = "${artifact.format.label} Export Ready",
        fontWeight = FontWeight.Bold,
        color = FigmaInk
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = artifact.reportTitle,
          fontWeight = FontWeight.SemiBold,
          fontSize = 14.sp,
          color = FigmaNavy
        )
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = FigmaBackground,
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FigmaLine, RoundedCornerShape(8.dp))
        ) {
          Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = "File: ${artifact.file.name}",
              fontSize = 12.sp,
              color = FigmaInk,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = "Size: ${artifact.file.length()} bytes • Records: ${artifact.recordCount}",
              fontSize = 11.sp,
              color = FigmaMuted
            )
          }
        }
        Text(
          text = "Save directly to your device Downloads folder or share via WhatsApp, Email, Excel or PDF Viewer.",
          fontSize = 12.sp,
          color = FigmaMuted
        )
      }
    },
    confirmButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
          onClick = onSaveToDevice,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag("export_save_button")
        ) {
          Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = FigmaNavy)
          Spacer(modifier = Modifier.width(4.dp))
          Text("Save", color = FigmaNavy, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Button(
          onClick = onShare,
          colors = ButtonDefaults.buttonColors(containerColor = FigmaTeal, contentColor = FigmaWhite),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag("export_share_button")
        ) {
          Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Share", fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Close", color = FigmaMuted)
      }
    }
  )
}

@Composable
fun ProfitLossReportTab(summary: ProfitLossSummary) {
  val scrollState = rememberScrollState()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(scrollState),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // Net Profit Hero Card
    Card(
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = FigmaWhite),
      elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, FigmaLine, RoundedCornerShape(12.dp))
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Net Business Profit", style = MaterialTheme.typography.bodySmall, color = FigmaMuted)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = SubledgerCalculationEngine.formatPaise(summary.netProfitPaise),
          style = MaterialTheme.typography.headlineLarge,
          fontWeight = FontWeight.Bold,
          color = if (summary.netProfitPaise >= 0) FigmaTeal else FigmaRed
        )
        Text(
          text = "Calculated from Gross Profit minus Operating Expenses",
          style = MaterialTheme.typography.labelSmall,
          color = FigmaMuted
        )
      }
    }

    // Financial Breakdown Table
    Card(
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = FigmaWhite),
      elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, FigmaLine, RoundedCornerShape(12.dp))
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = "Operational Financial Statement", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = FigmaInk)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(text = "Total Sales Revenue", color = FigmaMuted, fontSize = 13.sp)
          Text(text = SubledgerCalculationEngine.formatPaise(summary.totalSalesRevenuePaise), fontWeight = FontWeight.Bold, color = FigmaInk)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(text = "Cost of Goods Sold (COGS)", color = FigmaMuted, fontSize = 13.sp)
          Text(text = "- ${SubledgerCalculationEngine.formatPaise(summary.totalCogsPaise)}", fontWeight = FontWeight.SemiBold, color = FigmaRed)
        }

        HorizontalDivider(thickness = 0.5.dp, color = FigmaLine)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(text = "Gross Profit", fontWeight = FontWeight.Bold, color = FigmaInk, fontSize = 13.sp)
          Text(text = SubledgerCalculationEngine.formatPaise(summary.grossProfitPaise), fontWeight = FontWeight.Bold, color = FigmaTeal)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(text = "Business Operating Expenses", color = FigmaMuted, fontSize = 13.sp)
          Text(text = "- ${SubledgerCalculationEngine.formatPaise(summary.totalOperatingExpensesPaise)}", fontWeight = FontWeight.SemiBold, color = FigmaRed)
        }

        HorizontalDivider(thickness = 1.dp, color = FigmaLine)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(text = "Net Profit / (Loss)", fontWeight = FontWeight.Bold, color = FigmaInk, fontSize = 14.sp)
          Text(
            text = SubledgerCalculationEngine.formatPaise(summary.netProfitPaise),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = if (summary.netProfitPaise >= 0) FigmaTeal else FigmaRed
          )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(text = "Total Pieces Sold: ${summary.totalPiecesSold} Pcs", fontSize = 11.sp, color = FigmaMuted)
          Text(text = "Invoices: ${summary.invoiceCount}", fontSize = 11.sp, color = FigmaMuted)
        }
      }
    }
  }
}

@Composable
fun SalesOverviewTab(
  salesInvoices: List<SalesInvoice>,
  customerNames: Map<Long, String>
) {
  if (salesInvoices.isEmpty()) {
    EmptyStateView(
      icon = Icons.Default.PointOfSale,
      title = "No Sales Data",
      message = "Sales invoices recorded for retail shops will appear in this report."
    )
  } else {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(salesInvoices, key = { it.id }) { inv ->
        Card(
          shape = RoundedCornerShape(10.dp),
          colors = CardDefaults.cardColors(containerColor = FigmaWhite),
          elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
          modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, FigmaLine, RoundedCornerShape(10.dp))
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
              Text(text = "Bill #${inv.invoiceNumber}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FigmaInk)
              Text(
                text = "${customerNames[inv.customerId] ?: "Customer #${inv.customerId}"} · ${SubledgerCalculationEngine.formatDate(inv.invoiceDate)}",
                fontSize = 11.sp,
                color = FigmaMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            Column(horizontalAlignment = Alignment.End) {
              Text(text = SubledgerCalculationEngine.formatPaise(inv.totalAmountPaise), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FigmaInk)
              Text(text = inv.paymentStatus.displayName, fontSize = 10.sp, color = if (inv.balanceDuePaise > 0) FigmaRed else FigmaTeal)
            }
          }
        }
      }
    }
  }
}

@Composable
fun PurchasesOverviewTab(
  purchaseInvoices: List<PurchaseInvoice>,
  supplierNames: Map<Long, String>
) {
  if (purchaseInvoices.isEmpty()) {
    EmptyStateView(
      icon = Icons.Default.ShoppingCart,
      title = "No Purchase Data",
      message = "Purchase bills recorded from factory suppliers will appear in this report."
    )
  } else {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(purchaseInvoices, key = { it.id }) { bill ->
        Card(
          shape = RoundedCornerShape(10.dp),
          colors = CardDefaults.cardColors(containerColor = FigmaWhite),
          elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
          modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, FigmaLine, RoundedCornerShape(10.dp))
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
              Text(text = "Bill #${bill.invoiceNumber}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FigmaInk)
              Text(
                text = "${supplierNames[bill.supplierId] ?: "Supplier #${bill.supplierId}"} · ${SubledgerCalculationEngine.formatDate(bill.invoiceDate)}",
                fontSize = 11.sp,
                color = FigmaMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            Column(horizontalAlignment = Alignment.End) {
              Text(text = SubledgerCalculationEngine.formatPaise(bill.totalAmountPaise), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FigmaInk)
              val statusColor = when (bill.paymentStatus) {
                PaymentStatus.PAID -> FigmaTeal
                PaymentStatus.PARTIALLY_PAID -> FigmaAmber
                PaymentStatus.UNPAID -> FigmaRed
              }
              Text(text = bill.paymentStatus.displayName, fontSize = 10.sp, color = statusColor)
            }
          }
        }
      }
    }
  }
}

@Composable
fun ProductPerformanceTab(products: List<ProductSalesPerformance>) {
  if (products.isEmpty()) {
    EmptyStateView(
      icon = Icons.Default.Inventory2,
      title = "No Product Sales",
      message = "Product sales analysis and margins will populate as sales occur."
    )
  } else {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(products, key = { it.productId }) { p ->
        Card(
          shape = RoundedCornerShape(10.dp),
          colors = CardDefaults.cardColors(containerColor = FigmaWhite),
          elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
          modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, FigmaLine, RoundedCornerShape(10.dp))
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text(text = p.productName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FigmaInk, modifier = Modifier.weight(1f).padding(end = 8.dp))
              Text(text = "${p.totalPiecesSold} Pcs", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FigmaNavy)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text(text = "Revenue: ${SubledgerCalculationEngine.formatPaise(p.totalRevenuePaise)}", fontSize = 11.sp, color = FigmaMuted)
              Text(text = "Margin: ${SubledgerCalculationEngine.formatPaise(p.grossProfitPaise)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FigmaTeal)
            }
          }
        }
      }
    }
  }
}

@Composable
fun ShopPerformanceTab(shops: List<ShopSalesPerformance>) {
  if (shops.isEmpty()) {
    EmptyStateView(
      icon = Icons.Default.ReceiptLong,
      title = "No Retail Shops",
      message = "Shop billing summaries and outstanding balances will appear here."
    )
  } else {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(shops, key = { it.customerId }) { s ->
        Card(
          shape = RoundedCornerShape(10.dp),
          colors = CardDefaults.cardColors(containerColor = FigmaWhite),
          elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
          modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, FigmaLine, RoundedCornerShape(10.dp))
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text(text = s.shopName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FigmaInk, modifier = Modifier.weight(1f).padding(end = 8.dp))
              Text(text = "${s.invoiceCount} Bills", fontSize = 11.sp, color = FigmaMuted)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text(text = "Sales: ${SubledgerCalculationEngine.formatPaise(s.totalSalesPaise)}", fontSize = 11.sp, color = FigmaMuted)
              val balLabel = if (s.advanceCreditPaise > 0L) {
                "Adv: ${SubledgerCalculationEngine.formatPaise(s.advanceCreditPaise)}"
              } else {
                "Due: ${SubledgerCalculationEngine.formatPaise(s.outstandingDuesPaise)}"
              }
              Text(
                text = balLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (s.outstandingDuesPaise > 0L) FigmaRed else FigmaTeal
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun ExportDocumentsTab(
  dateRangeLabel: String,
  profitLossSummary: ProfitLossSummary,
  salesInvoices: List<SalesInvoice>,
  purchaseInvoices: List<PurchaseInvoice>,
  customers: List<Customer>,
  suppliers: List<Supplier>,
  batches: List<ProductBatch>,
  sellableStockValuePaise: Long = 0L,
  expiredStockValuePaise: Long = 0L,
  selectedCustomerId: Long?,
  selectedSupplierId: Long?,
  onSelectCustomer: (Long?) -> Unit,
  onSelectSupplier: (Long?) -> Unit,
  onExport: (ReportExportType, ExportFormat) -> Unit
) {
  val scrollState = rememberScrollState()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .testTag("export_documents_tab"),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // Active filter banner
    Surface(
      shape = RoundedCornerShape(10.dp),
      color = FigmaSoftTeal,
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, FigmaLine, RoundedCornerShape(10.dp))
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = "Export Period: $dateRangeLabel",
          fontWeight = FontWeight.Bold,
          fontSize = 12.sp,
          color = FigmaNavy
        )
        Text(
          text = "PDF · XLSX · CSV",
          fontWeight = FontWeight.SemiBold,
          fontSize = 11.sp,
          color = FigmaTeal
        )
      }
    }

    // 1. Profit & Loss Statement
    ReportExportCard(
      title = "1. Profit & Loss Statement",
      subtitle = "Net Profit: ${SubledgerCalculationEngine.formatPaise(profitLossSummary.netProfitPaise)} • ${profitLossSummary.invoiceCount} bills (Actual Batch COGS)",
      icon = Icons.Default.Assessment,
      testTagPrefix = "export_pl",
      onExport = { format -> onExport(ReportExportType.PROFIT_LOSS, format) }
    )

    // 2. Sales Report
    val totalSalesPaise = salesInvoices.sumOf { it.totalAmountPaise }
    ReportExportCard(
      title = "2. Sales & Invoicing Report",
      subtitle = "${salesInvoices.size} invoices • Total: ${SubledgerCalculationEngine.formatPaise(totalSalesPaise)}",
      icon = Icons.Default.PointOfSale,
      testTagPrefix = "export_sales",
      onExport = { format -> onExport(ReportExportType.SALES, format) }
    )

    // 3. Purchase Report
    val totalPurchasesPaise = purchaseInvoices.sumOf { it.totalAmountPaise }
    ReportExportCard(
      title = "3. Purchase & Inward Report",
      subtitle = "${purchaseInvoices.size} purchase bills • Total: ${SubledgerCalculationEngine.formatPaise(totalPurchasesPaise)}",
      icon = Icons.Default.ShoppingCart,
      testTagPrefix = "export_purchase",
      onExport = { format -> onExport(ReportExportType.PURCHASE, format) }
    )

    // 4. Customer Account Statement (with optional specific Customer selector)
    val selectedCustomerName = customers.find { it.id == selectedCustomerId }?.shopName ?: "All Retail Shops (Summary)"
    ReportExportCard(
      title = "4. Customer Account Statements",
      subtitle = "Opening balance, bills, payments, adjustments & closing balance",
      icon = Icons.Default.Store,
      testTagPrefix = "export_customer_stmt",
      selectorContent = {
        PartySelectorDropdown(
          selectedLabel = selectedCustomerName,
          allOptionLabel = "All Retail Shops (Summary)",
          items = customers.map { it.id to it.shopName },
          onSelect = onSelectCustomer,
          testTag = "customer_statement_selector"
        )
      },
      onExport = { format -> onExport(ReportExportType.CUSTOMER_STATEMENT, format) }
    )

    // 5. Supplier Account Statement (with optional specific Supplier selector)
    val selectedSupplierName = suppliers.find { it.id == selectedSupplierId }?.name ?: "All Suppliers (Summary)"
    ReportExportCard(
      title = "5. Supplier Account Statements",
      subtitle = "Opening balance, inward bills, payments & closing payable",
      icon = Icons.Default.Business,
      testTagPrefix = "export_supplier_stmt",
      selectorContent = {
        PartySelectorDropdown(
          selectedLabel = selectedSupplierName,
          allOptionLabel = "All Suppliers (Summary)",
          items = suppliers.map { it.id to it.name },
          onSelect = onSelectSupplier,
          testTag = "supplier_statement_selector"
        )
      },
      onExport = { format -> onExport(ReportExportType.SUPPLIER_STATEMENT, format) }
    )

    // 6. Inventory Valuation & FEFO Batches
    val expiredSuffix = if (expiredStockValuePaise > 0L) {
      " • Expired: ${SubledgerCalculationEngine.formatPaise(expiredStockValuePaise)}"
    } else ""
    ReportExportCard(
      title = "6. Inventory Valuation & FEFO",
      subtitle = "${batches.size} active batches • Sellable: ${SubledgerCalculationEngine.formatPaise(sellableStockValuePaise)}$expiredSuffix",
      icon = Icons.Default.Inventory2,
      testTagPrefix = "export_inventory",
      onExport = { format -> onExport(ReportExportType.INVENTORY_FEFO, format) }
    )

    Spacer(modifier = Modifier.height(12.dp))
  }
}

@Composable
private fun ReportExportCard(
  title: String,
  subtitle: String,
  icon: ImageVector,
  testTagPrefix: String,
  selectorContent: (@Composable () -> Unit)? = null,
  onExport: (ExportFormat) -> Unit
) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = FigmaWhite),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, FigmaLine, RoundedCornerShape(12.dp))
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = FigmaSoftTeal,
          modifier = Modifier.size(38.dp)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = FigmaTeal, modifier = Modifier.size(20.dp))
          }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = FigmaInk
          )
          Text(
            text = subtitle,
            fontSize = 11.sp,
            color = FigmaMuted
          )
        }
      }

      if (selectorContent != null) {
        selectorContent()
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        ExportFormatButton(
          label = "PDF",
          icon = Icons.Default.PictureAsPdf,
          containerColor = FigmaNavy,
          contentColor = FigmaWhite,
          modifier = Modifier
            .weight(1f)
            .testTag("${testTagPrefix}_pdf"),
          onClick = { onExport(ExportFormat.PDF) }
        )
        ExportFormatButton(
          label = "XLSX",
          icon = Icons.Default.TableChart,
          containerColor = FigmaTeal,
          contentColor = FigmaWhite,
          modifier = Modifier
            .weight(1f)
            .testTag("${testTagPrefix}_xlsx"),
          onClick = { onExport(ExportFormat.XLSX) }
        )
        ExportFormatButton(
          label = "CSV",
          icon = Icons.Default.Description,
          containerColor = FigmaSoftTeal,
          contentColor = FigmaNavy,
          modifier = Modifier
            .weight(1f)
            .testTag("${testTagPrefix}_csv"),
          onClick = { onExport(ExportFormat.CSV) }
        )
      }
    }
  }
}

@Composable
private fun ExportFormatButton(
  label: String,
  icon: ImageVector,
  containerColor: androidx.compose.ui.graphics.Color,
  contentColor: androidx.compose.ui.graphics.Color,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  Button(
    onClick = onClick,
    colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
    shape = RoundedCornerShape(8.dp),
    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    modifier = modifier.defaultMinSize(minHeight = 48.dp)
  ) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
    Spacer(modifier = Modifier.width(4.dp))
    Text(
      text = label,
      fontSize = 12.sp,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
      softWrap = false
    )
  }
}

@Composable
private fun PartySelectorDropdown(
  selectedLabel: String,
  allOptionLabel: String,
  items: List<Pair<Long, String>>,
  onSelect: (Long?) -> Unit,
  testTag: String
) {
  var expanded by remember { mutableStateOf(false) }

  Box(modifier = Modifier.fillMaxWidth()) {
    Surface(
      shape = RoundedCornerShape(8.dp),
      color = FigmaBackground,
      modifier = Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 48.dp)
        .border(1.dp, FigmaLine, RoundedCornerShape(8.dp))
        .clickable { expanded = true }
        .testTag(testTag)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.AccountBalance, contentDescription = null, tint = FigmaTeal, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = selectedLabel,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = FigmaInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Account", tint = FigmaMuted)
      }
    }

    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      modifier = Modifier.background(FigmaWhite)
    ) {
      DropdownMenuItem(
        text = { Text(allOptionLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FigmaNavy) },
        onClick = {
          onSelect(null)
          expanded = false
        }
      )
      HorizontalDivider(color = FigmaLine)
      items.forEach { (id, name) ->
        DropdownMenuItem(
          text = { Text(name, fontSize = 13.sp, color = FigmaInk) },
          onClick = {
            onSelect(id)
            expanded = false
          }
        )
      }
    }
  }
}
