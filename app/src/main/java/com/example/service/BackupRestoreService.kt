package com.example.service

import androidx.room.withTransaction
import com.example.data.local.IceTrackDatabase
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
import com.example.domain.model.ReminderDueKind
import com.example.domain.model.ReminderStatus
import com.example.domain.model.ReturnCondition
import com.example.domain.model.ReturnSettlementType
import com.example.domain.model.StockMovementType
import com.example.domain.model.SubledgerEntryType
import com.example.domain.model.UnitType
import org.json.JSONObject

data class BackupValidationResult(
  val isValid: Boolean,
  val productCount: Int,
  val customerCount: Int,
  val supplierCount: Int,
  val salesCount: Int,
  val purchaseCount: Int,
  val errorMessage: String? = null,
  val schemaVersion: Int = 5,
  val subledgerCount: Int = 0,
  val returnCount: Int = 0,
  val noteCount: Int = 0,
  val refundCount: Int = 0,
  val auditLogCount: Int = 0
)

object BackupRestoreService {

  const val CURRENT_SCHEMA_VERSION = 5

  suspend fun exportDatabaseFromDatabase(db: IceTrackDatabase): String {
    return exportDatabaseToJson(
      products = db.productDao().getAllIncludingArchivedSync(),
      units = db.productUnitDao().getAllSync(),
      productSuppliers = db.productSupplierDao().getAllSync(),
      suppliers = db.supplierDao().getAllIncludingArchivedSync(),
      customers = db.customerDao().getAllIncludingArchivedSync(),
      customerPrices = db.customerProductPriceDao().getAllSync(),
      batches = db.productBatchDao().getAllBatchesSync(),
      movements = db.stockMovementDao().getAllSync(),
      purchases = db.purchaseInvoiceDao().getAllSync(),
      purchaseItems = db.purchaseInvoiceItemDao().getAllItemsSync(),
      sales = db.salesInvoiceDao().getAllSync(),
      salesItems = db.salesInvoiceItemDao().getAllItemsSync(),
      subledger = db.subledgerDao().getAllEntriesSync(),
      allocations = db.paymentAllocationDao().getAllSync(),
      expenses = db.expenseDao().getAllIncludingDeletedSync(),
      settings = db.appSettingDao().getAllSync(),
      reminders = db.customerPaymentReminderDao().getAllRemindersSync(),
      reminderInvoices = db.customerPaymentReminderDao().getAllReminderInvoicesSync(),
      salesReturns = db.salesReturnDao().getAllSync(),
      salesReturnItems = db.salesReturnDao().getAllItemsSync(),
      purchaseReturns = db.purchaseReturnDao().getAllSync(),
      purchaseReturnItems = db.purchaseReturnDao().getAllItemsSync(),
      creditDebitNotes = db.creditDebitNoteDao().getAllSync(),
      refundRecords = db.refundRecordDao().getAllSync(),
      auditLogs = db.financialAuditLogDao().getAllSync()
    )
  }

