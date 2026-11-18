package com.example.data.remote

import com.example.data.local.entity.*
import com.example.domain.model.*

/**
 * Part 2 of IceTrack Firestore 1:1 Mappers covering financial ledgers, returns, notes,
 * manual refunds, immutable audit logs, expenses, reminders, and settings.
 */
object FirestoreFinanceMappers {

  private inline fun <reified E : Enum<E>> safeEnum(raw: Any?, default: E): E {
    val name = raw as? String ?: return default
    return enumValues<E>().firstOrNull { it.name == name } ?: default
  }

  private inline fun <reified E : Enum<E>> safeEnumOrNull(raw: Any?): E? {
    val name = raw as? String ?: return null
    return enumValues<E>().firstOrNull { it.name == name }
  }

  private fun numLong(data: Map<String, Any?>, key: String, default: Long = 0L): Long =
    (data[key] as? Number)?.toLong() ?: default

  private fun numLongOrNull(data: Map<String, Any?>, key: String): Long? =
    (data[key] as? Number)?.toLong()

  private fun numInt(data: Map<String, Any?>, key: String, default: Int = 0): Int =
    (data[key] as? Number)?.toInt() ?: default

  private fun boolVal(data: Map<String, Any?>, key: String, default: Boolean = false): Boolean =
    (data[key] as? Boolean) ?: default

  private fun strVal(data: Map<String, Any?>, key: String, default: String = ""): String =
    (data[key] as? String) ?: default

  fun toSubledgerFields(uid: String, s: SubledgerEntryEntity): Pair<Map<String, Any>, String> =
    FirestoreSyncMappers.businessFieldsWithHash(
      linkedMapOf(
        "userId" to uid,
        "localId" to s.id,
        "partyType" to s.partyType.name,
        "partyLocalId" to s.partyId,
        "entryType" to s.entryType.name,
        "referenceType" to s.referenceType.ifBlank { "LEDGER" }.take(80),
        "referenceId" to s.referenceId,
        "debitPaise" to s.debitPaise.coerceAtLeast(0L),
        "creditPaise" to s.creditPaise.coerceAtLeast(0L),
        "entryDateEpochMs" to s.entryDate.coerceAtLeast(0L),
        "notes" to s.notes.take(1000),
        "reversed" to s.isReversed,
        "operationRef" to s.operationRef.take(160),
        "reversedAtEpochMs" to s.reversedAt?.coerceAtLeast(0L),
        "reversalReason" to s.reversalReason?.take(500),
        "localCreatedAtMs" to s.createdAt.coerceAtLeast(0L)
      )
    )

  fun fromSubledgerDoc(data: Map<String, Any?>): SubledgerEntryEntity = SubledgerEntryEntity(
    id = numLong(data, "localId"),
    partyType = safeEnum(data["partyType"], PartyType.CUSTOMER),
    partyId = numLong(data, "partyLocalId"),
    entryType = safeEnum(data["entryType"], SubledgerEntryType.ADJUSTMENT),
    referenceType = strVal(data, "referenceType"),
    referenceId = numLong(data, "referenceId"),
    debitPaise = numLong(data, "debitPaise"),
    creditPaise = numLong(data, "creditPaise"),
    entryDate = numLong(data, "entryDateEpochMs"),
    createdAt = numLong(data, "localCreatedAtMs"),
    notes = strVal(data, "notes"),
    isReversed = boolVal(data, "reversed", false),
    operationRef = strVal(data, "operationRef"),
    reversedAt = numLongOrNull(data, "reversedAtEpochMs"),
    reversalReason = data["reversalReason"] as? String
  )

