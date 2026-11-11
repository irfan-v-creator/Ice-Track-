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
import com.example.domain.engine.FefoInventoryEngine
import com.example.domain.engine.InvoiceAllocationTarget
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

class ProductRepository(private val db: IceTrackDatabase) {
  private val productDao = db.productDao()
  private val unitDao = db.productUnitDao()
  private val productSupplierDao = db.productSupplierDao()

  val allActiveProducts: Flow<List<Product>> = productDao.getAllActive().map { list ->
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

  suspend fun saveProductWithUnit(
    product: Product,
    piecesPerBox: Int,
    boxesPerCarton: Int,
    supplierCosts: List<ProductSupplier> = emptyList()
  ): Long {
    UnitConversionEngine.validateRatios(piecesPerBox, boxesPerCarton)

    val entity = product.toEntity()
    val productId = if (product.id == 0L) {
      productDao.insert(entity)
    } else {
      productDao.update(entity)
      product.id
    }

    unitDao.insertOrUpdate(
      ProductUnitEntity(
        productId = productId,
        piecesPerBox = piecesPerBox,
        boxesPerCarton = boxesPerCarton
      )
    )

    for (sc in supplierCosts) {
      productSupplierDao.insertOrUpdate(
        ProductSupplierEntity(
          productId = productId,
          supplierId = sc.supplierId,
          supplierProductCode = sc.supplierProductCode,
          purchaseCostPaise = sc.purchaseCostPaise,
          isPreferredSupplier = sc.isPreferredSupplier
        )
      )
    }

    return productId
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

  suspend fun saveSupplier(supplier: Supplier): Long {
    val isNew = supplier.id == 0L
    val supplierId = if (isNew) {
      supplierDao.insert(supplier.toEntity())
    } else {
      supplierDao.update(supplier.toEntity())
      supplier.id
    }

    if (isNew && supplier.openingBalancePaise > 0L) {
      subledgerDao.insert(
        SubledgerEntryEntity(
          partyType = PartyType.SUPPLIER,
          partyId = supplierId,
          entryType = SubledgerEntryType.OPENING_BALANCE,
          referenceType = "SUPPLIER_OPENING",
          referenceId = supplierId,
          debitPaise = 0L,
          creditPaise = supplier.openingBalancePaise,
          entryDate = supplier.openingBalanceDate,
          notes = "Initial opening balance payable"
        )
      )
    }

    return supplierId
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

  suspend fun getPriceForCustomerProduct(customerId: Long, productId: Long): Long? {
    return priceDao.getPrice(customerId, productId)?.customSellingPricePaise
  }

  suspend fun saveCustomer(
    customer: Customer,
    customPrices: List<CustomerProductPrice> = emptyList()
  ): Long {
    val isNew = customer.id == 0L
    val customerId = if (isNew) {
      customerDao.insert(customer.toEntity())
    } else {
      customerDao.update(customer.toEntity())
      customer.id
    }

    if (isNew && customer.openingBalancePaise > 0L) {
      subledgerDao.insert(
        SubledgerEntryEntity(
          partyType = PartyType.CUSTOMER,
          partyId = customerId,
          entryType = SubledgerEntryType.OPENING_BALANCE,
          referenceType = "CUSTOMER_OPENING",
          referenceId = customerId,
          debitPaise = customer.openingBalancePaise,
          creditPaise = 0L,
          entryDate = customer.openingBalanceDate,
          notes = "Initial opening balance receivable"
        )
      )
    }

    for (cp in customPrices) {
      priceDao.insertOrUpdate(
        CustomerProductPriceEntity(
          customerId = customerId,
          productId = cp.productId,
          customSellingPricePaise = cp.customSellingPricePaise
        )
      )
    }

    return customerId
  }

  suspend fun softDelete(customerId: Long) {
    customerDao.softDelete(customerId)
  }
}

class SubledgerRepository(private val db: IceTrackDatabase) {
  private val subledgerDao = db.subledgerDao()

  val allEntries: Flow<List<SubledgerEntry>> = subledgerDao.getAllEntries().map { list ->
    list.map { it.toDomain() }
  }

  fun getEntriesForParty(partyType: PartyType, partyId: Long): Flow<List<SubledgerEntry>> {
    return subledgerDao.getEntriesForParty(partyType, partyId).map { list ->
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
    val batch = batchDao.getById(batchId) ?: throw IllegalArgumentException("Batch $batchId not found")
    val newQuantity = batch.currentQuantityPieces + adjustmentPieces
    if (newQuantity < 0) {
      throw IllegalArgumentException("Cannot reduce stock below zero (current: ${batch.currentQuantityPieces}, adjust: $adjustmentPieces)")
    }

    if (adjustmentPieces > 0) {
      batchDao.addStock(batchId, adjustmentPieces)
    } else if (adjustmentPieces < 0) {
      batchDao.deductStock(batchId, kotlin.math.abs(adjustmentPieces))
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
        notes = reason
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

  suspend fun createPurchaseInvoice(
    supplierId: Long,
    invoiceNumber: String,
    invoiceDate: Long,
    dueDate: Long,
    items: List<PurchaseInvoiceItem>,
    paidAmountPaise: Long,
    paymentMethod: PaymentMethod?,
    notes: String
  ): Long = db.withTransaction {
    require(items.isNotEmpty()) { "Purchase invoice must have at least one product line" }
    require(paidAmountPaise >= 0) { "Paid amount cannot be negative" }

    val totalAmountPaise = items.sumOf { it.lineTotalPaise }
    val paymentStatus = when {
      paidAmountPaise >= totalAmountPaise -> PaymentStatus.PAID
      paidAmountPaise > 0 -> PaymentStatus.PARTIALLY_PAID
      else -> PaymentStatus.UNPAID
    }

    // 1. Insert Purchase Invoice Header
    val invoiceEntity = PurchaseInvoiceEntity(
      invoiceNumber = invoiceNumber,
      supplierId = supplierId,
      invoiceDate = invoiceDate,
      dueDate = dueDate,
      totalAmountPaise = totalAmountPaise,
      paidAmountPaise = paidAmountPaise,
      paymentStatus = paymentStatus,
      paymentMethod = paymentMethod,
      notes = notes,
      isConfirmed = true
    )
    val invoiceId = purchaseDao.insert(invoiceEntity)

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
          notes = "Received via Invoice #$invoiceNumber"
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
        notes = "Purchase Invoice #$invoiceNumber"
      )
    )

    // 4. Subledger Accounts Payable Debit if partial/full payment was made at purchase time
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
          notes = "Payment for Purchase #$invoiceNumber (${paymentMethod?.displayName ?: "Direct"})"
        )
      )

      allocationDao.insert(
        PaymentAllocationEntity(
          paymentId = paymentSubledgerId,
          invoiceId = invoiceId,
          allocatedAmountPaise = paidAmountPaise,
          allocatedAt = invoiceDate
        )
      )
    }

    invoiceId
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

    // 2. FIFO Payment Allocation across unpaid purchase invoices
    val unpaidInvoices = purchaseDao.getUnpaidForSupplier(supplierId)
    val targets = unpaidInvoices.map { inv ->
      InvoiceAllocationTarget(
        invoiceId = inv.id,
        totalAmountPaise = inv.totalAmountPaise,
        alreadyPaidPaise = inv.paidAmountPaise,
        invoiceDate = inv.invoiceDate
      )
    }

    val allocationResult = PaymentAllocationEngine.allocateFifo(
      paymentId = subledgerId,
      totalPaymentPaise = amountPaise,
      invoices = targets
    )

    for (alloc in allocationResult.allocations) {
      allocationDao.insert(alloc.toEntity())
      val inv = unpaidInvoices.first { it.id == alloc.invoiceId }
      val newPaid = inv.paidAmountPaise + alloc.allocatedAmountPaise
      val status = allocationResult.invoiceStatuses[alloc.invoiceId] ?: PaymentStatus.PARTIALLY_PAID
      purchaseDao.update(inv.copy(paidAmountPaise = newPaid, paymentStatus = status))
    }

    subledgerId
  }
}

class SalesRepository(private val db: IceTrackDatabase) {
  private val salesDao = db.salesInvoiceDao()
  private val itemDao = db.salesInvoiceItemDao()
  private val batchDao = db.productBatchDao()
  private val movementDao = db.stockMovementDao()
  private val subledgerDao = db.subledgerDao()
  private val allocationDao = db.paymentAllocationDao()

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

