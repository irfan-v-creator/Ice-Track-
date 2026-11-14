package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.ExpenseCategory
import com.example.domain.model.PartyType
import com.example.domain.model.PaymentAllocationType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentStatus
import com.example.domain.model.StockMovementType
import com.example.domain.model.SubledgerEntryType

@Entity(
  tableName = "products",
  indices = [Index("sku", unique = true), Index("category"), Index("isActive")]
)
data class ProductEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val sku: String,
  val category: String,
  val defaultPurchasePricePaise: Long,
  val defaultSellingPricePaise: Long,
  val minStockLevelPieces: Int,
  val isActive: Boolean = true,
  val createdAt: Long = System.currentTimeMillis(),
  val deletedAt: Long? = null
)

@Entity(
  tableName = "product_units",
  foreignKeys = [
    ForeignKey(
      entity = ProductEntity::class,
      parentColumns = ["id"],
      childColumns = ["productId"],
      onDelete = ForeignKey.RESTRICT
    )
  ],
  indices = [Index("productId", unique = true)]
)
data class ProductUnitEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val productId: Long,
  val piecesPerBox: Int,
  val boxesPerCarton: Int
)

@Entity(
  tableName = "product_suppliers",
  foreignKeys = [
    ForeignKey(
      entity = ProductEntity::class,
      parentColumns = ["id"],
      childColumns = ["productId"],
      onDelete = ForeignKey.RESTRICT
    ),
    ForeignKey(
      entity = SupplierEntity::class,
      parentColumns = ["id"],
      childColumns = ["supplierId"],
      onDelete = ForeignKey.RESTRICT
    )
  ],
  indices = [Index(value = ["productId", "supplierId"], unique = true), Index("supplierId")]
)
data class ProductSupplierEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val productId: Long,
  val supplierId: Long,
  val supplierProductCode: String = "",
  val purchaseCostPaise: Long,
  val isPreferredSupplier: Boolean = false
)

@Entity(
  tableName = "suppliers",
  indices = [Index("name"), Index("isActive")]
)
data class SupplierEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val contactPerson: String,
  val phoneNumber: String,
  val address: String,
  val openingBalancePaise: Long = 0L,
  val openingBalanceDate: Long = System.currentTimeMillis(),
  val creditTermsDays: Int = 15,
  val creditLimitPaise: Long = 0L,
  val isActive: Boolean = true,
  val createdAt: Long = System.currentTimeMillis(),
  val deletedAt: Long? = null
)

@Entity(
  tableName = "customers",
  indices = [Index("shopName"), Index("ownerName"), Index("isActive")]
)
data class CustomerEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val shopName: String,
  val ownerName: String,
  val phoneNumber: String,
  val address: String,
  val openingBalancePaise: Long = 0L,
  val openingBalanceDate: Long = System.currentTimeMillis(),
  val creditLimitPaise: Long = 0L,
  val paymentTermsDays: Int = 7,
  val isActive: Boolean = true,
  val createdAt: Long = System.currentTimeMillis(),
  val deletedAt: Long? = null
)

@Entity(
  tableName = "customer_product_prices",
  foreignKeys = [
    ForeignKey(
      entity = CustomerEntity::class,
      parentColumns = ["id"],
      childColumns = ["customerId"],
      onDelete = ForeignKey.RESTRICT
    ),
    ForeignKey(
      entity = ProductEntity::class,
      parentColumns = ["id"],
      childColumns = ["productId"],
      onDelete = ForeignKey.RESTRICT
    )
  ],
  indices = [Index(value = ["customerId", "productId"], unique = true), Index("productId")]
)
data class CustomerProductPriceEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val customerId: Long,
  val productId: Long,
  val customSellingPricePaise: Long
)

@Entity(
  tableName = "subledger_entries",
  indices = [
    Index(value = ["partyType", "partyId"]),
    Index("entryDate"),
    Index(value = ["referenceType", "referenceId"])
  ]
)
data class SubledgerEntryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
  val isReversed: Boolean = false
)