  /**
   * Serializes the entire IceTrack SQLite database (all 25 tables, Schema V5) into a validated JSON backup string.
   */
  suspend fun exportDatabaseToJson(
    products: List<ProductEntity>,
    units: List<ProductUnitEntity>,
    productSuppliers: List<ProductSupplierEntity>,
    suppliers: List<SupplierEntity>,
    customers: List<CustomerEntity>,
    customerPrices: List<CustomerProductPriceEntity>,
    batches: List<ProductBatchEntity>,
    movements: List<StockMovementEntity>,
    purchases: List<PurchaseInvoiceEntity>,
    purchaseItems: List<PurchaseInvoiceItemEntity>,
    sales: List<SalesInvoiceEntity>,
    salesItems: List<SalesInvoiceItemEntity>,
    subledger: List<SubledgerEntryEntity>,
    allocations: List<PaymentAllocationEntity>,
    expenses: List<ExpenseEntity>,
    settings: List<AppSettingEntity>,
    reminders: List<CustomerPaymentReminderEntity> = emptyList(),
    reminderInvoices: List<CustomerPaymentReminderInvoiceEntity> = emptyList(),
    salesReturns: List<SalesReturnEntity> = emptyList(),
    salesReturnItems: List<SalesReturnItemEntity> = emptyList(),
    purchaseReturns: List<PurchaseReturnEntity> = emptyList(),
    purchaseReturnItems: List<PurchaseReturnItemEntity> = emptyList(),
    creditDebitNotes: List<CreditDebitNoteEntity> = emptyList(),
    refundRecords: List<RefundRecordEntity> = emptyList(),
    auditLogs: List<FinancialAuditLogEntity> = emptyList()
  ): String {
    val root = linkedMapOf<String, Any?>(
      "app" to "IceTrack",
      "schemaVersion" to CURRENT_SCHEMA_VERSION,
      "exportedAt" to System.currentTimeMillis(),
      "products" to products.map {
        linkedMapOf(
          "id" to it.id,
          "name" to it.name,
          "sku" to it.sku,
          "category" to it.category,
          "defaultPurchasePricePaise" to it.defaultPurchasePricePaise,
          "defaultSellingPricePaise" to it.defaultSellingPricePaise,
          "minStockLevelPieces" to it.minStockLevelPieces,
          "isActive" to it.isActive,
          "createdAt" to it.createdAt,
          "deletedAt" to it.deletedAt
        )
      },
      "product_units" to units.map {
        linkedMapOf(
          "id" to it.id,
          "productId" to it.productId,
          "piecesPerBox" to it.piecesPerBox,
          "boxesPerCarton" to it.boxesPerCarton
        )
      },
      "product_suppliers" to productSuppliers.map {
        linkedMapOf(
          "id" to it.id,
          "productId" to it.productId,
          "supplierId" to it.supplierId,
          "supplierProductCode" to it.supplierProductCode,
          "purchaseCostPaise" to it.purchaseCostPaise,
          "isPreferredSupplier" to it.isPreferredSupplier
        )
      },
      "suppliers" to suppliers.map {
        linkedMapOf(
          "id" to it.id,
          "name" to it.name,
          "contactPerson" to it.contactPerson,
          "phoneNumber" to it.phoneNumber,
          "address" to it.address,
          "openingBalancePaise" to it.openingBalancePaise,
          "openingBalanceDate" to it.openingBalanceDate,
          "creditTermsDays" to it.creditTermsDays,
          "creditLimitPaise" to it.creditLimitPaise,
          "isActive" to it.isActive,
          "createdAt" to it.createdAt,
          "deletedAt" to it.deletedAt
        )
      },
      "customers" to customers.map {
        linkedMapOf(
          "id" to it.id,
          "shopName" to it.shopName,
          "ownerName" to it.ownerName,
          "phoneNumber" to it.phoneNumber,
          "address" to it.address,
          "openingBalancePaise" to it.openingBalancePaise,
          "openingBalanceDate" to it.openingBalanceDate,
          "creditLimitPaise" to it.creditLimitPaise,
          "paymentTermsDays" to it.paymentTermsDays,
          "isActive" to it.isActive,
          "createdAt" to it.createdAt,
          "deletedAt" to it.deletedAt
        )
      },
      "customer_product_prices" to customerPrices.map {
        linkedMapOf(
          "id" to it.id,
          "customerId" to it.customerId,
          "productId" to it.productId,
          "customSellingPricePaise" to it.customSellingPricePaise
        )
      },
      "product_batches" to batches.map {
        linkedMapOf(
          "id" to it.id,
          "productId" to it.productId,
          "batchNumber" to it.batchNumber,
          "purchaseInvoiceId" to it.purchaseInvoiceId,
          "purchaseDate" to it.purchaseDate,
          "expiryDate" to it.expiryDate,
          "initialQuantityPieces" to it.initialQuantityPieces,
          "currentQuantityPieces" to it.currentQuantityPieces,
          "purchaseCostPerPiecePaise" to it.purchaseCostPerPiecePaise,
          "isActive" to it.isActive,
          "createdAt" to it.createdAt
        )
      },
      "stock_movements" to movements.map {
        linkedMapOf(
          "id" to it.id,
          "productId" to it.productId,
          "batchId" to it.batchId,
          "movementType" to it.movementType.name,
          "quantityPieces" to it.quantityPieces,
          "unitCostPaise" to it.unitCostPaise,
          "referenceType" to it.referenceType,
          "referenceId" to it.referenceId,
          "movementDate" to it.movementDate,
          "notes" to it.notes,
          "createdAt" to it.createdAt
        )
      },
      "purchase_invoices" to purchases.map {
        linkedMapOf(
          "id" to it.id,
          "invoiceNumber" to it.invoiceNumber,
          "supplierId" to it.supplierId,
          "invoiceDate" to it.invoiceDate,
          "dueDate" to it.dueDate,
          "totalAmountPaise" to it.totalAmountPaise,
          "paidAmountPaise" to it.paidAmountPaise,
          "paymentStatus" to it.paymentStatus.name,
          "paymentMethod" to it.paymentMethod?.name,
          "notes" to it.notes,
          "isConfirmed" to it.isConfirmed,
          "createdAt" to it.createdAt,
          "subtotalPaise" to it.subtotalPaise,
          "discountType" to it.discountType.name,
          "discountValueRaw" to it.discountValueRaw,
          "discountAmountPaise" to it.discountAmountPaise,
          "additionalChargesPaise" to it.additionalChargesPaise,
          "invoiceStatus" to it.invoiceStatus.name,
          "cancelledAt" to it.cancelledAt,
          "cancellationReason" to it.cancellationReason,
          "revisionVersion" to it.revisionVersion,
          "updatedAt" to it.updatedAt,
          "partyNameSnapshot" to it.partyNameSnapshot,
          "partyPhoneSnapshot" to it.partyPhoneSnapshot,
          "partyAddressSnapshot" to it.partyAddressSnapshot
        )
      },
      "purchase_invoice_items" to purchaseItems.map {
        linkedMapOf(
          "id" to it.id,
          "purchaseInvoiceId" to it.purchaseInvoiceId,
          "productId" to it.productId,
          "batchNumber" to it.batchNumber,
          "expiryDate" to it.expiryDate,
          "quantityPieces" to it.quantityPieces,
          "costPerPiecePaise" to it.costPerPiecePaise,
          "lineTotalPaise" to it.lineTotalPaise,
          "productNameSnapshot" to it.productNameSnapshot,
          "unitType" to it.unitType.name,
          "unitQuantity" to it.unitQuantity,
          "unitRatePaise" to it.unitRatePaise,
          "itemDiscountPaise" to it.itemDiscountPaise
        )
      },
      "sales_invoices" to sales.map {
        linkedMapOf(
          "id" to it.id,
          "invoiceNumber" to it.invoiceNumber,
          "customerId" to it.customerId,
          "invoiceDate" to it.invoiceDate,
          "dueDate" to it.dueDate,
          "totalAmountPaise" to it.totalAmountPaise,
          "paidAmountPaise" to it.paidAmountPaise,
          "paymentStatus" to it.paymentStatus.name,
          "paymentMethod" to it.paymentMethod?.name,
          "notes" to it.notes,
          "isConfirmed" to it.isConfirmed,
          "createdAt" to it.createdAt,
          "subtotalPaise" to it.subtotalPaise,
          "discountType" to it.discountType.name,
          "discountValueRaw" to it.discountValueRaw,
          "discountAmountPaise" to it.discountAmountPaise,
          "additionalChargesPaise" to it.additionalChargesPaise,
          "invoiceStatus" to it.invoiceStatus.name,
          "cancelledAt" to it.cancelledAt,
          "cancellationReason" to it.cancellationReason,
          "revisionVersion" to it.revisionVersion,
          "updatedAt" to it.updatedAt,
          "partyNameSnapshot" to it.partyNameSnapshot,
          "partyPhoneSnapshot" to it.partyPhoneSnapshot,
          "partyAddressSnapshot" to it.partyAddressSnapshot
        )
      },
      "sales_invoice_items" to salesItems.map {
        linkedMapOf(
          "id" to it.id,
          "salesInvoiceId" to it.salesInvoiceId,
          "productId" to it.productId,
          "batchId" to it.batchId,
          "quantityPieces" to it.quantityPieces,
          "unitPricePaise" to it.unitPricePaise,
          "lineTotalPaise" to it.lineTotalPaise,
          "productNameSnapshot" to it.productNameSnapshot,
          "unitType" to it.unitType.name,
          "unitQuantity" to it.unitQuantity,
          "unitRatePaise" to it.unitRatePaise,
          "itemDiscountPaise" to it.itemDiscountPaise
        )
      },
      "sales_returns" to salesReturns.map {
        linkedMapOf(
          "id" to it.id,
          "returnNumber" to it.returnNumber,
          "salesInvoiceId" to it.salesInvoiceId,
          "customerId" to it.customerId,
          "returnDate" to it.returnDate,
          "totalReturnAmountPaise" to it.totalReturnAmountPaise,
          "settlementType" to it.settlementType.name,
          "creditNoteId" to it.creditNoteId,
          "refundRecordId" to it.refundRecordId,
          "subledgerEntryId" to it.subledgerEntryId,
          "reason" to it.reason,
          "notes" to it.notes,
          "idempotencyKey" to it.idempotencyKey,
          "createdAt" to it.createdAt
        )
      },
      "sales_return_items" to salesReturnItems.map {
        linkedMapOf(
          "id" to it.id,
          "salesReturnId" to it.salesReturnId,
          "originalSalesInvoiceItemId" to it.originalSalesInvoiceItemId,
          "productId" to it.productId,
          "batchId" to it.batchId,
          "productNameSnapshot" to it.productNameSnapshot,
          "quantityPieces" to it.quantityPieces,
          "unitPricePaise" to it.unitPricePaise,
          "lineTotalPaise" to it.lineTotalPaise,
          "condition" to it.condition.name,
          "restoredToSaleableStock" to it.restoredToSaleableStock
        )
      },
      "purchase_returns" to purchaseReturns.map {
        linkedMapOf(
          "id" to it.id,
          "returnNumber" to it.returnNumber,
          "purchaseInvoiceId" to it.purchaseInvoiceId,
          "supplierId" to it.supplierId,
          "returnDate" to it.returnDate,
          "totalReturnAmountPaise" to it.totalReturnAmountPaise,
          "settlementType" to it.settlementType.name,
          "debitNoteId" to it.debitNoteId,
          "refundRecordId" to it.refundRecordId,
          "subledgerEntryId" to it.subledgerEntryId,
          "reason" to it.reason,
          "notes" to it.notes,
          "idempotencyKey" to it.idempotencyKey,
          "createdAt" to it.createdAt
        )
      },
      "purchase_return_items" to purchaseReturnItems.map {
        linkedMapOf(
          "id" to it.id,
          "purchaseReturnId" to it.purchaseReturnId,
          "originalPurchaseInvoiceItemId" to it.originalPurchaseInvoiceItemId,
          "productId" to it.productId,
          "batchId" to it.batchId,
          "productNameSnapshot" to it.productNameSnapshot,
          "batchNumberSnapshot" to it.batchNumberSnapshot,
          "quantityPieces" to it.quantityPieces,
          "costPerPiecePaise" to it.costPerPiecePaise,
          "lineTotalPaise" to it.lineTotalPaise,
          "condition" to it.condition.name
        )
      },
      "subledger_entries" to subledger.map {
        linkedMapOf(
          "id" to it.id,
          "partyType" to it.partyType.name,
          "partyId" to it.partyId,
          "entryType" to it.entryType.name,
          "referenceType" to it.referenceType,
          "referenceId" to it.referenceId,
          "debitPaise" to it.debitPaise,
          "creditPaise" to it.creditPaise,
          "entryDate" to it.entryDate,
          "createdAt" to it.createdAt,
          "notes" to it.notes,
          "isReversed" to it.isReversed,
          "operationRef" to it.operationRef,
          "reversedAt" to it.reversedAt,
          "reversalReason" to it.reversalReason
        )
      },
      "payment_allocations" to allocations.map {
        linkedMapOf(
          "id" to it.id,
          "paymentId" to it.paymentId,
          "partyType" to it.partyType.name,
          "partyId" to it.partyId,
          "allocationType" to it.allocationType.name,
          "invoiceId" to it.invoiceId,
          "allocatedAmountPaise" to it.allocatedAmountPaise,
          "allocatedAt" to it.allocatedAt,
          "notes" to it.notes,
          "isReversed" to it.isReversed,
          "isFromAdvanceConsumption" to it.isFromAdvanceConsumption,
          "reversedAt" to it.reversedAt,
          "reversalReason" to it.reversalReason
        )
      },
      "credit_debit_notes" to creditDebitNotes.map {
        linkedMapOf(
          "id" to it.id,
          "noteNumber" to it.noteNumber,
          "noteType" to it.noteType.name,
          "partyType" to it.partyType.name,
          "partyId" to it.partyId,
          "linkedInvoiceId" to it.linkedInvoiceId,
          "linkedReturnId" to it.linkedReturnId,
          "subledgerEntryId" to it.subledgerEntryId,
          "amountPaise" to it.amountPaise,
          "appliedAmountPaise" to it.appliedAmountPaise,
          "status" to it.status.name,
          "issueDate" to it.issueDate,
          "reason" to it.reason,
          "notes" to it.notes,
          "idempotencyKey" to it.idempotencyKey,
          "createdAt" to it.createdAt,
          "updatedAt" to it.updatedAt
        )
      },
      "refund_records" to refundRecords.map {
        linkedMapOf(
          "id" to it.id,
          "partyType" to it.partyType.name,
          "partyId" to it.partyId,
          "sourceType" to it.sourceType.name,
          "sourceId" to it.sourceId,
          "amountPaise" to it.amountPaise,
          "paymentMethod" to it.paymentMethod.name,
          "executionStatus" to it.executionStatus.name,
          "reason" to it.reason,
          "notes" to it.notes,
          "referenceNumber" to it.referenceNumber,
          "subledgerEntryId" to it.subledgerEntryId,
          "recordedAt" to it.recordedAt,
          "executedAt" to it.executedAt,
          "idempotencyKey" to it.idempotencyKey
        )
      },
      "expenses" to expenses.map {
        linkedMapOf(
          "id" to it.id,
          "category" to it.category.name,
          "amountPaise" to it.amountPaise,
          "expenseDate" to it.expenseDate,
          "paymentMethod" to it.paymentMethod.name,
          "referenceNumber" to it.referenceNumber,
          "notes" to it.notes,
          "createdAt" to it.createdAt,
          "deletedAt" to it.deletedAt
        )
      },
      "customer_payment_reminders" to reminders.map {
        linkedMapOf(
          "id" to it.id,
          "customerId" to it.customerId,
          "targetPhoneE164" to it.targetPhoneE164,
          "dueKind" to it.dueKind.name,
          "status" to it.status.name,
          "snapshotPendingInvoiceCount" to it.snapshotPendingInvoiceCount,
          "snapshotTotalInvoicePaise" to it.snapshotTotalInvoicePaise,
          "snapshotTotalInvoicePendingPaise" to it.snapshotTotalInvoicePendingPaise,
          "snapshotNetOutstandingPaise" to it.snapshotNetOutstandingPaise,
          "renderedMessage" to it.renderedMessage,
          "whatsappPackageUsed" to it.whatsappPackageUsed,
          "failureReason" to it.failureReason,
          "awaitingUserConfirmation" to it.awaitingUserConfirmation,
          "createdAt" to it.createdAt,
          "whatsappOpenedAt" to it.whatsappOpenedAt,
          "confirmedSentAt" to it.confirmedSentAt,
          "updatedAt" to it.updatedAt
        )
      },
      "customer_payment_reminder_invoices" to reminderInvoices.map {
        linkedMapOf(
          "id" to it.id,
          "reminderId" to it.reminderId,
          "salesInvoiceId" to it.salesInvoiceId,
          "invoiceDateSnapshot" to it.invoiceDateSnapshot,
          "totalAmountPaiseSnapshot" to it.totalAmountPaiseSnapshot,
          "pendingAmountPaiseSnapshot" to it.pendingAmountPaiseSnapshot
        )
      },
      "app_settings" to settings.map {
        linkedMapOf(
          "key" to it.key,
          "value" to it.value
        )
      },
      "financial_audit_logs" to auditLogs.map {
        linkedMapOf(
          "id" to it.id,
          "operationType" to it.operationType.name,
          "entityType" to it.entityType,
          "entityId" to it.entityId,
          "relatedPartyType" to it.relatedPartyType?.name,
          "relatedPartyId" to it.relatedPartyId,
          "actorLabel" to it.actorLabel,
          "reason" to it.reason,
          "beforeJson" to it.beforeJson,
          "afterJson" to it.afterJson,
          "operationReference" to it.operationReference,
          "createdAt" to it.createdAt
        )
      }
    )

    return StrictJson.stringify(root, indent = 2)
  }

