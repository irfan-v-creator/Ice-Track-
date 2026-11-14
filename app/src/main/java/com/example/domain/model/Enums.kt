package com.example.domain.model

enum class UnitType(val displayName: String, val shortName: String) {
  PIECE("Piece", "Pcs"),
  BOX("Box", "Box"),
  CARTON("Carton", "Ctn")
}

enum class PartyType {
  CUSTOMER,
  SUPPLIER
}

enum class SubledgerEntryType(val displayName: String) {
  OPENING_BALANCE("Opening Balance"),
  INVOICE("Invoice"),
  PAYMENT("Payment"),
  CREDIT_NOTE("Credit Note"),
  DEBIT_NOTE("Debit Note"),
  ADJUSTMENT("Adjustment")
}

enum class PaymentStatus(val displayName: String) {
  UNPAID("Unpaid"),
  PARTIALLY_PAID("Partially Paid"),
  PAID("Paid")
}

enum class PaymentMethod(val displayName: String) {
  CASH("Cash"),
  UPI("UPI"),
  BANK_TRANSFER("Bank Transfer"),
  CHEQUE("Cheque")
}

enum class StockMovementType(val displayName: String) {
  PURCHASE_RECEIPT("Purchase Receipt"),
  PURCHASE_RETURN("Purchase Return"),
  SALE_DISPATCH("Sale Dispatch"),
  SALE_RETURN("Sale Return"),
  WASTAGE_EXPIRED("Wastage / Expired"),
  STOCK_ADJUSTMENT("Stock Adjustment")
}

enum class ExpenseCategory(val displayName: String) {
  VEHICLE_FUEL("Vehicle Fuel"),
  VEHICLE_MAINTENANCE("Vehicle Maintenance & Service"),
  ELECTRICITY_COLD_STORAGE("Electricity & Cold Storage"),
  ICE_SALT_PACKAGING("Dry Ice, Salt & Packaging"),
  SHOP_RENT_OFFICE("Warehouse / Office Rent"),
  SALARY_LABOUR("Staff & Loading Labour"),
  MISCELLANEOUS("Miscellaneous / Other")
}

enum class PaymentAllocationType(val displayName: String) {
  INVOICE("Invoice Payment"),
  ADVANCE("Advance / Unallocated"),
  OPENING_BALANCE("Opening Balance Settlement")
}