@Entity(
  tableName = "payment_allocations",
  indices = [
    Index("paymentId"),
    Index("invoiceId"),
    Index(value = ["partyType", "partyId"]),
    Index("allocationType")
  ]
)
data class PaymentAllocationEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val paymentId: Long,
  val partyType: PartyType,
  val partyId: Long,
  val allocationType: PaymentAllocationType,
  val invoiceId: Long? = null,
  val allocatedAmountPaise: Long,
  val allocatedAt: Long = System.currentTimeMillis(),
  val notes: String = "",
  val isReversed: Boolean = false
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
  @PrimaryKey val key: String,
  val value: String
)

// ==========================================
// Step 2 Entities: Inventory, Purchases, Sales & Expenses
// ==========================================

@Entity(
  tableName = "product_batches",
  foreignKeys = [
    ForeignKey(
      entity = ProductEntity::class,
      parentColumns = ["id"],
      childColumns = ["productId"],
      onDelete = ForeignKey.RESTRICT
    )
  ],
  indices = [Index("productId"), Index("expiryDate"), Index("purchaseDate")]
)
data class ProductBatchEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
)

@Entity(
  tableName = "stock_movements",
  foreignKeys = [
    ForeignKey(
      entity = ProductEntity::class,
      parentColumns = ["id"],
      childColumns = ["productId"],
      onDelete = ForeignKey.RESTRICT
    )
  ],
  indices = [Index("productId"), Index("batchId"), Index("movementDate")]
)
data class StockMovementEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

@Entity(
  tableName = "purchase_invoices",
  foreignKeys = [
    ForeignKey(
      entity = SupplierEntity::class,
      parentColumns = ["id"],
      childColumns = ["supplierId"],
      onDelete = ForeignKey.RESTRICT
    )
  ],
  indices = [Index("invoiceNumber", unique = true), Index("supplierId"), Index("invoiceDate"), Index("dueDate")]
)
data class PurchaseInvoiceEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
  val createdAt: Long = System.currentTimeMillis()
)

@Entity(
  tableName = "purchase_invoice_items",
  foreignKeys = [
    ForeignKey(
      entity = PurchaseInvoiceEntity::class,
      parentColumns = ["id"],
      childColumns = ["purchaseInvoiceId"],
      onDelete = ForeignKey.CASCADE
    ),
    ForeignKey(
      entity = ProductEntity::class,
      parentColumns = ["id"],
      childColumns = ["productId"],
      onDelete = ForeignKey.RESTRICT
    )
  ],
  indices = [Index("purchaseInvoiceId"), Index("productId")]
)
data class PurchaseInvoiceItemEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val purchaseInvoiceId: Long,
  val productId: Long,
  val batchNumber: String,
  val expiryDate: Long,
  val quantityPieces: Int,
  val costPerPiecePaise: Long,
  val lineTotalPaise: Long
)

@Entity(
  tableName = "sales_invoices",
  foreignKeys = [
    ForeignKey(
      entity = CustomerEntity::class,
      parentColumns = ["id"],
      childColumns = ["customerId"],
      onDelete = ForeignKey.RESTRICT
    )
  ],
  indices = [Index("invoiceNumber", unique = true), Index("customerId"), Index("invoiceDate"), Index("dueDate")]
)
data class SalesInvoiceEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
  val createdAt: Long = System.currentTimeMillis()
)

@Entity(
  tableName = "sales_invoice_items",
  foreignKeys = [
    ForeignKey(
      entity = SalesInvoiceEntity::class,
      parentColumns = ["id"],
      childColumns = ["salesInvoiceId"],
      onDelete = ForeignKey.CASCADE
    ),
    ForeignKey(
      entity = ProductEntity::class,
      parentColumns = ["id"],
      childColumns = ["productId"],
      onDelete = ForeignKey.RESTRICT
    )
  ],
  indices = [Index("salesInvoiceId"), Index("productId"), Index("batchId")]
)
data class SalesInvoiceItemEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val salesInvoiceId: Long,
  val productId: Long,
  val batchId: Long,
  val quantityPieces: Int,
  val unitPricePaise: Long,
  val lineTotalPaise: Long
)

@Entity(
  tableName = "expenses",
  indices = [Index("category"), Index("expenseDate")]
)
data class ExpenseEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val category: ExpenseCategory,
  val amountPaise: Long,
  val expenseDate: Long,
  val paymentMethod: PaymentMethod,
  val referenceNumber: String = "",
  val notes: String = "",
  val createdAt: Long = System.currentTimeMillis(),
  val deletedAt: Long? = null
)
