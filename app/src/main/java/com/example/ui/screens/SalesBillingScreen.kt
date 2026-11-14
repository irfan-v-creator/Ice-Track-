package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import kotlinx.coroutines.launch
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.model.Customer
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentStatus
import com.example.domain.model.Product
import com.example.domain.model.SalesInvoice
import com.example.ui.components.EmptyStateView
import com.example.ui.theme.FigmaAmber
import com.example.ui.theme.FigmaBackground
import com.example.ui.theme.FigmaInk
import com.example.ui.theme.FigmaLine
import com.example.ui.theme.FigmaMuted
import com.example.ui.theme.FigmaNavy
import com.example.ui.theme.FigmaRed
import com.example.ui.theme.FigmaSoftTeal
import com.example.ui.theme.FigmaTeal
import com.example.ui.theme.FigmaWhite
import com.example.ui.viewmodel.SalesItemDraft
import com.example.ui.viewmodel.SalesViewModel

@Composable
fun SalesBillingScreen(
  viewModel: SalesViewModel,
  modifier: Modifier = Modifier
) {
  val salesInvoices by viewModel.salesInvoices.collectAsState()
  val customers by viewModel.customers.collectAsState()
  val products by viewModel.products.collectAsState()

  var showNewSaleDialog by remember { mutableStateOf(false) }
  var showCustomerPaymentDialog by remember { mutableStateOf<Customer?>(null) }
  var selectedInvoiceDetail by remember { mutableStateOf<SalesInvoice?>(null) }
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  Scaffold(
    containerColor = FigmaBackground,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showNewSaleDialog = true },
        containerColor = FigmaTeal,
        contentColor = FigmaWhite,
        modifier = Modifier.testTag("new_sale_fab")
      ) {
        Icon(Icons.Default.Add, contentDescription = "New Sale")
      }
    },
    modifier = modifier.fillMaxSize()
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(FigmaBackground)
        .padding(padding)
        .padding(horizontal = 24.dp, vertical = 12.dp)
        .testTag("sales_screen"),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Header Section
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(
          modifier = Modifier
            .weight(1f)
            .padding(end = 12.dp)
        ) {
          Text(
            text = "Sales & Billing",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = FigmaInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = "Retail invoices & FEFO issuing",
            style = MaterialTheme.typography.bodySmall,
            color = FigmaMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        Button(
          onClick = {
            if (customers.isNotEmpty()) {
              showCustomerPaymentDialog = customers.first()
            } else {
              scope.launch {
                snackbarHostState.showSnackbar("Add a customer shop first to receive a payment.")
              }
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = FigmaSoftTeal, contentColor = FigmaTeal),
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
          modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag("receive_payment_header_btn")
        ) {
          Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Receive Payment",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
          )
        }
      }

      if (salesInvoices.isEmpty()) {
        EmptyStateView(
          icon = Icons.Default.PointOfSale,
          title = "No Sales Recorded",
          message = "Tap '+ New Sale' to create an invoice for a retail shop. Stock will be automatically issued via FEFO."
        )
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          items(salesInvoices, key = { it.id }) { invoice ->
            val customer = customers.find { it.id == invoice.customerId }
            SalesInvoiceCard(
              invoice = invoice,
              customerShopName = customer?.shopName ?: "Shop #${invoice.customerId}",
              onClick = { selectedInvoiceDetail = invoice }
            )
          }
        }
      }
    }
  }

  // New Sale Dialog
  if (showNewSaleDialog) {
    CreateSalesInvoiceDialog(
      customers = customers,
      products = products,
      onGetPrice = { custId, prodId, defPrice ->
        viewModel.getSellingPriceForShop(custId, prodId, defPrice)
      },
      onDismiss = { showNewSaleDialog = false },
      onSubmit = { customerId, invNum, invDate, dueDate, items, paidAmount, method, notes, allowExpired, onError ->
        viewModel.createSalesInvoice(
          customerId = customerId,
          invoiceNumber = invNum,
          invoiceDate = invDate,
          dueDate = dueDate,
          items = items,
          paidAmountPaise = paidAmount,
          paymentMethod = method,
          notes = notes,
          allowExpired = allowExpired,
          onSuccess = { showNewSaleDialog = false },
          onError = onError
        )
      }
    )
  }

  // Receive Customer Payment Dialog
  showCustomerPaymentDialog?.let { currentCustomer ->
    ReceiveCustomerPaymentDialog(
      customer = currentCustomer,
      allCustomers = customers,
      onDismiss = { showCustomerPaymentDialog = null },
      onSubmit = { custId, amount, date, method, notes, onError ->
        viewModel.recordCustomerPayment(
          customerId = custId,
          amountPaise = amount,
          paymentDate = date,
          paymentMethod = method,
          notes = notes,
          onSuccess = { showCustomerPaymentDialog = null },
          onError = onError
        )
      }
    )
  }
}

