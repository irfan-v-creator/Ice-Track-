package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.example.domain.engine.CreditLimitAssessment
import com.example.domain.engine.PaymentAllocationEngine
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

private data class PendingSaleCreditOverride(
  val customerId: Long,
  val invoiceNumber: String,
  val invoiceDate: Long,
  val dueDate: Long,
  val items: List<SalesItemDraft>,
  val paidAmountPaise: Long,
  val paymentMethod: PaymentMethod?,
  val notes: String,
  val allowExpired: Boolean,
  val applyAvailableAdvance: Boolean,
  val assessment: CreditLimitAssessment
)

@Composable
fun SalesBillingScreen(
  viewModel: SalesViewModel,
  modifier: Modifier = Modifier
) {
  val salesInvoices by viewModel.salesInvoices.collectAsState()
  val customers by viewModel.customers.collectAsState()
  val products by viewModel.products.collectAsState()
  val customerBalances by viewModel.customerBalances.collectAsState()
  val customerAdvances by viewModel.customerAdvances.collectAsState()
  val isSubmitting by viewModel.isSubmitting.collectAsState()

  var showNewSaleDialog by remember { mutableStateOf(false) }
  var pendingCreditOverride by remember { mutableStateOf<PendingSaleCreditOverride?>(null) }
  var overrideFailureError by remember { mutableStateOf<String?>(null) }
  var showCustomerPaymentDialog by remember { mutableStateOf<Customer?>(null) }
  var selectedInvoiceDetail by remember { mutableStateOf<SalesInvoice?>(null) }
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  Scaffold(
    containerColor = FigmaBackground,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    floatingActionButton = {
      FloatingActionButton(
        onClick = {
          overrideFailureError = null
          pendingCreditOverride = null
          showNewSaleDialog = true
        },
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
      customerBalances = customerBalances,
      customerAdvances = customerAdvances,
      isSubmitting = isSubmitting,
      externalErrorMessage = overrideFailureError,
      onClearExternalError = {
        overrideFailureError = null
        pendingCreditOverride = null
      },
      onGenerateInvoiceNumber = { viewModel.generateNextInvoiceNumber() },
      onGetPrice = { custId, prodId, defPrice ->
        viewModel.getSellingPriceForShop(custId, prodId, defPrice)
      },
      onDismiss = {
        pendingCreditOverride = null
        overrideFailureError = null
        showNewSaleDialog = false
      },
      onSubmit = { customerId, invNum, invDate, dueDate, items, paidAmount, method, notes, allowExpired, applyAdvance, onError ->
        overrideFailureError = null
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
          applyAvailableAdvance = applyAdvance,
          allowCreditLimitOverride = false,
          onCreditLimitExceeded = { assessment ->
            pendingCreditOverride = PendingSaleCreditOverride(
              customerId = customerId,
              invoiceNumber = invNum,
              invoiceDate = invDate,
              dueDate = dueDate,
              items = items,
              paidAmountPaise = paidAmount,
              paymentMethod = method,
              notes = notes,
              allowExpired = allowExpired,
              applyAvailableAdvance = applyAdvance,
              assessment = assessment
            )
          },
          onSuccess = {
            pendingCreditOverride = null
            overrideFailureError = null
            showNewSaleDialog = false
          },
          onError = onError
        )
      }
    )
  }

  pendingCreditOverride?.let { pending ->
    val custName = customers.find { it.id == pending.customerId }?.shopName ?: "Customer #${pending.customerId}"
    AlertDialog(
      onDismissRequest = { pendingCreditOverride = null },
      title = { Text("Customer Credit Limit Exceeded", fontWeight = FontWeight.Bold, color = FigmaRed) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Shop: $custName", fontWeight = FontWeight.SemiBold, color = FigmaInk)
          Text("Credit Limit: ${SubledgerCalculationEngine.formatPaise(pending.assessment.creditLimitPaise)}", fontSize = 13.sp, color = FigmaInk)
          Text("Current Balance: ${SubledgerCalculationEngine.formatPaise(pending.assessment.currentBalancePaise)}", fontSize = 13.sp, color = FigmaMuted)
          Text("Projected Balance: ${SubledgerCalculationEngine.formatPaise(pending.assessment.projectedBalancePaise)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FigmaRed)
          Text(
            "Exceeded by ${SubledgerCalculationEngine.formatPaise(pending.assessment.exceededByPaise)}. Override credit limit and issue bill anyway?",
            fontSize = 12.sp,
            color = FigmaMuted
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.createSalesInvoice(
              customerId = pending.customerId,
              invoiceNumber = pending.invoiceNumber,
              invoiceDate = pending.invoiceDate,
              dueDate = pending.dueDate,
              items = pending.items,
              paidAmountPaise = pending.paidAmountPaise,
              paymentMethod = pending.paymentMethod,
              notes = pending.notes,
              allowExpired = pending.allowExpired,
              applyAvailableAdvance = pending.applyAvailableAdvance,
              allowCreditLimitOverride = true,
              onSuccess = {
                pendingCreditOverride = null
                overrideFailureError = null
                showNewSaleDialog = false
              },
              onError = { err ->
                pendingCreditOverride = null
                overrideFailureError = err
                scope.launch { snackbarHostState.showSnackbar(err) }
              }
            )
          },
          enabled = !isSubmitting,
          colors = ButtonDefaults.buttonColors(containerColor = FigmaRed, contentColor = FigmaWhite),
          modifier = Modifier.testTag("confirm_sale_credit_override_btn")
        ) {
          Text("Override & Issue Bill")
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingCreditOverride = null }) {
          Text("Cancel", color = FigmaMuted)
        }
      }
    )
  }

  // Receive Customer Payment Dialog
  showCustomerPaymentDialog?.let { currentCustomer ->
    ReceiveCustomerPaymentDialog(
      customer = currentCustomer,
      allCustomers = customers,
      customerBalances = customerBalances,
      customerAdvances = customerAdvances,
      isSubmitting = isSubmitting,
      onDismiss = { showCustomerPaymentDialog = null },
      onApplyAdvance = { custId, onError ->
        viewModel.applyCustomerAdvance(
          customerId = custId,
          onSuccess = { applied ->
            showCustomerPaymentDialog = null
            scope.launch {
              snackbarHostState.showSnackbar("Applied advance of ${SubledgerCalculationEngine.formatPaise(applied)} to unpaid bills.")
            }
          },
          onError = onError
        )
      },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateSalesInvoiceDialog(
  customers: List<Customer>,
  products: List<Product>,
  customerBalances: Map<Long, Long> = emptyMap(),
  customerAdvances: Map<Long, Long> = emptyMap(),
  isSubmitting: Boolean = false,
  externalErrorMessage: String? = null,
  onClearExternalError: () -> Unit = {},
  onGenerateInvoiceNumber: suspend () -> String = { "INV-${System.currentTimeMillis() % 100000}" },
  onGetPrice: suspend (Long, Long, Long) -> Long,
  onDismiss: () -> Unit,
  onSubmit: (Long, String, Long, Long, List<SalesItemDraft>, Long, PaymentMethod?, String, Boolean, Boolean, (String) -> Unit) -> Unit
) {
  val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
  var selectedCustomerId by remember { mutableStateOf(customers.firstOrNull()?.id ?: 0L) }
  var invoiceNumber by remember { mutableStateOf("") }
  var notes by remember { mutableStateOf("") }
  var paidAmountRupees by remember { mutableStateOf("0") }
  var paymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
  var applyAvailableAdvance by remember { mutableStateOf(true) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  androidx.compose.runtime.LaunchedEffect(Unit) {
    if (invoiceNumber.isBlank()) {
      invoiceNumber = onGenerateInvoiceNumber()
    }
  }

  val items = remember { mutableStateListOf<SalesItemDraft>() }

  var selectedProductId by remember { mutableStateOf(products.firstOrNull()?.id ?: 0L) }
  var quantityInput by remember { mutableStateOf("") }
  var priceInput by remember { mutableStateOf("") }

  // Auto-fill price function
  fun autoFillPrice(custId: Long, prodId: Long) {
    val prod = products.find { it.id == prodId } ?: return
    coroutineScope.launch {
      val pricePaise = onGetPrice(custId, prodId, prod.defaultSellingPricePaise)
      priceInput = SubledgerCalculationEngine.formatPaiseForInput(pricePaise)
    }
  }

  // Initial auto-fill for default selections
  androidx.compose.runtime.LaunchedEffect(selectedCustomerId, selectedProductId) {
    if (priceInput.isBlank() && selectedCustomerId != 0L && selectedProductId != 0L) {
      autoFillPrice(selectedCustomerId, selectedProductId)
    }
  }

  val selectedCustomer = customers.find { it.id == selectedCustomerId }
  val selectedBalancePaise = customerBalances[selectedCustomerId] ?: 0L
  val selectedAdvancePaise = customerAdvances[selectedCustomerId] ?: 0L
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
                errorMessage = null
                onClearExternalError()
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

        selectedCustomer?.let { cust ->
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = FigmaSoftTeal,
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, FigmaLine, RoundedCornerShape(8.dp))
          ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              val termsText = "Terms: ${cust.paymentTermsDays} days"
              val limitText = if (cust.creditLimitPaise > 0L) {
                "Limit: ${SubledgerCalculationEngine.formatPaise(cust.creditLimitPaise)}"
              } else {
                "Limit: Unlimited"
              }
              Text(text = "$termsText · $limitText", fontSize = 11.sp, color = FigmaNavy, fontWeight = FontWeight.SemiBold)
              Text(
                text = "Current Due: ${SubledgerCalculationEngine.formatPaise(selectedBalancePaise)}" +
                  if (selectedAdvancePaise > 0L) " · Advance Credit: ${SubledgerCalculationEngine.formatPaise(selectedAdvancePaise)}" else "",
                fontSize = 11.sp,
                color = FigmaInk
              )
            }
          }
        }

        OutlinedTextField(
          value = invoiceNumber,
          onValueChange = {
            invoiceNumber = it
            errorMessage = null
            onClearExternalError()
          },
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
            val qty = quantityInput.trim().toIntOrNull()
            val pricePaise = SubledgerCalculationEngine.parseRupeesToPaiseOrNull(priceInput)

            if (prod == null || qty == null || qty <= 0 || pricePaise == null || pricePaise <= 0L) {
              errorMessage = "Please enter valid product, positive quantity, and price."
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
                  text = "${item.quantityPieces} pcs @ ${SubledgerCalculationEngine.formatPaise(item.unitPricePaise)} = ${SubledgerCalculationEngine.formatPaise(item.lineTotalPaise)}",
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
            onValueChange = {
              paidAmountRupees = it
              errorMessage = null
              onClearExternalError()
            },
            label = { Text("Payment Received Now (₹)") },
            modifier = Modifier.fillMaxWidth()
          )

          if (selectedAdvancePaise > 0L) {
            val applicableAdv = kotlin.math.min(selectedAdvancePaise, totalAmountPaise)
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  applyAvailableAdvance = !applyAvailableAdvance
                  onClearExternalError()
                },
              verticalAlignment = Alignment.CenterVertically
            ) {
              Checkbox(
                checked = applyAvailableAdvance,
                onCheckedChange = {
                  applyAvailableAdvance = it
                  onClearExternalError()
                },
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
              text = "Excess payment of ${SubledgerCalculationEngine.formatPaise(excessAdv)} will be saved as Customer Advance.",
              fontSize = 11.sp,
              color = FigmaTeal,
              fontWeight = FontWeight.Medium
            )
          }

          Text(text = "Payment Method:", fontSize = 12.sp, color = FigmaMuted)
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            PaymentMethod.values().forEach { method ->
              Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (paymentMethod == method) FigmaTeal else FigmaWhite,
                modifier = Modifier
                  .border(1.dp, if (paymentMethod == method) FigmaTeal else FigmaLine, RoundedCornerShape(6.dp))
                  .clickable {
                    paymentMethod = method
                    onClearExternalError()
                  }
                  .testTag("sales_payment_method_${method.name}")
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

        val displayedError = errorMessage ?: externalErrorMessage
        displayedError?.let { err ->
          Surface(
            color = FigmaRed.copy(alpha = 0.08f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, FigmaRed.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
              .testTag("sales_dialog_error")
          ) {
            Text(text = err, color = FigmaRed, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (selectedCustomerId == 0L) {
            errorMessage = "Please select a retail shop"
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
            errorMessage = "Please enter a valid payment received amount"
            return@Button
          }
          val now = System.currentTimeMillis()
          val termsDays = selectedCustomer?.paymentTermsDays ?: 7
          val dueDate = PaymentAllocationEngine.calculateDueDate(now, termsDays)

          errorMessage = null
          onSubmit(
            selectedCustomerId,
            invoiceNumber.trim(),
            now,
            dueDate,
            items.toList(),
            paidPaise,
            if (paidPaise > 0) paymentMethod else null,
            notes,
            false, // allowExpired = false strictly enforces FEFO non-expired stock
            applyAvailableAdvance
          ) { err ->
            errorMessage = err
          }
        },
        enabled = !isSubmitting,
        colors = ButtonDefaults.buttonColors(containerColor = FigmaTeal, contentColor = FigmaWhite)
      ) {
        Text(if (isSubmitting) "Generating..." else "Generate Bill & Dispatch")
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
  customerBalances: Map<Long, Long> = emptyMap(),
  customerAdvances: Map<Long, Long> = emptyMap(),
  isSubmitting: Boolean = false,
  onDismiss: () -> Unit,
  onApplyAdvance: (Long, (String) -> Unit) -> Unit = { _, _ -> },
  onSubmit: (Long, Long, Long, PaymentMethod, String, (String) -> Unit) -> Unit
) {
  var selectedCustId by remember { mutableStateOf(customer.id) }
  var amountInput by remember { mutableStateOf("") }
  var selectedMethod by remember { mutableStateOf(PaymentMethod.UPI) }
  var notes by remember { mutableStateOf("") }
  var errorText by remember { mutableStateOf<String?>(null) }

  val currentCust = allCustomers.find { it.id == selectedCustId } ?: customer
  val currentDue = customerBalances[selectedCustId] ?: 0L
  val currentAdvance = customerAdvances[selectedCustId] ?: 0L

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Receive Customer Payment", fontWeight = FontWeight.Bold, color = FigmaInk) },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
      ) {
        Text(text = "Select Customer:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FigmaInk)
        allCustomers.forEach { cust ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { selectedCustId = cust.id }
              .background(if (selectedCustId == cust.id) FigmaSoftTeal else FigmaWhite, RoundedCornerShape(6.dp))
              .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = cust.shopName,
              fontWeight = if (selectedCustId == cust.id) FontWeight.Bold else FontWeight.Normal,
              fontSize = 13.sp,
              color = FigmaInk
            )
            val bal = customerBalances[cust.id] ?: 0L
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
              text = "${currentCust.shopName} · Net Due: ${SubledgerCalculationEngine.formatPaise(currentDue.coerceAtLeast(0L))}",
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              color = FigmaInk
            )
            if (currentAdvance > 0L) {
              Text(
                text = "Available Advance: ${SubledgerCalculationEngine.formatPaise(currentAdvance)}",
                fontSize = 11.sp,
                color = FigmaTeal,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }

        if (currentAdvance > 0L && currentDue > 0L) {
          OutlinedButton(
            onClick = { onApplyAdvance(selectedCustId) { errorText = it } },
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
          label = { Text("Amount Received (₹)") },
          modifier = Modifier.fillMaxWidth()
        )

        val parsedAmt = SubledgerCalculationEngine.parseRupeesToPaiseOrNull(amountInput, allowNegative = false, allowEmptyAsZero = true) ?: 0L
        if (parsedAmt > currentDue.coerceAtLeast(0L) && parsedAmt > 0L) {
          val excess = parsedAmt - currentDue.coerceAtLeast(0L)
          Text(
            text = "Excess ${SubledgerCalculationEngine.formatPaise(excess)} above dues will be recorded as Customer Advance.",
            fontSize = 11.sp,
            color = FigmaTeal,
            fontWeight = FontWeight.Medium
          )
        }

        Text(text = "Payment Method:", fontSize = 12.sp, color = FigmaMuted)
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          PaymentMethod.values().forEach { method ->
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = if (selectedMethod == method) FigmaTeal else FigmaWhite,
              modifier = Modifier
                .border(1.dp, if (selectedMethod == method) FigmaTeal else FigmaLine, RoundedCornerShape(8.dp))
                .clickable { selectedMethod = method }
                .testTag("customer_payment_method_${method.name}")
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
            errorText = "Please enter a valid positive received amount"
            return@Button
          }
          errorText = null
          onSubmit(selectedCustId, paise, System.currentTimeMillis(), selectedMethod, notes) { err ->
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
