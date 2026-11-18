package com.example.data.remote

import com.example.data.local.entity.*
import com.example.domain.engine.CloudSyncConflictEngine
import com.example.domain.model.*
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue

/**
 * Bidirectional 1:1 payload mappers between IceTrack Room V5 entities and Firestore documents.
 *
 * Strictly enforces:
 * - No `"id"` field inside written maps (only `localId` + `@DocumentId` on reads).
 * - No `is`-prefixed Boolean keys.
 * - `.filterValues { it != null }` so absent optional fields are omitted rather than stored as `null`.
 * - `FieldValue.serverTimestamp()` for `createdAt` / `updatedAt` (never overwritten on updates).
 * - Safe Number conversions & safe enum fallback on reads (`DocumentSnapshot.ServerTimestampBehavior.ESTIMATE`).
 */
object FirestoreSyncMappers {

  fun serverEstimate(snapshot: DocumentSnapshot): Map<String, Any?> =
    snapshot.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: emptyMap()

  private inline fun <reified E : Enum<E>> safeEnum(raw: Any?, default: E): E {
    val name = raw as? String ?: return default
    return enumValues<E>().firstOrNull { it.name == name } ?: default
  }

  private inline fun <reified E : Enum<E>> safeEnumOrNull(raw: Any?): E? {
    val name = raw as? String ?: return null
    return enumValues<E>().firstOrNull { it.name == name }
  }

  private fun numLong(data: Map<String, Any?>, key: String, default: Long = 0L): Long =
    (data[key] as? Number)?.toLong() ?: default

  private fun numLongOrNull(data: Map<String, Any?>, key: String): Long? =
    (data[key] as? Number)?.toLong()

  private fun numInt(data: Map<String, Any?>, key: String, default: Int = 0): Int =
    (data[key] as? Number)?.toInt() ?: default

  private fun boolVal(data: Map<String, Any?>, key: String, default: Boolean = false): Boolean =
    (data[key] as? Boolean) ?: default

  private fun strVal(data: Map<String, Any?>, key: String, default: String = ""): String =
    (data[key] as? String) ?: default

  fun businessFieldsWithHash(rawMap: Map<String, Any?>): Pair<Map<String, Any>, String> {
    val clean = linkedMapOf<String, Any>()
    for ((k, v) in rawMap) {
      if (v != null) clean[k] = v
    }
    val hash = CloudSyncConflictEngine.computeBusinessHash(clean)
    clean["contentHash"] = hash
    return clean to hash
  }

  fun attachCreateTimestamps(map: MutableMap<String, Any>, hasUpdatedAt: Boolean = true): Map<String, Any> {
    map["createdAt"] = FieldValue.serverTimestamp()
    if (hasUpdatedAt) map["updatedAt"] = FieldValue.serverTimestamp()
    return map
  }

  fun attachUpdateTimestamp(
    fullMap: Map<String, Any>,
    immutableKeys: Set<String>
  ): Map<String, Any> {
    val diff = linkedMapOf<String, Any>()
    for ((k, v) in fullMap) {
      if (k !in immutableKeys && k != "createdAt" && k != "updatedAt") {
        diff[k] = v
      }
    }
    diff["updatedAt"] = FieldValue.serverTimestamp()
    return diff
  }

  fun workspaceCreateMap(
    uid: String,
    email: String?,
    displayName: String?,
    businessName: String?,
    lastSyncAtEpochMs: Long
  ): Map<String, Any> = linkedMapOf<String, Any?>(
    "userId" to uid,
    "email" to email?.take(200),
    "displayName" to displayName?.take(120),
    "businessName" to businessName?.take(150),
    "schemaVersion" to 5,
    "lastSyncAtEpochMs" to lastSyncAtEpochMs.coerceAtLeast(0L),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp()
  ).filterValues { it != null }.mapValues { it.value!! }

  fun workspaceUpdateMap(
    email: String?,
    displayName: String?,
    businessName: String?,
    lastSyncAtEpochMs: Long
  ): Map<String, Any> = linkedMapOf<String, Any?>(
    "email" to email?.take(200),
    "displayName" to displayName?.take(120),
    "businessName" to businessName?.take(150),
    "schemaVersion" to 5,
    "lastSyncAtEpochMs" to lastSyncAtEpochMs.coerceAtLeast(0L),
    "updatedAt" to FieldValue.serverTimestamp()
  ).filterValues { it != null }.mapValues { it.value!! }

