package com.example.domain.engine

import com.example.domain.model.PartyType
import com.example.domain.model.PaymentAllocation
import com.example.domain.model.PaymentAllocationType
import com.example.domain.model.PaymentStatus

data class InvoiceAllocationTarget(
  val invoiceId: Long,
  val totalAmountPaise: Long,
  val alreadyPaidPaise: Long,
  val invoiceDate: Long
) {
  val remainingUnpaidPaise: Long get() = (totalAmountPaise - alreadyPaidPaise).coerceAtLeast(0L)
  val isFullyPaid: Boolean get() = remainingUnpaidPaise == 0L
}

data class OpeningBalanceAllocationTarget(
  val subledgerEntryId: Long,
  val originalAmountPaise: Long,
  val alreadyAllocatedPaise: Long,
  val entryDate: Long
) {
  val remainingUnpaidPaise: Long get() = (originalAmountPaise - alreadyAllocatedPaise).coerceAtLeast(0L)
}

data class AllocationResult(
  val allocations: List<PaymentAllocation>,
  val unallocatedRemainingPaise: Long,
  val invoiceStatuses: Map<Long, PaymentStatus>
)

data class FullAllocationResult(
  val allocations: List<PaymentAllocation>,
  val invoiceStatuses: Map<Long, PaymentStatus>,
  val invoiceAdditionalPaidPaise: Map<Long, Long>,
  val openingBalanceAllocatedPaise: Long,
  val advanceCreatedPaise: Long
)

data class CreditLimitAssessment(
  val currentBalancePaise: Long,
  val newCreditExposurePaise: Long,
  val projectedBalancePaise: Long,
  val creditLimitPaise: Long,
  val exceededByPaise: Long
) {
  val isExceeded: Boolean get() = creditLimitPaise > 0L && exceededByPaise > 0L
}

object PaymentAllocationEngine {

  const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

  /**
   * Computes invoice due date from invoice date and configured payment/credit terms (days).
   */
  fun calculateDueDate(invoiceDateMillis: Long, termsDays: Int): Long {
    val safeDays = termsDays.coerceAtLeast(0)
    return invoiceDateMillis + safeDays.toLong() * MILLIS_PER_DAY
  }

  /**
   * Assesses whether adding net unpaid exposure for a party exceeds configured credit limit.
   * A credit limit <= 0 means no limit configured.
   */
  fun assessCreditLimit(
    currentBalancePaise: Long,
    newCreditExposurePaise: Long,
    creditLimitPaise: Long
  ): CreditLimitAssessment {
    val exposure = newCreditExposurePaise.coerceAtLeast(0L)
    val projected = currentBalancePaise + exposure
    val exceeded = if (creditLimitPaise > 0L && exposure > 0L && projected > creditLimitPaise) {
      projected - creditLimitPaise
    } else {
      0L
    }
    return CreditLimitAssessment(
      currentBalancePaise = currentBalancePaise,
      newCreditExposurePaise = exposure,
      projectedBalancePaise = projected,
      creditLimitPaise = creditLimitPaise,
      exceededByPaise = exceeded
    )
  }

