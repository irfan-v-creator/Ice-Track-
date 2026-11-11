package com.example.domain.engine

import com.example.domain.model.PaymentAllocation
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

data class AllocationResult(
  val allocations: List<PaymentAllocation>,
  val unallocatedRemainingPaise: Long,
  val invoiceStatuses: Map<Long, PaymentStatus>
)

object PaymentAllocationEngine {

  /**
   * Allocates payment across outstanding invoices using FIFO (First In First Out) strategy.
   * Invoices are sorted by oldest date first.
   */
  fun allocateFifo(
    paymentId: Long,
    totalPaymentPaise: Long,
    invoices: List<InvoiceAllocationTarget>
  ): AllocationResult {
    require(totalPaymentPaise >= 0) { "Payment amount cannot be negative" }

    val sortedInvoices = invoices.sortedBy { it.invoiceDate }
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
          invoiceId = target.invoiceId,
          allocatedAmountPaise = allocateAmount
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
