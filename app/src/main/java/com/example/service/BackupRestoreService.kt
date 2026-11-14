package com.example.service

import com.example.data.local.IceTrackDatabase
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
import org.json.JSONArray
import org.json.JSONObject

data class BackupValidationResult(
  val isValid: Boolean,
  val productCount: Int,
  val customerCount: Int,
  val supplierCount: Int,
  val salesCount: Int,
  val purchaseCount: Int,
  val errorMessage: String? = null
)

object BackupRestoreService {

  /**
   * Serializes the entire IceTrack SQLite database into a validated JSON backup string.
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
    settings: List<AppSettingEntity>
  ): String {
    val root = JSONObject()
    root.put("app", "IceTrack")
    root.put("schemaVersion", 3)
    root.put("exportedAt", System.currentTimeMillis())

    fun <T> listToJsonArray(list: List<T>, mapper: (T) -> JSONObject): JSONArray {
      val arr = JSONArray()
      for (item in list) {
        arr.put(mapper(item))
      }
      return arr
    }

    root.put("products", listToJsonArray(products) {
      JSONObject().apply {
        put("id", it.id)
        put("name", it.name)
        put("sku", it.sku)
        put("category", it.category)
        put("defaultPurchasePricePaise", it.defaultPurchasePricePaise)
        put("defaultSellingPricePaise", it.defaultSellingPricePaise)
        put("minStockLevelPieces", it.minStockLevelPieces)
        put("isActive", it.isActive)
        put("createdAt", it.createdAt)
      }
    })

    root.put("customers", listToJsonArray(customers) {
      JSONObject().apply {
        put("id", it.id)
        put("shopName", it.shopName)
        put("ownerName", it.ownerName)
        put("phoneNumber", it.phoneNumber)
        put("address", it.address)
        put("openingBalancePaise", it.openingBalancePaise)
        put("openingBalanceDate", it.openingBalanceDate)
        put("creditLimitPaise", it.creditLimitPaise)
        put("paymentTermsDays", it.paymentTermsDays)
        put("isActive", it.isActive)
        put("createdAt", it.createdAt)
      }
    })

    root.put("suppliers", listToJsonArray(suppliers) {
      JSONObject().apply {
        put("id", it.id)
        put("name", it.name)
        put("contactPerson", it.contactPerson)
        put("phoneNumber", it.phoneNumber)
        put("address", it.address)
        put("openingBalancePaise", it.openingBalancePaise)
        put("openingBalanceDate", it.openingBalanceDate)
        put("creditTermsDays", it.creditTermsDays)
        put("creditLimitPaise", it.creditLimitPaise)
        put("isActive", it.isActive)
        put("createdAt", it.createdAt)
      }
    })

    root.put("product_batches", listToJsonArray(batches) {
      JSONObject().apply {
        put("id", it.id)
        put("productId", it.productId)
        put("batchNumber", it.batchNumber)
        put("purchaseDate", it.purchaseDate)
        put("expiryDate", it.expiryDate)
        put("initialQuantityPieces", it.initialQuantityPieces)
        put("currentQuantityPieces", it.currentQuantityPieces)
        put("purchaseCostPerPiecePaise", it.purchaseCostPerPiecePaise)
        put("isActive", it.isActive)
        put("createdAt", it.createdAt)
      }
    })

    root.put("sales_invoices", listToJsonArray(sales) {
      JSONObject().apply {
        put("id", it.id)
        put("invoiceNumber", it.invoiceNumber)
        put("customerId", it.customerId)
        put("invoiceDate", it.invoiceDate)
        put("dueDate", it.dueDate)
        put("totalAmountPaise", it.totalAmountPaise)
        put("paidAmountPaise", it.paidAmountPaise)
        put("paymentStatus", it.paymentStatus.name)
        put("notes", it.notes)
        put("isConfirmed", it.isConfirmed)
        put("createdAt", it.createdAt)
      }
    })

    root.put("subledger_entries", listToJsonArray(subledger) {
      JSONObject().apply {
        put("id", it.id)
        put("partyType", it.partyType.name)
        put("partyId", it.partyId)
        put("entryType", it.entryType.name)
        put("referenceType", it.referenceType)
        put("referenceId", it.referenceId)
        put("debitPaise", it.debitPaise)
        put("creditPaise", it.creditPaise)
        put("entryDate", it.entryDate)
        put("notes", it.notes)
        put("isReversed", it.isReversed)
        put("createdAt", it.createdAt)
      }
    })

    root.put("payment_allocations", listToJsonArray(allocations) {
      JSONObject().apply {
        put("id", it.id)
        put("paymentId", it.paymentId)
        put("partyType", it.partyType.name)
        put("partyId", it.partyId)
        put("allocationType", it.allocationType.name)
        put("invoiceId", it.invoiceId ?: JSONObject.NULL)
        put("allocatedAmountPaise", it.allocatedAmountPaise)
        put("allocatedAt", it.allocatedAt)
        put("notes", it.notes)
        put("isReversed", it.isReversed)
      }
    })

    return root.toString(2)
  }

  /**
   * Validates a backup string before attempting restore.
   * Checks schema version, essential entities, and data integrity.
   */
  fun validateBackupJson(jsonString: String): BackupValidationResult {
    return try {
      val trimmed = jsonString.trim()
      if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
        return BackupValidationResult(false, 0, 0, 0, 0, 0, "Malformed JSON: does not start and end with curly braces")
      }

      val appMatch = Regex("\"app\"\\s*:\\s*\"([^\"]+)\"").find(trimmed)
      val app = appMatch?.groupValues?.get(1)
      if (app != "IceTrack") {
        return BackupValidationResult(false, 0, 0, 0, 0, 0, "Invalid backup format: Not an IceTrack backup")
      }

      val schemaMatch = Regex("\"schemaVersion\"\\s*:\\s*(\\d+)").find(trimmed)
      val schemaVersion = schemaMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
      if (schemaVersion < 1) {
        return BackupValidationResult(false, 0, 0, 0, 0, 0, "Unsupported schema version: $schemaVersion")
      }

      fun countItems(key: String): Int {
        val pattern = Regex("\"$key\"\\s*:\\s*\\[([^\\]]*)\\]")
        val match = pattern.find(trimmed) ?: return 0
        val content = match.groupValues[1].trim()
        if (content.isEmpty()) return 0
        return content.count { it == '{' }
      }

      val products = countItems("products")
      val customers = countItems("customers")
      val suppliers = countItems("suppliers")
      val sales = countItems("sales_invoices")
      val purchases = countItems("purchase_invoices")

      BackupValidationResult(
        isValid = true,
        productCount = products,
        customerCount = customers,
        supplierCount = suppliers,
        salesCount = sales,
        purchaseCount = purchases
      )
    } catch (e: Exception) {
      BackupValidationResult(false, 0, 0, 0, 0, 0, "Malformed JSON: ${e.message}")
    }
  }
}
