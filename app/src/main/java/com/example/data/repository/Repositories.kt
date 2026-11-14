package com.example.data.repository

import androidx.room.withTransaction
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
import com.example.domain.engine.CreditLimitAssessment
import com.example.domain.engine.FefoInventoryEngine
import com.example.domain.engine.InvoiceAllocationTarget
import com.example.domain.engine.OpeningBalanceAllocationTarget
import com.example.domain.engine.PaymentAllocationEngine
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.engine.UnitConversionEngine
import com.example.domain.model.AppSetting
import com.example.domain.model.Customer
import com.example.domain.model.CustomerProductPrice
import com.example.domain.model.Expense
import com.example.domain.model.ExpenseCategory
import com.example.domain.model.PartyType
import com.example.domain.model.PaymentAllocation
import com.example.domain.model.PaymentAllocationType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentStatus
import com.example.domain.model.Product
import com.example.domain.model.ProductBatch
import com.example.domain.model.ProductSupplier
import com.example.domain.model.ProductUnit
import com.example.domain.model.PurchaseInvoice
import com.example.domain.model.PurchaseInvoiceItem
import com.example.domain.model.SalesInvoice
import com.example.domain.model.SalesInvoiceItem
import com.example.domain.model.StockMovement
import com.example.domain.model.StockMovementType
import com.example.domain.model.SubledgerEntry
import com.example.domain.model.SubledgerEntryType
import com.example.domain.model.Supplier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

class CreditLimitExceededException(
  val assessment: CreditLimitAssessment,
  override val message: String
) : IllegalStateException(message)

class ProductRepository(private val db: IceTrackDatabase) {
  private val productDao = db.productDao()
  private val unitDao = db.productUnitDao()
  private val productSupplierDao = db.productSupplierDao()

  val allActiveProducts: Flow<List<Product>> = productDao.getAllActive().map { list ->
    list.map { it.toDomain() }
  }

  val allProductsIncludingArchived: Flow<List<Product>> = productDao.getAllIncludingArchived().map { list ->
    list.map { it.toDomain() }
  }

  val activeProductCount: Flow<Int> = productDao.countActive()

  fun searchProducts(query: String): Flow<List<Product>> {
    return if (query.isBlank()) allActiveProducts else productDao.search(query).map { list ->
      list.map { it.toDomain() }
    }
  }

  fun getUnitForProduct(productId: Long): Flow<ProductUnit?> {
    return unitDao.getForProduct(productId).map { it?.toDomain() }
  }

  fun getSuppliersForProduct(productId: Long): Flow<List<ProductSupplier>> {
    return productSupplierDao.getSuppliersForProduct(productId).map { list ->
      list.map { it.toDomain() }
    }
  }

  suspend fun getUnitForProductSync(productId: Long): ProductUnit? {
    return unitDao.getForProductSync(productId)?.toDomain()
  }

  suspend fun getSuppliersForProductSync(productId: Long): List<ProductSupplier> {
    return productSupplierDao.getSuppliersForProductSync(productId).map { it.toDomain() }
  }

  suspend fun saveProductWithUnit(
    product: Product,
    piecesPerBox: Int,
    boxesPerCarton: Int,
    supplierCosts: List<ProductSupplier> = emptyList()
  ): Long = db.withTransaction {
    UnitConversionEngine.validateRatios(piecesPerBox, boxesPerCarton)

    val existingProduct = if (product.id != 0L) productDao.getProductSync(product.id) else null
    val entity = product.toEntity().let {
      if (existingProduct != null) {
        it.copy(
          isActive = existingProduct.isActive,
          createdAt = existingProduct.createdAt,
          deletedAt = existingProduct.deletedAt
        )
      } else it
    }

    val productId = if (product.id == 0L) {
      productDao.insert(entity)
    } else {
      productDao.update(entity)
      product.id
    }

    val existingUnit = unitDao.getForProductSync(productId)
    unitDao.insertOrUpdate(
      ProductUnitEntity(
        id = existingUnit?.id ?: 0L,
        productId = productId,
        piecesPerBox = piecesPerBox,
        boxesPerCarton = boxesPerCarton
      )
    )

    for (sc in supplierCosts) {
      val existingSc = productSupplierDao.getSpecificSupplierCost(productId, sc.supplierId)
      productSupplierDao.insertOrUpdate(
        ProductSupplierEntity(
          id = existingSc?.id ?: sc.id,
          productId = productId,
          supplierId = sc.supplierId,
          supplierProductCode = sc.supplierProductCode,
          purchaseCostPaise = sc.purchaseCostPaise,
          isPreferredSupplier = sc.isPreferredSupplier
        )
      )
    }

    productId
  }

  suspend fun softDelete(productId: Long) {
    productDao.softDelete(productId)
  }
}

class SupplierRepository(private val db: IceTrackDatabase) {
  private val supplierDao = db.supplierDao()
  private val subledgerDao = db.subledgerDao()

  val allActiveSuppliers: Flow<List<Supplier>> = supplierDao.getAllActive().map { list ->
    list.map { it.toDomain() }
  }

  val allSuppliersIncludingArchived: Flow<List<Supplier>> = supplierDao.getAllIncludingArchived().map { list ->
    list.map { it.toDomain() }
  }

  val activeSupplierCount: Flow<Int> = supplierDao.countActive()

  fun searchSuppliers(query: String): Flow<List<Supplier>> {
    return if (query.isBlank()) allActiveSuppliers else supplierDao.search(query).map { list ->
      list.map { it.toDomain() }
    }
  }

  fun getSupplierWithBalance(supplierId: Long): Flow<Pair<Supplier?, Long>> {
    val supplierFlow = supplierDao.getById(supplierId).map { it?.toDomain() }
    val entriesFlow = subledgerDao.getEntriesForParty(PartyType.SUPPLIER, supplierId).map { list ->
      list.map { it.toDomain() }
    }

    return combine(supplierFlow, entriesFlow) { supplier, entries ->
      val balance = SubledgerCalculationEngine.calculateSupplierBalance(entries)
      supplier to balance
    }
  }

  suspend fun saveSupplier(supplier: Supplier): Long = db.withTransaction {
    val isNew = supplier.id == 0L
    val existing = if (!isNew) {
      supplierDao.getSupplierSync(supplier.id)
        ?: throw IllegalArgumentException("Supplier #${supplier.id} not found")
    } else null

    val entityToSave = supplier.toEntity().let {
      if (existing != null) {
        it.copy(
          isActive = existing.isActive,
          createdAt = existing.createdAt,
          deletedAt = existing.deletedAt
        )
      } else it
    }

    val supplierId = if (isNew) {
      supplierDao.insert(entityToSave)
    } else {
      supplierDao.update(entityToSave)
      supplier.id
    }

    val openingChanged = existing == null ||
      existing.openingBalancePaise != supplier.openingBalancePaise ||
      existing.openingBalanceDate != supplier.openingBalanceDate

    if (openingChanged) {
      if (existing != null) {
        val activeOpeningEntries = subledgerDao.getActiveEntriesByTypeForPartySync(
          partyType = PartyType.SUPPLIER,
          partyId = supplierId,
          entryType = SubledgerEntryType.OPENING_BALANCE
        )
        for (oldEntry in activeOpeningEntries) {
          subledgerDao.markReversed(oldEntry.id)
        }
      }

      if (supplier.openingBalancePaise != 0L) {
        val credit = if (supplier.openingBalancePaise > 0L) supplier.openingBalancePaise else 0L
        val debit = if (supplier.openingBalancePaise < 0L) kotlin.math.abs(supplier.openingBalancePaise) else 0L
        val noteText = if (existing == null) {
          "Initial opening balance payable"
        } else {
          "Updated opening balance payable (previous: ${SubledgerCalculationEngine.formatPaise(existing.openingBalancePaise)})"
        }
        subledgerDao.insert(
          SubledgerEntryEntity(
            partyType = PartyType.SUPPLIER,
            partyId = supplierId,
            entryType = SubledgerEntryType.OPENING_BALANCE,
            referenceType = "SUPPLIER_OPENING",
            referenceId = supplierId,
            debitPaise = debit,
            creditPaise = credit,
            entryDate = supplier.openingBalanceDate,
            notes = noteText
          )
        )
      }
    }

    supplierId
  }