  fun toPaymentAllocationFields(uid: String, a: PaymentAllocationEntity): Pair<Map<String, Any>, String> =
    FirestoreSyncMappers.businessFieldsWithHash(
      linkedMapOf(
        "userId" to uid,
        "localId" to a.id,
        "paymentLocalId" to a.paymentId,
        "partyType" to a.partyType.name,
        "partyLocalId" to a.partyId,
        "allocationType" to a.allocationType.name,
        "invoiceLocalId" to a.invoiceId?.coerceAtLeast(0L),
        "allocatedAmountPaise" to a.allocatedAmountPaise.coerceAtLeast(0L),
        "allocatedAtEpochMs" to a.allocatedAt.coerceAtLeast(0L),
        "notes" to a.notes.take(1000),
        "reversed" to a.isReversed,
        "fromAdvanceConsumption" to a.isFromAdvanceConsumption,
        "reversedAtEpochMs" to a.reversedAt?.coerceAtLeast(0L),
        "reversalReason" to a.reversalReason?.take(500)
      )
    )

  fun fromPaymentAllocationDoc(data: Map<String, Any?>): PaymentAllocationEntity = PaymentAllocationEntity(
    id = numLong(data, "localId"),
    paymentId = numLong(data, "paymentLocalId"),
    partyType = safeEnum(data["partyType"], PartyType.CUSTOMER),
    partyId = numLong(data, "partyLocalId"),
    allocationType = safeEnum(data["allocationType"], PaymentAllocationType.INVOICE),
    invoiceId = numLongOrNull(data, "invoiceLocalId"),
    allocatedAmountPaise = numLong(data, "allocatedAmountPaise"),
    allocatedAt = numLong(data, "allocatedAtEpochMs"),
    notes = strVal(data, "notes"),
    isReversed = boolVal(data, "reversed", false),
    isFromAdvanceConsumption = boolVal(data, "fromAdvanceConsumption", false),
    reversedAt = numLongOrNull(data, "reversedAtEpochMs"),
    reversalReason = data["reversalReason"] as? String
  )

  fun toSalesReturnFields(uid: String, r: SalesReturnEntity): Pair<Map<String, Any>, String> =
    FirestoreSyncMappers.businessFieldsWithHash(
      linkedMapOf(
        "userId" to uid,
        "localId" to r.id,
        "returnNumber" to r.returnNumber.ifBlank { "SR-${r.id}" }.take(80),
        "salesInvoiceLocalId" to r.salesInvoiceId,
        "customerLocalId" to r.customerId,
        "returnDateEpochMs" to r.returnDate.coerceAtLeast(0L),
        "totalReturnAmountPaise" to r.totalReturnAmountPaise.coerceAtLeast(0L),
        "settlementType" to r.settlementType.name,
        "creditNoteLocalId" to r.creditNoteId?.coerceAtLeast(0L),
        "refundRecordLocalId" to r.refundRecordId?.coerceAtLeast(0L),
        "subledgerEntryLocalId" to r.subledgerEntryId?.coerceAtLeast(0L),
        "reason" to r.reason.ifBlank { "Sales return recorded" }.take(500),
        "notes" to r.notes.take(1000),
        "idempotencyKey" to r.idempotencyKey?.takeIf { it.isNotBlank() }?.take(160),
        "localCreatedAtMs" to r.createdAt.coerceAtLeast(0L)
      )
    )

  fun fromSalesReturnDoc(data: Map<String, Any?>): SalesReturnEntity = SalesReturnEntity(
    id = numLong(data, "localId"),
    returnNumber = strVal(data, "returnNumber"),
    salesInvoiceId = numLong(data, "salesInvoiceLocalId"),
    customerId = numLong(data, "customerLocalId"),
    returnDate = numLong(data, "returnDateEpochMs"),
    totalReturnAmountPaise = numLong(data, "totalReturnAmountPaise"),
    settlementType = safeEnum(data["settlementType"], ReturnSettlementType.CREDIT_NOTE),
    creditNoteId = numLongOrNull(data, "creditNoteLocalId"),
    refundRecordId = numLongOrNull(data, "refundRecordLocalId"),
    subledgerEntryId = numLongOrNull(data, "subledgerEntryLocalId"),
    reason = strVal(data, "reason"),
    notes = strVal(data, "notes"),
    idempotencyKey = data["idempotencyKey"] as? String,
    createdAt = numLong(data, "localCreatedAtMs")
  )

