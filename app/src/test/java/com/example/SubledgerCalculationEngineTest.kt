package com.example

import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.model.PartyType
import com.example.domain.model.SubledgerEntry
import com.example.domain.model.SubledgerEntryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
    assertEquals("₹1,00,000.00", SubledgerCalculationEngine.formatPaise(10000000L))
    assertEquals("₹10,00,000.00", SubledgerCalculationEngine.formatPaise(100000000L))
    assertEquals("₹1,23,45,678.90", SubledgerCalculationEngine.formatPaise(1234567890L))
    assertEquals("- ₹5,240.50", SubledgerCalculationEngine.formatPaise(-524050L))
    assertEquals("1,00,000.00", SubledgerCalculationEngine.formatPaise(10000000L, includeSymbol = false))
    assertEquals(524050L, SubledgerCalculationEngine.parseRupeesToPaise("5240.50"))
  }

  @Test
  fun testStrictBigDecimalMoneyParsingAndInputFormatting() {
    assertEquals(1250L, SubledgerCalculationEngine.parseRupeesToPaiseOrNull("12.50"))
    assertEquals(1250L, SubledgerCalculationEngine.parseRupeesToPaiseOrNull("12.5"))
    assertEquals(10000000L, SubledgerCalculationEngine.parseRupeesToPaiseOrNull("₹1,00,000.00"))
    assertEquals(0L, SubledgerCalculationEngine.parseRupeesToPaiseOrNull("", allowEmptyAsZero = true))
    assertNull(SubledgerCalculationEngine.parseRupeesToPaiseOrNull("", allowEmptyAsZero = false))
    assertNull(SubledgerCalculationEngine.parseRupeesToPaiseOrNull("abc"))
    assertNull(SubledgerCalculationEngine.parseRupeesToPaiseOrNull("12.345"))
    assertNull(SubledgerCalculationEngine.parseRupeesToPaiseOrNull("-50", allowNegative = false))
    assertEquals(-5000L, SubledgerCalculationEngine.parseRupeesToPaiseOrNull("-50.00", allowNegative = true))
    assertEquals("12.50", SubledgerCalculationEngine.formatPaiseForInput(1250L))
    assertEquals("100", SubledgerCalculationEngine.formatPaiseForInput(10000L))
    assertEquals("-25.50", SubledgerCalculationEngine.formatPaiseForInput(-2550L))
    assertThrows(IllegalArgumentException::class.java) {
      SubledgerCalculationEngine.parseRupeesToPaise("invalid_amount")
    }
  }

  @Test
  fun testFormattersAreThreadSafeUnderConcurrency() {
    val executor = Executors.newFixedThreadPool(8)
    val futures = (1..200).map { i ->
      executor.submit<Pair<String, String>> {
        val formattedMoney = SubledgerCalculationEngine.formatPaise(10000000L + i)
        val formattedDate = SubledgerCalculationEngine.formatDate(1700000000000L + i * 86400000L)
        formattedMoney to formattedDate
      }
    }
    executor.shutdown()
    executor.awaitTermination(10, TimeUnit.SECONDS)
    futures.forEachIndexed { idx, future ->
      val (money, date) = future.get()
      assertEquals(SubledgerCalculationEngine.formatPaise(10000000L + (idx + 1)), money)
      assertEquals(SubledgerCalculationEngine.formatDate(1700000000000L + (idx + 1) * 86400000L), date)
    }
  }
}
