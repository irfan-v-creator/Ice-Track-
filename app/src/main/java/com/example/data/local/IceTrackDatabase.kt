package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.AppSettingDao
import com.example.data.local.dao.CreditDebitNoteDao
import com.example.data.local.dao.CustomerDao
import com.example.data.local.dao.CustomerPaymentReminderDao
import com.example.data.local.dao.CustomerProductPriceDao
import com.example.data.local.dao.ExpenseDao
import com.example.data.local.dao.FinancialAuditLogDao
import com.example.data.local.dao.PaymentAllocationDao
import com.example.data.local.dao.ProductBatchDao
import com.example.data.local.dao.ProductDao
import com.example.data.local.dao.ProductSupplierDao
import com.example.data.local.dao.ProductUnitDao
import com.example.data.local.dao.PurchaseInvoiceDao
import com.example.data.local.dao.PurchaseInvoiceItemDao
import com.example.data.local.dao.PurchaseReturnDao
import com.example.data.local.dao.RefundRecordDao
import com.example.data.local.dao.SalesInvoiceDao
import com.example.data.local.dao.SalesInvoiceItemDao
import com.example.data.local.dao.SalesReturnDao
import com.example.data.local.dao.StockMovementDao
import com.example.data.local.dao.SubledgerDao
import com.example.data.local.dao.SupplierDao
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

@Database(
  entities = [
    ProductEntity::class,
    ProductUnitEntity::class,
    ProductSupplierEntity::class,
    SupplierEntity::class,
    CustomerEntity::class,
    CustomerProductPriceEntity::class,
    SubledgerEntryEntity::class,
    PaymentAllocationEntity::class,
    AppSettingEntity::class,
    // Step 2 Entities:
    ProductBatchEntity::class,
    StockMovementEntity::class,
    PurchaseInvoiceEntity::class,
    PurchaseInvoiceItemEntity::class,
    SalesInvoiceEntity::class,
    SalesInvoiceItemEntity::class,
    ExpenseEntity::class,
    CustomerPaymentReminderEntity::class,
    CustomerPaymentReminderInvoiceEntity::class,
    // Phase 3 Entities:
    SalesReturnEntity::class,
    SalesReturnItemEntity::class,
    PurchaseReturnEntity::class,
    PurchaseReturnItemEntity::class,
    CreditDebitNoteEntity::class,
    RefundRecordEntity::class,
    FinancialAuditLogEntity::class
  ],
  version = 5,
  exportSchema = true
)
abstract class IceTrackDatabase : RoomDatabase() {

  abstract fun productDao(): ProductDao
  abstract fun productUnitDao(): ProductUnitDao
  abstract fun productSupplierDao(): ProductSupplierDao
  abstract fun supplierDao(): SupplierDao
  abstract fun customerDao(): CustomerDao
  abstract fun customerProductPriceDao(): CustomerProductPriceDao
  abstract fun subledgerDao(): SubledgerDao
  abstract fun paymentAllocationDao(): PaymentAllocationDao
  abstract fun appSettingDao(): AppSettingDao

  // Step 2 DAOs
  abstract fun productBatchDao(): ProductBatchDao
  abstract fun stockMovementDao(): StockMovementDao
  abstract fun purchaseInvoiceDao(): PurchaseInvoiceDao
  abstract fun purchaseInvoiceItemDao(): PurchaseInvoiceItemDao
  abstract fun salesInvoiceDao(): SalesInvoiceDao
  abstract fun salesInvoiceItemDao(): SalesInvoiceItemDao
  abstract fun expenseDao(): ExpenseDao
  abstract fun customerPaymentReminderDao(): CustomerPaymentReminderDao

  // Phase 3 DAOs
  abstract fun salesReturnDao(): SalesReturnDao
  abstract fun purchaseReturnDao(): PurchaseReturnDao
  abstract fun creditDebitNoteDao(): CreditDebitNoteDao
  abstract fun refundRecordDao(): RefundRecordDao
  abstract fun financialAuditLogDao(): FinancialAuditLogDao

  companion object {
    @Volatile
    private var INSTANCE: IceTrackDatabase? = null

    fun getInstance(context: Context): IceTrackDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          IceTrackDatabase::class.java,
          "icetrack_database.db"
        )
        .addMigrations(
          DatabaseMigrations.MIGRATION_1_2,
          DatabaseMigrations.MIGRATION_2_3,
          DatabaseMigrations.MIGRATION_3_4,
          DatabaseMigrations.MIGRATION_4_5
        )
        .build()
        INSTANCE = instance
        instance
      }
    }
  }
}
