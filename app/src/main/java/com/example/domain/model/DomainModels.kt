package com.example.domain.model

data class QuantityBreakdown(
  val cartons: Int,
  val boxes: Int,
  val pieces: Int,
  val totalPieces: Long
) {
  fun toFormattedString(): String {
    val parts = mutableListOf<String>()
    if (cartons > 0) parts.add("$cartons Ctn")
    if (boxes > 0) parts.add("$boxes Box")
    if (pieces > 0 || parts.isEmpty()) parts.add("$pieces Pcs")
    return parts.joinToString(", ")
  }
}

data class Product(
  val id: Long = 0,
  val name: String,
  val sku: String,
  val category: String,
  val defaultPurchasePricePaise: Long,
  val defaultSellingPricePaise: Long,
  val minStockLevelPieces: Int,
  val isActive: Boolean = true,
  val createdAt: Long = System.currentTimeMillis()
)

data class ProductUnit(
  val id: Long = 0,
  val productId: Long,
  val piecesPerBox: Int,
  val boxesPerCarton: Int
) {
  val piecesPerCarton: Int get() = piecesPerBox * boxesPerCarton
}

data class ProductSupplier(
  val id: Long = 0,
  val productId: Long,
  val supplierId: Long,
  val supplierProductCode: String = "",
  val purchaseCostPaise: Long,
  val isPreferredSupplier: Boolean = false
)

data class Supplier(
  val id: Long = 0,
  val name: String,
  val contactPerson: String,
  val phoneNumber: String,
  val address: String,
  val openingBalancePaise: Long = 0L,
  val openingBalanceDate: Long = System.currentTimeMillis(),
  val creditTermsDays: Int = 15,
  val creditLimitPaise: Long = 0L,
  val isActive: Boolean = true,
  val createdAt: Long = System.currentTimeMillis()
)

data class Customer(
  val id: Long = 0,
  val shopName: String,
  val ownerName: String,
  val phoneNumber: String,
  val address: String,
  val openingBalancePaise: Long = 0L,
  val openingBalanceDate: Long = System.currentTimeMillis(),
  val creditLimitPaise: Long = 0L,
  val paymentTermsDays: Int = 7,
  val isActive: Boolean = true,
  val createdAt: Long = System.currentTimeMillis()
)

data class CustomerProductPrice(
  val id: Long = 0,
  val customerId: Long,
  val productId: Long,
  val customSellingPricePaise: Long
)

data class SubledgerEntry(
  val id: Long = 0,
  val partyType: PartyType,
  val partyId: Long,
  val entryType: SubledgerEntryType,
  val referenceType: String,
  val referenceId: Long,
  val debitPaise: Long,
  val creditPaise: Long,
  val entryDate: Long,
  val createdAt: Long = System.currentTimeMillis(),
  val notes: String = "",
  val isReversed: Boolean = false,
  val operationRef: String = "",
  val reversedAt: Long? = null,
  val reversalReason: String? = null
)

data class PaymentAllocation(
  val id: Long = 0,
  val paymentId: Long,
  val partyType: PartyType = PartyType.CUSTOMER,
  val partyId: Long = 0L,
  val allocationType: PaymentAllocationType = PaymentAllocationType.INVOICE,
  val invoiceId: Long? = null,
  val allocatedAmountPaise: Long,
  val allocatedAt: Long = System.currentTimeMillis(),
  val notes: String = "",
  val isReversed: Boolean = false,
  val isFromAdvanceConsumption: Boolean = false,
  val reversedAt: Long? = null,
  val reversalReason: String? = null
)

data class AppSetting(
  val key: String,
  val value: String
)

// Step 2 Domain Models

data class ProductBatch(
  val id: Long = 0,
  val productId: Long,
  val batchNumber: String,
  val purchaseInvoiceId: Long? = null,
  val purchaseDate: Long,
  val expiryDate: Long,
  val initialQuantityPieces: Int,
  val currentQuantityPieces: Int,
  val purchaseCostPerPiecePaise: Long,
  val isActive: Boolean = true,
  val createdAt: Long = System.currentTimeMillis()
) {
  val isExpired: Boolean get() = expiryDate <= System.currentTimeMillis()
}

data class StockMovement(
  val id: Long = 0,
  val productId: Long,
  val batchId: Long? = null,
  val movementType: StockMovementType,
  val quantityPieces: Int,
  val unitCostPaise: Long,
  val referenceType: String,
  val referenceId: Long,
  val movementDate: Long,
  val notes: String = "",
  val createdAt: Long = System.currentTimeMillis()
)

