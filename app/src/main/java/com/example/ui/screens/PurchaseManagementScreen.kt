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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.domain.engine.CreditLimitAssessment
import com.example.domain.engine.PaymentAllocationEngine
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

private data class PendingPurchaseCreditOverride(
  val supplierId: Long,
  val invoiceNumber: String,
  val invoiceDate: Long,
  val dueDate: Long,
  val items: List<PurchaseItemDraft>,
  val paidAmountPaise: Long,
  val paymentMethod: PaymentMethod?,
  val notes: String,
  val applyAvailableAdvance: Boolean,
  val assessment: CreditLimitAssessment
)

@Composable
fun PurchaseManagementScreen(
  viewModel: PurchaseViewModel,
  modifier: Modifier = Modifier
) {
  val invoices by viewModel.purchases.collectAsState()
  val suppliers by viewModel.suppliers.collectAsState()
  val products by viewModel.products.collectAsState()
  val supplierBalances by viewModel.supplierBalances.collectAsState()
  val supplierAdvances by viewModel.supplierAdvances.collectAsState()
  val isSubmitting by viewModel.isSubmitting.collectAsState()

  var showNewInvoiceDialog by remember { mutableStateOf(false) }
  var pendingCreditOverride by remember { mutableStateOf<PendingPurchaseCreditOverride?>(null) }
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
      supplierBalances = supplierBalances,
      supplierAdvances = supplierAdvances,
      isSubmitting = isSubmitting,
      onGenerateInvoiceNumber = { viewModel.generateNextInvoiceNumber() },
      onDismiss = { showNewInvoiceDialog = false },
      onSubmit = { supplierId, invNum, invDate, dueDate, items, paidAmount, method, notes, applyAdvance, onError ->
        viewModel.createPurchaseInvoice(
          supplierId = supplierId,
          invoiceNumber = invNum,
          invoiceDate = invDate,
          dueDate = dueDate,
          items = items,
          paidAmountPaise = paidAmount,
          paymentMethod = method,
          notes = notes,
          applyAvailableAdvance = applyAdvance,
          allowCreditLimitOverride = false,
          onCreditLimitExceeded = { assessment ->
            pendingCreditOverride = PendingPurchaseCreditOverride(
              supplierId = supplierId,
              invoiceNumber = invNum,
              invoiceDate = invDate,
              dueDate = dueDate,
              items = items,
              paidAmountPaise = paidAmount,
              paymentMethod = method,
              notes = notes,
              applyAvailableAdvance = applyAdvance,
              assessment = assessment
            )
          },
          onSuccess = { showNewInvoiceDialog = false },
          onError = onError
        )
      }
    )
  }

  pendingCreditOverride?.let { pending ->
    val supName = suppliers.find { it.id == pending.supplierId }?.name ?: "Supplier #${pending.supplierId}"
    AlertDialog(
      onDismissRequest = { pendingCreditOverride = null },
      title = { Text("Supplier Credit Limit Exceeded", fontWeight = FontWeight.Bold, color = FigmaRed) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Supplier: $supName", fontWeight = FontWeight.SemiBold, color = FigmaInk)
          Text("Credit Limit: ${SubledgerCalculationEngine.formatPaise(pending.assessment.creditLimitPaise)}", fontSize = 13.sp, color = FigmaInk)
          Text("Current Payable: ${SubledgerCalculationEngine.formatPaise(pending.assessment.currentBalancePaise)}", fontSize = 13.sp, color = FigmaMuted)
          Text("Projected Payable: ${SubledgerCalculationEngine.formatPaise(pending.assessment.projectedBalancePaise)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FigmaRed)
          Text(
            "Exceeded by ${SubledgerCalculationEngine.formatPaise(pending.assessment.exceededByPaise)}. Override supplier credit limit and confirm purchase?",
            fontSize = 12.sp,
            color = FigmaMuted
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.createPurchaseInvoice(
              supplierId = pending.supplierId,
              invoiceNumber = pending.invoiceNumber,
              invoiceDate = pending.invoiceDate,
              dueDate = pending.dueDate,
              items = pending.items,
              paidAmountPaise = pending.paidAmountPaise,
              paymentMethod = pending.paymentMethod,
              notes = pending.notes,
              applyAvailableAdvance = pending.applyAvailableAdvance,
              allowCreditLimitOverride = true,
              onSuccess = {
                pendingCreditOverride = null
                showNewInvoiceDialog = false
              },
              onError = { err ->
                pendingCreditOverride = null
                scope.launch { snackbarHostState.showSnackbar(err) }
              }
            )
          },
          enabled = !isSubmitting,
          colors = ButtonDefaults.buttonColors(containerColor = FigmaRed, contentColor = FigmaWhite),
          modifier = Modifier.testTag("confirm_purchase_credit_override_btn")
        ) {
          Text("Override & Confirm")
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingCreditOverride = null }) {
          Text("Cancel", color = FigmaMuted)
        }
      }
    )
  }

  // Record Supplier Payment Dialog
  showPaymentDialog?.let { currentSupplier ->
    RecordSupplierPaymentDialog(
      supplier = currentSupplier,
      allSuppliers = suppliers,
      supplierBalances = supplierBalances,
      supplierAdvances = supplierAdvances,
      isSubmitting = isSubmitting,
      onDismiss = { showPaymentDialog = null },
      onApplyAdvance = { supId, onError ->
        viewModel.applySupplierAdvance(
          supplierId = supId,
          onSuccess = { applied ->
            showPaymentDialog = null
            scope.launch {
              snackbarHostState.showSnackbar("Applied advance of ${SubledgerCalculationEngine.formatPaise(applied)} to unpaid bills.")
            }
          },
          onError = onError
        )
      },
      onSubmit = { supId, amount, date, method, notes, onError ->
        viewModel.recordSupplierPayment(
          supplierId = supId,
          amountPaise = amount,
          paymentDate = date,
          paymentMethod = method,
          notes = notes,
          onSuccess = { showPaymentDialog = null },
          onError = onError
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
  supplierBalances: Map<Long, Long> = emptyMap(),
  supplierAdvances: Map<Long, Long> = emptyMap(),
  isSubmitting: Boolean = false,
  onGenerateInvoiceNumber: suspend () -> String = { "PUR-${System.currentTimeMillis() % 100000}" },
  onDismiss: () -> Unit,
  onSubmit: (Long, String, Long, Long, List<PurchaseItemDraft>, Long, PaymentMethod?, String, Boolean, (String) -> Unit) -> Unit
) {
  var selectedSupplierId by remember { mutableStateOf(suppliers.firstOrNull()?.id ?: 0L) }
  var invoiceNumber by remember { mutableStateOf("") }
  var notes by remember { mutableStateOf("") }
  var paidAmountRupees by remember { mutableStateOf("0") }
  var paymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
  var applyAvailableAdvance by remember { mutableStateOf(true) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    if (invoiceNumber.isBlank()) {
      invoiceNumber = onGenerateInvoiceNumber()
    }
  }

  // Draft items list
  val items = remember { mutableStateListOf<PurchaseItemDraft>() }

  // New item inputs
  var selectedProductId by remember { mutableStateOf(products.firstOrNull()?.id ?: 0L) }
  var batchNumberInput by remember { mutableStateOf("B-${(100..999).random()}") }
  var quantityInput by remember { mutableStateOf("") }
  var costInput by remember {
    mutableStateOf(
      products.firstOrNull()?.let { SubledgerCalculationEngine.formatPaiseForInput(it.defaultPurchasePricePaise) } ?: ""
    )
  }
  var expiryDaysInput by remember { mutableStateOf("90") }

  val selectedSupplier = suppliers.find { it.id == selectedSupplierId }
  val selectedBalancePaise = supplierBalances[selectedSupplierId] ?: 0L
  val selectedAdvancePaise = supplierAdvances[selectedSupplierId] ?: 0L
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

        selectedSupplier?.let { sup ->
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = FigmaSoftTeal,
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, FigmaLine, RoundedCornerShape(8.dp))
          ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              val termsText = "Terms: ${sup.creditTermsDays} days"
              val limitText = if (sup.creditLimitPaise > 0L) {
                "Limit: ${SubledgerCalculationEngine.formatPaise(sup.creditLimitPaise)}"
              } else {
                "Limit: Unlimited"
              }
              Text(text = "$termsText · $limitText", fontSize = 11.sp, color = FigmaNavy, fontWeight = FontWeight.SemiBold)
              Text(
                text = "Current Payable: ${SubledgerCalculationEngine.formatPaise(selectedBalancePaise)}" +
                  if (selectedAdvancePaise > 0L) " · Advance Paid: ${SubledgerCalculationEngine.formatPaise(selectedAdvancePaise)}" else "",
                fontSize = 11.sp,
                color = FigmaInk
              )
            }
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
                costInput = SubledgerCalculationEngine.formatPaiseForInput(prod.defaultPurchasePricePaise)
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
            val qty = quantityInput.trim().toIntOrNull()
            val costPaise = SubledgerCalculationEngine.parseRupeesToPaiseOrNull(costInput)
            val expDays = expiryDaysInput.trim().toIntOrNull()

            if (prod == null || qty == null || qty <= 0 || costPaise == null || costPaise <= 0L || batchNumberInput.isBlank()) {
              errorMessage = "Please enter valid product, batch #, positive pieces, and cost."
              return@Button
            }
            if (expDays == null || expDays <= 0) {
              errorMessage = "Expiry days must be a positive number."
              return@Button
            }

            val expiryDate = System.currentTimeMillis() + (expDays * 86400000L)
            items.add(
              PurchaseItemDraft(
                product = prod,
                batchNumber = batchNumberInput.trim(),
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
                  text = "${item.quantityPieces} pcs @ ${SubledgerCalculationEngine.formatPaise(item.costPerPiecePaise)} = ${SubledgerCalculationEngine.formatPaise(item.lineTotalPaise)}",
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

          if (selectedAdvancePaise > 0L) {
            val applicableAdv = kotlin.math.min(selectedAdvancePaise, totalAmountPaise)
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { applyAvailableAdvance = !applyAvailableAdvance },
              verticalAlignment = Alignment.CenterVertically
            ) {
              Checkbox(
                checked = applyAvailableAdvance,
                onCheckedChange = { applyAvailableAdvance = it },
                colors = CheckboxDefaults.colors(checkedColor = FigmaTeal)
              )
              Text(
                text = "Apply available advance (${SubledgerCalculationEngine.formatPaise(applicableAdv)})",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = FigmaTeal
              )
            }
          }

          val advAppliedPreview = if (applyAvailableAdvance) kotlin.math.min(selectedAdvancePaise, totalAmountPaise) else 0L
          val netAfterAdvancePreview = (totalAmountPaise - advAppliedPreview).coerceAtLeast(0L)
          val parsedCashPaise = SubledgerCalculationEngine.parseRupeesToPaiseOrNull(paidAmountRupees, allowNegative = false, allowEmptyAsZero = true) ?: 0L
          if (parsedCashPaise > netAfterAdvancePreview && totalAmountPaise > 0L) {
            val excessAdv = parsedCashPaise - netAfterAdvancePreview
            Text(
              text = "Excess payment of ${SubledgerCalculationEngine.formatPaise(excessAdv)} will be saved as Supplier Advance.",
              fontSize = 11.sp,
              color = FigmaTeal,
              fontWeight = FontWeight.Medium
            )
          }
        }

        errorMessage?.let { err ->
          Surface(
            color = FigmaRed.copy(alpha = 0.08f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, FigmaRed.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
              .testTag("purchase_dialog_error")
          ) {
            Text(
              text = err,
              color = FigmaRed,
              fontSize = 12.sp,
              modifier = Modifier.padding(8.dp)
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (selectedSupplierId == 0L) {
            errorMessage = "Please select a supplier"
            return@Button
          }
          if (invoiceNumber.isBlank()) {
            errorMessage = "Invoice number cannot be empty"
            return@Button
          }
          if (items.isEmpty()) {
            errorMessage = "Please add at least one line item"
            return@Button
          }
          val paidPaise = SubledgerCalculationEngine.parseRupeesToPaiseOrNull(
            paidAmountRupees,
            allowNegative = false,
            allowEmptyAsZero = true
          )
          if (paidPaise == null) {
            errorMessage = "Please enter a valid initial payment amount"
            return@Button
          }
          val now = System.currentTimeMillis()
          val termsDays = selectedSupplier?.creditTermsDays ?: 15
          val dueDate = PaymentAllocationEngine.calculateDueDate(now, termsDays)

          errorMessage = null
          onSubmit(
            selectedSupplierId,
            invoiceNumber.trim(),
            now,
            dueDate,
            items.toList(),
            paidPaise,
            if (paidPaise > 0) paymentMethod else null,
            notes,
            applyAvailableAdvance
          ) { err ->
            errorMessage = err
          }
        },
        enabled = !isSubmitting,
        colors = ButtonDefaults.buttonColors(containerColor = FigmaTeal, contentColor = FigmaWhite)
      ) {
        Text(if (isSubmitting) "Saving..." else "Confirm & Receive Stock")
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
  supplierBalances: Map<Long, Long> = emptyMap(),
  supplierAdvances: Map<Long, Long> = emptyMap(),
  isSubmitting: Boolean = false,
  onDismiss: () -> Unit,
  onApplyAdvance: (Long, (String) -> Unit) -> Unit = { _, _ -> },
  onSubmit: (Long, Long, Long, PaymentMethod, String, (String) -> Unit) -> Unit
) {
  var selectedSupId by remember { mutableStateOf(supplier.id) }
  var amountInput by remember { mutableStateOf("") }
  var selectedMethod by remember { mutableStateOf(PaymentMethod.UPI) }
  var notes by remember { mutableStateOf("") }
  var errorText by remember { mutableStateOf<String?>(null) }

  val currentSup = allSuppliers.find { it.id == selectedSupId } ?: supplier
  val currentDue = supplierBalances[selectedSupId] ?: 0L
  val currentAdvance = supplierAdvances[selectedSupId] ?: 0L

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Record Supplier Payment", fontWeight = FontWeight.Bold, color = FigmaInk) },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
      ) {
        Text(text = "Select Supplier:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FigmaInk)
        allSuppliers.forEach { sup ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { selectedSupId = sup.id }
              .background(if (selectedSupId == sup.id) FigmaSoftTeal else FigmaWhite, RoundedCornerShape(6.dp))
              .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = sup.name,
              fontWeight = if (selectedSupId == sup.id) FontWeight.Bold else FontWeight.Normal,
              fontSize = 13.sp,
              color = FigmaInk
            )
            val bal = supplierBalances[sup.id] ?: 0L
            Text(
              text = if (bal < 0L) "Adv ${SubledgerCalculationEngine.formatPaise(kotlin.math.abs(bal))}" else SubledgerCalculationEngine.formatPaise(bal),
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              color = if (bal > 0L) FigmaRed else FigmaTeal
            )
          }
        }

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = FigmaSoftTeal,
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FigmaLine, RoundedCornerShape(8.dp))
        ) {
          Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = "${currentSup.name} · Net Payable: ${SubledgerCalculationEngine.formatPaise(currentDue.coerceAtLeast(0L))}",
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              color = FigmaInk
            )
            if (currentAdvance > 0L) {
              Text(
                text = "Available Advance Paid: ${SubledgerCalculationEngine.formatPaise(currentAdvance)}",
                fontSize = 11.sp,
                color = FigmaTeal,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }

        if (currentAdvance > 0L && currentDue > 0L) {
          OutlinedButton(
            onClick = { onApplyAdvance(selectedSupId) { errorText = it } },
            enabled = !isSubmitting,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = "Apply Existing Advance (${SubledgerCalculationEngine.formatPaise(currentAdvance)})",
              fontSize = 12.sp,
              color = FigmaTeal,
              fontWeight = FontWeight.SemiBold
            )
          }
        }

        OutlinedTextField(
          value = amountInput,
          onValueChange = { amountInput = it },
          label = { Text("Payment Amount (₹)") },
          modifier = Modifier.fillMaxWidth()
        )

        val parsedAmt = SubledgerCalculationEngine.parseRupeesToPaiseOrNull(amountInput, allowNegative = false, allowEmptyAsZero = true) ?: 0L
        if (parsedAmt > currentDue.coerceAtLeast(0L) && parsedAmt > 0L) {
          val excess = parsedAmt - currentDue.coerceAtLeast(0L)
          Text(
            text = "Excess ${SubledgerCalculationEngine.formatPaise(excess)} above payables will be recorded as Supplier Advance.",
            fontSize = 11.sp,
            color = FigmaTeal,
            fontWeight = FontWeight.Medium
          )
        }

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

        errorText?.let { err ->
          Surface(
            color = FigmaRed.copy(alpha = 0.08f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, FigmaRed.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
          ) {
            Text(text = err, color = FigmaRed, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val paise = SubledgerCalculationEngine.parseRupeesToPaiseOrNull(amountInput)
          if (paise == null || paise <= 0L) {
            errorText = "Please enter a valid positive payment amount"
            return@Button
          }
          errorText = null
          onSubmit(selectedSupId, paise, System.currentTimeMillis(), selectedMethod, notes) { err ->
            errorText = err
          }
        },
        enabled = !isSubmitting,
        colors = ButtonDefaults.buttonColors(containerColor = FigmaTeal, contentColor = FigmaWhite)
      ) {
        Text(if (isSubmitting) "Saving..." else "Record Payment")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel", color = FigmaMuted) }
    }
  )
}