  fun toSalesReturnItemFields(uid: String, item: SalesReturnItemEntity): Pair<Map<String, Any>, String> =
    FirestoreSyncMappers.businessFieldsWithHash(
      linkedMapOf(
        "userId" to uid,
        "localId" to item.id,
        "salesReturnLocalId" to item.salesReturnId,
        "originalSalesInvoiceItemLocalId" to item.originalSalesInvoiceItemId?.coerceAtLeast(0L),
        "productLocalId" to item.productId,
        "batchLocalId" to item.batchId,
        "productNameSnapshot" to item.productNameSnapshot.take(200),
        "quantityPieces" to item.quantityPieces.coerceAtLeast(1),
        "unitPricePaise" to item.unitPricePaise.coerceAtLeast(0L),
        "lineTotalPaise" to item.lineTotalPaise.coerceAtLeast(0L),
        "condition" to item.condition.name,
        "restoredToSaleableStock" to item.restoredToSaleableStock
      )
    )

  fun fromSalesReturnItemDoc(data: Map<String, Any?>): SalesReturnItemEntity = SalesReturnItemEntity(
    id = numLong(data, "localId"),
    salesReturnId = numLong(data, "salesReturnLocalId"),
    originalSalesInvoiceItemId = numLongOrNull(data, "originalSalesInvoiceItemLocalId"),
    productId = numLong(data, "productLocalId"),
    batchId = numLong(data, "batchLocalId"),
    productNameSnapshot = strVal(data, "productNameSnapshot"),
    quantityPieces = numInt(data, "quantityPieces", 1),
    unitPricePaise = numLong(data, "unitPricePaise"),
    lineTotalPaise = numLong(data, "lineTotalPaise"),
    condition = safeEnum(data["condition"], ReturnCondition.RESELLABLE),
    restoredToSaleableStock = boolVal(data, "restoredToSaleableStock", false)
  )

  fun toPurchaseReturnFields(uid: String, r: PurchaseReturnEntity): Pair<Map<String, Any>, String> =
    FirestoreSyncMappers.businessFieldsWithHash(
      linkedMapOf(
        "userId" to uid,
        "localId" to r.id,
        "returnNumber" to r.returnNumber.ifBlank { "PR-${r.id}" }.take(80),
        "purchaseInvoiceLocalId" to r.purchaseInvoiceId,
        "supplierLocalId" to r.supplierId,
        "returnDateEpochMs" to r.returnDate.coerceAtLeast(0L),
        "totalReturnAmountPaise" to r.totalReturnAmountPaise.coerceAtLeast(0L),
        "settlementType" to r.settlementType.name,
        "debitNoteLocalId" to r.debitNoteId?.coerceAtLeast(0L),
        "refundRecordLocalId" to r.refundRecordId?.coerceAtLeast(0L),
        "subledgerEntryLocalId" to r.subledgerEntryId?.coerceAtLeast(0L),
        "reason" to r.reason.ifBlank { "Purchase return recorded" }.take(500),
        "notes" to r.notes.take(1000),
        "idempotencyKey" to r.idempotencyKey?.takeIf { it.isNotBlank() }?.take(160),
        "localCreatedAtMs" to r.createdAt.coerceAtLeast(0L)
      )
    )

  fun fromPurchaseReturnDoc(data: Map<String, Any?>): PurchaseReturnEntity = PurchaseReturnEntity(
    id = numLong(data, "localId"),
    returnNumber = strVal(data, "returnNumber"),
    purchaseInvoiceId = numLong(data, "purchaseInvoiceLocalId"),
    supplierId = numLong(data, "supplierLocalId"),
    returnDate = numLong(data, "returnDateEpochMs"),
    totalReturnAmountPaise = numLong(data, "totalReturnAmountPaise"),
    settlementType = safeEnum(data["settlementType"], ReturnSettlementType.DEBIT_NOTE),
    debitNoteId = numLongOrNull(data, "debitNoteLocalId"),
    refundRecordId = numLongOrNull(data, "refundRecordLocalId"),
    subledgerEntryId = numLongOrNull(data, "subledgerEntryLocalId"),
    reason = strVal(data, "reason"),
    notes = strVal(data, "notes"),
    idempotencyKey = data["idempotencyKey"] as? String,
    createdAt = numLong(data, "localCreatedAtMs")
  )

