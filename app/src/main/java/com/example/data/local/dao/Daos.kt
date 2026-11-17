package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.AppSettingEntity
import com.example.data.local.entity.CreditDebitNoteEntity
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.CustomerPaymentReminderEntity
import com.example.data.local.entity.CustomerPaymentReminderInvoiceEntity
import com.example.data.local.entity.CustomerProductPriceEntity
import com.example.data.local.entity.ExpenseEntity
import com.example.data.local.entity.FinancialAuditLogEntity
import com.example.data.local.entity.PaymentAllocationEntity
import com.example.data.local.entity.ProductBatchEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.ProductSupplierEntity
import com.example.data.local.entity.ProductUnitEntity
import com.example.data.local.entity.PurchaseInvoiceEntity
import com.example.data.local.entity.PurchaseInvoiceItemEntity
import com.example.data.local.entity.PurchaseReturnEntity
import com.example.data.local.entity.PurchaseReturnItemEntity
import com.example.data.local.entity.RefundRecordEntity
import com.example.data.local.entity.SalesInvoiceEntity
import com.example.data.local.entity.SalesInvoiceItemEntity
import com.example.data.local.entity.SalesReturnEntity
import com.example.data.local.entity.SalesReturnItemEntity
import com.example.data.local.entity.StockMovementEntity
import com.example.data.local.entity.SubledgerEntryEntity
import com.example.data.local.entity.SupplierEntity
import com.example.domain.model.NoteStatus
import com.example.domain.model.PartyType
import com.example.domain.model.PaymentAllocationType
import com.example.domain.model.RefundExecutionStatus
import com.example.domain.model.SubledgerEntryType
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
  @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY name ASC")
  fun getAllActive(): Flow<List<ProductEntity>>

  @Query("SELECT * FROM products ORDER BY name ASC")
  fun getAllIncludingArchived(): Flow<List<ProductEntity>>

  @Query("SELECT * FROM products WHERE id = :id")
  fun getById(id: Long): Flow<ProductEntity?>

  @Query("SELECT * FROM products WHERE id = :id")
  suspend fun getProductSync(id: Long): ProductEntity?

  @Query("SELECT * FROM products WHERE isActive = 1 AND (name LIKE '%' || :query || '%' OR sku LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%') ORDER BY name ASC")
  fun search(query: String): Flow<List<ProductEntity>>

  @Query("SELECT COUNT(*) FROM products WHERE isActive = 1")
  fun countActive(): Flow<Int>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(product: ProductEntity): Long

  @Update
  suspend fun update(product: ProductEntity)

  @Query("UPDATE products SET isActive = 0, deletedAt = :deletedAt WHERE id = :id")
  suspend fun softDelete(id: Long, deletedAt: Long = System.currentTimeMillis())

  @Query("SELECT * FROM products ORDER BY id ASC")
  suspend fun getAllIncludingArchivedSync(): List<ProductEntity>
}

@Dao
interface ProductUnitDao {
  @Query("SELECT * FROM product_units WHERE productId = :productId LIMIT 1")
  fun getForProduct(productId: Long): Flow<ProductUnitEntity?>

  @Query("SELECT * FROM product_units WHERE productId = :productId LIMIT 1")
  suspend fun getForProductSync(productId: Long): ProductUnitEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertOrUpdate(unit: ProductUnitEntity): Long

  @Query("SELECT * FROM product_units ORDER BY id ASC")
  suspend fun getAllSync(): List<ProductUnitEntity>
}

@Dao
interface ProductSupplierDao {
  @Query("SELECT * FROM product_suppliers WHERE productId = :productId")
  fun getSuppliersForProduct(productId: Long): Flow<List<ProductSupplierEntity>>

  @Query("SELECT * FROM product_suppliers WHERE productId = :productId")
  suspend fun getSuppliersForProductSync(productId: Long): List<ProductSupplierEntity>

  @Query("SELECT * FROM product_suppliers WHERE supplierId = :supplierId")
  fun getProductsForSupplier(supplierId: Long): Flow<List<ProductSupplierEntity>>

  @Query("SELECT * FROM product_suppliers WHERE productId = :productId AND supplierId = :supplierId LIMIT 1")
  suspend fun getSpecificSupplierCost(productId: Long, supplierId: Long): ProductSupplierEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertOrUpdate(relation: ProductSupplierEntity): Long

  @Delete
  suspend fun delete(relation: ProductSupplierEntity)

  @Query("SELECT * FROM product_suppliers ORDER BY id ASC")
  suspend fun getAllSync(): List<ProductSupplierEntity>
}

@Dao
interface SupplierDao {
  @Query("SELECT * FROM suppliers WHERE isActive = 1 ORDER BY name ASC")
  fun getAllActive(): Flow<List<SupplierEntity>>

