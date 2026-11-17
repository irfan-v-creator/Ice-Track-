package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.IceTrackDatabase
import com.example.data.local.entity.FinancialAuditLogEntity
import com.example.data.local.entity.PaymentAllocationEntity
import com.example.data.local.entity.ProductBatchEntity
import com.example.data.local.entity.PurchaseInvoiceItemEntity
import com.example.data.local.entity.RefundRecordEntity
import com.example.data.local.entity.SalesInvoiceItemEntity
import com.example.data.local.entity.StockMovementEntity
import com.example.data.local.entity.SubledgerEntryEntity
import com.example.domain.engine.FefoInventoryEngine
import com.example.domain.engine.PaymentAllocationEngine
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.model.AuditOperationType
import com.example.domain.model.DiscountType
import com.example.domain.model.InvoiceCancellationPaymentMode
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.PartyType
import com.example.domain.model.PaymentAllocationType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentStatus
import com.example.domain.model.PurchaseInvoiceDetail
import com.example.domain.model.RefundExecutionStatus
import com.example.domain.model.RefundSourceType
import com.example.domain.model.SalesInvoiceDetail
import com.example.domain.model.StockMovementType
import com.example.domain.model.SubledgerEntryType
import com.example.domain.model.UnitType
import org.json.JSONObject

class InvoiceLifecycleRepository(private val db: IceTrackDatabase) {
  private val salesDao = db.salesInvoiceDao()
  private val salesItemDao = db.salesInvoiceItemDao()
  private val purchaseDao = db.purchaseInvoiceDao()
  private val purchaseItemDao = db.purchaseInvoiceItemDao()
  private val batchDao = db.productBatchDao()
  private val movementDao = db.stockMovementDao()
  private val subledgerDao = db.subledgerDao()
  private val allocationDao = db.paymentAllocationDao()
  private val customerDao = db.customerDao()
  private val supplierDao = db.supplierDao()
  private val productDao = db.productDao()
  private val salesReturnDao = db.salesReturnDao()
  private val purchaseReturnDao = db.purchaseReturnDao()
  private val noteDao = db.creditDebitNoteDao()
  private val refundDao = db.refundRecordDao()
  private val auditDao = db.financialAuditLogDao()

  data class EditSalesLineRequest(
    val productId: Long,
    val productNameSnapshot: String = "",
    val quantityPieces: Int,
    val unitPricePaise: Long,
    val unitType: UnitType = UnitType.PIECE,
    val unitQuantity: Int = quantityPieces,
    val unitRatePaise: Long = unitPricePaise,
    val itemDiscountPaise: Long = 0L
  )

  data class EditSalesInvoiceRequest(
    val invoiceId: Long,
    val expectedRevisionVersion: Int,
    val customerId: Long,
    val partyNameSnapshot: String,
    val partyPhoneSnapshot: String,
    val partyAddressSnapshot: String,
    val invoiceDate: Long,
    val dueDate: Long,
    val discountType: DiscountType,
    val discountValueRaw: Long,
    val additionalChargesPaise: Long,
    val paymentMethod: PaymentMethod?,
    val notes: String,
    val lines: List<EditSalesLineRequest>,
    val allowExpired: Boolean = false,
    val allowCreditLimitOverride: Boolean = false,
    val requestedAdvanceRetentionPaise: Long? = null,
    val refundPaymentMethod: PaymentMethod = PaymentMethod.CASH,
    val reason: String
  )

  data class EditPurchaseLineRequest(
    val productId: Long,
    val productNameSnapshot: String = "",
    val batchNumber: String,
    val expiryDate: Long,
    val quantityPieces: Int,
    val costPerPiecePaise: Long,
    val unitType: UnitType = UnitType.PIECE,
    val unitQuantity: Int = quantityPieces,
    val unitRatePaise: Long = costPerPiecePaise,
    val itemDiscountPaise: Long = 0L
  )

  data class EditPurchaseInvoiceRequest(
    val invoiceId: Long,
    val expectedRevisionVersion: Int,
    val supplierId: Long,
    val partyNameSnapshot: String,
    val partyPhoneSnapshot: String,
    val partyAddressSnapshot: String,
    val invoiceDate: Long,
    val dueDate: Long,
    val discountType: DiscountType,
    val discountValueRaw: Long,
    val additionalChargesPaise: Long,
    val paymentMethod: PaymentMethod?,
    val notes: String,
    val lines: List<EditPurchaseLineRequest>,
    val allowCreditLimitOverride: Boolean = false,
    val requestedAdvanceRetentionPaise: Long? = null,
    val refundPaymentMethod: PaymentMethod = PaymentMethod.CASH,
    val reason: String
  )

  suspend fun getSalesInvoiceDetail(invoiceId: Long): SalesInvoiceDetail? {
    val entity = salesDao.getById(invoiceId) ?: return null
    val customer = customerDao.getCustomerSync(entity.customerId)?.toDomain()
    val items = salesItemDao.getItemsForInvoiceSync(invoiceId).map { it.toDomain() }
    val allocations = allocationDao.getForInvoiceSync(PartyType.CUSTOMER, invoiceId).map { it.toDomain() }
    val returns = salesReturnDao.getForInvoiceSync(invoiceId).map { it.toDomain() }
    val returnedItems = salesReturnDao.getReturnedItemsForInvoiceSync(invoiceId)
    val returnedByProd = returnedItems.groupBy { it.productId }.mapValues { (_, list) -> list.sumOf { it.quantityPieces } }
    val notesList = noteDao.getForInvoiceSync(PartyType.CUSTOMER, invoiceId).map { it.toDomain() }
    val refunds = refundDao.getForSourceSync(RefundSourceType.INVOICE_EDIT_EXCESS, invoiceId) +
      refundDao.getForSourceSync(RefundSourceType.INVOICE_CANCEL, invoiceId)
    val auditLogs = auditDao.getForEntitySync("SALES_INVOICE", invoiceId).map { it.toDomain() }
    return SalesInvoiceDetail(
      invoice = entity.toDomain(),
      customer = customer,
      items = items,
      allocations = allocations,
      returns = returns,
      notesList = notesList,
      refunds = refunds.map { it.toDomain() },
      auditLogs = auditLogs,
      returnedQtyByProductId = returnedByProd,
      totalReturnedAmountPaise = returns.sumOf { it.totalReturnAmountPaise }
    )
  }

