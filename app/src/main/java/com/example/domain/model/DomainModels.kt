package com.example.domain.model

data class QuantityBreakdown(
  val cartons: Int,
  val boxes: Int,
  val pieces: Int,
  val totalPieces: Long
) {
  fun toFormattedString(): String {
    val parts = mutableListOf<String>()
    if (cartons > 0) parts.add("$cartons Ctn")
    if (boxes > 0) parts.add("$boxes Box")
    if (pieces > 0 || parts.isEmpty()) parts.add("$pieces Pcs")
    return parts.joinToString(", ")
  }
}

data class Product(
  val id: Long = 0,
  val name: String,
  val sku: String,
  val category: String,
  val defaultPurchasePricePaise: Long,
  val defaultSellingPricePaise: Long,
  val minStockLevelPieces: Int,
  val isActive: Boolean = true,
  val createdAt: Long = System.currentTimeMillis()
)

data class ProductUnit(
  val id: Long = 0,
  val productId: Long,
  val piecesPerBox: Int,
  val boxesPerCarton: Int
) {
  val piecesPerCarton: Int get() = piecesPerBox * boxesPerCarton
}

data class ProductSupplier(
  val id: Long = 0,
  val productId: Long,
  val supplierId: Long,
  val supplierProductCode: String = "",
  val purchaseCostPaise: Long,
  val isPreferredSupplier: Boolean = false
)

data class Supplier(
  val id: Long = 0,
  val name: String,
  val contactPerson: String,
  val phoneNumber: String,
  val address: String,
  val openingBalancePaise: Long = 0L,
  val openingBalanceDate: Long = System.currentTimeMillis(),
  val creditTermsDays: Int = 15,
  val creditLimitPaise: Long = 0L,
  val isActive: Boolean = true,
  val createdAt: Long = System.currentTimeMillis()
)

data class Customer(
  val id: Long = 0,
  val shopName: String,
  val ownerName: String,
  val phoneNumber: String,
  val address: String,
  val openingBalancePaise: Long = 0L,
  val openingBalanceDate: Long = System.currentTimeMillis(),
  val creditLimitPaise: Long = 0L,
  val paymentTermsDays: Int = 7,
  val isActive: Boolean = true,
  val createdAt: Long = System.currentTimeMillis()
)

data class CustomerProductPrice(
  val id: Long = 0,
  val customerId: Long,
  val productId: Long,
  val customSellingPricePaise: Long
)

data class SubledgerEntry(
  val id: Long = 0,
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

data class PaymentAllocation(
  val id: Long = 0,
  val paymentId: Long,
  val partyType: PartyType = PartyType.CUSTOMER,
  val partyId: Long = 0L,
  val allocationType: PaymentAllocationType = PaymentAllocationType.INVOICE,
  val invoiceId: Long? = null,
  val allocatedAmountPaise: Long,
  val allocatedAt: Long = System.currentTimeMillis(),
  val notes: String = "",
  val isReversed: Boolean = false
)

data class AppSetting(
  val key: String,
  val value: String
)

// Step 2 Domain Models

data class ProductBatch(
  val id: Long = 0,
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
) {
  val isExpired: Boolean get() = expiryDate <= System.currentTimeMillis()
}

data class StockMovement(
  val id: Long = 0,
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

data class PurchaseInvoice(
  val id: Long = 0,
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
) {
  val balanceDuePaise: Long get() = (totalAmountPaise - paidAmountPaise).coerceAtLeast(0L)
}

data class PurchaseInvoiceItem(
  val id: Long = 0,
  val purchaseInvoiceId: Long = 0,
  val productId: Long,
  val batchNumber: String,
  val expiryDate: Long,
  val quantityPieces: Int,
  val costPerPiecePaise: Long,
  val lineTotalPaise: Long
)

data class SalesInvoice(
  val id: Long = 0,
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
) {
  val balanceDuePaise: Long get() = (totalAmountPaise - paidAmountPaise).coerceAtLeast(0L)
}

data class SalesInvoiceItem(
  val id: Long = 0,
  val salesInvoiceId: Long = 0,
  val productId: Long,
  val batchId: Long,
  val quantityPieces: Int,
  val unitPricePaise: Long,
  val lineTotalPaise: Long
)

data class Expense(
  val id: Long = 0,
  val category: ExpenseCategory,
  val amountPaise: Long,
  val expenseDate: Long,
  val paymentMethod: PaymentMethod,
  val referenceNumber: String = "",
  val notes: String = "",
  val createdAt: Long = System.currentTimeMillis(),
  val deletedAt: Long? = null
)