  fun toProductFields(uid: String, p: ProductEntity, unit: ProductUnitEntity?, updatedAtMs: Long): Pair<Map<String, Any>, String> =
    businessFieldsWithHash(
      linkedMapOf(
        "userId" to uid,
        "localId" to p.id,
        "name" to p.name.ifBlank { "Unnamed Product" }.take(200),
        "sku" to p.sku.ifBlank { "SKU-${p.id}" }.take(80),
        "category" to p.category.ifBlank { "Ice Cream" }.take(100),
        "defaultPurchasePricePaise" to p.defaultPurchasePricePaise.coerceAtLeast(0L),
        "defaultSellingPricePaise" to p.defaultSellingPricePaise.coerceAtLeast(0L),
        "minStockLevelPieces" to p.minStockLevelPieces.coerceAtLeast(0),
        "piecesPerBox" to (unit?.piecesPerBox ?: 1).coerceAtLeast(1),
        "boxesPerCarton" to (unit?.boxesPerCarton ?: 1).coerceAtLeast(1),
        "active" to p.isActive,
        "localCreatedAtMs" to p.createdAt.coerceAtLeast(0L),
        "localUpdatedAtMs" to maxOf(updatedAtMs, p.createdAt, p.deletedAt ?: 0L).coerceAtLeast(0L),
        "deletedAtEpochMs" to p.deletedAt?.coerceAtLeast(0L)
      )
    )

  fun fromProductDoc(data: Map<String, Any?>): Pair<ProductEntity, ProductUnitEntity> {
    val pid = numLong(data, "localId")
    val product = ProductEntity(
      id = pid,
      name = strVal(data, "name"),
      sku = strVal(data, "sku"),
      category = strVal(data, "category"),
      defaultPurchasePricePaise = numLong(data, "defaultPurchasePricePaise"),
      defaultSellingPricePaise = numLong(data, "defaultSellingPricePaise"),
      minStockLevelPieces = numInt(data, "minStockLevelPieces"),
      isActive = boolVal(data, "active", true),
      createdAt = numLong(data, "localCreatedAtMs"),
      deletedAt = numLongOrNull(data, "deletedAtEpochMs")
    )
    val unit = ProductUnitEntity(
      id = pid,
      productId = pid,
      piecesPerBox = numInt(data, "piecesPerBox", 1).coerceAtLeast(1),
      boxesPerCarton = numInt(data, "boxesPerCarton", 1).coerceAtLeast(1)
    )
    return product to unit
  }

  fun toSupplierFields(uid: String, s: SupplierEntity, updatedAtMs: Long): Pair<Map<String, Any>, String> =
    businessFieldsWithHash(
      linkedMapOf(
        "userId" to uid,
        "localId" to s.id,
        "name" to s.name.ifBlank { "Supplier #${s.id}" }.take(200),
        "contactPerson" to s.contactPerson.take(150),
        "phoneNumber" to s.phoneNumber.take(40),
        "address" to s.address.take(300),
        "openingBalancePaise" to s.openingBalancePaise,
        "openingBalanceDateEpochMs" to s.openingBalanceDate.coerceAtLeast(0L),
        "creditTermsDays" to s.creditTermsDays.coerceAtLeast(0),
        "creditLimitPaise" to s.creditLimitPaise.coerceAtLeast(0L),
        "active" to s.isActive,
        "localCreatedAtMs" to s.createdAt.coerceAtLeast(0L),
        "localUpdatedAtMs" to maxOf(updatedAtMs, s.createdAt, s.deletedAt ?: 0L).coerceAtLeast(0L),
        "deletedAtEpochMs" to s.deletedAt?.coerceAtLeast(0L)
      )
    )