  suspend fun getPurchaseInvoiceDetail(invoiceId: Long): PurchaseInvoiceDetail? {
    val entity = purchaseDao.getById(invoiceId) ?: return null
    val supplier = supplierDao.getSupplierSync(entity.supplierId)?.toDomain()
    val items = purchaseItemDao.getItemsForInvoiceSync(invoiceId).map { it.toDomain() }
    val batches = batchDao.getBatchesByPurchaseInvoiceSync(invoiceId)
    val soldMap = batches.associate { b ->
      b.batchNumber to (b.initialQuantityPieces - b.currentQuantityPieces).coerceAtLeast(0)
    }
    val allocations = allocationDao.getForInvoiceSync(PartyType.SUPPLIER, invoiceId).map { it.toDomain() }
    val returns = purchaseReturnDao.getForInvoiceSync(invoiceId).map { it.toDomain() }
    val returnedItems = purchaseReturnDao.getReturnedItemsForInvoiceSync(invoiceId)
    val returnedByProd = returnedItems.groupBy { it.productId }.mapValues { (_, list) -> list.sumOf { it.quantityPieces } }
    val notesList = noteDao.getForInvoiceSync(PartyType.SUPPLIER, invoiceId).map { it.toDomain() }
    val refunds = refundDao.getForSourceSync(RefundSourceType.INVOICE_EDIT_EXCESS, invoiceId) +
      refundDao.getForSourceSync(RefundSourceType.INVOICE_CANCEL, invoiceId)
    val auditLogs = auditDao.getForEntitySync("PURCHASE_INVOICE", invoiceId).map { it.toDomain() }
    return PurchaseInvoiceDetail(
      invoice = entity.toDomain(),
      supplier = supplier,
      items = items,
      batches = batches.map { it.toDomain() },
      soldQtyByBatchNumber = soldMap,
      allocations = allocations,
      returns = returns,
      notesList = notesList,
      refunds = refunds.map { it.toDomain() },
      auditLogs = auditLogs,
      returnedQtyByProductId = returnedByProd,
      totalReturnedAmountPaise = returns.sumOf { it.totalReturnAmountPaise }
    )
  }

