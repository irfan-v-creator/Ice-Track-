package com.example

import com.example.domain.engine.BusinessReportEngine
import com.example.domain.engine.ProductSalesPerformance
import com.example.domain.engine.ProfitLossSummary
import com.example.domain.engine.ShopSalesPerformance
import com.example.domain.engine.SupplierPurchaseSummary
import com.example.domain.model.Customer
import com.example.domain.model.PartyType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentStatus
import com.example.domain.model.Product
import com.example.domain.model.ProductBatch
import com.example.domain.model.PurchaseInvoice
import com.example.domain.model.SalesInvoice
import com.example.domain.model.SubledgerEntry
import com.example.domain.model.SubledgerEntryType
import com.example.domain.model.Supplier
import com.example.service.ExportDocumentService
import com.example.service.ExportFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

class ExportDocumentServiceTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  private val dayMillis = 86_400_000L

  private fun assertValidXlsx(file: File, expectedSheetNames: List<String>, expectedTextSnippets: List<String>) {
    assertTrue("XLSX file should exist and pass integrity check", ExportDocumentService.validateExportedFile(file, ExportFormat.XLSX))
    ZipFile(file).use { zip ->
      val contentTypes = zip.getEntry("[Content_Types].xml")?.let { zip.getInputStream(it).bufferedReader().readText() } ?: ""
      val workbookXml = zip.getEntry("xl/workbook.xml")?.let { zip.getInputStream(it).bufferedReader().readText() } ?: ""
      val stylesXml = zip.getEntry("xl/styles.xml")?.let { zip.getInputStream(it).bufferedReader().readText() } ?: ""

      assertTrue("Missing [Content_Types].xml", contentTypes.contains("worksheet+xml"))
      assertTrue("Missing styles fonts", stylesXml.contains("<fonts count=\"5\">"))
      expectedSheetNames.forEach { sheet ->
        assertTrue("Workbook missing sheet '$sheet'", workbookXml.contains(sheet.replace("&", "&amp;")))
      }

      val allSheetContent = buildString {
        for (idx in 1..expectedSheetNames.size) {
          val entry = zip.getEntry("xl/worksheets/sheet$idx.xml")
          assertTrue("Missing sheet$idx.xml", entry != null)
          append(zip.getInputStream(entry!!).bufferedReader().readText())
        }
      }
      expectedTextSnippets.forEach { snippet ->
        assertTrue("Sheet XML missing '$snippet'", allSheetContent.contains(snippet))
      }
    }
  }

  private fun assertValidPdf(file: File) {
    assertTrue("PDF file should exist and pass header check", ExportDocumentService.validateExportedFile(file, ExportFormat.PDF))
    assertTrue("PDF file should be non-empty", file.length() > 100L)
  }

  // ==========================================================
  // Existing Core Export Tests (Preserved)
  // ==========================================================

  @Test
  fun testProfitLossCsvGeneration() {
    val summary = ProfitLossSummary(
      totalSalesRevenuePaise = 250000L,
      totalCogsPaise = 150000L,
      grossProfitPaise = 100000L,
      totalOperatingExpensesPaise = 20000L,
      netProfitPaise = 80000L,
      invoiceCount = 12,
      totalPiecesSold = 150
    )

    val csv = ExportDocumentService.generateProfitLossCsv(summary)

    assertTrue("Header should contain Metric", csv.contains("Metric,Amount (INR),Notes"))
    assertTrue("Should contain Total Sales Revenue", csv.contains("Total Sales Revenue,\"2,500.00\""))
    assertTrue("Should contain True Cost of Goods Sold", csv.contains("Cost of Goods Sold (COGS),\"1,500.00\""))
    assertTrue("Should contain Gross Profit", csv.contains("Gross Profit,\"1,000.00\""))
    assertTrue("Should contain Operating Expenses", csv.contains("Operating Expenses,\"200.00\""))
    assertTrue("Should contain Net Profit", csv.contains("Net Business Profit,\"800.00\""))
    assertTrue("Should contain Total Pieces Sold", csv.contains("Total Pieces Sold,150"))
  }

  @Test
  fun testInventoryCsvGeneration() {
    val product = Product(id = 1L, name = "Vanilla Cup 100ml", sku = "VC-100", category = "Cups", defaultPurchasePricePaise = 1200L, defaultSellingPricePaise = 2000L, minStockLevelPieces = 15)
    val batch = ProductBatch(id = 10L, productId = 1L, batchNumber = "B-999", purchaseDate = System.currentTimeMillis(), expiryDate = System.currentTimeMillis() + dayMillis, initialQuantityPieces = 100, currentQuantityPieces = 75, purchaseCostPerPiecePaise = 1200L)

    val csv = ExportDocumentService.generateInventoryCsv(listOf(product), listOf(batch))

    assertTrue(csv.contains("Product Name,SKU,Category,Batch Number,Current Stock (Pcs),Cost/Pc (INR),Total Valuation (INR),Expiry Date,Status"))
    assertTrue(csv.contains("Vanilla Cup 100ml"))
    assertTrue(csv.contains("VC-100"))
    assertTrue(csv.contains("B-999"))
    assertTrue(csv.contains("\"12.00\""))
    assertTrue(csv.contains("\"900.00\""))
    assertTrue(csv.contains("75"))
  }

  @Test
  fun testCustomerAccountStatementCsv_runningBalanceRule() {
    val now = System.currentTimeMillis()
    val entry1 = SubledgerEntry(id = 1L, partyType = PartyType.CUSTOMER, partyId = 1L, entryType = SubledgerEntryType.INVOICE, referenceType = "INVOICE", referenceId = 1001L, debitPaise = 500000L, creditPaise = 0L, entryDate = now)
    val entry2 = SubledgerEntry(id = 2L, partyType = PartyType.CUSTOMER, partyId = 1L, entryType = SubledgerEntryType.PAYMENT, referenceType = "PAYMENT", referenceId = 2001L, debitPaise = 0L, creditPaise = 300000L, entryDate = now + 1000L)

    val csv = ExportDocumentService.generateAccountStatementCsv("City Bakery", "CUSTOMER", listOf(entry1, entry2))

    assertTrue(csv.contains("Account Statement for: City Bakery (CUSTOMER)"))
    assertTrue(csv.contains("\"5,000.00\",\"0.00\",\"5,000.00\""))
    assertTrue(csv.contains("\"0.00\",\"3,000.00\",\"2,000.00\""))
  }

  @Test
  fun testSupplierAccountStatementCsv_runningBalanceRule() {
    val now = System.currentTimeMillis()
    val entry1 = SubledgerEntry(id = 3L, partyType = PartyType.SUPPLIER, partyId = 2L, entryType = SubledgerEntryType.INVOICE, referenceType = "PURCHASE", referenceId = 3001L, debitPaise = 0L, creditPaise = 1000000L, entryDate = now)
    val entry2 = SubledgerEntry(id = 4L, partyType = PartyType.SUPPLIER, partyId = 2L, entryType = SubledgerEntryType.PAYMENT, referenceType = "PAYMENT", referenceId = 4001L, debitPaise = 400000L, creditPaise = 0L, entryDate = now + 1000L)

    val csv = ExportDocumentService.generateAccountStatementCsv("Dairy Pure Ltd", "SUPPLIER", listOf(entry1, entry2))

    assertTrue(csv.contains("Account Statement for: Dairy Pure Ltd (SUPPLIER)"))
    assertTrue(csv.contains("\"0.00\",\"10,000.00\",\"10,000.00\""))
    assertTrue(csv.contains("\"4,000.00\",\"0.00\",\"6,000.00\""))
  }

  // ==========================================================
  // All 6 Report Exports: CSV, XLSX & PDF Validation Tests
  // ==========================================================

  @Test
  fun testProfitLossExports_csvXlsxPdf() {
    val dir = tempFolder.newFolder("pl_exports")
    val summary = ProfitLossSummary(
      totalSalesRevenuePaise = 520000L,
      totalCogsPaise = 310000L,
      grossProfitPaise = 210000L,
      totalOperatingExpensesPaise = 45000L,
      netProfitPaise = 165000L,
      invoiceCount = 8,
      totalPiecesSold = 240
    )

    val csvFile = ExportDocumentService.exportProfitLossCsvFile(null, summary, "Last 7 Days", dir)
    assertTrue(ExportDocumentService.validateExportedFile(csvFile, ExportFormat.CSV))
    assertTrue(csvFile.readText().contains("Last 7 Days"))

    val xlsxFile = ExportDocumentService.generateProfitLossXlsx(null, summary, "Last 7 Days", dir)
    assertValidXlsx(xlsxFile, listOf("Profit & Loss"), listOf("Total Sales Revenue", "1650.00", "Last 7 Days"))

    val pdfFile = ExportDocumentService.generateProfitLossPdf(null, summary, "Last 7 Days", dir)
    assertValidPdf(pdfFile)
  }

  @Test
  fun testSalesReportExports_csvXlsxPdf() {
    val dir = tempFolder.newFolder("sales_exports")
    val now = System.currentTimeMillis()
    val invoices = listOf(
      SalesInvoice(id = 1L, invoiceNumber = "INV-101", customerId = 1L, invoiceDate = now, dueDate = now + 7 * dayMillis, totalAmountPaise = 120000L, paidAmountPaise = 80000L, paymentStatus = PaymentStatus.PARTIALLY_PAID, paymentMethod = PaymentMethod.UPI, isConfirmed = true),
      SalesInvoice(id = 2L, invoiceNumber = "INV-102", customerId = 2L, invoiceDate = now, dueDate = now + 7 * dayMillis, totalAmountPaise = 65000L, paidAmountPaise = 65000L, paymentStatus = PaymentStatus.PAID, paymentMethod = PaymentMethod.CASH, isConfirmed = true)
    )
    val customers = mapOf(1L to "Kochi Freeze Mart", 2L to "Calicut Cool Bar")
    val productSales = listOf(
      ProductSalesPerformance(1L, "Mango Bar", 100, 185000L, 110000L, 75000L)
    )
    val shopSales = listOf(
      ShopSalesPerformance(1L, "Kochi Freeze Mart", 1, 120000L, 80000L, 40000L),
      ShopSalesPerformance(2L, "Calicut Cool Bar", 1, 65000L, 65000L, 0L)
    )

    val csvFile = ExportDocumentService.exportSalesReportCsvFile(null, invoices, customers, productSales, shopSales, "Today", dir)
    assertTrue(ExportDocumentService.validateExportedFile(csvFile, ExportFormat.CSV))
    val csv = csvFile.readText()
    assertTrue(csv.contains("INV-101"))
    assertTrue(csv.contains("Kochi Freeze Mart"))
    assertTrue(csv.contains("Mango Bar"))

    val xlsxFile = ExportDocumentService.generateSalesReportXlsx(null, invoices, customers, productSales, shopSales, "Today", dir)
    assertValidXlsx(
      xlsxFile,
      listOf("Sales Invoices", "Product Performance", "Shop Billing"),
      listOf("INV-101", "Kochi Freeze Mart", "Mango Bar", "1850.00")
    )

    val pdfFile = ExportDocumentService.generateSalesReportPdf(null, invoices, customers, productSales, "Today", dir)
    assertValidPdf(pdfFile)
  }

  @Test
  fun testPurchaseReportExports_csvXlsxPdf() {
    val dir = tempFolder.newFolder("purchase_exports")
    val now = System.currentTimeMillis()
    val purchases = listOf(
      PurchaseInvoice(id = 1L, invoiceNumber = "PUR-501", supplierId = 10L, invoiceDate = now, dueDate = now + 15 * dayMillis, totalAmountPaise = 300000L, paidAmountPaise = 150000L, paymentStatus = PaymentStatus.PARTIALLY_PAID, paymentMethod = PaymentMethod.BANK_TRANSFER, isConfirmed = true)
    )
    val suppliers = mapOf(10L to "Milma Cold Chain")
    val summaries = listOf(SupplierPurchaseSummary(10L, "Milma Cold Chain", 1, 300000L, 150000L, 150000L))

    val csvFile = ExportDocumentService.exportPurchaseReportCsvFile(null, purchases, suppliers, summaries, "Last 30 Days", dir)
    assertTrue(ExportDocumentService.validateExportedFile(csvFile, ExportFormat.CSV))
    val csv = csvFile.readText()
    assertTrue(csv.contains("PUR-501"))
    assertTrue(csv.contains("Milma Cold Chain"))
    assertTrue(csv.contains("3,000.00"))

    val xlsxFile = ExportDocumentService.generatePurchaseReportXlsx(null, purchases, suppliers, summaries, "Last 30 Days", dir)
    assertValidXlsx(
      xlsxFile,
      listOf("Purchase Bills", "Supplier Summary"),
      listOf("PUR-501", "Milma Cold Chain", "3000.00")
    )

    val pdfFile = ExportDocumentService.generatePurchaseReportPdf(null, purchases, suppliers, "Last 30 Days", dir)
    assertValidPdf(pdfFile)
  }

  @Test
  fun testCustomerStatementExports_specificShopAndAllShops_csvXlsxPdf() {
    val dir = tempFolder.newFolder("cust_stmt_exports")
    val now = System.currentTimeMillis()
    val startDate = now - (10 * dayMillis)
    val c1 = Customer(id = 1L, shopName = "Malabar Treats", ownerName = "Rashid", phoneNumber = "9876543210", address = "MG Road", creditLimitPaise = 1000000L, openingBalancePaise = 0L)
    val c2 = Customer(id = 2L, shopName = "Cochin Cones", ownerName = "Anil", phoneNumber = "9876500000", address = "Marine Drive", creditLimitPaise = 500000L, openingBalancePaise = 0L)
    val entries = listOf(
      // Prior balance = 2,000.00
      SubledgerEntry(id = 1L, partyType = PartyType.CUSTOMER, partyId = 1L, entryType = SubledgerEntryType.INVOICE, referenceType = "INVOICE", referenceId = 10L, debitPaise = 200000L, creditPaise = 0L, entryDate = now - (15 * dayMillis), notes = "Prior"),
      // In period
      SubledgerEntry(id = 2L, partyType = PartyType.CUSTOMER, partyId = 1L, entryType = SubledgerEntryType.INVOICE, referenceType = "INVOICE", referenceId = 11L, debitPaise = 300000L, creditPaise = 0L, entryDate = now - (2 * dayMillis), notes = "Bill 11"),
      SubledgerEntry(id = 3L, partyType = PartyType.CUSTOMER, partyId = 1L, entryType = SubledgerEntryType.PAYMENT, referenceType = "PAYMENT", referenceId = 12L, debitPaise = 0L, creditPaise = 100000L, entryDate = now - dayMillis, notes = "Cash paid")
    )

    // Specific Customer Statement
    val csvFile = ExportDocumentService.exportCustomerStatementCsvFile(null, c1, entries, listOf(c1, c2), startDate, null, "Last 10 Days", dir)
    assertTrue(ExportDocumentService.validateExportedFile(csvFile, ExportFormat.CSV))
    val csv = csvFile.readText()
    assertTrue(csv.contains("Opening Balance,B/F,Opening Balance,\"0.00\",\"0.00\",\"2,000.00\""))
    assertTrue(csv.contains("Closing Balance,C/F,Closing Summary,\"3,000.00\",\"1,000.00\",\"4,000.00\""))

    val xlsxFile = ExportDocumentService.generateCustomerStatementXlsx(null, c1, entries, listOf(c1, c2), startDate, null, "Last 10 Days", dir)
    assertValidXlsx(xlsxFile, listOf("Customer Statement"), listOf("Malabar Treats", "2000.00", "4000.00"))

    val pdfFile = ExportDocumentService.generateCustomerStatementPdf(null, c1, entries, listOf(c1, c2), startDate, null, "Last 10 Days", dir)
    assertValidPdf(pdfFile)

    // All Customers Summary
    val allCsv = ExportDocumentService.exportCustomerStatementCsvFile(null, null, entries, listOf(c1, c2), null, null, "All Time", dir)
    assertTrue(allCsv.readText().contains("Malabar Treats"))
    assertTrue(allCsv.readText().contains("4,000.00"))

    val allXlsx = ExportDocumentService.generateCustomerStatementXlsx(null, null, entries, listOf(c1, c2), null, null, "All Time", dir)
    assertValidXlsx(allXlsx, listOf("All Customers"), listOf("Malabar Treats", "Cochin Cones", "4000.00"))

    val allPdf = ExportDocumentService.generateCustomerStatementPdf(null, null, entries, listOf(c1, c2), null, null, "All Time", dir)
    assertValidPdf(allPdf)
  }

  @Test
  fun testSupplierStatementExports_specificSupplierAndAllSuppliers_csvXlsxPdf() {
    val dir = tempFolder.newFolder("sup_stmt_exports")
    val now = System.currentTimeMillis()
    val startDate = now - (10 * dayMillis)
    val s1 = Supplier(id = 1L, name = "Creambell Factory", contactPerson = "Suresh", phoneNumber = "9000011111", address = "Industrial Estate", creditTermsDays = 30, openingBalancePaise = 0L)
    val entries = listOf(
      SubledgerEntry(id = 1L, partyType = PartyType.SUPPLIER, partyId = 1L, entryType = SubledgerEntryType.INVOICE, referenceType = "PURCHASE", referenceId = 50L, debitPaise = 0L, creditPaise = 500000L, entryDate = now - (15 * dayMillis), notes = "Prior purchase"),
      SubledgerEntry(id = 2L, partyType = PartyType.SUPPLIER, partyId = 1L, entryType = SubledgerEntryType.INVOICE, referenceType = "PURCHASE", referenceId = 51L, debitPaise = 0L, creditPaise = 250000L, entryDate = now - (3 * dayMillis), notes = "Inward bill"),
      SubledgerEntry(id = 3L, partyType = PartyType.SUPPLIER, partyId = 1L, entryType = SubledgerEntryType.PAYMENT, referenceType = "PAYMENT", referenceId = 52L, debitPaise = 150000L, creditPaise = 0L, entryDate = now - dayMillis, notes = "NEFT paid")
    )

    // Specific Supplier Statement (5000 B/F + 2500 bill - 1500 paid = 6000 C/F)
    val csvFile = ExportDocumentService.exportSupplierStatementCsvFile(null, s1, entries, listOf(s1), startDate, null, "Last 10 Days", dir)
    assertTrue(ExportDocumentService.validateExportedFile(csvFile, ExportFormat.CSV))
    val csv = csvFile.readText()
    assertTrue(csv.contains("Opening Balance,B/F,Opening Balance,\"0.00\",\"0.00\",\"5,000.00\""))
    assertTrue(csv.contains("Closing Balance,C/F,Closing Summary,\"1,500.00\",\"2,500.00\",\"6,000.00\""))

    val xlsxFile = ExportDocumentService.generateSupplierStatementXlsx(null, s1, entries, listOf(s1), startDate, null, "Last 10 Days", dir)
    assertValidXlsx(xlsxFile, listOf("Supplier Statement"), listOf("Creambell Factory", "5000.00", "6000.00"))

    val pdfFile = ExportDocumentService.generateSupplierStatementPdf(null, s1, entries, listOf(s1), startDate, null, "Last 10 Days", dir)
    assertValidPdf(pdfFile)

    // All Suppliers Summary
    val allXlsx = ExportDocumentService.generateSupplierStatementXlsx(null, null, entries, listOf(s1), null, null, "All Time", dir)
    assertValidXlsx(allXlsx, listOf("All Suppliers"), listOf("Creambell Factory", "6000.00"))

    val allPdf = ExportDocumentService.generateSupplierStatementPdf(null, null, entries, listOf(s1), null, null, "All Time", dir)
    assertValidPdf(allPdf)
  }

  @Test
  fun testInventoryFefoExports_csvXlsxPdf() {
    val dir = tempFolder.newFolder("inv_exports")
    val now = System.currentTimeMillis()
    val p1 = Product(id = 1L, name = "Chocobar Stick", sku = "CB-70", category = "Sticks", defaultPurchasePricePaise = 1000L, defaultSellingPricePaise = 2000L, minStockLevelPieces = 50)
    val bSoon = ProductBatch(id = 1L, productId = 1L, batchNumber = "B-EXP-SOON", purchaseDate = now - 5 * dayMillis, expiryDate = now + 3 * dayMillis, initialQuantityPieces = 20, currentQuantityPieces = 20, purchaseCostPerPiecePaise = 1000L)
    val bLater = ProductBatch(id = 2L, productId = 1L, batchNumber = "B-GOOD", purchaseDate = now, expiryDate = now + 60 * dayMillis, initialQuantityPieces = 20, currentQuantityPieces = 20, purchaseCostPerPiecePaise = 1100L)

    val csvFile = ExportDocumentService.exportInventoryCsvFile(null, listOf(p1), listOf(bLater, bSoon), dir)
    assertTrue(ExportDocumentService.validateExportedFile(csvFile, ExportFormat.CSV))
    val csv = csvFile.readText()
    assertTrue("B-EXP-SOON should appear before B-GOOD by FEFO expiry order", csv.indexOf("B-EXP-SOON") < csv.indexOf("B-GOOD"))
    assertTrue(csv.contains("EXPIRING_SOON"))

    val xlsxFile = ExportDocumentService.generateInventoryXlsx(null, listOf(p1), listOf(bLater, bSoon), dir)
    assertValidXlsx(
      xlsxFile,
      listOf("FEFO Batches", "Product Stock Summary"),
      listOf("Chocobar Stick", "B-EXP-SOON", "EXPIRING_SOON", "LOW STOCK")
    )

    val pdfFile = ExportDocumentService.generateInventoryPdf(null, listOf(p1), listOf(bLater, bSoon), dir)
    assertValidPdf(pdfFile)
  }

  // ==========================================================
  // Empty-Data & Large-Data & Validation / Error Tests
  // ==========================================================

  @Test
  fun testAllSixExports_emptyDataScenario_validFilesGenerated() {
    val dir = tempFolder.newFolder("empty_exports")
    val emptyPl = ProfitLossSummary(0L, 0L, 0L, 0L, 0L, 0, 0)

    val files = listOf(
      ExportDocumentService.exportProfitLossCsvFile(null, emptyPl, "All Time", dir) to ExportFormat.CSV,
      ExportDocumentService.generateProfitLossXlsx(null, emptyPl, "All Time", dir) to ExportFormat.XLSX,
      ExportDocumentService.generateProfitLossPdf(null, emptyPl, "All Time", dir) to ExportFormat.PDF,

      ExportDocumentService.exportSalesReportCsvFile(null, emptyList(), emptyMap(), emptyList(), emptyList(), "All Time", dir) to ExportFormat.CSV,
      ExportDocumentService.generateSalesReportXlsx(null, emptyList(), emptyMap(), emptyList(), emptyList(), "All Time", dir) to ExportFormat.XLSX,
      ExportDocumentService.generateSalesReportPdf(null, emptyList(), emptyMap(), emptyList(), "All Time", dir) to ExportFormat.PDF,

      ExportDocumentService.exportPurchaseReportCsvFile(null, emptyList(), emptyMap(), emptyList(), "All Time", dir) to ExportFormat.CSV,
      ExportDocumentService.generatePurchaseReportXlsx(null, emptyList(), emptyMap(), emptyList(), "All Time", dir) to ExportFormat.XLSX,
      ExportDocumentService.generatePurchaseReportPdf(null, emptyList(), emptyMap(), "All Time", dir) to ExportFormat.PDF,

      ExportDocumentService.exportCustomerStatementCsvFile(null, null, emptyList(), emptyList(), null, null, "All Time", dir) to ExportFormat.CSV,
      ExportDocumentService.generateCustomerStatementXlsx(null, null, emptyList(), emptyList(), null, null, "All Time", dir) to ExportFormat.XLSX,
      ExportDocumentService.generateCustomerStatementPdf(null, null, emptyList(), emptyList(), null, null, "All Time", dir) to ExportFormat.PDF,

      ExportDocumentService.exportSupplierStatementCsvFile(null, null, emptyList(), emptyList(), null, null, "All Time", dir) to ExportFormat.CSV,
      ExportDocumentService.generateSupplierStatementXlsx(null, null, emptyList(), emptyList(), null, null, "All Time", dir) to ExportFormat.XLSX,
      ExportDocumentService.generateSupplierStatementPdf(null, null, emptyList(), emptyList(), null, null, "All Time", dir) to ExportFormat.PDF,

      ExportDocumentService.exportInventoryCsvFile(null, emptyList(), emptyList(), dir) to ExportFormat.CSV,
      ExportDocumentService.generateInventoryXlsx(null, emptyList(), emptyList(), dir) to ExportFormat.XLSX,
      ExportDocumentService.generateInventoryPdf(null, emptyList(), emptyList(), dir) to ExportFormat.PDF
    )

    assertEquals(18, files.size)
    files.forEach { (file, fmt) ->
      assertTrue("Empty-data export failed validation for ${file.name}", ExportDocumentService.validateExportedFile(file, fmt))
    }
  }

  @Test
  fun testLargeDatasetExports_550Records_csvXlsxPdf() {
    val dir = tempFolder.newFolder("large_exports")
    val now = System.currentTimeMillis()

    val products = (1..20).map { i ->
      Product(id = i.toLong(), name = "IcePack & Flavor #$i", sku = "SKU-$i", category = "Tubs", defaultPurchasePricePaise = 1000L, defaultSellingPricePaise = 1800L, minStockLevelPieces = 10)
    }
    val batches = (1..550).map { i ->
      ProductBatch(
        id = i.toLong(),
        productId = ((i - 1) % 20 + 1).toLong(),
        batchNumber = "B-$i",
        purchaseDate = now - (i * 1000L),
        expiryDate = now + ((i + 5) * dayMillis),
        initialQuantityPieces = 100,
        currentQuantityPieces = 40 + (i % 50),
        purchaseCostPerPiecePaise = 1000L + i
      )
    }
    val customers = (1..30).associate { it.toLong() to "Shop & Mart #$it" }
    val largeSales = (1..550).map { i ->
      SalesInvoice(
        id = i.toLong(),
        invoiceNumber = "INV-${1000 + i}",
        customerId = ((i - 1) % 30 + 1).toLong(),
        invoiceDate = now - (i * 3600_000L),
        dueDate = now + 7 * dayMillis,
        totalAmountPaise = 50000L + (i * 100L),
        paidAmountPaise = if (i % 2 == 0) 50000L + (i * 100L) else 10000L,
        paymentStatus = if (i % 2 == 0) PaymentStatus.PAID else PaymentStatus.PARTIALLY_PAID,
        paymentMethod = PaymentMethod.UPI,
        isConfirmed = true
      )
    }

    val salesCsv = ExportDocumentService.exportSalesReportCsvFile(null, largeSales, customers, emptyList(), emptyList(), "All Time", dir)
    val salesXlsx = ExportDocumentService.generateSalesReportXlsx(null, largeSales, customers, emptyList(), emptyList(), "All Time", dir)
    val salesPdf = ExportDocumentService.generateSalesReportPdf(null, largeSales, customers, emptyList(), "All Time", dir)
    val invXlsx = ExportDocumentService.generateInventoryXlsx(null, products, batches, dir)
    val invPdf = ExportDocumentService.generateInventoryPdf(null, products, batches, dir)

    assertTrue(ExportDocumentService.validateExportedFile(salesCsv, ExportFormat.CSV))
    assertValidXlsx(salesXlsx, listOf("Sales Invoices"), listOf("INV-1550", "Shop &amp; Mart #10"))
    assertValidPdf(salesPdf)
    assertValidXlsx(invXlsx, listOf("FEFO Batches", "Product Stock Summary"), listOf("B-550", "IcePack &amp; Flavor #1"))
    assertValidPdf(invPdf)
  }

  @Test
  fun testFileValidationAndInvalidDestinationErrorHandling() {
    val dir = tempFolder.newFolder("validation_tests")

    // Empty / corrupted file validation failures
    val emptyFile = File(dir, "empty.pdf").apply { createNewFile() }
    assertFalse(ExportDocumentService.validateExportedFile(emptyFile, ExportFormat.PDF))

    val fakePdf = File(dir, "bad.pdf").apply { writeText("NOT A PDF") }
    assertFalse(ExportDocumentService.validateExportedFile(fakePdf, ExportFormat.PDF))

    val fakeXlsx = File(dir, "bad.xlsx").apply { writeText("NOT A ZIP") }
    assertFalse(ExportDocumentService.validateExportedFile(fakeXlsx, ExportFormat.XLSX))

    val badCsv = File(dir, "bad.csv").apply { writeText("no commas here") }
    assertFalse(ExportDocumentService.validateExportedFile(badCsv, ExportFormat.CSV))

    // Passing a regular file as overrideDir should throw IOException instead of failing silently
    val fileUsedAsDir = File(dir, "regular_file.txt").apply { writeText("not a directory") }
    var threw = false
    try {
      ExportDocumentService.exportProfitLossCsvFile(null, ProfitLossSummary(0, 0, 0, 0, 0, 0, 0), "All Time", fileUsedAsDir)
    } catch (_: IOException) {
      threw = true
    }
    assertTrue("Expected IOException when export target directory is invalid", threw)
  }
}