  @Query("SELECT * FROM suppliers ORDER BY name ASC")
  fun getAllIncludingArchived(): Flow<List<SupplierEntity>>

  @Query("SELECT * FROM suppliers WHERE id = :id")
  fun getById(id: Long): Flow<SupplierEntity?>

  @Query("SELECT * FROM suppliers WHERE id = :id")
  suspend fun getSupplierSync(id: Long): SupplierEntity?

  @Query("SELECT * FROM suppliers WHERE isActive = 1 AND (name LIKE '%' || :query || '%' OR contactPerson LIKE '%' || :query || '%' OR phoneNumber LIKE '%' || :query || '%') ORDER BY name ASC")
  fun search(query: String): Flow<List<SupplierEntity>>

  @Query("SELECT COUNT(*) FROM suppliers WHERE isActive = 1")
  fun countActive(): Flow<Int>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(supplier: SupplierEntity): Long

  @Update
  suspend fun update(supplier: SupplierEntity)

  @Query("UPDATE suppliers SET isActive = 0, deletedAt = :deletedAt WHERE id = :id")
  suspend fun softDelete(id: Long, deletedAt: Long = System.currentTimeMillis())

  @Query("SELECT * FROM suppliers ORDER BY id ASC")
  suspend fun getAllIncludingArchivedSync(): List<SupplierEntity>
}

@Dao
interface CustomerDao {
  @Query("SELECT * FROM customers WHERE isActive = 1 ORDER BY shopName ASC")
  fun getAllActive(): Flow<List<CustomerEntity>>

  @Query("SELECT * FROM customers ORDER BY shopName ASC")
  fun getAllIncludingArchived(): Flow<List<CustomerEntity>>

  @Query("SELECT * FROM customers WHERE id = :id")
  fun getById(id: Long): Flow<CustomerEntity?>

  @Query("SELECT * FROM customers WHERE id = :id")
  suspend fun getCustomerSync(id: Long): CustomerEntity?

  @Query("SELECT * FROM customers WHERE isActive = 1 AND (shopName LIKE '%' || :query || '%' OR ownerName LIKE '%' || :query || '%' OR phoneNumber LIKE '%' || :query || '%') ORDER BY shopName ASC")
  fun search(query: String): Flow<List<CustomerEntity>>

  @Query("SELECT COUNT(*) FROM customers WHERE isActive = 1")
  fun countActive(): Flow<Int>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(customer: CustomerEntity): Long

  @Update
  suspend fun update(customer: CustomerEntity)

  @Query("UPDATE customers SET isActive = 0, deletedAt = :deletedAt WHERE id = :id")
  suspend fun softDelete(id: Long, deletedAt: Long = System.currentTimeMillis())

  @Query("SELECT * FROM customers ORDER BY id ASC")
  suspend fun getAllIncludingArchivedSync(): List<CustomerEntity>
}

@Dao
interface CustomerProductPriceDao {
  @Query("SELECT * FROM customer_product_prices WHERE customerId = :customerId")
  fun getCustomPricesForCustomer(customerId: Long): Flow<List<CustomerProductPriceEntity>>

  @Query("SELECT * FROM customer_product_prices WHERE customerId = :customerId")
  suspend fun getCustomPricesForCustomerSync(customerId: Long): List<CustomerProductPriceEntity>

  @Query("SELECT * FROM customer_product_prices WHERE customerId = :customerId AND productId = :productId LIMIT 1")
  suspend fun getPrice(customerId: Long, productId: Long): CustomerProductPriceEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertOrUpdate(price: CustomerProductPriceEntity): Long

  @Query("DELETE FROM customer_product_prices WHERE customerId = :customerId AND productId = :productId")
  suspend fun delete(customerId: Long, productId: Long)

  @Query("DELETE FROM customer_product_prices WHERE customerId = :customerId")
  suspend fun deleteAllForCustomer(customerId: Long)

  @Query("SELECT * FROM customer_product_prices ORDER BY id ASC")
  suspend fun getAllSync(): List<CustomerProductPriceEntity>
}

@Dao
interface SubledgerDao {
  @Query("SELECT * FROM subledger_entries WHERE partyType = :partyType AND partyId = :partyId ORDER BY entryDate ASC, id ASC")
  fun getEntriesForParty(partyType: PartyType, partyId: Long): Flow<List<SubledgerEntryEntity>>

  @Query("SELECT * FROM subledger_entries WHERE partyType = :partyType AND partyId = :partyId ORDER BY entryDate ASC, id ASC")
  suspend fun getEntriesForPartySync(partyType: PartyType, partyId: Long): List<SubledgerEntryEntity>