data class PurchaseInvoice(
  val id: Long = 0,
  val invoiceNumber: String,
  val supplierId: Long,
  val invoiceDate: Long,
  val dueDate: Long,
  val totalAmountPaise: Long,
  val paidAmountPaise: Long = 0L,
  val paymentStatus: PaymentStatus = PaymentStatus.UNPAID,
  val paymentMethod: PaymentMethod? = null,
  val notes: String = "",
  val isConfirmed: Boolean = true,
  val createdAt: Long = System.currentTimeMillis(),
  val subtotalPaise: Long = totalAmountPaise,
  val discountType: DiscountType = DiscountType.NONE,
  val discountValueRaw: Long = 0L,
  val discountAmountPaise: Long = 0L,
  val additionalChargesPaise: Long = 0L,
  val invoiceStatus: InvoiceStatus = InvoiceStatus.ACTIVE,
  val cancelledAt: Long? = null,
  val cancellationReason: String? = null,
  val revisionVersion: Int = 1,
  val updatedAt: Long = createdAt,
  val partyNameSnapshot: String = "",
  val partyPhoneSnapshot: String = "",
  val partyAddressSnapshot: String = ""
) {
  val balanceDuePaise: Long
    get() = if (invoiceStatus == InvoiceStatus.CANCELLED) 0L else (totalAmountPaise - paidAmountPaise).coerceAtLeast(0L)
}

data class PurchaseInvoiceItem(
  val id: Long = 0,
  val purchaseInvoiceId: Long = 0,
  val productId: Long,
  val batchNumber: String,
  val expiryDate: Long,
  val quantityPieces: Int,
  val costPerPiecePaise: Long,
  val lineTotalPaise: Long,
  val productNameSnapshot: String = "",
  val unitType: UnitType = UnitType.PIECE,
  val unitQuantity: Int = quantityPieces,
  val unitRatePaise: Long = costPerPiecePaise,
  val itemDiscountPaise: Long = 0L
)

data class SalesInvoice(
  val id: Long = 0,
  val invoiceNumber: String,
  val customerId: Long,
  val invoiceDate: Long,
  val dueDate: Long,
  val totalAmountPaise: Long,
  val paidAmountPaise: Long = 0L,
  val paymentStatus: PaymentStatus = PaymentStatus.UNPAID,
  val paymentMethod: PaymentMethod? = null,
  val notes: String = "",
  val isConfirmed: Boolean = true,
  val createdAt: Long = System.currentTimeMillis(),
  val subtotalPaise: Long = totalAmountPaise,
  val discountType: DiscountType = DiscountType.NONE,
  val discountValueRaw: Long = 0L,
  val discountAmountPaise: Long = 0L,
  val additionalChargesPaise: Long = 0L,
  val invoiceStatus: InvoiceStatus = InvoiceStatus.ACTIVE,
  val cancelledAt: Long? = null,
  val cancellationReason: String? = null,
  val revisionVersion: Int = 1,
  val updatedAt: Long = createdAt,
  val partyNameSnapshot: String = "",
  val partyPhoneSnapshot: String = "",
  val partyAddressSnapshot: String = ""
) {
  val balanceDuePaise: Long
    get() = if (invoiceStatus == InvoiceStatus.CANCELLED) 0L else (totalAmountPaise - paidAmountPaise).coerceAtLeast(0L)
}

data class SalesInvoiceItem(
  val id: Long = 0,
  val salesInvoiceId: Long = 0,
  val productId: Long,
  val batchId: Long,
  val quantityPieces: Int,
  val unitPricePaise: Long,
  val lineTotalPaise: Long,
  val productNameSnapshot: String = "",
  val unitType: UnitType = UnitType.PIECE,
  val unitQuantity: Int = quantityPieces,
  val unitRatePaise: Long = unitPricePaise,
  val itemDiscountPaise: Long = 0L
)

data class Expense(
  val id: Long = 0,
  val category: ExpenseCategory,
  val amountPaise: Long,
  val expenseDate: Long,
  val paymentMethod: PaymentMethod,
  val referenceNumber: String = "",
  val notes: String = "",
  val createdAt: Long = System.currentTimeMillis(),
  val deletedAt: Long? = null
)

data class CustomerPaymentReminder(
  val id: Long = 0,
  val customerId: Long,
  val targetPhoneE164: String,
  val dueKind: ReminderDueKind,
  val status: ReminderStatus,
  val snapshotPendingInvoiceCount: Int,
  val snapshotTotalInvoicePaise: Long,
  val snapshotTotalInvoicePendingPaise: Long,
  val snapshotNetOutstandingPaise: Long,
  val renderedMessage: String,
  val whatsappPackageUsed: String? = null,
  val failureReason: String? = null,
  val awaitingUserConfirmation: Boolean = false,
  val createdAt: Long = System.currentTimeMillis(),
  val whatsappOpenedAt: Long? = null,
  val confirmedSentAt: Long? = null,
  val updatedAt: Long = System.currentTimeMillis()
)

data class CustomerPaymentReminderInvoice(
  val id: Long = 0,
  val reminderId: Long,
  val salesInvoiceId: Long,
  val invoiceDateSnapshot: Long,
  val totalAmountPaiseSnapshot: Long,
  val pendingAmountPaiseSnapshot: Long
)

// ==========================================
// Phase 3 Domain Models
// ==========================================