  /**
   * Strictly parses and validates a JSON backup string using RFC-8259 parsing (never regex).
   */
  fun validateBackupJson(jsonString: String): BackupValidationResult {
    return try {
      val root = StrictJson.parseObject(jsonString)
      val app = root["app"] as? String
      if (app != "IceTrack") {
        return BackupValidationResult(false, 0, 0, 0, 0, 0, "Invalid backup format: Not an IceTrack backup")
      }
      val schemaVersion = (root["schemaVersion"] as? Number)?.toInt() ?: 0
      if (schemaVersion !in 1..CURRENT_SCHEMA_VERSION) {
        return BackupValidationResult(false, 0, 0, 0, 0, 0, "Unsupported schema version: $schemaVersion")
      }

      fun arraySize(key: String): Int {
        val raw = root[key] ?: return 0
        val list = raw as? List<*>
          ?: throw IllegalArgumentException("Table '$key' must be a JSON array")
        for (entry in list) {
          if (entry !is Map<*, *>) {
            throw IllegalArgumentException("Each row in '$key' must be a JSON object")
          }
        }
        return list.size
      }

      val tableKeys = listOf(
        "products", "product_units", "product_suppliers", "suppliers", "customers",
        "customer_product_prices", "product_batches", "stock_movements",
        "purchase_invoices", "purchase_invoice_items", "sales_invoices", "sales_invoice_items",
        "sales_returns", "sales_return_items", "purchase_returns", "purchase_return_items",
        "subledger_entries", "payment_allocations", "credit_debit_notes", "refund_records",
        "expenses", "customer_payment_reminders", "customer_payment_reminder_invoices",
        "app_settings", "financial_audit_logs"
      )
      for (key in tableKeys) {
        arraySize(key)
      }

      val salesReturnsCount = arraySize("sales_returns")
      val purchaseReturnsCount = arraySize("purchase_returns")

      BackupValidationResult(
        isValid = true,
        productCount = arraySize("products"),
        customerCount = arraySize("customers"),
        supplierCount = arraySize("suppliers"),
        salesCount = arraySize("sales_invoices"),
        purchaseCount = arraySize("purchase_invoices"),
        schemaVersion = schemaVersion,
        subledgerCount = arraySize("subledger_entries"),
        returnCount = salesReturnsCount + purchaseReturnsCount,
        noteCount = arraySize("credit_debit_notes"),
        refundCount = arraySize("refund_records"),
        auditLogCount = arraySize("financial_audit_logs")
      )
    } catch (e: Exception) {
      BackupValidationResult(false, 0, 0, 0, 0, 0, "Malformed JSON: ${e.message}")
    }
  }

