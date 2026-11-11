package com.example

import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.model.PartyType
import com.example.domain.model.SubledgerEntry
import com.example.domain.model.SubledgerEntryType
import org.junit.Assert.assertEquals
import org.junit.Test

class SubledgerCalculationEngineTest {

  @Test
  fun testCustomerBalance_calculatesNetReceivable() {
    val entries = listOf(
      SubledgerEntry(
        id = 1,
        partyType = PartyType.CUSTOMER,
        partyId = 101,
        entryType = SubledgerEntryType.OPENING_BALANCE,
        referenceType = "OPENING",
        referenceId = 101,
        debitPaise = 500000L, // ₹5,000 opening
        creditPaise = 0L,
        entryDate = 1000L
      ),
      SubledgerEntry(
        id = 2,
        partyType = PartyType.CUSTOMER,
        partyId = 101,
        entryType = SubledgerEntryType.INVOICE,
        referenceType = "SALE",
        referenceId = 201,
        debitPaise = 300000L, // ₹3,000 sale
        creditPaise = 0L,
        entryDate = 2000L
      ),
      SubledgerEntry(
        id = 3,
        partyType = PartyType.CUSTOMER,
        partyId = 101,
        entryType = SubledgerEntryType.PAYMENT,
        referenceType = "PAYMENT",
        referenceId = 301,
        debitPaise = 0L,
        creditPaise = 400000L, // ₹4,000 paid
        entryDate = 3000L
      )
    )

    // Overall balance: 5000 + 3000 - 4000 = 4000 (400,000 paise)
    val currentBalance = SubledgerCalculationEngine.calculateCustomerBalance(entries)
    assertEquals(400000L, currentBalance)

    // Historical As-Of Date balances:
    // As of date 1500: only entry 1 (500000 paise)
    assertEquals(500000L, SubledgerCalculationEngine.calculateCustomerBalance(entries, asOfDate = 1500L))

    // As of date 2500: entry 1 + entry 2 (800000 paise)
    assertEquals(800000L, SubledgerCalculationEngine.calculateCustomerBalance(entries, asOfDate = 2500L))

    // As of date 3500: all entries (400000 paise)
    assertEquals(400000L, SubledgerCalculationEngine.calculateCustomerBalance(entries, asOfDate = 3500L))
  }

  @Test
  fun testSupplierBalance_calculatesNetPayable() {
    val entries = listOf(
      SubledgerEntry(
        id = 1,
        partyType = PartyType.SUPPLIER,
        partyId = 501,
        entryType = SubledgerEntryType.OPENING_BALANCE,
        referenceType = "OPENING",
        referenceId = 501,
        debitPaise = 0L,
        creditPaise = 1000000L, // ₹10,000 credit (payable)
        entryDate = 1000L
      ),
      SubledgerEntry(
        id = 2,
        partyType = PartyType.SUPPLIER,
        partyId = 501,
        entryType = SubledgerEntryType.PAYMENT,
        referenceType = "PAYMENT",
        referenceId = 601,
        debitPaise = 400000L, // ₹4,000 paid
        creditPaise = 0L,
        entryDate = 2000L
      )
    )

    // Supplier balance: 10,000 - 4,000 = 6,000 (600,000 paise)
    val balance = SubledgerCalculationEngine.calculateSupplierBalance(entries)
    assertEquals(600000L, balance)
  }

  @Test
  fun testCurrencyFormatting() {
    assertEquals("₹5,240.50", SubledgerCalculationEngine.formatPaise(524050L))
    assertEquals("₹0.00", SubledgerCalculationEngine.formatPaise(0L))
    assertEquals("₹100.00", SubledgerCalculationEngine.formatPaise(10000L))
    assertEquals(524050L, SubledgerCalculationEngine.parseRupeesToPaise("5240.50"))
  }
}
