package com.example.data.sync

import androidx.room.withTransaction
import com.example.data.local.IceTrackDatabase
import com.example.data.local.entity.*
import com.example.data.remote.FirestoreFinanceMappers
import com.example.data.remote.FirestoreSyncMappers
import com.example.domain.engine.CloudSyncConflictEngine

/**
 * Applies pulled Cloud Firestore snapshots cleanly into Room V5 inside an atomic transaction
 * respecting SQLite foreign-key parent-to-child order without resetting balances or wiping
 * unrelated local records during incremental pulls.
 */
object CloudRestoreApplicator {

  data class RemoteSnapshotBundle(
    val products: List<Pair<ProductEntity, ProductUnitEntity>> = emptyList(),
    val suppliers: List<SupplierEntity> = emptyList(),
    val customers: List<CustomerEntity> = emptyList(),
    val customerPrices: List<CustomerProductPriceEntity> = emptyList(),
    val productSuppliers: List<ProductSupplierEntity> = emptyList(),
    val batches: List<ProductBatchEntity> = emptyList(),
    val stockMovements: List<StockMovementEntity> = emptyList(),
    val purchaseInvoices: List<PurchaseInvoiceEntity> = emptyList(),
    val purchaseInvoiceItems: List<PurchaseInvoiceItemEntity> = emptyList(),
    val salesInvoices: List<SalesInvoiceEntity> = emptyList(),
    val salesInvoiceItems: List<SalesInvoiceItemEntity> = emptyList(),
    val salesReturns: List<SalesReturnEntity> = emptyList(),
    val salesReturnItems: List<SalesReturnItemEntity> = emptyList(),
    val purchaseReturns: List<PurchaseReturnEntity> = emptyList(),
    val purchaseReturnItems: List<PurchaseReturnItemEntity> = emptyList(),
    val subledgerEntries: List<SubledgerEntryEntity> = emptyList(),
    val paymentAllocations: List<PaymentAllocationEntity> = emptyList(),
    val creditDebitNotes: List<CreditDebitNoteEntity> = emptyList(),
    val refundRecords: List<RefundRecordEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val paymentReminders: List<CustomerPaymentReminderEntity> = emptyList(),
    val paymentReminderInvoices: List<CustomerPaymentReminderInvoiceEntity> = emptyList(),
    val appSettings: List<AppSettingEntity> = emptyList(),
    val auditLogs: List<FinancialAuditLogEntity> = emptyList()
  ) {
    fun totalCount(): Int =
      products.size + suppliers.size + customers.size + customerPrices.size +
        productSuppliers.size + batches.size + stockMovements.size +
        purchaseInvoices.size + purchaseInvoiceItems.size + salesInvoices.size +
        salesInvoiceItems.size + salesReturns.size + salesReturnItems.size +
        purchaseReturns.size + purchaseReturnItems.size + subledgerEntries.size +
        paymentAllocations.size + creditDebitNotes.size + refundRecords.size +
        expenses.size + paymentReminders.size + paymentReminderInvoices.size +
        appSettings.size + auditLogs.size
  }