  fun toPurchaseReturnItemFields(uid: String, item: PurchaseReturnItemEntity): Pair<Map<String, Any>, String> =
    FirestoreSyncMappers.businessFieldsWithHash(
      linkedMapOf(
        "userId" to uid,
        "localId" to item.id,
        "purchaseReturnLocalId" to item.purchaseReturnId,
        "originalPurchaseInvoiceItemLocalId" to item.originalPurchaseInvoiceItemId?.coerceAtLeast(0L),
        "productLocalId" to item.productId,
        "batchLocalId" to item.batchId,
        "productNameSnapshot" to item.productNameSnapshot.take(200),
        "batchNumberSnapshot" to item.batchNumberSnapshot.take(100),
        "quantityPieces" to item.quantityPieces.coerceAtLeast(1),
        "costPerPiecePaise" to item.costPerPiecePaise.coerceAtLeast(0L),
        "lineTotalPaise" to item.lineTotalPaise.coerceAtLeast(0L),
        "condition" to item.condition.name
      )
    )

  fun fromPurchaseReturnItemDoc(data: Map<String, Any?>): PurchaseReturnItemEntity = PurchaseReturnItemEntity(
    id = numLong(data, "localId"),
    purchaseReturnId = numLong(data, "purchaseReturnLocalId"),
    originalPurchaseInvoiceItemId = numLongOrNull(data, "originalPurchaseInvoiceItemLocalId"),
    productId = numLong(data, "productLocalId"),
    batchId = numLong(data, "batchLocalId"),
    productNameSnapshot = strVal(data, "productNameSnapshot"),
    batchNumberSnapshot = strVal(data, "batchNumberSnapshot"),
    quantityPieces = numInt(data, "quantityPieces", 1),
    costPerPiecePaise = numLong(data, "costPerPiecePaise"),
    lineTotalPaise = numLong(data, "lineTotalPaise"),
    condition = safeEnum(data["condition"], ReturnCondition.RESELLABLE)
  )

  fun toCreditDebitNoteFields(uid: String, n: CreditDebitNoteEntity): Pair<Map<String, Any>, String> =
    FirestoreSyncMappers.businessFieldsWithHash(
      linkedMapOf(
        "userId" to uid,
        "localId" to n.id,
        "noteNumber" to n.noteNumber.ifBlank { "NOTE-${n.id}" }.take(80),
        "noteType" to n.noteType.name,
        "partyType" to n.partyType.name,
        "partyLocalId" to n.partyId,
        "linkedInvoiceLocalId" to n.linkedInvoiceId?.coerceAtLeast(0L),
        "linkedReturnLocalId" to n.linkedReturnId?.coerceAtLeast(0L),
        "subledgerEntryLocalId" to n.subledgerEntryId?.coerceAtLeast(0L),
        "amountPaise" to n.amountPaise.coerceAtLeast(1L),
        "appliedAmountPaise" to n.appliedAmountPaise.coerceAtLeast(0L),
        "status" to n.status.name,
        "issueDateEpochMs" to n.issueDate.coerceAtLeast(0L),
        "reason" to n.reason.ifBlank { "Adjustment note" }.take(500),
        "notes" to n.notes.take(1000),
        "idempotencyKey" to n.idempotencyKey?.takeIf { it.isNotBlank() }?.take(160),
        "localCreatedAtMs" to n.createdAt.coerceAtLeast(0L),
        "localUpdatedAtMs" to maxOf(n.updatedAt, n.createdAt).coerceAtLeast(0L)
      )
    )