  data class RequestedSaleLine(
    val productId: Long,
    val quantityPieces: Int,
    val unitPricePaise: Long
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
    allowExpired: Boolean = false
  ): Long = db.withTransaction {
    require(requestedLines.isNotEmpty()) { "Sales invoice must have at least one product line" }
    require(paidAmountPaise >= 0) { "Paid amount cannot be negative" }

    // 1. Validate and execute FEFO stock allocation for all requested lines
    val itemsToInsert = mutableListOf<SalesInvoiceItemEntity>()
    var totalAmountPaise = 0L

    for (line in requestedLines) {
      val availableBatches = batchDao.getActiveBatchesForProductSync(line.productId).map { it.toDomain() }
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
        val lineTotal = alloc.quantityPiecesToIssue.toLong() * line.unitPricePaise
        totalAmountPaise += lineTotal
        itemsToInsert.add(
          SalesInvoiceItemEntity(
            salesInvoiceId = 0L, // will be populated after header insert
            productId = line.productId,
            batchId = alloc.batch.id,
            quantityPieces = alloc.quantityPiecesToIssue,
            unitPricePaise = line.unitPricePaise,
            lineTotalPaise = lineTotal
          )
        )
      }
    }

    val paymentStatus = when {
      paidAmountPaise >= totalAmountPaise -> PaymentStatus.PAID
      paidAmountPaise > 0 -> PaymentStatus.PARTIALLY_PAID
      else -> PaymentStatus.UNPAID
    }