  @Query("SELECT * FROM subledger_entries WHERE partyType = :partyType AND partyId = :partyId AND entryType = :entryType AND isReversed = 0 ORDER BY id ASC")
  suspend fun getActiveEntriesByTypeForPartySync(
    partyType: PartyType,
    partyId: Long,
    entryType: SubledgerEntryType
  ): List<SubledgerEntryEntity>

  @Query("SELECT * FROM subledger_entries ORDER BY entryDate DESC, id DESC")
  fun getAllEntries(): Flow<List<SubledgerEntryEntity>>

  @Query("SELECT * FROM subledger_entries ORDER BY id ASC")
  suspend fun getAllEntriesSync(): List<SubledgerEntryEntity>

  @Query("SELECT * FROM subledger_entries WHERE id = :id LIMIT 1")
  suspend fun getByIdSync(id: Long): SubledgerEntryEntity?

  @Query("SELECT * FROM subledger_entries WHERE referenceType = :referenceType AND referenceId = :referenceId AND isReversed = 0 ORDER BY id ASC")
  suspend fun getActiveByReferenceSync(referenceType: String, referenceId: Long): List<SubledgerEntryEntity>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(entry: SubledgerEntryEntity): Long

  @Query("UPDATE subledger_entries SET isReversed = 1, reversedAt = :reversedAt, reversalReason = :reversalReason WHERE id = :id")
  suspend fun markReversed(
    id: Long,
    reversedAt: Long = System.currentTimeMillis(),
    reversalReason: String = ""
  )
}

@Dao
interface PaymentAllocationDao {
  @Query("SELECT * FROM payment_allocations WHERE paymentId = :paymentId AND isReversed = 0")
  fun getForPayment(paymentId: Long): Flow<List<PaymentAllocationEntity>>

  @Query("SELECT * FROM payment_allocations WHERE paymentId = :paymentId AND isReversed = 0 ORDER BY allocatedAt ASC, id ASC")
  suspend fun getActiveForPaymentSync(paymentId: Long): List<PaymentAllocationEntity>

  @Query("SELECT * FROM payment_allocations WHERE paymentId = :paymentId AND isReversed = 0 AND isFromAdvanceConsumption = 1 ORDER BY allocatedAt ASC, id ASC")
  suspend fun getConsumedAdvanceAllocationsForPaymentSync(paymentId: Long): List<PaymentAllocationEntity>

  @Query("SELECT * FROM payment_allocations WHERE partyType = :partyType AND invoiceId = :invoiceId AND allocationType = 'INVOICE' AND isReversed = 0")
  fun getForInvoice(partyType: PartyType, invoiceId: Long): Flow<List<PaymentAllocationEntity>>

  @Query("SELECT * FROM payment_allocations WHERE partyType = :partyType AND invoiceId = :invoiceId AND allocationType = 'INVOICE' AND isReversed = 0")
  suspend fun getForInvoiceSync(partyType: PartyType, invoiceId: Long): List<PaymentAllocationEntity>

  @Query("SELECT * FROM payment_allocations WHERE partyType = :partyType AND partyId = :partyId AND isReversed = 0 ORDER BY allocatedAt ASC, id ASC")
  fun getActiveAllocationsForParty(partyType: PartyType, partyId: Long): Flow<List<PaymentAllocationEntity>>

  @Query("SELECT * FROM payment_allocations WHERE partyType = :partyType AND isReversed = 0 ORDER BY allocatedAt ASC, id ASC")
  fun getAllActiveAllocationsByPartyType(partyType: PartyType): Flow<List<PaymentAllocationEntity>>

  @Query("SELECT * FROM payment_allocations WHERE partyType = :partyType AND partyId = :partyId AND allocationType = :allocationType AND isReversed = 0 ORDER BY allocatedAt ASC, id ASC")
  suspend fun getActiveAllocationsByTypeForPartySync(
    partyType: PartyType,
    partyId: Long,
    allocationType: PaymentAllocationType
  ): List<PaymentAllocationEntity>

  @Query("SELECT COALESCE(SUM(allocatedAmountPaise), 0) FROM payment_allocations WHERE partyType = :partyType AND partyId = :partyId AND allocationType = 'ADVANCE' AND isReversed = 0")
  fun getAvailableAdvancePaise(partyType: PartyType, partyId: Long): Flow<Long>

  @Query("SELECT COALESCE(SUM(allocatedAmountPaise), 0) FROM payment_allocations WHERE partyType = :partyType AND partyId = :partyId AND allocationType = 'ADVANCE' AND isReversed = 0")
  suspend fun getAvailableAdvancePaiseSync(partyType: PartyType, partyId: Long): Long

  @Query("SELECT COALESCE(SUM(allocatedAmountPaise), 0) FROM payment_allocations WHERE partyType = :partyType AND partyId = :partyId AND allocationType = 'OPENING_BALANCE' AND isReversed = 0")
  suspend fun getTotalAllocatedOpeningBalancePaiseSync(partyType: PartyType, partyId: Long): Long