  fun fromCreditDebitNoteDoc(data: Map<String, Any?>): CreditDebitNoteEntity = CreditDebitNoteEntity(
    id = numLong(data, "localId"),
    noteNumber = strVal(data, "noteNumber"),
    noteType = safeEnum(data["noteType"], CreditDebitNoteType.CREDIT_NOTE),
    partyType = safeEnum(data["partyType"], PartyType.CUSTOMER),
    partyId = numLong(data, "partyLocalId"),
    linkedInvoiceId = numLongOrNull(data, "linkedInvoiceLocalId"),
    linkedReturnId = numLongOrNull(data, "linkedReturnLocalId"),
    subledgerEntryId = numLongOrNull(data, "subledgerEntryLocalId"),
    amountPaise = numLong(data, "amountPaise"),
    appliedAmountPaise = numLong(data, "appliedAmountPaise"),
    status = safeEnum(data["status"], NoteStatus.OPEN),
    issueDate = numLong(data, "issueDateEpochMs"),
    reason = strVal(data, "reason"),
    notes = strVal(data, "notes"),
    idempotencyKey = data["idempotencyKey"] as? String,
    createdAt = numLong(data, "localCreatedAtMs"),
    updatedAt = numLong(data, "localUpdatedAtMs")
  )

  fun toRefundRecordFields(uid: String, r: RefundRecordEntity): Pair<Map<String, Any>, String> =
    FirestoreSyncMappers.businessFieldsWithHash(
      linkedMapOf(
        "userId" to uid,
        "localId" to r.id,
        "partyType" to r.partyType.name,
        "partyLocalId" to r.partyId,
        "sourceType" to r.sourceType.name,
        "sourceId" to r.sourceId,
        "amountPaise" to r.amountPaise.coerceAtLeast(1L),
        "paymentMethod" to r.paymentMethod.name,
        "executionStatus" to r.executionStatus.name,
        "reason" to r.reason.ifBlank { "Manual refund recorded" }.take(500),
        "notes" to r.notes.take(1000),
        "referenceNumber" to r.referenceNumber.take(120),
        "subledgerEntryLocalId" to r.subledgerEntryId?.coerceAtLeast(0L),
        "recordedAtEpochMs" to r.recordedAt.coerceAtLeast(0L),
        "executedAtEpochMs" to r.executedAt?.coerceAtLeast(0L),
        "idempotencyKey" to r.idempotencyKey?.takeIf { it.isNotBlank() }?.take(160)
      )
    )

  fun fromRefundRecordDoc(data: Map<String, Any?>): RefundRecordEntity = RefundRecordEntity(
    id = numLong(data, "localId"),
    partyType = safeEnum(data["partyType"], PartyType.CUSTOMER),
    partyId = numLong(data, "partyLocalId"),
    sourceType = safeEnum(data["sourceType"], RefundSourceType.SALES_RETURN),
    sourceId = numLong(data, "sourceId"),
    amountPaise = numLong(data, "amountPaise"),
    paymentMethod = safeEnum(data["paymentMethod"], PaymentMethod.CASH),
    executionStatus = safeEnum(data["executionStatus"], RefundExecutionStatus.RECORDED_PENDING_PAYOUT),
    reason = strVal(data, "reason"),
    notes = strVal(data, "notes"),
    referenceNumber = strVal(data, "referenceNumber"),
    subledgerEntryId = numLongOrNull(data, "subledgerEntryLocalId"),
    recordedAt = numLong(data, "recordedAtEpochMs"),
    executedAt = numLongOrNull(data, "executedAtEpochMs"),
    idempotencyKey = data["idempotencyKey"] as? String
  )

  fun toAuditLogFields(uid: String, a: FinancialAuditLogEntity): Pair<Map<String, Any>, String> =
    FirestoreSyncMappers.businessFieldsWithHash(
      linkedMapOf(
        "userId" to uid,
        "localId" to a.id,
        "operationType" to a.operationType.name,
        "entityType" to a.entityType.ifBlank { "RECORD" }.take(80),
        "entityId" to a.entityId,
        "relatedPartyType" to a.relatedPartyType?.name,
        "relatedPartyId" to a.relatedPartyId?.coerceAtLeast(0L),
        "actorLabel" to a.actorLabel.ifBlank { "Owner" }.take(120),
        "reason" to a.reason.ifBlank { "Audit entry recorded" }.take(500),
        "beforeJson" to a.beforeJson.take(16000),
        "afterJson" to a.afterJson.take(16000),
        "operationReference" to a.operationReference.take(160),
        "localCreatedAtMs" to a.createdAt.coerceAtLeast(0L)
      )
    )

