package com.example.domain.engine

import com.example.domain.model.Expense
import com.example.domain.model.PartyType
import com.example.domain.model.Product
import com.example.domain.model.PurchaseInvoice
import com.example.domain.model.SalesInvoice
import com.example.domain.model.SalesInvoiceItem
import com.example.domain.model.SubledgerEntry
import java.util.Calendar

data class ProfitLossSummary(
  val totalSalesRevenuePaise: Long,
  val totalCogsPaise: Long,
  val grossProfitPaise: Long,
  val totalOperatingExpensesPaise: Long,
  val netProfitPaise: Long,
  val invoiceCount: Int,
  val totalPiecesSold: Int
)

data class ProductSalesPerformance(
  val productId: Long,
  val productName: String,
  val totalPiecesSold: Int,
  val totalRevenuePaise: Long,
  val totalCogsPaise: Long,
  val grossProfitPaise: Long
)

data class ShopSalesPerformance(
  val customerId: Long,
  val shopName: String,
  val invoiceCount: Int,
  val totalSalesPaise: Long,
  val paidAmountPaise: Long,
  val outstandingDuesPaise: Long
)

data class SupplierPurchaseSummary(
  val supplierId: Long,
  val supplierName: String,
  val billCount: Int,
  val totalPurchasedPaise: Long,
  val totalPaidPaise: Long,
  val balancePayablePaise: Long
)

data class AccountStatementLine(
  val date: Long,
  val reference: String,
  val entryTypeLabel: String,
  val debitPaise: Long,
  val creditPaise: Long,
  val runningBalancePaise: Long,
  val notes: String
)

data class AccountStatementReport(
  val partyName: String,
  val partyType: PartyType,
  val contactDetails: String,
  val openingBalancePaise: Long,
  val closingBalancePaise: Long,
  val totalDebitsPaise: Long,
  val totalCreditsPaise: Long,
  val lines: List<AccountStatementLine>,
  val periodLabel: String
)

object BusinessReportEngine {

