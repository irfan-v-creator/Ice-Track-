package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.IceTrackDatabase
import com.example.data.local.entity.CreditDebitNoteEntity
import com.example.data.local.entity.FinancialAuditLogEntity
import com.example.data.local.entity.PaymentAllocationEntity
import com.example.data.local.entity.PurchaseReturnEntity
import com.example.data.local.entity.PurchaseReturnItemEntity
import com.example.data.local.entity.RefundRecordEntity
import com.example.data.local.entity.SalesReturnEntity
import com.example.data.local.entity.SalesReturnItemEntity
import com.example.data.local.entity.StockMovementEntity
import com.example.data.local.entity.SubledgerEntryEntity
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.model.AuditOperationType
import com.example.domain.model.CreditDebitNoteType
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.NoteStatus
import com.example.domain.model.PartyType
import com.example.domain.model.PaymentAllocationType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentStatus
import com.example.domain.model.PurchaseReturn
import com.example.domain.model.RefundExecutionStatus
import com.example.domain.model.RefundSourceType
import com.example.domain.model.ReturnCondition
import com.example.domain.model.ReturnSettlementType
import com.example.domain.model.SalesReturn
import com.example.domain.model.StockMovementType
import com.example.domain.model.SubledgerEntryType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReturnRepository(private val db: IceTrackDatabase) {
  private val salesDao = db.salesInvoiceDao()
  private val salesItemDao = db.salesInvoiceItemDao()
  private val salesReturnDao = db.salesReturnDao()
  private val purchaseDao = db.purchaseInvoiceDao()
  private val purchaseItemDao = db.purchaseInvoiceItemDao()
  private val purchaseReturnDao = db.purchaseReturnDao()
  private val batchDao = db.productBatchDao()
  private val movementDao = db.stockMovementDao()
  private val subledgerDao = db.subledgerDao()
  private val allocationDao = db.paymentAllocationDao()
  private val noteDao = db.creditDebitNoteDao()
  private val refundDao = db.refundRecordDao()
  private val auditDao = db.financialAuditLogDao()

  val allSalesReturns: Flow<List<SalesReturn>> = salesReturnDao.observeAll().map { it.map { r -> r.toDomain() } }
  val allSalesReturnItems: Flow<List<com.example.domain.model.SalesReturnItem>> = salesReturnDao.observeAllItems().map { it.map { i -> i.toDomain() } }
  val allPurchaseReturns: Flow<List<PurchaseReturn>> = purchaseReturnDao.observeAll().map { it.map { r -> r.toDomain() } }

  data class RequestedSalesReturnLine(
    val salesInvoiceItemId: Long,
    val quantityPieces: Int,
    val condition: ReturnCondition
  )

  data class RequestedPurchaseReturnLine(
    val purchaseInvoiceItemId: Long,
    val quantityPieces: Int,
    val condition: ReturnCondition = ReturnCondition.RESELLABLE
  )

  suspend fun generateNextSalesReturnNumber(nowMillis: Long = System.currentTimeMillis()): String {
    val dateTag = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(nowMillis))
    var seq = salesReturnDao.countReturns() + 1
    var candidate = "SR-$dateTag-${seq.toString().padStart(4, '0')}"
    while (salesReturnDao.existsByReturnNumber(candidate) > 0) {
      seq++
      candidate = "SR-$dateTag-${seq.toString().padStart(4, '0')}"
    }
    return candidate
  }

  suspend fun generateNextPurchaseReturnNumber(nowMillis: Long = System.currentTimeMillis()): String {
    val dateTag = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(nowMillis))
    var seq = purchaseReturnDao.countReturns() + 1
    var candidate = "PR-$dateTag-${seq.toString().padStart(4, '0')}"
    while (purchaseReturnDao.existsByReturnNumber(candidate) > 0) {
      seq++
      candidate = "PR-$dateTag-${seq.toString().padStart(4, '0')}"
    }
    return candidate
  }

  /**
   * Executes a complete atomic Sales Return:
   * - Validates invoice is ACTIVE, lines exist on invoice, and returned qty <= remaining returnable qty.
   * - RESELLABLE restores saleable batch stock (+ SALE_RETURN movement); DAMAGED/MELTED/EXPIRED records non-saleable WASTAGE_EXPIRED without adding to saleable stock.
   * - Posts Customer AR Credit and settles via CREDIT_NOTE, ADVANCE, or CASH_REFUND (recorded liability).
   */
  suspend fun createSalesReturn(
    salesInvoiceId: Long,
    returnDate: Long = System.currentTimeMillis(),
    requestedLines: List<RequestedSalesReturnLine>,
    settlementType: ReturnSettlementType,
    refundPaymentMethod: PaymentMethod = PaymentMethod.CASH,
    reason: String,
    notes: String = "",
    idempotencyKey: String? = null
  ): Long = db.withTransaction {
    if (!idempotencyKey.isNullOrBlank()) {
      val existing = salesReturnDao.getByIdempotencyKey(idempotencyKey.trim())
      if (existing != null) return@withTransaction existing.id
    }

    val cleanReason = reason.trim()
    require(cleanReason.isNotBlank()) { "Sales return reason is mandatory" }
    require(requestedLines.isNotEmpty()) { "Select at least one item to return" }
    require(settlementType != ReturnSettlementType.DEBIT_NOTE) {
      "Sales return cannot settle via supplier Debit Note"
    }

    val invoice = salesDao.getById(salesInvoiceId)
      ?: throw IllegalArgumentException("Sales invoice #$salesInvoiceId not found")
    require(invoice.invoiceStatus == InvoiceStatus.ACTIVE) {
      "Cannot process a return against cancelled invoice #${invoice.invoiceNumber}"
    }

    val invoiceItems = salesItemDao.getItemsForInvoiceSync(salesInvoiceId)
    val itemById = invoiceItems.associateBy { it.id }

    // Aggregate already returned pieces per (productId, batchId) and per originalSalesInvoiceItemId
    val previousReturned = salesReturnDao.getReturnedItemsForInvoiceSync(salesInvoiceId)
    val returnedByOrigItem = mutableMapOf<Long, Int>()
    val returnedByProdBatch = mutableMapOf<Pair<Long, Long>, Int>()
    for (prev in previousReturned) {
      prev.originalSalesInvoiceItemId?.let { origId ->
        returnedByOrigItem[origId] = (returnedByOrigItem[origId] ?: 0) + prev.quantityPieces
      }
      val key = prev.productId to prev.batchId
      returnedByProdBatch[key] = (returnedByProdBatch[key] ?: 0) + prev.quantityPieces
    }

    // Track in-flight return quantities per product+batch to reject duplicate line inflation
    val inFlightByProdBatch = mutableMapOf<Pair<Long, Long>, Int>()
    val soldByProdBatch = invoiceItems.groupBy { it.productId to it.batchId }
      .mapValues { (_, list) -> list.sumOf { it.quantityPieces } }

    var totalReturnAmountPaise = 0L
    val preparedItems = mutableListOf<SalesReturnItemEntity>()

    for (req in requestedLines) {
      require(req.quantityPieces > 0) { "Return quantity must be positive" }
      val origItem = itemById[req.salesInvoiceItemId]
        ?: throw IllegalArgumentException("Invoice item #${req.salesInvoiceItemId} does not belong to invoice #${invoice.invoiceNumber}")
      val key = origItem.productId to origItem.batchId
      val totalSoldForKey = soldByProdBatch[key] ?: 0
      val alreadyRetForKey = returnedByProdBatch[key] ?: 0
      val plannedForKey = (inFlightByProdBatch[key] ?: 0) + req.quantityPieces
      val remainingReturnable = (totalSoldForKey - alreadyRetForKey).coerceAtLeast(0)

      if (plannedForKey > remainingReturnable) {
        val label = origItem.productNameSnapshot.ifBlank { "Product #${origItem.productId}" }
        throw IllegalStateException(
          "Cannot return ${plannedForKey} pcs of '$label' (remaining returnable on invoice: $remainingReturnable pcs)"
        )
      }
      inFlightByProdBatch[key] = plannedForKey

      // Compute proportional effective net price per piece honoring item discount or pro-rata invoice discount if needed
      val effectiveUnitPrice = if (origItem.quantityPieces > 0) {
        origItem.lineTotalPaise / origItem.quantityPieces
      } else origItem.unitPricePaise
      val lineTotal = effectiveUnitPrice * req.quantityPieces.toLong()
      totalReturnAmountPaise += lineTotal

      preparedItems.add(
        SalesReturnItemEntity(
          salesReturnId = 0L,
          originalSalesInvoiceItemId = origItem.id,
          productId = origItem.productId,
          batchId = origItem.batchId,
          productNameSnapshot = origItem.productNameSnapshot.ifBlank {
            db.productDao().getProductSync(origItem.productId)?.name ?: "Product #${origItem.productId}"
          },
          quantityPieces = req.quantityPieces,
          unitPricePaise = effectiveUnitPrice,
          lineTotalPaise = lineTotal,
          condition = req.condition,
          restoredToSaleableStock = req.condition.isSaleable
        )
      )
    }

    val now = System.currentTimeMillis()
    val returnNumber = generateNextSalesReturnNumber(returnDate)

    // 1. Insert Sales Return Header (creditNoteId/refundRecordId/subledgerEntryId updated right after)
    val returnId = salesReturnDao.insertReturn(
      SalesReturnEntity(
        returnNumber = returnNumber,
        salesInvoiceId = salesInvoiceId,
        customerId = invoice.customerId,
        returnDate = returnDate,
        totalReturnAmountPaise = totalReturnAmountPaise,
        settlementType = settlementType,
        reason = cleanReason,
        notes = notes.trim(),
        idempotencyKey = idempotencyKey?.trim()?.ifBlank { null },
        createdAt = now
      )
    )

    // 2. Insert items + adjust inventory cleanly based on condition
    val itemsWithParent = preparedItems.map { it.copy(salesReturnId = returnId) }
    salesReturnDao.insertItems(itemsWithParent)

    for (item in itemsWithParent) {
      val batch = batchDao.getById(item.batchId)
      val costPerPiece = batch?.purchaseCostPerPiecePaise ?: 0L
      if (item.restoredToSaleableStock) {
        if (batch != null) {
          if (!batch.isActive) {
            batchDao.update(batch.copy(isActive = true, currentQuantityPieces = batch.currentQuantityPieces + item.quantityPieces))
          } else {
            batchDao.addStockIfActive(batch.id, item.quantityPieces)
          }
        }
        movementDao.insert(
          StockMovementEntity(
            productId = item.productId,
            batchId = item.batchId,
            movementType = StockMovementType.SALE_RETURN,
            quantityPieces = item.quantityPieces,
            unitCostPaise = costPerPiece,
            referenceType = "SALES_RETURN",
            referenceId = returnId,
            movementDate = returnDate,
            notes = "Resellable return $returnNumber from #${invoice.invoiceNumber}",
            createdAt = now
          )
        )
      } else {
        // Damaged / Melted / Expired return: NEVER add back to saleable batch inventory; log wastage movement
        movementDao.insert(
          StockMovementEntity(
            productId = item.productId,
            batchId = item.batchId,
            movementType = StockMovementType.WASTAGE_EXPIRED,
            quantityPieces = 0,
            unitCostPaise = costPerPiece,
            referenceType = "SALES_RETURN_NON_SALEABLE",
            referenceId = returnId,
            movementDate = returnDate,
            notes = "${item.condition.displayName} return (${item.quantityPieces} pcs non-saleable) via $returnNumber",
            createdAt = now
          )
        )
      }
    }

    // 3. Financial settlement (Customer AR Credit)
    var creditNoteId: Long? = null
    var refundRecordId: Long? = null
    val subledgerEntryType = when (settlementType) {
      ReturnSettlementType.CREDIT_NOTE -> SubledgerEntryType.CREDIT_NOTE
      ReturnSettlementType.ADVANCE, ReturnSettlementType.CASH_REFUND -> SubledgerEntryType.ADJUSTMENT
      ReturnSettlementType.DEBIT_NOTE -> SubledgerEntryType.CREDIT_NOTE
    }

    val subledgerId = subledgerDao.insert(
      SubledgerEntryEntity(
        partyType = PartyType.CUSTOMER,
        partyId = invoice.customerId,
        entryType = subledgerEntryType,
        referenceType = "SALES_RETURN",
        referenceId = returnId,
        debitPaise = 0L,
        creditPaise = totalReturnAmountPaise,
        entryDate = returnDate,
        notes = "Sales Return $returnNumber against #${invoice.invoiceNumber} ($settlementType): $cleanReason",
        operationRef = returnNumber,
        createdAt = now
      )
    )

    when (settlementType) {
      ReturnSettlementType.CREDIT_NOTE -> {
        val cnRepo = CreditDebitNoteRepository(db)
        val cnNum = cnRepo.generateNextNoteNumber(CreditDebitNoteType.CREDIT_NOTE, returnDate)
        val currentInvoiceDue = (invoice.totalAmountPaise - invoice.paidAmountPaise).coerceAtLeast(0L)
        val autoApplyToInvoice = kotlin.math.min(totalReturnAmountPaise, currentInvoiceDue)
        if (autoApplyToInvoice > 0L) {
          allocationDao.insert(
            PaymentAllocationEntity(
              paymentId = subledgerId,
              partyType = PartyType.CUSTOMER,
              partyId = invoice.customerId,
              allocationType = PaymentAllocationType.INVOICE,
              invoiceId = invoice.id,
              allocatedAmountPaise = autoApplyToInvoice,
              allocatedAt = returnDate,
              notes = "Credit Note $cnNum from return $returnNumber"
            )
          )
          val newPaid = invoice.paidAmountPaise + autoApplyToInvoice
          val newStatus = if (newPaid >= invoice.totalAmountPaise) PaymentStatus.PAID else PaymentStatus.PARTIALLY_PAID
          salesDao.update(invoice.copy(paidAmountPaise = newPaid, paymentStatus = newStatus, updatedAt = now))
        }
        val cnStatus = when {
          autoApplyToInvoice >= totalReturnAmountPaise -> NoteStatus.SETTLED
          autoApplyToInvoice > 0L -> NoteStatus.APPLIED
          else -> NoteStatus.OPEN
        }
        creditNoteId = noteDao.insert(
          CreditDebitNoteEntity(
            noteNumber = cnNum,
            noteType = CreditDebitNoteType.CREDIT_NOTE,
            partyType = PartyType.CUSTOMER,
            partyId = invoice.customerId,
            linkedInvoiceId = invoice.id,
            linkedReturnId = returnId,
            subledgerEntryId = subledgerId,
            amountPaise = totalReturnAmountPaise,
            appliedAmountPaise = autoApplyToInvoice,
            status = cnStatus,
            issueDate = returnDate,
            reason = cleanReason,
            notes = "Auto-generated from Sales Return $returnNumber",
            createdAt = now,
            updatedAt = now
          )
        )
      }
      ReturnSettlementType.ADVANCE -> {
        allocationDao.insert(
          PaymentAllocationEntity(
            paymentId = subledgerId,
            partyType = PartyType.CUSTOMER,
            partyId = invoice.customerId,
            allocationType = PaymentAllocationType.ADVANCE,
            invoiceId = null,
            allocatedAmountPaise = totalReturnAmountPaise,
            allocatedAt = returnDate,
            notes = "Sales Return $returnNumber advance credit",
            isFromAdvanceConsumption = false
          )
        )
      }
      ReturnSettlementType.CASH_REFUND -> {
        refundRecordId = refundDao.insert(
          RefundRecordEntity(
            partyType = PartyType.CUSTOMER,
            partyId = invoice.customerId,
            sourceType = RefundSourceType.SALES_RETURN,
            sourceId = returnId,
            amountPaise = totalReturnAmountPaise,
            paymentMethod = refundPaymentMethod,
            executionStatus = RefundExecutionStatus.RECORDED_PENDING_PAYOUT,
            reason = cleanReason,
            notes = "Recorded refund from Sales Return $returnNumber",
            referenceNumber = returnNumber,
            subledgerEntryId = subledgerId,
            recordedAt = now
          )
        )
      }
      ReturnSettlementType.DEBIT_NOTE -> Unit
    }

    val savedHeader = salesReturnDao.getByIdSync(returnId)!!
    salesReturnDao.updateReturn(
      savedHeader.copy(
        creditNoteId = creditNoteId,
        refundRecordId = refundRecordId,
        subledgerEntryId = subledgerId
      )
    )

    auditDao.insert(
      FinancialAuditLogEntity(
        operationType = AuditOperationType.SALES_RETURN,
        entityType = "SALES_RETURN",
        entityId = returnId,
        relatedPartyType = PartyType.CUSTOMER,
        relatedPartyId = invoice.customerId,
        reason = cleanReason,
        beforeJson = JSONObject().put("invoiceNumber", invoice.invoiceNumber).toString(),
        afterJson = JSONObject()
          .put("returnNumber", returnNumber)
          .put("totalReturnAmountPaise", totalReturnAmountPaise)
          .put("settlementType", settlementType.name)
          .put("resellableItems", itemsWithParent.count { it.restoredToSaleableStock })
          .put("nonSaleableItems", itemsWithParent.count { !it.restoredToSaleableStock })
          .toString(),
        operationReference = returnNumber,
        createdAt = now
      )
    )

    returnId
  }

  /**
   * Executes a complete atomic Purchase Return to supplier:
   * - Validates active Purchase Invoice, supplier, returnable qty, AND live batch currentQuantityPieces >= return qty.
   * - Prevents negative batch stock, records PURCHASE_RETURN movement, posts Supplier AP Debit,
   *   and creates DEBIT_NOTE / ADVANCE / CASH_REFUND record.
   */
  suspend fun createPurchaseReturn(
    purchaseInvoiceId: Long,
    returnDate: Long = System.currentTimeMillis(),
    requestedLines: List<RequestedPurchaseReturnLine>,
    settlementType: ReturnSettlementType,
    refundPaymentMethod: PaymentMethod = PaymentMethod.CASH,
    reason: String,
    notes: String = "",
    idempotencyKey: String? = null
  ): Long = db.withTransaction {
    if (!idempotencyKey.isNullOrBlank()) {
      val existing = purchaseReturnDao.getByIdempotencyKey(idempotencyKey.trim())
      if (existing != null) return@withTransaction existing.id
    }

    val cleanReason = reason.trim()
    require(cleanReason.isNotBlank()) { "Purchase return reason is mandatory" }
    require(requestedLines.isNotEmpty()) { "Select at least one item to return to supplier" }
    require(settlementType != ReturnSettlementType.CREDIT_NOTE) {
      "Purchase return cannot settle via customer Credit Note"
    }

    val invoice = purchaseDao.getById(purchaseInvoiceId)
      ?: throw IllegalArgumentException("Purchase invoice #$purchaseInvoiceId not found")
    require(invoice.invoiceStatus == InvoiceStatus.ACTIVE) {
      "Cannot process a return against cancelled purchase invoice #${invoice.invoiceNumber}"
    }

    val invoiceItems = purchaseItemDao.getItemsForInvoiceSync(purchaseInvoiceId)
    val itemById = invoiceItems.associateBy { it.id }
    val invoiceBatches = batchDao.getBatchesByPurchaseInvoiceSync(purchaseInvoiceId)
    val previousReturned = purchaseReturnDao.getReturnedItemsForInvoiceSync(purchaseInvoiceId)

    val returnedByKey = mutableMapOf<Pair<Long, String>, Int>()
    for (prev in previousReturned) {
      val key = prev.productId to prev.batchNumberSnapshot
      returnedByKey[key] = (returnedByKey[key] ?: 0) + prev.quantityPieces
    }

    val inFlightByKey = mutableMapOf<Pair<Long, String>, Int>()
    val preparedItems = mutableListOf<PurchaseReturnItemEntity>()
    var totalReturnAmountPaise = 0L

    for (req in requestedLines) {
      require(req.quantityPieces > 0) { "Return quantity must be positive" }
      val origItem = itemById[req.purchaseInvoiceItemId]
        ?: throw IllegalArgumentException("Purchase item #${req.purchaseInvoiceItemId} does not belong to invoice #${invoice.invoiceNumber}")
      val batch = invoiceBatches.firstOrNull {
        it.productId == origItem.productId && it.batchNumber == origItem.batchNumber
      } ?: throw IllegalStateException("Batch '${origItem.batchNumber}' not found for purchase item #${origItem.id}")

      val key = origItem.productId to origItem.batchNumber
      val alreadyRet = returnedByKey[key] ?: 0
      val totalInFlight = (inFlightByKey[key] ?: 0) + req.quantityPieces
      val remainingPurchasedReturnable = (origItem.quantityPieces - alreadyRet).coerceAtLeast(0)
      if (totalInFlight > remainingPurchasedReturnable) {
        throw IllegalStateException(
          "Cannot return $totalInFlight pcs of batch '${origItem.batchNumber}' (remaining returnable on invoice: $remainingPurchasedReturnable pcs)"
        )
      }
      if (totalInFlight > batch.currentQuantityPieces) {
        throw IllegalStateException(
          "Insufficient available stock in batch '${origItem.batchNumber}' (current stock: ${batch.currentQuantityPieces} pcs, requested return: $totalInFlight pcs)"
        )
      }
      inFlightByKey[key] = totalInFlight

      val effectiveCost = if (origItem.quantityPieces > 0) {
        origItem.lineTotalPaise / origItem.quantityPieces
      } else origItem.costPerPiecePaise
      val lineTotal = effectiveCost * req.quantityPieces.toLong()
      totalReturnAmountPaise += lineTotal

      preparedItems.add(
        PurchaseReturnItemEntity(
          purchaseReturnId = 0L,
          originalPurchaseInvoiceItemId = origItem.id,
          productId = origItem.productId,
          batchId = batch.id,
          productNameSnapshot = origItem.productNameSnapshot.ifBlank {
            db.productDao().getProductSync(origItem.productId)?.name ?: "Product #${origItem.productId}"
          },
          batchNumberSnapshot = origItem.batchNumber,
          quantityPieces = req.quantityPieces,
          costPerPiecePaise = effectiveCost,
          lineTotalPaise = lineTotal,
          condition = req.condition
        )
      )
    }

    val now = System.currentTimeMillis()
    val returnNumber = generateNextPurchaseReturnNumber(returnDate)

    val returnId = purchaseReturnDao.insertReturn(
      PurchaseReturnEntity(
        returnNumber = returnNumber,
        purchaseInvoiceId = purchaseInvoiceId,
        supplierId = invoice.supplierId,
        returnDate = returnDate,
        totalReturnAmountPaise = totalReturnAmountPaise,
        settlementType = settlementType,
        reason = cleanReason,
        notes = notes.trim(),
        idempotencyKey = idempotencyKey?.trim()?.ifBlank { null },
        createdAt = now
      )
    )

    val itemsWithParent = preparedItems.map { it.copy(purchaseReturnId = returnId) }
    purchaseReturnDao.insertItems(itemsWithParent)

    for (item in itemsWithParent) {
      val updated = batchDao.deductStockIfAvailable(item.batchId, item.quantityPieces)
      if (updated != 1) {
        throw IllegalStateException("Batch '${item.batchNumberSnapshot}' stock changed concurrently; transaction rolled back.")
      }
      movementDao.insert(
        StockMovementEntity(
          productId = item.productId,
          batchId = item.batchId,
          movementType = StockMovementType.PURCHASE_RETURN,
          quantityPieces = -item.quantityPieces,
          unitCostPaise = item.costPerPiecePaise,
          referenceType = "PURCHASE_RETURN",
          referenceId = returnId,
          movementDate = returnDate,
          notes = "Returned ${item.quantityPieces} pcs to supplier via $returnNumber (#${invoice.invoiceNumber})",
          createdAt = now
        )
      )
    }

    val entryType = if (settlementType == ReturnSettlementType.DEBIT_NOTE) {
      SubledgerEntryType.DEBIT_NOTE
    } else {
      SubledgerEntryType.ADJUSTMENT
    }

    val subledgerId = subledgerDao.insert(
      SubledgerEntryEntity(
        partyType = PartyType.SUPPLIER,
        partyId = invoice.supplierId,
        entryType = entryType,
        referenceType = "PURCHASE_RETURN",
        referenceId = returnId,
        debitPaise = totalReturnAmountPaise,
        creditPaise = 0L,
        entryDate = returnDate,
        notes = "Purchase Return $returnNumber against #${invoice.invoiceNumber} ($settlementType): $cleanReason",
        operationRef = returnNumber,
        createdAt = now
      )
    )

    var debitNoteId: Long? = null
    var refundRecordId: Long? = null

    when (settlementType) {
      ReturnSettlementType.DEBIT_NOTE -> {
        val cnRepo = CreditDebitNoteRepository(db)
        val dnNum = cnRepo.generateNextNoteNumber(CreditDebitNoteType.DEBIT_NOTE, returnDate)
        val currentDue = (invoice.totalAmountPaise - invoice.paidAmountPaise).coerceAtLeast(0L)
        val autoApply = kotlin.math.min(totalReturnAmountPaise, currentDue)
        if (autoApply > 0L) {
          allocationDao.insert(
            PaymentAllocationEntity(
              paymentId = subledgerId,
              partyType = PartyType.SUPPLIER,
              partyId = invoice.supplierId,
              allocationType = PaymentAllocationType.INVOICE,
              invoiceId = invoice.id,
              allocatedAmountPaise = autoApply,
              allocatedAt = returnDate,
              notes = "Debit Note $dnNum from return $returnNumber"
            )
          )
          val newPaid = invoice.paidAmountPaise + autoApply
          val newStatus = if (newPaid >= invoice.totalAmountPaise) PaymentStatus.PAID else PaymentStatus.PARTIALLY_PAID
          purchaseDao.update(invoice.copy(paidAmountPaise = newPaid, paymentStatus = newStatus, updatedAt = now))
        }
        val dnStatus = when {
          autoApply >= totalReturnAmountPaise -> NoteStatus.SETTLED
          autoApply > 0L -> NoteStatus.APPLIED
          else -> NoteStatus.OPEN
        }
        debitNoteId = noteDao.insert(
          CreditDebitNoteEntity(
            noteNumber = dnNum,
            noteType = CreditDebitNoteType.DEBIT_NOTE,
            partyType = PartyType.SUPPLIER,
            partyId = invoice.supplierId,
            linkedInvoiceId = invoice.id,
            linkedReturnId = returnId,
            subledgerEntryId = subledgerId,
            amountPaise = totalReturnAmountPaise,
            appliedAmountPaise = autoApply,
            status = dnStatus,
            issueDate = returnDate,
            reason = cleanReason,
            notes = "Auto-generated from Purchase Return $returnNumber",
            createdAt = now,
            updatedAt = now
          )
        )
      }
      ReturnSettlementType.ADVANCE -> {
        allocationDao.insert(
          PaymentAllocationEntity(
            paymentId = subledgerId,
            partyType = PartyType.SUPPLIER,
            partyId = invoice.supplierId,
            allocationType = PaymentAllocationType.ADVANCE,
            invoiceId = null,
            allocatedAmountPaise = totalReturnAmountPaise,
            allocatedAt = returnDate,
            notes = "Purchase Return $returnNumber advance credit",
            isFromAdvanceConsumption = false
          )
        )
      }
      ReturnSettlementType.CASH_REFUND -> {
        refundRecordId = refundDao.insert(
          RefundRecordEntity(
            partyType = PartyType.SUPPLIER,
            partyId = invoice.supplierId,
            sourceType = RefundSourceType.PURCHASE_RETURN,
            sourceId = returnId,
            amountPaise = totalReturnAmountPaise,
            paymentMethod = refundPaymentMethod,
            executionStatus = RefundExecutionStatus.RECORDED_PENDING_PAYOUT,
            reason = cleanReason,
            notes = "Recorded supplier refund due from Purchase Return $returnNumber",
            referenceNumber = returnNumber,
            subledgerEntryId = subledgerId,
            recordedAt = now
          )
        )
      }
      ReturnSettlementType.CREDIT_NOTE -> Unit
    }

    val savedHeader = purchaseReturnDao.getByIdSync(returnId)!!
    purchaseReturnDao.updateReturn(
      savedHeader.copy(
        debitNoteId = debitNoteId,
        refundRecordId = refundRecordId,
        subledgerEntryId = subledgerId
      )
    )

    auditDao.insert(
      FinancialAuditLogEntity(
        operationType = AuditOperationType.PURCHASE_RETURN,
        entityType = "PURCHASE_RETURN",
        entityId = returnId,
        relatedPartyType = PartyType.SUPPLIER,
        relatedPartyId = invoice.supplierId,
        reason = cleanReason,
        beforeJson = JSONObject().put("invoiceNumber", invoice.invoiceNumber).toString(),
        afterJson = JSONObject()
          .put("returnNumber", returnNumber)
          .put("totalReturnAmountPaise", totalReturnAmountPaise)
          .put("settlementType", settlementType.name)
          .toString(),
        operationReference = returnNumber,
        createdAt = now
      )
    )

    returnId
  }
}