  @Query("SELECT * FROM payment_allocations WHERE partyType = :partyType AND partyId = :partyId ORDER BY allocatedAt DESC, id DESC")
  fun getAllIncludingReversedForParty(partyType: PartyType, partyId: Long): Flow<List<PaymentAllocationEntity>>

  @Query("SELECT * FROM payment_allocations ORDER BY id ASC")
  suspend fun getAllSync(): List<PaymentAllocationEntity>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(allocation: PaymentAllocationEntity): Long

  @Query("UPDATE payment_allocations SET isReversed = 1, reversedAt = :reversedAt, reversalReason = :reversalReason WHERE id = :id")
  suspend fun markReversed(
    id: Long,
    reversedAt: Long = System.currentTimeMillis(),
    reversalReason: String = ""
  )
}

@Dao
interface AppSettingDao {
  @Query("SELECT * FROM app_settings WHERE key = :key LIMIT 1")
  fun get(key: String): Flow<AppSettingEntity?>

  @Query("SELECT * FROM app_settings WHERE key = :key LIMIT 1")
  suspend fun getSync(key: String): AppSettingEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun set(setting: AppSettingEntity)

  @Query("SELECT * FROM app_settings")
  fun getAll(): Flow<List<AppSettingEntity>>

  @Query("SELECT * FROM app_settings ORDER BY `key` ASC")
  suspend fun getAllSync(): List<AppSettingEntity>
}

// ==========================================
// Step 2 DAOs: Inventory, Purchases, Sales & Expenses
// ==========================================

@Dao
interface ProductBatchDao {
  @Query("SELECT * FROM product_batches WHERE productId = :productId AND isActive = 1 ORDER BY expiryDate ASC, purchaseDate ASC")
  fun getActiveBatchesForProduct(productId: Long): Flow<List<ProductBatchEntity>>

  @Query("SELECT * FROM product_batches WHERE productId = :productId AND isActive = 1 ORDER BY expiryDate ASC, purchaseDate ASC")
  suspend fun getActiveBatchesForProductSync(productId: Long): List<ProductBatchEntity>

  @Query("SELECT * FROM product_batches WHERE isActive = 1 AND currentQuantityPieces > 0 ORDER BY expiryDate ASC")
  fun getAllStockedBatches(): Flow<List<ProductBatchEntity>>

  @Query("SELECT * FROM product_batches WHERE isActive = 1 ORDER BY expiryDate ASC, purchaseDate ASC")
  fun getAllActiveBatches(): Flow<List<ProductBatchEntity>>

  @Query("SELECT * FROM product_batches ORDER BY expiryDate ASC, purchaseDate ASC")
  fun getAllBatchesIncludingInactive(): Flow<List<ProductBatchEntity>>

  @Query("SELECT * FROM product_batches ORDER BY id ASC")
  suspend fun getAllBatchesSync(): List<ProductBatchEntity>

  @Query("SELECT * FROM product_batches WHERE purchaseInvoiceId = :purchaseInvoiceId ORDER BY id ASC")
  suspend fun getBatchesByPurchaseInvoiceSync(purchaseInvoiceId: Long): List<ProductBatchEntity>

  @Query("SELECT * FROM product_batches WHERE id = :batchId")
  suspend fun getById(batchId: Long): ProductBatchEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(batch: ProductBatchEntity): Long

  @Update
  suspend fun update(batch: ProductBatchEntity)

  @Query("UPDATE product_batches SET currentQuantityPieces = currentQuantityPieces - :deductPieces WHERE id = :batchId AND isActive = 1 AND currentQuantityPieces >= :deductPieces")
  suspend fun deductStockIfAvailable(batchId: Long, deductPieces: Int): Int

  @Query("UPDATE product_batches SET currentQuantityPieces = currentQuantityPieces + :addPieces WHERE id = :batchId AND isActive = 1")
  suspend fun addStockIfActive(batchId: Long, addPieces: Int): Int
}

@Dao
interface StockMovementDao {
  @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY movementDate DESC, id DESC")
  fun getMovementsForProduct(productId: Long): Flow<List<StockMovementEntity>>

  @Query("SELECT * FROM stock_movements ORDER BY movementDate DESC, id DESC LIMIT :limit")
  fun getRecentMovements(limit: Int = 100): Flow<List<StockMovementEntity>>

  @Query("SELECT * FROM stock_movements ORDER BY id ASC")
  suspend fun getAllSync(): List<StockMovementEntity>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(movement: StockMovementEntity): Long
}

@Dao
interface PurchaseInvoiceDao {
  @Query("SELECT * FROM purchase_invoices ORDER BY invoiceDate DESC, id DESC")
  fun getAll(): Flow<List<PurchaseInvoiceEntity>>