@Composable
fun SalesInvoiceCard(
  invoice: SalesInvoice,
  customerShopName: String,
  onClick: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = FigmaWhite),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, FigmaLine, RoundedCornerShape(12.dp))
      .clickable(onClick = onClick)
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(
          modifier = Modifier
            .weight(1f)
            .padding(end = 8.dp)
        ) {
          Text(
            text = "Bill #${invoice.invoiceNumber}",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = FigmaInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = "$customerShopName · ${SubledgerCalculationEngine.formatDate(invoice.invoiceDate)}",
            fontSize = 12.sp,
            color = FigmaMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        Surface(
          shape = RoundedCornerShape(6.dp),
          color = when (invoice.paymentStatus) {
            PaymentStatus.PAID -> FigmaSoftTeal
            PaymentStatus.PARTIALLY_PAID -> FigmaAmber.copy(alpha = 0.15f)
            PaymentStatus.UNPAID -> FigmaRed.copy(alpha = 0.12f)
          }
        ) {
          Text(
            text = invoice.paymentStatus.displayName,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = when (invoice.paymentStatus) {
              PaymentStatus.PAID -> FigmaTeal
              PaymentStatus.PARTIALLY_PAID -> FigmaAmber
              PaymentStatus.UNPAID -> FigmaRed
            }
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))
      HorizontalDivider(thickness = 0.5.dp, color = FigmaLine)
      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column {
          Text(text = "Total Bill", fontSize = 11.sp, color = FigmaMuted)
          Text(
            text = SubledgerCalculationEngine.formatPaise(invoice.totalAmountPaise),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = FigmaInk
          )
        }

        Column(horizontalAlignment = Alignment.End) {
          Text(text = "Outstanding Due", fontSize = 11.sp, color = FigmaMuted)
          Text(
            text = SubledgerCalculationEngine.formatPaise(invoice.balanceDuePaise),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = if (invoice.balanceDuePaise > 0) FigmaRed else FigmaTeal
          )
        }
      }
    }
  }
}