  fun fromSupplierDoc(data: Map<String, Any?>): SupplierEntity = SupplierEntity(
    id = numLong(data, "localId"),
    name = strVal(data, "name"),
    contactPerson = strVal(data, "contactPerson"),
    phoneNumber = strVal(data, "phoneNumber"),
    address = strVal(data, "address"),
    openingBalancePaise = numLong(data, "openingBalancePaise"),
    openingBalanceDate = numLong(data, "openingBalanceDateEpochMs"),
    creditTermsDays = numInt(data, "creditTermsDays", 15),
    creditLimitPaise = numLong(data, "creditLimitPaise"),
    isActive = boolVal(data, "active", true),
    createdAt = numLong(data, "localCreatedAtMs"),
    deletedAt = numLongOrNull(data, "deletedAtEpochMs")
  )

  fun toCustomerFields(uid: String, c: CustomerEntity, updatedAtMs: Long): Pair<Map<String, Any>, String> =
    businessFieldsWithHash(
      linkedMapOf(
        "userId" to uid,
        "localId" to c.id,
        "shopName" to c.shopName.ifBlank { "Shop #${c.id}" }.take(200),
        "ownerName" to c.ownerName.take(150),
        "phoneNumber" to c.phoneNumber.take(40),
        "address" to c.address.take(300),
        "openingBalancePaise" to c.openingBalancePaise,
        "openingBalanceDateEpochMs" to c.openingBalanceDate.coerceAtLeast(0L),
        "creditLimitPaise" to c.creditLimitPaise.coerceAtLeast(0L),
        "paymentTermsDays" to c.paymentTermsDays.coerceAtLeast(0),
        "active" to c.isActive,
        "localCreatedAtMs" to c.createdAt.coerceAtLeast(0L),
        "localUpdatedAtMs" to maxOf(updatedAtMs, c.createdAt, c.deletedAt ?: 0L).coerceAtLeast(0L),
        "deletedAtEpochMs" to c.deletedAt?.coerceAtLeast(0L)
      )
    )

  fun fromCustomerDoc(data: Map<String, Any?>): CustomerEntity = CustomerEntity(
    id = numLong(data, "localId"),
    shopName = strVal(data, "shopName"),
    ownerName = strVal(data, "ownerName"),
    phoneNumber = strVal(data, "phoneNumber"),
    address = strVal(data, "address"),
    openingBalancePaise = numLong(data, "openingBalancePaise"),
    openingBalanceDate = numLong(data, "openingBalanceDateEpochMs"),
    creditLimitPaise = numLong(data, "creditLimitPaise"),
    paymentTermsDays = numInt(data, "paymentTermsDays", 7),
    isActive = boolVal(data, "active", true),
    createdAt = numLong(data, "localCreatedAtMs"),
    deletedAt = numLongOrNull(data, "deletedAtEpochMs")
  )

  fun toCustomerPriceFields(uid: String, cp: CustomerProductPriceEntity, updatedAtMs: Long): Pair<Map<String, Any>, String> =
    businessFieldsWithHash(
      linkedMapOf(
        "userId" to uid,
        "localId" to cp.id,
        "customerLocalId" to cp.customerId,
        "productLocalId" to cp.productId,
        "customSellingPricePaise" to cp.customSellingPricePaise.coerceAtLeast(0L),
        "localUpdatedAtMs" to updatedAtMs.coerceAtLeast(0L)
      )
    )

  fun fromCustomerPriceDoc(data: Map<String, Any?>): CustomerProductPriceEntity = CustomerProductPriceEntity(
    id = numLong(data, "localId"),
    customerId = numLong(data, "customerLocalId"),
    productId = numLong(data, "productLocalId"),
    customSellingPricePaise = numLong(data, "customSellingPricePaise")
  )

  fun toProductSupplierFields(uid: String, ps: ProductSupplierEntity, updatedAtMs: Long): Pair<Map<String, Any>, String> =
    businessFieldsWithHash(
      linkedMapOf(
        "userId" to uid,
        "localId" to ps.id,
        "productLocalId" to ps.productId,
        "supplierLocalId" to ps.supplierId,
        "supplierProductCode" to ps.supplierProductCode.take(100),
        "purchaseCostPaise" to ps.purchaseCostPaise.coerceAtLeast(0L),
        "preferredSupplier" to ps.isPreferredSupplier,
        "localUpdatedAtMs" to updatedAtMs.coerceAtLeast(0L)
      )
    )