  @Query("SELECT * FROM purchase_invoices WHERE invoiceStatus = 'ACTIVE' ORDER BY invoiceDate DESC, id DESC")
  fun getAllActive(): Flow<List<PurchaseInvoiceEntity>>

  @Query("SELECT * FROM purchase_invoices ORDER BY id ASC")
  suspend fun getAllSync(): List<PurchaseInvoiceEntity>

  @Query("SELECT * FROM purchase_invoices WHERE supplierId = :supplierId ORDER BY invoiceDate DESC, id DESC")
  fun getForSupplier(supplierId: Long): Flow<List<PurchaseInvoiceEntity>>

  @Query("SELECT * FROM purchase_invoices WHERE id = :id")
  suspend fun getById(id: Long): PurchaseInvoiceEntity?

  @Query("SELECT * FROM purchase_invoices WHERE supplierId = :supplierId AND invoiceStatus = 'ACTIVE' AND paymentStatus != 'PAID' ORDER BY invoiceDate ASC, id ASC")
  suspend fun getUnpaidForSupplier(supplierId: Long): List<PurchaseInvoiceEntity>

  @Query("SELECT COUNT(*) FROM purchase_invoices")
  suspend fun countInvoices(): Int

  @Query("SELECT COUNT(*) FROM purchase_invoices WHERE invoiceNumber = :invoiceNumber")
  suspend fun existsByInvoiceNumber(invoiceNumber: String): Int

  @Query("SELECT COUNT(*) FROM purchase_invoices WHERE invoiceNumber = :invoiceNumber AND id != :excludeId")
  suspend fun existsByInvoiceNumberExcludingId(invoiceNumber: String, excludeId: Long): Int

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(invoice: PurchaseInvoiceEntity): Long

  @Update
  suspend fun update(invoice: PurchaseInvoiceEntity)
}

@Dao
interface PurchaseInvoiceItemDao {
  @Query("SELECT * FROM purchase_invoice_items WHERE purchaseInvoiceId = :invoiceId")
  fun getItemsForInvoice(invoiceId: Long): Flow<List<PurchaseInvoiceItemEntity>>

  @Query("SELECT * FROM purchase_invoice_items WHERE purchaseInvoiceId = :invoiceId")
  suspend fun getItemsForInvoiceSync(invoiceId: Long): List<PurchaseInvoiceItemEntity>

  @Query("SELECT * FROM purchase_invoice_items ORDER BY id ASC")
  suspend fun getAllItemsSync(): List<PurchaseInvoiceItemEntity>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertAll(items: List<PurchaseInvoiceItemEntity>)

  @Query("DELETE FROM purchase_invoice_items WHERE purchaseInvoiceId = :invoiceId")
  suspend fun deleteForInvoice(invoiceId: Long)
}

@Dao
interface SalesInvoiceDao {
  @Query("SELECT * FROM sales_invoices ORDER BY invoiceDate DESC, id DESC")
  fun getAll(): Flow<List<SalesInvoiceEntity>>

  @Query("SELECT * FROM sales_invoices WHERE invoiceStatus = 'ACTIVE' ORDER BY invoiceDate DESC, id DESC")
  fun getAllActive(): Flow<List<SalesInvoiceEntity>>

  @Query("SELECT * FROM sales_invoices ORDER BY id ASC")
  suspend fun getAllSync(): List<SalesInvoiceEntity>

  @Query("SELECT * FROM sales_invoices WHERE customerId = :customerId ORDER BY invoiceDate DESC, id DESC")
  fun getForCustomer(customerId: Long): Flow<List<SalesInvoiceEntity>>

  @Query("SELECT * FROM sales_invoices WHERE id = :id")
  suspend fun getById(id: Long): SalesInvoiceEntity?

  @Query("SELECT * FROM sales_invoices WHERE customerId = :customerId AND invoiceStatus = 'ACTIVE' AND paymentStatus != 'PAID' ORDER BY invoiceDate ASC, id ASC")
  suspend fun getUnpaidForCustomer(customerId: Long): List<SalesInvoiceEntity>

  @Query("SELECT COUNT(*) FROM sales_invoices")
  suspend fun countInvoices(): Int

  @Query("SELECT COUNT(*) FROM sales_invoices WHERE invoiceNumber = :invoiceNumber")
  suspend fun existsByInvoiceNumber(invoiceNumber: String): Int

  @Query("SELECT COUNT(*) FROM sales_invoices WHERE invoiceNumber = :invoiceNumber AND id != :excludeId")
  suspend fun existsByInvoiceNumberExcludingId(invoiceNumber: String, excludeId: Long): Int

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(invoice: SalesInvoiceEntity): Long

  @Update
  suspend fun update(invoice: SalesInvoiceEntity)
}