  fun fromAuditLogDoc(data: Map<String, Any?>): FinancialAuditLogEntity = FinancialAuditLogEntity(
    id = numLong(data, "localId"),
    operationType = safeEnum(data["operationType"], AuditOperationType.FINANCIAL_ADJUSTMENT),
    entityType = strVal(data, "entityType"),
    entityId = numLong(data, "entityId"),
    relatedPartyType = safeEnumOrNull<PartyType>(data["relatedPartyType"]),
    relatedPartyId = numLongOrNull(data, "relatedPartyId"),
    actorLabel = strVal(data, "actorLabel", "Owner"),
    reason = strVal(data, "reason"),
    beforeJson = strVal(data, "beforeJson"),
    afterJson = strVal(data, "afterJson"),
    operationReference = strVal(data, "operationReference"),
    createdAt = numLong(data, "localCreatedAtMs")
  )

  fun toExpenseFields(uid: String, e: ExpenseEntity): Pair<Map<String, Any>, String> =
    FirestoreSyncMappers.businessFieldsWithHash(
      linkedMapOf(
        "userId" to uid,
        "localId" to e.id,
        "category" to e.category.name,
        "amountPaise" to e.amountPaise.coerceAtLeast(1L),
        "expenseDateEpochMs" to e.expenseDate.coerceAtLeast(0L),
        "paymentMethod" to e.paymentMethod.name,
        "referenceNumber" to e.referenceNumber.take(120),
        "notes" to e.notes.take(1000),
        "localCreatedAtMs" to e.createdAt.coerceAtLeast(0L),
        "deletedAtEpochMs" to e.deletedAt?.coerceAtLeast(0L)
      )
    )

  fun fromExpenseDoc(data: Map<String, Any?>): ExpenseEntity = ExpenseEntity(
    id = numLong(data, "localId"),
    category = safeEnum(data["category"], ExpenseCategory.MISCELLANEOUS),
    amountPaise = numLong(data, "amountPaise"),
    expenseDate = numLong(data, "expenseDateEpochMs"),
    paymentMethod = safeEnum(data["paymentMethod"], PaymentMethod.CASH),
    referenceNumber = strVal(data, "referenceNumber"),
    notes = strVal(data, "notes"),
    createdAt = numLong(data, "localCreatedAtMs"),
    deletedAt = numLongOrNull(data, "deletedAtEpochMs")
  )

  fun toPaymentReminderFields(uid: String, r: CustomerPaymentReminderEntity): Pair<Map<String, Any>, String> =
    FirestoreSyncMappers.businessFieldsWithHash(
      linkedMapOf(
        "userId" to uid,
        "localId" to r.id,
        "customerLocalId" to r.customerId,
        "targetPhoneE164" to r.targetPhoneE164.ifBlank { "+910000000000" }.take(30),
        "dueKind" to r.dueKind.name,
        "status" to r.status.name,
        "snapshotPendingInvoiceCount" to r.snapshotPendingInvoiceCount.coerceAtLeast(0),
        "snapshotTotalInvoicePaise" to r.snapshotTotalInvoicePaise.coerceAtLeast(0L),
        "snapshotTotalInvoicePendingPaise" to r.snapshotTotalInvoicePendingPaise.coerceAtLeast(0L),
        "snapshotNetOutstandingPaise" to r.snapshotNetOutstandingPaise.coerceAtLeast(0L),
        "renderedMessage" to r.renderedMessage.ifBlank { "Payment reminder" }.take(4000),
        "whatsappPackageUsed" to r.whatsappPackageUsed?.take(120),
        "failureReason" to r.failureReason?.take(500),
        "awaitingUserConfirmation" to r.awaitingUserConfirmation,
        "localCreatedAtMs" to r.createdAt.coerceAtLeast(0L),
        "whatsappOpenedAtEpochMs" to r.whatsappOpenedAt?.coerceAtLeast(0L),
        "confirmedSentAtEpochMs" to r.confirmedSentAt?.coerceAtLeast(0L),
        "localUpdatedAtMs" to maxOf(r.updatedAt, r.createdAt).coerceAtLeast(0L)
      )
    )