  suspend fun editSalesInvoice(req: EditSalesInvoiceRequest) = db.withTransaction {
    val cleanReason = req.reason.trim()
    require(cleanReason.isNotBlank()) { "Reason for editing the invoice is mandatory" }
    require(req.lines.isNotEmpty()) { "Sales invoice must keep at least one item line" }

    val existing = salesDao.getById(req.invoiceId)
      ?: throw IllegalArgumentException("Sales invoice #${req.invoiceId} not found")
    require(existing.invoiceStatus == InvoiceStatus.ACTIVE) {
      "Cancelled invoice #${existing.invoiceNumber} cannot be edited"
    }
    if (existing.revisionVersion != req.expectedRevisionVersion) {
      throw IllegalStateException("Invoice was modified concurrently. Please reopen and retry.")
    }

    val targetCustomer = customerDao.getCustomerSync(req.customerId)
      ?: throw IllegalArgumentException("Customer #${req.customerId} not found")
    val isCustomerTransfer = req.customerId != existing.customerId

    val existingReturns = salesReturnDao.getForInvoiceSync(existing.id)
    val existingNotes = noteDao.getForInvoiceSync(PartyType.CUSTOMER, existing.id)
    if (isCustomerTransfer && (existingReturns.isNotEmpty() || existingNotes.isNotEmpty())) {
      throw IllegalStateException(
        "Cannot transfer customer account on invoice #${existing.invoiceNumber} because linked returns or credit notes already exist."
      )
    }

    // Protect existing sales returns from exceeding newly edited item quantities per product
    val returnedByProd = salesReturnDao.getReturnedItemsForInvoiceSync(existing.id)
      .groupBy { it.productId }
      .mapValues { (_, list) -> list.sumOf { it.quantityPieces } }
    val requestedByProd = req.lines.groupBy { it.productId }
      .mapValues { (_, list) -> list.sumOf { it.quantityPieces } }
    for ((prodId, retQty) in returnedByProd) {
      val newQty = requestedByProd[prodId] ?: 0
      if (newQty < retQty) {
        val prodName = productDao.getProductSync(prodId)?.name ?: "Product #$prodId"
        throw IllegalStateException("Cannot reduce '$prodName' below already returned quantity ($retQty pcs).")
      }
    }

    val now = System.currentTimeMillis()
    val oldItems = salesItemDao.getItemsForInvoiceSync(existing.id)

    // 1. Restore old dispatched quantities back into their batches before re-allocating FEFO
    for (oldItem in oldItems) {
      val batch = batchDao.getById(oldItem.batchId)
      if (batch != null) {
        if (!batch.isActive) {
          batchDao.update(batch.copy(isActive = true, currentQuantityPieces = batch.currentQuantityPieces + oldItem.quantityPieces))
        } else {
          batchDao.addStockIfActive(batch.id, oldItem.quantityPieces)
        }
        movementDao.insert(
          StockMovementEntity(
            productId = oldItem.productId,
            batchId = oldItem.batchId,
            movementType = StockMovementType.STOCK_ADJUSTMENT,
            quantityPieces = oldItem.quantityPieces,
            unitCostPaise = batch.purchaseCostPerPiecePaise,
            referenceType = "SALES_INVOICE_EDIT_REVERSAL",
            referenceId = existing.id,
            movementDate = now,
            notes = "Restored stock prior to edit on #${existing.invoiceNumber}",
            createdAt = now
          )
        )
      }
    }
    salesItemDao.deleteForInvoice(existing.id)

    // 2. Allocate FEFO for updated lines cleanly
    val remainingBatchPieces = mutableMapOf<Long, Int>()
    val preparedItems = mutableListOf<Pair<SalesInvoiceItemEntity, Long>>()
    var newSubtotalPaise = 0L

    for (line in req.lines) {
      require(line.quantityPieces > 0) { "Item quantity must be positive" }
      require(line.unitPricePaise >= 0) { "Unit price cannot be negative" }
      require(line.itemDiscountPaise >= 0) { "Item discount cannot be negative" }
      val prodName = line.productNameSnapshot.ifBlank {
        productDao.getProductSync(line.productId)?.name ?: "Product #${line.productId}"
      }
      val availableBatches = batchDao.getActiveBatchesForProductSync(line.productId).map { entity ->
        val effQty = remainingBatchPieces.getOrPut(entity.id) { entity.currentQuantityPieces }
        entity.toDomain().copy(currentQuantityPieces = effQty)
      }
      val fefo = FefoInventoryEngine.allocateFefo(
        requestedPieces = line.quantityPieces,
        availableBatches = availableBatches,
        allowExpired = req.allowExpired,
        currentTimestamp = req.invoiceDate
      )
      if (fefo.remainingUnfulfilledPieces > 0) {
        throw IllegalStateException(
          "Insufficient stock for '$prodName'. Requested: ${line.quantityPieces} pcs, available: ${fefo.fulfilledPieces} pcs."
        )
      }

      var itemDiscountLeft = line.itemDiscountPaise
      fefo.allocations.forEachIndexed { idx, alloc ->
        val currentLeft = remainingBatchPieces[alloc.batch.id] ?: alloc.batch.currentQuantityPieces
        remainingBatchPieces[alloc.batch.id] = currentLeft - alloc.quantityPiecesToIssue
        val rawTotal = alloc.quantityPiecesToIssue.toLong() * line.unitPricePaise
        val sliceDiscount = if (idx == fefo.allocations.lastIndex) {
          itemDiscountLeft.coerceAtMost(rawTotal)
        } else 0L
        itemDiscountLeft -= sliceDiscount
        val netLineTotal = (rawTotal - sliceDiscount).coerceAtLeast(0L)
        newSubtotalPaise += netLineTotal

        preparedItems.add(
          SalesInvoiceItemEntity(
            salesInvoiceId = existing.id,
            productId = line.productId,
            batchId = alloc.batch.id,
            quantityPieces = alloc.quantityPiecesToIssue,
            unitPricePaise = line.unitPricePaise,
            lineTotalPaise = netLineTotal,
            productNameSnapshot = prodName,
            unitType = line.unitType,
            unitQuantity = if (idx == 0 && fefo.allocations.size == 1) line.unitQuantity else alloc.quantityPiecesToIssue,
            unitRatePaise = if (idx == 0 && fefo.allocations.size == 1) line.unitRatePaise else line.unitPricePaise,
            itemDiscountPaise = sliceDiscount
          ) to alloc.batch.purchaseCostPerPiecePaise
        )
      }
    }

    val totals = SubledgerCalculationEngine.computeInvoiceTotals(
      subtotalPaise = newSubtotalPaise,
      discountType = req.discountType,
      discountValueRaw = req.discountValueRaw,
      additionalChargesPaise = req.additionalChargesPaise
    )
    val newTotalPaise = totals.totalAmountPaise

    // Insert new items & deduct batch inventory
    for ((entity, costPerPiece) in preparedItems) {
      salesItemDao.insertAll(listOf(entity))
      val deducted = batchDao.deductStockIfAvailable(entity.batchId, entity.quantityPieces)
      if (deducted != 1) {
        throw IllegalStateException("Concurrent stock change on batch #${entity.batchId}; edit rolled back.")
      }
      movementDao.insert(
        StockMovementEntity(
          productId = entity.productId,
          batchId = entity.batchId,
          movementType = StockMovementType.SALE_DISPATCH,
          quantityPieces = -entity.quantityPieces,
          unitCostPaise = costPerPiece,
          referenceType = "SALES_INVOICE_EDIT",
          referenceId = existing.id,
          movementDate = req.invoiceDate,
          notes = "Edited dispatch via #${existing.invoiceNumber}",
          createdAt = now
        )
      )
    }

    // 3. Subledger & Payment Allocation Reconciliation
    val oldActiveSubledger = subledgerDao.getActiveByReferenceSync("SALES_INVOICE", existing.id)
    for (se in oldActiveSubledger) {
      subledgerDao.markReversed(se.id, now, "Edited: $cleanReason")
    }
    subledgerDao.insert(
      SubledgerEntryEntity(
        partyType = PartyType.CUSTOMER,
        partyId = req.customerId,
        entryType = SubledgerEntryType.INVOICE,
        referenceType = "SALES_INVOICE",
        referenceId = existing.id,
        debitPaise = newTotalPaise,
        creditPaise = 0L,
        entryDate = req.invoiceDate,
        notes = "Edited Sales Invoice #${existing.invoiceNumber}: $cleanReason",
        operationRef = "REV-${existing.revisionVersion + 1}",
        createdAt = now
      )
    )

    val activeAllocs = allocationDao.getForInvoiceSync(PartyType.CUSTOMER, existing.id)
    var finalPaidPaise = 0L

    if (isCustomerTransfer) {
      // Return old customer's INVOICE allocations to old customer's ADVANCE pool cleanly
      for (alloc in activeAllocs) {
        allocationDao.markReversed(alloc.id, now, "Invoice transferred to ${targetCustomer.shopName}: $cleanReason")
        allocationDao.insert(
          PaymentAllocationEntity(
            paymentId = alloc.paymentId,
            partyType = PartyType.CUSTOMER,
            partyId = existing.customerId,
            allocationType = PaymentAllocationType.ADVANCE,
            invoiceId = null,
            allocatedAmountPaise = alloc.allocatedAmountPaise,
            allocatedAt = now,
            notes = "Advance freed from transferred invoice #${existing.invoiceNumber}",
            isFromAdvanceConsumption = false
          )
        )
      }
    } else {
      val currentAllocated = activeAllocs.sumOf { it.allocatedAmountPaise }
      if (currentAllocated <= newTotalPaise) {
        finalPaidPaise = currentAllocated
      } else {
        // Excess payment occurred: reduce allocations LIFO down to newTotalPaise
        val totalExcess = currentAllocated - newTotalPaise
        val requestedAdv = req.requestedAdvanceRetentionPaise ?: totalExcess
        val split = PaymentAllocationEngine.validateExcessPaymentSplit(totalExcess, requestedAdv)

        var excessLeft = totalExcess
        var advanceLeftToAssign = split.advancePaise
        var refundLeftToAssign = split.refundLiabilityPaise

        for (alloc in activeAllocs.sortedByDescending { it.id }) {
          if (excessLeft <= 0L) break
          val trimFromAlloc = kotlin.math.min(excessLeft, alloc.allocatedAmountPaise)
          val keepOnInvoice = alloc.allocatedAmountPaise - trimFromAlloc
          allocationDao.markReversed(alloc.id, now, "Invoice edited down: $cleanReason")

          if (keepOnInvoice > 0L) {
            allocationDao.insert(
              alloc.copy(
                id = 0L,
                allocatedAmountPaise = keepOnInvoice,
                notes = "Adjusted allocation after edit on #${existing.invoiceNumber}"
              )
            )
          }

          val toAdvance = kotlin.math.min(trimFromAlloc, advanceLeftToAssign)
          if (toAdvance > 0L) {
            allocationDao.insert(
              PaymentAllocationEntity(
                paymentId = alloc.paymentId,
                partyType = PartyType.CUSTOMER,
                partyId = existing.customerId,
                allocationType = PaymentAllocationType.ADVANCE,
                invoiceId = null,
                allocatedAmountPaise = toAdvance,
                allocatedAt = now,
                notes = "Excess payment retained as advance after edit on #${existing.invoiceNumber}",
                isFromAdvanceConsumption = false
              )
            )
            advanceLeftToAssign -= toAdvance
          }

          val toRefund = trimFromAlloc - toAdvance
          if (toRefund > 0L) {
            refundLeftToAssign -= toRefund
          }
          excessLeft -= trimFromAlloc
        }

        if (split.refundLiabilityPaise > 0L) {
          refundDao.insert(
            RefundRecordEntity(
              partyType = PartyType.CUSTOMER,
              partyId = existing.customerId,
              sourceType = RefundSourceType.INVOICE_EDIT_EXCESS,
              sourceId = existing.id,
              amountPaise = split.refundLiabilityPaise,
              paymentMethod = req.refundPaymentMethod,
              executionStatus = RefundExecutionStatus.RECORDED_PENDING_PAYOUT,
              reason = cleanReason,
              notes = "Recorded excess refund due from edit on #${existing.invoiceNumber}",
              referenceNumber = existing.invoiceNumber,
              recordedAt = now
            )
          )
        }
        finalPaidPaise = newTotalPaise
      }
    }

    // Credit limit guard against new exposure
    val targetEntries = subledgerDao.getEntriesForPartySync(PartyType.CUSTOMER, req.customerId).map { it.toDomain() }
    val targetBalance = SubledgerCalculationEngine.calculateCustomerBalance(targetEntries)
    val netDueAfterEdit = (newTotalPaise - finalPaidPaise).coerceAtLeast(0L)
    if (targetCustomer.creditLimitPaise > 0L && targetBalance > targetCustomer.creditLimitPaise && !req.allowCreditLimitOverride) {
      val assessment = PaymentAllocationEngine.assessCreditLimit(
        currentBalancePaise = (targetBalance - netDueAfterEdit).coerceAtLeast(0L),
        newCreditExposurePaise = netDueAfterEdit,
        creditLimitPaise = targetCustomer.creditLimitPaise
      )
      if (assessment.isExceeded) {
        throw CreditLimitExceededException(
          assessment = assessment,
          message = "Customer credit limit (${SubledgerCalculationEngine.formatPaise(assessment.creditLimitPaise)}) exceeded after edit."
        )
      }
    }

    val newStatus = when {
      finalPaidPaise >= newTotalPaise && newTotalPaise > 0L -> PaymentStatus.PAID
      finalPaidPaise > 0L -> PaymentStatus.PARTIALLY_PAID
      else -> PaymentStatus.UNPAID
    }

    val updatedInvoice = existing.copy(
      customerId = req.customerId,
      invoiceDate = req.invoiceDate,
      dueDate = if (req.dueDate > 0L) req.dueDate else existing.dueDate,
      subtotalPaise = totals.subtotalPaise,
      discountType = req.discountType,
      discountValueRaw = req.discountValueRaw,
      discountAmountPaise = totals.discountAmountPaise,
      additionalChargesPaise = totals.additionalChargesPaise,
      totalAmountPaise = newTotalPaise,
      paidAmountPaise = finalPaidPaise,
      paymentStatus = newStatus,
      paymentMethod = req.paymentMethod,
      notes = req.notes.trim(),
      revisionVersion = existing.revisionVersion + 1,
      updatedAt = now,
      partyNameSnapshot = req.partyNameSnapshot.trim().ifBlank { targetCustomer.shopName },
      partyPhoneSnapshot = req.partyPhoneSnapshot.trim(),
      partyAddressSnapshot = req.partyAddressSnapshot.trim()
    )
    salesDao.update(updatedInvoice)

    auditDao.insert(
      FinancialAuditLogEntity(
        operationType = AuditOperationType.INVOICE_EDIT,
        entityType = "SALES_INVOICE",
        entityId = existing.id,
        relatedPartyType = PartyType.CUSTOMER,
        relatedPartyId = req.customerId,
        reason = cleanReason,
        beforeJson = JSONObject()
          .put("customerId", existing.customerId)
          .put("totalAmountPaise", existing.totalAmountPaise)
          .put("paidAmountPaise", existing.paidAmountPaise)
          .put("revision", existing.revisionVersion)
          .toString(),
        afterJson = JSONObject()
          .put("customerId", updatedInvoice.customerId)
          .put("totalAmountPaise", updatedInvoice.totalAmountPaise)
          .put("paidAmountPaise", updatedInvoice.paidAmountPaise)
          .put("revision", updatedInvoice.revisionVersion)
          .toString(),
        operationReference = existing.invoiceNumber,
        createdAt = now
      )
    )
  }

