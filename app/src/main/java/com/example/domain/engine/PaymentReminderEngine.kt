package com.example.domain.engine

import com.example.domain.model.Customer
import com.example.domain.model.PaymentStatus
import com.example.domain.model.ReminderDueKind
import com.example.domain.model.SalesInvoice
import com.example.domain.model.SubledgerEntry
import kotlin.math.abs

data class PhoneValidationResult(
  val isValid: Boolean,
  val normalizedE164: String? = null,
  val whatsappPhoneDigits: String? = null,
  val errorMessage: String? = null
)

data class PendingReminderInvoiceLine(
  val salesInvoiceId: Long,
  val invoiceDate: Long,
  val totalAmountPaise: Long,
  val pendingAmountPaise: Long
)

data class ComposedReminder(
  val customerId: Long,
  val targetPhoneE164: String,
  val whatsappPhoneDigits: String,
  val dueKind: ReminderDueKind,
  val pendingInvoices: List<PendingReminderInvoiceLine>,
  val totalInvoiceAmountPaise: Long,
  val totalInvoicePendingPaise: Long,
  val netOutstandingPaise: Long,
  val ledgerDifferencePaise: Long,
  val renderedMessage: String
)

sealed class ReminderCompositionResult {
  data class Ready(val reminder: ComposedReminder) : ReminderCompositionResult()
  data class Blocked(val reason: String, val netOutstandingPaise: Long) : ReminderCompositionResult()
}

object PaymentReminderEngine {

  private val multiSeparatorRegex = Regex("[,/;]")
  private val e164Regex = Regex("^\\+[1-9]\\d{7,14}$")
  private val indianMobile10Regex = Regex("^[6-9]\\d{9}$")

  fun normalizeAndValidatePhone(rawPhone: String): PhoneValidationResult {
    val trimmed = rawPhone.trim()
    if (trimmed.isEmpty()) {
      return PhoneValidationResult(
        isValid = false,
        errorMessage = "Customer phone number is missing. Add a valid mobile number before sending a reminder."
      )
    }
    if (multiSeparatorRegex.containsMatchIn(trimmed)) {
      return PhoneValidationResult(
        isValid = false,
        errorMessage = "Multiple phone numbers found. Keep a single valid WhatsApp mobile number."
      )
    }

    var cleaned = trimmed
      .replace(" ", "")
      .replace("-", "")
      .replace("(", "")
      .replace(")", "")
      .replace(".", "")

    if (cleaned.startsWith("00")) {
      cleaned = "+" + cleaned.drop(2)
    }

    if (cleaned.startsWith("+")) {
      if (!e164Regex.matches(cleaned)) {
        return PhoneValidationResult(
          isValid = false,
          errorMessage = "Enter a valid phone number with country code (e.g., +91 9876543210)."
        )
      }
      if (cleaned.startsWith("+91")) {
        val local = cleaned.removePrefix("+91")
        if (!indianMobile10Regex.matches(local)) {
          return PhoneValidationResult(
            isValid = false,
            errorMessage = "Invalid Indian mobile number. Expected 10 digits starting with 6-9 after +91."
          )
        }
      }
      return PhoneValidationResult(
        isValid = true,
        normalizedE164 = cleaned,
        whatsappPhoneDigits = cleaned.removePrefix("+")
      )
    }

    if (!cleaned.all { it.isDigit() }) {
      return PhoneValidationResult(
        isValid = false,
        errorMessage = "Phone number contains invalid characters."
      )
    }

    val digits = when {
      cleaned.length == 11 && cleaned.startsWith("0") -> cleaned.drop(1)
      else -> cleaned
    }

    return when {
      digits.length == 10 && indianMobile10Regex.matches(digits) -> {
        val e164 = "+91$digits"
        PhoneValidationResult(
          isValid = true,
          normalizedE164 = e164,
          whatsappPhoneDigits = "91$digits"
        )
      }
      digits.length == 12 && digits.startsWith("91") && indianMobile10Regex.matches(digits.drop(2)) -> {
        val e164 = "+$digits"
        PhoneValidationResult(
          isValid = true,
          normalizedE164 = e164,
          whatsappPhoneDigits = digits
        )
      }
      else -> {
        PhoneValidationResult(
          isValid = false,
          errorMessage = "Enter a valid 10-digit Indian mobile number (starting with 6-9) or E.164 number with +."
        )
      }
    }
  }

