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

enum class ReminderStatus(val displayName: String) {
  DRAFT("Draft"),
  WHATSAPP_OPENED("WhatsApp Opened"),
  USER_CONFIRMED_SENT("Confirmed Sent"),
  FAILED("Failed")
}

enum class ReminderDueKind(val displayName: String) {
  INVOICE_BASED("Invoice Dues"),
  ACCOUNT_DUE("Opening / Account Due")
}

// ==========================================
// Phase 3 Enums: Lifecycle, Returns, Notes, Refunds & Audit
// ==========================================

enum class InvoiceStatus(val displayName: String) {
  ACTIVE("Active"),
  CANCELLED("Cancelled")
}

enum class DiscountType(val displayName: String) {
  NONE("No Discount"),
  FLAT("Flat (₹)"),
  PERCENT("Percentage (%)")
}

enum class ReturnCondition(val displayName: String, val isSaleable: Boolean) {
  RESELLABLE("Resellable Stock", true),
  DAMAGED("Damaged (Non-Saleable)", false),
  MELTED("Melted (Non-Saleable)", false),
  EXPIRED("Expired (Non-Saleable)", false)
}

enum class ReturnSettlementType(val displayName: String) {
  CREDIT_NOTE("Credit Note"),
  DEBIT_NOTE("Debit Note"),
  CASH_REFUND("Recorded Cash Refund"),
  ADVANCE("Keep as Advance Credit")
}

enum class CreditDebitNoteType(val displayName: String) {
  CREDIT_NOTE("Credit Note"),
  DEBIT_NOTE("Debit Note")
}

enum class NoteStatus(val displayName: String) {
  OPEN("Open"),
  APPLIED("Partially / Applied"),
  SETTLED("Settled"),
  CANCELLED("Cancelled")
}

enum class AdvanceReversalStrategy(val displayName: String) {
  BLOCK_IF_CONSUMED("Block if Advance Used"),
  CASCADE_UNAPPLY("Unapply Used Advance & Reverse")
}

enum class InvoiceCancellationPaymentMode(val displayName: String) {
  BLOCK_IF_PAYMENTS_EXIST("Block if Active Payments Exist"),
  CONVERT_TO_ADVANCE("Convert Payments to Advance")
}

enum class RefundSourceType(val displayName: String) {
  INVOICE_EDIT_EXCESS("Invoice Edit Overpayment"),
  INVOICE_CANCEL("Cancelled Invoice Payment"),
  SALES_RETURN("Sales Return"),
  PURCHASE_RETURN("Purchase Return"),
  CREDIT_DEBIT_NOTE("Credit / Debit Note Settlement")
}

enum class RefundExecutionStatus(val displayName: String) {
  RECORDED_PENDING_PAYOUT("Recorded Liability (Pending Payout)"),
  PAYOUT_EXECUTED("Payout Executed")
}

enum class AuditOperationType(val displayName: String) {
  INVOICE_EDIT("Invoice Edit"),
  INVOICE_CANCEL("Invoice Cancellation"),
  PAYMENT_REVERSAL("Payment Reversal"),
  SALES_RETURN("Sales Return"),
  PURCHASE_RETURN("Purchase Return"),
  CREDIT_NOTE("Credit Note"),
  DEBIT_NOTE("Debit Note"),
  OPENING_BALANCE_EDIT("Opening Balance Edit"),
  EXCESS_SETTLEMENT("Excess Settlement"),
  REFUND_PAYOUT("Refund Payout"),
  FINANCIAL_ADJUSTMENT("Financial Adjustment"),
  BACKUP_RESTORE("Backup Restore")
}

