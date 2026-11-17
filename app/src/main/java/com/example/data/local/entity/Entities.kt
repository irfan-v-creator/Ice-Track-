package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.AuditOperationType
import com.example.domain.model.CreditDebitNoteType
import com.example.domain.model.DiscountType
import com.example.domain.model.ExpenseCategory
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.NoteStatus
import com.example.domain.model.PartyType
import com.example.domain.model.PaymentAllocationType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentStatus
import com.example.domain.model.RefundExecutionStatus
import com.example.domain.model.RefundSourceType
import com.example.domain.model.ReturnCondition
import com.example.domain.model.ReturnSettlementType
import com.example.domain.model.StockMovementType
import com.example.domain.model.SubledgerEntryType
import com.example.domain.model.UnitType

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
  val isReversed: Boolean = false,
  val operationRef: String = "",
  val reversedAt: Long? = null,
  val reversalReason: String? = null
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
  val isReversed: Boolean = false,
  val isFromAdvanceConsumption: Boolean = false,
  val reversedAt: Long? = null,
  val reversalReason: String? = null
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
  indices = [Index("productId"), Index("expiryDate"), Index("purchaseDate"), Index("purchaseInvoiceId")]
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
  val lineTotalPaise: Long,
  val productNameSnapshot: String = "",
  val unitType: UnitType = UnitType.PIECE,
  val unitQuantity: Int = quantityPieces,
  val unitRatePaise: Long = costPerPiecePaise,
  val itemDiscountPaise: Long = 0L
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
  val lineTotalPaise: Long,
  val productNameSnapshot: String = "",
  val unitType: UnitType = UnitType.PIECE,
  val unitQuantity: Int = quantityPieces,
  val unitRatePaise: Long = unitPricePaise,
  val itemDiscountPaise: Long = 0L
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

@Entity(
  tableName = "customer_payment_reminders",
  foreignKeys = [
    ForeignKey(
      entity = CustomerEntity::class,
      parentColumns = ["id"],
      childColumns = ["customerId"],
      onDelete = ForeignKey.RESTRICT
    )
  ],
  indices = [
    Index("customerId"),
    Index("status"),
    Index("whatsappOpenedAt"),
    Index("createdAt")
  ]
)
data class CustomerPaymentReminderEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val customerId: Long,
  val targetPhoneE164: String,
  val dueKind: com.example.domain.model.ReminderDueKind,
  val status: com.example.domain.model.ReminderStatus,
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

@Entity(
  tableName = "customer_payment_reminder_invoices",
  foreignKeys = [
    ForeignKey(
      entity = CustomerPaymentReminderEntity::class,
      parentColumns = ["id"],
      childColumns = ["reminderId"],
      onDelete = ForeignKey.CASCADE
    ),
    ForeignKey(
      entity = SalesInvoiceEntity::class,
      parentColumns = ["id"],
      childColumns = ["salesInvoiceId"],
      onDelete = ForeignKey.RESTRICT
    )
  ],
  indices = [
    Index("reminderId"),
    Index("salesInvoiceId")
  ]
)
data class CustomerPaymentReminderInvoiceEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val reminderId: Long,
  val salesInvoiceId: Long,
  val invoiceDateSnapshot: Long,
  val totalAmountPaiseSnapshot: Long,
  val pendingAmountPaiseSnapshot: Long
)

// ==========================================
// Phase 3 Entities: Returns, Credit/Debit Notes, Refunds & Immutable Audit Logs
// ==========================================

@Entity(
  tableName = "sales_returns",
  foreignKeys = [
    ForeignKey(
      entity = SalesInvoiceEntity::class,
      parentColumns = ["id"],
      childColumns = ["salesInvoiceId"],
      onDelete = ForeignKey.RESTRICT
    ),
    ForeignKey(
      entity = CustomerEntity::class,
      parentColumns = ["id"],
      childColumns = ["customerId"],
      onDelete = ForeignKey.RESTRICT
    )
  ],
  indices = [
    Index("returnNumber", unique = true),
    Index("salesInvoiceId"),
    Index("customerId"),
    Index("returnDate"),
    Index("idempotencyKey", unique = true)
  ]
)
data class SalesReturnEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

@Entity(
  tableName = "sales_return_items",
  foreignKeys = [
    ForeignKey(
      entity = SalesReturnEntity::class,
      parentColumns = ["id"],
      childColumns = ["salesReturnId"],
      onDelete = ForeignKey.CASCADE
    ),
    ForeignKey(
      entity = ProductEntity::class,
      parentColumns = ["id"],
      childColumns = ["productId"],
      onDelete = ForeignKey.RESTRICT
    )
  ],
  indices = [Index("salesReturnId"), Index("productId"), Index("batchId")]
)
data class SalesReturnItemEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val salesReturnId: Long,
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

@Entity(
  tableName = "purchase_returns",
  foreignKeys = [
    ForeignKey(
      entity = PurchaseInvoiceEntity::class,
      parentColumns = ["id"],
      childColumns = ["purchaseInvoiceId"],
      onDelete = ForeignKey.RESTRICT
    ),
    ForeignKey(
      entity = SupplierEntity::class,
      parentColumns = ["id"],
      childColumns = ["supplierId"],
      onDelete = ForeignKey.RESTRICT
    )
  ],
  indices = [
    Index("returnNumber", unique = true),
    Index("purchaseInvoiceId"),
    Index("supplierId"),
    Index("returnDate"),
    Index("idempotencyKey", unique = true)
  ]
)
data class PurchaseReturnEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

@Entity(
  tableName = "purchase_return_items",
  foreignKeys = [
    ForeignKey(
      entity = PurchaseReturnEntity::class,
      parentColumns = ["id"],
      childColumns = ["purchaseReturnId"],
      onDelete = ForeignKey.CASCADE
    ),
    ForeignKey(
      entity = ProductEntity::class,
      parentColumns = ["id"],
      childColumns = ["productId"],
      onDelete = ForeignKey.RESTRICT
    ),
    ForeignKey(
      entity = ProductBatchEntity::class,
      parentColumns = ["id"],
      childColumns = ["batchId"],
      onDelete = ForeignKey.RESTRICT
    )
  ],
  indices = [Index("purchaseReturnId"), Index("productId"), Index("batchId")]
)
data class PurchaseReturnItemEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val purchaseReturnId: Long,
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

@Entity(
  tableName = "credit_debit_notes",
  indices = [
    Index("noteNumber", unique = true),
    Index(value = ["partyType", "partyId"]),
    Index("linkedInvoiceId"),
    Index("linkedReturnId"),
    Index("issueDate"),
    Index("idempotencyKey", unique = true)
  ]
)
data class CreditDebitNoteEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
)

@Entity(
  tableName = "refund_records",
  indices = [
    Index(value = ["partyType", "partyId"]),
    Index(value = ["sourceType", "sourceId"]),
    Index("executionStatus"),
    Index("idempotencyKey", unique = true)
  ]
)
data class RefundRecordEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

@Entity(
  tableName = "financial_audit_logs",
  indices = [
    Index("operationType"),
    Index(value = ["entityType", "entityId"]),
    Index(value = ["relatedPartyType", "relatedPartyId"]),
    Index("createdAt")
  ]
)
data class FinancialAuditLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
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


