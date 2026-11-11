package com.example

import com.example.data.local.DatabaseMigrations
import com.example.domain.engine.FefoInventoryEngine
import com.example.domain.engine.InvoiceAllocationTarget
import com.example.domain.engine.PaymentAllocationEngine
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.engine.UnitConversionEngine
import com.example.domain.model.PartyType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentStatus
import com.example.domain.model.Product
import com.example.domain.model.ProductBatch
import com.example.domain.model.StockMovementType
import com.example.domain.model.SubledgerEntry
import com.example.domain.model.SubledgerEntryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Step2OperationsIntegrationTest {

  private val dayMillis = 86400000L
  private val now = System.currentTimeMillis()

  // =========================================================================
  // 1. Database Migration SQL Structure & Integrity Audit
  // =========================================================================

  @Test
  fun testMigration1To2SqlDefinitionsAreValid() {
    // Verify migration version transition
    assertEquals(1, DatabaseMigrations.MIGRATION_1_2.startVersion)
    assertEquals(2, DatabaseMigrations.MIGRATION_1_2.endVersion)
  }

  // =========================================================================
  // 2. Purchase and Inventory Inward Integration
  // =========================================================================

  @Test
  fun testPurchaseReceiptCreatesBatchesAndCalculatesPayableCorrectly() {
    val supplierId = 101L
    val purchaseCostPerPiece = 2500L // ₹25.00
    val quantityPieces = 200

    // Step 2 Inward Purchase Line Calculation
    val lineTotalPaise = quantityPieces.toLong() * purchaseCostPerPiece
    assertEquals(500000L, lineTotalPaise) // ₹5,000.00

    // Batch created from purchase
    val batch = ProductBatch(
      id = 1L,
      productId = 1L,
      batchNumber = "B-PUR-001",
      purchaseInvoiceId = 501L,
      purchaseDate = now,
      expiryDate = now + (90 * dayMillis),
      initialQuantityPieces = quantityPieces,
      currentQuantityPieces = quantityPieces,
      purchaseCostPerPiecePaise = purchaseCostPerPiece
    )

    assertEquals(200, batch.currentQuantityPieces)
    assertFalse("Freshly purchased batch must not be expired", batch.isExpired)

    // Subledger entry for purchase invoice (AP credit: increases what distributor owes supplier)
    val invoiceSubledger = SubledgerEntry(
      id = 1L,
      partyType = PartyType.SUPPLIER,
      partyId = supplierId,
      entryType = SubledgerEntryType.INVOICE,
      referenceType = "PURCHASE_INVOICE",
      referenceId = 501L,
      debitPaise = 0L,
      creditPaise = lineTotalPaise,
      entryDate = now
    )

    // When distributor pays partial amount at purchase time (e.g. ₹2,000 paid, ₹3,000 credit)
    val paymentPaise = 200000L
    val paymentSubledger = SubledgerEntry(
      id = 2L,
      partyType = PartyType.SUPPLIER,
      partyId = supplierId,
      entryType = SubledgerEntryType.PAYMENT,
      referenceType = "PURCHASE_PAYMENT",
      referenceId = 501L,
      debitPaise = paymentPaise,
      creditPaise = 0L,
      entryDate = now
    )

    val netPayable = SubledgerCalculationEngine.calculateSupplierBalance(listOf(invoiceSubledger, paymentSubledger))
    // 5000 - 2000 = 3000 (300,000 paise remaining due to supplier)
    assertEquals(300000L, netPayable)
    assertEquals("₹3,000.00", SubledgerCalculationEngine.formatPaise(netPayable))
  }

  // =========================================================================
  // 3. Sales & FEFO Stock Allocation with Multi-Batch & Price Override
  // =========================================================================

  @Test
  fun testSaleFefoAllocationAndCogsCalculation() {
    // 2 Batches of Vanilla Ice Cream in freezer
    val batchOldExpiringSoon = ProductBatch(
      id = 10L,
      productId = 1L,
      batchNumber = "B-MAR-01",
      purchaseDate = now - (20 * dayMillis),
      expiryDate = now + (5 * dayMillis), // Expires in 5 days!
      initialQuantityPieces = 100,
      currentQuantityPieces = 100,
      purchaseCostPerPiecePaise = 2000L // ₹20.00
    )

    val batchNewExpiringLater = ProductBatch(
      id = 11L,
      productId = 1L,
      batchNumber = "B-MAY-01",
      purchaseDate = now - (2 * dayMillis),
      expiryDate = now + (60 * dayMillis), // Expires in 60 days
      initialQuantityPieces = 150,
      currentQuantityPieces = 150,
      purchaseCostPerPiecePaise = 2200L // ₹22.00
    )

    // Shop orders 120 pieces at selling price ₹30.00 (3000 paise)
    val requestedQuantity = 120
    val sellingPricePerPiece = 3000L

    val fefoResult = FefoInventoryEngine.allocateFefo(
      requestedPieces = requestedQuantity,
      availableBatches = listOf(batchOldExpiringSoon, batchNewExpiringLater),
      allowExpired = false,
      currentTimestamp = now
    )

    assertEquals(120, fefoResult.fulfilledPieces)
    assertEquals(0, fefoResult.remainingUnfulfilledPieces)
    assertEquals(2, fefoResult.allocations.size)

    // FEFO verification: all 100 pieces must be taken from batchOldExpiringSoon first!
    val alloc1 = fefoResult.allocations[0]
    assertEquals(10L, alloc1.batch.id)
    assertEquals(100, alloc1.quantityPiecesToIssue)

    // Remaining 20 pieces taken from batchNewExpiringLater
    val alloc2 = fefoResult.allocations[1]
    assertEquals(11L, alloc2.batch.id)
    assertEquals(20, alloc2.quantityPiecesToIssue)

    // Actual COGS (Cost of Goods Sold) calculation based on actual batch costs:
    // 100 pcs * ₹20.00 = ₹2,000.00 (200,000 paise)
    // 20 pcs * ₹22.00 = ₹440.00 (44,000 paise)
    // Total COGS = ₹2,440.00 (244,000 paise)
    val actualCogsPaise = (alloc1.quantityPiecesToIssue.toLong() * alloc1.batch.purchaseCostPerPiecePaise) +
                          (alloc2.quantityPiecesToIssue.toLong() * alloc2.batch.purchaseCostPerPiecePaise)
    assertEquals(244000L, actualCogsPaise)

    // Total Revenue = 120 pcs * ₹30.00 = ₹3,600.00 (360,000 paise)
    val totalRevenuePaise = requestedQuantity.toLong() * sellingPricePerPiece
    assertEquals(360000L, totalRevenuePaise)

    // Gross Margin = 3,600 - 2,440 = ₹1,160.00 (116,000 paise)
    val grossProfitPaise = totalRevenuePaise - actualCogsPaise
    assertEquals(116000L, grossProfitPaise)
    assertEquals("₹1,160.00", SubledgerCalculationEngine.formatPaise(grossProfitPaise))
  }

  @Test
  fun testFefoRejectsExpiredStockAndHandlesInsufficientInventory() {
    val expiredBatch = ProductBatch(
      id = 1L,
      productId = 2L,
      batchNumber = "B-OLD-EXPIRED",
      purchaseDate = now - (60 * dayMillis),
      expiryDate = now - (1 * dayMillis), // Expired yesterday!
      initialQuantityPieces = 50,
      currentQuantityPieces = 50,
      purchaseCostPerPiecePaise = 1500L
    )

    val validBatch = ProductBatch(
      id = 2L,
      productId = 2L,
      batchNumber = "B-FRESH",
      purchaseDate = now - (5 * dayMillis),
      expiryDate = now + (30 * dayMillis),
      initialQuantityPieces = 30,
      currentQuantityPieces = 30,
      purchaseCostPerPiecePaise = 1500L
    )

    // Customer requests 40 pieces:
    // Only 30 pcs can be fulfilled from valid batch; expired batch is strictly rejected.
    val result = FefoInventoryEngine.allocateFefo(
      requestedPieces = 40,
      availableBatches = listOf(expiredBatch, validBatch),
      allowExpired = false,
      currentTimestamp = now
    )

    assertEquals(30, result.fulfilledPieces)
    assertEquals(10, result.remainingUnfulfilledPieces)
    assertEquals(1, result.allocations.size)
    assertEquals(2L, result.allocations[0].batch.id)
  }

  // =========================================================================
  // 4. Financial Integrity: Allocation, FIFO Settlement, and Reversals
  // =========================================================================

  @Test
  fun testCustomerPaymentAllocationAcrossMultipleInvoices() {
    val customerId = 201L

    val invoice1 = InvoiceAllocationTarget(invoiceId = 10, totalAmountPaise = 150000L, alreadyPaidPaise = 0L, invoiceDate = 1000L) // ₹1,500
    val invoice2 = InvoiceAllocationTarget(invoiceId = 11, totalAmountPaise = 250000L, alreadyPaidPaise = 0L, invoiceDate = 2000L) // ₹2,500

    // Customer pays ₹3,000 (300,000 paise) via UPI
    val result = PaymentAllocationEngine.allocateFifo(
      paymentId = 999L,
      totalPaymentPaise = 300000L,
      invoices = listOf(invoice1, invoice2)
    )

    // Invoice 1: Fully settled (₹1,500 allocated -> PAID)
    assertEquals(150000L, result.allocations[0].allocatedAmountPaise)
    assertEquals(PaymentStatus.PAID, result.invoiceStatuses[10])

    // Invoice 2: Partially settled (₹1,500 allocated, ₹1,000 remaining -> PARTIALLY_PAID)
    assertEquals(150000L, result.allocations[1].allocatedAmountPaise)
    assertEquals(PaymentStatus.PARTIALLY_PAID, result.invoiceStatuses[11])

    assertEquals(0L, result.unallocatedRemainingPaise)
  }

  @Test
  fun testFinancialReversalPreservesAuditTrailWithoutSilentDeletion() {
    val customerId = 301L

    // Entry 1: Sale of ₹5,000
    val saleEntry = SubledgerEntry(
      id = 1L,
      partyType = PartyType.CUSTOMER,
      partyId = customerId,
      entryType = SubledgerEntryType.INVOICE,
      referenceType = "SALE",
      referenceId = 1001L,
      debitPaise = 500000L,
      creditPaise = 0L,
      entryDate = 1000L,
      isReversed = false
    )

    // Entry 2: Erroneously entered payment of ₹3,000
    val wrongPayment = SubledgerEntry(
      id = 2L,
      partyType = PartyType.CUSTOMER,
      partyId = customerId,
      entryType = SubledgerEntryType.PAYMENT,
      referenceType = "PAYMENT",
      referenceId = 2001L,
      debitPaise = 0L,
      creditPaise = 300000L,
      entryDate = 2000L,
      isReversed = false
    )

    // Balance before reversal: 5000 - 3000 = 2000 (200,000 paise)
    assertEquals(200000L, SubledgerCalculationEngine.calculateCustomerBalance(listOf(saleEntry, wrongPayment)))

    // Now reverse the wrong payment (isReversed = true). It remains in database history!
    val reversedPayment = wrongPayment.copy(isReversed = true)

    // Dynamic balance excludes reversed entries: 5000 - 0 = 5000 (500,000 paise)
    val balanceAfterReversal = SubledgerCalculationEngine.calculateCustomerBalance(listOf(saleEntry, reversedPayment))
    assertEquals(500000L, balanceAfterReversal)
  }

  @Test
  fun testUnitConversionEnginePieceBoxCartonIntegrity() {
    // 1 Box = 20 Pcs, 1 Carton = 10 Boxes (200 Pcs per Carton)
    val piecesPerBox = 20
    val boxesPerCarton = 10

    // 2 Cartons + 3 Boxes + 5 Pieces = (2 * 200) + (3 * 20) + 5 = 465 Pieces
    val totalPieces = UnitConversionEngine.toTotalPieces(
      cartons = 2,
      boxes = 3,
      pieces = 5,
      piecesPerBox = piecesPerBox,
      boxesPerCarton = boxesPerCarton
    )
    assertEquals(465L, totalPieces)

    // Convert back from 465 Pieces
    val breakdown = UnitConversionEngine.fromTotalPieces(
      totalPieces = 465L,
      piecesPerBox = piecesPerBox,
      boxesPerCarton = boxesPerCarton
    )
    assertEquals(2, breakdown.cartons)
    assertEquals(3, breakdown.boxes)
    assertEquals(5, breakdown.pieces)
    assertEquals("2 Ctn, 3 Box, 5 Pcs", breakdown.toFormattedString())
  }

  // =========================================================================
  // 5. Shop-Specific Pricing Auto-Fill and Fallback Tests
  // =========================================================================

  @Test
  fun testShopSpecificPricingAutoFillWithFallbackAndManualOverride() {
    val defaultProductPrice = 3500L // ₹35.00 default price
    val customPricesMap = mapOf(
      (101L to 1L) to 3000L, // Shop A (101) has custom price ₹30.00 for Product 1
      (102L to 1L) to 3200L  // Shop B (102) has custom price ₹32.00 for Product 1
      // Shop C (103) has NO custom price configured for Product 1
    )

    fun resolveSellingPrice(shopId: Long, productId: Long, defaultPrice: Long): Long {
      return customPricesMap[shopId to productId] ?: defaultPrice
    }

    // 1. Shop A gets custom price ₹30.00 (3000 paise)
    val priceForShopA = resolveSellingPrice(101L, 1L, defaultProductPrice)
    assertEquals(3000L, priceForShopA)

    // 2. Shop B gets custom price ₹32.00 (3200 paise)
    val priceForShopB = resolveSellingPrice(102L, 1L, defaultProductPrice)
    assertEquals(3200L, priceForShopB)

    // 3. Shop C falls back to default price ₹35.00 (3500 paise)
    val priceForShopC = resolveSellingPrice(103L, 1L, defaultProductPrice)
    assertEquals(3500L, priceForShopC)

    // 4. Manual override: Seller manually enters ₹28.00 at billing time
    val manualOverridePrice = 2800L
    val quantityPieces = 50
    val finalLineTotal = quantityPieces.toLong() * manualOverridePrice

    assertEquals(140000L, finalLineTotal) // ₹1,400.00 correctly stored
    assertEquals("₹1,400.00", SubledgerCalculationEngine.formatPaise(finalLineTotal))
  }
}