  fun fromProductSupplierDoc(data: Map<String, Any?>): ProductSupplierEntity = ProductSupplierEntity(
    id = numLong(data, "localId"),
    productId = numLong(data, "productLocalId"),
    supplierId = numLong(data, "supplierLocalId"),
    supplierProductCode = strVal(data, "supplierProductCode"),
    purchaseCostPaise = numLong(data, "purchaseCostPaise"),
    isPreferredSupplier = boolVal(data, "preferredSupplier", false)
  )

  fun toBatchFields(uid: String, b: ProductBatchEntity, updatedAtMs: Long): Pair<Map<String, Any>, String> =
    businessFieldsWithHash(
      linkedMapOf(
        "userId" to uid,
        "localId" to b.id,
        "productLocalId" to b.productId,
        "batchNumber" to b.batchNumber.ifBlank { "B-${b.id}" }.take(100),
        "purchaseInvoiceLocalId" to b.purchaseInvoiceId?.coerceAtLeast(0L),
        "purchaseDateEpochMs" to b.purchaseDate.coerceAtLeast(0L),
        "expiryDateEpochMs" to b.expiryDate.coerceAtLeast(0L),
        "initialQuantityPieces" to b.initialQuantityPieces.coerceAtLeast(0),
        "currentQuantityPieces" to b.currentQuantityPieces.coerceAtLeast(0),
        "purchaseCostPerPiecePaise" to b.purchaseCostPerPiecePaise.coerceAtLeast(0L),
        "active" to b.isActive,
        "localCreatedAtMs" to b.createdAt.coerceAtLeast(0L),
        "localUpdatedAtMs" to maxOf(updatedAtMs, b.createdAt).coerceAtLeast(0L)
      )
    )

  fun fromBatchDoc(data: Map<String, Any?>): ProductBatchEntity = ProductBatchEntity(
    id = numLong(data, "localId"),
    productId = numLong(data, "productLocalId"),
    batchNumber = strVal(data, "batchNumber"),
    purchaseInvoiceId = numLongOrNull(data, "purchaseInvoiceLocalId"),
    purchaseDate = numLong(data, "purchaseDateEpochMs"),
    expiryDate = numLong(data, "expiryDateEpochMs"),
    initialQuantityPieces = numInt(data, "initialQuantityPieces"),
    currentQuantityPieces = numInt(data, "currentQuantityPieces"),
    purchaseCostPerPiecePaise = numLong(data, "purchaseCostPerPiecePaise"),
    isActive = boolVal(data, "active", true),
    createdAt = numLong(data, "localCreatedAtMs")
  )

  fun toStockMovementFields(uid: String, m: StockMovementEntity): Pair<Map<String, Any>, String> =
    businessFieldsWithHash(
      linkedMapOf(
        "userId" to uid,
        "localId" to m.id,
        "productLocalId" to m.productId,
        "batchLocalId" to m.batchId?.coerceAtLeast(0L),
        "movementType" to m.movementType.name,
        "quantityPieces" to m.quantityPieces,
        "unitCostPaise" to m.unitCostPaise.coerceAtLeast(0L),
        "referenceType" to m.referenceType.ifBlank { "MANUAL" }.take(80),
        "referenceId" to m.referenceId,
        "movementDateEpochMs" to m.movementDate.coerceAtLeast(0L),
        "notes" to m.notes.take(500),
        "localCreatedAtMs" to m.createdAt.coerceAtLeast(0L)
      )
    )

  fun fromStockMovementDoc(data: Map<String, Any?>): StockMovementEntity = StockMovementEntity(
    id = numLong(data, "localId"),
    productId = numLong(data, "productLocalId"),
    batchId = numLongOrNull(data, "batchLocalId"),
    movementType = safeEnum(data["movementType"], StockMovementType.STOCK_ADJUSTMENT),
    quantityPieces = numInt(data, "quantityPieces"),
    unitCostPaise = numLong(data, "unitCostPaise"),
    referenceType = strVal(data, "referenceType"),
    referenceId = numLong(data, "referenceId"),
    movementDate = numLong(data, "movementDateEpochMs"),
    notes = strVal(data, "notes"),
    createdAt = numLong(data, "localCreatedAtMs")
  )