  /**
   * Allocates payment across outstanding invoices using FIFO (First In First Out) strategy.
   * Invoices are sorted by oldest date first.
   */
  fun allocateFifo(
    paymentId: Long,
    totalPaymentPaise: Long,
    invoices: List<InvoiceAllocationTarget>,
    partyType: PartyType = PartyType.CUSTOMER,
    partyId: Long = 0L,
    allocatedAt: Long = System.currentTimeMillis()
  ): AllocationResult {
    require(totalPaymentPaise >= 0) { "Payment amount cannot be negative" }

    val sortedInvoices = invoices.sortedWith(compareBy<InvoiceAllocationTarget> { it.invoiceDate }.thenBy { it.invoiceId })
    var availablePayment = totalPaymentPaise
    val allocations = mutableListOf<PaymentAllocation>()
    val statusMap = mutableMapOf<Long, PaymentStatus>()

    for (target in sortedInvoices) {
      val needed = target.remainingUnpaidPaise
      if (needed <= 0L) {
        statusMap[target.invoiceId] = PaymentStatus.PAID
        continue
      }

      if (availablePayment <= 0L) {
        statusMap[target.invoiceId] = if (target.alreadyPaidPaise > 0L) {
          PaymentStatus.PARTIALLY_PAID
        } else {
          PaymentStatus.UNPAID
        }
        continue
      }

      val allocateAmount = kotlin.math.min(availablePayment, needed)
      allocations.add(
        PaymentAllocation(
          paymentId = paymentId,
          partyType = partyType,
          partyId = partyId,
          allocationType = PaymentAllocationType.INVOICE,
          invoiceId = target.invoiceId,
          allocatedAmountPaise = allocateAmount,
          allocatedAt = allocatedAt,
          notes = "FIFO invoice allocation"
        )
      )

      availablePayment -= allocateAmount
      val newTotalPaid = target.alreadyPaidPaise + allocateAmount
      statusMap[target.invoiceId] = when {
        newTotalPaid >= target.totalAmountPaise -> PaymentStatus.PAID
        newTotalPaid > 0L -> PaymentStatus.PARTIALLY_PAID
        else -> PaymentStatus.UNPAID
      }
    }

    return AllocationResult(
      allocations = allocations,
      unallocatedRemainingPaise = availablePayment,
      invoiceStatuses = statusMap
    )
  }