  suspend fun applyBundleTransactionally(
    db: IceTrackDatabase,
    bundle: RemoteSnapshotBundle
  ) {
    if (bundle.totalCount() == 0) return

    db.withTransaction {
      // 1. Master parents (Products + Units, Suppliers, Customers, Settings, Expenses)
      bundle.products.forEach { (product, unit) ->
        val existing = db.productDao().getProductSync(product.id)
        if (existing == null) db.productDao().insert(product) else db.productDao().update(product)
        db.productUnitDao().insertOrUpdate(unit)
      }
      bundle.suppliers.forEach { s ->
        val existing = db.supplierDao().getSupplierSync(s.id)
        if (existing == null) db.supplierDao().insert(s) else db.supplierDao().update(s)
      }
      bundle.customers.forEach { c ->
        val existing = db.customerDao().getCustomerSync(c.id)
        if (existing == null) db.customerDao().insert(c) else db.customerDao().update(c)
      }
      bundle.appSettings.forEach { db.appSettingDao().set(it) }

      val existingExpenseIds = db.expenseDao().getAllIncludingDeletedSync().map { it.id }.toSet()
      bundle.expenses.forEach { e ->
        if (e.id in existingExpenseIds) db.expenseDao().update(e) else db.expenseDao().insert(e)
      }

      // 2. Product links & prices
      bundle.customerPrices.forEach { db.customerProductPriceDao().insertOrUpdate(it) }
      bundle.productSuppliers.forEach { db.productSupplierDao().insertOrUpdate(it) }

      // 3. Batches & Immutable Stock Movements
      bundle.batches.forEach { b ->
        val existing = db.productBatchDao().getById(b.id)
        if (existing == null) db.productBatchDao().insert(b) else db.productBatchDao().update(b)
      }
      val existingMovementIds = db.stockMovementDao().getAllSync().map { it.id }.toSet()
      bundle.stockMovements.forEach { m ->
        if (m.id !in existingMovementIds) db.stockMovementDao().insert(m)
      }

      // 4. Invoices & Items (preserve parent before items)
      bundle.purchaseInvoices.forEach { inv ->
        val existing = db.purchaseInvoiceDao().getById(inv.id)
        if (existing == null) db.purchaseInvoiceDao().insert(inv) else db.purchaseInvoiceDao().update(inv)
      }
      val existingPItemIds = db.purchaseInvoiceItemDao().getAllItemsSync().map { it.id }.toSet()
      val newPItems = bundle.purchaseInvoiceItems.filter { it.id !in existingPItemIds }
      if (newPItems.isNotEmpty()) db.purchaseInvoiceItemDao().insertAll(newPItems)

      bundle.salesInvoices.forEach { inv ->
        val existing = db.salesInvoiceDao().getById(inv.id)
        if (existing == null) db.salesInvoiceDao().insert(inv) else db.salesInvoiceDao().update(inv)
      }
      val existingSItemIds = db.salesInvoiceItemDao().getAllItemsSync().map { it.id }.toSet()
      val newSItems = bundle.salesInvoiceItems.filter { it.id !in existingSItemIds }
      if (newSItems.isNotEmpty()) db.salesInvoiceItemDao().insertAll(newSItems)

      // 5. Returns & Return Items
      bundle.salesReturns.forEach { r ->
        if (db.salesReturnDao().getByIdSync(r.id) == null) db.salesReturnDao().insertReturn(r)
      }
      val existingSrItemIds = db.salesReturnDao().getAllItemsSync().map { it.id }.toSet()
      val newSrItems = bundle.salesReturnItems.filter { it.id !in existingSrItemIds }
      if (newSrItems.isNotEmpty()) db.salesReturnDao().insertItems(newSrItems)

      bundle.purchaseReturns.forEach { r ->
        if (db.purchaseReturnDao().getByIdSync(r.id) == null) db.purchaseReturnDao().insertReturn(r)
      }
      val existingPrItemIds = db.purchaseReturnDao().getAllItemsSync().map { it.id }.toSet()
      val newPrItems = bundle.purchaseReturnItems.filter { it.id !in existingPrItemIds }
      if (newPrItems.isNotEmpty()) db.purchaseReturnDao().insertItems(newPrItems)

      // 6. Subledger, Allocations, Notes, Refunds
      bundle.subledgerEntries.forEach { entry ->
        val existing = db.subledgerDao().getByIdSync(entry.id)
        if (existing == null) {
          db.subledgerDao().insert(entry)
        } else if (!existing.isReversed && entry.isReversed) {
          db.subledgerDao().markReversed(
            id = entry.id,
            reversedAt = entry.reversedAt ?: System.currentTimeMillis(),
            reversalReason = entry.reversalReason ?: ""
          )
        }
      }

      val existingAllocs = db.paymentAllocationDao().getAllSync().associateBy { it.id }
      bundle.paymentAllocations.forEach { alloc ->
        val existing = existingAllocs[alloc.id]
        if (existing == null) {
          db.paymentAllocationDao().insert(alloc)
        } else if (!existing.isReversed && alloc.isReversed) {
          db.paymentAllocationDao().markReversed(
            id = alloc.id,
            reversedAt = alloc.reversedAt ?: System.currentTimeMillis(),
            reversalReason = alloc.reversalReason ?: ""
          )
        }
      }

      bundle.creditDebitNotes.forEach { n ->
        val existing = db.creditDebitNoteDao().getByIdSync(n.id)
        if (existing == null) db.creditDebitNoteDao().insert(n) else db.creditDebitNoteDao().update(n)
      }

      bundle.refundRecords.forEach { r ->
        val existing = db.refundRecordDao().getByIdSync(r.id)
        if (existing == null) db.refundRecordDao().insert(r) else db.refundRecordDao().update(r)
      }

      // 7. Reminders & Immutable Audit Logs
      bundle.paymentReminders.forEach { rem ->
        val existing = db.customerPaymentReminderDao().getByIdSync(rem.id)
        if (existing == null) db.customerPaymentReminderDao().insertReminder(rem)
        else db.customerPaymentReminderDao().updateReminder(rem)
      }
      val existingRemInvIds = db.customerPaymentReminderDao().getAllReminderInvoicesSync().map { it.id }.toSet()
      val newRemInvs = bundle.paymentReminderInvoices.filter { it.id !in existingRemInvIds }
      if (newRemInvs.isNotEmpty()) db.customerPaymentReminderDao().insertReminderInvoices(newRemInvs)

      val existingAuditIds = db.financialAuditLogDao().getAllSync().map { it.id }.toSet()
      bundle.auditLogs.forEach { log ->
        if (log.id !in existingAuditIds) db.financialAuditLogDao().insert(log)
      }
    }
  }

