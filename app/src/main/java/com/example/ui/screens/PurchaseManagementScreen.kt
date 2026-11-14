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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
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
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentStatus
import com.example.domain.model.Product
import com.example.domain.model.PurchaseInvoice
import com.example.domain.model.Supplier
import com.example.ui.components.CurrencyText
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
import com.example.ui.viewmodel.PurchaseItemDraft
import com.example.ui.viewmodel.PurchaseViewModel

@Composable
fun PurchaseManagementScreen(
  viewModel: PurchaseViewModel,
  modifier: Modifier = Modifier
) {
  val invoices by viewModel.purchases.collectAsState()
  val suppliers by viewModel.suppliers.collectAsState()
  val products by viewModel.products.collectAsState()

  var showNewInvoiceDialog by remember { mutableStateOf(false) }
  var showPaymentDialog by remember { mutableStateOf<Supplier?>(null) }
  var selectedInvoiceForDetail by remember { mutableStateOf<PurchaseInvoice?>(null) }
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  Scaffold(
    containerColor = FigmaBackground,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showNewInvoiceDialog = true },
        containerColor = FigmaTeal,
        contentColor = FigmaWhite,
        modifier = Modifier.testTag("new_purchase_fab")
      ) {
        Icon(Icons.Default.Add, contentDescription = "New Purchase")
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
        .testTag("purchase_screen"),
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
            text = "Purchases & Inward",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = FigmaInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = "Receive batches & payables",
            style = MaterialTheme.typography.bodySmall,
            color = FigmaMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        Button(
          onClick = {
            if (suppliers.isNotEmpty()) {
              showPaymentDialog = suppliers.first()
            } else {
              scope.launch {
                snackbarHostState.showSnackbar("Add a supplier first to record a payment.")
              }
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = FigmaSoftTeal, contentColor = FigmaTeal),
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
          modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag("pay_supplier_header_btn")
        ) {
          Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Pay Supplier",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
          )
        }
      }

      if (invoices.isEmpty()) {
        EmptyStateView(
          icon = Icons.Default.ShoppingCart,
          title = "No Purchases Recorded",
          message = "Tap '+ New Purchase' to record a purchase invoice from a factory supplier."
        )
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          items(invoices, key = { it.id }) { invoice ->
            val supplier = suppliers.find { it.id == invoice.supplierId }
            PurchaseInvoiceCard(
              invoice = invoice,
              supplierName = supplier?.name ?: "Supplier #${invoice.supplierId}",
              onClick = { selectedInvoiceForDetail = invoice }
            )
          }
        }
      }
    }
  }

  // Create Purchase Invoice Dialog
  if (showNewInvoiceDialog) {
    CreatePurchaseInvoiceDialog(
      suppliers = suppliers,
      products = products,
      onDismiss = { showNewInvoiceDialog = false },
      onSubmit = { supplierId, invNum, invDate, dueDate, items, paidAmount, method, notes ->
        viewModel.createPurchaseInvoice(
          supplierId = supplierId,
          invoiceNumber = invNum,
          invoiceDate = invDate,
          dueDate = dueDate,
          items = items,
          paidAmountPaise = paidAmount,
          paymentMethod = method,
          notes = notes,
          onSuccess = { showNewInvoiceDialog = false },
          onError = { /* handled in dialog */ }
        )
      }
    )
  }

  // Record Supplier Payment Dialog
  showPaymentDialog?.let { currentSupplier ->
    RecordSupplierPaymentDialog(
      supplier = currentSupplier,
      allSuppliers = suppliers,
      onDismiss = { showPaymentDialog = null },
      onSubmit = { supId, amount, date, method, notes ->
        viewModel.recordSupplierPayment(
          supplierId = supId,
          amountPaise = amount,
          paymentDate = date,
          paymentMethod = method,
          notes = notes,
          onSuccess = { showPaymentDialog = null },
          onError = { /* handled in dialog */ }
        )
      }
    )
  }
}