  fun computeStartDate(range: String, nowMillis: Long = System.currentTimeMillis()): Long? {
    if (range == "ALL") return null
    val cal = Calendar.getInstance().apply {
      timeInMillis = nowMillis
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    return when (range) {
      "TODAY" -> cal.timeInMillis
      "WEEK" -> {
        cal.add(Calendar.DAY_OF_YEAR, -6)
        cal.timeInMillis
      }
      "MONTH" -> {
        cal.add(Calendar.DAY_OF_YEAR, -29)
        cal.timeInMillis
      }
      else -> null
    }
  }

  fun formatDateRangeLabel(range: String): String = when (range) {
    "TODAY" -> "Today"
    "WEEK" -> "Last 7 Days"
    "MONTH" -> "Last 30 Days"
    else -> "All Time"
  }

  fun filterSalesByDateRange(
    salesInvoices: List<SalesInvoice>,
    startDate: Long? = null,
    endDate: Long? = null
  ): List<SalesInvoice> {
    return salesInvoices.filter { invoice ->
      (startDate == null || invoice.invoiceDate >= startDate) &&
      (endDate == null || invoice.invoiceDate <= endDate)
    }
  }

  fun filterPurchasesByDateRange(
    purchases: List<PurchaseInvoice>,
    startDate: Long? = null,
    endDate: Long? = null
  ): List<PurchaseInvoice> {
    return purchases.filter { invoice ->
      (startDate == null || invoice.invoiceDate >= startDate) &&
      (endDate == null || invoice.invoiceDate <= endDate)
    }
  }

  /**
   * Calculates comprehensive Profit & Loss using actual batch purchase costs (COGS).
   * Gross Profit = Total Sales Revenue - COGS
   * Net Profit = Gross Profit - Operating Expenses
   *
   * Note: COGS is calculated strictly from line-level batch costs, NEVER estimated from selling price alone.
   */
  fun calculateProfitAndLoss(
    salesInvoices: List<SalesInvoice>,
    salesItems: List<SalesInvoiceItem>,
    batchCostMap: Map<Long, Long>, // batchId -> costPerPiecePaise
    expenses: List<Expense>,
    startDate: Long? = null,
    endDate: Long? = null
  ): ProfitLossSummary {
    val filteredSales = filterSalesByDateRange(salesInvoices, startDate, endDate)

    val filteredInvoiceIds = filteredSales.map { it.id }.toSet()
    val relevantItems = salesItems.filter { it.salesInvoiceId in filteredInvoiceIds }

    val totalSalesRevenue = relevantItems.sumOf { it.lineTotalPaise }

    val totalCogs = relevantItems.sumOf { item ->
      val costPerPiece = batchCostMap[item.batchId] ?: 0L
      item.quantityPieces.toLong() * costPerPiece
    }

    val grossProfit = totalSalesRevenue - totalCogs

    val filteredExpenses = expenses.filter { exp ->
      (startDate == null || exp.expenseDate >= startDate) &&
      (endDate == null || exp.expenseDate <= endDate)
    }
    val totalExpenses = filteredExpenses.sumOf { it.amountPaise }

    val netProfit = grossProfit - totalExpenses
    val totalPieces = relevantItems.sumOf { it.quantityPieces }

    return ProfitLossSummary(
      totalSalesRevenuePaise = totalSalesRevenue,
      totalCogsPaise = totalCogs,
      grossProfitPaise = grossProfit,
      totalOperatingExpensesPaise = totalExpenses,
      netProfitPaise = netProfit,
      invoiceCount = filteredSales.size,
      totalPiecesSold = totalPieces
    )
  }

  /**
   * Calculates Product-wise sales performance including pieces sold, revenue, COGS, and gross margin.
   */
  fun calculateProductSales(
    products: List<Product>,
    salesItems: List<SalesInvoiceItem>,
    batchCostMap: Map<Long, Long>
  ): List<ProductSalesPerformance> {
    val itemsByProduct = salesItems.groupBy { it.productId }

    return products.map { product ->
      val items = itemsByProduct[product.id] ?: emptyList()
      val pieces = items.sumOf { it.quantityPieces }
      val revenue = items.sumOf { it.lineTotalPaise }
      val cogs = items.sumOf { item ->
        val cost = batchCostMap[item.batchId] ?: 0L
        item.quantityPieces.toLong() * cost
      }
      val grossProfit = revenue - cogs

      ProductSalesPerformance(
        productId = product.id,
        productName = product.name,
        totalPiecesSold = pieces,
        totalRevenuePaise = revenue,
        totalCogsPaise = cogs,
        grossProfitPaise = grossProfit
      )
    }.sortedByDescending { it.totalRevenuePaise }
  }

  /**
   * Calculates Shop-wise sales performance and outstanding receivables per customer.
   */
  fun calculateShopSales(
    customers: Map<Long, String>, // customerId -> shopName
    salesInvoices: List<SalesInvoice>
  ): List<ShopSalesPerformance> {
    val invoicesByCustomer = salesInvoices.groupBy { it.customerId }

    return customers.map { (customerId, shopName) ->
      val invoices = invoicesByCustomer[customerId] ?: emptyList()
      val totalSales = invoices.sumOf { it.totalAmountPaise }
      val paidAmount = invoices.sumOf { it.paidAmountPaise }
      val outstanding = (totalSales - paidAmount).coerceAtLeast(0L)

      ShopSalesPerformance(
        customerId = customerId,
        shopName = shopName,
        invoiceCount = invoices.size,
        totalSalesPaise = totalSales,
        paidAmountPaise = paidAmount,
        outstandingDuesPaise = outstanding
      )
    }.sortedByDescending { it.totalSalesPaise }
  }

  /**
   * Calculates Supplier-wise purchase totals and payables across filtered purchase invoices.
   */
  fun calculateSupplierPurchases(
    suppliers: Map<Long, String>,
    purchaseInvoices: List<PurchaseInvoice>
  ): List<SupplierPurchaseSummary> {
    val bySupplier = purchaseInvoices.groupBy { it.supplierId }
    return suppliers.map { (supplierId, supplierName) ->
      val bills = bySupplier[supplierId] ?: emptyList()
      val total = bills.sumOf { it.totalAmountPaise }
      val paid = bills.sumOf { it.paidAmountPaise }
      val payable = (total - paid).coerceAtLeast(0L)
      SupplierPurchaseSummary(
        supplierId = supplierId,
        supplierName = supplierName,
        billCount = bills.size,
        totalPurchasedPaise = total,
        totalPaidPaise = paid,
        balancePayablePaise = payable
      )
    }.sortedByDescending { it.totalPurchasedPaise }
  }

  /**
   * Builds a complete Account Statement with brought-forward Opening Balance,
   * chronological transactions/payments/adjustments within the date window,
   * exact running balances, and Closing Balance.
   */
  fun buildAccountStatement(
    partyId: Long,
    partyName: String,
    partyType: PartyType,
    contactDetails: String,
    entries: List<SubledgerEntry>,
    startDate: Long? = null,
    endDate: Long? = null,
    periodLabel: String = "All Time"
  ): AccountStatementReport {
    val activePartyEntries = entries
      .filter { !it.isReversed && it.partyType == partyType && it.partyId == partyId }
      .sortedWith(compareBy<SubledgerEntry> { it.entryDate }.thenBy { it.id })

    fun delta(entry: SubledgerEntry): Long {
      return if (partyType == PartyType.CUSTOMER) {
        entry.debitPaise - entry.creditPaise
      } else {
        entry.creditPaise - entry.debitPaise
      }
    }

    val openingBalancePaise = if (startDate != null) {
      activePartyEntries
        .filter { it.entryDate < startDate }
        .fold(0L) { acc, entry -> acc + delta(entry) }
    } else {
      0L
    }

    val periodEntries = activePartyEntries.filter { entry ->
      (startDate == null || entry.entryDate >= startDate) &&
      (endDate == null || entry.entryDate <= endDate)
    }

    var runningBalance = openingBalancePaise
    var totalDebits = 0L
    var totalCredits = 0L

    val lines = periodEntries.map { entry ->
      runningBalance += delta(entry)
      totalDebits += entry.debitPaise
      totalCredits += entry.creditPaise
      AccountStatementLine(
        date = entry.entryDate,
        reference = "${entry.referenceType} #${entry.referenceId}",
        entryTypeLabel = entry.entryType.displayName,
        debitPaise = entry.debitPaise,
        creditPaise = entry.creditPaise,
        runningBalancePaise = runningBalance,
        notes = entry.notes
      )
    }

    return AccountStatementReport(
      partyName = partyName,
      partyType = partyType,
      contactDetails = contactDetails,
      openingBalancePaise = openingBalancePaise,
      closingBalancePaise = runningBalance,
      totalDebitsPaise = totalDebits,
      totalCreditsPaise = totalCredits,
      lines = lines,
      periodLabel = periodLabel
    )
  }
}
