package com.example.domain.engine

import com.example.domain.model.DiscountType
import com.example.domain.model.PartyType
import com.example.domain.model.SubledgerEntry
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SubledgerCalculationEngine {

  private val dateFormatterThreadLocal: ThreadLocal<SimpleDateFormat> =
    ThreadLocal.withInitial { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

  private val strictMoneyRegex = Regex("^[+-]?\\d+(?:\\.\\d{1,2})?$")

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
   * Thread-safe Indian Rupee formatter with proper 2-2-3 Lakh/Crore digit grouping
   * (e.g. 524050L -> "₹5,240.50", 100000000L -> "₹10,00,000.00").
   */
  fun formatPaise(paise: Long, includeSymbol: Boolean = true): String {
    val isNegative = paise < 0L
    val absPaiseBig = BigInteger.valueOf(paise).abs()
    val (rupeesBig, remPaiseBig) = absPaiseBig.divideAndRemainder(BigInteger.valueOf(100L))
    val rupeesDigits = rupeesBig.toString()
    val groupedRupees = if (rupeesDigits.length <= 3) {
      rupeesDigits
    } else {
      val lastThree = rupeesDigits.takeLast(3)
      val prefix = rupeesDigits.dropLast(3)
      val groupedPrefix = prefix.reversed().chunked(2).joinToString(",").reversed()
      "$groupedPrefix,$lastThree"
    }
    val paiseStr = remPaiseBig.toInt().toString().padStart(2, '0')
    val prefix = if (isNegative) "- " else ""
    val symbol = if (includeSymbol) "₹" else ""
    return "$prefix$symbol$groupedRupees.$paiseStr"
  }

  /**
   * Formats paise to an exact editable decimal string without scientific notation or commas
   * (e.g. 1250L -> "12.50", 10000L -> "100", 0L -> "0").
   */
  fun formatPaiseForInput(paise: Long): String {
    return if (paise % 100L == 0L) {
      (paise / 100L).toString()
    } else {
      BigDecimal.valueOf(paise, 2).toPlainString()
    }
  }

  /**
   * Strictly parses user input in Rupees into Integer Paise using BigDecimal.
   * Returns null if malformed, contains >2 decimal places, overflows Long, or violates [allowNegative].
   */
  fun parseRupeesToPaiseOrNull(
    input: String,
    allowNegative: Boolean = false,
    allowEmptyAsZero: Boolean = false
  ): Long? {
    val clean = input.trim()
      .replace("₹", "")
      .replace(",", "")
      .replace(" ", "")

    if (clean.isEmpty()) {
      return if (allowEmptyAsZero) 0L else null
    }
    if (!strictMoneyRegex.matches(clean)) {
      return null
    }

    val paise = try {
      BigDecimal(clean)
        .movePointRight(2)
        .longValueExact()
    } catch (_: NumberFormatException) {
      return null
    } catch (_: ArithmeticException) {
      return null
    }

    if (!allowNegative && paise < 0L) {
      return null
    }
    return paise
  }

  /**
   * Strictly parses user input in Rupees into Integer Paise, throwing IllegalArgumentException on invalid input.
   */
  fun parseRupeesToPaiseStrict(
    input: String,
    fieldName: String = "Amount",
    allowNegative: Boolean = false,
    allowEmptyAsZero: Boolean = false
  ): Long {
    return parseRupeesToPaiseOrNull(
      input = input,
      allowNegative = allowNegative,
      allowEmptyAsZero = allowEmptyAsZero
    ) ?: throw IllegalArgumentException("Invalid $fieldName: '$input'. Enter a valid number with up to 2 decimal places.")
  }

  /**
   * Parses user input in Rupees into Integer Paise using BigDecimal.
   * Blank strings resolve to 0L; malformed non-blank inputs throw IllegalArgumentException.
   */
  fun parseRupeesToPaise(input: String): Long {
    return parseRupeesToPaiseStrict(
      input = input,
      fieldName = "amount",
      allowNegative = false,
      allowEmptyAsZero = true
    )
  }

  fun formatDate(timestamp: Long): String {
    val formatter = dateFormatterThreadLocal.get()
      ?: SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return formatter.format(Date(timestamp))
  }

  /**
   * Computes exact integer paise discount from [subtotalPaise] using BigDecimal HALF_UP.
   * For [DiscountType.PERCENT], [discountValueRaw] is in basis points (e.g., 500 = 5.00%, 10000 = 100.00%).
   * For [DiscountType.FLAT], [discountValueRaw] is in paise.
   */
  fun calculateDiscountPaise(
    subtotalPaise: Long,
    discountType: DiscountType,
    discountValueRaw: Long
  ): Long {
    require(subtotalPaise >= 0L) { "Subtotal cannot be negative" }
    return when (discountType) {
      DiscountType.NONE -> 0L
      DiscountType.FLAT -> {
        require(discountValueRaw >= 0L) { "Flat discount cannot be negative" }
        require(discountValueRaw <= subtotalPaise) {
          "Flat discount (${formatPaise(discountValueRaw)}) cannot exceed subtotal (${formatPaise(subtotalPaise)})"
        }
        discountValueRaw
      }
      DiscountType.PERCENT -> {
        require(discountValueRaw in 0L..10000L) { "Percentage discount must be between 0% and 100%" }
        BigDecimal.valueOf(subtotalPaise)
          .multiply(BigDecimal.valueOf(discountValueRaw))
          .divide(BigDecimal.valueOf(10000L), 0, RoundingMode.HALF_UP)
          .longValueExact()
          .coerceAtMost(subtotalPaise)
      }
    }
  }

  fun parsePercentToBasisPoints(input: String): Long {
    val clean = input.trim().replace("%", "").replace(" ", "")
    if (clean.isEmpty()) return 0L
    val bp = parseRupeesToPaiseOrNull(clean, allowNegative = false, allowEmptyAsZero = true)
      ?: throw IllegalArgumentException("Invalid percentage: '$input'. Enter 0 to 100 with up to 2 decimals.")
    require(bp in 0L..10000L) { "Percentage must be between 0% and 100%" }
    return bp
  }

  fun formatBasisPointsAsPercent(basisPoints: Long): String {
    return formatPaiseForInput(basisPoints)
  }

  data class InvoiceTotalComputation(
    val subtotalPaise: Long,
    val discountAmountPaise: Long,
    val additionalChargesPaise: Long,
    val totalAmountPaise: Long
  )

  fun computeInvoiceTotals(
    subtotalPaise: Long,
    discountType: DiscountType,
    discountValueRaw: Long,
    additionalChargesPaise: Long
  ): InvoiceTotalComputation {
    require(subtotalPaise >= 0L) { "Subtotal cannot be negative" }
    require(additionalChargesPaise >= 0L) { "Additional charges cannot be negative" }
    val discountPaise = calculateDiscountPaise(subtotalPaise, discountType, discountValueRaw)
    val total = (subtotalPaise - discountPaise + additionalChargesPaise).coerceAtLeast(0L)
    return InvoiceTotalComputation(
      subtotalPaise = subtotalPaise,
      discountAmountPaise = discountPaise,
      additionalChargesPaise = additionalChargesPaise,
      totalAmountPaise = total
    )
  }
}