  /**
   * Atomically restores all 25 tables from a validated IceTrack backup JSON inside a single database transaction,
   * preserving primary keys and foreign keys in strict topological order, then records a BACKUP_RESTORE audit entry.
   */
  suspend fun restoreDatabaseFromJson(
    db: IceTrackDatabase,
    jsonString: String,
    reason: String = "Full database restore from backup"
  ): BackupValidationResult {
    val validation = validateBackupJson(jsonString)
    if (!validation.isValid) {
      throw IllegalArgumentException(validation.errorMessage ?: "Invalid backup JSON")
    }

    val root = StrictJson.parseObject(jsonString)

    @Suppress("UNCHECKED_CAST")
    fun rows(key: String): List<Map<String, Any?>> {
      val list = root[key] as? List<*> ?: return emptyList()
      return list.map { it as Map<String, Any?> }
    }

    fun Map<String, Any?>.str(k: String, default: String = ""): String =
      (this[k] as? String) ?: default

    fun Map<String, Any?>.strOrNull(k: String): String? =
      this[k] as? String

    fun Map<String, Any?>.lng(k: String, default: Long = 0L): Long =
      (this[k] as? Number)?.toLong() ?: default

    fun Map<String, Any?>.lngOrNull(k: String): Long? =
      (this[k] as? Number)?.toLong()

    fun Map<String, Any?>.intVal(k: String, default: Int = 0): Int =
      (this[k] as? Number)?.toInt() ?: default

    fun Map<String, Any?>.boolVal(k: String, default: Boolean = false): Boolean =
      (this[k] as? Boolean) ?: default

    fun <E : Enum<E>> Map<String, Any?>.enumVal(k: String, default: E, parser: (String) -> E): E {
      val raw = this[k] as? String ?: return default
      return runCatching { parser(raw) }.getOrDefault(default)
    }

    fun <E : Enum<E>> Map<String, Any?>.enumOrNull(k: String, parser: (String) -> E): E? {
      val raw = this[k] as? String ?: return null
      return runCatching { parser(raw) }.getOrNull()
    }

    db.withTransaction {
      val writableDb = db.openHelper.writableDatabase
      val deleteOrder = listOf(
        "customer_payment_reminder_invoices",
        "customer_payment_reminders",
        "financial_audit_logs",
        "refund_records",
        "credit_debit_notes",
        "payment_allocations",
        "subledger_entries",
        "purchase_return_items",
        "purchase_returns",
        "sales_return_items",
        "sales_returns",
        "sales_invoice_items",
        "sales_invoices",
        "purchase_invoice_items",
        "purchase_invoices",
        "stock_movements",
        "product_batches",
        "customer_product_prices",
        "product_suppliers",
        "product_units",
        "expenses",
        "app_settings",
        "customers",
        "suppliers",
        "products"
      )
      for (table in deleteOrder) {
        writableDb.execSQL("DELETE FROM $table")
      }

      // 1. Master parents
      val productDao = db.productDao()
      rows("products").forEach { r ->
        productDao.insert(
          ProductEntity(
            id = r.lng("id"),
            name = r.str("name"),
            sku = r.str("sku"),
            category = r.str("category"),
            defaultPurchasePricePaise = r.lng("defaultPurchasePricePaise"),
            defaultSellingPricePaise = r.lng("defaultSellingPricePaise"),
            minStockLevelPieces = r.intVal("minStockLevelPieces"),
            isActive = r.boolVal("isActive", true),
            createdAt = r.lng("createdAt", System.currentTimeMillis()),
            deletedAt = r.lngOrNull("deletedAt")
          )
        )
      }

      val supplierDao = db.supplierDao()
      rows("suppliers").forEach { r ->
        supplierDao.insert(
          SupplierEntity(
            id = r.lng("id"),
            name = r.str("name"),
            contactPerson = r.str("contactPerson"),
            phoneNumber = r.str("phoneNumber"),
            address = r.str("address"),
            openingBalancePaise = r.lng("openingBalancePaise"),
            openingBalanceDate = r.lng("openingBalanceDate", System.currentTimeMillis()),
            creditTermsDays = r.intVal("creditTermsDays", 15),
            creditLimitPaise = r.lng("creditLimitPaise"),
            isActive = r.boolVal("isActive", true),
            createdAt = r.lng("createdAt", System.currentTimeMillis()),
            deletedAt = r.lngOrNull("deletedAt")
          )
        )
      }

      val customerDao = db.customerDao()
      rows("customers").forEach { r ->
        customerDao.insert(
          CustomerEntity(
            id = r.lng("id"),
            shopName = r.str("shopName"),
            ownerName = r.str("ownerName"),
            phoneNumber = r.str("phoneNumber"),
            address = r.str("address"),
            openingBalancePaise = r.lng("openingBalancePaise"),
            openingBalanceDate = r.lng("openingBalanceDate", System.currentTimeMillis()),
            creditLimitPaise = r.lng("creditLimitPaise"),
            paymentTermsDays = r.intVal("paymentTermsDays", 7),
            isActive = r.boolVal("isActive", true),
            createdAt = r.lng("createdAt", System.currentTimeMillis()),
            deletedAt = r.lngOrNull("deletedAt")
          )
        )
      }

      val appSettingDao = db.appSettingDao()
      rows("app_settings").forEach { r ->
        appSettingDao.set(AppSettingEntity(key = r.str("key"), value = r.str("value")))
      }

      val expenseDao = db.expenseDao()
      rows("expenses").forEach { r ->
        expenseDao.insert(
          ExpenseEntity(
            id = r.lng("id"),
            category = r.enumVal("category", ExpenseCategory.MISCELLANEOUS, ExpenseCategory::valueOf),
            amountPaise = r.lng("amountPaise"),
            expenseDate = r.lng("expenseDate"),
            paymentMethod = r.enumVal("paymentMethod", PaymentMethod.CASH, PaymentMethod::valueOf),
            referenceNumber = r.str("referenceNumber"),
            notes = r.str("notes"),
            createdAt = r.lng("createdAt", System.currentTimeMillis()),
            deletedAt = r.lngOrNull("deletedAt")
          )
        )
      }

      // 2. Product relations
      val productUnitDao = db.productUnitDao()
      rows("product_units").forEach { r ->
        productUnitDao.insertOrUpdate(
          ProductUnitEntity(
            id = r.lng("id"),
            productId = r.lng("productId"),
            piecesPerBox = r.intVal("piecesPerBox", 1),
            boxesPerCarton = r.intVal("boxesPerCarton", 1)
          )
        )
      }

      val productSupplierDao = db.productSupplierDao()
      rows("product_suppliers").forEach { r ->
        productSupplierDao.insertOrUpdate(
          ProductSupplierEntity(
            id = r.lng("id"),
            productId = r.lng("productId"),
            supplierId = r.lng("supplierId"),
            supplierProductCode = r.str("supplierProductCode"),
            purchaseCostPaise = r.lng("purchaseCostPaise"),
            isPreferredSupplier = r.boolVal("isPreferredSupplier", false)
          )
        )
      }

      val customPriceDao = db.customerProductPriceDao()
      rows("customer_product_prices").forEach { r ->
        customPriceDao.insertOrUpdate(
          CustomerProductPriceEntity(
            id = r.lng("id"),
            customerId = r.lng("customerId"),
            productId = r.lng("productId"),
            customSellingPricePaise = r.lng("customSellingPricePaise")
          )
        )
      }

      // 3. Batches & Stock Movements
      val batchDao = db.productBatchDao()
      rows("product_batches").forEach { r ->
        batchDao.insert(
          ProductBatchEntity(
            id = r.lng("id"),
            productId = r.lng("productId"),
            batchNumber = r.str("batchNumber"),
            purchaseInvoiceId = r.lngOrNull("purchaseInvoiceId"),
            purchaseDate = r.lng("purchaseDate"),
            expiryDate = r.lng("expiryDate"),
            initialQuantityPieces = r.intVal("initialQuantityPieces"),
            currentQuantityPieces = r.intVal("currentQuantityPieces"),
            purchaseCostPerPiecePaise = r.lng("purchaseCostPerPiecePaise"),
            isActive = r.boolVal("isActive", true),
            createdAt = r.lng("createdAt", System.currentTimeMillis())
          )
        )
      }

      val movementDao = db.stockMovementDao()
      rows("stock_movements").forEach { r ->
        movementDao.insert(
          StockMovementEntity(
            id = r.lng("id"),
            productId = r.lng("productId"),
            batchId = r.lngOrNull("batchId"),
            movementType = r.enumVal("movementType", StockMovementType.STOCK_ADJUSTMENT, StockMovementType::valueOf),
            quantityPieces = r.intVal("quantityPieces"),
            unitCostPaise = r.lng("unitCostPaise"),
            referenceType = r.str("referenceType"),
            referenceId = r.lng("referenceId"),
            movementDate = r.lng("movementDate"),
            notes = r.str("notes"),
            createdAt = r.lng("createdAt", System.currentTimeMillis())
          )
        )
      }

      // 4. Purchase & Sales Invoices + Items
      val purchaseDao = db.purchaseInvoiceDao()
      rows("purchase_invoices").forEach { r ->
        val total = r.lng("totalAmountPaise")
        val created = r.lng("createdAt", System.currentTimeMillis())
        purchaseDao.insert(
          PurchaseInvoiceEntity(
            id = r.lng("id"),
            invoiceNumber = r.str("invoiceNumber"),
            supplierId = r.lng("supplierId"),
            invoiceDate = r.lng("invoiceDate"),
            dueDate = r.lng("dueDate"),
            totalAmountPaise = total,
            paidAmountPaise = r.lng("paidAmountPaise"),
            paymentStatus = r.enumVal("paymentStatus", PaymentStatus.UNPAID, PaymentStatus::valueOf),
            paymentMethod = r.enumOrNull("paymentMethod", PaymentMethod::valueOf),
            notes = r.str("notes"),
            isConfirmed = r.boolVal("isConfirmed", true),
            createdAt = created,
            subtotalPaise = r.lngOrNull("subtotalPaise") ?: total,
            discountType = r.enumVal("discountType", DiscountType.NONE, DiscountType::valueOf),
            discountValueRaw = r.lng("discountValueRaw"),
            discountAmountPaise = r.lng("discountAmountPaise"),
            additionalChargesPaise = r.lng("additionalChargesPaise"),
            invoiceStatus = r.enumVal("invoiceStatus", InvoiceStatus.ACTIVE, InvoiceStatus::valueOf),
            cancelledAt = r.lngOrNull("cancelledAt"),
            cancellationReason = r.strOrNull("cancellationReason"),
            revisionVersion = r.intVal("revisionVersion", 1),
            updatedAt = r.lng("updatedAt", created),
            partyNameSnapshot = r.str("partyNameSnapshot"),
            partyPhoneSnapshot = r.str("partyPhoneSnapshot"),
            partyAddressSnapshot = r.str("partyAddressSnapshot")
          )
        )
      }

      val purchaseItems = rows("purchase_invoice_items").map { r ->
        val qty = r.intVal("quantityPieces")
        val cost = r.lng("costPerPiecePaise")
        PurchaseInvoiceItemEntity(
          id = r.lng("id"),
          purchaseInvoiceId = r.lng("purchaseInvoiceId"),
          productId = r.lng("productId"),
          batchNumber = r.str("batchNumber"),
          expiryDate = r.lng("expiryDate"),
          quantityPieces = qty,
          costPerPiecePaise = cost,
          lineTotalPaise = r.lng("lineTotalPaise"),
          productNameSnapshot = r.str("productNameSnapshot"),
          unitType = r.enumVal("unitType", UnitType.PIECE, UnitType::valueOf),
          unitQuantity = r.intVal("unitQuantity", qty),
          unitRatePaise = r.lng("unitRatePaise", cost),
          itemDiscountPaise = r.lng("itemDiscountPaise")
        )
      }
      if (purchaseItems.isNotEmpty()) db.purchaseInvoiceItemDao().insertAll(purchaseItems)

      val salesDao = db.salesInvoiceDao()
      rows("sales_invoices").forEach { r ->
        val total = r.lng("totalAmountPaise")
        val created = r.lng("createdAt", System.currentTimeMillis())
        salesDao.insert(
          SalesInvoiceEntity(
            id = r.lng("id"),
            invoiceNumber = r.str("invoiceNumber"),
            customerId = r.lng("customerId"),
            invoiceDate = r.lng("invoiceDate"),
            dueDate = r.lng("dueDate"),
            totalAmountPaise = total,
            paidAmountPaise = r.lng("paidAmountPaise"),
            paymentStatus = r.enumVal("paymentStatus", PaymentStatus.UNPAID, PaymentStatus::valueOf),
            paymentMethod = r.enumOrNull("paymentMethod", PaymentMethod::valueOf),
            notes = r.str("notes"),
            isConfirmed = r.boolVal("isConfirmed", true),
            createdAt = created,
            subtotalPaise = r.lngOrNull("subtotalPaise") ?: total,
            discountType = r.enumVal("discountType", DiscountType.NONE, DiscountType::valueOf),
            discountValueRaw = r.lng("discountValueRaw"),
            discountAmountPaise = r.lng("discountAmountPaise"),
            additionalChargesPaise = r.lng("additionalChargesPaise"),
            invoiceStatus = r.enumVal("invoiceStatus", InvoiceStatus.ACTIVE, InvoiceStatus::valueOf),
            cancelledAt = r.lngOrNull("cancelledAt"),
            cancellationReason = r.strOrNull("cancellationReason"),
            revisionVersion = r.intVal("revisionVersion", 1),
            updatedAt = r.lng("updatedAt", created),
            partyNameSnapshot = r.str("partyNameSnapshot"),
            partyPhoneSnapshot = r.str("partyPhoneSnapshot"),
            partyAddressSnapshot = r.str("partyAddressSnapshot")
          )
        )
      }

      val salesItems = rows("sales_invoice_items").map { r ->
        val qty = r.intVal("quantityPieces")
        val price = r.lng("unitPricePaise")
        SalesInvoiceItemEntity(
          id = r.lng("id"),
          salesInvoiceId = r.lng("salesInvoiceId"),
          productId = r.lng("productId"),
          batchId = r.lng("batchId"),
          quantityPieces = qty,
          unitPricePaise = price,
          lineTotalPaise = r.lng("lineTotalPaise"),
          productNameSnapshot = r.str("productNameSnapshot"),
          unitType = r.enumVal("unitType", UnitType.PIECE, UnitType::valueOf),
          unitQuantity = r.intVal("unitQuantity", qty),
          unitRatePaise = r.lng("unitRatePaise", price),
          itemDiscountPaise = r.lng("itemDiscountPaise")
        )
      }
      if (salesItems.isNotEmpty()) db.salesInvoiceItemDao().insertAll(salesItems)

      // 5. Sales & Purchase Returns + Items
      val salesReturnDao = db.salesReturnDao()
      rows("sales_returns").forEach { r ->
        salesReturnDao.insertReturn(
          SalesReturnEntity(
            id = r.lng("id"),
            returnNumber = r.str("returnNumber"),
            salesInvoiceId = r.lng("salesInvoiceId"),
            customerId = r.lng("customerId"),
            returnDate = r.lng("returnDate"),
            totalReturnAmountPaise = r.lng("totalReturnAmountPaise"),
            settlementType = r.enumVal("settlementType", ReturnSettlementType.CREDIT_NOTE, ReturnSettlementType::valueOf),
            creditNoteId = r.lngOrNull("creditNoteId"),
            refundRecordId = r.lngOrNull("refundRecordId"),
            subledgerEntryId = r.lngOrNull("subledgerEntryId"),
            reason = r.str("reason"),
            notes = r.str("notes"),
            idempotencyKey = r.strOrNull("idempotencyKey"),
            createdAt = r.lng("createdAt", System.currentTimeMillis())
          )
        )
      }
      val srItems = rows("sales_return_items").map { r ->
        val condition = r.enumVal("condition", ReturnCondition.RESELLABLE, ReturnCondition::valueOf)
        SalesReturnItemEntity(
          id = r.lng("id"),
          salesReturnId = r.lng("salesReturnId"),
          originalSalesInvoiceItemId = r.lngOrNull("originalSalesInvoiceItemId"),
          productId = r.lng("productId"),
          batchId = r.lng("batchId"),
          productNameSnapshot = r.str("productNameSnapshot"),
          quantityPieces = r.intVal("quantityPieces"),
          unitPricePaise = r.lng("unitPricePaise"),
          lineTotalPaise = r.lng("lineTotalPaise"),
          condition = condition,
          restoredToSaleableStock = r.boolVal("restoredToSaleableStock", condition.isSaleable)
        )
      }
      if (srItems.isNotEmpty()) salesReturnDao.insertItems(srItems)

      val purchaseReturnDao = db.purchaseReturnDao()
      rows("purchase_returns").forEach { r ->
        purchaseReturnDao.insertReturn(
          PurchaseReturnEntity(
            id = r.lng("id"),
            returnNumber = r.str("returnNumber"),
            purchaseInvoiceId = r.lng("purchaseInvoiceId"),
            supplierId = r.lng("supplierId"),
            returnDate = r.lng("returnDate"),
            totalReturnAmountPaise = r.lng("totalReturnAmountPaise"),
            settlementType = r.enumVal("settlementType", ReturnSettlementType.DEBIT_NOTE, ReturnSettlementType::valueOf),
            debitNoteId = r.lngOrNull("debitNoteId"),
            refundRecordId = r.lngOrNull("refundRecordId"),
            subledgerEntryId = r.lngOrNull("subledgerEntryId"),
            reason = r.str("reason"),
            notes = r.str("notes"),
            idempotencyKey = r.strOrNull("idempotencyKey"),
            createdAt = r.lng("createdAt", System.currentTimeMillis())
          )
        )
      }
      val prItems = rows("purchase_return_items").map { r ->
        PurchaseReturnItemEntity(
          id = r.lng("id"),
          purchaseReturnId = r.lng("purchaseReturnId"),
          originalPurchaseInvoiceItemId = r.lngOrNull("originalPurchaseInvoiceItemId"),
          productId = r.lng("productId"),
          batchId = r.lng("batchId"),
          productNameSnapshot = r.str("productNameSnapshot"),
          batchNumberSnapshot = r.str("batchNumberSnapshot"),
          quantityPieces = r.intVal("quantityPieces"),
          costPerPiecePaise = r.lng("costPerPiecePaise"),
          lineTotalPaise = r.lng("lineTotalPaise"),
          condition = r.enumVal("condition", ReturnCondition.RESELLABLE, ReturnCondition::valueOf)
        )
      }
      if (prItems.isNotEmpty()) purchaseReturnDao.insertItems(prItems)

      // 6. Subledger, Allocations, Notes, Refunds
      val subledgerDao = db.subledgerDao()
      rows("subledger_entries").forEach { r ->
        subledgerDao.insert(
          SubledgerEntryEntity(
            id = r.lng("id"),
            partyType = r.enumVal("partyType", PartyType.CUSTOMER, PartyType::valueOf),
            partyId = r.lng("partyId"),
            entryType = r.enumVal("entryType", SubledgerEntryType.ADJUSTMENT, SubledgerEntryType::valueOf),
            referenceType = r.str("referenceType"),
            referenceId = r.lng("referenceId"),
            debitPaise = r.lng("debitPaise"),
            creditPaise = r.lng("creditPaise"),
            entryDate = r.lng("entryDate"),
            createdAt = r.lng("createdAt", System.currentTimeMillis()),
            notes = r.str("notes"),
            isReversed = r.boolVal("isReversed", false),
            operationRef = r.str("operationRef"),
            reversedAt = r.lngOrNull("reversedAt"),
            reversalReason = r.strOrNull("reversalReason")
          )
        )
      }

      val allocationDao = db.paymentAllocationDao()
      rows("payment_allocations").forEach { r ->
        allocationDao.insert(
          PaymentAllocationEntity(
            id = r.lng("id"),
            paymentId = r.lng("paymentId"),
            partyType = r.enumVal("partyType", PartyType.CUSTOMER, PartyType::valueOf),
            partyId = r.lng("partyId"),
            allocationType = r.enumVal("allocationType", PaymentAllocationType.INVOICE, PaymentAllocationType::valueOf),
            invoiceId = r.lngOrNull("invoiceId"),
            allocatedAmountPaise = r.lng("allocatedAmountPaise"),
            allocatedAt = r.lng("allocatedAt", System.currentTimeMillis()),
            notes = r.str("notes"),
            isReversed = r.boolVal("isReversed", false),
            isFromAdvanceConsumption = r.boolVal("isFromAdvanceConsumption", false),
            reversedAt = r.lngOrNull("reversedAt"),
            reversalReason = r.strOrNull("reversalReason")
          )
        )
      }

      val noteDao = db.creditDebitNoteDao()
      rows("credit_debit_notes").forEach { r ->
        val created = r.lng("createdAt", System.currentTimeMillis())
        noteDao.insert(
          CreditDebitNoteEntity(
            id = r.lng("id"),
            noteNumber = r.str("noteNumber"),
            noteType = r.enumVal("noteType", CreditDebitNoteType.CREDIT_NOTE, CreditDebitNoteType::valueOf),
            partyType = r.enumVal("partyType", PartyType.CUSTOMER, PartyType::valueOf),
            partyId = r.lng("partyId"),
            linkedInvoiceId = r.lngOrNull("linkedInvoiceId"),
            linkedReturnId = r.lngOrNull("linkedReturnId"),
            subledgerEntryId = r.lngOrNull("subledgerEntryId"),
            amountPaise = r.lng("amountPaise"),
            appliedAmountPaise = r.lng("appliedAmountPaise"),
            status = r.enumVal("status", NoteStatus.OPEN, NoteStatus::valueOf),
            issueDate = r.lng("issueDate"),
            reason = r.str("reason"),
            notes = r.str("notes"),
            idempotencyKey = r.strOrNull("idempotencyKey"),
            createdAt = created,
            updatedAt = r.lng("updatedAt", created)
          )
        )
      }

      val refundDao = db.refundRecordDao()
      rows("refund_records").forEach { r ->
        refundDao.insert(
          RefundRecordEntity(
            id = r.lng("id"),
            partyType = r.enumVal("partyType", PartyType.CUSTOMER, PartyType::valueOf),
            partyId = r.lng("partyId"),
            sourceType = r.enumVal("sourceType", RefundSourceType.SALES_RETURN, RefundSourceType::valueOf),
            sourceId = r.lng("sourceId"),
            amountPaise = r.lng("amountPaise"),
            paymentMethod = r.enumVal("paymentMethod", PaymentMethod.CASH, PaymentMethod::valueOf),
            executionStatus = r.enumVal("executionStatus", RefundExecutionStatus.RECORDED_PENDING_PAYOUT, RefundExecutionStatus::valueOf),
            reason = r.str("reason"),
            notes = r.str("notes"),
            referenceNumber = r.str("referenceNumber"),
            subledgerEntryId = r.lngOrNull("subledgerEntryId"),
            recordedAt = r.lng("recordedAt", System.currentTimeMillis()),
            executedAt = r.lngOrNull("executedAt"),
            idempotencyKey = r.strOrNull("idempotencyKey")
          )
        )
      }

      // 7. Payment Reminders + Snapshot Invoices
      val reminderDao = db.customerPaymentReminderDao()
      rows("customer_payment_reminders").forEach { r ->
        val created = r.lng("createdAt", System.currentTimeMillis())
        reminderDao.insertReminder(
          CustomerPaymentReminderEntity(
            id = r.lng("id"),
            customerId = r.lng("customerId"),
            targetPhoneE164 = r.str("targetPhoneE164"),
            dueKind = r.enumVal("dueKind", ReminderDueKind.INVOICE_BASED, ReminderDueKind::valueOf),
            status = r.enumVal("status", ReminderStatus.DRAFT, ReminderStatus::valueOf),
            snapshotPendingInvoiceCount = r.intVal("snapshotPendingInvoiceCount"),
            snapshotTotalInvoicePaise = r.lng("snapshotTotalInvoicePaise"),
            snapshotTotalInvoicePendingPaise = r.lng("snapshotTotalInvoicePendingPaise"),
            snapshotNetOutstandingPaise = r.lng("snapshotNetOutstandingPaise"),
            renderedMessage = r.str("renderedMessage"),
            whatsappPackageUsed = r.strOrNull("whatsappPackageUsed"),
            failureReason = r.strOrNull("failureReason"),
            awaitingUserConfirmation = r.boolVal("awaitingUserConfirmation", false),
            createdAt = created,
            whatsappOpenedAt = r.lngOrNull("whatsappOpenedAt"),
            confirmedSentAt = r.lngOrNull("confirmedSentAt"),
            updatedAt = r.lng("updatedAt", created)
          )
        )
      }
      val remInvs = rows("customer_payment_reminder_invoices").map { r ->
        CustomerPaymentReminderInvoiceEntity(
          id = r.lng("id"),
          reminderId = r.lng("reminderId"),
          salesInvoiceId = r.lng("salesInvoiceId"),
          invoiceDateSnapshot = r.lng("invoiceDateSnapshot"),
          totalAmountPaiseSnapshot = r.lng("totalAmountPaiseSnapshot"),
          pendingAmountPaiseSnapshot = r.lng("pendingAmountPaiseSnapshot")
        )
      }
      if (remInvs.isNotEmpty()) reminderDao.insertReminderInvoices(remInvs)

      // 8. Historical Audit Logs + New BACKUP_RESTORE Audit Entry
      val auditDao = db.financialAuditLogDao()
      rows("financial_audit_logs").forEach { r ->
        auditDao.insert(
          FinancialAuditLogEntity(
            id = r.lng("id"),
            operationType = r.enumVal("operationType", AuditOperationType.BACKUP_RESTORE, AuditOperationType::valueOf),
            entityType = r.str("entityType"),
            entityId = r.lng("entityId"),
            relatedPartyType = r.enumOrNull("relatedPartyType", PartyType::valueOf),
            relatedPartyId = r.lngOrNull("relatedPartyId"),
            actorLabel = r.str("actorLabel", "Owner"),
            reason = r.str("reason"),
            beforeJson = r.str("beforeJson"),
            afterJson = r.str("afterJson"),
            operationReference = r.str("operationReference"),
            createdAt = r.lng("createdAt", System.currentTimeMillis())
          )
        )
      }

      auditDao.insert(
        FinancialAuditLogEntity(
          operationType = AuditOperationType.BACKUP_RESTORE,
          entityType = "DATABASE",
          entityId = 0L,
          reason = reason.trim().ifBlank { "Database restored from JSON backup" },
          afterJson = StrictJson.stringify(
            linkedMapOf(
              "schemaVersion" to validation.schemaVersion,
              "products" to validation.productCount,
              "customers" to validation.customerCount,
              "suppliers" to validation.supplierCount,
              "sales" to validation.salesCount,
              "purchases" to validation.purchaseCount,
              "subledger" to validation.subledgerCount
            ),
            indent = 0
          ),
          operationReference = "RESTORE-V${validation.schemaVersion}",
          createdAt = System.currentTimeMillis()
        )
      )
    }

    return validation
  }
}