  /**
   * Allocates a party payment strictly FIFO across:
   * 1. Remaining positive Opening Balance entries (sorted oldest first)
   * 2. Unpaid / Partially Paid Invoices (sorted oldest first, strictly capped to remainingUnpaidPaise)
   * 3. Any unallocated excess becomes an explicit ADVANCE allocation (invoiceId = null)
   */
  fun allocateFifoAcrossDuesAndAdvance(
    paymentId: Long,
    partyType: PartyType,
    partyId: Long,
    totalPaymentPaise: Long,
    openingBalances: List<OpeningBalanceAllocationTarget>,
    invoices: List<InvoiceAllocationTarget>,
    allocatedAt: Long = System.currentTimeMillis(),
    advanceNote: String = "Unallocated advance credit"
  ): FullAllocationResult {
    require(totalPaymentPaise >= 0L) { "Payment amount cannot be negative" }

    var remaining = totalPaymentPaise
    val allocations = mutableListOf<PaymentAllocation>()
    var openingAllocatedTotal = 0L

    val sortedOpening = openingBalances.sortedWith(
      compareBy<OpeningBalanceAllocationTarget> { it.entryDate }.thenBy { it.subledgerEntryId }
    )
    val sortedInvoices = invoices.sortedWith(
      compareBy<InvoiceAllocationTarget> { it.invoiceDate }.thenBy { it.invoiceId }
    )

    // Interleave chronologically by entryDate vs invoiceDate so oldest dues settle first
    var obIdx = 0
    var invIdx = 0
    val statusMap = mutableMapOf<Long, PaymentStatus>()
    val invoiceAddedMap = mutableMapOf<Long, Long>()

    fun applyOpening(ob: OpeningBalanceAllocationTarget) {
      val needed = ob.remainingUnpaidPaise
      if (needed > 0L && remaining > 0L) {
        val take = kotlin.math.min(remaining, needed)
        allocations.add(
          PaymentAllocation(
            paymentId = paymentId,
            partyType = partyType,
            partyId = partyId,
            allocationType = PaymentAllocationType.OPENING_BALANCE,
            invoiceId = null,
            allocatedAmountPaise = take,
            allocatedAt = allocatedAt,
            notes = "Opening balance settlement (Entry #${ob.subledgerEntryId})"
          )
        )
        remaining -= take
        openingAllocatedTotal += take
      }
    }

    fun applyInvoice(inv: InvoiceAllocationTarget) {
      val needed = inv.remainingUnpaidPaise
      if (needed <= 0L) {
        statusMap[inv.invoiceId] = PaymentStatus.PAID
        return
      }
      if (remaining <= 0L) {
        statusMap[inv.invoiceId] = if (inv.alreadyPaidPaise > 0L) PaymentStatus.PARTIALLY_PAID else PaymentStatus.UNPAID
        return
      }
      val take = kotlin.math.min(remaining, needed)
      allocations.add(
        PaymentAllocation(
          paymentId = paymentId,
          partyType = partyType,
          partyId = partyId,
          allocationType = PaymentAllocationType.INVOICE,
          invoiceId = inv.invoiceId,
          allocatedAmountPaise = take,
          allocatedAt = allocatedAt,
          notes = "FIFO invoice allocation"
        )
      )
      remaining -= take
      invoiceAddedMap[inv.invoiceId] = take
      val newPaid = inv.alreadyPaidPaise + take
      statusMap[inv.invoiceId] = when {
        newPaid >= inv.totalAmountPaise -> PaymentStatus.PAID
        newPaid > 0L -> PaymentStatus.PARTIALLY_PAID
        else -> PaymentStatus.UNPAID
      }
    }

    while (obIdx < sortedOpening.size && invIdx < sortedInvoices.size) {
      val ob = sortedOpening[obIdx]
      val inv = sortedInvoices[invIdx]
      if (ob.entryDate <= inv.invoiceDate) {
        applyOpening(ob)
        obIdx++
      } else {
        applyInvoice(inv)
        invIdx++
      }
    }
    while (obIdx < sortedOpening.size) {
      applyOpening(sortedOpening[obIdx])
      obIdx++
    }
    while (invIdx < sortedInvoices.size) {
      applyInvoice(sortedInvoices[invIdx])
      invIdx++
    }

    var advanceCreated = 0L
    if (remaining > 0L) {
      advanceCreated = remaining
      allocations.add(
        PaymentAllocation(
          paymentId = paymentId,
          partyType = partyType,
          partyId = partyId,
          allocationType = PaymentAllocationType.ADVANCE,
          invoiceId = null,
          allocatedAmountPaise = advanceCreated,
          allocatedAt = allocatedAt,
          notes = advanceNote
        )
      )
      remaining = 0L
    }

    return FullAllocationResult(
      allocations = allocations,
      invoiceStatuses = statusMap,
      invoiceAdditionalPaidPaise = invoiceAddedMap,
      openingBalanceAllocatedPaise = openingAllocatedTotal,
      advanceCreatedPaise = advanceCreated
    )
  }

  /**
   * Validates manual allocations against invoice limits and total payment bounds.
   */
  fun validateManualAllocations(
    totalPaymentPaise: Long,
    invoices: List<InvoiceAllocationTarget>,
    requestedAllocations: Map<Long, Long> // invoiceId -> amountPaise
  ) {
    val totalRequested = requestedAllocations.values.sum()
    if (totalRequested > totalPaymentPaise) {
      throw IllegalArgumentException("Total allocated ($totalRequested paise) exceeds payment amount ($totalPaymentPaise paise)")
    }

    val invoiceMap = invoices.associateBy { it.invoiceId }
    for ((invoiceId, amount) in requestedAllocations) {
      if (amount < 0) throw IllegalArgumentException("Allocation cannot be negative for invoice $invoiceId")
      val target = invoiceMap[invoiceId] ?: throw IllegalArgumentException("Invoice $invoiceId not found")
      if (amount > target.remainingUnpaidPaise) {
        throw IllegalArgumentException("Allocated amount ($amount paise) exceeds invoice unpaid balance (${target.remainingUnpaidPaise} paise)")
      }
    }
  }
}