@Dao
interface SalesInvoiceItemDao {
  @Query("SELECT * FROM sales_invoice_items WHERE salesInvoiceId = :invoiceId")
  fun getItemsForInvoice(invoiceId: Long): Flow<List<SalesInvoiceItemEntity>>

  @Query("SELECT * FROM sales_invoice_items WHERE salesInvoiceId = :invoiceId")
  suspend fun getItemsForInvoiceSync(invoiceId: Long): List<SalesInvoiceItemEntity>

  @Query("SELECT * FROM sales_invoice_items")
  fun getAllItems(): Flow<List<SalesInvoiceItemEntity>>

  @Query("SELECT * FROM sales_invoice_items")
  suspend fun getAllItemsSync(): List<SalesInvoiceItemEntity>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertAll(items: List<SalesInvoiceItemEntity>)

  @Query("DELETE FROM sales_invoice_items WHERE salesInvoiceId = :invoiceId")
  suspend fun deleteForInvoice(invoiceId: Long)
}

@Dao
interface ExpenseDao {
  @Query("SELECT * FROM expenses WHERE deletedAt IS NULL ORDER BY expenseDate DESC, id DESC")
  fun getAllActive(): Flow<List<ExpenseEntity>>

  @Query("SELECT * FROM expenses ORDER BY id ASC")
  suspend fun getAllIncludingDeletedSync(): List<ExpenseEntity>

  @Query("SELECT * FROM expenses WHERE deletedAt IS NULL AND expenseDate BETWEEN :startDate AND :endDate ORDER BY expenseDate DESC")
  fun getExpensesBetween(startDate: Long, endDate: Long): Flow<List<ExpenseEntity>>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(expense: ExpenseEntity): Long

  @Update
  suspend fun update(expense: ExpenseEntity)

  @Query("UPDATE expenses SET deletedAt = :deletedAt WHERE id = :id")
  suspend fun softDelete(id: Long, deletedAt: Long = System.currentTimeMillis())
}

@Dao
interface CustomerPaymentReminderDao {
  @Query("SELECT * FROM customer_payment_reminders ORDER BY createdAt DESC, id DESC")
  fun observeAll(): Flow<List<CustomerPaymentReminderEntity>>

  @Query("SELECT * FROM customer_payment_reminders ORDER BY id ASC")
  suspend fun getAllRemindersSync(): List<CustomerPaymentReminderEntity>

  @Query("SELECT * FROM customer_payment_reminder_invoices ORDER BY id ASC")
  suspend fun getAllReminderInvoicesSync(): List<CustomerPaymentReminderInvoiceEntity>

  @Query("SELECT * FROM customer_payment_reminders WHERE customerId = :customerId ORDER BY createdAt DESC, id DESC")
  fun observeHistoryForCustomer(customerId: Long): Flow<List<CustomerPaymentReminderEntity>>

  @Query("SELECT * FROM customer_payment_reminders WHERE status = 'WHATSAPP_OPENED' AND awaitingUserConfirmation = 1 ORDER BY whatsappOpenedAt DESC, id DESC LIMIT 1")
  fun observePendingConfirmation(): Flow<CustomerPaymentReminderEntity?>

  @Query("SELECT * FROM customer_payment_reminders WHERE status = 'WHATSAPP_OPENED' AND awaitingUserConfirmation = 1 ORDER BY whatsappOpenedAt DESC, id DESC LIMIT 1")
  suspend fun getPendingConfirmationSync(): CustomerPaymentReminderEntity?

  @Query("SELECT * FROM customer_payment_reminders WHERE id = :id LIMIT 1")
  suspend fun getByIdSync(id: Long): CustomerPaymentReminderEntity?

  @Query("SELECT * FROM customer_payment_reminder_invoices WHERE reminderId = :reminderId ORDER BY invoiceDateSnapshot ASC, id ASC")
  suspend fun getInvoicesForReminderSync(reminderId: Long): List<CustomerPaymentReminderInvoiceEntity>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertReminder(reminder: CustomerPaymentReminderEntity): Long

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertReminderInvoices(items: List<CustomerPaymentReminderInvoiceEntity>)

  @Update
  suspend fun updateReminder(reminder: CustomerPaymentReminderEntity)
}

// ==========================================
// Phase 3 DAOs
// ==========================================

@Dao
interface SalesReturnDao {
  @Query("SELECT * FROM sales_returns ORDER BY returnDate DESC, id DESC")
  fun observeAll(): Flow<List<SalesReturnEntity>>

  @Query("SELECT * FROM sales_returns WHERE salesInvoiceId = :invoiceId ORDER BY returnDate DESC, id DESC")
  fun observeForInvoice(invoiceId: Long): Flow<List<SalesReturnEntity>>

  @Query("SELECT * FROM sales_returns WHERE salesInvoiceId = :invoiceId ORDER BY id ASC")
  suspend fun getForInvoiceSync(invoiceId: Long): List<SalesReturnEntity>