/**
 * Pure-Kotlin strict RFC-8259 JSON parser & serializer that executes identically
 * in Android runtime, Robolectric, and plain JVM unit tests without android.jar stub failures.
 */
internal object StrictJson {

  fun parseObject(input: String): Map<String, Any?> {
    val parser = Parser(input)
    parser.skipWhitespace()
    val value = parser.parseObjectInternal()
    parser.skipWhitespace()
    if (!parser.isAtEnd()) {
      throw IllegalArgumentException("Unexpected trailing characters at index ${parser.pos}")
    }
    return value
  }

  fun stringify(value: Any?, indent: Int = 2): String {
    val sb = StringBuilder()
    writeValue(sb, value, indent, 0)
    return sb.toString()
  }

  private fun writeValue(sb: StringBuilder, value: Any?, indent: Int, depth: Int) {
    when (value) {
      null -> sb.append("null")
      is Boolean -> sb.append(if (value) "true" else "false")
      is Number -> sb.append(value.toString())
      is String -> writeString(sb, value)
      is Map<*, *> -> {
        if (value.isEmpty()) {
          sb.append("{}")
          return
        }
        sb.append('{')
        var first = true
        for ((k, v) in value) {
          if (!first) sb.append(',')
          first = false
          if (indent > 0) {
            sb.append('\n')
            repeat((depth + 1) * indent) { sb.append(' ') }
          }
          writeString(sb, k.toString())
          sb.append(if (indent > 0) ": " else ":")
          writeValue(sb, v, indent, depth + 1)
        }
        if (indent > 0) {
          sb.append('\n')
          repeat(depth * indent) { sb.append(' ') }
        }
        sb.append('}')
      }
      is Iterable<*> -> {
        val list = value.toList()
        if (list.isEmpty()) {
          sb.append("[]")
          return
        }
        sb.append('[')
        list.forEachIndexed { idx, elem ->
          if (idx > 0) sb.append(',')
          if (indent > 0) {
            sb.append('\n')
            repeat((depth + 1) * indent) { sb.append(' ') }
          }
          writeValue(sb, elem, indent, depth + 1)
        }
        if (indent > 0) {
          sb.append('\n')
          repeat(depth * indent) { sb.append(' ') }
        }
        sb.append(']')
      }
      else -> writeString(sb, value.toString())
    }
  }

