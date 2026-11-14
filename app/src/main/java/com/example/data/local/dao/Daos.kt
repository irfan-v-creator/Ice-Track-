package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.AppSettingEntity
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.CustomerProductPriceEntity
import com.example.data.local.entity.ExpenseEntity
import com.example.data.local.entity.PaymentAllocationEntity
import com.example.data.local.entity.ProductBatchEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.ProductSupplierEntity
import com.example.data.local.entity.ProductUnitEntity
import com.example.data.local.entity.PurchaseInvoiceEntity
import com.example.data.local.entity.PurchaseInvoiceItemEntity
import com.example.data.local.entity.SalesInvoiceEntity
import com.example.data.local.entity.SalesInvoiceItemEntity
import com.example.data.local.entity.StockMovementEntity
import com.example.data.local.entity.SubledgerEntryEntity
import com.example.data.local.entity.SupplierEntity
import com.example.domain.model.PartyType
import com.example.domain.model.PaymentAllocationType
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
}

@Dao
interface ProductUnitDao {
  @Query("SELECT * FROM product_units WHERE productId = :productId LIMIT 1")
  fun getForProduct(productId: Long): Flow<ProductUnitEntity?>

  @Query("SELECT * FROM product_units WHERE productId = :productId LIMIT 1")
  suspend fun getForProductSync(productId: Long): ProductUnitEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertOrUpdate(unit: ProductUnitEntity): Long
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

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(entry: SubledgerEntryEntity): Long

  @Query("UPDATE subledger_entries SET isReversed = 1 WHERE id = :id")
  suspend fun markReversed(id: Long)
}

@Dao
interface PaymentAllocationDao {
  @Query("SELECT * FROM payment_allocations WHERE paymentId = :paymentId AND isReversed = 0")
  fun getForPayment(paymentId: Long): Flow<List<PaymentAllocationEntity>>

  @Query("SELECT * FROM payment_allocations WHERE paymentId = :paymentId AND isReversed = 0 ORDER BY allocatedAt ASC, id ASC")
  suspend fun getActiveForPaymentSync(paymentId: Long): List<PaymentAllocationEntity>

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

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(allocation: PaymentAllocationEntity): Long

  @Query("UPDATE payment_allocations SET isReversed = 1 WHERE id = :id")
  suspend fun markReversed(id: Long)
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

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(movement: StockMovementEntity): Long
}

@Dao
interface PurchaseInvoiceDao {
  @Query("SELECT * FROM purchase_invoices ORDER BY invoiceDate DESC, id DESC")
  fun getAll(): Flow<List<PurchaseInvoiceEntity>>

  @Query("SELECT * FROM purchase_invoices WHERE supplierId = :supplierId ORDER BY invoiceDate DESC, id DESC")
  fun getForSupplier(supplierId: Long): Flow<List<PurchaseInvoiceEntity>>

  @Query("SELECT * FROM purchase_invoices WHERE id = :id")
  suspend fun getById(id: Long): PurchaseInvoiceEntity?

  @Query("SELECT * FROM purchase_invoices WHERE supplierId = :supplierId AND paymentStatus != 'PAID' ORDER BY invoiceDate ASC, id ASC")
  suspend fun getUnpaidForSupplier(supplierId: Long): List<PurchaseInvoiceEntity>

  @Query("SELECT COUNT(*) FROM purchase_invoices")
  suspend fun countInvoices(): Int

  @Query("SELECT COUNT(*) FROM purchase_invoices WHERE invoiceNumber = :invoiceNumber")
  suspend fun existsByInvoiceNumber(invoiceNumber: String): Int

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

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertAll(items: List<PurchaseInvoiceItemEntity>)
}

@Dao
interface SalesInvoiceDao {
  @Query("SELECT * FROM sales_invoices ORDER BY invoiceDate DESC, id DESC")
  fun getAll(): Flow<List<SalesInvoiceEntity>>

  @Query("SELECT * FROM sales_invoices WHERE customerId = :customerId ORDER BY invoiceDate DESC, id DESC")
  fun getForCustomer(customerId: Long): Flow<List<SalesInvoiceEntity>>

  @Query("SELECT * FROM sales_invoices WHERE id = :id")
  suspend fun getById(id: Long): SalesInvoiceEntity?

  @Query("SELECT * FROM sales_invoices WHERE customerId = :customerId AND paymentStatus != 'PAID' ORDER BY invoiceDate ASC, id ASC")
  suspend fun getUnpaidForCustomer(customerId: Long): List<SalesInvoiceEntity>

  @Query("SELECT COUNT(*) FROM sales_invoices")
  suspend fun countInvoices(): Int

  @Query("SELECT COUNT(*) FROM sales_invoices WHERE invoiceNumber = :invoiceNumber")
  suspend fun existsByInvoiceNumber(invoiceNumber: String): Int

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
}

@Dao
interface ExpenseDao {
  @Query("SELECT * FROM expenses WHERE deletedAt IS NULL ORDER BY expenseDate DESC, id DESC")
  fun getAllActive(): Flow<List<ExpenseEntity>>

  @Query("SELECT * FROM expenses WHERE deletedAt IS NULL AND expenseDate BETWEEN :startDate AND :endDate ORDER BY expenseDate DESC")
  fun getExpensesBetween(startDate: Long, endDate: Long): Flow<List<ExpenseEntity>>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(expense: ExpenseEntity): Long

  @Update
  suspend fun update(expense: ExpenseEntity)

  @Query("UPDATE expenses SET deletedAt = :deletedAt WHERE id = :id")
  suspend fun softDelete(id: Long, deletedAt: Long = System.currentTimeMillis())
}