  fun parseConflictCandidateToApply(
    entityType: String,
    payload: Map<String, Any?>,
    bundle: RemoteSnapshotBundle
  ): RemoteSnapshotBundle = when (entityType) {
    "products" -> bundle.copy(products = bundle.products + FirestoreSyncMappers.fromProductDoc(payload))
    "suppliers" -> bundle.copy(suppliers = bundle.suppliers + FirestoreSyncMappers.fromSupplierDoc(payload))
    "customers" -> bundle.copy(customers = bundle.customers + FirestoreSyncMappers.fromCustomerDoc(payload))
    "customer_product_prices" -> bundle.copy(customerPrices = bundle.customerPrices + FirestoreSyncMappers.fromCustomerPriceDoc(payload))
    "product_suppliers" -> bundle.copy(productSuppliers = bundle.productSuppliers + FirestoreSyncMappers.fromProductSupplierDoc(payload))
    "product_batches" -> bundle.copy(batches = bundle.batches + FirestoreSyncMappers.fromBatchDoc(payload))
    "stock_movements" -> bundle.copy(stockMovements = bundle.stockMovements + FirestoreFinanceMappers.fromAuditLogDocOrNullMovement(payload))
    "purchase_invoices" -> bundle.copy(purchaseInvoices = bundle.purchaseInvoices + FirestoreSyncMappers.fromPurchaseInvoiceDoc(payload))
    "purchase_invoice_items" -> bundle.copy(purchaseInvoiceItems = bundle.purchaseInvoiceItems + FirestoreSyncMappers.fromPurchaseInvoiceItemDoc(payload))
    "sales_invoices" -> bundle.copy(salesInvoices = bundle.salesInvoices + FirestoreSyncMappers.fromSalesInvoiceDoc(payload))
    "sales_invoice_items" -> bundle.copy(salesInvoiceItems = bundle.salesInvoiceItems + FirestoreSyncMappers.fromSalesInvoiceItemDoc(payload))
    "subledger_entries" -> bundle.copy(subledgerEntries = bundle.subledgerEntries + FirestoreFinanceMappers.fromSubledgerDoc(payload))
    "payment_allocations" -> bundle.copy(paymentAllocations = bundle.paymentAllocations + FirestoreFinanceMappers.fromPaymentAllocationDoc(payload))
    "sales_returns" -> bundle.copy(salesReturns = bundle.salesReturns + FirestoreFinanceMappers.fromSalesReturnDoc(payload))
    "sales_return_items" -> bundle.copy(salesReturnItems = bundle.salesReturnItems + FirestoreFinanceMappers.fromSalesReturnItemDoc(payload))
    "purchase_returns" -> bundle.copy(purchaseReturns = bundle.purchaseReturns + FirestoreFinanceMappers.fromPurchaseReturnDoc(payload))
    "purchase_return_items" -> bundle.copy(purchaseReturnItems = bundle.purchaseReturnItems + FirestoreFinanceMappers.fromPurchaseReturnItemDoc(payload))
    "credit_debit_notes" -> bundle.copy(creditDebitNotes = bundle.creditDebitNotes + FirestoreFinanceMappers.fromCreditDebitNoteDoc(payload))
    "refund_records" -> bundle.copy(refundRecords = bundle.refundRecords + FirestoreFinanceMappers.fromRefundRecordDoc(payload))
    "expenses" -> bundle.copy(expenses = bundle.expenses + FirestoreFinanceMappers.fromExpenseDoc(payload))
    "payment_reminders" -> bundle.copy(paymentReminders = bundle.paymentReminders + FirestoreFinanceMappers.fromPaymentReminderDoc(payload))
    "payment_reminder_invoices" -> bundle.copy(paymentReminderInvoices = bundle.paymentReminderInvoices + FirestoreFinanceMappers.fromPaymentReminderInvoiceDoc(payload))
    "app_settings" -> bundle.copy(appSettings = bundle.appSettings + FirestoreFinanceMappers.fromAppSettingDoc(payload))
    "financial_audit_logs" -> bundle.copy(auditLogs = bundle.auditLogs + FirestoreFinanceMappers.fromAuditLogDoc(payload))
    else -> bundle
  }
}

private fun FirestoreFinanceMappers.fromAuditLogDocOrNullMovement(payload: Map<String, Any?>): StockMovementEntity =
  FirestoreSyncMappers.fromStockMovementDoc(payload)
