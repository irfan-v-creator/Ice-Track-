package com.example

import com.example.domain.engine.InvoiceAllocationTarget
import com.example.domain.engine.PaymentAllocationEngine
import com.example.domain.model.PaymentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PaymentAllocationEngineTest {

  @Test
  fun testAllocateFifo_allocatesOldestFirst() {
    val invoices = listOf(
      InvoiceAllocationTarget(invoiceId = 1, totalAmountPaise = 200000L, alreadyPaidPaise = 0L, invoiceDate = 1000L), // ₹2,000 (oldest)
      InvoiceAllocationTarget(invoiceId = 2, totalAmountPaise = 300000L, alreadyPaidPaise = 0L, invoiceDate = 2000L)  // ₹3,000
    )

    // Payment of ₹3,500 (350,000 paise)
    val result = PaymentAllocationEngine.allocateFifo(
      paymentId = 99,
      totalPaymentPaise = 350000L,
      invoices = invoices
    )

    assertEquals(2, result.allocations.size)
    // First invoice should get ₹2,000 and become PAID
    assertEquals(200000L, result.allocations[0].allocatedAmountPaise)
    assertEquals(PaymentStatus.PAID, result.invoiceStatuses[1])

    // Second invoice should get ₹1,500 and become PARTIALLY_PAID
    assertEquals(150000L, result.allocations[1].allocatedAmountPaise)
    assertEquals(PaymentStatus.PARTIALLY_PAID, result.invoiceStatuses[2])

    // Remaining unallocated payment = 0 paise
    assertEquals(0L, result.unallocatedRemainingPaise)
  }

  @Test
  fun testAllocateFifo_surplusPayment() {
    val invoices = listOf(
      InvoiceAllocationTarget(invoiceId = 1, totalAmountPaise = 100000L, alreadyPaidPaise = 0L, invoiceDate = 1000L)
    )

    // Payment of ₹1,500 (150,000 paise) against ₹1,000 invoice
    val result = PaymentAllocationEngine.allocateFifo(
      paymentId = 99,
      totalPaymentPaise = 150000L,
      invoices = invoices
    )

    assertEquals(1, result.allocations.size)
    assertEquals(100000L, result.allocations[0].allocatedAmountPaise)
    assertEquals(PaymentStatus.PAID, result.invoiceStatuses[1])
    assertEquals(50000L, result.unallocatedRemainingPaise) // ₹500 unallocated credit
  }

  @Test
  fun testValidateManualAllocations_rejectsOverAllocation() {
    val invoices = listOf(
      InvoiceAllocationTarget(invoiceId = 1, totalAmountPaise = 100000L, alreadyPaidPaise = 50000L, invoiceDate = 1000L) // unpaid = 50,000 paise
    )

    // Attempting to allocate 60,000 paise when only 50,000 is needed
    assertThrows(IllegalArgumentException::class.java) {
      PaymentAllocationEngine.validateManualAllocations(
        totalPaymentPaise = 100000L,
        invoices = invoices,
        requestedAllocations = mapOf(1L to 60000L)
      )
    }

    // Attempting to allocate more than payment total
    assertThrows(IllegalArgumentException::class.java) {
      PaymentAllocationEngine.validateManualAllocations(
        totalPaymentPaise = 40000L,
        invoices = invoices,
        requestedAllocations = mapOf(1L to 50000L)
      )
    }
  }
}