@Composable
fun CreateSalesInvoiceDialog(
  customers: List<Customer>,
  products: List<Product>,
  onGetPrice: suspend (Long, Long, Long) -> Long,
  onDismiss: () -> Unit,
  onSubmit: (Long, String, Long, Long, List<SalesItemDraft>, Long, PaymentMethod?, String, Boolean, (String) -> Unit) -> Unit
) {
  val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
  var selectedCustomerId by remember { mutableStateOf(customers.firstOrNull()?.id ?: 0L) }
  var invoiceNumber by remember { mutableStateOf("INV-${System.currentTimeMillis() % 100000}") }
  var notes by remember { mutableStateOf("") }
  var paidAmountRupees by remember { mutableStateOf("0") }
  var paymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  val items = remember { mutableStateListOf<SalesItemDraft>() }

  var selectedProductId by remember { mutableStateOf(products.firstOrNull()?.id ?: 0L) }
  var quantityInput by remember { mutableStateOf("") }
  var priceInput by remember { mutableStateOf("") }

  // Auto-fill price function
  fun autoFillPrice(custId: Long, prodId: Long) {
    val prod = products.find { it.id == prodId } ?: return
    coroutineScope.launch {
      val pricePaise = onGetPrice(custId, prodId, prod.defaultSellingPricePaise)
      priceInput = (pricePaise.toDouble() / 100.0).toString()
    }
  }

  // Initial auto-fill for default selections
  androidx.compose.runtime.LaunchedEffect(selectedCustomerId, selectedProductId) {
    if (priceInput.isBlank() && selectedCustomerId != 0L && selectedProductId != 0L) {
      autoFillPrice(selectedCustomerId, selectedProductId)
    }
  }

  val totalAmountPaise = items.sumOf { it.lineTotalPaise }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = "New Sales Invoice", fontWeight = FontWeight.Bold, color = FigmaInk) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text(text = "Select Retail Shop:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FigmaInk)
        customers.forEach { cust ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                selectedCustomerId = cust.id
                autoFillPrice(cust.id, selectedProductId)
              }
              .background(
                if (selectedCustomerId == cust.id) FigmaSoftTeal else FigmaWhite,
                RoundedCornerShape(6.dp)
              )
              .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "${cust.shopName} (${cust.ownerName})",
              fontWeight = if (selectedCustomerId == cust.id) FontWeight.Bold else FontWeight.Normal,
              color = FigmaInk,
              fontSize = 13.sp
            )
          }
        }

        OutlinedTextField(
          value = invoiceNumber,
          onValueChange = { invoiceNumber = it },
          label = { Text("Invoice Number / Bill No.") },
          modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider(thickness = 1.dp, color = FigmaLine)
        Text(text = "Add Item to Bill:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FigmaInk)

        products.forEach { prod ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                selectedProductId = prod.id
                autoFillPrice(selectedCustomerId, prod.id)
              }
              .background(
                if (selectedProductId == prod.id) FigmaSoftTeal else FigmaWhite,
                RoundedCornerShape(6.dp)
              )
              .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "${prod.name} (Default: ₹${prod.defaultSellingPricePaise / 100.0})",
              fontSize = 12.sp,
              fontWeight = if (selectedProductId == prod.id) FontWeight.Bold else FontWeight.Normal,
              color = FigmaInk
            )
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          OutlinedTextField(
            value = quantityInput,
            onValueChange = { quantityInput = it },
            label = { Text("Pieces") },
            modifier = Modifier.weight(1f)
          )
          OutlinedTextField(
            value = priceInput,
            onValueChange = { priceInput = it },
            label = { Text("Price (₹/pc)") },
            modifier = Modifier.weight(1f)
          )
        }

        Button(
          onClick = {
            val prod = products.find { it.id == selectedProductId }
            val qty = quantityInput.toIntOrNull()
            val pricePaise = SubledgerCalculationEngine.parseRupeesToPaise(priceInput)

            if (prod == null || qty == null || qty <= 0 || pricePaise <= 0) {
              errorMessage = "Please enter valid product, quantity, and price."
              return@Button
            }

            items.add(
              SalesItemDraft(
                product = prod,
                quantityPieces = qty,
                unitPricePaise = pricePaise
              )
            )
            quantityInput = ""
            errorMessage = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = FigmaNavy, contentColor = FigmaWhite),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text("+ Add Line Item")
        }

        if (items.isNotEmpty()) {
          Text(text = "Items in Invoice (${items.size}):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = FigmaInk)
          items.forEachIndexed { idx, item ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .background(FigmaSoftTeal, RoundedCornerShape(6.dp))
                .padding(6.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(
                modifier = Modifier
                  .weight(1f)
                  .padding(end = 8.dp)
              ) {
                Text(
                  text = item.product.name,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = FigmaInk,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = "${item.quantityPieces} pcs @ ₹${item.unitPricePaise / 100.0} = ₹${item.lineTotalPaise / 100.0}",
                  fontSize = 10.sp,
                  color = FigmaMuted,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              IconButton(onClick = { items.removeAt(idx) }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = FigmaRed, modifier = Modifier.size(16.dp))
              }
            }
          }

          Text(
            text = "Total Bill Amount: ${SubledgerCalculationEngine.formatPaise(totalAmountPaise)}",
            fontWeight = FontWeight.Bold,
            color = FigmaInk,
            fontSize = 14.sp
          )

          OutlinedTextField(
            value = paidAmountRupees,
            onValueChange = { paidAmountRupees = it },
            label = { Text("Payment Received Now (₹)") },
            modifier = Modifier.fillMaxWidth()
          )

          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PaymentMethod.values().take(3).forEach { method ->
              Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (paymentMethod == method) FigmaTeal else FigmaWhite,
                modifier = Modifier
                  .border(1.dp, if (paymentMethod == method) FigmaTeal else FigmaLine, RoundedCornerShape(6.dp))
                  .clickable { paymentMethod = method }
                  .padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Text(
                  text = method.displayName,
                  fontSize = 11.sp,
                  color = if (paymentMethod == method) FigmaWhite else FigmaInk
                )
              }
            }
          }
        }

        errorMessage?.let {
          Text(text = it, color = FigmaRed, fontSize = 12.sp)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (items.isEmpty()) {
            errorMessage = "Please add at least one line item"
            return@Button
          }
          val paidPaise = SubledgerCalculationEngine.parseRupeesToPaise(paidAmountRupees)
          val now = System.currentTimeMillis()
          val dueDate = now + (7 * 86400000L) // Default 7 days credit

          onSubmit(
            selectedCustomerId,
            invoiceNumber,
            now,
            dueDate,
            items.toList(),
            paidPaise,
            if (paidPaise > 0) paymentMethod else null,
            notes,
            false // allowExpired = false strictly enforces FEFO non-expired stock
          ) { err ->
            errorMessage = err
          }
        },
        colors = ButtonDefaults.buttonColors(containerColor = FigmaTeal, contentColor = FigmaWhite)
      ) {
        Text("Generate Bill & Dispatch")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", color = FigmaMuted)
      }
    }
  )
}