    // 2. Insert Sales Invoice Header
    val headerEntity = SalesInvoiceEntity(
      invoiceNumber = invoiceNumber,
      customerId = customerId,
      invoiceDate = invoiceDate,
      dueDate = dueDate,
      totalAmountPaise = totalAmountPaise,
      paidAmountPaise = paidAmountPaise,
      paymentStatus = paymentStatus,
      paymentMethod = paymentMethod,
      notes = notes,
      isConfirmed = true
    )
    val invoiceId = salesDao.insert(headerEntity)

    // 3. Deduct stock from batches, insert items and record stock dispatch movements
    for (item in itemsToInsert) {
      val itemWithHeader = item.copy(salesInvoiceId = invoiceId)
      itemDao.insertAll(listOf(itemWithHeader))

      batchDao.deductStock(item.batchId, item.quantityPieces)

      movementDao.insert(
        StockMovementEntity(
          productId = item.productId,
          batchId = item.batchId,
          movementType = StockMovementType.SALE_DISPATCH,
          quantityPieces = -item.quantityPieces,
          unitCostPaise = item.unitPricePaise,
          referenceType = "SALES_INVOICE",
          referenceId = invoiceId,
          movementDate = invoiceDate,
          notes = "Dispatched via Sales Invoice #$invoiceNumber"
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
        notes = "Sales Invoice #$invoiceNumber"
      )
    )

    // 5. Subledger Accounts Receivable Credit if partial/full payment was received at billing time
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
          notes = "Payment received for Sale #$invoiceNumber (${paymentMethod?.displayName ?: "Direct"})"
        )
      )

      allocationDao.insert(
        PaymentAllocationEntity(
          paymentId = paymentSubledgerId,
          invoiceId = invoiceId,
          allocatedAmountPaise = paidAmountPaise,
          allocatedAt = invoiceDate
        )
      )
    }

    invoiceId
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

    // 2. FIFO Payment Allocation across unpaid sales invoices
    val unpaidInvoices = salesDao.getUnpaidForCustomer(customerId)
    val targets = unpaidInvoices.map { inv ->
      InvoiceAllocationTarget(
        invoiceId = inv.id,
        totalAmountPaise = inv.totalAmountPaise,
        alreadyPaidPaise = inv.paidAmountPaise,
        invoiceDate = inv.invoiceDate
      )
    }

    val allocationResult = PaymentAllocationEngine.allocateFifo(
      paymentId = subledgerId,
      totalPaymentPaise = amountPaise,
      invoices = targets
    )

    for (alloc in allocationResult.allocations) {
      allocationDao.insert(alloc.toEntity())
      val inv = unpaidInvoices.first { it.id == alloc.invoiceId }
      val newPaid = inv.paidAmountPaise + alloc.allocatedAmountPaise
      val status = allocationResult.invoiceStatuses[alloc.invoiceId] ?: PaymentStatus.PARTIALLY_PAID
      salesDao.update(inv.copy(paidAmountPaise = newPaid, paymentStatus = status))
    }

    subledgerId
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
  invoiceId = invoiceId,
  allocatedAmountPaise = allocatedAmountPaise,
  allocatedAt = allocatedAt,
  isReversed = isReversed
)

fun PaymentAllocation.toEntity() = PaymentAllocationEntity(
  id = id,
  paymentId = paymentId,
  invoiceId = invoiceId,
  allocatedAmountPaise = allocatedAmountPaise,
  allocatedAt = allocatedAt,
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