  private fun writeString(sb: StringBuilder, s: String) {
    sb.append('"')
    for (ch in s) {
      when (ch) {
        '"' -> sb.append("\\\"")
        '\\' -> sb.append("\\\\")
        '\b' -> sb.append("\\b")
        '\u000C' -> sb.append("\\f")
        '\n' -> sb.append("\\n")
        '\r' -> sb.append("\\r")
        '\t' -> sb.append("\\t")
        else -> if (ch.code < 0x20) {
          sb.append("\\u%04x".format(ch.code))
        } else {
          sb.append(ch)
        }
      }
    }
    sb.append('"')
  }

  private class Parser(private val s: String) {
    var pos: Int = 0

    fun isAtEnd(): Boolean = pos >= s.length

    fun skipWhitespace() {
      while (pos < s.length && s[pos].isWhitespace()) pos++
    }

    private fun peek(): Char {
      if (pos >= s.length) throw IllegalArgumentException("Unexpected end of JSON input")
      return s[pos]
    }

    private fun expect(expected: Char) {
      skipWhitespace()
      val actual = peek()
      if (actual != expected) {
        throw IllegalArgumentException("Expected '$expected' at index $pos but found '$actual'")
      }
      pos++
    }

    fun parseValue(): Any? {
      skipWhitespace()
      return when (peek()) {
        '{' -> parseObjectInternal()
        '[' -> parseArrayInternal()
        '"' -> parseStringInternal()
        't', 'f' -> parseBooleanInternal()
        'n' -> parseNullInternal()
        '-', in '0'..'9' -> parseNumberInternal()
        else -> throw IllegalArgumentException("Unexpected token '${s[pos]}' at index $pos")
      }
    }