  suspend fun editPurchaseInvoice(req: EditPurchaseInvoiceRequest) = db.withTransaction {
    val cleanReason = req.reason.trim()
    require(cleanReason.isNotBlank()) { "Reason for editing purchase invoice is mandatory" }
    require(req.lines.isNotEmpty()) { "Purchase invoice must keep at least one item line" }

    val existing = purchaseDao.getById(req.invoiceId)
      ?: throw IllegalArgumentException("Purchase invoice #${req.invoiceId} not found")
    require(existing.invoiceStatus == InvoiceStatus.ACTIVE) {
      "Cancelled purchase invoice #${existing.invoiceNumber} cannot be edited"
    }
    if (existing.revisionVersion != req.expectedRevisionVersion) {
      throw IllegalStateException("Purchase invoice was modified concurrently. Please reopen and retry.")
    }

    val targetSupplier = supplierDao.getSupplierSync(req.supplierId)
      ?: throw IllegalArgumentException("Supplier #${req.supplierId} not found")
    val isSupplierTransfer = req.supplierId != existing.supplierId

    val existingReturns = purchaseReturnDao.getForInvoiceSync(existing.id)
    val existingNotes = noteDao.getForInvoiceSync(PartyType.SUPPLIER, existing.id)
    if (isSupplierTransfer && (existingReturns.isNotEmpty() || existingNotes.isNotEmpty())) {
      throw IllegalStateException(
        "Cannot transfer supplier account on purchase #${existing.invoiceNumber} because linked returns or debit notes exist."
      )
    }

    val now = System.currentTimeMillis()
    val existingBatches = batchDao.getBatchesByPurchaseInvoiceSync(existing.id)
    val oldBatchByKey = existingBatches.associateBy { it.productId to it.batchNumber }
    val seenOldBatchIds = mutableSetOf<Long>()

    // Validate duplicate (productId, batchNumber) lines in edit request
    val seenReqKeys = mutableSetOf<Pair<Long, String>>()
    var newSubtotalPaise = 0L
    for (line in req.lines) {
      require(line.quantityPieces > 0) { "Purchase item quantity must be positive" }
      require(line.costPerPiecePaise >= 0) { "Cost cannot be negative" }
      require(line.batchNumber.isNotBlank()) { "Batch number cannot be blank" }
      val key = line.productId to line.batchNumber.trim()
      require(seenReqKeys.add(key)) { "Duplicate batch '${line.batchNumber}' for same product in invoice" }

      val lineTotal = (line.quantityPieces.toLong() * line.costPerPiecePaise - line.itemDiscountPaise).coerceAtLeast(0L)
      newSubtotalPaise += lineTotal

      val existingBatch = oldBatchByKey[key]
      if (existingBatch != null) {
        val soldOrConsumed = (existingBatch.initialQuantityPieces - existingBatch.currentQuantityPieces).coerceAtLeast(0)
        if (line.quantityPieces < soldOrConsumed) {
          throw IllegalStateException(
            "Batch '${existingBatch.batchNumber}' has already sold/consumed $soldOrConsumed pcs. Quantity cannot be reduced below $soldOrConsumed pcs."
          )
        }
        seenOldBatchIds.add(existingBatch.id)
      }
    }

    // Any removed old batch must have 0 consumed stock!
    for (oldBatch in existingBatches) {
      if (oldBatch.id !in seenOldBatchIds) {
        val soldOrConsumed = (oldBatch.initialQuantityPieces - oldBatch.currentQuantityPieces).coerceAtLeast(0)
        if (soldOrConsumed > 0) {
          throw IllegalStateException(
            "Batch '${oldBatch.batchNumber}' has $soldOrConsumed pcs already sold/returned and cannot be removed from invoice #${existing.invoiceNumber}."
          )
        }
        batchDao.update(oldBatch.copy(isActive = false, currentQuantityPieces = 0))
        movementDao.insert(
          StockMovementEntity(
            productId = oldBatch.productId,
            batchId = oldBatch.id,
            movementType = StockMovementType.STOCK_ADJUSTMENT,
            quantityPieces = -oldBatch.currentQuantityPieces,
            unitCostPaise = oldBatch.purchaseCostPerPiecePaise,
            referenceType = "PURCHASE_INVOICE_EDIT_REMOVAL",
            referenceId = existing.id,
            movementDate = now,
            notes = "Removed unconsumed batch '${oldBatch.batchNumber}' in edit of #${existing.invoiceNumber}",
            createdAt = now
          )
        )
      }
    }

    purchaseItemDao.deleteForInvoice(existing.id)
    for (line in req.lines) {
      val cleanBatch = line.batchNumber.trim()
      val key = line.productId to cleanBatch
      val prodName = line.productNameSnapshot.ifBlank {
        productDao.getProductSync(line.productId)?.name ?: "Product #${line.productId}"
      }
      val lineTotal = (line.quantityPieces.toLong() * line.costPerPiecePaise - line.itemDiscountPaise).coerceAtLeast(0L)

      purchaseItemDao.insertAll(
        listOf(
          PurchaseInvoiceItemEntity(
            purchaseInvoiceId = existing.id,
            productId = line.productId,
            batchNumber = cleanBatch,
            expiryDate = line.expiryDate,
            quantityPieces = line.quantityPieces,
            costPerPiecePaise = line.costPerPiecePaise,
            lineTotalPaise = lineTotal,
            productNameSnapshot = prodName,
            unitType = line.unitType,
            unitQuantity = line.unitQuantity,
            unitRatePaise = line.unitRatePaise,
            itemDiscountPaise = line.itemDiscountPaise
          )
        )
      )

      val oldBatch = oldBatchByKey[key]
      if (oldBatch != null) {
        val soldOrConsumed = (oldBatch.initialQuantityPieces - oldBatch.currentQuantityPieces).coerceAtLeast(0)
        val newCurrent = (line.quantityPieces - soldOrConsumed).coerceAtLeast(0)
        val delta = newCurrent - oldBatch.currentQuantityPieces
        batchDao.update(
          oldBatch.copy(
            purchaseDate = req.invoiceDate,
            expiryDate = line.expiryDate,
            initialQuantityPieces = line.quantityPieces,
            currentQuantityPieces = newCurrent,
            purchaseCostPerPiecePaise = line.costPerPiecePaise,
            isActive = true
          )
        )
        if (delta != 0) {
          movementDao.insert(
            StockMovementEntity(
              productId = line.productId,
              batchId = oldBatch.id,
              movementType = StockMovementType.STOCK_ADJUSTMENT,
              quantityPieces = delta,
              unitCostPaise = line.costPerPiecePaise,
              referenceType = "PURCHASE_INVOICE_EDIT",
              referenceId = existing.id,
              movementDate = req.invoiceDate,
              notes = "Batch '$cleanBatch' adjusted by $delta pcs on edit of #${existing.invoiceNumber}",
              createdAt = now
            )
          )
        }
      } else {
        val newBatchId = batchDao.insert(
          ProductBatchEntity(
            productId = line.productId,
            batchNumber = cleanBatch,
            purchaseInvoiceId = existing.id,
            purchaseDate = req.invoiceDate,
            expiryDate = line.expiryDate,
            initialQuantityPieces = line.quantityPieces,
            currentQuantityPieces = line.quantityPieces,
            purchaseCostPerPiecePaise = line.costPerPiecePaise
          )
        )
        movementDao.insert(
          StockMovementEntity(
            productId = line.productId,
            batchId = newBatchId,
            movementType = StockMovementType.PURCHASE_RECEIPT,
            quantityPieces = line.quantityPieces,
            unitCostPaise = line.costPerPiecePaise,
            referenceType = "PURCHASE_INVOICE_EDIT",
            referenceId = existing.id,
            movementDate = req.invoiceDate,
            notes = "Added batch '$cleanBatch' during edit of #${existing.invoiceNumber}",
            createdAt = now
          )
        )
      }
    }

    val totals = SubledgerCalculationEngine.computeInvoiceTotals(
      subtotalPaise = newSubtotalPaise,
      discountType = req.discountType,
      discountValueRaw = req.discountValueRaw,
      additionalChargesPaise = req.additionalChargesPaise
    )
    val newTotalPaise = totals.totalAmountPaise

    val oldActiveSubledger = subledgerDao.getActiveByReferenceSync("PURCHASE_INVOICE", existing.id)
    for (se in oldActiveSubledger) {
      subledgerDao.markReversed(se.id, now, "Edited: $cleanReason")
    }
    subledgerDao.insert(
      SubledgerEntryEntity(
        partyType = PartyType.SUPPLIER,
        partyId = req.supplierId,
        entryType = SubledgerEntryType.INVOICE,
        referenceType = "PURCHASE_INVOICE",
        referenceId = existing.id,
        debitPaise = 0L,
        creditPaise = newTotalPaise,
        entryDate = req.invoiceDate,
        notes = "Edited Purchase Invoice #${existing.invoiceNumber}: $cleanReason",
        operationRef = "REV-${existing.revisionVersion + 1}",
        createdAt = now
      )
    )

    val activeAllocs = allocationDao.getForInvoiceSync(PartyType.SUPPLIER, existing.id)
    var finalPaidPaise = 0L

    if (isSupplierTransfer) {
      for (alloc in activeAllocs) {
        allocationDao.markReversed(alloc.id, now, "Purchase transferred to ${targetSupplier.name}: $cleanReason")
        allocationDao.insert(
          PaymentAllocationEntity(
            paymentId = alloc.paymentId,
            partyType = PartyType.SUPPLIER,
            partyId = existing.supplierId,
            allocationType = PaymentAllocationType.ADVANCE,
            invoiceId = null,
            allocatedAmountPaise = alloc.allocatedAmountPaise,
            allocatedAt = now,
            notes = "Advance freed from transferred purchase #${existing.invoiceNumber}",
            isFromAdvanceConsumption = false
          )
        )
      }
    } else {
      val currentAllocated = activeAllocs.sumOf { it.allocatedAmountPaise }
      if (currentAllocated <= newTotalPaise) {
        finalPaidPaise = currentAllocated
      } else {
        val totalExcess = currentAllocated - newTotalPaise
        val requestedAdv = req.requestedAdvanceRetentionPaise ?: totalExcess
        val split = PaymentAllocationEngine.validateExcessPaymentSplit(totalExcess, requestedAdv)

        var excessLeft = totalExcess
        var advanceLeft = split.advancePaise
        for (alloc in activeAllocs.sortedByDescending { it.id }) {
          if (excessLeft <= 0L) break
          val trim = kotlin.math.min(excessLeft, alloc.allocatedAmountPaise)
          val keep = alloc.allocatedAmountPaise - trim
          allocationDao.markReversed(alloc.id, now, "Purchase edited down: $cleanReason")
          if (keep > 0L) {
            allocationDao.insert(
              alloc.copy(
                id = 0L,
                allocatedAmountPaise = keep,
                notes = "Adjusted allocation after edit on #${existing.invoiceNumber}"
              )
            )
          }
          val toAdv = kotlin.math.min(trim, advanceLeft)
          if (toAdv > 0L) {
            allocationDao.insert(
              PaymentAllocationEntity(
                paymentId = alloc.paymentId,
                partyType = PartyType.SUPPLIER,
                partyId = existing.supplierId,
                allocationType = PaymentAllocationType.ADVANCE,
                invoiceId = null,
                allocatedAmountPaise = toAdv,
                allocatedAt = now,
                notes = "Excess supplier payment retained as advance from #${existing.invoiceNumber}",
                isFromAdvanceConsumption = false
              )
            )
            advanceLeft -= toAdv
          }
          excessLeft -= trim
        }

        if (split.refundLiabilityPaise > 0L) {
          refundDao.insert(
            RefundRecordEntity(
              partyType = PartyType.SUPPLIER,
              partyId = existing.supplierId,
              sourceType = RefundSourceType.INVOICE_EDIT_EXCESS,
              sourceId = existing.id,
              amountPaise = split.refundLiabilityPaise,
              paymentMethod = req.refundPaymentMethod,
              executionStatus = RefundExecutionStatus.RECORDED_PENDING_PAYOUT,
              reason = cleanReason,
              notes = "Recorded excess supplier refund from edit on #${existing.invoiceNumber}",
              referenceNumber = existing.invoiceNumber,
              recordedAt = now
            )
          )
        }
        finalPaidPaise = newTotalPaise
      }
    }

    val newStatus = when {
      finalPaidPaise >= newTotalPaise && newTotalPaise > 0L -> PaymentStatus.PAID
      finalPaidPaise > 0L -> PaymentStatus.PARTIALLY_PAID
      else -> PaymentStatus.UNPAID
    }

    val updatedInvoice = existing.copy(
      supplierId = req.supplierId,
      invoiceDate = req.invoiceDate,
      dueDate = if (req.dueDate > 0L) req.dueDate else existing.dueDate,
      subtotalPaise = totals.subtotalPaise,
      discountType = req.discountType,
      discountValueRaw = req.discountValueRaw,
      discountAmountPaise = totals.discountAmountPaise,
      additionalChargesPaise = totals.additionalChargesPaise,
      totalAmountPaise = newTotalPaise,
      paidAmountPaise = finalPaidPaise,
      paymentStatus = newStatus,
      paymentMethod = req.paymentMethod,
      notes = req.notes.trim(),
      revisionVersion = existing.revisionVersion + 1,
      updatedAt = now,
      partyNameSnapshot = req.partyNameSnapshot.trim().ifBlank { targetSupplier.name },
      partyPhoneSnapshot = req.partyPhoneSnapshot.trim(),
      partyAddressSnapshot = req.partyAddressSnapshot.trim()
    )
    purchaseDao.update(updatedInvoice)

    auditDao.insert(
      FinancialAuditLogEntity(
        operationType = AuditOperationType.INVOICE_EDIT,
        entityType = "PURCHASE_INVOICE",
        entityId = existing.id,
        relatedPartyType = PartyType.SUPPLIER,
        relatedPartyId = req.supplierId,
        reason = cleanReason,
        beforeJson = JSONObject()
          .put("supplierId", existing.supplierId)
          .put("totalAmountPaise", existing.totalAmountPaise)
          .put("paidAmountPaise", existing.paidAmountPaise)
          .put("revision", existing.revisionVersion)
          .toString(),
        afterJson = JSONObject()
          .put("supplierId", updatedInvoice.supplierId)
          .put("totalAmountPaise", updatedInvoice.totalAmountPaise)
          .put("paidAmountPaise", updatedInvoice.paidAmountPaise)
          .put("revision", updatedInvoice.revisionVersion)
          .toString(),
        operationReference = existing.invoiceNumber,
        createdAt = now
      )
    )
  }