data class SalesReturn(
  val id: Long = 0,
  val returnNumber: String,
  val salesInvoiceId: Long,
  val customerId: Long,
  val returnDate: Long,
  val totalReturnAmountPaise: Long,
  val settlementType: ReturnSettlementType,
  val creditNoteId: Long? = null,
  val refundRecordId: Long? = null,
  val subledgerEntryId: Long? = null,
  val reason: String,
  val notes: String = "",
  val idempotencyKey: String? = null,
  val createdAt: Long = System.currentTimeMillis()
)

data class SalesReturnItem(
  val id: Long = 0,
  val salesReturnId: Long = 0,
  val originalSalesInvoiceItemId: Long? = null,
  val productId: Long,
  val batchId: Long,
  val productNameSnapshot: String = "",
  val quantityPieces: Int,
  val unitPricePaise: Long,
  val lineTotalPaise: Long,
  val condition: ReturnCondition,
  val restoredToSaleableStock: Boolean
)

data class PurchaseReturn(
  val id: Long = 0,
  val returnNumber: String,
  val purchaseInvoiceId: Long,
  val supplierId: Long,
  val returnDate: Long,
  val totalReturnAmountPaise: Long,
  val settlementType: ReturnSettlementType,
  val debitNoteId: Long? = null,
  val refundRecordId: Long? = null,
  val subledgerEntryId: Long? = null,
  val reason: String,
  val notes: String = "",
  val idempotencyKey: String? = null,
  val createdAt: Long = System.currentTimeMillis()
)

data class PurchaseReturnItem(
  val id: Long = 0,
  val purchaseReturnId: Long = 0,
  val originalPurchaseInvoiceItemId: Long? = null,
  val productId: Long,
  val batchId: Long,
  val productNameSnapshot: String = "",
  val batchNumberSnapshot: String = "",
  val quantityPieces: Int,
  val costPerPiecePaise: Long,
  val lineTotalPaise: Long,
  val condition: ReturnCondition
)

data class CreditDebitNote(
  val id: Long = 0,
  val noteNumber: String,
  val noteType: CreditDebitNoteType,
  val partyType: PartyType,
  val partyId: Long,
  val linkedInvoiceId: Long? = null,
  val linkedReturnId: Long? = null,
  val subledgerEntryId: Long? = null,
  val amountPaise: Long,
  val appliedAmountPaise: Long = 0L,
  val status: NoteStatus = NoteStatus.OPEN,
  val issueDate: Long,
  val reason: String,
  val notes: String = "",
  val idempotencyKey: String? = null,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis()
) {
  val remainingAmountPaise: Long get() = (amountPaise - appliedAmountPaise).coerceAtLeast(0L)
}

data class RefundRecord(
  val id: Long = 0,
  val partyType: PartyType,
  val partyId: Long,
  val sourceType: RefundSourceType,
  val sourceId: Long,
  val amountPaise: Long,
  val paymentMethod: PaymentMethod,
  val executionStatus: RefundExecutionStatus = RefundExecutionStatus.RECORDED_PENDING_PAYOUT,
  val reason: String,
  val notes: String = "",
  val referenceNumber: String = "",
  val subledgerEntryId: Long? = null,
  val recordedAt: Long = System.currentTimeMillis(),
  val executedAt: Long? = null,
  val idempotencyKey: String? = null
)

data class FinancialAuditLog(
  val id: Long = 0,
  val operationType: AuditOperationType,
  val entityType: String,
  val entityId: Long,
  val relatedPartyType: PartyType? = null,
  val relatedPartyId: Long? = null,
  val actorLabel: String = "Owner",
  val reason: String,
  val beforeJson: String = "",
  val afterJson: String = "",
  val operationReference: String = "",
  val createdAt: Long = System.currentTimeMillis()
)

data class SalesInvoiceDetail(
  val invoice: SalesInvoice,
  val customer: Customer?,
  val items: List<SalesInvoiceItem>,
  val allocations: List<PaymentAllocation>,
  val returns: List<SalesReturn>,
  val notesList: List<CreditDebitNote>,
  val refunds: List<RefundRecord>,
  val auditLogs: List<FinancialAuditLog>,
  val returnedQtyByProductId: Map<Long, Int> = emptyMap(),
  val totalReturnedAmountPaise: Long = 0L
)

data class PurchaseInvoiceDetail(
  val invoice: PurchaseInvoice,
  val supplier: Supplier?,
  val items: List<PurchaseInvoiceItem>,
  val batches: List<ProductBatch>,
  val soldQtyByBatchNumber: Map<String, Int>,
  val allocations: List<PaymentAllocation>,
  val returns: List<PurchaseReturn>,
  val notesList: List<CreditDebitNote>,
  val refunds: List<RefundRecord>,
  val auditLogs: List<FinancialAuditLog>,
  val returnedQtyByProductId: Map<Long, Int> = emptyMap(),
  val totalReturnedAmountPaise: Long = 0L
)