  suspend fun softDelete(supplierId: Long) {
    supplierDao.softDelete(supplierId)
  }
}

class CustomerRepository(private val db: IceTrackDatabase) {
  private val customerDao = db.customerDao()
  private val subledgerDao = db.subledgerDao()
  private val priceDao = db.customerProductPriceDao()

  val allActiveCustomers: Flow<List<Customer>> = customerDao.getAllActive().map { list ->
    list.map { it.toDomain() }
  }

  val allCustomersIncludingArchived: Flow<List<Customer>> = customerDao.getAllIncludingArchived().map { list ->
    list.map { it.toDomain() }
  }

  val activeCustomerCount: Flow<Int> = customerDao.countActive()

  fun searchCustomers(query: String): Flow<List<Customer>> {
    return if (query.isBlank()) allActiveCustomers else customerDao.search(query).map { list ->
      list.map { it.toDomain() }
    }
  }

  fun getCustomPrices(customerId: Long): Flow<List<CustomerProductPrice>> {
    return priceDao.getCustomPricesForCustomer(customerId).map { list ->
      list.map { it.toDomain() }
    }
  }

  suspend fun getCustomPricesSync(customerId: Long): List<CustomerProductPrice> {
    return priceDao.getCustomPricesForCustomerSync(customerId).map { it.toDomain() }
  }

  suspend fun getPriceForCustomerProduct(customerId: Long, productId: Long): Long? {
    return priceDao.getPrice(customerId, productId)?.customSellingPricePaise
  }

  suspend fun saveCustomer(
    customer: Customer,
    customPrices: List<CustomerProductPrice> = emptyList(),
    replaceCustomPrices: Boolean = false
  ): Long = db.withTransaction {
    val isNew = customer.id == 0L
    val existing = if (!isNew) {
      customerDao.getCustomerSync(customer.id)
        ?: throw IllegalArgumentException("Customer #${customer.id} not found")
    } else null

    val entityToSave = customer.toEntity().let {
      if (existing != null) {
        it.copy(
          isActive = existing.isActive,
          createdAt = existing.createdAt,
          deletedAt = existing.deletedAt
        )
      } else it
    }

    val customerId = if (isNew) {
      customerDao.insert(entityToSave)
    } else {
      customerDao.update(entityToSave)
      customer.id
    }

    val openingChanged = existing == null ||
      existing.openingBalancePaise != customer.openingBalancePaise ||
      existing.openingBalanceDate != customer.openingBalanceDate

    if (openingChanged) {
      if (existing != null) {
        val activeOpeningEntries = subledgerDao.getActiveEntriesByTypeForPartySync(
          partyType = PartyType.CUSTOMER,
          partyId = customerId,
          entryType = SubledgerEntryType.OPENING_BALANCE
        )
        for (oldEntry in activeOpeningEntries) {
          subledgerDao.markReversed(oldEntry.id)
        }
      }

      if (customer.openingBalancePaise != 0L) {
        val debit = if (customer.openingBalancePaise > 0L) customer.openingBalancePaise else 0L
        val credit = if (customer.openingBalancePaise < 0L) kotlin.math.abs(customer.openingBalancePaise) else 0L
        val noteText = if (existing == null) {
          "Initial opening balance receivable"
        } else {
          "Updated opening balance receivable (previous: ${SubledgerCalculationEngine.formatPaise(existing.openingBalancePaise)})"
        }
        subledgerDao.insert(
          SubledgerEntryEntity(
            partyType = PartyType.CUSTOMER,
            partyId = customerId,
            entryType = SubledgerEntryType.OPENING_BALANCE,
            referenceType = "CUSTOMER_OPENING",
            referenceId = customerId,
            debitPaise = debit,
            creditPaise = credit,
            entryDate = customer.openingBalanceDate,
            notes = noteText
          )
        )
      }
    }

    if (replaceCustomPrices) {
      priceDao.deleteAllForCustomer(customerId)
    }

    for (cp in customPrices) {
      val existingPrice = if (!replaceCustomPrices) priceDao.getPrice(customerId, cp.productId) else null
      priceDao.insertOrUpdate(
        CustomerProductPriceEntity(
          id = existingPrice?.id ?: 0L,
          customerId = customerId,
          productId = cp.productId,
          customSellingPricePaise = cp.customSellingPricePaise
        )
      )
    }

    customerId
  }

  suspend fun softDelete(customerId: Long) {
    customerDao.softDelete(customerId)
  }
}

class SubledgerRepository(private val db: IceTrackDatabase) {
  private val subledgerDao = db.subledgerDao()
  private val allocationDao = db.paymentAllocationDao()

  val allEntries: Flow<List<SubledgerEntry>> = subledgerDao.getAllEntries().map { list ->
    list.map { it.toDomain() }
  }

  fun getEntriesForParty(partyType: PartyType, partyId: Long): Flow<List<SubledgerEntry>> {
    return subledgerDao.getEntriesForParty(partyType, partyId).map { list ->
      list.map { it.toDomain() }
    }
  }

  fun getActiveAllocationsForParty(partyType: PartyType, partyId: Long): Flow<List<PaymentAllocation>> {
    return allocationDao.getActiveAllocationsForParty(partyType, partyId).map { list ->
      list.map { it.toDomain() }
    }
  }

  fun getAllAllocationsIncludingReversedForParty(partyType: PartyType, partyId: Long): Flow<List<PaymentAllocation>> {
    return allocationDao.getAllIncludingReversedForParty(partyType, partyId).map { list ->
      list.map { it.toDomain() }
    }
  }

  fun getAvailableAdvancePaise(partyType: PartyType, partyId: Long): Flow<Long> {
    return allocationDao.getAvailableAdvancePaise(partyType, partyId)
  }

  suspend fun getAvailableAdvancePaiseSync(partyType: PartyType, partyId: Long): Long {
    return allocationDao.getAvailableAdvancePaiseSync(partyType, partyId)
  }

  fun getAllActiveAllocationsByPartyType(partyType: PartyType): Flow<List<PaymentAllocation>> {
    return allocationDao.getAllActiveAllocationsByPartyType(partyType).map { list ->
      list.map { it.toDomain() }
    }
  }

  suspend fun recordEntry(entry: SubledgerEntry): Long {
    return subledgerDao.insert(entry.toEntity())
  }

  suspend fun reverseEntry(entryId: Long) {
    subledgerDao.markReversed(entryId)
  }
}

class SettingsRepository(private val db: IceTrackDatabase) {
  private val appSettingDao = db.appSettingDao()

  fun getSetting(key: String, defaultValue: String = ""): Flow<String> {
    return appSettingDao.get(key).map { it?.value ?: defaultValue }
  }

  suspend fun setSetting(key: String, value: String) {
    appSettingDao.set(AppSettingEntity(key = key, value = value))
  }
}

// ==========================================
// Step 2 Repositories: Inventory, Purchases, Sales & Expenses
// ==========================================

class InventoryRepository(private val db: IceTrackDatabase) {
  private val batchDao = db.productBatchDao()
  private val movementDao = db.stockMovementDao()

  fun getBatchesForProduct(productId: Long): Flow<List<ProductBatch>> {
    return batchDao.getActiveBatchesForProduct(productId).map { list ->
      list.map { it.toDomain() }
    }
  }

  val allStockedBatches: Flow<List<ProductBatch>> = batchDao.getAllStockedBatches().map { list ->
    list.map { it.toDomain() }
  }

  val allActiveBatches: Flow<List<ProductBatch>> = batchDao.getAllActiveBatches().map { list ->
    list.map { it.toDomain() }
  }

  val allBatchesIncludingInactive: Flow<List<ProductBatch>> = batchDao.getAllBatchesIncludingInactive().map { list ->
    list.map { it.toDomain() }
  }

  fun getMovementsForProduct(productId: Long): Flow<List<StockMovement>> {
    return movementDao.getMovementsForProduct(productId).map { list ->
      list.map { it.toDomain() }
    }
  }