@Composable
fun ReceiveCustomerPaymentDialog(
  customer: Customer,
  allCustomers: List<Customer>,
  onDismiss: () -> Unit,
  onSubmit: (Long, Long, Long, PaymentMethod, String, (String) -> Unit) -> Unit
) {
  var selectedCustId by remember { mutableStateOf(customer.id) }
  var amountInput by remember { mutableStateOf("") }
  var selectedMethod by remember { mutableStateOf(PaymentMethod.UPI) }
  var notes by remember { mutableStateOf("") }
  var errorText by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Receive Customer Payment", fontWeight = FontWeight.Bold, color = FigmaInk) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = "Customer: ${customer.shopName} (${customer.ownerName})", fontWeight = FontWeight.SemiBold, color = FigmaInk)

        OutlinedTextField(
          value = amountInput,
          onValueChange = { amountInput = it },
          label = { Text("Amount Received (₹)") },
          modifier = Modifier.fillMaxWidth()
        )

        Text(text = "Payment Method:", fontSize = 12.sp, color = FigmaMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          PaymentMethod.values().take(3).forEach { method ->
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = if (selectedMethod == method) FigmaTeal else FigmaWhite,
              modifier = Modifier
                .border(1.dp, if (selectedMethod == method) FigmaTeal else FigmaLine, RoundedCornerShape(8.dp))
                .clickable { selectedMethod = method }
                .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
              Text(
                text = method.displayName,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selectedMethod == method) FigmaWhite else FigmaInk
              )
            }
          }
        }

        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Payment Ref / Notes (e.g. GPay UPI Ref)") },
          modifier = Modifier.fillMaxWidth()
        )

        errorText?.let { Text(text = it, color = FigmaRed, fontSize = 12.sp) }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val paise = SubledgerCalculationEngine.parseRupeesToPaise(amountInput)
          if (paise <= 0) {
            errorText = "Please enter a valid received amount"
            return@Button
          }
          onSubmit(selectedCustId, paise, System.currentTimeMillis(), selectedMethod, notes) { err ->
            errorText = err
          }
        },
        colors = ButtonDefaults.buttonColors(containerColor = FigmaTeal, contentColor = FigmaWhite)
      ) {
        Text("Record Payment")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel", color = FigmaMuted) }
    }
  )
}