  /**
   * Soft-cancels a Sales Invoice (never deletes row, never reuses invoiceNumber).
   * - Supports both BLOCK_IF_PAYMENTS_EXIST and CONVERT_TO_ADVANCE.
   * - Restores dispatched batch quantities & logs reversal stock movements.
   */
  suspend fun cancelSalesInvoice(
    invoiceId: Long,
    reason: String,
    paymentMode: InvoiceCancellationPaymentMode = InvoiceCancellationPaymentMode.CONVERT_TO_ADVANCE
  ) = db.withTransaction {
    val cleanReason = reason.trim()
    require(cleanReason.isNotBlank()) { "Cancellation reason is mandatory" }

    val invoice = salesDao.getById(invoiceId)
      ?: throw IllegalArgumentException("Sales invoice #$invoiceId not found")
    require(invoice.invoiceStatus == InvoiceStatus.ACTIVE) {
      "Invoice #${invoice.invoiceNumber} is already cancelled"
    }
    val returns = salesReturnDao.getForInvoiceSync(invoiceId)
    if (returns.isNotEmpty()) {
      throw IllegalStateException(
        "Invoice #${invoice.invoiceNumber} already has ${returns.size} sales return(s) and cannot be cancelled."
      )
    }

    val activeAllocs = allocationDao.getForInvoiceSync(PartyType.CUSTOMER, invoiceId)
    if (activeAllocs.isNotEmpty() && paymentMode == InvoiceCancellationPaymentMode.BLOCK_IF_PAYMENTS_EXIST) {
      throw IllegalStateException(
        "Invoice #${invoice.invoiceNumber} has active payment allocations (${SubledgerCalculationEngine.formatPaise(activeAllocs.sumOf { it.allocatedAmountPaise })}). " +
          "Reverse payments first OR choose 'Cancel & Convert Payments to Advance'."
      )
    }

    val now = System.currentTimeMillis()
    for (alloc in activeAllocs) {
      allocationDao.markReversed(alloc.id, now, "Cancelled invoice #${invoice.invoiceNumber}: $cleanReason")
      allocationDao.insert(
        PaymentAllocationEntity(
          paymentId = alloc.paymentId,
          partyType = PartyType.CUSTOMER,
          partyId = invoice.customerId,
          allocationType = PaymentAllocationType.ADVANCE,
          invoiceId = null,
          allocatedAmountPaise = alloc.allocatedAmountPaise,
          allocatedAt = now,
          notes = "Advance from cancelled Sale #${invoice.invoiceNumber}",
          isFromAdvanceConsumption = false
        )
      )
    }

    val items = salesItemDao.getItemsForInvoiceSync(invoiceId)
    for (item in items) {
      val batch = batchDao.getById(item.batchId)
      if (batch != null) {
        if (!batch.isActive) {
          batchDao.update(batch.copy(isActive = true, currentQuantityPieces = batch.currentQuantityPieces + item.quantityPieces))
        } else {
          batchDao.addStockIfActive(batch.id, item.quantityPieces)
        }
        movementDao.insert(
          StockMovementEntity(
            productId = item.productId,
            batchId = item.batchId,
            movementType = StockMovementType.STOCK_ADJUSTMENT,
            quantityPieces = item.quantityPieces,
            unitCostPaise = batch.purchaseCostPerPiecePaise,
            referenceType = "SALES_INVOICE_CANCEL",
            referenceId = invoiceId,
            movementDate = now,
            notes = "Restored from cancelled Sale #${invoice.invoiceNumber}: $cleanReason",
            createdAt = now
          )
        )
      }
    }

    val subledgerEntries = subledgerDao.getActiveByReferenceSync("SALES_INVOICE", invoiceId)
    for (se in subledgerEntries) {
      subledgerDao.markReversed(se.id, now, "Invoice cancelled: $cleanReason")
    }

    salesDao.update(
      invoice.copy(
        invoiceStatus = InvoiceStatus.CANCELLED,
        paidAmountPaise = 0L,
        paymentStatus = PaymentStatus.UNPAID,
        cancelledAt = now,
        cancellationReason = cleanReason,
        revisionVersion = invoice.revisionVersion + 1,
        updatedAt = now
      )
    )

    auditDao.insert(
      FinancialAuditLogEntity(
        operationType = AuditOperationType.INVOICE_CANCEL,
        entityType = "SALES_INVOICE",
        entityId = invoiceId,
        relatedPartyType = PartyType.CUSTOMER,
        relatedPartyId = invoice.customerId,
        reason = cleanReason,
        beforeJson = JSONObject()
          .put("invoiceStatus", invoice.invoiceStatus.name)
          .put("totalAmountPaise", invoice.totalAmountPaise)
          .put("paidAmountPaise", invoice.paidAmountPaise)
          .toString(),
        afterJson = JSONObject()
          .put("invoiceStatus", InvoiceStatus.CANCELLED.name)
          .put("convertedPaymentsToAdvancePaise", activeAllocs.sumOf { it.allocatedAmountPaise })
          .toString(),
        operationReference = invoice.invoiceNumber,
        createdAt = now
      )
    )
  }

