package com.example.domain.engine

import com.example.domain.model.PartyType
import com.example.domain.model.SubledgerEntry
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SubledgerCalculationEngine {

  private val currencyFormatter = DecimalFormat("##,##,##0.00")
  private val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

  /**
   * Calculates the Accounts Receivable balance for a Customer.
   * Debits (Invoices/Opening balance) increase what the customer owes.
   * Credits (Payments/Returns) decrease what the customer owes.
   * Active balance = Sum(Debits) - Sum(Credits).
   */
  fun calculateCustomerBalance(entries: List<SubledgerEntry>, asOfDate: Long? = null): Long {
    val filtered = if (asOfDate != null) {
      entries.filter { it.partyType == PartyType.CUSTOMER && !it.isReversed && it.entryDate <= asOfDate }
    } else {
      entries.filter { it.partyType == PartyType.CUSTOMER && !it.isReversed }
    }

    val totalDebits = filtered.fold(0L) { acc, entry -> acc + entry.debitPaise }
    val totalCredits = filtered.fold(0L) { acc, entry -> acc + entry.creditPaise }
    return totalDebits - totalCredits
  }

  /**
   * Calculates the Accounts Payable balance for a Supplier.
   * Credits (Purchases/Opening balance) increase what we owe the supplier.
   * Debits (Payments to supplier/Debit notes) decrease what we owe.
   * Active balance = Sum(Credits) - Sum(Debits).
   */
  fun calculateSupplierBalance(entries: List<SubledgerEntry>, asOfDate: Long? = null): Long {
    val filtered = if (asOfDate != null) {
      entries.filter { it.partyType == PartyType.SUPPLIER && !it.isReversed && it.entryDate <= asOfDate }
    } else {
      entries.filter { it.partyType == PartyType.SUPPLIER && !it.isReversed }
    }

    val totalCredits = filtered.fold(0L) { acc, entry -> acc + entry.creditPaise }
    val totalDebits = filtered.fold(0L) { acc, entry -> acc + entry.debitPaise }
    return totalCredits - totalDebits
  }

  /**
   * Converts Integer Paise to formatted Indian Rupee string (e.g. 524050L -> "₹5,240.50").
   */
  fun formatPaise(paise: Long, includeSymbol: Boolean = true): String {
    val isNegative = paise < 0
    val absPaise = kotlin.math.abs(paise)
    val rupees = absPaise.toDouble() / 100.0
    val formatted = currencyFormatter.format(rupees)
    val prefix = if (isNegative) "- " else ""
    val symbol = if (includeSymbol) "₹" else ""
    return "$prefix$symbol$formatted"
  }

  /**
   * Parses user input in Rupees (e.g. "150.50" or "5000") safely into Integer Paise.
   */
  fun parseRupeesToPaise(input: String): Long {
    val clean = input.trim().replace(",", "").replace("₹", "")
    if (clean.isBlank()) return 0L
    val doubleVal = clean.toDoubleOrNull() ?: 0.0
    return Math.round(doubleVal * 100.0)
  }

  fun formatDate(timestamp: Long): String {
    return dateFormatter.format(Date(timestamp))
  }
}