  val recentMovements: Flow<List<StockMovement>> = movementDao.getRecentMovements().map { list ->
    list.map { it.toDomain() }
  }

  suspend fun adjustStock(
    batchId: Long,
    adjustmentPieces: Int,
    reason: String,
    movementType: StockMovementType = StockMovementType.STOCK_ADJUSTMENT
  ) = db.withTransaction {
    require(adjustmentPieces != 0) { "Adjustment quantity must be non-zero" }
    require(reason.isNotBlank()) { "Adjustment reason cannot be empty" }

    val batch = batchDao.getById(batchId) ?: throw IllegalArgumentException("Batch $batchId not found")
    if (!batch.isActive) {
      throw IllegalStateException("Batch ${batch.batchNumber} is inactive and cannot be adjusted")
    }

    if (adjustmentPieces > 0) {
      val updated = batchDao.addStockIfActive(batchId, adjustmentPieces)
      if (updated != 1) {
        throw IllegalStateException("Failed to add stock to batch ${batch.batchNumber}")
      }
    } else {
      val deductPieces = kotlin.math.abs(adjustmentPieces)
      val updated = batchDao.deductStockIfAvailable(batchId, deductPieces)
      if (updated != 1) {
        throw IllegalStateException(
          "Cannot reduce stock below zero (current: ${batch.currentQuantityPieces}, deduct: $deductPieces)"
        )
      }
    }

    movementDao.insert(
      StockMovementEntity(
        productId = batch.productId,
        batchId = batchId,
        movementType = movementType,
        quantityPieces = adjustmentPieces,
        unitCostPaise = batch.purchaseCostPerPiecePaise,
        referenceType = "MANUAL_ADJUSTMENT",
        referenceId = batchId,
        movementDate = System.currentTimeMillis(),
        notes = reason.trim()
      )
    )
  }
}

class PurchaseRepository(private val db: IceTrackDatabase) {
  private val purchaseDao = db.purchaseInvoiceDao()
  private val itemDao = db.purchaseInvoiceItemDao()
  private val batchDao = db.productBatchDao()
  private val movementDao = db.stockMovementDao()
  private val subledgerDao = db.subledgerDao()
  private val allocationDao = db.paymentAllocationDao()

  private val appSettingDao = db.appSettingDao()

  val allPurchases: Flow<List<PurchaseInvoice>> = purchaseDao.getAll().map { list ->
    list.map { it.toDomain() }
  }

  fun getPurchasesForSupplier(supplierId: Long): Flow<List<PurchaseInvoice>> {
    return purchaseDao.getForSupplier(supplierId).map { list ->
      list.map { it.toDomain() }
    }
  }

  fun getItemsForPurchase(invoiceId: Long): Flow<List<PurchaseInvoiceItem>> {
    return itemDao.getItemsForInvoice(invoiceId).map { list ->
      list.map { it.toDomain() }
    }
  }