  fun fromPaymentReminderDoc(data: Map<String, Any?>): CustomerPaymentReminderEntity = CustomerPaymentReminderEntity(
    id = numLong(data, "localId"),
    customerId = numLong(data, "customerLocalId"),
    targetPhoneE164 = strVal(data, "targetPhoneE164"),
    dueKind = safeEnum(data["dueKind"], ReminderDueKind.INVOICE_BASED),
    status = safeEnum(data["status"], ReminderStatus.DRAFT),
    snapshotPendingInvoiceCount = numInt(data, "snapshotPendingInvoiceCount"),
    snapshotTotalInvoicePaise = numLong(data, "snapshotTotalInvoicePaise"),
    snapshotTotalInvoicePendingPaise = numLong(data, "snapshotTotalInvoicePendingPaise"),
    snapshotNetOutstandingPaise = numLong(data, "snapshotNetOutstandingPaise"),
    renderedMessage = strVal(data, "renderedMessage"),
    whatsappPackageUsed = data["whatsappPackageUsed"] as? String,
    failureReason = data["failureReason"] as? String,
    awaitingUserConfirmation = boolVal(data, "awaitingUserConfirmation", false),
    createdAt = numLong(data, "localCreatedAtMs"),
    whatsappOpenedAt = numLongOrNull(data, "whatsappOpenedAtEpochMs"),
    confirmedSentAt = numLongOrNull(data, "confirmedSentAtEpochMs"),
    updatedAt = numLong(data, "localUpdatedAtMs")
  )

  fun toPaymentReminderInvoiceFields(uid: String, item: CustomerPaymentReminderInvoiceEntity): Pair<Map<String, Any>, String> =
    FirestoreSyncMappers.businessFieldsWithHash(
      linkedMapOf(
        "userId" to uid,
        "localId" to item.id,
        "reminderLocalId" to item.reminderId,
        "salesInvoiceLocalId" to item.salesInvoiceId,
        "invoiceDateSnapshotMs" to item.invoiceDateSnapshot.coerceAtLeast(0L),
        "totalAmountPaiseSnapshot" to item.totalAmountPaiseSnapshot.coerceAtLeast(0L),
        "pendingAmountPaiseSnapshot" to item.pendingAmountPaiseSnapshot.coerceAtLeast(0L)
      )
    )

  fun fromPaymentReminderInvoiceDoc(data: Map<String, Any?>): CustomerPaymentReminderInvoiceEntity = CustomerPaymentReminderInvoiceEntity(
    id = numLong(data, "localId"),
    reminderId = numLong(data, "reminderLocalId"),
    salesInvoiceId = numLong(data, "salesInvoiceLocalId"),
    invoiceDateSnapshot = numLong(data, "invoiceDateSnapshotMs"),
    totalAmountPaiseSnapshot = numLong(data, "totalAmountPaiseSnapshot"),
    pendingAmountPaiseSnapshot = numLong(data, "pendingAmountPaiseSnapshot")
  )

  fun toAppSettingFields(uid: String, setting: AppSettingEntity, updatedAtMs: Long): Pair<Map<String, Any>, String> =
    FirestoreSyncMappers.businessFieldsWithHash(
      linkedMapOf(
        "userId" to uid,
        "settingKey" to setting.key.ifBlank { "default" }.take(100),
        "settingValue" to setting.value.take(2000),
        "localUpdatedAtMs" to updatedAtMs.coerceAtLeast(0L)
      )
    )

  fun fromAppSettingDoc(data: Map<String, Any?>): AppSettingEntity = AppSettingEntity(
    key = strVal(data, "settingKey"),
    value = strVal(data, "settingValue")
  )
}