    fun parseObjectInternal(): Map<String, Any?> {
      expect('{')
      skipWhitespace()
      val map = linkedMapOf<String, Any?>()
      if (!isAtEnd() && s[pos] == '}') {
        pos++
        return map
      }
      while (true) {
        skipWhitespace()
        if (peek() != '"') {
          throw IllegalArgumentException("Expected string key at index $pos")
        }
        val key = parseStringInternal()
        expect(':')
        val value = parseValue()
        map[key] = value
        skipWhitespace()
        when (peek()) {
          ',' -> pos++
          '}' -> {
            pos++
            return map
          }
          else -> throw IllegalArgumentException("Expected ',' or '}' at index $pos")
        }
      }
    }

    private fun parseArrayInternal(): List<Any?> {
      expect('[')
      skipWhitespace()
      val list = mutableListOf<Any?>()
      if (!isAtEnd() && s[pos] == ']') {
        pos++
        return list
      }
      while (true) {
        val value = parseValue()
        list.add(value)
        skipWhitespace()
        when (peek()) {
          ',' -> pos++
          ']' -> {
            pos++
            return list
          }
          else -> throw IllegalArgumentException("Expected ',' or ']' at index $pos")
        }
      }
    }

    private fun parseStringInternal(): String {
      expect('"')
      val sb = StringBuilder()
      while (pos < s.length) {
        val ch = s[pos++]
        when (ch) {
          '"' -> return sb.toString()
          '\\' -> {
            if (pos >= s.length) throw IllegalArgumentException("Unterminated escape sequence")
            when (val esc = s[pos++]) {
              '"' -> sb.append('"')
              '\\' -> sb.append('\\')
              '/' -> sb.append('/')
              'b' -> sb.append('\b')
              'f' -> sb.append('\u000C')
              'n' -> sb.append('\n')
              'r' -> sb.append('\r')
              't' -> sb.append('\t')
              'u' -> {
                if (pos + 4 > s.length) throw IllegalArgumentException("Invalid unicode escape at index $pos")
                val code = s.substring(pos, pos + 4).toIntOrNull(16)
                  ?: throw IllegalArgumentException("Invalid hex unicode escape at index $pos")
                sb.append(code.toChar())
                pos += 4
              }
              else -> throw IllegalArgumentException("Invalid escape '\\$esc' at index ${pos - 1}")
            }
          }
          else -> {
            if (ch.code < 0x20) {
              throw IllegalArgumentException("Unescaped control character in string at index ${pos - 1}")
            }
            sb.append(ch)
          }
        }
      }
      throw IllegalArgumentException("Unterminated string literal")
    }

