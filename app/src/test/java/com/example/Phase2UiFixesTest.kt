package com.example

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.IceTrackDatabase
import com.example.data.repository.CustomerRepository
import com.example.data.repository.InventoryRepository
import com.example.data.repository.ProductRepository
import com.example.data.repository.PurchaseRepository
import com.example.data.repository.SalesRepository
import com.example.data.repository.SubledgerRepository
import com.example.data.repository.SupplierRepository
import com.example.domain.model.Customer
import com.example.domain.model.PaymentMethod
import com.example.domain.model.Product
import com.example.domain.model.PurchaseInvoiceItem
import com.example.domain.model.Supplier
import com.example.ui.screens.CustomerListScreen
import com.example.ui.screens.PurchaseManagementScreen
import com.example.ui.screens.SalesBillingScreen
import com.example.ui.screens.SupplierListScreen
import com.example.ui.theme.IceTrackTheme
import com.example.ui.viewmodel.CustomerViewModel
import com.example.ui.viewmodel.PurchaseViewModel
import com.example.ui.viewmodel.SalesViewModel
import com.example.ui.viewmodel.SupplierViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
class Phase2UiFixesTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var context: Context
  private lateinit var db: IceTrackDatabase

  private val dayMs = 86400000L

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, IceTrackDatabase::class.java)
      .allowMainThreadQueries()
      .build()
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun purchaseDialog_persistsSelectedPaymentMethodAcrossAllFourOptions() {
    val productRepo = ProductRepository(db)
    val supplierRepo = SupplierRepository(db)
    val purchaseRepo = PurchaseRepository(db)
    val subledgerRepo = SubledgerRepository(db)

    runBlocking {
      productRepo.saveProductWithUnit(
        Product(name = "Matka Kulfi", sku = "MK-1", category = "Cups", defaultPurchasePricePaise = 1000L, defaultSellingPricePaise = 2000L, minStockLevelPieces = 5),
        10, 5
      )
      supplierRepo.saveSupplier(Supplier(name = "Dairy King", contactPerson = "Ali", phoneNumber = "9900011122", address = "Kochi"))
    }

    val purchaseVm = PurchaseViewModel(purchaseRepo, supplierRepo, productRepo, subledgerRepo)

    composeTestRule.setContent {
      IceTrackTheme {
        Box(modifier = Modifier.width(411.dp).height(900.dp)) {
          PurchaseManagementScreen(viewModel = purchaseVm)
        }
      }
    }
    composeTestRule.waitUntil(timeoutMillis = 5000) {
      ShadowLooper.idleMainLooper()
      purchaseVm.suppliers.value.isNotEmpty() && purchaseVm.products.value.isNotEmpty()
    }

    composeTestRule.onNodeWithTag("new_purchase_fab").performClick()
    composeTestRule.waitForIdle()

    // Add 1 line item (10 pcs * ₹10 = ₹100)
    composeTestRule.onNodeWithText("Pieces").performScrollTo().performTextInput("10")
    composeTestRule.onNodeWithText("+ Add Line Item").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    // Verify all 4 payment methods exist in Purchase dialog & select CHEQUE
    PaymentMethod.values().forEach { method ->
      composeTestRule.onNodeWithTag("purchase_payment_method_${method.name}").performScrollTo().assertIsDisplayed()
    }
    composeTestRule.onNodeWithTag("purchase_payment_method_CHEQUE").performScrollTo().performClick()
    composeTestRule.onNodeWithText("Initial Payment Made (₹)").performScrollTo().performTextInput("60")
    composeTestRule.onNodeWithText("Confirm & Receive Stock").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5000) {
      ShadowLooper.idleMainLooper()
      runBlocking { purchaseRepo.allPurchases.first().isNotEmpty() }
    }

    val saved = runBlocking { purchaseRepo.allPurchases.first().first() }
    assertEquals(PaymentMethod.CHEQUE, saved.paymentMethod)
    assertEquals(6000L, saved.paidAmountPaise)
  }

  @Test
  fun salesAndPaymentDialogs_renderAllFourPaymentMethodOptions() {
    val productRepo = ProductRepository(db)
    val supplierRepo = SupplierRepository(db)
    val customerRepo = CustomerRepository(db)
    val purchaseRepo = PurchaseRepository(db)
    val salesRepo = SalesRepository(db)
    val inventoryRepo = InventoryRepository(db)
    val subledgerRepo = SubledgerRepository(db)

    val now = System.currentTimeMillis()
    runBlocking {
      val prodId = productRepo.saveProductWithUnit(
        Product(name = "Choco Stick", sku = "CS-1", category = "Sticks", defaultPurchasePricePaise = 1000L, defaultSellingPricePaise = 2000L, minStockLevelPieces = 5),
        10, 5
      )
      val supId = supplierRepo.saveSupplier(Supplier(name = "Sup Methods", contactPerson = "", phoneNumber = "", address = ""))
      customerRepo.saveCustomer(Customer(shopName = "Shop Methods", ownerName = "", phoneNumber = "", address = ""))
      purchaseRepo.createPurchaseInvoice(
        supId, "PUR-M-1", now, 0L,
        listOf(PurchaseInvoiceItem(productId = prodId, batchNumber = "BM-1", expiryDate = now + 90 * dayMs, quantityPieces = 50, costPerPiecePaise = 1000L, lineTotalPaise = 50000L)),
        0L, null, ""
      )
    }

    val salesVm = SalesViewModel(salesRepo, customerRepo, productRepo, inventoryRepo, subledgerRepo)
    composeTestRule.setContent {
      IceTrackTheme {
        Box(modifier = Modifier.width(411.dp).height(900.dp)) {
          SalesBillingScreen(viewModel = salesVm)
        }
      }
    }
    composeTestRule.waitUntil(timeoutMillis = 5000) {
      ShadowLooper.idleMainLooper()
      salesVm.customers.value.isNotEmpty() && salesVm.products.value.isNotEmpty()
    }

    // ReceiveCustomerPaymentDialog has all 4 options
    composeTestRule.onNodeWithTag("receive_payment_header_btn").performClick()
    composeTestRule.waitForIdle()
    PaymentMethod.values().forEach { method ->
      composeTestRule.onNodeWithTag("customer_payment_method_${method.name}").performScrollTo().assertIsDisplayed()
    }
    composeTestRule.onNodeWithText("Cancel").performClick()
    composeTestRule.waitForIdle()

    // CreateSalesInvoiceDialog has all 4 options
    composeTestRule.onNodeWithTag("new_sale_fab").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Pieces").performScrollTo().performTextInput("5")
    composeTestRule.onNodeWithText("+ Add Line Item").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    PaymentMethod.values().forEach { method ->
      composeTestRule.onNodeWithTag("sales_payment_method_${method.name}").performScrollTo().assertIsDisplayed()
    }
    composeTestRule.onNodeWithTag("sales_payment_method_BANK_TRANSFER").performScrollTo().performClick()
    composeTestRule.onNodeWithText("Payment Received Now (₹)").performScrollTo().performTextInput("100")
    composeTestRule.onNodeWithText("Generate Bill & Dispatch").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5000) {
      ShadowLooper.idleMainLooper()
      runBlocking { salesRepo.allSales.first().isNotEmpty() }
    }
    val savedSale = runBlocking { salesRepo.allSales.first().first() }
    assertEquals(PaymentMethod.BANK_TRANSFER, savedSale.paymentMethod)
  }

  @Test
  fun salesCreditLimitOverrideFailure_surfacesErrorInsideOpenDialogAndClearsOnEdit() {
    val productRepo = ProductRepository(db)
    val supplierRepo = SupplierRepository(db)
    val customerRepo = CustomerRepository(db)
    val purchaseRepo = PurchaseRepository(db)
    val salesRepo = SalesRepository(db)
    val inventoryRepo = InventoryRepository(db)
    val subledgerRepo = SubledgerRepository(db)

    val now = System.currentTimeMillis()
    runBlocking {
      val prodId = productRepo.saveProductWithUnit(
        Product(name = "Vanilla Cup", sku = "VC-1", category = "Cups", defaultPurchasePricePaise = 1000L, defaultSellingPricePaise = 2000L, minStockLevelPieces = 1),
        10, 5
      )
      val supId = supplierRepo.saveSupplier(Supplier(name = "Sup Cred", contactPerson = "", phoneNumber = "", address = ""))
      val custId = customerRepo.saveCustomer(
        Customer(shopName = "Credit Capped Shop", ownerName = "", phoneNumber = "", address = "", creditLimitPaise = 5000L) // ₹50 limit
      )
      purchaseRepo.createPurchaseInvoice(
        supId, "PUR-CR-1", now, 0L,
        listOf(PurchaseInvoiceItem(productId = prodId, batchNumber = "BC-1", expiryDate = now + 90 * dayMs, quantityPieces = 10, costPerPiecePaise = 1000L, lineTotalPaise = 10000L)),
        0L, null, ""
      )
      // Pre-create an invoice INV-DUP-001 so overriding with duplicate invoice # fails on step 2
      salesRepo.createSalesInvoice(
        custId, "INV-DUP-001", now, 0L,
        listOf(SalesRepository.RequestedSaleLine(prodId, 1, 2000L)),
        2000L, PaymentMethod.CASH, ""
      )
    }

    val salesVm = SalesViewModel(salesRepo, customerRepo, productRepo, inventoryRepo, subledgerRepo)
    composeTestRule.setContent {
      IceTrackTheme {
        Box(modifier = Modifier.width(411.dp).height(900.dp)) {
          SalesBillingScreen(viewModel = salesVm)
        }
      }
    }
    composeTestRule.waitUntil(timeoutMillis = 5000) {
      ShadowLooper.idleMainLooper()
      salesVm.customers.value.isNotEmpty() && salesVm.products.value.isNotEmpty()
    }

    composeTestRule.onNodeWithTag("new_sale_fab").performClick()
    composeTestRule.waitForIdle()

    // Enter duplicate invoice number + 5 pcs (₹100 > ₹50 credit limit)
    composeTestRule.onNodeWithText("Invoice Number / Bill No.").performScrollTo().performTextReplacement("INV-DUP-001")
    composeTestRule.onNodeWithText("Pieces").performScrollTo().performTextInput("5")
    composeTestRule.onNodeWithText("+ Add Line Item").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("Generate Bill & Dispatch").performClick()

    // Credit limit exceeded override confirmation modal opens
    composeTestRule.waitUntil(timeoutMillis = 5000) {
      ShadowLooper.idleMainLooper()
      try {
        composeTestRule.onNodeWithTag("confirm_sale_credit_override_btn").assertExists()
        true
      } catch (_: AssertionError) {
        false
      }
    }
    composeTestRule.onNodeWithTag("confirm_sale_credit_override_btn").performClick()

    // Override fails on duplicate invoice number -> error MUST render inside open sales dialog!
    composeTestRule.waitUntil(timeoutMillis = 5000) {
      ShadowLooper.idleMainLooper()
      try {
        composeTestRule.onNodeWithTag("sales_dialog_error").assertExists()
        true
      } catch (_: AssertionError) {
        false
      }
    }
    composeTestRule.onNodeWithTag("sales_dialog_error").performScrollTo().assertIsDisplayed()

    // Editing invoice number clears stale external error
    composeTestRule.onNodeWithText("Invoice Number / Bill No.").performScrollTo().performTextReplacement("INV-FIXED-002")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("sales_dialog_error").assertDoesNotExist()
  }

  @Test
  fun purchaseCreditLimitOverrideFailure_surfacesErrorInsideOpenDialog() {
    val productRepo = ProductRepository(db)
    val supplierRepo = SupplierRepository(db)
    val purchaseRepo = PurchaseRepository(db)
    val subledgerRepo = SubledgerRepository(db)

    val now = System.currentTimeMillis()
    runBlocking {
      val prodId = productRepo.saveProductWithUnit(
        Product(name = "Mango Cone", sku = "MC-9", category = "Cones", defaultPurchasePricePaise = 1000L, defaultSellingPricePaise = 2000L, minStockLevelPieces = 1),
        10, 5
      )
      val supId = supplierRepo.saveSupplier(
        Supplier(name = "Capped Supplier", contactPerson = "", phoneNumber = "", address = "", creditLimitPaise = 5000L)
      )
      // Pre-create PUR-DUP-001 so override fails on duplicate invoice #
      purchaseRepo.createPurchaseInvoice(
        supId, "PUR-DUP-001", now, 0L,
        listOf(PurchaseInvoiceItem(productId = prodId, batchNumber = "BP-1", expiryDate = now + 90 * dayMs, quantityPieces = 1, costPerPiecePaise = 1000L, lineTotalPaise = 1000L)),
        1000L, PaymentMethod.CASH, ""
      )
    }

    val purchaseVm = PurchaseViewModel(purchaseRepo, supplierRepo, productRepo, subledgerRepo)
    composeTestRule.setContent {
      IceTrackTheme {
        Box(modifier = Modifier.width(411.dp).height(900.dp)) {
          PurchaseManagementScreen(viewModel = purchaseVm)
        }
      }
    }
    composeTestRule.waitUntil(timeoutMillis = 5000) {
      ShadowLooper.idleMainLooper()
      purchaseVm.suppliers.value.isNotEmpty() && purchaseVm.products.value.isNotEmpty()
    }

    composeTestRule.onNodeWithTag("new_purchase_fab").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("Invoice Number / Ref").performScrollTo().performTextReplacement("PUR-DUP-001")
    composeTestRule.onNodeWithText("Pieces").performScrollTo().performTextInput("10") // ₹100 > ₹50 credit limit
    composeTestRule.onNodeWithText("+ Add Line Item").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("Confirm & Receive Stock").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5000) {
      ShadowLooper.idleMainLooper()
      try {
        composeTestRule.onNodeWithTag("confirm_purchase_credit_override_btn").assertExists()
        true
      } catch (_: AssertionError) {
        false
      }
    }
    composeTestRule.onNodeWithTag("confirm_purchase_credit_override_btn").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5000) {
      ShadowLooper.idleMainLooper()
      try {
        composeTestRule.onNodeWithTag("purchase_dialog_error").assertExists()
        true
      } catch (_: AssertionError) {
        false
      }
    }
    composeTestRule.onNodeWithTag("purchase_dialog_error").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun archivedToggle_onCustomerAndSupplierLists_revealsArchivedPartiesWhileSsotSummaryShowsTotal() {
    val productRepo = ProductRepository(db)
    val supplierRepo = SupplierRepository(db)
    val customerRepo = CustomerRepository(db)
    val subledgerRepo = SubledgerRepository(db)

    var archivedCustId = 0L
    var archivedSupId = 0L
    runBlocking {
      val c1 = customerRepo.saveCustomer(Customer(shopName = "Active Dairy", ownerName = "", phoneNumber = "", address = "", openingBalancePaise = 10000L))
      assertTrue(c1 > 0L)
      archivedCustId = customerRepo.saveCustomer(Customer(shopName = "Old Dairy", ownerName = "", phoneNumber = "", address = "", openingBalancePaise = 20000L))
      customerRepo.softDelete(archivedCustId)

      supplierRepo.saveSupplier(Supplier(name = "Active Cold", contactPerson = "", phoneNumber = "", address = "", openingBalancePaise = 30000L))
      archivedSupId = supplierRepo.saveSupplier(Supplier(name = "Old Cold", contactPerson = "", phoneNumber = "", address = "", openingBalancePaise = 40000L))
      supplierRepo.softDelete(archivedSupId)
    }

    val custVm = CustomerViewModel(customerRepo, productRepo, subledgerRepo)
    composeTestRule.setContent {
      IceTrackTheme {
        Box(modifier = Modifier.width(411.dp).height(900.dp)) {
          CustomerListScreen(viewModel = custVm)
        }
      }
    }
    composeTestRule.waitUntil(timeoutMillis = 5000) {
      ShadowLooper.idleMainLooper()
      custVm.listSummary.value.totalAllReceivablesPaise == 30000L
    }

    composeTestRule.onNodeWithTag("customer_ssot_summary").assertIsDisplayed()
    composeTestRule.onNodeWithTag("customer_card_$archivedCustId").assertDoesNotExist()
    composeTestRule.onNodeWithTag("toggle_archived_customers").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("customer_card_$archivedCustId").assertIsDisplayed()

    val supVm = SupplierViewModel(supplierRepo, subledgerRepo)
    composeTestRule.setContent {
      IceTrackTheme {
        Box(modifier = Modifier.width(411.dp).height(900.dp)) {
          SupplierListScreen(viewModel = supVm)
        }
      }
    }
    composeTestRule.waitUntil(timeoutMillis = 5000) {
      ShadowLooper.idleMainLooper()
      supVm.listSummary.value.totalAllPayablesPaise == 70000L
    }

    composeTestRule.onNodeWithTag("supplier_ssot_summary").assertIsDisplayed()
    composeTestRule.onNodeWithTag("supplier_card_$archivedSupId").assertDoesNotExist()
    composeTestRule.onNodeWithTag("toggle_archived_suppliers").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("supplier_card_$archivedSupId").assertIsDisplayed()
  }
}
