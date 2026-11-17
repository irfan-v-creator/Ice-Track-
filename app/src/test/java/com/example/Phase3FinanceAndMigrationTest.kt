package com.example

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.DatabaseMigrations
import com.example.data.local.IceTrackDatabase
import com.example.data.repository.AuditRepository
import com.example.data.repository.CreditDebitNoteRepository
import com.example.data.repository.CreditLimitExceededException
import com.example.data.repository.CustomerRepository
import com.example.data.repository.InvoiceLifecycleRepository
import com.example.data.repository.ProductRepository
import com.example.data.repository.PurchaseRepository
import com.example.data.repository.RefundRepository
import com.example.data.repository.ReturnRepository
import com.example.data.repository.SalesRepository
import com.example.data.repository.SubledgerRepository
import com.example.data.repository.SupplierRepository
import com.example.data.repository.toDomain
import com.example.domain.engine.BusinessReportEngine
import com.example.domain.engine.PaymentAllocationEngine
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.model.AdvanceReversalStrategy
import com.example.domain.model.AuditOperationType
import com.example.domain.model.CreditDebitNoteType
import com.example.domain.model.Customer
import com.example.domain.model.DiscountType
import com.example.domain.model.InvoiceCancellationPaymentMode
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.NoteStatus
import com.example.domain.model.PartyType
import com.example.domain.model.PaymentAllocationType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentStatus
import com.example.domain.model.Product
import com.example.domain.model.PurchaseInvoiceItem
import com.example.domain.model.RefundExecutionStatus
import com.example.domain.model.ReturnCondition
import com.example.domain.model.ReturnSettlementType
import com.example.domain.model.StockMovementType
import com.example.domain.model.Supplier
import com.example.domain.model.UnitType
import com.example.service.BackupRestoreService
import com.example.service.XlsxCell
import com.example.service.XlsxSheet
import com.example.service.XlsxWorkbookWriter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase3FinanceAndMigrationTest {

  @get:Rule
  val migrationHelper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    IceTrackDatabase::class.java,
    emptyList(),
    FrameworkSQLiteOpenHelperFactory()
  )

  private lateinit var context: Context
  private lateinit var db: IceTrackDatabase
  private lateinit var productRepo: ProductRepository
  private lateinit var supplierRepo: SupplierRepository
  private lateinit var customerRepo: CustomerRepository
  private lateinit var subledgerRepo: SubledgerRepository
  private lateinit var purchaseRepo: PurchaseRepository
  private lateinit var salesRepo: SalesRepository
  private lateinit var lifecycleRepo: InvoiceLifecycleRepository
  private lateinit var returnRepo: ReturnRepository
  private lateinit var noteRepo: CreditDebitNoteRepository
  private lateinit var refundRepo: RefundRepository
  private lateinit var auditRepo: AuditRepository

  private val dayMs = 86400000L

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, IceTrackDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    productRepo = ProductRepository(db)
    supplierRepo = SupplierRepository(db)
    customerRepo = CustomerRepository(db)
    subledgerRepo = SubledgerRepository(db)
    purchaseRepo = PurchaseRepository(db)
    salesRepo = SalesRepository(db)
    lifecycleRepo = InvoiceLifecycleRepository(db)
    returnRepo = ReturnRepository(db)
    noteRepo = CreditDebitNoteRepository(db)
    refundRepo = RefundRepository(db)
    auditRepo = AuditRepository(db)
  }

  @After
  fun tearDown() {
    db.close()
  }

  private suspend fun seedBaseData(
    now: Long = System.currentTimeMillis(),
    customerCreditLimitPaise: Long = 0L
  ): Triple<Long, Long, Pair<Long, Long>> {
    val prodId = productRepo.saveProductWithUnit(
      Product(
        name = "Mango Bar",
        sku = "MB-01",
        category = "Bars",
        defaultPurchasePricePaise = 1000L, // Rs 10
        defaultSellingPricePaise = 1500L,  // Rs 15
        minStockLevelPieces = 5
      ),
      piecesPerBox = 20,
      boxesPerCarton = 5
    )
    val supId = supplierRepo.saveSupplier(
      Supplier(
        name = "Malabar Dairy",
        contactPerson = "Raju",
        phoneNumber = "9847000111",
        address = "Kozhikode",
        openingBalancePaise = 0L,
        openingBalanceDate = now
      )
    )
    val cust1Id = customerRepo.saveCustomer(
      Customer(
        shopName = "A1 Cold Bar",
        ownerName = "Nisar",
        phoneNumber = "9847111222",
        address = "MG Road",
        openingBalancePaise = 0L,
        openingBalanceDate = now,
        creditLimitPaise = customerCreditLimitPaise,
        paymentTermsDays = 7
      )
    )
    val cust2Id = customerRepo.saveCustomer(
      Customer(
        shopName = "Metro Mart",
        ownerName = "Suresh",
        phoneNumber = "9847222333",
        address = "Convent Rd",
        openingBalancePaise = 0L,
        openingBalanceDate = now,
        creditLimitPaise = 0L,
        paymentTermsDays = 7
      )
    )
    purchaseRepo.createPurchaseInvoice(
      supplierId = supId,
      invoiceNumber = "PUR-BASE-01",
      invoiceDate = now - 5 * dayMs,
      dueDate = now + 10 * dayMs,
      items = listOf(
        PurchaseInvoiceItem(
          productId = prodId,
          batchNumber = "B-BASE-1",
          expiryDate = now + 90 * dayMs,
          quantityPieces = 100,
          costPerPiecePaise = 1000L,
          lineTotalPaise = 100_000L
        )
      ),
      paidAmountPaise = 0L,
      paymentMethod = null,
      notes = "Base stock"
    )
    return Triple(prodId, supId, cust1Id to cust2Id)
  }

  @Test
  fun editSalesInvoice_supportsDiscountsChargesCustomerTransferExcessSplitAndCreditLimit() { runBlocking {
    val now = 1_720_000_000_000L
    val (prodId, _, custPair) = seedBaseData(now, customerCreditLimitPaise = 0L)
    val (cust1Id, cust2Id) = custPair

    // Create invoice: 20 pcs @ Rs 15 = Rs 300 (30,000p), paid Rs 250 (25,000p) -> due Rs 50
    val invId = salesRepo.createSalesInvoice(
      customerId = cust1Id,
      invoiceNumber = "INV-EDIT-01",
      invoiceDate = now,
      dueDate = now + 7 * dayMs,
      requestedLines = listOf(SalesRepository.RequestedSaleLine(prodId, 20, 1500L)),
      paidAmountPaise = 25_000L,
      paymentMethod = PaymentMethod.UPI,
      notes = "Initial sale"
    )

    // Edit Mode A: reduce qty to 10 pcs @ Rs 15 (15,000p), add item discount 1,000p, invoice flat discount 500p, delivery charge 1,500p -> Total = 15,000p.
    // Paid was 25,000p -> Excess = 10,000p. Retain 4,000p as Advance, refund 6,000p as Cash refund liability!
    val loaded = lifecycleRepo.getSalesInvoiceDetail(invId)!!
    lifecycleRepo.editSalesInvoice(
      InvoiceLifecycleRepository.EditSalesInvoiceRequest(
        invoiceId = invId,
        expectedRevisionVersion = loaded.invoice.revisionVersion,
        customerId = cust1Id,
        partyNameSnapshot = "A1 Cold Bar Snapshot",
        partyPhoneSnapshot = "9847111222",
        partyAddressSnapshot = "MG Road",
        invoiceDate = now + dayMs,
        dueDate = now + 8 * dayMs,
        discountType = DiscountType.FLAT,
        discountValueRaw = 500L,
        additionalChargesPaise = 1500L,
        paymentMethod = PaymentMethod.UPI,
        notes = "Updated invoice",
        lines = listOf(
          InvoiceLifecycleRepository.EditSalesLineRequest(
            productId = prodId,
            productNameSnapshot = "Mango Bar",
            quantityPieces = 10,
            unitPricePaise = 1500L,
            unitType = UnitType.PIECE,
            unitQuantity = 10,
            unitRatePaise = 1500L,
            itemDiscountPaise = 1000L
          )
        ),
        requestedAdvanceRetentionPaise = 4_000L,
        refundPaymentMethod = PaymentMethod.CASH,
        reason = "Customer reduced order after dispatch"
      )
    )

    val afterEdit1 = lifecycleRepo.getSalesInvoiceDetail(invId)!!
    assertEquals(15_000L, afterEdit1.invoice.totalAmountPaise)
    assertEquals(PaymentStatus.PAID, afterEdit1.invoice.paymentStatus)
    assertEquals("A1 Cold Bar Snapshot", afterEdit1.invoice.partyNameSnapshot)
    assertEquals(2, afterEdit1.invoice.revisionVersion)

    // Stock should be restored from 80 back to 90 pieces
    val batches = db.productBatchDao().getAllBatchesSync().filter { it.productId == prodId }
    assertEquals(90, batches.sumOf { it.currentQuantityPieces })

    // Verify Excess Split: 4,000p ADVANCE + 6,000p RefundRecord pending payout
    val cust1Allocs = db.paymentAllocationDao().getAllSync().filter { !it.isReversed && it.partyType == PartyType.CUSTOMER && it.partyId == cust1Id }
    assertEquals(4_000L, cust1Allocs.filter { it.allocationType == PaymentAllocationType.ADVANCE }.sumOf { it.allocatedAmountPaise })
    val refunds = db.refundRecordDao().getAllSync()
    assertEquals(1, refunds.size)
    assertEquals(6_000L, refunds.first().amountPaise)
    assertEquals(RefundExecutionStatus.RECORDED_PENDING_PAYOUT, refunds.first().executionStatus)

    // Execute Refund Payout and verify status & ledger balance
    refundRepo.executeRefundPayout(refunds.first().id, PaymentMethod.CASH, "REF-101", "Paid cash refund to customer")
    val executedRefund = db.refundRecordDao().getAllSync().first()
    assertEquals(RefundExecutionStatus.PAYOUT_EXECUTED, executedRefund.executionStatus)
    // Net Cust1 ledger: +15,000 invoice -25,000 cash receipt +6,000 refund payout = -4,000 (4,000 Advance credit remaining!)
    val cust1Entries = db.subledgerDao().getEntriesForPartySync(PartyType.CUSTOMER, cust1Id).filter { !it.isReversed }.map { it.toDomain() }
    assertEquals(-4_000L, SubledgerCalculationEngine.calculateCustomerBalance(cust1Entries))

    // Edit Mode B: transfer invoice to Cust2 (who has credit limit 10,000p) -> test credit limit block & override
    val c2 = db.customerDao().getCustomerSync(cust2Id)!!.toDomain()
    customerRepo.saveCustomer(c2.copy(creditLimitPaise = 10_000L))
    val transferReq = InvoiceLifecycleRepository.EditSalesInvoiceRequest(
      invoiceId = invId,
      expectedRevisionVersion = afterEdit1.invoice.revisionVersion,
      customerId = cust2Id,
      partyNameSnapshot = "Metro Mart",
      partyPhoneSnapshot = "9847222333",
      partyAddressSnapshot = "Convent Rd",
      invoiceDate = now,
      dueDate = now + 7 * dayMs,
      discountType = DiscountType.PERCENT,
      discountValueRaw = 0L,
      additionalChargesPaise = 0L,
      paymentMethod = null,
      notes = "Transferred bill",
      lines = listOf(InvoiceLifecycleRepository.EditSalesLineRequest(prodId, "Mango Bar", 10, 1500L)),
      allowCreditLimitOverride = false,
      reason = "Billed to sister branch Metro Mart"
    )
    try {
      lifecycleRepo.editSalesInvoice(transferReq)
      fail("Expected CreditLimitExceededException on customer transfer")
    } catch (_: CreditLimitExceededException) {
      // Expected
    }
    lifecycleRepo.editSalesInvoice(transferReq.copy(allowCreditLimitOverride = true))
    val cust2Entries = db.subledgerDao().getEntriesForPartySync(PartyType.CUSTOMER, cust2Id).filter { !it.isReversed }.map { it.toDomain() }
    assertEquals(15_000L, SubledgerCalculationEngine.calculateCustomerBalance(cust2Entries))
    val audits = db.financialAuditLogDao().getAllSync()
    assertTrue(audits.any { it.operationType == AuditOperationType.INVOICE_EDIT })
    assertTrue(audits.any { it.operationType == AuditOperationType.REFUND_PAYOUT })
  }
  }

  @Test
  fun editPurchaseInvoice_blocksReducingBelowConsumedQty_allowsUpdatingCostAndQtyAboveConsumed() { runBlocking {
    val now = 1_720_000_000_000L
    val (prodId, supId, custPair) = seedBaseData(now)
    val cust1Id = custPair.first

    // Sell 30 pcs from the purchased batch of 100 pcs
    salesRepo.createSalesInvoice(
      customerId = cust1Id,
      invoiceNumber = "INV-SOLD-30",
      invoiceDate = now,
      dueDate = now + 7 * dayMs,
      requestedLines = listOf(SalesRepository.RequestedSaleLine(prodId, 30, 1500L)),
      paidAmountPaise = 0L,
      paymentMethod = null,
      notes = ""
    )

    val purchaseId = db.purchaseInvoiceDao().getAllSync().first().id
    val detail = lifecycleRepo.getPurchaseInvoiceDetail(purchaseId)!!
    assertEquals(30, detail.soldQtyByBatchNumber["B-BASE-1"])

    // Attempt reducing below 30 sold pieces -> MUST fail cleanly
    try {
      lifecycleRepo.editPurchaseInvoice(
        InvoiceLifecycleRepository.EditPurchaseInvoiceRequest(
          invoiceId = purchaseId,
          expectedRevisionVersion = detail.invoice.revisionVersion,
          supplierId = supId,
          partyNameSnapshot = "Malabar Dairy",
          partyPhoneSnapshot = "9847000111",
          partyAddressSnapshot = "Kozhikode",
          invoiceDate = now - 5 * dayMs,
          dueDate = now + 10 * dayMs,
          discountType = DiscountType.NONE,
          discountValueRaw = 0L,
          additionalChargesPaise = 0L,
          paymentMethod = null,
          notes = "Invalid reduction",
          lines = listOf(
            InvoiceLifecycleRepository.EditPurchaseLineRequest(
              productId = prodId,
              batchNumber = "B-BASE-1",
              expiryDate = now + 90 * dayMs,
              quantityPieces = 20, // Less than 30 sold!
              costPerPiecePaise = 1000L
            )
          ),
          reason = "Try illegal reduction"
        )
      )
      fail("Expected error when reducing batch below sold pieces")
    } catch (e: IllegalStateException) {
      assertTrue(e.message!!.contains("30"))
    }

    // Valid edit: change qty to 120 and cost to 950p/pc -> remaining batch pieces should be 120 - 30 = 90
    lifecycleRepo.editPurchaseInvoice(
      InvoiceLifecycleRepository.EditPurchaseInvoiceRequest(
        invoiceId = purchaseId,
        expectedRevisionVersion = detail.invoice.revisionVersion,
        supplierId = supId,
        partyNameSnapshot = "Malabar Dairy Updated",
        partyPhoneSnapshot = "9847000111",
        partyAddressSnapshot = "Kozhikode",
        invoiceDate = now - 5 * dayMs,
        dueDate = now + 10 * dayMs,
        discountType = DiscountType.NONE,
        discountValueRaw = 0L,
        additionalChargesPaise = 0L,
        paymentMethod = null,
        notes = "Cost & qty negotiated",
        lines = listOf(
          InvoiceLifecycleRepository.EditPurchaseLineRequest(
            productId = prodId,
            batchNumber = "B-BASE-1",
            expiryDate = now + 90 * dayMs,
            quantityPieces = 120,
            costPerPiecePaise = 950L
          )
        ),
        reason = "Supplier revised rate & qty"
      )
    )

    val batch = db.productBatchDao().getAllBatchesSync().filter { it.productId == prodId }.first { it.batchNumber == "B-BASE-1" }
    assertEquals(120, batch.initialQuantityPieces)
    assertEquals(90, batch.currentQuantityPieces)
    assertEquals(950L, batch.purchaseCostPerPiecePaise)
  }
  }

  @Test
  fun softCancelSalesAndPurchase_restoresStockConvertsPaymentsToAdvanceAndNeverReusesNumber() { runBlocking {
    val now = 1_720_000_000_000L
    val (prodId, supId, custPair) = seedBaseData(now)
    val cust1Id = custPair.first

    val invId = salesRepo.createSalesInvoice(
      customerId = cust1Id,
      invoiceNumber = "INV-CANCEL-01",
      invoiceDate = now,
      dueDate = now + 7 * dayMs,
      requestedLines = listOf(SalesRepository.RequestedSaleLine(prodId, 25, 1500L)),
      paidAmountPaise = 10_000L,
      paymentMethod = PaymentMethod.CASH,
      notes = ""
    )

    // BLOCK_IF_PAYMENTS_EXIST must block when paidAmountPaise > 0
    try {
      lifecycleRepo.cancelSalesInvoice(invId, "Duplicate", InvoiceCancellationPaymentMode.BLOCK_IF_PAYMENTS_EXIST)
      fail("Should block cancellation while active payments exist")
    } catch (e: IllegalStateException) {
      assertTrue(e.message!!.contains("advance", ignoreCase = true) || e.message!!.contains("payment", ignoreCase = true))
    }

    // CONVERT_TO_ADVANCE must soft-cancel, restore 25 pcs -> 100 pcs, convert 10,000p to ADVANCE
    lifecycleRepo.cancelSalesInvoice(invId, "Order cancelled by shop", InvoiceCancellationPaymentMode.CONVERT_TO_ADVANCE)
    val cancelledInv = db.salesInvoiceDao().getById(invId)!!
    assertEquals(InvoiceStatus.CANCELLED, cancelledInv.invoiceStatus)
    assertEquals(100, db.productBatchDao().getAllBatchesSync().filter { it.productId == prodId }.sumOf { it.currentQuantityPieces })

    val allocs = db.paymentAllocationDao().getAllSync().filter { !it.isReversed && it.partyType == PartyType.CUSTOMER && it.partyId == cust1Id }
    assertEquals(10_000L, allocs.filter { it.allocationType == PaymentAllocationType.ADVANCE }.sumOf { it.allocatedAmountPaise })
    val custEntries = db.subledgerDao().getEntriesForPartySync(PartyType.CUSTOMER, cust1Id).filter { !it.isReversed }.map { it.toDomain() }
    assertEquals(-10_000L, SubledgerCalculationEngine.calculateCustomerBalance(custEntries))

    // Invoice number stays occupied (never reused!)
    try {
      salesRepo.createSalesInvoice(
        customerId = cust1Id,
        invoiceNumber = "INV-CANCEL-01",
        invoiceDate = now,
        dueDate = now + 7 * dayMs,
        requestedLines = listOf(SalesRepository.RequestedSaleLine(prodId, 5, 1500L)),
        paidAmountPaise = 0L,
        paymentMethod = null,
        notes = ""
      )
      fail("Cancelled invoice number must never be reused")
    } catch (_: Exception) {
      // Expected unique conflict
    }

    // Soft-cancel unconsumed Purchase Invoice
    val p2Id = purchaseRepo.createPurchaseInvoice(
      supplierId = supId,
      invoiceNumber = "PUR-CANCEL-02",
      invoiceDate = now,
      dueDate = now + 7 * dayMs,
      items = listOf(
        PurchaseInvoiceItem(
          productId = prodId,
          batchNumber = "B-UNUSED",
          expiryDate = now + 60 * dayMs,
          quantityPieces = 40,
          costPerPiecePaise = 900L,
          lineTotalPaise = 36_000L
        )
      ),
      paidAmountPaise = 12_000L,
      paymentMethod = PaymentMethod.BANK_TRANSFER,
      notes = ""
    )
    assertEquals(140, db.productBatchDao().getAllBatchesSync().filter { it.productId == prodId }.sumOf { it.currentQuantityPieces })
    lifecycleRepo.cancelPurchaseInvoice(p2Id, "Wrong dispatch batch", InvoiceCancellationPaymentMode.CONVERT_TO_ADVANCE)
    assertEquals(InvoiceStatus.CANCELLED, db.purchaseInvoiceDao().getById(p2Id)!!.invoiceStatus)
    assertEquals(100, db.productBatchDao().getAllBatchesSync().filter { it.productId == prodId }.sumOf { it.currentQuantityPieces })
    val supAllocs = db.paymentAllocationDao().getAllSync().filter { !it.isReversed && it.partyType == PartyType.SUPPLIER && it.partyId == supId }
    assertEquals(12_000L, supAllocs.filter { it.allocationType == PaymentAllocationType.ADVANCE }.sumOf { it.allocatedAmountPaise })
  }
  }

  @Test
  fun salesAndPurchaseReturns_handleResellableMeltedWastageAndPnlParityAndStockGuards() { runBlocking {
    val now = 1_720_000_000_000L
    val (prodId, supId, custPair) = seedBaseData(now)
    val cust1Id = custPair.first

    // Sell 20 pcs @ Rs 15 = 30,000p (COGS = 20 * 1,000p = 20,000p)
    val saleId = salesRepo.createSalesInvoice(
      customerId = cust1Id,
      invoiceNumber = "INV-RET-01",
      invoiceDate = now,
      dueDate = now + 7 * dayMs,
      requestedLines = listOf(SalesRepository.RequestedSaleLine(prodId, 20, 1500L)),
      paidAmountPaise = 30_000L,
      paymentMethod = PaymentMethod.CASH,
      notes = ""
    )
    val saleItemId = db.salesInvoiceItemDao().getItemsForInvoiceSync(saleId).first().id

    // Return 6 pcs RESELLABLE (restores 6 pcs stock, reduces COGS by 6*1000=6000) & 4 pcs MELTED (non-saleable wastage, keeps COGS write-off!)
    // Total return amount = 10 * 1500 = 15,000p via CREDIT_NOTE
    returnRepo.createSalesReturn(
      salesInvoiceId = saleId,
      returnDate = now + dayMs,
      requestedLines = listOf(
        ReturnRepository.RequestedSalesReturnLine(saleItemId, 6, ReturnCondition.RESELLABLE),
        ReturnRepository.RequestedSalesReturnLine(saleItemId, 4, ReturnCondition.MELTED)
      ),
      settlementType = ReturnSettlementType.CREDIT_NOTE,
      reason = "6 boxes good surplus, 4 pcs melted",
      notes = ""
    )

    // Remaining live batch qty: 100 - 20 + 6 = 86 pcs
    assertEquals(86, db.productBatchDao().getAllBatchesSync().filter { it.productId == prodId }.sumOf { it.currentQuantityPieces })
    val movements = db.stockMovementDao().getAllSync()
    assertTrue(movements.any { it.movementType == StockMovementType.SALE_RETURN && it.quantityPieces == 6 })
    assertTrue(
      movements.any {
        it.movementType == StockMovementType.WASTAGE_EXPIRED &&
          it.quantityPieces == 0 &&
          it.referenceType == "SALES_RETURN_NON_SALEABLE" &&
          it.notes.contains("4 pcs non-saleable")
      }
    )

    // P&L verification: Net Revenue = 30,000 - 15,000 = 15,000p; COGS = 20,000 - 6,000 = 14,000p; Gross Profit = 1,000p
    val batchCostMap = db.productBatchDao().getAllBatchesSync().associate { it.id to it.purchaseCostPerPiecePaise }
    val pl = BusinessReportEngine.calculateProfitAndLoss(
      salesInvoices = db.salesInvoiceDao().getAllSync().map { it.toDomain() },
      salesItems = db.salesInvoiceItemDao().getAllItemsSync().map { it.toDomain() },
      batchCostMap = batchCostMap,
      expenses = emptyList(),
      salesReturns = db.salesReturnDao().getAllSync().map { it.toDomain() },
      salesReturnItems = db.salesReturnDao().getAllItemsSync().map { it.toDomain() }
    )
    assertEquals(15_000L, pl.totalSalesRevenuePaise)
    assertEquals(14_000L, pl.totalCogsPaise)
    assertEquals(1_000L, pl.grossProfitPaise)
    assertEquals(1_000L, pl.netProfitPaise)
    assertEquals(1, pl.invoiceCount)
    assertEquals(10, pl.totalPiecesSold)

    // Attempt over-return (> 10 remaining of 20 sold) -> MUST reject!
    try {
      returnRepo.createSalesReturn(
        salesInvoiceId = saleId,
        requestedLines = listOf(ReturnRepository.RequestedSalesReturnLine(saleItemId, 11, ReturnCondition.RESELLABLE)),
        settlementType = ReturnSettlementType.ADVANCE,
        reason = "Over return attempt"
      )
      fail("Should block returning more pieces than remaining sold quantity")
    } catch (e: IllegalStateException) {
      assertTrue(e.message!!.contains("remaining returnable"))
    }

    // Purchase Return on remaining 86 pcs -> return 16 pcs @ 1,000p = 16,000p via DEBIT_NOTE
    val purchaseId = db.purchaseInvoiceDao().getAllSync().first().id
    val purItemId = db.purchaseInvoiceItemDao().getItemsForInvoiceSync(purchaseId).first().id
    returnRepo.createPurchaseReturn(
      purchaseInvoiceId = purchaseId,
      returnDate = now + 2 * dayMs,
      requestedLines = listOf(ReturnRepository.RequestedPurchaseReturnLine(purItemId, 16, ReturnCondition.DAMAGED)),
      settlementType = ReturnSettlementType.DEBIT_NOTE,
      reason = "Damaged batch boxes returned to factory"
    )
    assertEquals(70, db.productBatchDao().getAllBatchesSync().filter { it.productId == prodId }.sumOf { it.currentQuantityPieces })
    val supEntries = db.subledgerDao().getEntriesForPartySync(PartyType.SUPPLIER, supId).filter { !it.isReversed }.map { it.toDomain() }
    assertEquals(84_000L, SubledgerCalculationEngine.calculateSupplierBalance(supEntries))
  }
  }

  @Test
  fun paymentReversal_enforcesBlockVsCascadeAndRecomputesInvoices() { runBlocking {
    val now = 1_720_000_000_000L
    val (prodId, _, custPair) = seedBaseData(now)
    val cust1Id = custPair.first

    // Inv1 = 10,000p; Pay 18,000p (8,000p becomes ADVANCE)
    val inv1Id = salesRepo.createSalesInvoice(
      customerId = cust1Id,
      invoiceNumber = "INV-REV-01",
      invoiceDate = now,
      dueDate = now + 7 * dayMs,
      requestedLines = listOf(SalesRepository.RequestedSaleLine(prodId, 10, 1000L)),
      paidAmountPaise = 18_000L,
      paymentMethod = PaymentMethod.UPI,
      notes = ""
    )
    val paymentEntryId = db.subledgerDao().getEntriesForPartySync(PartyType.CUSTOMER, cust1Id)
      .filter { !it.isReversed }
      .first { it.creditPaise == 18_000L }.id

    // Inv2 = 12,000p using the 8,000p ADVANCE
    val inv2Id = salesRepo.createSalesInvoice(
      customerId = cust1Id,
      invoiceNumber = "INV-REV-02",
      invoiceDate = now + dayMs,
      dueDate = now + 8 * dayMs,
      requestedLines = listOf(SalesRepository.RequestedSaleLine(prodId, 12, 1000L)),
      paidAmountPaise = 0L,
      paymentMethod = null,
      notes = "",
      applyAvailableAdvance = true
    )
    assertEquals(4_000L, db.salesInvoiceDao().getById(inv2Id)!!.toDomain().balanceDuePaise)

    // BLOCK_IF_CONSUMED must reject reversing paymentEntryId
    try {
      subledgerRepo.reversePaymentTransaction(paymentEntryId, "UPI bounced", AdvanceReversalStrategy.BLOCK_IF_CONSUMED)
      fail("BLOCK_IF_CONSUMED must prevent reversing payment whose advance was already applied to INV-REV-02")
    } catch (_: IllegalStateException) {
      // Expected
    }

    // CASCADE_UNAPPLY reverses payment + offsets/unapplies advance from INV-REV-02, recomputing both invoices to UNPAID!
    subledgerRepo.reversePaymentTransaction(paymentEntryId, "UPI failed", AdvanceReversalStrategy.CASCADE_UNAPPLY)
    val inv1After = db.salesInvoiceDao().getById(inv1Id)!!.toDomain()
    val inv2After = db.salesInvoiceDao().getById(inv2Id)!!.toDomain()
    assertEquals(0L, inv1After.paidAmountPaise)
    assertEquals(10_000L, inv1After.balanceDuePaise)
    assertEquals(PaymentStatus.UNPAID, inv1After.paymentStatus)
    assertEquals(0L, inv2After.paidAmountPaise)
    assertEquals(12_000L, inv2After.balanceDuePaise)
    assertEquals(PaymentStatus.UNPAID, inv2After.paymentStatus)
    assertTrue(db.subledgerDao().getByIdSync(paymentEntryId)!!.isReversed)
    val cust1EntriesAfter = db.subledgerDao().getEntriesForPartySync(PartyType.CUSTOMER, cust1Id).filter { !it.isReversed }.map { it.toDomain() }
    assertEquals(22_000L, SubledgerCalculationEngine.calculateCustomerBalance(cust1EntriesAfter))
  }
  }

  @Test
  fun openingBalanceEditAndCreditDebitNotesLifecycle_stayConsistentAndAudited() { runBlocking {
    val now = 1_720_000_000_000L
    val custId = customerRepo.saveCustomer(
      Customer(
        shopName = "Kovalam Ice",
        ownerName = "Babu",
        phoneNumber = "9847555666",
        address = "Beach Rd",
        openingBalancePaise = -5_000L, // Start with Rs 50 Advance credit
        openingBalanceDate = now - 10 * dayMs
      ),
      openingEditReason = "Initial migration advance"
    )
    var cust = db.customerDao().getCustomerSync(custId)!!.toDomain()
    // Edit opening balance from -5,000p Advance to +12,000p Due with custom date
    customerRepo.saveCustomer(
      cust.copy(openingBalancePaise = 12_000L, openingBalanceDate = now - 5 * dayMs),
      openingEditReason = "Corrected paper ledger opening due"
    )
    val obEntries = db.subledgerDao().getEntriesForPartySync(PartyType.CUSTOMER, custId).filter { !it.isReversed }.map { it.toDomain() }
    assertEquals(12_000L, SubledgerCalculationEngine.calculateCustomerBalance(obEntries))
    val openingAlloc = db.paymentAllocationDao().getAllSync().filter { !it.isReversed && it.partyType == PartyType.CUSTOMER && it.partyId == custId }
      .filter { it.allocationType == PaymentAllocationType.OPENING_BALANCE }
    assertEquals(0L, openingAlloc.sumOf { it.allocatedAmountPaise })

    // Standalone Credit Note: issue Rs 150 (15,000p), apply 12,000p to dues? Note: applies to open invoices, so let's issue an unused 7,000p note -> cancel it; and issue 8,000p -> settle as refund
    val noteUnusedId = noteRepo.createNote(
      noteType = CreditDebitNoteType.CREDIT_NOTE,
      partyType = PartyType.CUSTOMER,
      partyId = custId,
      amountPaise = 7_000L,
      reason = "Promo scheme credit",
      applyToDues = false
    )
    noteRepo.cancelUnusedNote(noteUnusedId, "Entered on wrong customer")
    assertEquals(NoteStatus.CANCELLED, db.creditDebitNoteDao().getByIdSync(noteUnusedId)!!.status)

    val noteRefundId = noteRepo.createNote(
      noteType = CreditDebitNoteType.CREDIT_NOTE,
      partyType = PartyType.CUSTOMER,
      partyId = custId,
      amountPaise = 8_000L,
      reason = "Price difference rebate",
      applyToDues = false
    )
    val refundId = noteRepo.settleOpenNoteAsRefundRecord(noteRefundId, PaymentMethod.UPI, "Settling rebate via UPI")
    assertEquals(NoteStatus.SETTLED, db.creditDebitNoteDao().getByIdSync(noteRefundId)!!.status)
    assertEquals(8_000L, db.refundRecordDao().getByIdSync(refundId)!!.amountPaise)
    assertTrue(db.financialAuditLogDao().getAllSync().any { it.operationType == AuditOperationType.OPENING_BALANCE_EDIT })
  }
  }

  @Test
  fun migration1To5AndBackupV5RoundTripAndXlsxFormatting_succeedWithZeroDataLoss() { runBlocking {
    val dbName = "icetrack_v1_to_v5_test.db"
    val now = 1_710_000_000_000L

    // 1. Verify full V1 -> V2 -> V3 -> V4 -> V5 migration chain
    migrationHelper.createDatabase(dbName, 1).apply {
      execSQL("INSERT INTO products (id, name, sku, category, defaultPurchasePricePaise, defaultSellingPricePaise, minStockLevelPieces, isActive, createdAt, deletedAt) VALUES (1, 'Chocobar', 'CB-1', 'Bars', 1000, 1500, 10, 1, $now, NULL)")
      execSQL("INSERT INTO product_units (id, productId, piecesPerBox, boxesPerCarton) VALUES (1, 1, 20, 5)")
      execSQL("INSERT INTO suppliers (id, name, contactPerson, phoneNumber, address, openingBalancePaise, openingBalanceDate, creditTermsDays, creditLimitPaise, isActive, createdAt, deletedAt) VALUES (1, 'Legacy Sup', 'Ali', '9847000001', 'Kochi', 0, $now, 15, 0, 1, $now, NULL)")
      execSQL("INSERT INTO customers (id, shopName, ownerName, phoneNumber, address, openingBalancePaise, openingBalanceDate, creditLimitPaise, paymentTermsDays, isActive, createdAt, deletedAt) VALUES (1, 'Legacy Shop', 'Biju', '9847000002', 'Thrissur', 5000, $now, 0, 7, 1, $now, NULL)")
      close()
    }
    val migratedDb = migrationHelper.runMigrationsAndValidate(
      dbName,
      5,
      true,
      DatabaseMigrations.MIGRATION_1_2,
      DatabaseMigrations.MIGRATION_2_3,
      DatabaseMigrations.MIGRATION_3_4,
      DatabaseMigrations.MIGRATION_4_5
    )
    migratedDb.close()

    // 2. Seed rich Phase 3 data in live in-memory DB -> Backup V5 -> Restore into fresh DB
    val (prodId, supId, custPair) = seedBaseData(now)
    val cust1Id = custPair.first
    val saleId = salesRepo.createSalesInvoice(
      customerId = cust1Id,
      invoiceNumber = "INV-BKP-01",
      invoiceDate = now,
      dueDate = now + 7 * dayMs,
      requestedLines = listOf(SalesRepository.RequestedSaleLine(prodId, 10, 1500L)),
      paidAmountPaise = 15_000L,
      paymentMethod = PaymentMethod.CASH,
      notes = "Backup test"
    )
    val saleItemId = db.salesInvoiceItemDao().getItemsForInvoiceSync(saleId).first().id
    returnRepo.createSalesReturn(
      salesInvoiceId = saleId,
      requestedLines = listOf(ReturnRepository.RequestedSalesReturnLine(saleItemId, 2, ReturnCondition.RESELLABLE)),
      settlementType = ReturnSettlementType.CREDIT_NOTE,
      reason = "Surplus return before backup"
    )

    val json = BackupRestoreService.exportDatabaseFromDatabase(db)
    val validation = BackupRestoreService.validateBackupJson(json)
    assertTrue(validation.isValid)
    assertEquals(5, validation.schemaVersion)
    assertEquals(1, validation.returnCount)
    assertTrue(validation.noteCount >= 1)
    assertTrue(validation.auditLogCount >= 1)

    val freshDb = Room.inMemoryDatabaseBuilder(context, IceTrackDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val restoreResult = BackupRestoreService.restoreDatabaseFromJson(freshDb, json, "JUnit restore verification")
      assertTrue(restoreResult.isValid)
      assertEquals(1, freshDb.salesReturnDao().getAllSync().size)
      assertTrue(freshDb.creditDebitNoteDao().getAllSync().isNotEmpty())
      assertTrue(freshDb.financialAuditLogDao().getAllSync().any { it.operationType == AuditOperationType.BACKUP_RESTORE })
    } finally {
      freshDb.close()
    }

    // 3. Verify Indian Currency Formatting & XlsxWorkbookWriter BigDecimal + Lakh/Crore Excel Format
    assertEquals("₹12,34,567.89", SubledgerCalculationEngine.formatPaise(123_456_789L))
    val xlsxFile = File(context.cacheDir, "phase3_xlsx_verify.xlsx")
    XlsxWorkbookWriter.writeWorkbook(
      outputFile = xlsxFile,
      sheets = listOf(
        XlsxSheet(
          name = "P&L",
          titleBanner = "IceTrack Test",
          subtitleBanner = "Verification",
          headers = listOf("Item", "Amount"),
          rows = listOf(listOf(XlsxCell.Text("Net Sales"), XlsxCell.Currency(123_456_789L)))
        )
      )
    )
    assertTrue(xlsxFile.exists() && xlsxFile.length() > 0L)
    ZipFile(xlsxFile).use { zip ->
      val stylesXml = zip.getInputStream(zip.getEntry("xl/styles.xml")).bufferedReader().readText()
      val sheetXml = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")).bufferedReader().readText()
      assertTrue(stylesXml.contains("##\\,##\\,##0.00"))
      assertTrue(sheetXml.contains("<v>1234567.89</v>"))
    }
    xlsxFile.delete()
  }
  }
}
