package com.example

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.DatabaseMigrations
import com.example.data.local.IceTrackDatabase
import com.example.data.repository.CreditLimitExceededException
import com.example.data.repository.CustomerRepository
import com.example.data.repository.ExpenseRepository
import com.example.data.repository.InventoryRepository
import com.example.data.repository.ProductRepository
import com.example.data.repository.PurchaseRepository
import com.example.data.repository.SalesRepository
import com.example.data.repository.SubledgerRepository
import com.example.data.repository.SupplierRepository
import com.example.domain.engine.BusinessReportEngine
import com.example.domain.engine.FefoInventoryEngine
import com.example.domain.engine.PaymentAllocationEngine
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.model.Customer
import com.example.domain.model.PartyType
import com.example.domain.model.PaymentAllocationType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentStatus
import com.example.domain.model.Product
import com.example.domain.model.PurchaseInvoiceItem
import com.example.domain.model.StockMovementType
import com.example.domain.model.SubledgerEntryType
import com.example.domain.model.Supplier
import com.example.ui.viewmodel.DashboardViewModel
import com.example.ui.viewmodel.InventoryViewModel
import com.example.ui.viewmodel.ProductViewModel
import com.example.ui.viewmodel.ReportsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase2FinanceIntegrationAndMigrationTest {

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
  fun migration2To3_populatedDatabaseWithOverpaidInvoicesAndUnallocatedPayments_reconcilesAndValidatesSchema() {
    val dbName = "icetrack_migration_v2_v3_test.db"
    val now = 1710000000000L

    // 1. Create and populate Version 2 database with:
    //    - Active master data + batch
    //    - Dual invoice ID=1 collision (sales_invoices id=1 and purchase_invoices id=1)
    //    - Overpaid V2 sales invoice (#1: total 10,000, paid 15,000 -> 5,000 advance)
    //    - Overpaid V2 purchase invoice (#1: total 8,000, paid 11,000 -> 3,000 advance)
    //    - Unallocated V2 standalone customer PAYMENT subledger entry (8,000 -> 8,000 advance)
    migrationHelper.createDatabase(dbName, 2).apply {
      execSQL(
        "INSERT INTO products (id, name, sku, category, defaultPurchasePricePaise, defaultSellingPricePaise, minStockLevelPieces, isActive, createdAt, deletedAt) " +
          "VALUES (1, 'Kulfi Classic', 'KF-01', 'Sticks/Bars', 1000, 1500, 10, 1, $now, NULL)"
      )
      execSQL("INSERT INTO product_units (id, productId, piecesPerBox, boxesPerCarton) VALUES (1, 1, 20, 5)")
      execSQL(
        "INSERT INTO suppliers (id, name, contactPerson, phoneNumber, address, openingBalancePaise, openingBalanceDate, creditTermsDays, creditLimitPaise, isActive, createdAt, deletedAt) " +
          "VALUES (1, 'Kochi Creamery', 'Manoj', '9847111222', 'Kochi', 0, $now, 15, 0, 1, $now, NULL)"
      )
      execSQL(
        "INSERT INTO customers (id, shopName, ownerName, phoneNumber, address, openingBalancePaise, openingBalanceDate, creditLimitPaise, paymentTermsDays, isActive, createdAt, deletedAt) " +
          "VALUES (1, 'Calicut Mart', 'Faisal', '9847333444', 'Calicut', 0, $now, 0, 7, 1, $now, NULL)"
      )
      // Overpaid Purchase Invoice id = 1
      execSQL(
        "INSERT INTO purchase_invoices (id, invoiceNumber, supplierId, invoiceDate, dueDate, totalAmountPaise, paidAmountPaise, paymentStatus, paymentMethod, notes, isConfirmed, createdAt) " +
          "VALUES (1, 'PUR-V2-001', 1, $now, ${now + 15 * dayMs}, 8000, 11000, 'PAID', 'BANK_TRANSFER', 'Overpaid V2 bill', 1, $now)"
      )
      // Overpaid Sales Invoice id = 1 (collides on numeric id=1 with purchase_invoices!)
      execSQL(
        "INSERT INTO sales_invoices (id, invoiceNumber, customerId, invoiceDate, dueDate, totalAmountPaise, paidAmountPaise, paymentStatus, paymentMethod, notes, isConfirmed, createdAt) " +
          "VALUES (1, 'INV-V2-001', 1, $now, ${now + 7 * dayMs}, 10000, 15000, 'PAID', 'CASH', 'Overpaid V2 sale', 1, $now)"
      )
      // Subledger entries (must remain unaltered!)
      execSQL(
        "INSERT INTO subledger_entries (id, partyType, partyId, entryType, referenceType, referenceId, debitPaise, creditPaise, entryDate, createdAt, notes, isReversed) VALUES " +
          "(1, 'SUPPLIER', 1, 'INVOICE', 'PURCHASE_INVOICE', 1, 0, 8000, $now, $now, 'Pur #1', 0)," +
          "(2, 'SUPPLIER', 1, 'PAYMENT', 'PURCHASE_PAYMENT', 1, 11000, 0, $now, $now, 'Pur Pay #1', 0)," +
          "(3, 'CUSTOMER', 1, 'INVOICE', 'SALES_INVOICE', 1, 10000, 0, $now, $now, 'Sale #1', 0)," +
          "(4, 'CUSTOMER', 1, 'PAYMENT', 'SALES_PAYMENT', 1, 0, 15000, $now, $now, 'Sale Pay #1', 0)," +
          "(5, 'CUSTOMER', 1, 'PAYMENT', 'CUSTOMER_PAYMENT', 1, 0, 8000, ${now + 1000L}, ${now + 1000L}, 'Standalone unallocated V2 payment', 0)"
      )
      // V2 payment_allocations (no partyType/partyId/allocationType columns)
      execSQL(
        "INSERT INTO payment_allocations (id, paymentId, invoiceId, allocatedAmountPaise, allocatedAt, isReversed) VALUES " +
          "(1, 2, 1, 11000, $now, 0)," +
          "(2, 4, 1, 15000, $now, 0)"
      )
      close()
    }

    // 2. Run MIGRATION_2_3 and validate schema against exported 3.json
    val migratedDb = migrationHelper.runMigrationsAndValidate(
      dbName,
      3,
      true,
      DatabaseMigrations.MIGRATION_2_3
    )

    // 3. Verify subledger_entries are completely unchanged (net customer = 10,000 - 23,000 = -13,000; supplier = 8,000 - 11,000 = -3,000)
    migratedDb.query("SELECT COALESCE(SUM(debitPaise), 0), COALESCE(SUM(creditPaise), 0) FROM subledger_entries WHERE partyType = 'CUSTOMER' AND partyId = 1 AND isReversed = 0").use { c ->
      assertTrue(c.moveToFirst())
      assertEquals(10000L, c.getLong(0))
      assertEquals(23000L, c.getLong(1))
    }
    migratedDb.query("SELECT COALESCE(SUM(debitPaise), 0), COALESCE(SUM(creditPaise), 0) FROM subledger_entries WHERE partyType = 'SUPPLIER' AND partyId = 1 AND isReversed = 0").use { c ->
      assertTrue(c.moveToFirst())
      assertEquals(11000L, c.getLong(0))
      assertEquals(8000L, c.getLong(1))
    }

    // 4. Verify overpaid invoices capped at totalAmountPaise
    migratedDb.query("SELECT paidAmountPaise, paymentStatus FROM sales_invoices WHERE id = 1").use { c ->
      assertTrue(c.moveToFirst())
      assertEquals(10000L, c.getLong(0))
      assertEquals("PAID", c.getString(1))
    }
    migratedDb.query("SELECT paidAmountPaise, paymentStatus FROM purchase_invoices WHERE id = 1").use { c ->
      assertTrue(c.moveToFirst())
      assertEquals(8000L, c.getLong(0))
      assertEquals("PAID", c.getString(1))
    }

    // 5. Verify payment_allocations split + dual-ID isolation + unallocated V2 payment advance
    migratedDb.query("SELECT COALESCE(SUM(allocatedAmountPaise), 0) FROM payment_allocations WHERE partyType = 'CUSTOMER' AND allocationType = 'INVOICE' AND invoiceId = 1 AND isReversed = 0").use { c ->
      assertTrue(c.moveToFirst())
      assertEquals(10000L, c.getLong(0))
    }
    migratedDb.query("SELECT COALESCE(SUM(allocatedAmountPaise), 0) FROM payment_allocations WHERE partyType = 'SUPPLIER' AND allocationType = 'INVOICE' AND invoiceId = 1 AND isReversed = 0").use { c ->
      assertTrue(c.moveToFirst())
      assertEquals(8000L, c.getLong(0))
    }
    // Customer total ADVANCE = 5,000 (from overpaid Sale #1) + 8,000 (from standalone payment #5) = 13,000
    migratedDb.query("SELECT COALESCE(SUM(allocatedAmountPaise), 0) FROM payment_allocations WHERE partyType = 'CUSTOMER' AND allocationType = 'ADVANCE' AND invoiceId IS NULL AND isReversed = 0").use { c ->
      assertTrue(c.moveToFirst())
      assertEquals(13000L, c.getLong(0))
    }
    // Supplier total ADVANCE = 3,000 (from overpaid Purchase #1)
    migratedDb.query("SELECT COALESCE(SUM(allocatedAmountPaise), 0) FROM payment_allocations WHERE partyType = 'SUPPLIER' AND allocationType = 'ADVANCE' AND invoiceId IS NULL AND isReversed = 0").use { c ->
      assertTrue(c.moveToFirst())
      assertEquals(3000L, c.getLong(0))
    }
    migratedDb.close()

    // 6. Verify Room IceTrackDatabase opens cleanly on V3 and DAOs read migrated allocations
    val roomDb = Room.databaseBuilder(context, IceTrackDatabase::class.java, dbName)
      .addMigrations(DatabaseMigrations.MIGRATION_1_2, DatabaseMigrations.MIGRATION_2_3)
      .allowMainThreadQueries()
      .build()
    try {
      runBlocking {
        val subledgerRepo = SubledgerRepository(roomDb)
        assertEquals(13000L, subledgerRepo.getAvailableAdvancePaiseSync(PartyType.CUSTOMER, 1L))
        assertEquals(3000L, subledgerRepo.getAvailableAdvancePaiseSync(PartyType.SUPPLIER, 1L))
        val custInvoiceAllocs = roomDb.paymentAllocationDao().getForInvoiceSync(PartyType.CUSTOMER, 1L)
        assertEquals(1, custInvoiceAllocs.size)
        assertEquals(10000L, custInvoiceAllocs[0].allocatedAmountPaise)
        val supInvoiceAllocs = roomDb.paymentAllocationDao().getForInvoiceSync(PartyType.SUPPLIER, 1L)
        assertEquals(1, supInvoiceAllocs.size)
        assertEquals(8000L, supInvoiceAllocs[0].allocatedAmountPaise)
      }
    } finally {
      roomDb.close()
      context.deleteDatabase(dbName)
    }
  }

  @Test
  fun overpayment_atInvoiceAndStandalonePayment_capsInvoicePaidAndTracksAdvance() = runBlocking {
    val productRepo = ProductRepository(db)
    val supplierRepo = SupplierRepository(db)
    val customerRepo = CustomerRepository(db)
    val purchaseRepo = PurchaseRepository(db)
    val salesRepo = SalesRepository(db)
    val subledgerRepo = SubledgerRepository(db)

    val now = 1710000000000L
    val prodId = productRepo.saveProductWithUnit(
      Product(name = "Choco Cone", sku = "CC-10", category = "Cones", defaultPurchasePricePaise = 1000L, defaultSellingPricePaise = 1500L, minStockLevelPieces = 5),
      10, 5
    )
    val supId = supplierRepo.saveSupplier(Supplier(name = "Sup Alpha", contactPerson = "", phoneNumber = "", address = ""))
    val custId = customerRepo.saveCustomer(Customer(shopName = "Shop Alpha", ownerName = "", phoneNumber = "", address = ""))

    // Purchase invoice total ₹10,000 (100 * 100), pay ₹12,500 at purchase time -> ₹2,500 advance
    val purId = purchaseRepo.createPurchaseInvoice(
      supplierId = supId,
      invoiceNumber = "PUR-OV-1",
      invoiceDate = now,
      dueDate = 0L,
      items = listOf(PurchaseInvoiceItem(productId = prodId, batchNumber = "B-OV", expiryDate = now + 60 * dayMs, quantityPieces = 100, costPerPiecePaise = 1000L, lineTotalPaise = 100000L)),
      paidAmountPaise = 125000L,
      paymentMethod = PaymentMethod.BANK_TRANSFER,
      notes = "Overpaid bill"
    )
    val purInvoice = purchaseRepo.allPurchases.first().first { it.id == purId }
    assertEquals(100000L, purInvoice.totalAmountPaise)
    assertEquals(100000L, purInvoice.paidAmountPaise)
    assertEquals(PaymentStatus.PAID, purInvoice.paymentStatus)
    assertEquals(25000L, subledgerRepo.getAvailableAdvancePaiseSync(PartyType.SUPPLIER, supId))

    val supBalance = SubledgerCalculationEngine.calculateSupplierBalance(subledgerRepo.getEntriesForParty(PartyType.SUPPLIER, supId).first())
    assertEquals(-25000L, supBalance)

    // Sales invoice total ₹30,000 (20 * 1500), customer pays ₹40,000 -> ₹10,000 advance
    val saleId = salesRepo.createSalesInvoice(
      customerId = custId,
      invoiceNumber = "INV-OV-1",
      invoiceDate = now,
      dueDate = 0L,
      requestedLines = listOf(SalesRepository.RequestedSaleLine(prodId, 20, 1500L)),
      paidAmountPaise = 40000L,
      paymentMethod = PaymentMethod.CASH,
      notes = "Overpaid sale"
    )
    val saleInvoice = salesRepo.allSales.first().first { it.id == saleId }
    assertEquals(30000L, saleInvoice.totalAmountPaise)
    assertEquals(30000L, saleInvoice.paidAmountPaise)
    assertEquals(PaymentStatus.PAID, saleInvoice.paymentStatus)
    assertEquals(10000L, subledgerRepo.getAvailableAdvancePaiseSync(PartyType.CUSTOMER, custId))

    // Excess standalone customer payment (+₹5,000) -> total advance ₹15,000
    salesRepo.recordCustomerPayment(
      customerId = custId,
      amountPaise = 5000L,
      paymentDate = now + 1000L,
      paymentMethod = PaymentMethod.UPI,
      notes = "Extra advance"
    )
    assertEquals(15000L, subledgerRepo.getAvailableAdvancePaiseSync(PartyType.CUSTOMER, custId))
    val custBalance = SubledgerCalculationEngine.calculateCustomerBalance(subledgerRepo.getEntriesForParty(PartyType.CUSTOMER, custId).first())
    assertEquals(-15000L, custBalance)
  }

  @Test
  fun openingBalance_fifoSettlementAndAdvanceSplitting_reconcilesSubledgerAndInvoices() = runBlocking {
    val productRepo = ProductRepository(db)
    val supplierRepo = SupplierRepository(db)
    val customerRepo = CustomerRepository(db)
    val purchaseRepo = PurchaseRepository(db)
    val salesRepo = SalesRepository(db)
    val subledgerRepo = SubledgerRepository(db)

    val t0 = 1710000000000L
    val prodId = productRepo.saveProductWithUnit(
      Product(name = "Mango Cup", sku = "MC-01", category = "Cups", defaultPurchasePricePaise = 500L, defaultSellingPricePaise = 1000L, minStockLevelPieces = 5),
      10, 5
    )

    // Supplier with ₹400 opening balance at t0
    val supId = supplierRepo.saveSupplier(
      Supplier(name = "Sup OB", contactPerson = "", phoneNumber = "", address = "", openingBalancePaise = 40000L, openingBalanceDate = t0)
    )
    // Customer with ₹500 opening balance at t0
    val custId = customerRepo.saveCustomer(
      Customer(shopName = "Cust OB", ownerName = "", phoneNumber = "", address = "", openingBalancePaise = 50000L, openingBalanceDate = t0)
    )

    // Purchase Bill 1 at t0 + 1 day for ₹600 unpaid
    val pBill1 = purchaseRepo.createPurchaseInvoice(
      supplierId = supId,
      invoiceNumber = "PUR-OB-1",
      invoiceDate = t0 + dayMs,
      dueDate = 0L,
      items = listOf(PurchaseInvoiceItem(productId = prodId, batchNumber = "B-OB", expiryDate = t0 + 90 * dayMs, quantityPieces = 120, costPerPiecePaise = 500L, lineTotalPaise = 60000L)),
      paidAmountPaise = 0L,
      paymentMethod = null,
      notes = ""
    )

    // Pay Supplier ₹1,200 -> ₹400 settles OPENING_BALANCE, ₹600 settles Bill 1, ₹200 becomes ADVANCE
    purchaseRepo.recordSupplierPayment(supId, 120000L, t0 + 2 * dayMs, PaymentMethod.BANK_TRANSFER, "Settle OB + Bill + Adv")
    val supAllocs = subledgerRepo.getActiveAllocationsForParty(PartyType.SUPPLIER, supId).first()
    assertEquals(40000L, supAllocs.filter { it.allocationType == PaymentAllocationType.OPENING_BALANCE }.sumOf { it.allocatedAmountPaise })
    assertEquals(60000L, supAllocs.filter { it.allocationType == PaymentAllocationType.INVOICE && it.invoiceId == pBill1 }.sumOf { it.allocatedAmountPaise })
    assertEquals(20000L, subledgerRepo.getAvailableAdvancePaiseSync(PartyType.SUPPLIER, supId))

    // Sales Invoice 1 at t0 + 1 day for ₹400 unpaid
    val sInv1 = salesRepo.createSalesInvoice(
      customerId = custId,
      invoiceNumber = "INV-OB-1",
      invoiceDate = t0 + dayMs,
      dueDate = 0L,
      requestedLines = listOf(SalesRepository.RequestedSaleLine(prodId, 40, 1000L)),
      paidAmountPaise = 0L,
      paymentMethod = null,
      notes = ""
    )

    // Customer pays ₹1,200 -> ₹500 settles OPENING_BALANCE, ₹400 settles Sale 1, ₹300 becomes ADVANCE
    salesRepo.recordCustomerPayment(custId, 120000L, t0 + 2 * dayMs, PaymentMethod.CASH, "Settle OB + Inv + Adv")
    val custAllocsAfterPay = subledgerRepo.getActiveAllocationsForParty(PartyType.CUSTOMER, custId).first()
    assertEquals(50000L, custAllocsAfterPay.filter { it.allocationType == PaymentAllocationType.OPENING_BALANCE }.sumOf { it.allocatedAmountPaise })
    assertEquals(40000L, custAllocsAfterPay.filter { it.allocationType == PaymentAllocationType.INVOICE && it.invoiceId == sInv1 }.sumOf { it.allocatedAmountPaise })
    assertEquals(30000L, subledgerRepo.getAvailableAdvancePaiseSync(PartyType.CUSTOMER, custId))
    assertEquals(PaymentStatus.PAID, salesRepo.allSales.first().first { it.id == sInv1 }.paymentStatus)

    // Create Sales Invoice 2 for ₹200 with applyAvailableAdvance = true -> consumes ₹200 of ₹300 advance, splits ₹100 remainder
    val paymentEntriesBefore = subledgerRepo.getEntriesForParty(PartyType.CUSTOMER, custId).first()
      .count { it.entryType == SubledgerEntryType.PAYMENT }

    val sInv2 = salesRepo.createSalesInvoice(
      customerId = custId,
      invoiceNumber = "INV-OB-2",
      invoiceDate = t0 + 3 * dayMs,
      dueDate = 0L,
      requestedLines = listOf(SalesRepository.RequestedSaleLine(prodId, 20, 1000L)),
      paidAmountPaise = 0L,
      paymentMethod = null,
      notes = "Uses partial advance",
      applyAvailableAdvance = true
    )
    val sale2 = salesRepo.allSales.first().first { it.id == sInv2 }
    assertEquals(20000L, sale2.paidAmountPaise)
    assertEquals(PaymentStatus.PAID, sale2.paymentStatus)
    assertEquals(10000L, subledgerRepo.getAvailableAdvancePaiseSync(PartyType.CUSTOMER, custId))
    // Must NOT create any duplicate PAYMENT subledger row
    val paymentEntriesAfter = subledgerRepo.getEntriesForParty(PartyType.CUSTOMER, custId).first()
      .count { it.entryType == SubledgerEntryType.PAYMENT }
    assertEquals(paymentEntriesBefore, paymentEntriesAfter)

    // Create Sales Invoice 3 for ₹250 with applyAvailableAdvance = false (unpaid), then apply remaining ₹100 advance explicitly
    val sInv3 = salesRepo.createSalesInvoice(
      customerId = custId,
      invoiceNumber = "INV-OB-3",
      invoiceDate = t0 + 4 * dayMs,
      dueDate = 0L,
      requestedLines = listOf(SalesRepository.RequestedSaleLine(prodId, 25, 1000L)),
      paidAmountPaise = 0L,
      paymentMethod = null,
      notes = "Leave advance untouched",
      applyAvailableAdvance = false
    )
    assertEquals(10000L, subledgerRepo.getAvailableAdvancePaiseSync(PartyType.CUSTOMER, custId))
    assertEquals(PaymentStatus.UNPAID, salesRepo.allSales.first().first { it.id == sInv3 }.paymentStatus)

    val applied = salesRepo.applyAvailableCustomerAdvance(custId, t0 + 5 * dayMs)
    assertEquals(10000L, applied)
    assertEquals(0L, subledgerRepo.getAvailableAdvancePaiseSync(PartyType.CUSTOMER, custId))
    val sale3After = salesRepo.allSales.first().first { it.id == sInv3 }
    assertEquals(10000L, sale3After.paidAmountPaise)
    assertEquals(PaymentStatus.PARTIALLY_PAID, sale3After.paymentStatus)

    // Final Subledger reconciliation: OB(500) + Inv1(400) + Inv2(200) + Inv3(250) - Payment(1200) = ₹150 receivable!
    val finalCustBalance = SubledgerCalculationEngine.calculateCustomerBalance(
      subledgerRepo.getEntriesForParty(PartyType.CUSTOMER, custId).first()
    )
    assertEquals(15000L, finalCustBalance)
  }

  @Test
  fun creditLimitAndAutoDueDate_forCustomerAndSupplier_enforcedAndOverridable() = runBlocking {
    val productRepo = ProductRepository(db)
    val supplierRepo = SupplierRepository(db)
    val customerRepo = CustomerRepository(db)
    val purchaseRepo = PurchaseRepository(db)
    val salesRepo = SalesRepository(db)

    val now = 1710000000000L
    val prodId = productRepo.saveProductWithUnit(
      Product(name = "Pista Tub", sku = "PT-1", category = "Family Tubs", defaultPurchasePricePaise = 10000L, defaultSellingPricePaise = 15000L, minStockLevelPieces = 2),
      10, 4
    )

    // Supplier credit limit = ₹500 (50,000 paise), creditTerms = 30 days
    val supId = supplierRepo.saveSupplier(
      Supplier(name = "Sup Limited", contactPerson = "", phoneNumber = "", address = "", creditTermsDays = 30, creditLimitPaise = 50000L)
    )
    // Customer credit limit = ₹400 (40,000 paise), paymentTerms = 14 days
    val custId = customerRepo.saveCustomer(
      Customer(shopName = "Cust Limited", ownerName = "", phoneNumber = "", address = "", creditLimitPaise = 40000L, paymentTermsDays = 14)
    )

    // Attempt Purchase ₹600 unpaid (> ₹500 limit) -> throws CreditLimitExceededException and rolls back
    var supBlocked = false
    try {
      purchaseRepo.createPurchaseInvoice(
        supplierId = supId,
        invoiceNumber = "PUR-CL-BLOCK",
        invoiceDate = now,
        dueDate = 0L,
        items = listOf(PurchaseInvoiceItem(productId = prodId, batchNumber = "B-CL", expiryDate = now + 90 * dayMs, quantityPieces = 6, costPerPiecePaise = 10000L, lineTotalPaise = 60000L)),
        paidAmountPaise = 0L,
        paymentMethod = null,
        notes = "",
        allowCreditLimitOverride = false
      )
    } catch (e: CreditLimitExceededException) {
      supBlocked = true
      assertEquals(10000L, e.assessment.exceededByPaise)
    }
    assertTrue(supBlocked)
    assertEquals(0, purchaseRepo.allPurchases.first().size)

    // Same purchase with ₹200 cash paid (exposure ₹400 <= ₹500) succeeds & computes dueDate = now + 30 days
    val purOkId = purchaseRepo.createPurchaseInvoice(
      supplierId = supId,
      invoiceNumber = "PUR-CL-OK",
      invoiceDate = now,
      dueDate = 0L,
      items = listOf(PurchaseInvoiceItem(productId = prodId, batchNumber = "B-CL", expiryDate = now + 90 * dayMs, quantityPieces = 6, costPerPiecePaise = 10000L, lineTotalPaise = 60000L)),
      paidAmountPaise = 20000L,
      paymentMethod = PaymentMethod.CASH,
      notes = ""
    )
    val savedPur = purchaseRepo.allPurchases.first().first { it.id == purOkId }
    assertEquals(PaymentAllocationEngine.calculateDueDate(now, 30), savedPur.dueDate)

    // Attempt Sale ₹450 unpaid (> ₹400 limit) -> throws CreditLimitExceededException & leaves 6 pcs intact
    var custBlocked = false
    try {
      salesRepo.createSalesInvoice(
        customerId = custId,
        invoiceNumber = "INV-CL-BLOCK",
        invoiceDate = now,
        dueDate = 0L,
        requestedLines = listOf(SalesRepository.RequestedSaleLine(prodId, 3, 15000L)),
        paidAmountPaise = 0L,
        paymentMethod = null,
        notes = "",
        allowCreditLimitOverride = false
      )
    } catch (e: CreditLimitExceededException) {
      custBlocked = true
      assertEquals(5000L, e.assessment.exceededByPaise)
    }
    assertTrue(custBlocked)
    assertEquals(6, db.productBatchDao().getActiveBatchesForProductSync(prodId).sumOf { it.currentQuantityPieces })

    // Retry with explicit allowCreditLimitOverride = true -> succeeds & computes dueDate = now + 14 days
    val saleOverrideId = salesRepo.createSalesInvoice(
      customerId = custId,
      invoiceNumber = "INV-CL-OVR",
      invoiceDate = now,
      dueDate = 0L,
      requestedLines = listOf(SalesRepository.RequestedSaleLine(prodId, 3, 15000L)),
      paidAmountPaise = 0L,
      paymentMethod = null,
      notes = "Manager approved override",
      allowCreditLimitOverride = true
    )
    val savedSale = salesRepo.allSales.first().first { it.id == saleOverrideId }
    assertEquals(PaymentAllocationEngine.calculateDueDate(now, 14), savedSale.dueDate)
  }

  @Test
  fun saleDispatchStockMovement_recordsActualBatchPurchaseCostPerPiece() = runBlocking {
    val productRepo = ProductRepository(db)
    val supplierRepo = SupplierRepository(db)
    val customerRepo = CustomerRepository(db)
    val purchaseRepo = PurchaseRepository(db)
    val salesRepo = SalesRepository(db)
    val inventoryRepo = InventoryRepository(db)

    val now = 1710000000000L
    val prodId = productRepo.saveProductWithUnit(
      Product(name = "Belgian Dark", sku = "BD-01", category = "Sticks/Bars", defaultPurchasePricePaise = 1000L, defaultSellingPricePaise = 2000L, minStockLevelPieces = 5),
      10, 5
    )
    val supId = supplierRepo.saveSupplier(Supplier(name = "Sup", contactPerson = "", phoneNumber = "", address = ""))
    val custId = customerRepo.saveCustomer(Customer(shopName = "Cust", ownerName = "", phoneNumber = "", address = ""))

    // Batch 1 earlier expiry @ ₹10 (1000 paise), Batch 2 later expiry @ ₹12 (1200 paise)
    purchaseRepo.createPurchaseInvoice(
      supplierId = supId,
      invoiceNumber = "PUR-COST-1",
      invoiceDate = now,
      dueDate = 0L,
      items = listOf(
        PurchaseInvoiceItem(productId = prodId, batchNumber = "B-EARLY", expiryDate = now + 10 * dayMs, quantityPieces = 5, costPerPiecePaise = 1000L, lineTotalPaise = 5000L),
        PurchaseInvoiceItem(productId = prodId, batchNumber = "B-LATE", expiryDate = now + 30 * dayMs, quantityPieces = 10, costPerPiecePaise = 1200L, lineTotalPaise = 12000L)
      ),
      paidAmountPaise = 0L,
      paymentMethod = null,
      notes = ""
    )

    // Sell 8 pcs at selling price ₹20 (2000 paise) -> FEFO consumes 5 from B-EARLY + 3 from B-LATE
    salesRepo.createSalesInvoice(
      customerId = custId,
      invoiceNumber = "INV-COST-1",
      invoiceDate = now,
      dueDate = 0L,
      requestedLines = listOf(SalesRepository.RequestedSaleLine(prodId, 8, 2000L)),
      paidAmountPaise = 16000L,
      paymentMethod = PaymentMethod.CASH,
      notes = ""
    )

    val dispatchMovements = inventoryRepo.getMovementsForProduct(prodId).first()
      .filter { it.movementType == StockMovementType.SALE_DISPATCH }
    assertEquals(2, dispatchMovements.size)
    val costs = dispatchMovements.map { it.unitCostPaise to it.quantityPieces }.toSet()
    assertTrue("Movement must record 1000 paise cost for -5 pcs", (1000L to -5) in costs)
    assertTrue("Movement must record 1200 paise cost for -3 pcs", (1200L to -3) in costs)
    assertFalse("Movement must never record selling price (2000)", dispatchMovements.any { it.unitCostPaise == 2000L })
  }

  @Test
  fun ssotInventoryValuationLowStockTodayFilterAndArchivedHistory_consistentAcrossViewModels() = runBlocking {
    val productRepo = ProductRepository(db)
    val supplierRepo = SupplierRepository(db)
    val customerRepo = CustomerRepository(db)
    val purchaseRepo = PurchaseRepository(db)
    val salesRepo = SalesRepository(db)
    val inventoryRepo = InventoryRepository(db)
    val expenseRepo = ExpenseRepository(db)
    val subledgerRepo = SubledgerRepository(db)

    val now = System.currentTimeMillis()
    val prodActive = productRepo.saveProductWithUnit(
      Product(name = "Active Bar", sku = "ACT-1", category = "Sticks/Bars", defaultPurchasePricePaise = 1000L, defaultSellingPricePaise = 1800L, minStockLevelPieces = 10),
      10, 5
    )
    val prodToArchive = productRepo.saveProductWithUnit(
      Product(name = "Seasonal Mango", sku = "SEA-1", category = "Cups", defaultPurchasePricePaise = 800L, defaultSellingPricePaise = 1400L, minStockLevelPieces = 5),
      10, 5
    )
    val supId = supplierRepo.saveSupplier(Supplier(name = "Archive Sup", contactPerson = "", phoneNumber = "", address = ""))
    val custId = customerRepo.saveCustomer(Customer(shopName = "Archive Shop", ownerName = "", phoneNumber = "", address = ""))

    // Receive: 20 expired pcs of Active Bar (cost ₹10 = ₹200) + 5 sellable pcs of Active Bar (cost ₹10 = ₹50, < minStock 10 -> LOW STOCK!)
    // + 10 sellable pcs of Seasonal Mango (cost ₹8 = ₹80)
    purchaseRepo.createPurchaseInvoice(
      supplierId = supId,
      invoiceNumber = "PUR-SSOT-1",
      invoiceDate = now - 3 * dayMs,
      dueDate = 0L,
      items = listOf(
        PurchaseInvoiceItem(productId = prodActive, batchNumber = "B-EXP", expiryDate = now - 2 * dayMs, quantityPieces = 20, costPerPiecePaise = 1000L, lineTotalPaise = 20000L),
        PurchaseInvoiceItem(productId = prodActive, batchNumber = "B-GOOD", expiryDate = now + 30 * dayMs, quantityPieces = 5, costPerPiecePaise = 1000L, lineTotalPaise = 5000L),
        PurchaseInvoiceItem(productId = prodToArchive, batchNumber = "B-MANGO", expiryDate = now + 30 * dayMs, quantityPieces = 10, costPerPiecePaise = 800L, lineTotalPaise = 8000L)
      ),
      paidAmountPaise = 0L,
      paymentMethod = null,
      notes = ""
    )

    // Yesterday's sale (excluded from TODAY filter) + Today's sale of Seasonal Mango (5 pcs @ ₹14 = ₹70, COGS ₹40)
    val calYesterday = Calendar.getInstance().apply {
      timeInMillis = now
      add(Calendar.DAY_OF_YEAR, -1)
    }
    salesRepo.createSalesInvoice(
      customerId = custId,
      invoiceNumber = "INV-YEST",
      invoiceDate = calYesterday.timeInMillis,
      dueDate = 0L,
      requestedLines = listOf(SalesRepository.RequestedSaleLine(prodToArchive, 2, 1400L)),
      paidAmountPaise = 0L,
      paymentMethod = null,
      notes = "Yesterday"
    )
    salesRepo.createSalesInvoice(
      customerId = custId,
      invoiceNumber = "INV-TODAY",
      invoiceDate = now,
      dueDate = 0L,
      requestedLines = listOf(SalesRepository.RequestedSaleLine(prodToArchive, 5, 1400L)),
      paidAmountPaise = 0L,
      paymentMethod = null,
      notes = "Today"
    )

    // Soft-delete Seasonal Mango, Customer, Supplier (master records archived)
    productRepo.softDelete(prodToArchive)
    customerRepo.softDelete(custId)
    supplierRepo.softDelete(supId)

    val dashboardVm = DashboardViewModel(productRepo, supplierRepo, customerRepo, subledgerRepo, salesRepo, purchaseRepo, inventoryRepo)
    val inventoryVm = InventoryViewModel(inventoryRepo, productRepo)
    val productVm = ProductViewModel(productRepo, supplierRepo, inventoryRepo)
    val reportsVm = ReportsViewModel(salesRepo, purchaseRepo, productRepo, customerRepo, supplierRepo, inventoryRepo, expenseRepo, subledgerRepo)

    val dashState = awaitState(dashboardVm.uiState) { it.productCount == 1 && it.todaySalesCount == 1 }
    val invState = awaitState(inventoryVm.uiState) { it.stockSummaries.size == 1 && it.sellableStockValuePaise > 0L }
    val prodValuation = awaitState(productVm.inventoryValuation) { it.sellableValuePaise > 0L }
    reportsVm.setDateRange("TODAY")
    val reportsToday = awaitState(reportsVm.uiState) { it.selectedDateRange == "TODAY" && it.salesInvoices.isNotEmpty() }

    // 1. Sellable vs Expired stock valuation SSOT:
    //    Active Bar sellable = 5 * 1000 = 5,000
    //    Seasonal Mango sellable remaining = 3 * 800 = 2,400 -> Total sellable = 7,400 paise
    //    Active Bar expired = 20 * 1000 = 20,000 paise
    assertEquals(7400L, dashState.sellableStockValuePaise)
    assertEquals(20000L, dashState.expiredStockValuePaise)
    assertEquals(dashState.sellableStockValuePaise, invState.sellableStockValuePaise)
    assertEquals(dashState.expiredStockValuePaise, invState.expiredStockValuePaise)
    assertEquals(dashState.sellableStockValuePaise, prodValuation.sellableValuePaise)
    assertEquals(dashState.sellableStockValuePaise, reportsToday.sellableStockValuePaise)

    // 2. Low-stock SSOT: Active Bar has 5 sellable pcs (< 10 min), 20 expired pcs must NOT hide low stock!
    assertEquals(1, dashState.lowStockCount)
    assertEquals(1, invState.lowStockItemCount)
    assertTrue(invState.stockSummaries.first { it.product.id == prodActive }.isLowStock)

    // 3. TODAY filter parity between Dashboard and Reports
    assertEquals(7000L, dashState.todaySalesPaise)
    assertEquals(7000L, reportsToday.profitLossSummary.totalSalesRevenuePaise)
    assertEquals(4000L, reportsToday.profitLossSummary.totalCogsPaise)
    assertEquals(3000L, reportsToday.profitLossSummary.grossProfitPaise)

    // 4. Archived entities retained in historical Reports & Statements (ALL range)
    reportsVm.setDateRange("ALL")
    val reportsAll = awaitState(reportsVm.uiState) { it.selectedDateRange == "ALL" && it.salesInvoices.size == 2 }
    assertEquals(9800L, reportsAll.profitLossSummary.totalSalesRevenuePaise) // (2 + 5) * 1400
    assertEquals(5600L, reportsAll.profitLossSummary.totalCogsPaise) // (2 + 5) * 800
    assertTrue(reportsAll.products.any { it.id == prodToArchive && !it.isActive })
    assertTrue(reportsAll.customers.any { it.id == custId && !it.isActive })
    assertTrue(reportsAll.suppliers.any { it.id == supId && !it.isActive })
  }

  private fun <T> awaitState(
    flow: StateFlow<T>,
    timeoutMs: Long = 5000L,
    predicate: (T) -> Boolean
  ): T {
    val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    val job = scope.launch { flow.collect {} }
    try {
      val deadline = System.currentTimeMillis() + timeoutMs
      while (!predicate(flow.value) && System.currentTimeMillis() < deadline) {
        ShadowLooper.idleMainLooper()
        Thread.sleep(15)
      }
      assertTrue("Timed out waiting for StateFlow predicate; last=${flow.value}", predicate(flow.value))
      return flow.value
    } finally {
      job.cancel()
      scope.cancel()
    }
  }
}
