package com.example

import com.example.domain.engine.BusinessReportEngine
import com.example.domain.model.Expense
import com.example.domain.model.ExpenseCategory
import com.example.domain.model.PartyType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentStatus
import com.example.domain.model.Product
import com.example.domain.model.ProductBatch
import com.example.domain.model.PurchaseInvoice
import com.example.domain.model.SalesInvoice
import com.example.domain.model.SalesInvoiceItem
import com.example.domain.model.SubledgerEntry
import com.example.domain.model.SubledgerEntryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BusinessReportEngineTest {

  private val dayMillis = 86_400_000L

  @Test
  fun testProfitLossCalculation_trueBatchCogsAndNetProfit() {
    val now = System.currentTimeMillis()

    val batchCostMap = mapOf(101L to 1000L, 102L to 2500L)

    val invoice = SalesInvoice(
      id = 501L,
      invoiceNumber = "INV-001",
      customerId = 1L,
      invoiceDate = now,
      dueDate = now + 7 * dayMillis,
      totalAmountPaise = 180000L,
      paidAmountPaise = 180000L,
      paymentStatus = PaymentStatus.PAID,
      isConfirmed = true
    )

    val item1 = SalesInvoiceItem(id = 1L, salesInvoiceId = 501L, productId = 1L, batchId = 101L, quantityPieces = 50, unitPricePaise = 2000L, lineTotalPaise = 100000L)
    val item2 = SalesInvoiceItem(id = 2L, salesInvoiceId = 501L, productId = 2L, batchId = 102L, quantityPieces = 20, unitPricePaise = 4000L, lineTotalPaise = 80000L)

    val exp1 = Expense(id = 1L, category = ExpenseCategory.VEHICLE_FUEL, amountPaise = 30000L, expenseDate = now, paymentMethod = PaymentMethod.CASH, notes = "Delivery van diesel")
    val exp2 = Expense(id = 2L, category = ExpenseCategory.ELECTRICITY_COLD_STORAGE, amountPaise = 20000L, expenseDate = now, paymentMethod = PaymentMethod.UPI, notes = "Deep freezer power")

    val summary = BusinessReportEngine.calculateProfitAndLoss(
      salesInvoices = listOf(invoice),
      salesItems = listOf(item1, item2),
      batchCostMap = batchCostMap,
      expenses = listOf(exp1, exp2),
      startDate = null,
      endDate = null
    )

    assertEquals(180000L, summary.totalSalesRevenuePaise)
    assertEquals(100000L, summary.totalCogsPaise)
    assertEquals(80000L, summary.grossProfitPaise)
    assertEquals(50000L, summary.totalOperatingExpensesPaise)
    assertEquals(30000L, summary.netProfitPaise)
    assertEquals(1, summary.invoiceCount)
    assertEquals(70, summary.totalPiecesSold)
  }

  @Test
  fun testProfitLossCalculation_netLossScenario() {
    val now = System.currentTimeMillis()

    val invoice = SalesInvoice(id = 601L, invoiceNumber = "INV-002", customerId = 1L, invoiceDate = now, dueDate = now, totalAmountPaise = 10000L, paidAmountPaise = 10000L, paymentStatus = PaymentStatus.PAID, isConfirmed = true)
    val item = SalesInvoiceItem(id = 10L, salesInvoiceId = 601L, productId = 1L, batchId = 201L, quantityPieces = 10, unitPricePaise = 1000L, lineTotalPaise = 10000L)
    val batchCostMap = mapOf(201L to 800L)

    val exp = Expense(id = 1L, category = ExpenseCategory.VEHICLE_MAINTENANCE, amountPaise = 50000L, expenseDate = now, paymentMethod = PaymentMethod.CASH, notes = "Compressor repair")

    val summary = BusinessReportEngine.calculateProfitAndLoss(
      salesInvoices = listOf(invoice),
      salesItems = listOf(item),
      batchCostMap = batchCostMap,
      expenses = listOf(exp),
      startDate = null,
      endDate = null
    )

    assertEquals(2000L, summary.grossProfitPaise)
    assertEquals(50000L, summary.totalOperatingExpensesPaise)
    assertEquals(-48000L, summary.netProfitPaise)
    assertTrue("Net profit should be negative", summary.netProfitPaise < 0)
  }

  @Test
  fun testProductSalesPerformance() {
    val p1 = Product(id = 1L, name = "Butterscotch Tub", sku = "BST-500", category = "Tubs", defaultPurchasePricePaise = 12000L, defaultSellingPricePaise = 18000L, minStockLevelPieces = 5)
    val batchCostMap = mapOf(301L to 12000L)
    val item = SalesInvoiceItem(id = 11L, salesInvoiceId = 701L, productId = 1L, batchId = 301L, quantityPieces = 10, unitPricePaise = 18000L, lineTotalPaise = 180000L)

    val perf = BusinessReportEngine.calculateProductSales(
      products = listOf(p1),
      salesItems = listOf(item),
      batchCostMap = batchCostMap
    )

    assertEquals(1, perf.size)
    val pPerf = perf[0]
    assertEquals(1L, pPerf.productId)
    assertEquals("Butterscotch Tub", pPerf.productName)
    assertEquals(10, pPerf.totalPiecesSold)
    assertEquals(180000L, pPerf.totalRevenuePaise)
    assertEquals(120000L, pPerf.totalCogsPaise)
    assertEquals(60000L, pPerf.grossProfitPaise)
  }

  @Test
  fun testShopSalesPerformance_duesAndBilling() {
    val now = System.currentTimeMillis()

    val inv1 = SalesInvoice(id = 801L, invoiceNumber = "INV-101", customerId = 1L, invoiceDate = now, dueDate = now, totalAmountPaise = 50000L, paidAmountPaise = 50000L, paymentStatus = PaymentStatus.PAID, isConfirmed = true)
    val inv2 = SalesInvoice(id = 802L, invoiceNumber = "INV-102", customerId = 1L, invoiceDate = now, dueDate = now, totalAmountPaise = 70000L, paidAmountPaise = 20000L, paymentStatus = PaymentStatus.PARTIALLY_PAID, isConfirmed = true)

    val perf = BusinessReportEngine.calculateShopSales(
      customers = mapOf(1L to "Grand Bakers"),
      salesInvoices = listOf(inv1, inv2)
    )

    assertEquals(1, perf.size)
    val cPerf = perf[0]
    assertEquals(1L, cPerf.customerId)
    assertEquals("Grand Bakers", cPerf.shopName)
    assertEquals(2, cPerf.invoiceCount)
    assertEquals(120000L, cPerf.totalSalesPaise)
    assertEquals(70000L, cPerf.paidAmountPaise)
    assertEquals(50000L, cPerf.outstandingDuesPaise)
  }

  @Test
  fun testDateRangeFiltering_todayWeekMonthAll() {
    val now = System.currentTimeMillis()
    val todayInv = SalesInvoice(id = 1L, invoiceNumber = "S-TODAY", customerId = 1L, invoiceDate = now, dueDate = now, totalAmountPaise = 10000L, paidAmountPaise = 10000L, paymentStatus = PaymentStatus.PAID, isConfirmed = true)
    val fiveDaysAgoInv = SalesInvoice(id = 2L, invoiceNumber = "S-5D", customerId = 1L, invoiceDate = now - (5 * dayMillis), dueDate = now, totalAmountPaise = 20000L, paidAmountPaise = 0L, paymentStatus = PaymentStatus.UNPAID, isConfirmed = true)
    val twentyDaysAgoInv = SalesInvoice(id = 3L, invoiceNumber = "S-20D", customerId = 1L, invoiceDate = now - (20 * dayMillis), dueDate = now, totalAmountPaise = 30000L, paidAmountPaise = 0L, paymentStatus = PaymentStatus.UNPAID, isConfirmed = true)
    val fortyDaysAgoInv = SalesInvoice(id = 4L, invoiceNumber = "S-40D", customerId = 1L, invoiceDate = now - (40 * dayMillis), dueDate = now, totalAmountPaise = 40000L, paidAmountPaise = 0L, paymentStatus = PaymentStatus.UNPAID, isConfirmed = true)
    val allInvoices = listOf(todayInv, fiveDaysAgoInv, twentyDaysAgoInv, fortyDaysAgoInv)

    assertNull(BusinessReportEngine.computeStartDate("ALL", now))
    val todayStart = BusinessReportEngine.computeStartDate("TODAY", now)!!
    val weekStart = BusinessReportEngine.computeStartDate("WEEK", now)!!
    val monthStart = BusinessReportEngine.computeStartDate("MONTH", now)!!

    assertEquals(4, BusinessReportEngine.filterSalesByDateRange(allInvoices, null).size)
    assertEquals(listOf("S-TODAY"), BusinessReportEngine.filterSalesByDateRange(allInvoices, todayStart).map { it.invoiceNumber })
    assertEquals(listOf("S-TODAY", "S-5D"), BusinessReportEngine.filterSalesByDateRange(allInvoices, weekStart).map { it.invoiceNumber })
    assertEquals(listOf("S-TODAY", "S-5D", "S-20D"), BusinessReportEngine.filterSalesByDateRange(allInvoices, monthStart).map { it.invoiceNumber })

    val pToday = PurchaseInvoice(id = 11L, invoiceNumber = "P-TODAY", supplierId = 1L, invoiceDate = now, dueDate = now, totalAmountPaise = 5000L, paidAmountPaise = 5000L, paymentStatus = PaymentStatus.PAID, isConfirmed = true)
    val pOld = PurchaseInvoice(id = 12L, invoiceNumber = "P-OLD", supplierId = 1L, invoiceDate = now - (40 * dayMillis), dueDate = now, totalAmountPaise = 8000L, paidAmountPaise = 0L, paymentStatus = PaymentStatus.UNPAID, isConfirmed = true)
    assertEquals(listOf("P-TODAY"), BusinessReportEngine.filterPurchasesByDateRange(listOf(pToday, pOld), monthStart).map { it.invoiceNumber })
  }

  @Test
  fun testSupplierPurchasesCalculation() {
    val now = System.currentTimeMillis()
    val p1 = PurchaseInvoice(id = 1L, invoiceNumber = "PUR-01", supplierId = 10L, invoiceDate = now, dueDate = now, totalAmountPaise = 100000L, paidAmountPaise = 60000L, paymentStatus = PaymentStatus.PARTIALLY_PAID, isConfirmed = true)
    val p2 = PurchaseInvoice(id = 2L, invoiceNumber = "PUR-02", supplierId = 10L, invoiceDate = now, dueDate = now, totalAmountPaise = 50000L, paidAmountPaise = 50000L, paymentStatus = PaymentStatus.PAID, isConfirmed = true)
    val p3 = PurchaseInvoice(id = 3L, invoiceNumber = "PUR-03", supplierId = 20L, invoiceDate = now, dueDate = now, totalAmountPaise = 80000L, paidAmountPaise = 0L, paymentStatus = PaymentStatus.UNPAID, isConfirmed = true)

    val summaries = BusinessReportEngine.calculateSupplierPurchases(
      suppliers = mapOf(10L to "Amul Dairy", 20L to "Kwality Cold"),
      purchaseInvoices = listOf(p1, p2, p3)
    )

    assertEquals(2, summaries.size)
    assertEquals("Amul Dairy", summaries[0].supplierName)
    assertEquals(2, summaries[0].billCount)
    assertEquals(150000L, summaries[0].totalPurchasedPaise)
    assertEquals(110000L, summaries[0].totalPaidPaise)
    assertEquals(40000L, summaries[0].balancePayablePaise)
    assertEquals(80000L, summaries[1].balancePayablePaise)
  }

  @Test
  fun testCustomerAccountStatement_openingBalanceRunningBalanceAndAdjustments() {
    val now = System.currentTimeMillis()
    val periodStart = now - (10 * dayMillis)

    val entries = listOf(
      // Before startDate -> rolls into brought-forward opening balance: +5000 - 2000 = +3000 (300000 paise)
      SubledgerEntry(id = 1L, partyType = PartyType.CUSTOMER, partyId = 5L, entryType = SubledgerEntryType.INVOICE, referenceType = "INVOICE", referenceId = 100L, debitPaise = 500000L, creditPaise = 0L, entryDate = now - (20 * dayMillis), notes = "Prior invoice"),
      SubledgerEntry(id = 2L, partyType = PartyType.CUSTOMER, partyId = 5L, entryType = SubledgerEntryType.PAYMENT, referenceType = "PAYMENT", referenceId = 101L, debitPaise = 0L, creditPaise = 200000L, entryDate = now - (15 * dayMillis), notes = "Prior payment"),
      // Reversed entry -> must be excluded
      SubledgerEntry(id = 3L, partyType = PartyType.CUSTOMER, partyId = 5L, entryType = SubledgerEntryType.INVOICE, referenceType = "INVOICE", referenceId = 102L, debitPaise = 999900L, creditPaise = 0L, entryDate = now - (5 * dayMillis), isReversed = true, notes = "Cancelled"),
      // Period entries
      SubledgerEntry(id = 4L, partyType = PartyType.CUSTOMER, partyId = 5L, entryType = SubledgerEntryType.INVOICE, referenceType = "INVOICE", referenceId = 103L, debitPaise = 400000L, creditPaise = 0L, entryDate = now - (5 * dayMillis), notes = "Fresh delivery"),
      SubledgerEntry(id = 5L, partyType = PartyType.CUSTOMER, partyId = 5L, entryType = SubledgerEntryType.PAYMENT, referenceType = "PAYMENT", referenceId = 104L, debitPaise = 0L, creditPaise = 150000L, entryDate = now - (3 * dayMillis), notes = "UPI receipt"),
      SubledgerEntry(id = 6L, partyType = PartyType.CUSTOMER, partyId = 5L, entryType = SubledgerEntryType.ADJUSTMENT, referenceType = "CREDIT_NOTE", referenceId = 105L, debitPaise = 0L, creditPaise = 50000L, entryDate = now - (1 * dayMillis), notes = "Melted pack return")
    )

    val report = BusinessReportEngine.buildAccountStatement(
      partyId = 5L,
      partyName = "Metro Ice Cream Mart",
      partyType = PartyType.CUSTOMER,
      contactDetails = "Ph: 9999900000",
      entries = entries,
      startDate = periodStart,
      endDate = null,
      periodLabel = "Last 10 Days"
    )

    assertEquals(300000L, report.openingBalancePaise)
    assertEquals(3, report.lines.size)
    assertEquals(400000L, report.totalDebitsPaise)
    assertEquals(200000L, report.totalCreditsPaise)
    // Line 1: 300000 + 400000 = 700000
    assertEquals(700000L, report.lines[0].runningBalancePaise)
    // Line 2: 700000 - 150000 = 550000
    assertEquals(550000L, report.lines[1].runningBalancePaise)
    // Line 3: 550000 - 50000 = 500000
    assertEquals(500000L, report.lines[2].runningBalancePaise)
    assertEquals(500000L, report.closingBalancePaise)
  }

  @Test
  fun testSupplierAccountStatement_openingBalanceRunningBalanceAndAdjustments() {
    val now = System.currentTimeMillis()
    val periodStart = now - (7 * dayMillis)

    val entries = listOf(
      // Before startDate -> +8000 bill - 3000 payment = +5000 (500000 paise)
      SubledgerEntry(id = 10L, partyType = PartyType.SUPPLIER, partyId = 9L, entryType = SubledgerEntryType.OPENING_BALANCE, referenceType = "OPENING", referenceId = 0L, debitPaise = 0L, creditPaise = 800000L, entryDate = now - (25 * dayMillis), notes = "Initial payable"),
      SubledgerEntry(id = 11L, partyType = PartyType.SUPPLIER, partyId = 9L, entryType = SubledgerEntryType.PAYMENT, referenceType = "SUPPLIER_PAY", referenceId = 20L, debitPaise = 300000L, creditPaise = 0L, entryDate = now - (14 * dayMillis), notes = "Bank transfer"),
      // Within period
      SubledgerEntry(id = 12L, partyType = PartyType.SUPPLIER, partyId = 9L, entryType = SubledgerEntryType.INVOICE, referenceType = "PURCHASE", referenceId = 30L, debitPaise = 0L, creditPaise = 600000L, entryDate = now - (4 * dayMillis), notes = "Batch inward"),
      SubledgerEntry(id = 13L, partyType = PartyType.SUPPLIER, partyId = 9L, entryType = SubledgerEntryType.PAYMENT, referenceType = "SUPPLIER_PAY", referenceId = 31L, debitPaise = 400000L, creditPaise = 0L, entryDate = now - (2 * dayMillis), notes = "Partial payout"),
      SubledgerEntry(id = 14L, partyType = PartyType.SUPPLIER, partyId = 9L, entryType = SubledgerEntryType.DEBIT_NOTE, referenceType = "PURCHASE_RETURN", referenceId = 32L, debitPaise = 100000L, creditPaise = 0L, entryDate = now - (1 * dayMillis), notes = "Damaged cartons returned")
    )

    val report = BusinessReportEngine.buildAccountStatement(
      partyId = 9L,
      partyName = "Arctic Creamery Pvt Ltd",
      partyType = PartyType.SUPPLIER,
      contactDetails = "Ph: 8888811111",
      entries = entries,
      startDate = periodStart,
      endDate = null,
      periodLabel = "Last 7 Days"
    )

    assertEquals(500000L, report.openingBalancePaise)
    assertEquals(3, report.lines.size)
    // Line 1 (+600000): 1100000
    assertEquals(1100000L, report.lines[0].runningBalancePaise)
    // Line 2 (-400000): 700000
    assertEquals(700000L, report.lines[1].runningBalancePaise)
    // Line 3 (-100000 return): 600000
    assertEquals(600000L, report.lines[2].runningBalancePaise)
    assertEquals(600000L, report.closingBalancePaise)
  }

  @Test
  fun testAccountStatement_emptyDataScenario() {
    val report = BusinessReportEngine.buildAccountStatement(
      partyId = 99L,
      partyName = "Zero Shop",
      partyType = PartyType.CUSTOMER,
      contactDetails = "",
      entries = emptyList(),
      startDate = null,
      endDate = null,
      periodLabel = "All Time"
    )

    assertEquals(0L, report.openingBalancePaise)
    assertEquals(0L, report.closingBalancePaise)
    assertEquals(0L, report.totalDebitsPaise)
    assertEquals(0L, report.totalCreditsPaise)
    assertTrue(report.lines.isEmpty())
  }
}