  fun toPurchaseInvoiceFields(uid: String, inv: PurchaseInvoiceEntity): Pair<Map<String, Any>, String> =
    businessFieldsWithHash(
      linkedMapOf(
        "userId" to uid,
        "localId" to inv.id,
        "invoiceNumber" to inv.invoiceNumber.ifBlank { "PUR-${inv.id}" }.take(80),
        "supplierLocalId" to inv.supplierId,
        "invoiceDateEpochMs" to inv.invoiceDate.coerceAtLeast(0L),
        "dueDateEpochMs" to inv.dueDate.coerceAtLeast(0L),
        "subtotalPaise" to inv.subtotalPaise.coerceAtLeast(0L),
        "discountType" to inv.discountType.name,
        "discountValueRaw" to inv.discountValueRaw.coerceAtLeast(0L),
        "discountAmountPaise" to inv.discountAmountPaise.coerceAtLeast(0L),
        "additionalChargesPaise" to inv.additionalChargesPaise.coerceAtLeast(0L),
        "totalAmountPaise" to inv.totalAmountPaise.coerceAtLeast(0L),
        "paidAmountPaise" to inv.paidAmountPaise.coerceAtLeast(0L),
        "paymentStatus" to inv.paymentStatus.name,
        "paymentMethod" to inv.paymentMethod?.name,
        "invoiceStatus" to inv.invoiceStatus.name,
        "revisionVersion" to inv.revisionVersion.coerceAtLeast(1),
        "notes" to inv.notes.take(1000),
        "confirmed" to inv.isConfirmed,
        "partyNameSnapshot" to inv.partyNameSnapshot.take(200),
        "partyPhoneSnapshot" to inv.partyPhoneSnapshot.take(40),
        "partyAddressSnapshot" to inv.partyAddressSnapshot.take(300),
        "cancelledAtEpochMs" to inv.cancelledAt?.coerceAtLeast(0L),
        "cancellationReason" to inv.cancellationReason?.take(500),
        "localCreatedAtMs" to inv.createdAt.coerceAtLeast(0L),
        "localUpdatedAtMs" to maxOf(inv.updatedAt, inv.createdAt).coerceAtLeast(0L)
      )
    )

  fun fromPurchaseInvoiceDoc(data: Map<String, Any?>): PurchaseInvoiceEntity {
    val created = numLong(data, "localCreatedAtMs")
    return PurchaseInvoiceEntity(
      id = numLong(data, "localId"),
      invoiceNumber = strVal(data, "invoiceNumber"),
      supplierId = numLong(data, "supplierLocalId"),
      invoiceDate = numLong(data, "invoiceDateEpochMs"),
      dueDate = numLong(data, "dueDateEpochMs"),
      totalAmountPaise = numLong(data, "totalAmountPaise"),
      paidAmountPaise = numLong(data, "paidAmountPaise"),
      paymentStatus = safeEnum(data["paymentStatus"], PaymentStatus.UNPAID),
      paymentMethod = safeEnumOrNull<PaymentMethod>(data["paymentMethod"]),
      notes = strVal(data, "notes"),
      isConfirmed = boolVal(data, "confirmed", true),
      createdAt = created,
      subtotalPaise = numLong(data, "subtotalPaise"),
      discountType = safeEnum(data["discountType"], DiscountType.NONE),
      discountValueRaw = numLong(data, "discountValueRaw"),
      discountAmountPaise = numLong(data, "discountAmountPaise"),
      additionalChargesPaise = numLong(data, "additionalChargesPaise"),
      invoiceStatus = safeEnum(data["invoiceStatus"], InvoiceStatus.ACTIVE),
      cancelledAt = numLongOrNull(data, "cancelledAtEpochMs"),
      cancellationReason = data["cancellationReason"] as? String,
      revisionVersion = numInt(data, "revisionVersion", 1),
      updatedAt = numLong(data, "localUpdatedAtMs", created),
      partyNameSnapshot = strVal(data, "partyNameSnapshot"),
      partyPhoneSnapshot = strVal(data, "partyPhoneSnapshot"),
      partyAddressSnapshot = strVal(data, "partyAddressSnapshot")
    )
  }