    private fun parseBooleanInternal(): Boolean {
      return if (s.startsWith("true", pos)) {
        pos += 4
        true
      } else if (s.startsWith("false", pos)) {
        pos += 5
        false
      } else {
        throw IllegalArgumentException("Invalid boolean literal at index $pos")
      }
    }

    private fun parseNullInternal(): Any? {
      if (s.startsWith("null", pos)) {
        pos += 4
        return null
      }
      throw IllegalArgumentException("Invalid null literal at index $pos")
    }

    private fun parseNumberInternal(): Number {
      val start = pos
      if (s[pos] == '-') pos++
      if (pos >= s.length || !s[pos].isDigit()) {
        throw IllegalArgumentException("Invalid number at index $start")
      }
      while (pos < s.length && s[pos].isDigit()) pos++
      var isDecimal = false
      if (pos < s.length && s[pos] == '.') {
        isDecimal = true
        pos++
        if (pos >= s.length || !s[pos].isDigit()) {
          throw IllegalArgumentException("Invalid decimal fraction at index $start")
        }
        while (pos < s.length && s[pos].isDigit()) pos++
      }
      if (pos < s.length && (s[pos] == 'e' || s[pos] == 'E')) {
        isDecimal = true
        pos++
        if (pos < s.length && (s[pos] == '+' || s[pos] == '-')) pos++
        if (pos >= s.length || !s[pos].isDigit()) {
          throw IllegalArgumentException("Invalid exponent at index $start")
        }
        while (pos < s.length && s[pos].isDigit()) pos++
      }
      val raw = s.substring(start, pos)
      return if (isDecimal) raw.toDouble() else raw.toLong()
    }
  }
}
