package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.DatabaseMigrations
import com.example.data.local.IceTrackDatabase
import com.example.data.repository.CustomerRepository
import com.example.data.repository.InventoryRepository
import com.example.data.repository.ProductRepository
import com.example.data.repository.PurchaseRepository
import com.example.data.repository.SalesRepository
import com.example.data.repository.SubledgerRepository
import com.example.data.repository.SupplierRepository
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.model.Customer
import com.example.domain.model.CustomerProductPrice
import com.example.domain.model.PartyType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.Product
import com.example.domain.model.ProductSupplier
import com.example.domain.model.PurchaseInvoiceItem
import com.example.domain.model.SubledgerEntryType
import com.example.domain.model.Supplier
import com.example.ui.screens.PurchaseManagementScreen
import com.example.ui.theme.IceTrackTheme
import com.example.ui.viewmodel.CustomerViewModel
import com.example.ui.viewmodel.InventoryViewModel
import com.example.ui.viewmodel.ProductViewModel
import com.example.ui.viewmodel.PurchaseViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
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
@Config(sdk = [36])
class Phase1IntegrityAndMigrationTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val migrationHelper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    IceTrackDatabase::class.java,
    emptyList(),
    FrameworkSQLiteOpenHelperFactory()
  )

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
  fun migration1To2_populatedDatabase_preservesAllV1DataAndValidatesSchema() {
    val dbName = "icetrack_migration_test.db"
    val now = 1700000000000L

    // 1. Create and populate Version 1 database
    migrationHelper.createDatabase(dbName, 1).apply {
      execSQL(
        "INSERT INTO products (id, name, sku, category, defaultPurchasePricePaise, defaultSellingPricePaise, minStockLevelPieces, isActive, createdAt, deletedAt) " +
          "VALUES (1, 'Choco Bar 65ml', 'CB-65', 'Sticks/Bars', 1000, 1500, 20, 1, $now, NULL)"
      )
      execSQL(
        "INSERT INTO product_units (id, productId, piecesPerBox, boxesPerCarton) VALUES (1, 1, 24, 6)"
      )
      execSQL(
        "INSERT INTO suppliers (id, name, contactPerson, phoneNumber, address, openingBalancePaise, openingBalanceDate, creditTermsDays, creditLimitPaise, isActive, createdAt, deletedAt) " +
          "VALUES (1, 'Kochi Dairy Factory', 'Suresh', '9876543210', 'Industrial Estate', 500000, $now, 15, 2000000, 1, $now, NULL)"
      )
      execSQL(
        "INSERT INTO customers (id, shopName, ownerName, phoneNumber, address, openingBalancePaise, openingBalanceDate, creditLimitPaise, paymentTermsDays, isActive, createdAt, deletedAt) " +
          "VALUES (1, 'Malabar Bakers', 'Naufal', '9847000111', 'MG Road', 250000, $now, 1000000, 7, 1, $now, NULL)"
      )
      execSQL(
        "INSERT INTO customer_product_prices (id, customerId, productId, customSellingPricePaise) VALUES (1, 1, 1, 1350)"
      )
      execSQL(
        "INSERT INTO product_suppliers (id, productId, supplierId, supplierProductCode, purchaseCostPaise, isPreferredSupplier) " +
          "VALUES (1, 1, 1, 'SUP-CB-65', 950, 1)"
      )
      execSQL(
        "INSERT INTO subledger_entries (id, partyType, partyId, entryType, referenceType, referenceId, debitPaise, creditPaise, entryDate, createdAt, notes, isReversed) " +
          "VALUES (1, 'CUSTOMER', 1, 'OPENING_BALANCE', 'CUSTOMER_OPENING', 1, 250000, 0, $now, $now, 'Opening', 0)"
      )
      execSQL(
        "INSERT INTO app_settings (key, value) VALUES ('business_name', 'IceTrack Kerala')"
      )
      close()
    }

    // 2. Run MIGRATION_1_2 and validate full schema against exported 2.json
    val migratedDb = migrationHelper.runMigrationsAndValidate(
      dbName,
      2,
      true,
      DatabaseMigrations.MIGRATION_1_2
    )

    // 3. Verify V1 records remain intact
    migratedDb.query("SELECT name, sku FROM products WHERE id = 1").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("Choco Bar 65ml", cursor.getString(0))
      assertEquals("CB-65", cursor.getString(1))
    }
    migratedDb.query("SELECT piecesPerBox, boxesPerCarton FROM product_units WHERE productId = 1").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(24, cursor.getInt(0))
      assertEquals(6, cursor.getInt(1))
    }
    migratedDb.query("SELECT customSellingPricePaise FROM customer_product_prices WHERE customerId = 1 AND productId = 1").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(1350L, cursor.getLong(0))
    }
    migratedDb.query("SELECT value FROM app_settings WHERE key = 'business_name'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("IceTrack Kerala", cursor.getString(0))
    }
    migratedDb.close()

    // 4. Open with Room IceTrackDatabase to confirm full upgrade through V3 and DAO operations succeed cleanly
    val roomDb = Room.databaseBuilder(context, IceTrackDatabase::class.java, dbName)
      .addMigrations(DatabaseMigrations.MIGRATION_1_2, DatabaseMigrations.MIGRATION_2_3)
      .allowMainThreadQueries()
      .build()
    try {
      runBlocking {
        val purchaseRepo = PurchaseRepository(roomDb)
        val purchaseId = purchaseRepo.createPurchaseInvoice(
          supplierId = 1L,
          invoiceNumber = "PUR-MIG-001",
          invoiceDate = now,
          dueDate = now + 15 * dayMs,
          items = listOf(
            PurchaseInvoiceItem(
              productId = 1L,
              batchNumber = "B-MIG-01",
              expiryDate = now + 60 * dayMs,
              quantityPieces = 100,
              costPerPiecePaise = 950L,
              lineTotalPaise = 95000L
            )
          ),
          paidAmountPaise = 0L,
          paymentMethod = null,
          notes = "Post migration receipt"
        )
        assertTrue(purchaseId > 0L)
        assertEquals(100, roomDb.productBatchDao().getActiveBatchesForProductSync(1L).sumOf { it.currentQuantityPieces })
      }
    } finally {
      roomDb.close()
      context.deleteDatabase(dbName)
    }
  }

  @Test
  fun salesRepository_duplicateProductLinesExceedingStock_throwsAndRollsBackCompletely() = runBlocking {
    val productRepo = ProductRepository(db)
    val supplierRepo = SupplierRepository(db)
    val customerRepo = CustomerRepository(db)
    val purchaseRepo = PurchaseRepository(db)
    val salesRepo = SalesRepository(db)

    val now = System.currentTimeMillis()
    val prodId = productRepo.saveProductWithUnit(
      Product(name = "Vanilla Cup", sku = "VC-100", category = "Cups", defaultPurchasePricePaise = 1000L, defaultSellingPricePaise = 1500L, minStockLevelPieces = 10),
      piecesPerBox = 12,
      boxesPerCarton = 4
    )
    val supId = supplierRepo.saveSupplier(
      Supplier(name = "Factory A", contactPerson = "Ali", phoneNumber = "9000011111", address = "A")
    )
    val custId = customerRepo.saveCustomer(
      Customer(shopName = "Shop X", ownerName = "Babu", phoneNumber = "9000022222", address = "B")
    )

    purchaseRepo.createPurchaseInvoice(
      supplierId = supId,
      invoiceNumber = "PUR-20260101-0001",
      invoiceDate = now,
      dueDate = now + 10 * dayMs,
      items = listOf(
        PurchaseInvoiceItem(
          productId = prodId,
          batchNumber = "B-DUP-01",
          expiryDate = now + 90 * dayMs,
          quantityPieces = 50,
          costPerPiecePaise = 1000L,
          lineTotalPaise = 50000L
        )
      ),
      paidAmountPaise = 50000L,
      paymentMethod = PaymentMethod.CASH,
      notes = "Initial stock"
    )

    // Duplicate product lines summing to 60 pcs > 50 available
    var failed = false
    try {
      salesRepo.createSalesInvoice(
        customerId = custId,
        invoiceNumber = "INV-DUP-FAIL",
        invoiceDate = now,
        dueDate = now + 7 * dayMs,
        requestedLines = listOf(
          SalesRepository.RequestedSaleLine(productId = prodId, quantityPieces = 30, unitPricePaise = 1500L),
          SalesRepository.RequestedSaleLine(productId = prodId, quantityPieces = 30, unitPricePaise = 1500L)
        ),
        paidAmountPaise = 0L,
        paymentMethod = null,
        notes = "Should fail oversell"
      )
    } catch (_: IllegalStateException) {
      failed = true
    }

    assertTrue("Overselling across duplicate lines must fail", failed)
    assertEquals(50, db.productBatchDao().getActiveBatchesForProductSync(prodId).sumOf { it.currentQuantityPieces })
    assertEquals(0, salesRepo.allSales.first().size)

    // Valid split lines summing to 40 pcs <= 50 must deduct cumulatively
    salesRepo.createSalesInvoice(
      customerId = custId,
      invoiceNumber = "INV-DUP-OK",
      invoiceDate = now,
      dueDate = now + 7 * dayMs,
      requestedLines = listOf(
        SalesRepository.RequestedSaleLine(productId = prodId, quantityPieces = 25, unitPricePaise = 1500L),
        SalesRepository.RequestedSaleLine(productId = prodId, quantityPieces = 15, unitPricePaise = 1400L)
      ),
      paidAmountPaise = 0L,
      paymentMethod = null,
      notes = "Valid split prices"
    )
    assertEquals(10, db.productBatchDao().getActiveBatchesForProductSync(prodId).sumOf { it.currentQuantityPieces })
    assertEquals(1, salesRepo.allSales.first().size)
  }

  @Test
  fun atomicStockDeductionAndMultiLineRollback_leavesDatabaseConsistent() = runBlocking {
    val productRepo = ProductRepository(db)
    val supplierRepo = SupplierRepository(db)
    val customerRepo = CustomerRepository(db)
    val purchaseRepo = PurchaseRepository(db)
    val salesRepo = SalesRepository(db)
    val inventoryRepo = InventoryRepository(db)

    val now = System.currentTimeMillis()
    val prod1 = productRepo.saveProductWithUnit(
      Product(name = "Pista Cone", sku = "PC-1", category = "Cones", defaultPurchasePricePaise = 1500L, defaultSellingPricePaise = 2500L, minStockLevelPieces = 5),
      10, 5
    )
    val prod2 = productRepo.saveProductWithUnit(
      Product(name = "Strawberry Cup", sku = "SC-2", category = "Cups", defaultPurchasePricePaise = 800L, defaultSellingPricePaise = 1200L, minStockLevelPieces = 5),
      20, 5
    )
    val supId = supplierRepo.saveSupplier(Supplier(name = "Sup", contactPerson = "", phoneNumber = "", address = ""))
    val custId = customerRepo.saveCustomer(Customer(shopName = "Cust", ownerName = "", phoneNumber = "", address = ""))

    purchaseRepo.createPurchaseInvoice(
      supplierId = supId,
      invoiceNumber = "PUR-ATOMIC-1",
      invoiceDate = now,
      dueDate = now + 10 * dayMs,
      items = listOf(
        PurchaseInvoiceItem(productId = prod1, batchNumber = "B1", expiryDate = now + 60 * dayMs, quantityPieces = 40, costPerPiecePaise = 1500L, lineTotalPaise = 60000L),
        PurchaseInvoiceItem(productId = prod2, batchNumber = "B2", expiryDate = now + 60 * dayMs, quantityPieces = 10, costPerPiecePaise = 800L, lineTotalPaise = 8000L)
      ),
      paidAmountPaise = 0L,
      paymentMethod = null,
      notes = ""
    )

    val batch1 = db.productBatchDao().getActiveBatchesForProductSync(prod1).first()
    // Direct guarded DAO check: deducting 100 from 40 must update 0 rows
    assertEquals(0, db.productBatchDao().deductStockIfAvailable(batch1.id, 100))
    assertEquals(40, db.productBatchDao().getById(batch1.id)!!.currentQuantityPieces)

    // Inventory adjustStock reducing below 0 throws and writes no movement
    var adjustFailed = false
    try {
      inventoryRepo.adjustStock(batch1.id, -50, "Wastage")
    } catch (_: IllegalStateException) {
      adjustFailed = true
    }
    assertTrue(adjustFailed)
    assertEquals(40, db.productBatchDao().getById(batch1.id)!!.currentQuantityPieces)

    // Multi-line sale where prod1 has enough stock (20/40) and prod2 lacks stock (15/10)
    var multiLineFailed = false
    try {
      salesRepo.createSalesInvoice(
        customerId = custId,
        invoiceNumber = "INV-ROLLBACK-1",
        invoiceDate = now,
        dueDate = now + 7 * dayMs,
        requestedLines = listOf(
          SalesRepository.RequestedSaleLine(prod1, 20, 2500L),
          SalesRepository.RequestedSaleLine(prod2, 15, 1200L)
        ),
        paidAmountPaise = 10000L,
        paymentMethod = PaymentMethod.CASH,
        notes = "Must rollback line 1 as well"
      )
    } catch (_: IllegalStateException) {
      multiLineFailed = true
    }

    assertTrue(multiLineFailed)
    assertEquals(40, db.productBatchDao().getActiveBatchesForProductSync(prod1).sumOf { it.currentQuantityPieces })
    assertEquals(10, db.productBatchDao().getActiveBatchesForProductSync(prod2).sumOf { it.currentQuantityPieces })
    assertEquals(0, db.subledgerDao().getEntriesForParty(PartyType.CUSTOMER, custId).first().size)
  }

  @Test
  fun productUnitAndCustomerPricing_editRoundTrip_preservesAndUpdatesWithoutOverwrite() = runBlocking {
    val productRepo = ProductRepository(db)
    val supplierRepo = SupplierRepository(db)
    val customerRepo = CustomerRepository(db)
    val subledgerRepo = SubledgerRepository(db)

    val supId = supplierRepo.saveSupplier(Supplier(name = "Sup 1", contactPerson = "", phoneNumber = "", address = ""))
    val initialCreatedAt = 1650000000000L
    val prodId = productRepo.saveProductWithUnit(
      product = Product(
        name = "Butterscotch Tub",
        sku = "BT-500",
        category = "Family Tubs",
        defaultPurchasePricePaise = 12000L,
        defaultSellingPricePaise = 18000L,
        minStockLevelPieces = 5,
        createdAt = initialCreatedAt
      ),
      piecesPerBox = 12,
      boxesPerCarton = 6,
      supplierCosts = listOf(
        ProductSupplier(productId = 0L, supplierId = supId, purchaseCostPaise = 11500L, isPreferredSupplier = true)
      )
    )
    val prod2Id = productRepo.saveProductWithUnit(
      product = Product(
        name = "Orange Candy",
        sku = "OC-50",
        category = "Sticks/Bars",
        defaultPurchasePricePaise = 400L,
        defaultSellingPricePaise = 700L,
        minStockLevelPieces = 10
      ),
      piecesPerBox = 30,
      boxesPerCarton = 10
    )

    val savedProd1 = db.productDao().getProductSync(prodId)!!.let {
      Product(it.id, it.name, it.sku, it.category, it.defaultPurchasePricePaise, it.defaultSellingPricePaise, it.minStockLevelPieces, it.isActive, it.createdAt)
    }

    // Simulate ProductViewModel edit dialog open + wait for async details + edit name only + save
    val productVm = ProductViewModel(productRepo, supplierRepo)
    productVm.openEditDialog(savedProd1)
    val pDeadline = System.currentTimeMillis() + 3000L
    while (productVm.formState.value.isLoadingDetails && System.currentTimeMillis() < pDeadline) {
      ShadowLooper.idleMainLooper()
      Thread.sleep(10)
    }
    assertEquals("12", productVm.formState.value.piecesPerBox)
    assertEquals("6", productVm.formState.value.boxesPerCarton)
    productVm.updateForm { it.copy(name = "Butterscotch Tub 500ml") }

    var pSaved = false
    productVm.saveProduct { pSaved = true }
    val psDeadline = System.currentTimeMillis() + 3000L
    while (!pSaved && System.currentTimeMillis() < psDeadline) {
      ShadowLooper.idleMainLooper()
      Thread.sleep(10)
    }
    assertTrue(pSaved)

    val updatedUnit = productRepo.getUnitForProductSync(prodId)!!
    assertEquals(12, updatedUnit.piecesPerBox)
    assertEquals(6, updatedUnit.boxesPerCarton)
    assertEquals(1, productRepo.getSuppliersForProductSync(prodId).size)
    assertEquals(initialCreatedAt, db.productDao().getProductSync(prodId)!!.createdAt)

    // Customer custom pricing round-trip via CustomerViewModel
    val custId = customerRepo.saveCustomer(
      customer = Customer(
        shopName = "Metro Mart",
        ownerName = "Ravi",
        phoneNumber = "9999988888",
        address = "Calicut",
        openingBalancePaise = 100000L
      ),
      customPrices = listOf(
        CustomerProductPrice(customerId = 0L, productId = prodId, customSellingPricePaise = 16550L)
      ),
      replaceCustomPrices = true
    )

    val savedCust = db.customerDao().getCustomerSync(custId)!!.let {
      Customer(it.id, it.shopName, it.ownerName, it.phoneNumber, it.address, it.openingBalancePaise, it.openingBalanceDate, it.creditLimitPaise, it.paymentTermsDays, it.isActive, it.createdAt)
    }

    val customerVm = CustomerViewModel(customerRepo, productRepo, subledgerRepo)
    customerVm.openEditDialog(savedCust)
    val cDeadline = System.currentTimeMillis() + 3000L
    while (customerVm.formState.value.isLoadingDetails && System.currentTimeMillis() < cDeadline) {
      ShadowLooper.idleMainLooper()
      Thread.sleep(10)
    }
    assertEquals("165.50", customerVm.formState.value.customPrices[prodId])
    customerVm.updateForm { it.copy(shopName = "Metro Super Mart") }
    customerVm.setCustomPrice(prod2Id, "6.25")

    var cSaved = false
    customerVm.saveCustomer { cSaved = true }
    val csDeadline = System.currentTimeMillis() + 3000L
    while (!cSaved && System.currentTimeMillis() < csDeadline) {
      ShadowLooper.idleMainLooper()
      Thread.sleep(10)
    }
    assertTrue(cSaved)

    assertEquals(16550L, customerRepo.getPriceForCustomerProduct(custId, prodId))
    assertEquals(625L, customerRepo.getPriceForCustomerProduct(custId, prod2Id))
    assertEquals("Metro Super Mart", db.customerDao().getCustomerSync(custId)!!.shopName)
  }

  @Test
  fun openingBalanceEdits_reversePreviousSubledgerEntryAndPreserveAuditTrail() = runBlocking {
    val supplierRepo = SupplierRepository(db)
    val customerRepo = CustomerRepository(db)
    val subledgerRepo = SubledgerRepository(db)

    // Supplier opening balance update: ₹5,000 -> ₹7,500 -> -₹1,000 (advance paid)
    val supId = supplierRepo.saveSupplier(
      Supplier(name = "Dairy Plant", contactPerson = "A", phoneNumber = "", address = "", openingBalancePaise = 500000L)
    )
    val supLoaded = db.supplierDao().getSupplierSync(supId)!!
    supplierRepo.saveSupplier(
      Supplier(
        id = supId,
        name = supLoaded.name,
        contactPerson = supLoaded.contactPerson,
        phoneNumber = supLoaded.phoneNumber,
        address = supLoaded.address,
        openingBalancePaise = 750000L,
        openingBalanceDate = supLoaded.openingBalanceDate,
        creditTermsDays = supLoaded.creditTermsDays,
        creditLimitPaise = supLoaded.creditLimitPaise
      )
    )

    val supEntries = subledgerRepo.getEntriesForParty(PartyType.SUPPLIER, supId).first()
    assertEquals(2, supEntries.size)
    assertEquals(1, supEntries.count { it.isReversed })
    assertEquals(1, supEntries.count { !it.isReversed })
    assertEquals(750000L, SubledgerCalculationEngine.calculateSupplierBalance(supEntries))

    // Update to negative supplier opening balance (-₹1,000)
    supplierRepo.saveSupplier(
      Supplier(
        id = supId,
        name = supLoaded.name,
        contactPerson = supLoaded.contactPerson,
        phoneNumber = supLoaded.phoneNumber,
        address = supLoaded.address,
        openingBalancePaise = -100000L,
        openingBalanceDate = supLoaded.openingBalanceDate
      )
    )
    val supEntriesAfterNeg = subledgerRepo.getEntriesForParty(PartyType.SUPPLIER, supId).first()
    assertEquals(3, supEntriesAfterNeg.size)
    assertEquals(-100000L, SubledgerCalculationEngine.calculateSupplierBalance(supEntriesAfterNeg))

    // Customer opening balance update: ₹3,000 -> ₹0
    val custId = customerRepo.saveCustomer(
      Customer(shopName = "Corner Shop", ownerName = "K", phoneNumber = "", address = "", openingBalancePaise = 300000L)
    )
    val custLoaded = db.customerDao().getCustomerSync(custId)!!
    customerRepo.saveCustomer(
      Customer(
        id = custId,
        shopName = custLoaded.shopName,
        ownerName = custLoaded.ownerName,
        phoneNumber = custLoaded.phoneNumber,
        address = custLoaded.address,
        openingBalancePaise = 0L,
        openingBalanceDate = custLoaded.openingBalanceDate
      )
    )
    val custEntries = subledgerRepo.getEntriesForParty(PartyType.CUSTOMER, custId).first()
    assertEquals(1, custEntries.size)
    assertTrue(custEntries[0].isReversed)
    assertEquals(0L, SubledgerCalculationEngine.calculateCustomerBalance(custEntries))
  }

  @Test
  fun sequentialInvoiceNumberGeneration_andDuplicateRejection() = runBlocking {
    val productRepo = ProductRepository(db)
    val supplierRepo = SupplierRepository(db)
    val customerRepo = CustomerRepository(db)
    val purchaseRepo = PurchaseRepository(db)
    val salesRepo = SalesRepository(db)

    val now = 1700000000000L
    val prodId = productRepo.saveProductWithUnit(
      Product(name = "Choco Stick", sku = "CS-1", category = "Sticks/Bars", defaultPurchasePricePaise = 1000L, defaultSellingPricePaise = 1500L, minStockLevelPieces = 5),
      10, 5
    )
    val supId = supplierRepo.saveSupplier(Supplier(name = "S1", contactPerson = "", phoneNumber = "", address = ""))
    val custId = customerRepo.saveCustomer(Customer(shopName = "C1", ownerName = "", phoneNumber = "", address = ""))

    val firstPurNum = purchaseRepo.generateNextInvoiceNumber(now)
    assertTrue(firstPurNum.endsWith("-0001"))

    purchaseRepo.createPurchaseInvoice(
      supplierId = supId,
      invoiceNumber = firstPurNum,
      invoiceDate = now,
      dueDate = now + 10 * dayMs,
      items = listOf(PurchaseInvoiceItem(productId = prodId, batchNumber = "B1", expiryDate = now + 90 * dayMs, quantityPieces = 30, costPerPiecePaise = 1000L, lineTotalPaise = 30000L)),
      paidAmountPaise = 0L,
      paymentMethod = null,
      notes = ""
    )

    val secondPurNum = purchaseRepo.generateNextInvoiceNumber(now)
    assertTrue(secondPurNum.endsWith("-0002"))

    var dupPurRejected = false
    try {
      purchaseRepo.createPurchaseInvoice(
        supplierId = supId,
        invoiceNumber = firstPurNum,
        invoiceDate = now,
        dueDate = now + 10 * dayMs,
        items = listOf(PurchaseInvoiceItem(productId = prodId, batchNumber = "B2", expiryDate = now + 90 * dayMs, quantityPieces = 10, costPerPiecePaise = 1000L, lineTotalPaise = 10000L)),
        paidAmountPaise = 0L,
        paymentMethod = null,
        notes = ""
      )
    } catch (_: IllegalStateException) {
      dupPurRejected = true
    }
    assertTrue(dupPurRejected)
    assertEquals(30, db.productBatchDao().getActiveBatchesForProductSync(prodId).sumOf { it.currentQuantityPieces })

    val firstSaleNum = salesRepo.generateNextInvoiceNumber(now)
    assertTrue(firstSaleNum.endsWith("-0001"))
    salesRepo.createSalesInvoice(
      customerId = custId,
      invoiceNumber = firstSaleNum,
      invoiceDate = now,
      dueDate = now + 7 * dayMs,
      requestedLines = listOf(SalesRepository.RequestedSaleLine(prodId, 5, 1500L)),
      paidAmountPaise = 0L,
      paymentMethod = null,
      notes = ""
    )
    assertTrue(salesRepo.generateNextInvoiceNumber(now).endsWith("-0002"))
  }

  @Test
  fun uiAndViewModelErrors_areSurfacedVisiblyAndNotSwallowed() {
    val productRepo = ProductRepository(db)
    val supplierRepo = SupplierRepository(db)
    val purchaseRepo = PurchaseRepository(db)
    val inventoryRepo = InventoryRepository(db)

    runBlocking {
      productRepo.saveProductWithUnit(
        Product(name = "Mango Bar", sku = "MB-10", category = "Sticks/Bars", defaultPurchasePricePaise = 1000L, defaultSellingPricePaise = 1500L, minStockLevelPieces = 5),
        10, 5
      )
      supplierRepo.saveSupplier(Supplier(name = "Cream Dairy", contactPerson = "Ali", phoneNumber = "", address = ""))
    }

    // InventoryViewModel surfaces error when adjusting nonexistent batch
    val invVm = InventoryViewModel(inventoryRepo, productRepo)
    var invErr: String? = null
    invVm.adjustStock(batchId = 99999L, quantityPieces = 5, reason = "Test", onError = { invErr = it })
    val invDeadline = System.currentTimeMillis() + 3000L
    while (invErr == null && System.currentTimeMillis() < invDeadline) {
      ShadowLooper.idleMainLooper()
      Thread.sleep(10)
    }
    assertNotNull("InventoryViewModel must forward error via onError", invErr)

    // PurchaseManagementScreen surfaces dialog error when submitting empty or duplicate invoice instead of swallowing
    val purchaseVm = PurchaseViewModel(purchaseRepo, supplierRepo, productRepo)
    composeTestRule.setContent {
      IceTrackTheme {
        PurchaseManagementScreen(viewModel = purchaseVm)
      }
    }
    composeTestRule.waitUntil(timeoutMillis = 5000L) {
      ShadowLooper.idleMainLooper()
      purchaseVm.suppliers.value.isNotEmpty() && purchaseVm.products.value.isNotEmpty()
    }

    composeTestRule.onNodeWithTag("new_purchase_fab").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Confirm & Receive Stock").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("purchase_dialog_error").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Please add at least one line item").assertIsDisplayed()
  }
}