  suspend fun generateNextInvoiceNumber(nowMillis: Long = System.currentTimeMillis()): String {
    val dateTag = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date(nowMillis))
    val storedSeq = appSettingDao.getSync("seq_purchase_invoice")?.value?.toIntOrNull()
    var seq = (storedSeq ?: purchaseDao.countInvoices()) + 1
    var candidate = "PUR-$dateTag-${seq.toString().padStart(4, '0')}"
    while (purchaseDao.existsByInvoiceNumber(candidate) > 0) {
      seq++
      candidate = "PUR-$dateTag-${seq.toString().padStart(4, '0')}"
    }
    return candidate
  }

  suspend fun createPurchaseInvoice(
    supplierId: Long,
    invoiceNumber: String,
    invoiceDate: Long,
    dueDate: Long,
    items: List<PurchaseInvoiceItem>,
    paidAmountPaise: Long,
    paymentMethod: PaymentMethod?,
    notes: String,
    applyAvailableAdvance: Boolean = true,
    allowCreditLimitOverride: Boolean = false
  ): Long = db.withTransaction {
    val cleanInvoiceNumber = invoiceNumber.trim()
    require(cleanInvoiceNumber.isNotBlank()) { "Purchase invoice number cannot be blank" }
    require(items.isNotEmpty()) { "Purchase invoice must have at least one product line" }
    require(paidAmountPaise >= 0) { "Paid amount cannot be negative" }
    items.forEach { item ->
      require(item.quantityPieces > 0) { "Purchase item quantity must be positive" }
      require(item.costPerPiecePaise >= 0) { "Purchase item cost cannot be negative" }
      require(item.batchNumber.isNotBlank()) { "Batch number cannot be blank" }
    }
    if (purchaseDao.existsByInvoiceNumber(cleanInvoiceNumber) > 0) {
      throw IllegalStateException("Purchase invoice number '$cleanInvoiceNumber' already exists. Please use a unique invoice number.")
    }

    val supplier = db.supplierDao().getSupplierSync(supplierId)
      ?: throw IllegalArgumentException("Supplier #$supplierId not found")
    val effectiveDueDate = if (dueDate > 0L) dueDate else PaymentAllocationEngine.calculateDueDate(invoiceDate, supplier.creditTermsDays)

    val totalAmountPaise = items.sumOf { it.lineTotalPaise }

    // Enforce supplier credit limit against current subledger payable + net unpaid credit exposure
    val existingEntries = subledgerDao.getEntriesForPartySync(PartyType.SUPPLIER, supplierId).map { it.toDomain() }
    val currentBalance = SubledgerCalculationEngine.calculateSupplierBalance(existingEntries)
    val netExposure = (totalAmountPaise - paidAmountPaise).coerceAtLeast(0L)
    val assessment = PaymentAllocationEngine.assessCreditLimit(
      currentBalancePaise = currentBalance,
      newCreditExposurePaise = netExposure,
      creditLimitPaise = supplier.creditLimitPaise
    )
    if (assessment.isExceeded && !allowCreditLimitOverride) {
      throw CreditLimitExceededException(
        assessment = assessment,
        message = "Supplier credit limit (${SubledgerCalculationEngine.formatPaise(assessment.creditLimitPaise)}) exceeded by ${SubledgerCalculationEngine.formatPaise(assessment.exceededByPaise)}."
      )
    }

    val availableAdvance = if (applyAvailableAdvance) {
      allocationDao.getAvailableAdvancePaiseSync(PartyType.SUPPLIER, supplierId)
    } else 0L
    val advanceAppliedToInvoice = kotlin.math.min(availableAdvance, totalAmountPaise)
    val remainingAfterAdvance = totalAmountPaise - advanceAppliedToInvoice
    val cashAllocatedToInvoice = kotlin.math.min(paidAmountPaise, remainingAfterAdvance)
    val excessCashAdvance = paidAmountPaise - cashAllocatedToInvoice
    val headerPaidAmountPaise = advanceAppliedToInvoice + cashAllocatedToInvoice

    val paymentStatus = when {
      headerPaidAmountPaise >= totalAmountPaise -> PaymentStatus.PAID
      headerPaidAmountPaise > 0 -> PaymentStatus.PARTIALLY_PAID
      else -> PaymentStatus.UNPAID
    }

    // 1. Insert Purchase Invoice Header (paidAmountPaise never exceeds totalAmountPaise)
    val invoiceEntity = PurchaseInvoiceEntity(
      invoiceNumber = cleanInvoiceNumber,
      supplierId = supplierId,
      invoiceDate = invoiceDate,
      dueDate = effectiveDueDate,
      totalAmountPaise = totalAmountPaise,
      paidAmountPaise = headerPaidAmountPaise,
      paymentStatus = paymentStatus,
      paymentMethod = paymentMethod,
      notes = notes,
      isConfirmed = true
    )
    val invoiceId = purchaseDao.insert(invoiceEntity)

    val parsedTrailingSeq = cleanInvoiceNumber.substringAfterLast("-").toIntOrNull()
    val currentSeq = appSettingDao.getSync("seq_purchase_invoice")?.value?.toIntOrNull() ?: 0
    val nextSeqValue = maxOf(currentSeq + 1, parsedTrailingSeq ?: 0)
    appSettingDao.set(AppSettingEntity("seq_purchase_invoice", nextSeqValue.toString()))

    // 2. Insert Invoice Items and create inventory Batches + Movements
    for (item in items) {
      itemDao.insertAll(
        listOf(
          PurchaseInvoiceItemEntity(
            purchaseInvoiceId = invoiceId,
            productId = item.productId,
            batchNumber = item.batchNumber,
            expiryDate = item.expiryDate,
            quantityPieces = item.quantityPieces,
            costPerPiecePaise = item.costPerPiecePaise,
            lineTotalPaise = item.lineTotalPaise
          )
        )
      )

      val batchId = batchDao.insert(
        ProductBatchEntity(
          productId = item.productId,
          batchNumber = item.batchNumber,
          purchaseInvoiceId = invoiceId,
          purchaseDate = invoiceDate,
          expiryDate = item.expiryDate,
          initialQuantityPieces = item.quantityPieces,
          currentQuantityPieces = item.quantityPieces,
          purchaseCostPerPiecePaise = item.costPerPiecePaise
        )
      )

      movementDao.insert(
        StockMovementEntity(
          productId = item.productId,
          batchId = batchId,
          movementType = StockMovementType.PURCHASE_RECEIPT,
          quantityPieces = item.quantityPieces,
          unitCostPaise = item.costPerPiecePaise,
          referenceType = "PURCHASE_INVOICE",
          referenceId = invoiceId,
          movementDate = invoiceDate,
          notes = "Received via Invoice #$cleanInvoiceNumber"
        )
      )
    }

    // 3. Subledger Accounts Payable Credit (increases what we owe supplier)
    subledgerDao.insert(
      SubledgerEntryEntity(
        partyType = PartyType.SUPPLIER,
        partyId = supplierId,
        entryType = SubledgerEntryType.INVOICE,
        referenceType = "PURCHASE_INVOICE",
        referenceId = invoiceId,
        debitPaise = 0L,
        creditPaise = totalAmountPaise,
        entryDate = invoiceDate,
        notes = "Purchase Invoice #$cleanInvoiceNumber"
      )
    )

    // 4. Consume existing available supplier ADVANCE allocations FIFO (without creating duplicate payment entries)
    if (advanceAppliedToInvoice > 0L) {
      var remainingNeed = advanceAppliedToInvoice
      val activeAdvances = allocationDao.getActiveAllocationsByTypeForPartySync(
        partyType = PartyType.SUPPLIER,
        partyId = supplierId,
        allocationType = PaymentAllocationType.ADVANCE
      )
      for (adv in activeAdvances) {
        if (remainingNeed <= 0L) break
        val take = kotlin.math.min(remainingNeed, adv.allocatedAmountPaise)
        allocationDao.markReversed(adv.id)
        allocationDao.insert(
          PaymentAllocationEntity(
            paymentId = adv.paymentId,
            partyType = PartyType.SUPPLIER,
            partyId = supplierId,
            allocationType = PaymentAllocationType.INVOICE,
            invoiceId = invoiceId,
            allocatedAmountPaise = take,
            allocatedAt = invoiceDate,
            notes = "Advance applied to Purchase #$cleanInvoiceNumber"
          )
        )
        val leftover = adv.allocatedAmountPaise - take
        if (leftover > 0L) {
          allocationDao.insert(
            PaymentAllocationEntity(
              paymentId = adv.paymentId,
              partyType = PartyType.SUPPLIER,
              partyId = supplierId,
              allocationType = PaymentAllocationType.ADVANCE,
              invoiceId = null,
              allocatedAmountPaise = leftover,
              allocatedAt = invoiceDate,
              notes = "Remaining advance after Purchase #$cleanInvoiceNumber"
            )
          )
        }
        remainingNeed -= take
      }
    }

    // 5. Subledger Accounts Payable Debit for cash paid at purchase time + explicit INVOICE & ADVANCE allocations
    if (paidAmountPaise > 0) {
      val paymentSubledgerId = subledgerDao.insert(
        SubledgerEntryEntity(
          partyType = PartyType.SUPPLIER,
          partyId = supplierId,
          entryType = SubledgerEntryType.PAYMENT,
          referenceType = "PURCHASE_PAYMENT",
          referenceId = invoiceId,
          debitPaise = paidAmountPaise,
          creditPaise = 0L,
          entryDate = invoiceDate,
          notes = "Payment for Purchase #$cleanInvoiceNumber (${paymentMethod?.displayName ?: "Direct"})"
        )
      )

      if (cashAllocatedToInvoice > 0L) {
        allocationDao.insert(
          PaymentAllocationEntity(
            paymentId = paymentSubledgerId,
            partyType = PartyType.SUPPLIER,
            partyId = supplierId,
            allocationType = PaymentAllocationType.INVOICE,
            invoiceId = invoiceId,
            allocatedAmountPaise = cashAllocatedToInvoice,
            allocatedAt = invoiceDate,
            notes = "Direct payment on Purchase #$cleanInvoiceNumber"
          )
        )
      }

      if (excessCashAdvance > 0L) {
        allocationDao.insert(
          PaymentAllocationEntity(
            paymentId = paymentSubledgerId,
            partyType = PartyType.SUPPLIER,
            partyId = supplierId,
            allocationType = PaymentAllocationType.ADVANCE,
            invoiceId = null,
            allocatedAmountPaise = excessCashAdvance,
            allocatedAt = invoiceDate,
            notes = "Overpayment advance from Purchase #$cleanInvoiceNumber"
          )
        )
      }
    }

    invoiceId
  }

  private suspend fun buildSupplierOpeningTargets(supplierId: Long): List<OpeningBalanceAllocationTarget> {
    val openingEntries = subledgerDao.getActiveEntriesByTypeForPartySync(
      partyType = PartyType.SUPPLIER,
      partyId = supplierId,
      entryType = SubledgerEntryType.OPENING_BALANCE
    ).filter { it.creditPaise > 0L }
    var alreadyAllocated = allocationDao.getTotalAllocatedOpeningBalancePaiseSync(PartyType.SUPPLIER, supplierId)
    return openingEntries.map { entry ->
      val consumedForEntry = kotlin.math.min(alreadyAllocated, entry.creditPaise)
      alreadyAllocated -= consumedForEntry
      OpeningBalanceAllocationTarget(
        subledgerEntryId = entry.id,
        originalAmountPaise = entry.creditPaise,
        alreadyAllocatedPaise = consumedForEntry,
        entryDate = entry.entryDate
      )
    }
  }

  suspend fun recordSupplierPayment(
    supplierId: Long,
    amountPaise: Long,
    paymentDate: Long,
    paymentMethod: PaymentMethod,
    notes: String
  ): Long = db.withTransaction {
    require(amountPaise > 0) { "Payment amount must be greater than zero" }

    // 1. Record in Subledger (Debit AP, reducing what we owe)
    val subledgerId = subledgerDao.insert(
      SubledgerEntryEntity(
        partyType = PartyType.SUPPLIER,
        partyId = supplierId,
        entryType = SubledgerEntryType.PAYMENT,
        referenceType = "SUPPLIER_PAYMENT",
        referenceId = supplierId,
        debitPaise = amountPaise,
        creditPaise = 0L,
        entryDate = paymentDate,
        notes = "Supplier Payment (${paymentMethod.displayName}) - $notes"
      )
    )

    // 2. FIFO Allocation across opening balance + unpaid invoices + ADVANCE for any excess
    val openingTargets = buildSupplierOpeningTargets(supplierId)
    val unpaidInvoices = purchaseDao.getUnpaidForSupplier(supplierId)
    val invoiceTargets = unpaidInvoices.map { inv ->
      InvoiceAllocationTarget(
        invoiceId = inv.id,
        totalAmountPaise = inv.totalAmountPaise,
        alreadyPaidPaise = inv.paidAmountPaise,
        invoiceDate = inv.invoiceDate
      )
    }

    val allocationResult = PaymentAllocationEngine.allocateFifoAcrossDuesAndAdvance(
      paymentId = subledgerId,
      partyType = PartyType.SUPPLIER,
      partyId = supplierId,
      totalPaymentPaise = amountPaise,
      openingBalances = openingTargets,
      invoices = invoiceTargets,
      allocatedAt = paymentDate,
      advanceNote = "Supplier advance payment (${paymentMethod.displayName})${if (notes.isNotBlank()) " - $notes" else ""}"
    )

    for (alloc in allocationResult.allocations) {
      allocationDao.insert(alloc.toEntity())
    }

    for ((invId, addedPaid) in allocationResult.invoiceAdditionalPaidPaise) {
      if (addedPaid <= 0L) continue
      val inv = unpaidInvoices.first { it.id == invId }
      val newPaid = (inv.paidAmountPaise + addedPaid).coerceAtMost(inv.totalAmountPaise)
      val status = allocationResult.invoiceStatuses[invId] ?: PaymentStatus.PARTIALLY_PAID
      purchaseDao.update(inv.copy(paidAmountPaise = newPaid, paymentStatus = status))
    }

    subledgerId
  }

  suspend fun applyAvailableSupplierAdvance(
    supplierId: Long,
    appliedAt: Long = System.currentTimeMillis()
  ): Long = db.withTransaction {
    val activeAdvances = allocationDao.getActiveAllocationsByTypeForPartySync(
      partyType = PartyType.SUPPLIER,
      partyId = supplierId,
      allocationType = PaymentAllocationType.ADVANCE
    )
    if (activeAdvances.isEmpty()) return@withTransaction 0L

    var totalApplied = 0L
    for (adv in activeAdvances) {
      val openingTargets = buildSupplierOpeningTargets(supplierId)
      val unpaidInvoices = purchaseDao.getUnpaidForSupplier(supplierId)
      val invoiceTargets = unpaidInvoices.map { inv ->
        InvoiceAllocationTarget(
          invoiceId = inv.id,
          totalAmountPaise = inv.totalAmountPaise,
          alreadyPaidPaise = inv.paidAmountPaise,
          invoiceDate = inv.invoiceDate
        )
      }
      if (openingTargets.none { it.remainingUnpaidPaise > 0L } && invoiceTargets.none { it.remainingUnpaidPaise > 0L }) {
        break
      }

      val result = PaymentAllocationEngine.allocateFifoAcrossDuesAndAdvance(
        paymentId = adv.paymentId,
        partyType = PartyType.SUPPLIER,
        partyId = supplierId,
        totalPaymentPaise = adv.allocatedAmountPaise,
        openingBalances = openingTargets,
        invoices = invoiceTargets,
        allocatedAt = appliedAt,
        advanceNote = "Remaining supplier advance (Payment #${adv.paymentId})"
      )
      val settled = result.openingBalanceAllocatedPaise + result.invoiceAdditionalPaidPaise.values.sum()
      if (settled <= 0L) break

      allocationDao.markReversed(adv.id)
      for (alloc in result.allocations) {
        val updatedNotes = when (alloc.allocationType) {
          PaymentAllocationType.INVOICE -> "Applied supplier advance (Payment #${adv.paymentId})"
          PaymentAllocationType.OPENING_BALANCE -> "Applied supplier advance to opening balance (Payment #${adv.paymentId})"
          PaymentAllocationType.ADVANCE -> alloc.notes
        }
        allocationDao.insert(alloc.copy(notes = updatedNotes).toEntity())
      }
      for ((invId, addedPaid) in result.invoiceAdditionalPaidPaise) {
        if (addedPaid <= 0L) continue
        val inv = unpaidInvoices.first { it.id == invId }
        val newPaid = (inv.paidAmountPaise + addedPaid).coerceAtMost(inv.totalAmountPaise)
        val status = result.invoiceStatuses[invId] ?: PaymentStatus.PARTIALLY_PAID
        purchaseDao.update(inv.copy(paidAmountPaise = newPaid, paymentStatus = status))
      }
      totalApplied += settled
    }
    totalApplied
  }
}

