package com.example

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.DatabaseMigrations
import com.example.data.local.IceTrackDatabase
import com.example.data.repository.CustomerRepository
import com.example.data.repository.HandoffPreparationResult
import com.example.data.repository.PaymentReminderRepository
import com.example.data.repository.PrepareDraftResult
import com.example.data.repository.ProductRepository
import com.example.data.repository.PurchaseRepository
import com.example.data.repository.SalesRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.SubledgerRepository
import com.example.data.repository.SupplierRepository
import com.example.domain.engine.PaymentReminderEngine
import com.example.domain.engine.ReminderCompositionResult
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.model.Customer
import com.example.domain.model.PartyType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentStatus
import com.example.domain.model.Product
import com.example.domain.model.PurchaseInvoiceItem
import com.example.domain.model.ReminderDueKind
import com.example.domain.model.ReminderStatus
import com.example.domain.model.SalesInvoice
import com.example.domain.model.SubledgerEntry
import com.example.domain.model.SubledgerEntryType
import com.example.domain.model.Supplier
import com.example.service.WhatsAppIntentHelper
import com.example.ui.viewmodel.PaymentReminderViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class CustomerPaymentReminderEngineAndMigrationTest {

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
  fun phoneNormalization_indianAndInternationalNumbers_normalizesAndRejectsInvalid(): Unit {
    assertEquals("+919876543210", PaymentReminderEngine.normalizeAndValidatePhone("9876543210").normalizedE164)
    assertEquals("919876543210", PaymentReminderEngine.normalizeAndValidatePhone("9876543210").whatsappPhoneDigits)
    assertEquals("+919876543210", PaymentReminderEngine.normalizeAndValidatePhone("09876543210").normalizedE164)
    assertEquals("+919876543210", PaymentReminderEngine.normalizeAndValidatePhone("919876543210").normalizedE164)
    assertEquals("+919876543210", PaymentReminderEngine.normalizeAndValidatePhone("+91 98765-43210").normalizedE164)
    assertEquals("+919876543210", PaymentReminderEngine.normalizeAndValidatePhone("0091 9876543210").normalizedE164)
    assertEquals("+971501234567", PaymentReminderEngine.normalizeAndValidatePhone("+971 50 123 4567").normalizedE164)

    assertFalse(PaymentReminderEngine.normalizeAndValidatePhone("").isValid)
    assertFalse(PaymentReminderEngine.normalizeAndValidatePhone("   ").isValid)
    assertFalse(PaymentReminderEngine.normalizeAndValidatePhone("9876543210, 9876500000").isValid)
    assertFalse(PaymentReminderEngine.normalizeAndValidatePhone("9876543210 / 9876500000").isValid)
    assertFalse(PaymentReminderEngine.normalizeAndValidatePhone("04842345678").isValid)
    assertFalse(PaymentReminderEngine.normalizeAndValidatePhone("5123456789").isValid)
    assertFalse(PaymentReminderEngine.normalizeAndValidatePhone("+915123456789").isValid)
    assertFalse(PaymentReminderEngine.normalizeAndValidatePhone("98765ABCDE").isValid)
  }

  @Test
  fun composeReminder_invoiceBased_ordersOldestFirstExcludesPaidAndExcludesItemDetails(): Unit {
    val customer = Customer(
      id = 7L,
      shopName = "Malabar Cream Shop",
      ownerName = "Naufal",
      phoneNumber = "9876543210",
      address = "MG Road"
    )
    val t1 = 1710000000000L // oldest
    val t2 = t1 + 3 * dayMs
    val t3 = t1 + 5 * dayMs

    val entries = listOf(
      SubledgerEntry(1L, PartyType.CUSTOMER, 7L, SubledgerEntryType.INVOICE, "SALES_INVOICE", 10L, debitPaise = 50000L, creditPaise = 0L, entryDate = t2),
      SubledgerEntry(2L, PartyType.CUSTOMER, 7L, SubledgerEntryType.INVOICE, "SALES_INVOICE", 11L, debitPaise = 30000L, creditPaise = 0L, entryDate = t1),
      SubledgerEntry(3L, PartyType.CUSTOMER, 7L, SubledgerEntryType.INVOICE, "SALES_INVOICE", 12L, debitPaise = 40000L, creditPaise = 0L, entryDate = t3),
      SubledgerEntry(4L, PartyType.CUSTOMER, 7L, SubledgerEntryType.PAYMENT, "PAYMENT", 12L, debitPaise = 0L, creditPaise = 40000L, entryDate = t3),
      SubledgerEntry(5L, PartyType.CUSTOMER, 7L, SubledgerEntryType.PAYMENT, "PAYMENT", 11L, debitPaise = 0L, creditPaise = 10000L, entryDate = t2)
    )
    val invoices = listOf(
      SalesInvoice(12L, "INV-PAID-3", 7L, t3, t3 + 7 * dayMs, 40000L, 40000L, PaymentStatus.PAID),
      SalesInvoice(10L, "INV-NEW-2", 7L, t2, t2 + 7 * dayMs, 50000L, 0L, PaymentStatus.UNPAID),
      SalesInvoice(11L, "INV-OLD-1", 7L, t1, t1 + 7 * dayMs, 30000L, 10000L, PaymentStatus.PARTIALLY_PAID)
    )

    val result = PaymentReminderEngine.composeReminder(
      customer = customer,
      businessName = "IceTrack Kerala",
      subledgerEntries = entries,
      customerSalesInvoices = invoices
    )

    assertTrue(result is ReminderCompositionResult.Ready)
    val composed = (result as ReminderCompositionResult.Ready).reminder
    assertEquals(ReminderDueKind.INVOICE_BASED, composed.dueKind)
    assertEquals("+919876543210", composed.targetPhoneE164)
    assertEquals(listOf(11L, 10L), composed.pendingInvoices.map { it.salesInvoiceId })
    assertEquals(80000L, composed.totalInvoiceAmountPaise)
    assertEquals(70000L, composed.totalInvoicePendingPaise)
    assertEquals(70000L, composed.netOutstandingPaise)
    assertEquals(0L, composed.ledgerDifferencePaise)

    val msg = composed.renderedMessage
    val firstDateIdx = msg.indexOf(SubledgerCalculationEngine.formatDate(t1))
    val secondDateIdx = msg.indexOf(SubledgerCalculationEngine.formatDate(t2))
    assertTrue("Oldest invoice date must appear first", firstDateIdx in 0 until secondDateIdx)
    assertFalse("Must exclude paid invoice", msg.contains(SubledgerCalculationEngine.formatDate(t3)))
    assertTrue(msg.contains("Total Invoice Amount: ₹800.00"))
    assertTrue(msg.contains("Total Outstanding Amount: ₹700.00"))
    assertFalse(msg.contains("SKU", ignoreCase = true))
    assertFalse(msg.contains("Batch", ignoreCase = true))
    assertFalse(msg.contains("Qty", ignoreCase = true))
  }

  @Test
  fun composeReminder_openingBalanceOnlyCustomer_createsAccountDueReminderWithoutFabricatingInvoice(): Unit {
    val customer = Customer(
      id = 9L,
      shopName = "Thrissur Dairy",
      ownerName = "Ramesh",
      phoneNumber = "09847001122",
      address = "Thrissur",
      openingBalancePaise = 150000L
    )
    val entries = listOf(
      SubledgerEntry(1L, PartyType.CUSTOMER, 9L, SubledgerEntryType.OPENING_BALANCE, "CUSTOMER_OPENING", 9L, debitPaise = 150000L, creditPaise = 0L, entryDate = 1710000000000L)
    )

    val result = PaymentReminderEngine.composeReminder(
      customer = customer,
      businessName = "IceTrack Wholesale",
      subledgerEntries = entries,
      customerSalesInvoices = emptyList()
    )

    assertTrue(result is ReminderCompositionResult.Ready)
    val composed = (result as ReminderCompositionResult.Ready).reminder
    assertEquals(ReminderDueKind.ACCOUNT_DUE, composed.dueKind)
    assertTrue(composed.pendingInvoices.isEmpty())
    assertEquals(0L, composed.totalInvoiceAmountPaise)
    assertEquals(150000L, composed.netOutstandingPaise)
    assertTrue(composed.renderedMessage.contains("Opening Balance / Account Due"))
    assertTrue(composed.renderedMessage.contains("Total Outstanding Amount: ₹1,500.00"))
    assertFalse(composed.renderedMessage.contains("Invoice Date:"))
    assertFalse(composed.renderedMessage.contains("INV-"))
  }

  @Test
  fun composeReminder_zeroAndAdvanceBalances_areBlocked(): Unit {
    val customer = Customer(
      id = 3L,
      shopName = "Zero Advance Shop",
      ownerName = "Owner",
      phoneNumber = "9876543210",
      address = ""
    )

    val zeroResult = PaymentReminderEngine.composeReminder(
      customer = customer,
      businessName = "IceTrack",
      subledgerEntries = emptyList(),
      customerSalesInvoices = emptyList()
    )
    assertTrue(zeroResult is ReminderCompositionResult.Blocked)
    assertEquals(0L, (zeroResult as ReminderCompositionResult.Blocked).netOutstandingPaise)

    val advanceResult = PaymentReminderEngine.composeReminder(
      customer = customer,
      businessName = "IceTrack",
      subledgerEntries = listOf(
        SubledgerEntry(1L, PartyType.CUSTOMER, 3L, SubledgerEntryType.PAYMENT, "CUSTOMER_PAYMENT", 3L, debitPaise = 0L, creditPaise = 25000L, entryDate = 1710000000000L)
      ),
      customerSalesInvoices = emptyList()
    )
    assertTrue(advanceResult is ReminderCompositionResult.Blocked)
    assertEquals(-25000L, (advanceResult as ReminderCompositionResult.Blocked).netOutstandingPaise)
  }

  @Test
  fun composeReminder_invoiceSumVsLedgerDifference_explainsOpeningBalanceAndUnappliedAdvance(): Unit {
    val customer = Customer(
      id = 4L,
      shopName = "Calicut Corner",
      ownerName = "Faisal",
      phoneNumber = "+91 9876543210",
      address = "Calicut"
    )
    val t1 = 1710000000000L
    val invoice = SalesInvoice(21L, "INV-21", 4L, t1, t1 + 7 * dayMs, 50000L, 10000L, PaymentStatus.PARTIALLY_PAID)

    val plusDiff = PaymentReminderEngine.composeReminder(
      customer = customer,
      businessName = "IceTrack",
      subledgerEntries = listOf(
        SubledgerEntry(1L, PartyType.CUSTOMER, 4L, SubledgerEntryType.OPENING_BALANCE, "CUSTOMER_OPENING", 4L, debitPaise = 20000L, creditPaise = 0L, entryDate = t1),
        SubledgerEntry(2L, PartyType.CUSTOMER, 4L, SubledgerEntryType.INVOICE, "SALES_INVOICE", 21L, debitPaise = 50000L, creditPaise = 0L, entryDate = t1),
        SubledgerEntry(3L, PartyType.CUSTOMER, 4L, SubledgerEntryType.PAYMENT, "PAYMENT", 21L, debitPaise = 0L, creditPaise = 10000L, entryDate = t1)
      ),
      customerSalesInvoices = listOf(invoice)
    ) as ReminderCompositionResult.Ready

    assertEquals(40000L, plusDiff.reminder.totalInvoicePendingPaise)
    assertEquals(60000L, plusDiff.reminder.netOutstandingPaise)
    assertEquals(20000L, plusDiff.reminder.ledgerDifferencePaise)
    assertTrue(plusDiff.reminder.renderedMessage.contains("Opening Balance / Account Adjustments Due: ₹200.00"))
    assertTrue(plusDiff.reminder.renderedMessage.contains("Total Outstanding Amount: ₹600.00"))

    val minusDiff = PaymentReminderEngine.composeReminder(
      customer = customer,
      businessName = "IceTrack",
      subledgerEntries = listOf(
        SubledgerEntry(1L, PartyType.CUSTOMER, 4L, SubledgerEntryType.INVOICE, "SALES_INVOICE", 21L, debitPaise = 50000L, creditPaise = 0L, entryDate = t1),
        SubledgerEntry(2L, PartyType.CUSTOMER, 4L, SubledgerEntryType.PAYMENT, "PAYMENT", 21L, debitPaise = 0L, creditPaise = 25000L, entryDate = t1)
      ),
      customerSalesInvoices = listOf(invoice)
    ) as ReminderCompositionResult.Ready

    assertEquals(40000L, minusDiff.reminder.totalInvoicePendingPaise)
    assertEquals(25000L, minusDiff.reminder.netOutstandingPaise)
    assertEquals(-15000L, minusDiff.reminder.ledgerDifferencePaise)
    assertTrue(minusDiff.reminder.renderedMessage.contains("Less Unapplied Advance / Credit Adjustment: ₹150.00"))
    assertTrue(minusDiff.reminder.renderedMessage.contains("Total Outstanding Amount: ₹250.00"))
  }

  @Test
  fun migration3To4_populatedDatabase_preservesV3DataAndEnablesReminders(): Unit {
    val dbName = "icetrack_migration_v3_v4_test.db"
    val now = 1715000000000L

    migrationHelper.createDatabase(dbName, 3).apply {
      execSQL(
        "INSERT INTO products (id, name, sku, category, defaultPurchasePricePaise, defaultSellingPricePaise, minStockLevelPieces, isActive, createdAt, deletedAt) " +
          "VALUES (1, 'Vanilla Cup', 'VC-01', 'Cups', 800, 1200, 10, 1, $now, NULL)"
      )
      execSQL("INSERT INTO product_units (id, productId, piecesPerBox, boxesPerCarton) VALUES (1, 1, 12, 4)")
      execSQL(
        "INSERT INTO customers (id, shopName, ownerName, phoneNumber, address, openingBalancePaise, openingBalanceDate, creditLimitPaise, paymentTermsDays, isActive, createdAt, deletedAt) " +
          "VALUES (1, 'Kochi Scoops', 'Anil', '9847000999', 'Edappally', 10000, $now, 0, 7, 1, $now, NULL)"
      )
      execSQL(
        "INSERT INTO sales_invoices (id, invoiceNumber, customerId, invoiceDate, dueDate, totalAmountPaise, paidAmountPaise, paymentStatus, paymentMethod, notes, isConfirmed, createdAt) " +
          "VALUES (1, 'INV-V3-001', 1, $now, ${now + 7 * dayMs}, 30000, 5000, 'PARTIALLY_PAID', 'CASH', 'V3 sale', 1, $now)"
      )
      execSQL(
        "INSERT INTO subledger_entries (id, partyType, partyId, entryType, referenceType, referenceId, debitPaise, creditPaise, entryDate, createdAt, notes, isReversed) VALUES " +
          "(1, 'CUSTOMER', 1, 'OPENING_BALANCE', 'CUSTOMER_OPENING', 1, 10000, 0, $now, $now, 'Opening', 0), " +
          "(2, 'CUSTOMER', 1, 'INVOICE', 'SALES_INVOICE', 1, 30000, 0, $now, $now, 'Sale', 0), " +
          "(3, 'CUSTOMER', 1, 'PAYMENT', 'CUSTOMER_PAYMENT', 1, 0, 5000, $now, $now, 'Part paid', 0)"
      )
      execSQL(
        "INSERT INTO payment_allocations (id, paymentId, partyType, partyId, allocationType, invoiceId, allocatedAmountPaise, allocatedAt, notes, isReversed) " +
          "VALUES (1, 3, 'CUSTOMER', 1, 'INVOICE', 1, 5000, $now, 'V3 invoice alloc', 0)"
      )
      close()
    }

    val migratedDb = migrationHelper.runMigrationsAndValidate(
      dbName,
      4,
      true,
      DatabaseMigrations.MIGRATION_3_4
    )

    migratedDb.query("SELECT shopName, phoneNumber FROM customers WHERE id = 1").use { c ->
      assertTrue(c.moveToFirst())
      assertEquals("Kochi Scoops", c.getString(0))
      assertEquals("9847000999", c.getString(1))
    }
    migratedDb.query("SELECT totalAmountPaise, paidAmountPaise FROM sales_invoices WHERE id = 1").use { c ->
      assertTrue(c.moveToFirst())
      assertEquals(30000L, c.getLong(0))
      assertEquals(5000L, c.getLong(1))
    }
    migratedDb.query("SELECT COALESCE(SUM(debitPaise - creditPaise), 0) FROM subledger_entries WHERE partyId = 1 AND isReversed = 0").use { c ->
      assertTrue(c.moveToFirst())
      assertEquals(35000L, c.getLong(0))
    }
    migratedDb.close()

    val roomDb = Room.databaseBuilder(context, IceTrackDatabase::class.java, dbName)
      .addMigrations(DatabaseMigrations.MIGRATION_1_2, DatabaseMigrations.MIGRATION_2_3, DatabaseMigrations.MIGRATION_3_4, DatabaseMigrations.MIGRATION_4_5)
      .allowMainThreadQueries()
      .build()
    try {
      runBlocking {
        val reminderRepo = PaymentReminderRepository(roomDb)
        val draft = reminderRepo.createDraftReminder(1L)
        assertTrue(draft is PrepareDraftResult.Created)
        val created = draft as PrepareDraftResult.Created
        assertEquals(35000L, created.composed.netOutstandingPaise)
        assertEquals(1, created.composed.pendingInvoices.size)
        assertEquals(1, reminderRepo.getInvoicesForReminderSync(created.reminder.id).size)
      }
    } finally {
      roomDb.close()
      context.deleteDatabase(dbName)
    }
  }

  @Test
  fun repository_ssotParityFreshVerificationConfirmationLifecycleAndAuditSnapshot(): Unit = runBlocking {
    val productRepo = ProductRepository(db)
    val supplierRepo = SupplierRepository(db)
    val customerRepo = CustomerRepository(db)
    val purchaseRepo = PurchaseRepository(db)
    val salesRepo = SalesRepository(db)
    val subledgerRepo = SubledgerRepository(db)
    val settingsRepo = SettingsRepository(db)
    val reminderRepo = PaymentReminderRepository(db)

    val now = 1715000000000L
    settingsRepo.setSetting("business_name", "Malabar Ice Co")
    val prodId = productRepo.saveProductWithUnit(
      Product(
        name = "Mango Bar SecretName",
        sku = "MB-999",
        category = "Bars",
        defaultPurchasePricePaise = 500L,
        defaultSellingPricePaise = 1000L,
        minStockLevelPieces = 5
      ),
      10,
      5
    )
    val supId = supplierRepo.saveSupplier(Supplier(name = "Dairy Sup", contactPerson = "", phoneNumber = "", address = ""))
    purchaseRepo.createPurchaseInvoice(
      supplierId = supId,
      invoiceNumber = "PUR-REM-1",
      invoiceDate = now - 5 * dayMs,
      dueDate = 0L,
      items = listOf(PurchaseInvoiceItem(productId = prodId, batchNumber = "SECRET-BATCH-7", expiryDate = now + 90 * dayMs, quantityPieces = 100, costPerPiecePaise = 500L, lineTotalPaise = 50000L)),
      paidAmountPaise = 0L,
      paymentMethod = null,
      notes = ""
    )

    val custId = customerRepo.saveCustomer(
      Customer(shopName = "Aluva Store", ownerName = "Basheer", phoneNumber = "9876543210", address = "Aluva")
    )
    val saleId = salesRepo.createSalesInvoice(
      customerId = custId,
      invoiceNumber = "SAL-REM-1",
      invoiceDate = now - 2 * dayMs,
      dueDate = 0L,
      requestedLines = listOf(SalesRepository.RequestedSaleLine(productId = prodId, quantityPieces = 20, unitPricePaise = 1000L)),
      paidAmountPaise = 5000L,
      paymentMethod = PaymentMethod.CASH,
      notes = "Credit sale"
    )

    // 1. Draft created with exact SSOT balance (₹200 - ₹50 = ₹150 = 15000 paise)
    val draft1 = reminderRepo.createDraftReminder(custId) as PrepareDraftResult.Created
    val entriesBefore = subledgerRepo.allEntries.first().filter { !it.isReversed && it.partyType == PartyType.CUSTOMER && it.partyId == custId }
    assertEquals(SubledgerCalculationEngine.calculateCustomerBalance(entriesBefore), draft1.reminder.snapshotNetOutstandingPaise)
    assertEquals(15000L, draft1.reminder.snapshotNetOutstandingPaise)
    assertFalse(draft1.reminder.renderedMessage.contains("Mango Bar SecretName"))
    assertFalse(draft1.reminder.renderedMessage.contains("MB-999"))
    assertFalse(draft1.reminder.renderedMessage.contains("SECRET-BATCH-7"))

    // 2. Partial payment happens BEFORE user taps Open WhatsApp -> stale draft fails & refreshed draft requires review
    salesRepo.recordCustomerPayment(custId, 5000L, now - dayMs, PaymentMethod.UPI, "Part pay")
    val changedResult = reminderRepo.verifyAndMarkWhatsAppOpened(draft1.reminder.id, WhatsAppIntentHelper.WHATSAPP_PACKAGE)
    assertTrue(changedResult is HandoffPreparationResult.BalanceChangedNeedsReview)
    val review = changedResult as HandoffPreparationResult.BalanceChangedNeedsReview
    assertEquals(15000L, review.previousOutstandingPaise)
    assertEquals(10000L, review.updatedReminder.snapshotNetOutstandingPaise)
    assertEquals(ReminderStatus.FAILED, db.customerPaymentReminderDao().getByIdSync(draft1.reminder.id)?.status)

    // 3. Re-verify updated draft -> transitions to WHATSAPP_OPENED awaiting confirmation
    val readyResult = reminderRepo.verifyAndMarkWhatsAppOpened(review.updatedReminder.id, WhatsAppIntentHelper.WHATSAPP_BUSINESS_PACKAGE)
    assertTrue(readyResult is HandoffPreparationResult.ReadyToLaunch)
    val openedReminder = (readyResult as HandoffPreparationResult.ReadyToLaunch).reminder
    assertEquals(ReminderStatus.WHATSAPP_OPENED, openedReminder.status)
    assertTrue(openedReminder.awaitingUserConfirmation)
    assertNotNull(openedReminder.whatsappOpenedAt)
    assertNull(openedReminder.confirmedSentAt)

    // 4. Dismiss as Not Sent -> must NOT promote to USER_CONFIRMED_SENT
    reminderRepo.dismissPendingConfirmationAsNotSent(openedReminder.id)
    val afterDismiss = db.customerPaymentReminderDao().getByIdSync(openedReminder.id)!!
    assertEquals(ReminderStatus.WHATSAPP_OPENED, afterDismiss.status)
    assertFalse(afterDismiss.awaitingUserConfirmation)
    assertNull(afterDismiss.confirmedSentAt)

    // 5. Explicit user confirmation marks USER_CONFIRMED_SENT & stays immutable even if failed later requested
    reminderRepo.confirmReminderSent(openedReminder.id)
    val confirmed = db.customerPaymentReminderDao().getByIdSync(openedReminder.id)!!
    assertEquals(ReminderStatus.USER_CONFIRMED_SENT, confirmed.status)
    assertNotNull(confirmed.confirmedSentAt)

    reminderRepo.markReminderFailed(openedReminder.id, "Should not overwrite confirmed sent")
    assertEquals(ReminderStatus.USER_CONFIRMED_SENT, db.customerPaymentReminderDao().getByIdSync(openedReminder.id)!!.status)

    // 6. Customer settles remaining ₹100 -> historical audit snapshot remains ₹100 while new reminder attempt is Blocked
    salesRepo.recordCustomerPayment(custId, 10000L, now, PaymentMethod.CASH, "Final settlement")
    val history = reminderRepo.observeHistoryForCustomer(custId).first()
    val confirmedInHistory = history.first { it.id == openedReminder.id }
    assertEquals(10000L, confirmedInHistory.snapshotNetOutstandingPaise)
    assertEquals(1, reminderRepo.getInvoicesForReminderSync(confirmedInHistory.id).size)
    assertEquals(saleId, reminderRepo.getInvoicesForReminderSync(confirmedInHistory.id).first().salesInvoiceId)

    val postSettlementAttempt = reminderRepo.createDraftReminder(custId)
    assertTrue(postSettlementAttempt is PrepareDraftResult.Blocked)
    assertEquals(0L, (postSettlementAttempt as PrepareDraftResult.Blocked).netOutstandingPaise)

    // 7. If customer pays off while draft exists, verifyAndMarkWhatsAppOpened blocks launch cleanly
    val cust2Id = customerRepo.saveCustomer(
      Customer(shopName = "Kottayam Store", ownerName = "Joshi", phoneNumber = "9847111000", address = "Kottayam", openingBalancePaise = 5000L)
    )
    val cust2Draft = (reminderRepo.createDraftReminder(cust2Id) as PrepareDraftResult.Created).reminder
    salesRepo.recordCustomerPayment(cust2Id, 5000L, now, PaymentMethod.CASH, "Cleared")
    val blockedAtLaunch = reminderRepo.verifyAndMarkWhatsAppOpened(cust2Draft.id, WhatsAppIntentHelper.WHATSAPP_PACKAGE)
    assertTrue(blockedAtLaunch is HandoffPreparationResult.Blocked)
    assertEquals(ReminderStatus.FAILED, db.customerPaymentReminderDao().getByIdSync(cust2Draft.id)?.status)
  }

  @Test
  fun processDeathRecovery_fileDatabasePersistsPendingConfirmationAcrossRestart(): Unit {
    val dbName = "icetrack_process_death_reminder_test.db"
    context.deleteDatabase(dbName)

    val fileDb1 = Room.databaseBuilder(context, IceTrackDatabase::class.java, dbName)
      .addMigrations(DatabaseMigrations.MIGRATION_1_2, DatabaseMigrations.MIGRATION_2_3, DatabaseMigrations.MIGRATION_3_4, DatabaseMigrations.MIGRATION_4_5)
      .allowMainThreadQueries()
      .build()

    val savedReminderId: Long
    try {
      runBlocking {
        val custRepo = CustomerRepository(fileDb1)
        val remRepo = PaymentReminderRepository(fileDb1)
        val custId = custRepo.saveCustomer(
          Customer(shopName = "Restart Recovery Mart", ownerName = "Salim", phoneNumber = "9895000111", address = "Kollam", openingBalancePaise = 75000L)
        )
        val draft = (remRepo.createDraftReminder(custId) as PrepareDraftResult.Created).reminder
        val ready = remRepo.verifyAndMarkWhatsAppOpened(draft.id, WhatsAppIntentHelper.WHATSAPP_PACKAGE) as HandoffPreparationResult.ReadyToLaunch
        savedReminderId = ready.reminder.id
      }
    } finally {
      fileDb1.close()
    }

    val fileDb2 = Room.databaseBuilder(context, IceTrackDatabase::class.java, dbName)
      .addMigrations(DatabaseMigrations.MIGRATION_1_2, DatabaseMigrations.MIGRATION_2_3, DatabaseMigrations.MIGRATION_3_4, DatabaseMigrations.MIGRATION_4_5)
      .allowMainThreadQueries()
      .build()
    try {
      runBlocking {
        val remRepo2 = PaymentReminderRepository(fileDb2)
        val vm2 = PaymentReminderViewModel(
          CustomerRepository(fileDb2),
          SalesRepository(fileDb2),
          SubledgerRepository(fileDb2),
          remRepo2
        )
        val recovered = remRepo2.getPendingConfirmationReminderSync()
        assertNotNull(recovered)
        assertEquals(savedReminderId, recovered!!.id)
        assertEquals(ReminderStatus.WHATSAPP_OPENED, recovered.status)
        assertTrue(recovered.awaitingUserConfirmation)
        assertEquals(75000L, recovered.snapshotNetOutstandingPaise)

        vm2.confirmPendingReminderSent(recovered.id)
        val deadline = System.currentTimeMillis() + 5000L
        while (System.currentTimeMillis() < deadline) {
          ShadowLooper.idleMainLooper()
          val current = fileDb2.customerPaymentReminderDao().getByIdSync(savedReminderId)
          if (current?.status == ReminderStatus.USER_CONFIRMED_SENT) break
          kotlinx.coroutines.delay(20L)
        }
        val afterConfirm = fileDb2.customerPaymentReminderDao().getByIdSync(savedReminderId)!!
        assertEquals(ReminderStatus.USER_CONFIRMED_SENT, afterConfirm.status)
        assertFalse(afterConfirm.awaitingUserConfirmation)
        assertNull(remRepo2.getPendingConfirmationReminderSync())
      }
    } finally {
      fileDb2.close()
      context.deleteDatabase(dbName)
    }
  }

  @Test
  fun whatsAppIntentHelper_buildsDeepLinkSendFallbackAndCopiesToClipboard(): Unit {
    val deepLink = WhatsAppIntentHelper.buildWhatsAppLaunchIntent(
      context = context,
      whatsappPhoneDigits = "919876543210",
      message = "Dear Shop,\nTotal Outstanding Amount: ₹500.00",
      targetPackage = WhatsAppIntentHelper.WHATSAPP_BUSINESS_PACKAGE
    )
    assertEquals(Intent.ACTION_VIEW, deepLink.action)
    assertEquals(WhatsAppIntentHelper.WHATSAPP_BUSINESS_PACKAGE, deepLink.`package`)
    val uriString = deepLink.data.toString()
    assertTrue(uriString.startsWith("https://api.whatsapp.com/send?phone=919876543210&text="))
    assertTrue(uriString.contains("Total%20Outstanding%20Amount"))

    val longMsg = "A".repeat(1900)
    val fallbackIntent = WhatsAppIntentHelper.buildWhatsAppLaunchIntent(
      context = context,
      whatsappPhoneDigits = "919876543210",
      message = longMsg,
      targetPackage = WhatsAppIntentHelper.WHATSAPP_PACKAGE
    )
    assertEquals(Intent.ACTION_SEND, fallbackIntent.action)
    assertEquals("text/plain", fallbackIntent.type)
    assertEquals(WhatsAppIntentHelper.WHATSAPP_PACKAGE, fallbackIntent.`package`)
    assertEquals(longMsg, fallbackIntent.getStringExtra(Intent.EXTRA_TEXT))
    assertEquals("919876543210@s.whatsapp.net", fallbackIntent.getStringExtra("jid"))

    WhatsAppIntentHelper.copyMessageToClipboard(context, "Copy check ₹150.00")
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    assertEquals("Copy check ₹150.00", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
  }
}