  @Query("SELECT * FROM sales_returns WHERE idempotencyKey = :key LIMIT 1")
  suspend fun getByIdempotencyKey(key: String): SalesReturnEntity?

  @Query("SELECT * FROM sales_returns WHERE id = :id LIMIT 1")
  suspend fun getByIdSync(id: Long): SalesReturnEntity?

  @Query("SELECT * FROM sales_returns ORDER BY id ASC")
  suspend fun getAllSync(): List<SalesReturnEntity>

  @Query("SELECT * FROM sales_return_items ORDER BY id ASC")
  fun observeAllItems(): Flow<List<SalesReturnItemEntity>>

  @Query("SELECT * FROM sales_return_items ORDER BY id ASC")
  suspend fun getAllItemsSync(): List<SalesReturnItemEntity>

  @Query("SELECT * FROM sales_return_items WHERE salesReturnId = :returnId ORDER BY id ASC")
  suspend fun getItemsForReturnSync(returnId: Long): List<SalesReturnItemEntity>

  @Query(
    """
    SELECT sri.* FROM sales_return_items sri
    INNER JOIN sales_returns sr ON sr.id = sri.salesReturnId
    WHERE sr.salesInvoiceId = :invoiceId
    ORDER BY sri.id ASC
    """
  )
  suspend fun getReturnedItemsForInvoiceSync(invoiceId: Long): List<SalesReturnItemEntity>

  @Query("SELECT COUNT(*) FROM sales_returns")
  suspend fun countReturns(): Int

  @Query("SELECT COUNT(*) FROM sales_returns WHERE returnNumber = :returnNumber")
  suspend fun existsByReturnNumber(returnNumber: String): Int

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertReturn(ret: SalesReturnEntity): Long

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertItems(items: List<SalesReturnItemEntity>)

  @Update
  suspend fun updateReturn(ret: SalesReturnEntity)
}

@Dao
interface PurchaseReturnDao {
  @Query("SELECT * FROM purchase_returns ORDER BY returnDate DESC, id DESC")
  fun observeAll(): Flow<List<PurchaseReturnEntity>>

  @Query("SELECT * FROM purchase_returns WHERE purchaseInvoiceId = :invoiceId ORDER BY returnDate DESC, id DESC")
  fun observeForInvoice(invoiceId: Long): Flow<List<PurchaseReturnEntity>>

  @Query("SELECT * FROM purchase_returns WHERE purchaseInvoiceId = :invoiceId ORDER BY id ASC")
  suspend fun getForInvoiceSync(invoiceId: Long): List<PurchaseReturnEntity>

  @Query("SELECT * FROM purchase_returns WHERE idempotencyKey = :key LIMIT 1")
  suspend fun getByIdempotencyKey(key: String): PurchaseReturnEntity?

  @Query("SELECT * FROM purchase_returns WHERE id = :id LIMIT 1")
  suspend fun getByIdSync(id: Long): PurchaseReturnEntity?

  @Query("SELECT * FROM purchase_returns ORDER BY id ASC")
  suspend fun getAllSync(): List<PurchaseReturnEntity>

  @Query("SELECT * FROM purchase_return_items ORDER BY id ASC")
  suspend fun getAllItemsSync(): List<PurchaseReturnItemEntity>

  @Query("SELECT * FROM purchase_return_items WHERE purchaseReturnId = :returnId ORDER BY id ASC")
  suspend fun getItemsForReturnSync(returnId: Long): List<PurchaseReturnItemEntity>

  @Query(
    """
    SELECT pri.* FROM purchase_return_items pri
    INNER JOIN purchase_returns pr ON pr.id = pri.purchaseReturnId
    WHERE pr.purchaseInvoiceId = :invoiceId
    ORDER BY pri.id ASC
    """
  )
  suspend fun getReturnedItemsForInvoiceSync(invoiceId: Long): List<PurchaseReturnItemEntity>

  @Query("SELECT COUNT(*) FROM purchase_returns")
  suspend fun countReturns(): Int

  @Query("SELECT COUNT(*) FROM purchase_returns WHERE returnNumber = :returnNumber")
  suspend fun existsByReturnNumber(returnNumber: String): Int

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertReturn(ret: PurchaseReturnEntity): Long

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertItems(items: List<PurchaseReturnItemEntity>)

  @Update
  suspend fun updateReturn(ret: PurchaseReturnEntity)
}

@Dao
interface CreditDebitNoteDao {
  @Query("SELECT * FROM credit_debit_notes ORDER BY issueDate DESC, id DESC")
  fun observeAll(): Flow<List<CreditDebitNoteEntity>>