@Composable
fun PurchaseInvoiceCard(
  invoice: PurchaseInvoice,
  supplierName: String,
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
            text = "Invoice #${invoice.invoiceNumber}",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = FigmaInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = "$supplierName · ${SubledgerCalculationEngine.formatDate(invoice.invoiceDate)}",
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
          Text(text = "Total Amount", fontSize = 11.sp, color = FigmaMuted)
          Text(
            text = SubledgerCalculationEngine.formatPaise(invoice.totalAmountPaise),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = FigmaInk
          )
        }

        Column(horizontalAlignment = Alignment.End) {
          Text(text = "Balance Due", fontSize = 11.sp, color = FigmaMuted)
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
fun CreatePurchaseInvoiceDialog(
  suppliers: List<Supplier>,
  products: List<Product>,
  onDismiss: () -> Unit,
  onSubmit: (Long, String, Long, Long, List<PurchaseItemDraft>, Long, PaymentMethod?, String) -> Unit
) {
  var selectedSupplierId by remember { mutableStateOf(suppliers.firstOrNull()?.id ?: 0L) }
  var invoiceNumber by remember { mutableStateOf("PUR-${System.currentTimeMillis() % 100000}") }
  var notes by remember { mutableStateOf("") }
  var paidAmountRupees by remember { mutableStateOf("0") }
  var paymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  // Draft items list
  val items = remember { mutableStateListOf<PurchaseItemDraft>() }

  // New item inputs
  var selectedProductId by remember { mutableStateOf(products.firstOrNull()?.id ?: 0L) }
  var batchNumberInput by remember { mutableStateOf("B-${(100..999).random()}") }
  var quantityInput by remember { mutableStateOf("") }
  var costInput by remember { mutableStateOf("") }
  var expiryDaysInput by remember { mutableStateOf("90") }

  val totalAmountPaise = items.sumOf { it.lineTotalPaise }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(text = "New Purchase Invoice", fontWeight = FontWeight.Bold, color = FigmaInk)
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // Supplier Selector
        Text(text = "Select Supplier:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FigmaInk)
        suppliers.forEach { sup ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { selectedSupplierId = sup.id }
              .background(
                if (selectedSupplierId == sup.id) FigmaSoftTeal else FigmaWhite,
                RoundedCornerShape(6.dp)
              )
              .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = sup.name,
              fontWeight = if (selectedSupplierId == sup.id) FontWeight.Bold else FontWeight.Normal,
              color = FigmaInk,
              fontSize = 13.sp
            )
          }
        }

        OutlinedTextField(
          value = invoiceNumber,
          onValueChange = { invoiceNumber = it },
          label = { Text("Invoice Number / Ref") },
          modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider(thickness = 1.dp, color = FigmaLine)
        Text(text = "Add Product Batch Item:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FigmaInk)

        // Product Selector
        products.forEach { prod ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                selectedProductId = prod.id
                costInput = (prod.defaultPurchasePricePaise.toDouble() / 100.0).toString()
              }
              .background(
                if (selectedProductId == prod.id) FigmaSoftTeal else FigmaWhite,
                RoundedCornerShape(6.dp)
              )
              .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "${prod.name} (${prod.sku})",
              fontSize = 12.sp,
              fontWeight = if (selectedProductId == prod.id) FontWeight.Bold else FontWeight.Normal,
              color = FigmaInk
            )
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          OutlinedTextField(
            value = batchNumberInput,
            onValueChange = { batchNumberInput = it },
            label = { Text("Batch #") },
            modifier = Modifier.weight(1f)
          )
          OutlinedTextField(
            value = quantityInput,
            onValueChange = { quantityInput = it },
            label = { Text("Pieces") },
            modifier = Modifier.weight(1f)
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          OutlinedTextField(
            value = costInput,
            onValueChange = { costInput = it },
            label = { Text("Cost (₹/pc)") },
            modifier = Modifier.weight(1f)
          )
          OutlinedTextField(
            value = expiryDaysInput,
            onValueChange = { expiryDaysInput = it },
            label = { Text("Expires in Days") },
            modifier = Modifier.weight(1f)
          )
        }

        Button(
          onClick = {
            val prod = products.find { it.id == selectedProductId }
            val qty = quantityInput.toIntOrNull()
            val costPaise = SubledgerCalculationEngine.parseRupeesToPaise(costInput)
            val expDays = expiryDaysInput.toIntOrNull() ?: 90

            if (prod == null || qty == null || qty <= 0 || costPaise <= 0) {
              errorMessage = "Please enter valid product, pieces, and cost."
              return@Button
            }

            val expiryDate = System.currentTimeMillis() + (expDays * 86400000L)
            items.add(
              PurchaseItemDraft(
                product = prod,
                batchNumber = batchNumberInput,
                expiryDate = expiryDate,
                quantityPieces = qty,
                costPerPiecePaise = costPaise
              )
            )
            quantityInput = ""
            batchNumberInput = "B-${(100..999).random()}"
            errorMessage = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = FigmaNavy, contentColor = FigmaWhite),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text("+ Add Line Item")
        }

        // Current Line Items
        if (items.isNotEmpty()) {
          Text(text = "Invoice Line Items (${items.size}):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = FigmaInk)
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
                  text = "${item.product.name} (Batch: ${item.batchNumber})",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = FigmaInk,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = "${item.quantityPieces} pcs @ ₹${item.costPerPiecePaise / 100.0} = ₹${item.lineTotalPaise / 100.0}",
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
            text = "Total Invoice Cost: ${SubledgerCalculationEngine.formatPaise(totalAmountPaise)}",
            fontWeight = FontWeight.Bold,
            color = FigmaInk,
            fontSize = 14.sp
          )

          OutlinedTextField(
            value = paidAmountRupees,
            onValueChange = { paidAmountRupees = it },
            label = { Text("Initial Payment Made (₹)") },
            modifier = Modifier.fillMaxWidth()
          )
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
          val dueDate = now + (15 * 86400000L) // Default 15 days credit

          onSubmit(
            selectedSupplierId,
            invoiceNumber,
            now,
            dueDate,
            items.toList(),
            paidPaise,
            if (paidPaise > 0) paymentMethod else null,
            notes
          )
        },
        colors = ButtonDefaults.buttonColors(containerColor = FigmaTeal, contentColor = FigmaWhite)
      ) {
        Text("Confirm & Receive Stock")
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
fun RecordSupplierPaymentDialog(
  supplier: Supplier,
  allSuppliers: List<Supplier>,
  onDismiss: () -> Unit,
  onSubmit: (Long, Long, Long, PaymentMethod, String) -> Unit
) {
  var selectedSupId by remember { mutableStateOf(supplier.id) }
  var amountInput by remember { mutableStateOf("") }
  var selectedMethod by remember { mutableStateOf(PaymentMethod.UPI) }
  var notes by remember { mutableStateOf("") }
  var errorText by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Record Supplier Payment", fontWeight = FontWeight.Bold, color = FigmaInk) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = "Paying to: ${supplier.name}", fontWeight = FontWeight.SemiBold, color = FigmaInk)

        OutlinedTextField(
          value = amountInput,
          onValueChange = { amountInput = it },
          label = { Text("Payment Amount (₹)") },
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
          label = { Text("Reference / Notes (e.g. UPI Ref, Cheque No.)") },
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
            errorText = "Please enter a valid payment amount"
            return@Button
          }
          onSubmit(selectedSupId, paise, System.currentTimeMillis(), selectedMethod, notes)
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