  /**
   * Composes a customer payment reminder strictly from live Subledger SSOT and unpaid Sales Invoices.
   * Never reads prior reminder snapshots and never includes product/item/SKU/batch/quantity details.
   */
  fun composeReminder(
    customer: Customer,
    businessName: String,
    subledgerEntries: List<SubledgerEntry>,
    customerSalesInvoices: List<SalesInvoice>
  ): ReminderCompositionResult {
    val customerEntries = subledgerEntries.filter { it.partyId == customer.id }
    val netOutstandingPaise = SubledgerCalculationEngine.calculateCustomerBalance(customerEntries)

    if (netOutstandingPaise <= 0L) {
      val reason = if (netOutstandingPaise < 0L) {
        "Customer has an advance credit of ${SubledgerCalculationEngine.formatPaise(abs(netOutstandingPaise))}. Payment reminders are blocked."
      } else {
        "Customer net outstanding balance is ₹0.00. Payment reminders are blocked."
      }
      return ReminderCompositionResult.Blocked(
        reason = reason,
        netOutstandingPaise = netOutstandingPaise
      )
    }

    val phoneResult = normalizeAndValidatePhone(customer.phoneNumber)
    if (!phoneResult.isValid || phoneResult.normalizedE164 == null || phoneResult.whatsappPhoneDigits == null) {
      return ReminderCompositionResult.Blocked(
        reason = phoneResult.errorMessage ?: "Invalid customer phone number.",
        netOutstandingPaise = netOutstandingPaise
      )
    }

    val pendingInvoices = customerSalesInvoices
      .asSequence()
      .filter { it.customerId == customer.id && it.paymentStatus != PaymentStatus.PAID && it.balanceDuePaise > 0L }
      .sortedWith(compareBy<SalesInvoice> { it.invoiceDate }.thenBy { it.id })
      .map { invoice ->
        PendingReminderInvoiceLine(
          salesInvoiceId = invoice.id,
          invoiceDate = invoice.invoiceDate,
          totalAmountPaise = invoice.totalAmountPaise,
          pendingAmountPaise = invoice.balanceDuePaise
        )
      }
      .toList()

    val safeBusinessName = businessName.trim().ifEmpty { "IceTrack" }
    val safeShopName = customer.shopName.trim().ifEmpty { "Customer" }

    return if (pendingInvoices.isNotEmpty()) {
      val totalInvoiceAmountPaise = pendingInvoices.sumOf { it.totalAmountPaise }
      val totalInvoicePendingPaise = pendingInvoices.sumOf { it.pendingAmountPaise }
      val differencePaise = netOutstandingPaise - totalInvoicePendingPaise

      val message = buildString {
        appendLine("Dear $safeShopName,")
        appendLine("Payment reminder from $safeBusinessName.")
        appendLine()
        appendLine("Pending Invoices (Oldest First):")
        pendingInvoices.forEachIndexed { index, inv ->
          appendLine(
            "${index + 1}. Invoice Date: ${SubledgerCalculationEngine.formatDate(inv.invoiceDate)} | " +
              "Total: ${SubledgerCalculationEngine.formatPaise(inv.totalAmountPaise)} | " +
              "Pending: ${SubledgerCalculationEngine.formatPaise(inv.pendingAmountPaise)}"
          )
        }
        appendLine()
        appendLine("Total Invoice Amount: ${SubledgerCalculationEngine.formatPaise(totalInvoiceAmountPaise)}")
        if (differencePaise > 0L) {
          appendLine(
            "Opening Balance / Account Adjustments Due: ${SubledgerCalculationEngine.formatPaise(differencePaise)}"
          )
        } else if (differencePaise < 0L) {
          appendLine(
            "Less Unapplied Advance / Credit Adjustment: ${SubledgerCalculationEngine.formatPaise(abs(differencePaise))}"
          )
        }
        appendLine("Total Outstanding Amount: ${SubledgerCalculationEngine.formatPaise(netOutstandingPaise)}")
        appendLine()
        append("Kindly arrange the payment at your earliest convenience. Thank you.")
      }

      ReminderCompositionResult.Ready(
        ComposedReminder(
          customerId = customer.id,
          targetPhoneE164 = phoneResult.normalizedE164,
          whatsappPhoneDigits = phoneResult.whatsappPhoneDigits,
          dueKind = ReminderDueKind.INVOICE_BASED,
          pendingInvoices = pendingInvoices,
          totalInvoiceAmountPaise = totalInvoiceAmountPaise,
          totalInvoicePendingPaise = totalInvoicePendingPaise,
          netOutstandingPaise = netOutstandingPaise,
          ledgerDifferencePaise = differencePaise,
          renderedMessage = message
        )
      )
    } else {
      val message = buildString {
        appendLine("Dear $safeShopName,")
        appendLine("Payment reminder from $safeBusinessName.")
        appendLine()
        appendLine("Account Due Summary:")
        appendLine("Due Type: Opening Balance / Account Due")
        appendLine("Total Outstanding Amount: ${SubledgerCalculationEngine.formatPaise(netOutstandingPaise)}")
        appendLine()
        append("Kindly arrange the payment at your earliest convenience. Thank you.")
      }

      ReminderCompositionResult.Ready(
        ComposedReminder(
          customerId = customer.id,
          targetPhoneE164 = phoneResult.normalizedE164,
          whatsappPhoneDigits = phoneResult.whatsappPhoneDigits,
          dueKind = ReminderDueKind.ACCOUNT_DUE,
          pendingInvoices = emptyList(),
          totalInvoiceAmountPaise = 0L,
          totalInvoicePendingPaise = 0L,
          netOutstandingPaise = netOutstandingPaise,
          ledgerDifferencePaise = netOutstandingPaise,
          renderedMessage = message
        )
      )
    }
  }
}