  @Query("SELECT * FROM credit_debit_notes WHERE partyType = :partyType AND partyId = :partyId ORDER BY issueDate DESC, id DESC")
  fun observeForParty(partyType: PartyType, partyId: Long): Flow<List<CreditDebitNoteEntity>>

  @Query("SELECT * FROM credit_debit_notes WHERE linkedInvoiceId = :invoiceId AND partyType = :partyType ORDER BY issueDate DESC, id DESC")
  suspend fun getForInvoiceSync(partyType: PartyType, invoiceId: Long): List<CreditDebitNoteEntity>

  @Query("SELECT * FROM credit_debit_notes WHERE id = :id LIMIT 1")
  suspend fun getByIdSync(id: Long): CreditDebitNoteEntity?

  @Query("SELECT * FROM credit_debit_notes WHERE idempotencyKey = :key LIMIT 1")
  suspend fun getByIdempotencyKey(key: String): CreditDebitNoteEntity?

  @Query("SELECT * FROM credit_debit_notes WHERE status IN ('OPEN', 'APPLIED') AND partyType = :partyType AND partyId = :partyId ORDER BY issueDate ASC, id ASC")
  suspend fun getOpenForPartySync(partyType: PartyType, partyId: Long): List<CreditDebitNoteEntity>

  @Query("SELECT * FROM credit_debit_notes ORDER BY id ASC")
  suspend fun getAllSync(): List<CreditDebitNoteEntity>

  @Query("SELECT COUNT(*) FROM credit_debit_notes WHERE noteType = :noteType")
  suspend fun countByType(noteType: com.example.domain.model.CreditDebitNoteType): Int

  @Query("SELECT COUNT(*) FROM credit_debit_notes WHERE noteNumber = :noteNumber")
  suspend fun existsByNoteNumber(noteNumber: String): Int

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(note: CreditDebitNoteEntity): Long

  @Update
  suspend fun update(note: CreditDebitNoteEntity)
}

@Dao
interface RefundRecordDao {
  @Query("SELECT * FROM refund_records ORDER BY recordedAt DESC, id DESC")
  fun observeAll(): Flow<List<RefundRecordEntity>>

  @Query("SELECT * FROM refund_records WHERE partyType = :partyType AND partyId = :partyId ORDER BY recordedAt DESC, id DESC")
  suspend fun getForPartySync(partyType: PartyType, partyId: Long): List<RefundRecordEntity>

  @Query("SELECT * FROM refund_records WHERE sourceType = :sourceType AND sourceId = :sourceId ORDER BY recordedAt DESC, id DESC")
  suspend fun getForSourceSync(
    sourceType: com.example.domain.model.RefundSourceType,
    sourceId: Long
  ): List<RefundRecordEntity>

  @Query("SELECT * FROM refund_records WHERE id = :id LIMIT 1")
  suspend fun getByIdSync(id: Long): RefundRecordEntity?

  @Query("SELECT * FROM refund_records WHERE idempotencyKey = :key LIMIT 1")
  suspend fun getByIdempotencyKey(key: String): RefundRecordEntity?

  @Query("SELECT * FROM refund_records WHERE executionStatus = :status ORDER BY recordedAt DESC, id DESC")
  fun observeByStatus(status: RefundExecutionStatus): Flow<List<RefundRecordEntity>>

  @Query("SELECT * FROM refund_records ORDER BY id ASC")
  suspend fun getAllSync(): List<RefundRecordEntity>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(refund: RefundRecordEntity): Long

  @Update
  suspend fun update(refund: RefundRecordEntity)
}

@Dao
interface FinancialAuditLogDao {
  // Append-only immutable audit log: NO @Update or @Delete operations permitted.
  @Query("SELECT * FROM financial_audit_logs ORDER BY createdAt DESC, id DESC")
  fun observeAll(): Flow<List<FinancialAuditLogEntity>>

  @Query("SELECT * FROM financial_audit_logs WHERE entityType = :entityType AND entityId = :entityId ORDER BY createdAt DESC, id DESC")
  fun observeForEntity(entityType: String, entityId: Long): Flow<List<FinancialAuditLogEntity>>

  @Query("SELECT * FROM financial_audit_logs WHERE entityType = :entityType AND entityId = :entityId ORDER BY createdAt DESC, id DESC")
  suspend fun getForEntitySync(entityType: String, entityId: Long): List<FinancialAuditLogEntity>

  @Query("SELECT * FROM financial_audit_logs WHERE relatedPartyType = :partyType AND relatedPartyId = :partyId ORDER BY createdAt DESC, id DESC")
  fun observeForParty(partyType: PartyType, partyId: Long): Flow<List<FinancialAuditLogEntity>>

  @Query("SELECT * FROM financial_audit_logs ORDER BY id ASC")
  suspend fun getAllSync(): List<FinancialAuditLogEntity>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(log: FinancialAuditLogEntity): Long
}