class SalesRepository(private val db: IceTrackDatabase) {
  private val salesDao = db.salesInvoiceDao()
  private val itemDao = db.salesInvoiceItemDao()
  private val batchDao = db.productBatchDao()
  private val movementDao = db.stockMovementDao()
  private val subledgerDao = db.subledgerDao()
  private val allocationDao = db.paymentAllocationDao()

  private val appSettingDao = db.appSettingDao()

  val allSales: Flow<List<SalesInvoice>> = salesDao.getAll().map { list ->
    list.map { it.toDomain() }
  }

  fun getSalesForCustomer(customerId: Long): Flow<List<SalesInvoice>> {
    return salesDao.getForCustomer(customerId).map { list ->
      list.map { it.toDomain() }
    }
  }

  fun getItemsForSale(invoiceId: Long): Flow<List<SalesInvoiceItem>> {
    return itemDao.getItemsForInvoice(invoiceId).map { list ->
      list.map { it.toDomain() }
    }
  }

  val allSalesItems: Flow<List<SalesInvoiceItem>> = itemDao.getAllItems().map { list ->
    list.map { it.toDomain() }
  }

  data class RequestedSaleLine(
    val productId: Long,
    val quantityPieces: Int,
    val unitPricePaise: Long
  )

  suspend fun generateNextInvoiceNumber(nowMillis: Long = System.currentTimeMillis()): String {
    val dateTag = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date(nowMillis))
    val storedSeq = appSettingDao.getSync("seq_sales_invoice")?.value?.toIntOrNull()
    var seq = (storedSeq ?: salesDao.countInvoices()) + 1
    var candidate = "INV-$dateTag-${seq.toString().padStart(4, '0')}"
    while (salesDao.existsByInvoiceNumber(candidate) > 0) {
      seq++
      candidate = "INV-$dateTag-${seq.toString().padStart(4, '0')}"
    }
    return candidate
  }

  private data class PreparedSaleDispatchLine(
    val item: SalesInvoiceItemEntity,
    val unitPurchaseCostPaise: Long
  )

  suspend fun createSalesInvoice(
    customerId: Long,
    invoiceNumber: String,
    invoiceDate: Long,
    dueDate: Long,
    requestedLines: List<RequestedSaleLine>,
    paidAmountPaise: Long,
    paymentMethod: PaymentMethod?,
    notes: String,
    allowExpired: Boolean = false,
    applyAvailableAdvance: Boolean = true,
    allowCreditLimitOverride: Boolean = false
  ): Long = db.withTransaction {
    val cleanInvoiceNumber = invoiceNumber.trim()
    require(cleanInvoiceNumber.isNotBlank()) { "Sales invoice number cannot be blank" }
    require(requestedLines.isNotEmpty()) { "Sales invoice must have at least one product line" }
    require(paidAmountPaise >= 0) { "Paid amount cannot be negative" }
    requestedLines.forEach { line ->
      require(line.quantityPieces > 0) { "Line quantity must be greater than zero" }
      require(line.unitPricePaise >= 0) { "Unit selling price cannot be negative" }
    }
    if (salesDao.existsByInvoiceNumber(cleanInvoiceNumber) > 0) {
      throw IllegalStateException("Sales invoice number '$cleanInvoiceNumber' already exists. Please use a unique invoice number.")
    }

    val customer = db.customerDao().getCustomerSync(customerId)
      ?: throw IllegalArgumentException("Customer #$customerId not found")
    val effectiveDueDate = if (dueDate > 0L) dueDate else PaymentAllocationEngine.calculateDueDate(invoiceDate, customer.paymentTermsDays)

    // 1. Validate and execute FEFO stock allocation across all lines, tracking in-flight batch deductions
    // so duplicate product lines in the same invoice cannot double-allocate or oversell a batch.
    val itemsToInsert = mutableListOf<PreparedSaleDispatchLine>()
    val remainingBatchPieces = mutableMapOf<Long, Int>()
    var totalAmountPaise = 0L

    for (line in requestedLines) {
      val availableBatches = batchDao.getActiveBatchesForProductSync(line.productId).map { entity ->
        val effectiveQty = remainingBatchPieces.getOrPut(entity.id) { entity.currentQuantityPieces }
        entity.toDomain().copy(currentQuantityPieces = effectiveQty)
      }
      val fefoResult = FefoInventoryEngine.allocateFefo(
        requestedPieces = line.quantityPieces,
        availableBatches = availableBatches,
        allowExpired = allowExpired,
        currentTimestamp = invoiceDate
      )

      if (fefoResult.remainingUnfulfilledPieces > 0) {
        val prod = db.productDao().getProductSync(line.productId)
        throw IllegalStateException(
          "Insufficient stock for '${prod?.name ?: "Product #${line.productId}"}'. " +
          "Requested: ${line.quantityPieces} pcs, available in non-expired batches: ${fefoResult.fulfilledPieces} pcs."
        )
      }

      for (alloc in fefoResult.allocations) {
        val currentLeft = remainingBatchPieces[alloc.batch.id] ?: alloc.batch.currentQuantityPieces
        remainingBatchPieces[alloc.batch.id] = currentLeft - alloc.quantityPiecesToIssue

        val lineTotal = alloc.quantityPiecesToIssue.toLong() * line.unitPricePaise
        totalAmountPaise += lineTotal
        itemsToInsert.add(
          PreparedSaleDispatchLine(
            item = SalesInvoiceItemEntity(
              salesInvoiceId = 0L, // populated after header insert
              productId = line.productId,
              batchId = alloc.batch.id,
              quantityPieces = alloc.quantityPiecesToIssue,
              unitPricePaise = line.unitPricePaise,
              lineTotalPaise = lineTotal
            ),
            unitPurchaseCostPaise = alloc.batch.purchaseCostPerPiecePaise
          )
        )
      }
    }

    // Enforce customer credit limit against current subledger receivable + net unpaid exposure
    val existingEntries = subledgerDao.getEntriesForPartySync(PartyType.CUSTOMER, customerId).map { it.toDomain() }
    val currentBalance = SubledgerCalculationEngine.calculateCustomerBalance(existingEntries)
    val netExposure = (totalAmountPaise - paidAmountPaise).coerceAtLeast(0L)
    val assessment = PaymentAllocationEngine.assessCreditLimit(
      currentBalancePaise = currentBalance,
      newCreditExposurePaise = netExposure,
      creditLimitPaise = customer.creditLimitPaise
    )
    if (assessment.isExceeded && !allowCreditLimitOverride) {
      throw CreditLimitExceededException(
        assessment = assessment,
        message = "Customer credit limit (${SubledgerCalculationEngine.formatPaise(assessment.creditLimitPaise)}) exceeded by ${SubledgerCalculationEngine.formatPaise(assessment.exceededByPaise)}."
      )
    }

    val availableAdvance = if (applyAvailableAdvance) {
      allocationDao.getAvailableAdvancePaiseSync(PartyType.CUSTOMER, customerId)
    } else 0L
    val advanceAppliedToInvoice = kotlin.math.min(availableAdvance, totalAmountPaise)
    val remainingAfterAdvance = totalAmountPaise - advanceAppliedToInvoice
    val cashAllocatedToInvoice = kotlin.math.min(paidAmountPaise, remainingAfterAdvance)
    val excessCashAdvance = paidAmountPaise - cashAllocatedToInvoice
    val headerPaidAmountPaise = advanceAppliedToInvoice + cashAllocatedToInvoice

    val paymentStatus = when {
      headerPaidAmountPaise >= totalAmountPaise -> PaymentStatus.PAID
      headerPaidAmountPaise > 0 -> PaymentStatus.PARTIALLY_PAID
      else -> PaymentStatus.UNPAID
    }

    // 2. Insert Sales Invoice Header (paidAmountPaise strictly capped <= totalAmountPaise)
    val headerEntity = SalesInvoiceEntity(
      invoiceNumber = cleanInvoiceNumber,
      customerId = customerId,
      invoiceDate = invoiceDate,
      dueDate = effectiveDueDate,
      totalAmountPaise = totalAmountPaise,
      paidAmountPaise = headerPaidAmountPaise,
      paymentStatus = paymentStatus,
      paymentMethod = paymentMethod,
      notes = notes,
      isConfirmed = true
    )
    val invoiceId = salesDao.insert(headerEntity)

    val parsedTrailingSeq = cleanInvoiceNumber.substringAfterLast("-").toIntOrNull()
    val currentSeq = appSettingDao.getSync("seq_sales_invoice")?.value?.toIntOrNull() ?: 0
    val nextSeqValue = maxOf(currentSeq + 1, parsedTrailingSeq ?: 0)
    appSettingDao.set(AppSettingEntity("seq_sales_invoice", nextSeqValue.toString()))

    // 3. Guarded atomic stock deduction, insert items and record stock dispatch movements with actual batch purchase cost
    for (prepared in itemsToInsert) {
      val itemWithHeader = prepared.item.copy(salesInvoiceId = invoiceId)
      itemDao.insertAll(listOf(itemWithHeader))

      val updatedRows = batchDao.deductStockIfAvailable(itemWithHeader.batchId, itemWithHeader.quantityPieces)
      if (updatedRows != 1) {
        throw IllegalStateException("Insufficient or inactive stock in batch #${itemWithHeader.batchId}; transaction rolled back.")
      }

      movementDao.insert(
        StockMovementEntity(
          productId = itemWithHeader.productId,
          batchId = itemWithHeader.batchId,
          movementType = StockMovementType.SALE_DISPATCH,
          quantityPieces = -itemWithHeader.quantityPieces,
          unitCostPaise = prepared.unitPurchaseCostPaise,
          referenceType = "SALES_INVOICE",
          referenceId = invoiceId,
          movementDate = invoiceDate,
          notes = "Dispatched via Sales Invoice #$cleanInvoiceNumber"
        )
      )
    }

    // 4. Subledger Accounts Receivable Debit (increases what customer owes us)
    subledgerDao.insert(
      SubledgerEntryEntity(
        partyType = PartyType.CUSTOMER,
        partyId = customerId,
        entryType = SubledgerEntryType.INVOICE,
        referenceType = "SALES_INVOICE",
        referenceId = invoiceId,
        debitPaise = totalAmountPaise,
        creditPaise = 0L,
        entryDate = invoiceDate,
        notes = "Sales Invoice #$cleanInvoiceNumber"
      )
    )

    // 5. Consume existing available customer ADVANCE allocations FIFO
    if (advanceAppliedToInvoice > 0L) {
      var remainingNeed = advanceAppliedToInvoice
      val activeAdvances = allocationDao.getActiveAllocationsByTypeForPartySync(
        partyType = PartyType.CUSTOMER,
        partyId = customerId,
        allocationType = PaymentAllocationType.ADVANCE
      )
      for (adv in activeAdvances) {
        if (remainingNeed <= 0L) break
        val take = kotlin.math.min(remainingNeed, adv.allocatedAmountPaise)
        allocationDao.markReversed(adv.id)
        allocationDao.insert(
          PaymentAllocationEntity(
            paymentId = adv.paymentId,
            partyType = PartyType.CUSTOMER,
            partyId = customerId,
            allocationType = PaymentAllocationType.INVOICE,
            invoiceId = invoiceId,
            allocatedAmountPaise = take,
            allocatedAt = invoiceDate,
            notes = "Advance applied to Sale #$cleanInvoiceNumber"
          )
        )
        val leftover = adv.allocatedAmountPaise - take
        if (leftover > 0L) {
          allocationDao.insert(
            PaymentAllocationEntity(
              paymentId = adv.paymentId,
              partyType = PartyType.CUSTOMER,
              partyId = customerId,
              allocationType = PaymentAllocationType.ADVANCE,
              invoiceId = null,
              allocatedAmountPaise = leftover,
              allocatedAt = invoiceDate,
              notes = "Remaining advance after Sale #$cleanInvoiceNumber"
            )
          )
        }
        remainingNeed -= take
      }
    }

    // 6. Subledger Accounts Receivable Credit for cash received + INVOICE / ADVANCE allocations
    if (paidAmountPaise > 0) {
      val paymentSubledgerId = subledgerDao.insert(
        SubledgerEntryEntity(
          partyType = PartyType.CUSTOMER,
          partyId = customerId,
          entryType = SubledgerEntryType.PAYMENT,
          referenceType = "SALES_PAYMENT",
          referenceId = invoiceId,
          debitPaise = 0L,
          creditPaise = paidAmountPaise,
          entryDate = invoiceDate,
          notes = "Payment received for Sale #$cleanInvoiceNumber (${paymentMethod?.displayName ?: "Direct"})"
        )
      )

      if (cashAllocatedToInvoice > 0L) {
        allocationDao.insert(
          PaymentAllocationEntity(
            paymentId = paymentSubledgerId,
            partyType = PartyType.CUSTOMER,
            partyId = customerId,
            allocationType = PaymentAllocationType.INVOICE,
            invoiceId = invoiceId,
            allocatedAmountPaise = cashAllocatedToInvoice,
            allocatedAt = invoiceDate,
            notes = "Direct payment on Sale #$cleanInvoiceNumber"
          )
        )
      }

      if (excessCashAdvance > 0L) {
        allocationDao.insert(
          PaymentAllocationEntity(
            paymentId = paymentSubledgerId,
            partyType = PartyType.CUSTOMER,
            partyId = customerId,
            allocationType = PaymentAllocationType.ADVANCE,
            invoiceId = null,
            allocatedAmountPaise = excessCashAdvance,
            allocatedAt = invoiceDate,
            notes = "Overpayment advance from Sale #$cleanInvoiceNumber"
          )
        )
      }
    }

    invoiceId
  }

  private suspend fun buildCustomerOpeningTargets(customerId: Long): List<OpeningBalanceAllocationTarget> {
    val openingEntries = subledgerDao.getActiveEntriesByTypeForPartySync(
      partyType = PartyType.CUSTOMER,
      partyId = customerId,
      entryType = SubledgerEntryType.OPENING_BALANCE
    ).filter { it.debitPaise > 0L }
    var alreadyAllocated = allocationDao.getTotalAllocatedOpeningBalancePaiseSync(PartyType.CUSTOMER, customerId)
    return openingEntries.map { entry ->
      val consumedForEntry = kotlin.math.min(alreadyAllocated, entry.debitPaise)
      alreadyAllocated -= consumedForEntry
      OpeningBalanceAllocationTarget(
        subledgerEntryId = entry.id,
        originalAmountPaise = entry.debitPaise,
        alreadyAllocatedPaise = consumedForEntry,
        entryDate = entry.entryDate
      )
    }
  }

  suspend fun recordCustomerPayment(
    customerId: Long,
    amountPaise: Long,
    paymentDate: Long,
    paymentMethod: PaymentMethod,
    notes: String
  ): Long = db.withTransaction {
    require(amountPaise > 0) { "Payment amount must be greater than zero" }

    // 1. Record in Subledger (Credit AR, reducing customer debt)
    val subledgerId = subledgerDao.insert(
      SubledgerEntryEntity(
        partyType = PartyType.CUSTOMER,
        partyId = customerId,
        entryType = SubledgerEntryType.PAYMENT,
        referenceType = "CUSTOMER_PAYMENT",
        referenceId = customerId,
        debitPaise = 0L,
        creditPaise = amountPaise,
        entryDate = paymentDate,
        notes = "Payment received (${paymentMethod.displayName}) - $notes"
      )
    )

    // 2. FIFO Payment Allocation across opening balance + unpaid invoices + ADVANCE for any excess
    val openingTargets = buildCustomerOpeningTargets(customerId)
    val unpaidInvoices = salesDao.getUnpaidForCustomer(customerId)
    val invoiceTargets = unpaidInvoices.map { inv ->
      InvoiceAllocationTarget(
        invoiceId = inv.id,
        totalAmountPaise = inv.totalAmountPaise,
        alreadyPaidPaise = inv.paidAmountPaise,
        invoiceDate = inv.invoiceDate
      )
    }

    val allocationResult = PaymentAllocationEngine.allocateFifoAcrossDuesAndAdvance(
      paymentId = subledgerId,
      partyType = PartyType.CUSTOMER,
      partyId = customerId,
      totalPaymentPaise = amountPaise,
      openingBalances = openingTargets,
      invoices = invoiceTargets,
      allocatedAt = paymentDate,
      advanceNote = "Customer advance payment (${paymentMethod.displayName})${if (notes.isNotBlank()) " - $notes" else ""}"
    )

    for (alloc in allocationResult.allocations) {
      allocationDao.insert(alloc.toEntity())
    }

    for ((invId, addedPaid) in allocationResult.invoiceAdditionalPaidPaise) {
      if (addedPaid <= 0L) continue
      val inv = unpaidInvoices.first { it.id == invId }
      val newPaid = (inv.paidAmountPaise + addedPaid).coerceAtMost(inv.totalAmountPaise)
      val status = allocationResult.invoiceStatuses[invId] ?: PaymentStatus.PARTIALLY_PAID
      salesDao.update(inv.copy(paidAmountPaise = newPaid, paymentStatus = status))
    }

    subledgerId
  }

  suspend fun applyAvailableCustomerAdvance(
    customerId: Long,
    appliedAt: Long = System.currentTimeMillis()
  ): Long = db.withTransaction {
    val activeAdvances = allocationDao.getActiveAllocationsByTypeForPartySync(
      partyType = PartyType.CUSTOMER,
      partyId = customerId,
      allocationType = PaymentAllocationType.ADVANCE
    )
    if (activeAdvances.isEmpty()) return@withTransaction 0L

    var totalApplied = 0L
    for (adv in activeAdvances) {
      val openingTargets = buildCustomerOpeningTargets(customerId)
      val unpaidInvoices = salesDao.getUnpaidForCustomer(customerId)
      val invoiceTargets = unpaidInvoices.map { inv ->
        InvoiceAllocationTarget(
          invoiceId = inv.id,
          totalAmountPaise = inv.totalAmountPaise,
          alreadyPaidPaise = inv.paidAmountPaise,
          invoiceDate = inv.invoiceDate
        )
      }
      if (openingTargets.none { it.remainingUnpaidPaise > 0L } && invoiceTargets.none { it.remainingUnpaidPaise > 0L }) {
        break
      }

      val result = PaymentAllocationEngine.allocateFifoAcrossDuesAndAdvance(
        paymentId = adv.paymentId,
        partyType = PartyType.CUSTOMER,
        partyId = customerId,
        totalPaymentPaise = adv.allocatedAmountPaise,
        openingBalances = openingTargets,
        invoices = invoiceTargets,
        allocatedAt = appliedAt,
        advanceNote = "Remaining customer advance (Payment #${adv.paymentId})"
      )
      val settled = result.openingBalanceAllocatedPaise + result.invoiceAdditionalPaidPaise.values.sum()
      if (settled <= 0L) break

      allocationDao.markReversed(adv.id)
      for (alloc in result.allocations) {
        val updatedNotes = when (alloc.allocationType) {
          PaymentAllocationType.INVOICE -> "Applied customer advance (Payment #${adv.paymentId})"
          PaymentAllocationType.OPENING_BALANCE -> "Applied customer advance to opening balance (Payment #${adv.paymentId})"
          PaymentAllocationType.ADVANCE -> alloc.notes
        }
        allocationDao.insert(alloc.copy(notes = updatedNotes).toEntity())
      }
      for ((invId, addedPaid) in result.invoiceAdditionalPaidPaise) {
        if (addedPaid <= 0L) continue
        val inv = unpaidInvoices.first { it.id == invId }
        val newPaid = (inv.paidAmountPaise + addedPaid).coerceAtMost(inv.totalAmountPaise)
        val status = result.invoiceStatuses[invId] ?: PaymentStatus.PARTIALLY_PAID
        salesDao.update(inv.copy(paidAmountPaise = newPaid, paymentStatus = status))
      }
      totalApplied += settled
    }
    totalApplied
  }
}