  /**
   * Soft-cancels a Purchase Invoice only if none of its received batches have been sold or returned.
   */
  suspend fun cancelPurchaseInvoice(
    invoiceId: Long,
    reason: String,
    paymentMode: InvoiceCancellationPaymentMode = InvoiceCancellationPaymentMode.CONVERT_TO_ADVANCE
  ) = db.withTransaction {
    val cleanReason = reason.trim()
    require(cleanReason.isNotBlank()) { "Cancellation reason is mandatory" }

    val invoice = purchaseDao.getById(invoiceId)
      ?: throw IllegalArgumentException("Purchase invoice #$invoiceId not found")
    require(invoice.invoiceStatus == InvoiceStatus.ACTIVE) {
      "Purchase invoice #${invoice.invoiceNumber} is already cancelled"
    }
    val returns = purchaseReturnDao.getForInvoiceSync(invoiceId)
    if (returns.isNotEmpty()) {
      throw IllegalStateException(
        "Purchase #${invoice.invoiceNumber} has existing purchase returns and cannot be cancelled."
      )
    }

    val batches = batchDao.getBatchesByPurchaseInvoiceSync(invoiceId)
    for (b in batches) {
      if (b.currentQuantityPieces < b.initialQuantityPieces) {
        val used = b.initialQuantityPieces - b.currentQuantityPieces
        throw IllegalStateException(
          "Cannot cancel Purchase #${invoice.invoiceNumber}: batch '${b.batchNumber}' has $used pcs already sold or adjusted."
        )
      }
    }

    val activeAllocs = allocationDao.getForInvoiceSync(PartyType.SUPPLIER, invoiceId)
    if (activeAllocs.isNotEmpty() && paymentMode == InvoiceCancellationPaymentMode.BLOCK_IF_PAYMENTS_EXIST) {
      throw IllegalStateException(
        "Purchase #${invoice.invoiceNumber} has active payment allocations (${SubledgerCalculationEngine.formatPaise(activeAllocs.sumOf { it.allocatedAmountPaise })}). " +
          "Reverse payments first OR convert payments to supplier Advance."
      )
    }

    val now = System.currentTimeMillis()
    for (alloc in activeAllocs) {
      allocationDao.markReversed(alloc.id, now, "Cancelled purchase #${invoice.invoiceNumber}: $cleanReason")
      allocationDao.insert(
        PaymentAllocationEntity(
          paymentId = alloc.paymentId,
          partyType = PartyType.SUPPLIER,
          partyId = invoice.supplierId,
          allocationType = PaymentAllocationType.ADVANCE,
          invoiceId = null,
          allocatedAmountPaise = alloc.allocatedAmountPaise,
          allocatedAt = now,
          notes = "Advance from cancelled Purchase #${invoice.invoiceNumber}",
          isFromAdvanceConsumption = false
        )
      )
    }

    for (b in batches) {
      val qtyToRemove = b.currentQuantityPieces
      batchDao.update(b.copy(isActive = false, currentQuantityPieces = 0))
      if (qtyToRemove > 0) {
        movementDao.insert(
          StockMovementEntity(
            productId = b.productId,
            batchId = b.id,
            movementType = StockMovementType.STOCK_ADJUSTMENT,
            quantityPieces = -qtyToRemove,
            unitCostPaise = b.purchaseCostPerPiecePaise,
            referenceType = "PURCHASE_INVOICE_CANCEL",
            referenceId = invoiceId,
            movementDate = now,
            notes = "Deactivated batch '${b.batchNumber}' from cancelled Purchase #${invoice.invoiceNumber}: $cleanReason",
            createdAt = now
          )
        )
      }
    }

    val subledgerEntries = subledgerDao.getActiveByReferenceSync("PURCHASE_INVOICE", invoiceId)
    for (se in subledgerEntries) {
      subledgerDao.markReversed(se.id, now, "Purchase cancelled: $cleanReason")
    }

    purchaseDao.update(
      invoice.copy(
        invoiceStatus = InvoiceStatus.CANCELLED,
        paidAmountPaise = 0L,
        paymentStatus = PaymentStatus.UNPAID,
        cancelledAt = now,
        cancellationReason = cleanReason,
        revisionVersion = invoice.revisionVersion + 1,
        updatedAt = now
      )
    )

    auditDao.insert(
      FinancialAuditLogEntity(
        operationType = AuditOperationType.INVOICE_CANCEL,
        entityType = "PURCHASE_INVOICE",
        entityId = invoiceId,
        relatedPartyType = PartyType.SUPPLIER,
        relatedPartyId = invoice.supplierId,
        reason = cleanReason,
        beforeJson = JSONObject()
          .put("invoiceStatus", invoice.invoiceStatus.name)
          .put("totalAmountPaise", invoice.totalAmountPaise)
          .toString(),
        afterJson = JSONObject()
          .put("invoiceStatus", InvoiceStatus.CANCELLED.name)
          .put("convertedPaymentsToAdvancePaise", activeAllocs.sumOf { it.allocatedAmountPaise })
          .toString(),
        operationReference = invoice.invoiceNumber,
        createdAt = now
      )
    )
  }
}