  fun toPurchaseInvoiceItemFields(uid: String, item: PurchaseInvoiceItemEntity, updatedAtMs: Long): Pair<Map<String, Any>, String> =
    businessFieldsWithHash(
      linkedMapOf(
        "userId" to uid,
        "localId" to item.id,
        "purchaseInvoiceLocalId" to item.purchaseInvoiceId,
        "productLocalId" to item.productId,
        "batchNumber" to item.batchNumber.ifBlank { "B-${item.id}" }.take(100),
        "expiryDateEpochMs" to item.expiryDate.coerceAtLeast(0L),
        "quantityPieces" to item.quantityPieces.coerceAtLeast(1),
        "costPerPiecePaise" to item.costPerPiecePaise.coerceAtLeast(0L),
        "lineTotalPaise" to item.lineTotalPaise.coerceAtLeast(0L),
        "productNameSnapshot" to item.productNameSnapshot.take(200),
        "unitType" to item.unitType.name,
        "unitQuantity" to item.unitQuantity.coerceAtLeast(1),
        "unitRatePaise" to item.unitRatePaise.coerceAtLeast(0L),
        "itemDiscountPaise" to item.itemDiscountPaise.coerceAtLeast(0L),
        "localUpdatedAtMs" to updatedAtMs.coerceAtLeast(0L)
      )
    )

  fun fromPurchaseInvoiceItemDoc(data: Map<String, Any?>): PurchaseInvoiceItemEntity = PurchaseInvoiceItemEntity(
    id = numLong(data, "localId"),
    purchaseInvoiceId = numLong(data, "purchaseInvoiceLocalId"),
    productId = numLong(data, "productLocalId"),
    batchNumber = strVal(data, "batchNumber"),
    expiryDate = numLong(data, "expiryDateEpochMs"),
    quantityPieces = numInt(data, "quantityPieces", 1),
    costPerPiecePaise = numLong(data, "costPerPiecePaise"),
    lineTotalPaise = numLong(data, "lineTotalPaise"),
    productNameSnapshot = strVal(data, "productNameSnapshot"),
    unitType = safeEnum(data["unitType"], UnitType.PIECE),
    unitQuantity = numInt(data, "unitQuantity", 1),
    unitRatePaise = numLong(data, "unitRatePaise"),
    itemDiscountPaise = numLong(data, "itemDiscountPaise")
  )

  fun toSalesInvoiceFields(uid: String, inv: SalesInvoiceEntity): Pair<Map<String, Any>, String> =
    businessFieldsWithHash(
      linkedMapOf(
        "userId" to uid,
        "localId" to inv.id,
        "invoiceNumber" to inv.invoiceNumber.ifBlank { "INV-${inv.id}" }.take(80),
        "customerLocalId" to inv.customerId,
        "invoiceDateEpochMs" to inv.invoiceDate.coerceAtLeast(0L),
        "dueDateEpochMs" to inv.dueDate.coerceAtLeast(0L),
        "subtotalPaise" to inv.subtotalPaise.coerceAtLeast(0L),
        "discountType" to inv.discountType.name,
        "discountValueRaw" to inv.discountValueRaw.coerceAtLeast(0L),
        "discountAmountPaise" to inv.discountAmountPaise.coerceAtLeast(0L),
        "additionalChargesPaise" to inv.additionalChargesPaise.coerceAtLeast(0L),
        "totalAmountPaise" to inv.totalAmountPaise.coerceAtLeast(0L),
        "paidAmountPaise" to inv.paidAmountPaise.coerceAtLeast(0L),
        "paymentStatus" to inv.paymentStatus.name,
        "paymentMethod" to inv.paymentMethod?.name,
        "invoiceStatus" to inv.invoiceStatus.name,
        "revisionVersion" to inv.revisionVersion.coerceAtLeast(1),
        "notes" to inv.notes.take(1000),
        "confirmed" to inv.isConfirmed,
        "partyNameSnapshot" to inv.partyNameSnapshot.take(200),
        "partyPhoneSnapshot" to inv.partyPhoneSnapshot.take(40),
        "partyAddressSnapshot" to inv.partyAddressSnapshot.take(300),
        "cancelledAtEpochMs" to inv.cancelledAt?.coerceAtLeast(0L),
        "cancellationReason" to inv.cancellationReason?.take(500),
        "localCreatedAtMs" to inv.createdAt.coerceAtLeast(0L),
        "localUpdatedAtMs" to maxOf(inv.updatedAt, inv.createdAt).coerceAtLeast(0L)
      )
    )