class ExpenseRepository(private val db: IceTrackDatabase) {
  private val expenseDao = db.expenseDao()

  val allExpenses: Flow<List<Expense>> = expenseDao.getAllActive().map { list ->
    list.map { it.toDomain() }
  }

  suspend fun recordExpense(
    category: ExpenseCategory,
    amountPaise: Long,
    expenseDate: Long,
    paymentMethod: PaymentMethod,
    referenceNumber: String = "",
    notes: String = ""
  ): Long {
    require(amountPaise > 0) { "Expense amount must be greater than zero" }
    return expenseDao.insert(
      ExpenseEntity(
        category = category,
        amountPaise = amountPaise,
        expenseDate = expenseDate,
        paymentMethod = paymentMethod,
        referenceNumber = referenceNumber,
        notes = notes
      )
    )
  }

  suspend fun softDelete(expenseId: Long) {
    expenseDao.softDelete(expenseId)
  }
}

// Entity <-> Domain Mappers
fun ProductEntity.toDomain() = Product(
  id = id,
  name = name,
  sku = sku,
  category = category,
  defaultPurchasePricePaise = defaultPurchasePricePaise,
  defaultSellingPricePaise = defaultSellingPricePaise,
  minStockLevelPieces = minStockLevelPieces,
  isActive = isActive,
  createdAt = createdAt
)

