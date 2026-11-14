package com.example

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.IceTrackDatabase
import com.example.data.repository.CustomerRepository
import com.example.data.repository.ExpenseRepository
import com.example.data.repository.InventoryRepository
import com.example.data.repository.ProductRepository
import com.example.data.repository.PurchaseRepository
import com.example.data.repository.SalesRepository
import com.example.data.repository.SubledgerRepository
import com.example.data.repository.SupplierRepository
import com.example.domain.model.Customer
import com.example.domain.model.ExpenseCategory
import com.example.domain.model.PaymentMethod
import com.example.domain.model.Product
import com.example.domain.model.PurchaseInvoiceItem
import com.example.domain.model.Supplier
import com.example.service.ExportDocumentService
import com.example.service.ExportFormat
import com.example.ui.screens.PurchaseManagementScreen
import com.example.ui.screens.ReportsAnalyticsScreen
import com.example.ui.screens.SalesBillingScreen
import com.example.ui.theme.IceTrackTheme
import com.example.ui.viewmodel.PurchaseViewModel
import com.example.ui.viewmodel.ReportsViewModel
import com.example.ui.viewmodel.SalesViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xhdpi")
class UiLayoutAndExportScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var context: Context
  private lateinit var db: IceTrackDatabase

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, IceTrackDatabase::class.java)
      .allowMainThreadQueries()
      .build()
  }

  @After
  fun tearDown() {
    db.close()
  }

  private fun assertSingleLineNoOverflow(text: String) {
    val node = composeTestRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
    val results = mutableListOf<TextLayoutResult>()
    val action = node.config[SemanticsActions.GetTextLayoutResult]
    action.action?.invoke(results)
    assertTrue("Expected text layout result for '$text'", results.isNotEmpty())
    assertEquals(
      "Text '$text' wrapped across ${results[0].lineCount} lines instead of staying on 1 horizontal line",
      1,
      results[0].lineCount
    )
    assertEquals(
      "Text '$text' was truncated/ellipsized instead of showing full label on 1 line",
      text.length,
      results[0].getLineEnd(0)
    )
  }

  @Test
  @Config(qualifiers = "w320dp-h640dp-mdpi")
  fun purchaseScreen_paySupplierButton_rendersHorizontallyOnNarrowWidth() {
    val purchaseVm = PurchaseViewModel(
      purchaseRepo = PurchaseRepository(db),
      supplierRepo = SupplierRepository(db),
      productRepo = ProductRepository(db)
    )

    composeTestRule.setContent {
      IceTrackTheme {
        Box(modifier = Modifier.width(320.dp).height(640.dp)) {
          PurchaseManagementScreen(viewModel = purchaseVm)
        }
      }
    }
    composeTestRule.waitForIdle()

    val btnNode = composeTestRule.onNodeWithTag("pay_supplier_header_btn")
      .assertIsDisplayed()
    val btnBounds = btnNode.getUnclippedBoundsInRoot()
    val titleBounds = composeTestRule.onNodeWithText("Purchases & Inward").getUnclippedBoundsInRoot()

    assertTrue("Button height must be single-line compact (<= 56.dp)", (btnBounds.bottom - btnBounds.top) <= 56.dp)
    assertTrue("Button must sit strictly to the right of title column without overlap", titleBounds.right <= btnBounds.left)
    assertTrue("Button width must exceed height (horizontal button)", (btnBounds.right - btnBounds.left) > (btnBounds.bottom - btnBounds.top))
    assertSingleLineNoOverflow("Pay Supplier")
  }

  @Test
  fun purchaseScreen_paySupplierButton_rendersHorizontallyOnStandardWidth() {
    val purchaseVm = PurchaseViewModel(
      purchaseRepo = PurchaseRepository(db),
      supplierRepo = SupplierRepository(db),
      productRepo = ProductRepository(db)
    )

    composeTestRule.setContent {
      IceTrackTheme {
        Box(modifier = Modifier.width(411.dp).height(800.dp)) {
          PurchaseManagementScreen(viewModel = purchaseVm)
        }
      }
    }
    composeTestRule.waitForIdle()

    val btnNode = composeTestRule.onNodeWithTag("pay_supplier_header_btn")
      .assertIsDisplayed()
    val btnBounds = btnNode.getUnclippedBoundsInRoot()
    val titleBounds = composeTestRule.onNodeWithText("Purchases & Inward").getUnclippedBoundsInRoot()

    assertTrue("Button height must be single-line compact (<= 56.dp)", (btnBounds.bottom - btnBounds.top) <= 56.dp)
    assertTrue("Button must sit strictly to the right of title column without overlap", titleBounds.right <= btnBounds.left)
    assertSingleLineNoOverflow("Pay Supplier")
  }

  @Test
  @Config(qualifiers = "w320dp-h640dp-mdpi")
  fun salesScreen_receivePaymentButton_rendersHorizontallyOnNarrowWidth() {
    val salesVm = SalesViewModel(
      salesRepo = SalesRepository(db),
      customerRepo = CustomerRepository(db),
      productRepo = ProductRepository(db),
      inventoryRepo = InventoryRepository(db)
    )

    composeTestRule.setContent {
      IceTrackTheme {
        Box(modifier = Modifier.width(320.dp).height(640.dp)) {
          SalesBillingScreen(viewModel = salesVm)
        }
      }
    }
    composeTestRule.waitForIdle()

    val btnNode = composeTestRule.onNodeWithTag("receive_payment_header_btn")
      .assertIsDisplayed()
    val btnBounds = btnNode.getUnclippedBoundsInRoot()
    val titleBounds = composeTestRule.onNodeWithText("Sales & Billing").getUnclippedBoundsInRoot()

    assertTrue("Button height must be single-line compact (<= 56.dp)", (btnBounds.bottom - btnBounds.top) <= 56.dp)
    assertTrue("Button must sit strictly to the right of title column without overlap", titleBounds.right <= btnBounds.left)
    assertTrue("Button width must exceed height (horizontal button)", (btnBounds.right - btnBounds.left) > (btnBounds.bottom - btnBounds.top))
    assertSingleLineNoOverflow("Receive Payment")
  }

  @Test
  fun reportsExportsTab_showsAllSixReportsAndTriggersRealExportDialog() {
    val productRepo = ProductRepository(db)
    val supplierRepo = SupplierRepository(db)
    val customerRepo = CustomerRepository(db)
    val purchaseRepo = PurchaseRepository(db)
    val salesRepo = SalesRepository(db)
    val inventoryRepo = InventoryRepository(db)
    val expenseRepo = ExpenseRepository(db)
    val subledgerRepo = SubledgerRepository(db)

    val now = System.currentTimeMillis()
    var supId = 0L
    var custId = 0L
    runBlocking<Unit> {
      val prodId = productRepo.saveProductWithUnit(
        product = Product(
          name = "Mango Duet 80ml",
          sku = "MNG-80",
          category = "Sticks",
          defaultPurchasePricePaise = 1200L,
          defaultSellingPricePaise = 2000L,
          minStockLevelPieces = 10
        ),
        piecesPerBox = 20,
        boxesPerCarton = 5
      )
      supId = supplierRepo.saveSupplier(
        Supplier(
          name = "Amul Cold Chain",
          contactPerson = "Ramesh",
          phoneNumber = "9999900001",
          address = "Plant 1",
          creditTermsDays = 15,
          openingBalancePaise = 5000L
        )
      )
      custId = customerRepo.saveCustomer(
        Customer(
          shopName = "Kochi Freeze Mart",
          ownerName = "Ajay",
          phoneNumber = "9999900002",
          address = "Broadway",
          creditLimitPaise = 500000L,
          openingBalancePaise = 2500L
        )
      )
      purchaseRepo.createPurchaseInvoice(
        supplierId = supId,
        invoiceNumber = "PUR-100",
        invoiceDate = now,
        dueDate = now + 15 * 86400000L,
        items = listOf(
          PurchaseInvoiceItem(
            productId = prodId,
            batchNumber = "B-MNG-01",
            expiryDate = now + 90 * 86400000L,
            quantityPieces = 50,
            costPerPiecePaise = 1200L,
            lineTotalPaise = 60000L
          )
        ),
        paidAmountPaise = 30000L,
        paymentMethod = PaymentMethod.UPI,
        notes = "Test inward"
      )
      salesRepo.createSalesInvoice(
        customerId = custId,
        invoiceNumber = "INV-200",
        invoiceDate = now,
        dueDate = now + 7 * 86400000L,
        requestedLines = listOf(
          SalesRepository.RequestedSaleLine(
            productId = prodId,
            quantityPieces = 10,
            unitPricePaise = 2000L
          )
        ),
        paidAmountPaise = 10000L,
        paymentMethod = PaymentMethod.CASH,
        notes = "Test sale"
      )
      expenseRepo.recordExpense(
        category = ExpenseCategory.ELECTRICITY_COLD_STORAGE,
        amountPaise = 2000L,
        expenseDate = now,
        paymentMethod = PaymentMethod.CASH,
        notes = "Freezer power"
      )
    }

    val reportsVm = ReportsViewModel(
      salesRepo = salesRepo,
      purchaseRepo = purchaseRepo,
      productRepo = productRepo,
      customerRepo = customerRepo,
      supplierRepo = supplierRepo,
      inventoryRepo = inventoryRepo,
      expenseRepo = expenseRepo,
      subledgerRepo = subledgerRepo
    )

    composeTestRule.setContent {
      IceTrackTheme {
        Box(modifier = Modifier.width(411.dp).height(900.dp)) {
          ReportsAnalyticsScreen(viewModel = reportsVm)
        }
      }
    }
    composeTestRule.waitForIdle()

    // Scroll to and click Exports tab (index 5)
    composeTestRule.onNodeWithTag("report_tab_5").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    // Wait until Room Flow emits populated state and Exports tab renders
    composeTestRule.waitUntil(timeoutMillis = 5000) {
      ShadowLooper.idleMainLooper()
      reportsVm.uiState.value.selectedTab == 5 &&
        reportsVm.uiState.value.salesInvoices.isNotEmpty()
    }
    composeTestRule.waitForIdle()

    // Verify all 6 report export cards & selectors appear
    val tagsToVerify = listOf(
      "export_pl_pdf", "export_pl_xlsx", "export_pl_csv",
      "export_sales_pdf", "export_sales_xlsx", "export_sales_csv",
      "export_purchase_pdf", "export_purchase_xlsx", "export_purchase_csv",
      "customer_statement_selector", "export_customer_stmt_pdf", "export_customer_stmt_xlsx", "export_customer_stmt_csv",
      "supplier_statement_selector", "export_supplier_stmt_pdf", "export_supplier_stmt_xlsx", "export_supplier_stmt_csv",
      "export_inventory_pdf", "export_inventory_xlsx", "export_inventory_csv"
    )
    tagsToVerify.forEach { tag ->
      composeTestRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
    }

    // Verify customer statement party selector updates state via UI dropdown
    composeTestRule.onNodeWithTag("customer_statement_selector").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Kochi Freeze Mart").performClick()
    composeTestRule.waitForIdle()
    assertEquals(custId, reportsVm.uiState.value.selectedCustomerId)

    // Trigger real XLSX export for Sales Report via UI button click
    composeTestRule.onNodeWithTag("export_sales_xlsx").performScrollTo().performClick()

    val deadline = System.currentTimeMillis() + 8000L
    while (reportsVm.uiState.value.latestExport == null && System.currentTimeMillis() < deadline) {
      val msg = reportsVm.uiState.value.exportMessage
      if (msg != null && msg.startsWith("Export failed")) {
        throw AssertionError("Export failed unexpectedly: $msg")
      }
      ShadowLooper.idleMainLooper()
      composeTestRule.mainClock.advanceTimeByFrame()
      Thread.sleep(25)
    }

    val exported = reportsVm.uiState.value.latestExport
    assertNotNull(
      "Expected latestExport to be populated (isExporting=${reportsVm.uiState.value.isExporting}, msg=${reportsVm.uiState.value.exportMessage})",
      exported
    )
    assertEquals(ExportFormat.XLSX, exported!!.format)
    assertTrue("Generated XLSX file should pass validation", ExportDocumentService.validateExportedFile(exported.file, ExportFormat.XLSX))

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("export_ready_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("export_save_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("export_share_button").assertIsDisplayed()
  }
}