  fun fromSalesInvoiceDoc(data: Map<String, Any?>): SalesInvoiceEntity {
    val created = numLong(data, "localCreatedAtMs")
    return SalesInvoiceEntity(
      id = numLong(data, "localId"),
      invoiceNumber = strVal(data, "invoiceNumber"),
      customerId = numLong(data, "customerLocalId"),
      invoiceDate = numLong(data, "invoiceDateEpochMs"),
      dueDate = numLong(data, "dueDateEpochMs"),
      totalAmountPaise = numLong(data, "totalAmountPaise"),
      paidAmountPaise = numLong(data, "paidAmountPaise"),
      paymentStatus = safeEnum(data["paymentStatus"], PaymentStatus.UNPAID),
      paymentMethod = safeEnumOrNull<PaymentMethod>(data["paymentMethod"]),
      notes = strVal(data, "notes"),
      isConfirmed = boolVal(data, "confirmed", true),
      createdAt = created,
      subtotalPaise = numLong(data, "subtotalPaise"),
      discountType = safeEnum(data["discountType"], DiscountType.NONE),
      discountValueRaw = numLong(data, "discountValueRaw"),
      discountAmountPaise = numLong(data, "discountAmountPaise"),
      additionalChargesPaise = numLong(data, "additionalChargesPaise"),
      invoiceStatus = safeEnum(data["invoiceStatus"], InvoiceStatus.ACTIVE),
      cancelledAt = numLongOrNull(data, "cancelledAtEpochMs"),
      cancellationReason = data["cancellationReason"] as? String,
      revisionVersion = numInt(data, "revisionVersion", 1),
      updatedAt = numLong(data, "localUpdatedAtMs", created),
      partyNameSnapshot = strVal(data, "partyNameSnapshot"),
      partyPhoneSnapshot = strVal(data, "partyPhoneSnapshot"),
      partyAddressSnapshot = strVal(data, "partyAddressSnapshot")
    )
  }

  fun toSalesInvoiceItemFields(uid: String, item: SalesInvoiceItemEntity, updatedAtMs: Long): Pair<Map<String, Any>, String> =
    businessFieldsWithHash(
      linkedMapOf(
        "userId" to uid,
        "localId" to item.id,
        "salesInvoiceLocalId" to item.salesInvoiceId,
        "productLocalId" to item.productId,
        "batchLocalId" to item.batchId,
        "quantityPieces" to item.quantityPieces.coerceAtLeast(1),
        "unitPricePaise" to item.unitPricePaise.coerceAtLeast(0L),
        "lineTotalPaise" to item.lineTotalPaise.coerceAtLeast(0L),
        "productNameSnapshot" to item.productNameSnapshot.take(200),
        "unitType" to item.unitType.name,
        "unitQuantity" to item.unitQuantity.coerceAtLeast(1),
        "unitRatePaise" to item.unitRatePaise.coerceAtLeast(0L),
        "itemDiscountPaise" to item.itemDiscountPaise.coerceAtLeast(0L),
        "localUpdatedAtMs" to updatedAtMs.coerceAtLeast(0L)
      )
    )

  fun fromSalesInvoiceItemDoc(data: Map<String, Any?>): SalesInvoiceItemEntity = SalesInvoiceItemEntity(
    id = numLong(data, "localId"),
    salesInvoiceId = numLong(data, "salesInvoiceLocalId"),
    productId = numLong(data, "productLocalId"),
    batchId = numLong(data, "batchLocalId"),
    quantityPieces = numInt(data, "quantityPieces", 1),
    unitPricePaise = numLong(data, "unitPricePaise"),
    lineTotalPaise = numLong(data, "lineTotalPaise"),
    productNameSnapshot = strVal(data, "productNameSnapshot"),
    unitType = safeEnum(data["unitType"], UnitType.PIECE),
    unitQuantity = numInt(data, "unitQuantity", 1),
    unitRatePaise = numLong(data, "unitRatePaise"),
    itemDiscountPaise = numLong(data, "itemDiscountPaise")
  )
}