fun Product.toEntity() = ProductEntity(
  id = id,
  name = name,
  sku = sku,
  category = category,
  defaultPurchasePricePaise = defaultPurchasePricePaise,
  defaultSellingPricePaise = defaultSellingPricePaise,
  minStockLevelPieces = minStockLevelPieces,
  isActive = isActive,
  createdAt = createdAt
)

fun ProductUnitEntity.toDomain() = ProductUnit(
  id = id,
  productId = productId,
  piecesPerBox = piecesPerBox,
  boxesPerCarton = boxesPerCarton
)

fun ProductSupplierEntity.toDomain() = ProductSupplier(
  id = id,
  productId = productId,
  supplierId = supplierId,
  supplierProductCode = supplierProductCode,
  purchaseCostPaise = purchaseCostPaise,
  isPreferredSupplier = isPreferredSupplier
)

fun SupplierEntity.toDomain() = Supplier(
  id = id,
  name = name,
  contactPerson = contactPerson,
  phoneNumber = phoneNumber,
  address = address,
  openingBalancePaise = openingBalancePaise,
  openingBalanceDate = openingBalanceDate,
  creditTermsDays = creditTermsDays,
  creditLimitPaise = creditLimitPaise,
  isActive = isActive,
  createdAt = createdAt
)

fun Supplier.toEntity() = SupplierEntity(
  id = id,
  name = name,
  contactPerson = contactPerson,
  phoneNumber = phoneNumber,
  address = address,
  openingBalancePaise = openingBalancePaise,
  openingBalanceDate = openingBalanceDate,
  creditTermsDays = creditTermsDays,
  creditLimitPaise = creditLimitPaise,
  isActive = isActive,
  createdAt = createdAt
)

fun CustomerEntity.toDomain() = Customer(
  id = id,
  shopName = shopName,
  ownerName = ownerName,
  phoneNumber = phoneNumber,
  address = address,
  openingBalancePaise = openingBalancePaise,
  openingBalanceDate = openingBalanceDate,
  creditLimitPaise = creditLimitPaise,
  paymentTermsDays = paymentTermsDays,
  isActive = isActive,
  createdAt = createdAt
)

fun Customer.toEntity() = CustomerEntity(
  id = id,
  shopName = shopName,
  ownerName = ownerName,
  phoneNumber = phoneNumber,
  address = address,
  openingBalancePaise = openingBalancePaise,
  openingBalanceDate = openingBalanceDate,
  creditLimitPaise = creditLimitPaise,
  paymentTermsDays = paymentTermsDays,
  isActive = isActive,
  createdAt = createdAt
)

fun CustomerProductPriceEntity.toDomain() = CustomerProductPrice(
  id = id,
  customerId = customerId,
  productId = productId,
  customSellingPricePaise = customSellingPricePaise
)

fun SubledgerEntryEntity.toDomain() = SubledgerEntry(
  id = id,
  partyType = partyType,
  partyId = partyId,
  entryType = entryType,
  referenceType = referenceType,
  referenceId = referenceId,
  debitPaise = debitPaise,
  creditPaise = creditPaise,
  entryDate = entryDate,
  createdAt = createdAt,
  notes = notes,
  isReversed = isReversed
)

fun SubledgerEntry.toEntity() = SubledgerEntryEntity(
  id = id,
  partyType = partyType,
  partyId = partyId,
  entryType = entryType,
  referenceType = referenceType,
  referenceId = referenceId,
  debitPaise = debitPaise,
  creditPaise = creditPaise,
  entryDate = entryDate,
  createdAt = createdAt,
  notes = notes,
  isReversed = isReversed
)

fun PaymentAllocationEntity.toDomain() = PaymentAllocation(
  id = id,
  paymentId = paymentId,
  partyType = partyType,
  partyId = partyId,
  allocationType = allocationType,
  invoiceId = invoiceId,
  allocatedAmountPaise = allocatedAmountPaise,
  allocatedAt = allocatedAt,
  notes = notes,
  isReversed = isReversed
)

fun PaymentAllocation.toEntity() = PaymentAllocationEntity(
  id = id,
  paymentId = paymentId,
  partyType = partyType,
  partyId = partyId,
  allocationType = allocationType,
  invoiceId = invoiceId,
  allocatedAmountPaise = allocatedAmountPaise,
  allocatedAt = allocatedAt,
  notes = notes,
  isReversed = isReversed
)

fun ProductBatchEntity.toDomain() = ProductBatch(
  id = id,
  productId = productId,
  batchNumber = batchNumber,
  purchaseInvoiceId = purchaseInvoiceId,
  purchaseDate = purchaseDate,
  expiryDate = expiryDate,
  initialQuantityPieces = initialQuantityPieces,
  currentQuantityPieces = currentQuantityPieces,
  purchaseCostPerPiecePaise = purchaseCostPerPiecePaise,
  isActive = isActive,
  createdAt = createdAt
)

fun StockMovementEntity.toDomain() = StockMovement(
  id = id,
  productId = productId,
  batchId = batchId,
  movementType = movementType,
  quantityPieces = quantityPieces,
  unitCostPaise = unitCostPaise,
  referenceType = referenceType,
  referenceId = referenceId,
  movementDate = movementDate,
  notes = notes,
  createdAt = createdAt
)

fun PurchaseInvoiceEntity.toDomain() = PurchaseInvoice(
  id = id,
  invoiceNumber = invoiceNumber,
  supplierId = supplierId,
  invoiceDate = invoiceDate,
  dueDate = dueDate,
  totalAmountPaise = totalAmountPaise,
  paidAmountPaise = paidAmountPaise,
  paymentStatus = paymentStatus,
  paymentMethod = paymentMethod,
  notes = notes,
  isConfirmed = isConfirmed,
  createdAt = createdAt
)

fun PurchaseInvoiceItemEntity.toDomain() = PurchaseInvoiceItem(
  id = id,
  purchaseInvoiceId = purchaseInvoiceId,
  productId = productId,
  batchNumber = batchNumber,
  expiryDate = expiryDate,
  quantityPieces = quantityPieces,
  costPerPiecePaise = costPerPiecePaise,
  lineTotalPaise = lineTotalPaise
)

fun SalesInvoiceEntity.toDomain() = SalesInvoice(
  id = id,
  invoiceNumber = invoiceNumber,
  customerId = customerId,
  invoiceDate = invoiceDate,
  dueDate = dueDate,
  totalAmountPaise = totalAmountPaise,
  paidAmountPaise = paidAmountPaise,
  paymentStatus = paymentStatus,
  paymentMethod = paymentMethod,
  notes = notes,
  isConfirmed = isConfirmed,
  createdAt = createdAt
)

fun SalesInvoiceItemEntity.toDomain() = SalesInvoiceItem(
  id = id,
  salesInvoiceId = salesInvoiceId,
  productId = productId,
  batchId = batchId,
  quantityPieces = quantityPieces,
  unitPricePaise = unitPricePaise,
  lineTotalPaise = lineTotalPaise
)

fun ExpenseEntity.toDomain() = Expense(
  id = id,
  category = category,
  amountPaise = amountPaise,
  expenseDate = expenseDate,
  paymentMethod = paymentMethod,
  referenceNumber = referenceNumber,
  notes = notes,
  createdAt = createdAt,
  deletedAt = deletedAt
)
