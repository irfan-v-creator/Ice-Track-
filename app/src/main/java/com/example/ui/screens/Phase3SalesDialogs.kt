package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.repository.InvoiceLifecycleRepository
import com.example.data.repository.ReturnRepository
import com.example.domain.engine.PaymentAllocationEngine
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.engine.UnitConversionEngine
import com.example.domain.model.Customer
import com.example.domain.model.DiscountType
import com.example.domain.model.InvoiceCancellationPaymentMode
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.PaymentMethod
import com.example.domain.model.Product
import com.example.domain.model.ProductUnit
import com.example.domain.model.ReturnCondition
import com.example.domain.model.ReturnSettlementType
import com.example.domain.model.SalesInvoiceDetail
import com.example.domain.model.UnitType
import com.example.ui.theme.FigmaAmber
import com.example.ui.theme.FigmaInk
import com.example.ui.theme.FigmaLine
import com.example.ui.theme.FigmaMuted
import com.example.ui.theme.FigmaNavy
import com.example.ui.theme.FigmaRed
import com.example.ui.theme.FigmaSoftTeal
import com.example.ui.theme.FigmaTeal
import com.example.ui.theme.FigmaWhite
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IceTrackDatePickerField(
  label: String,
  dateMillis: Long,
  onDateSelected: (Long) -> Unit,
  modifier: Modifier = Modifier,
  testTagValue: String = ""
) {
  var showPicker by remember { mutableStateOf(false) }
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = FigmaWhite,
    modifier = modifier
      .border(1.dp, FigmaLine, RoundedCornerShape(8.dp))
      .clickable { showPicker = true }
      .then(if (testTagValue.isNotBlank()) Modifier.testTag(testTagValue) else Modifier)
      .defaultMinSize(minHeight = 48.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text(text = label, fontSize = 10.sp, color = FigmaMuted)
        Text(
          text = SubledgerCalculationEngine.formatDate(dateMillis),
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
          color = FigmaInk
        )
      }
      Icon(Icons.Default.CalendarToday, contentDescription = "Pick $label", tint = FigmaTeal, modifier = Modifier.size(16.dp))
    }
  }

  if (showPicker) {
    val state = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
    DatePickerDialog(
      onDismissRequest = { showPicker = false },
      confirmButton = {
        TextButton(onClick = {
          state.selectedDateMillis?.let(onDateSelected)
          showPicker = false
        }) { Text("Select") }
      },
      dismissButton = {
        TextButton(onClick = { showPicker = false }) { Text("Cancel") }
      }
    ) {
      DatePicker(state = state)
    }
  }
}

@Composable
fun InvoiceErrorBanner(error: String?, testTagValue: String = "dialog_error_banner") {
  error?.let { msg ->
    Surface(
      color = FigmaRed.copy(alpha = 0.08f),
      shape = RoundedCornerShape(8.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, FigmaRed.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
        .testTag(testTagValue)
    ) {
      Text(text = msg, color = FigmaRed, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
    }
  }
}

@Composable
fun SalesInvoiceDetailDialog(
  detail: SalesInvoiceDetail,
  onDismiss: () -> Unit,
  onEditClick: () -> Unit,
  onReturnClick: () -> Unit,
  onCancelClick: () -> Unit,
  onShareClick: () -> Unit
) {
  val inv = detail.invoice
  val isCancelled = inv.invoiceStatus == InvoiceStatus.CANCELLED
  val partyName = inv.partyNameSnapshot.ifBlank { detail.customer?.shopName ?: "Customer #${inv.customerId}" }

  AlertDialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
    modifier = Modifier
      .fillMaxWidth(0.94f)
      .widthIn(max = 540.dp)
      .testTag("sales_detail_dialog"),
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(text = "Bill #${inv.invoiceNumber}", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = FigmaInk)
          Text(
            text = "Rev v${inv.revisionVersion} • ${inv.paymentStatus.displayName}",
            fontSize = 11.sp,
            color = FigmaMuted
          )
        }
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = if (isCancelled) FigmaRed.copy(alpha = 0.14f) else FigmaSoftTeal
        ) {
          Text(
            text = inv.invoiceStatus.displayName,
            color = if (isCancelled) FigmaRed else FigmaTeal,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = FigmaSoftTeal,
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FigmaLine, RoundedCornerShape(8.dp))
        ) {
          Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = "Shop: $partyName", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FigmaInk)
            if (detail.customer != null && detail.customer.shopName != partyName) {
              Text(text = "Linked Account: ${detail.customer.shopName}", fontSize = 11.sp, color = FigmaNavy)
            }
            if (inv.partyPhoneSnapshot.isNotBlank()) {
              Text(text = "Phone: ${inv.partyPhoneSnapshot}", fontSize = 11.sp, color = FigmaMuted)
            }
            if (inv.partyAddressSnapshot.isNotBlank()) {
              Text(text = "Address: ${inv.partyAddressSnapshot}", fontSize = 11.sp, color = FigmaMuted)
            }
            Text(
              text = "Date: ${SubledgerCalculationEngine.formatDate(inv.invoiceDate)} • Due: ${SubledgerCalculationEngine.formatDate(inv.dueDate)}",
              fontSize = 11.sp,
              color = FigmaMuted
            )
            if (inv.notes.isNotBlank()) {
              Text(text = "Notes: ${inv.notes}", fontSize = 11.sp, color = FigmaInk)
            }
            if (isCancelled && !inv.cancellationReason.isNullOrBlank()) {
              Text(text = "Cancel Reason: ${inv.cancellationReason}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FigmaRed)
            }
          }
        }

        Text(text = "Line Items (${detail.items.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = FigmaInk)
        detail.items.forEach { item ->
          val returnedQty = detail.returnedQtyByProductId[item.productId] ?: 0
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = FigmaWhite,
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, FigmaLine, RoundedCornerShape(8.dp))
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = item.productNameSnapshot.ifBlank { "Product #${item.productId}" },
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 12.sp,
                  color = FigmaInk
                )
                Text(
                  text = "${item.unitQuantity} ${item.unitType.shortName} (${item.quantityPieces} pcs) @ ${SubledgerCalculationEngine.formatPaise(item.unitPricePaise)}/pc",
                  fontSize = 11.sp,
                  color = FigmaMuted
                )
                if (item.itemDiscountPaise > 0L) {
                  Text(text = "Line Discount: -${SubledgerCalculationEngine.formatPaise(item.itemDiscountPaise)}", fontSize = 10.sp, color = FigmaAmber)
                }
                if (returnedQty > 0) {
                  Text(text = "Returned on invoice: $returnedQty pcs", fontSize = 10.sp, color = FigmaNavy)
                }
              }
              Text(
                text = SubledgerCalculationEngine.formatPaise(item.lineTotalPaise),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = FigmaInk
              )
            }
          }
        }

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = FigmaWhite,
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FigmaLine, RoundedCornerShape(8.dp))
        ) {
          Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DetailAmountRow("Subtotal", inv.subtotalPaise)
            if (inv.discountAmountPaise > 0L) {
              DetailAmountRow("Discount (${inv.discountType.displayName})", -inv.discountAmountPaise, valueColor = FigmaAmber)
            }
            if (inv.additionalChargesPaise > 0L) {
              DetailAmountRow("Additional Charges", inv.additionalChargesPaise)
            }
            HorizontalDivider(color = FigmaLine)
            DetailAmountRow("Grand Total", inv.totalAmountPaise, bold = true)
            DetailAmountRow("Paid / Allocated", inv.paidAmountPaise, valueColor = FigmaTeal)
            DetailAmountRow("Balance Due", inv.balanceDuePaise, bold = true, valueColor = if (inv.balanceDuePaise > 0L) FigmaRed else FigmaTeal)
          }
        }

        if (detail.allocations.isNotEmpty()) {
          Text(text = "Payment Allocations (${detail.allocations.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = FigmaInk)
          detail.allocations.forEach { alloc ->
            Text(
              text = "• ${SubledgerCalculationEngine.formatDate(alloc.allocatedAt)}: ${SubledgerCalculationEngine.formatPaise(alloc.allocatedAmountPaise)} (${alloc.notes})",
              fontSize = 11.sp,
              color = FigmaMuted
            )
          }
        }

        if (detail.returns.isNotEmpty()) {
          Text(text = "Sales Returns (${detail.returns.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = FigmaInk)
          detail.returns.forEach { ret ->
            Text(
              text = "• ${ret.returnNumber}: ${SubledgerCalculationEngine.formatPaise(ret.totalReturnAmountPaise)} (${ret.settlementType.displayName}) — ${ret.reason}",
              fontSize = 11.sp,
              color = FigmaNavy
            )
          }
        }

        if (detail.refunds.isNotEmpty()) {
          Text(text = "Refund Records (${detail.refunds.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = FigmaInk)
          detail.refunds.forEach { rf ->
            Text(
              text = "• ${SubledgerCalculationEngine.formatPaise(rf.amountPaise)} via ${rf.paymentMethod.displayName} — ${rf.executionStatus.displayName}",
              fontSize = 11.sp,
              color = FigmaAmber
            )
          }
        }

        if (detail.auditLogs.isNotEmpty()) {
          Text(text = "Audit Trail (${detail.auditLogs.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = FigmaInk)
          detail.auditLogs.take(5).forEach { log ->
            Text(
              text = "• ${log.operationType.displayName} (${SubledgerCalculationEngine.formatDate(log.createdAt)}): ${log.reason}",
              fontSize = 10.sp,
              color = FigmaMuted
            )
          }
        }
      }
    },
    confirmButton = {
      Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          OutlinedButton(
            onClick = onShareClick,
            modifier = Modifier
              .weight(1f)
              .defaultMinSize(minHeight = 48.dp)
              .testTag("sales_detail_share_btn")
          ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Share", fontSize = 12.sp)
          }
          if (!isCancelled) {
            Button(
              onClick = onEditClick,
              colors = ButtonDefaults.buttonColors(containerColor = FigmaTeal, contentColor = FigmaWhite),
              modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .testTag("sales_detail_edit_btn")
            ) {
              Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Edit", fontSize = 12.sp)
            }
          }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          if (!isCancelled) {
            OutlinedButton(
              onClick = onReturnClick,
              modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .testTag("sales_detail_return_btn")
            ) {
              Icon(Icons.Default.KeyboardReturn, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Return", fontSize = 12.sp)
            }
            OutlinedButton(
              onClick = onCancelClick,
              colors = ButtonDefaults.outlinedButtonColors(contentColor = FigmaRed),
              modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .testTag("sales_detail_cancel_btn")
            ) {
              Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Cancel Bill", fontSize = 12.sp)
            }
          }
          TextButton(
            onClick = onDismiss,
            modifier = Modifier
              .defaultMinSize(minHeight = 48.dp)
              .testTag("sales_detail_close_btn")
          ) {
            Text("Close", color = FigmaMuted)
          }
        }
      }
    }
  )
}

@Composable
fun DetailAmountRow(
  label: String,
  amountPaise: Long,
  bold: Boolean = false,
  valueColor: androidx.compose.ui.graphics.Color = FigmaInk
) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(
      text = label,
      fontSize = if (bold) 13.sp else 11.sp,
      fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
      color = if (bold) FigmaInk else FigmaMuted
    )
    Text(
      text = SubledgerCalculationEngine.formatPaise(amountPaise),
      fontSize = if (bold) 13.sp else 11.sp,
      fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
      color = valueColor
    )
  }
}

private data class EditSalesLineState(
  val productId: Long,
  val productName: String,
  val unitType: UnitType,
  val unitQuantityText: String,
  val pricePerPieceText: String,
  val itemDiscountText: String,
  val productUnit: ProductUnit?
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditSalesInvoiceDialog(
  detail: SalesInvoiceDetail,
  customers: List<Customer>,
  products: List<Product>,
  isSubmitting: Boolean,
  externalError: String? = null,
  onClearExternalError: () -> Unit = {},
  onGetProductUnit: suspend (Long) -> ProductUnit?,
  onDismiss: () -> Unit,
  onSubmit: (InvoiceLifecycleRepository.EditSalesInvoiceRequest, (String) -> Unit) -> Unit
) {
  val inv = detail.invoice
  val scope = rememberCoroutineScope()
  var selectedCustomerId by remember { mutableStateOf(inv.customerId) }
  var partyNameSnapshot by remember { mutableStateOf(inv.partyNameSnapshot.ifBlank { detail.customer?.shopName.orEmpty() }) }
  var partyPhoneSnapshot by remember { mutableStateOf(inv.partyPhoneSnapshot.ifBlank { detail.customer?.phoneNumber.orEmpty() }) }
  var partyAddressSnapshot by remember { mutableStateOf(inv.partyAddressSnapshot.ifBlank { detail.customer?.address.orEmpty() }) }
  var invoiceDate by remember { mutableStateOf(inv.invoiceDate) }
  var dueDate by remember { mutableStateOf(inv.dueDate) }
  var notes by remember { mutableStateOf(inv.notes) }
  var paymentMethod by remember { mutableStateOf(inv.paymentMethod ?: PaymentMethod.CASH) }
  var discountType by remember { mutableStateOf(inv.discountType) }
  var discountValueText by remember {
    mutableStateOf(
      when (inv.discountType) {
        DiscountType.NONE -> "0"
        DiscountType.FLAT -> SubledgerCalculationEngine.formatPaiseForInput(inv.discountValueRaw)
        DiscountType.PERCENT -> (inv.discountValueRaw / 100.0).let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
      }
    )
  }
  var additionalChargesText by remember { mutableStateOf(SubledgerCalculationEngine.formatPaiseForInput(inv.additionalChargesPaise)) }
  var editReason by remember { mutableStateOf("") }
  var advanceRetentionText by remember { mutableStateOf("") }
  var refundPaymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
  var localError by remember { mutableStateOf<String?>(null) }

  val unitCache = remember { mutableStateMapOf<Long, ProductUnit?>() }
  val editLines = remember { mutableStateListOf<EditSalesLineState>() }

  LaunchedEffect(inv.id) {
    val aggregated = detail.items.groupBy { it.productId }
    aggregated.forEach { (prodId, itemsForProd) ->
      val unit = onGetProductUnit(prodId)
      unitCache[prodId] = unit
      val first = itemsForProd.first()
      val totalPcs = itemsForProd.sumOf { it.quantityPieces }
      val totalLineDisc = itemsForProd.sumOf { it.itemDiscountPaise }
      editLines.add(
        EditSalesLineState(
          productId = prodId,
          productName = first.productNameSnapshot.ifBlank { products.find { it.id == prodId }?.name ?: "Product #$prodId" },
          unitType = UnitType.PIECE,
          unitQuantityText = totalPcs.toString(),
          pricePerPieceText = SubledgerCalculationEngine.formatPaiseForInput(first.unitPricePaise),
          itemDiscountText = SubledgerCalculationEngine.formatPaiseForInput(totalLineDisc),
          productUnit = unit
        )
      )
    }
  }

  var addProductId by remember { mutableStateOf(products.firstOrNull()?.id ?: 0L) }

  fun computeLinePieces(line: EditSalesLineState): Int {
    val qty = line.unitQuantityText.trim().toIntOrNull() ?: 0
    if (qty <= 0) return 0
    val unit = line.productUnit ?: unitCache[line.productId]
    return when (line.unitType) {
      UnitType.PIECE -> qty
      UnitType.BOX -> qty * (unit?.piecesPerBox ?: 1)
      UnitType.CARTON -> qty * ((unit?.piecesPerBox ?: 1) * (unit?.boxesPerCarton ?: 1))
    }
  }

  val subtotalPaise = editLines.sumOf { line ->
    val pcs = computeLinePieces(line)
    val rate = SubledgerCalculationEngine.parseRupeesToPaiseOrNull(line.pricePerPieceText, allowNegative = false, allowEmptyAsZero = true) ?: 0L
    val disc = SubledgerCalculationEngine.parseRupeesToPaiseOrNull(line.itemDiscountText, allowNegative = false, allowEmptyAsZero = true) ?: 0L
    (pcs.toLong() * rate - disc).coerceAtLeast(0L)
  }
  val discRaw = when (discountType) {
    DiscountType.NONE -> 0L
    DiscountType.FLAT -> SubledgerCalculationEngine.parseRupeesToPaiseOrNull(discountValueText, allowNegative = false, allowEmptyAsZero = true) ?: 0L
    DiscountType.PERCENT -> ((discountValueText.toDoubleOrNull() ?: 0.0) * 100.0).toLong().coerceIn(0L, 10000L)
  }
  val chargesPaise = SubledgerCalculationEngine.parseRupeesToPaiseOrNull(additionalChargesText, allowNegative = false, allowEmptyAsZero = true) ?: 0L
  val computedTotals = SubledgerCalculationEngine.computeInvoiceTotals(subtotalPaise, discountType, discRaw, chargesPaise)
  val excessPaidPaise = (inv.paidAmountPaise - computedTotals.totalAmountPaise).coerceAtLeast(0L)

  LaunchedEffect(excessPaidPaise) {
    if (excessPaidPaise > 0L) {
      advanceRetentionText = SubledgerCalculationEngine.formatPaiseForInput(excessPaidPaise)
    } else {
      advanceRetentionText = ""
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
    modifier = Modifier
      .fillMaxWidth(0.95f)
      .widthIn(max = 560.dp)
      .testTag("edit_sales_dialog"),
    title = { Text("Edit Sales Invoice #${inv.invoiceNumber}", fontWeight = FontWeight.Bold, color = FigmaInk) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text(text = "Customer Account (Mode B Transfer or Keep):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FigmaInk)
        customers.forEach { cust ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                selectedCustomerId = cust.id
                partyNameSnapshot = cust.shopName
                partyPhoneSnapshot = cust.phoneNumber
                partyAddressSnapshot = cust.address
                onClearExternalError()
              }
              .background(if (selectedCustomerId == cust.id) FigmaSoftTeal else FigmaWhite, RoundedCornerShape(6.dp))
              .padding(6.dp)
          ) {
            Text(
              text = cust.shopName,
              fontWeight = if (selectedCustomerId == cust.id) FontWeight.Bold else FontWeight.Normal,
              fontSize = 12.sp,
              color = FigmaInk
            )
          }
        }

        Text(text = "Party Billing Snapshot (Mode A Invoice Print Details):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FigmaNavy)
        OutlinedTextField(value = partyNameSnapshot, onValueChange = { partyNameSnapshot = it }, label = { Text("Billed Party Name") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          OutlinedTextField(value = partyPhoneSnapshot, onValueChange = { partyPhoneSnapshot = it }, label = { Text("Phone") }, modifier = Modifier.weight(1f))
          OutlinedTextField(value = partyAddressSnapshot, onValueChange = { partyAddressSnapshot = it }, label = { Text("Address") }, modifier = Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          IceTrackDatePickerField("Invoice Date", invoiceDate, { invoiceDate = it }, Modifier.weight(1f))
          IceTrackDatePickerField("Due Date", dueDate, { dueDate = it }, Modifier.weight(1f))
        }

        HorizontalDivider(color = FigmaLine)
        Text(text = "Line Items (${editLines.size}):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FigmaInk)

        editLines.forEachIndexed { idx, line ->
          val pcs = computeLinePieces(line)
          val rate = SubledgerCalculationEngine.parseRupeesToPaiseOrNull(line.pricePerPieceText, allowNegative = false, allowEmptyAsZero = true) ?: 0L
          val disc = SubledgerCalculationEngine.parseRupeesToPaiseOrNull(line.itemDiscountText, allowNegative = false, allowEmptyAsZero = true) ?: 0L
          val netLine = (pcs.toLong() * rate - disc).coerceAtLeast(0L)
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = FigmaWhite,
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, FigmaLine, RoundedCornerShape(8.dp))
          ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "${line.productName} ($pcs pcs = ${SubledgerCalculationEngine.formatPaise(netLine)})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = FigmaInk, modifier = Modifier.weight(1f))
                if (editLines.size > 1) {
                  IconButton(onClick = { editLines.removeAt(idx) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove line", tint = FigmaRed, modifier = Modifier.size(16.dp))
                  }
                }
              }
              FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                UnitType.values().forEach { ut ->
                  FilterChip(
                    selected = line.unitType == ut,
                    onClick = { editLines[idx] = line.copy(unitType = ut) },
                    label = { Text(ut.displayName, fontSize = 11.sp) }
                  )
                }
              }
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                  value = line.unitQuantityText,
                  onValueChange = { editLines[idx] = line.copy(unitQuantityText = it) },
                  label = { Text("Qty (${line.unitType.shortName})") },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                  value = line.pricePerPieceText,
                  onValueChange = { editLines[idx] = line.copy(pricePerPieceText = it) },
                  label = { Text("Rate ₹/pc") },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                  modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                  value = line.itemDiscountText,
                  onValueChange = { editLines[idx] = line.copy(itemDiscountText = it) },
                  label = { Text("Line Disc ₹") },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                  modifier = Modifier.weight(1f)
                )
              }
            }
          }
        }

        if (products.isNotEmpty()) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            val picked = products.find { it.id == addProductId } ?: products.first()
            OutlinedButton(
              onClick = {
                val curIdx = products.indexOfFirst { it.id == picked.id }
                addProductId = products[(curIdx + 1) % products.size].id
              },
              modifier = Modifier.weight(1f)
            ) {
              Text(picked.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
            }
            Button(
              onClick = {
                scope.launch {
                  val unit = onGetProductUnit(picked.id)
                  unitCache[picked.id] = unit
                  editLines.add(
                    EditSalesLineState(
                      productId = picked.id,
                      productName = picked.name,
                      unitType = UnitType.PIECE,
                      unitQuantityText = "1",
                      pricePerPieceText = SubledgerCalculationEngine.formatPaiseForInput(picked.defaultSellingPricePaise),
                      itemDiscountText = "0",
                      productUnit = unit
                    )
                  )
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = FigmaNavy, contentColor = FigmaWhite)
            ) {
              Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Text("Add", fontSize = 11.sp)
            }
          }
        }

        HorizontalDivider(color = FigmaLine)
        Text(text = "Invoice Discount & Additional Charges", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FigmaInk)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          DiscountType.values().forEach { dt ->
            FilterChip(
              selected = discountType == dt,
              onClick = { discountType = dt },
              label = { Text(dt.displayName, fontSize = 11.sp) }
            )
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          if (discountType != DiscountType.NONE) {
            OutlinedTextField(
              value = discountValueText,
              onValueChange = { discountValueText = it },
              label = { Text(if (discountType == DiscountType.FLAT) "Discount (₹)" else "Discount (%)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              modifier = Modifier.weight(1f)
            )
          }
          OutlinedTextField(
            value = additionalChargesText,
            onValueChange = { additionalChargesText = it },
            label = { Text("Extra Charges (₹)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f)
          )
        }

        Surface(shape = RoundedCornerShape(8.dp), color = FigmaSoftTeal, modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            DetailAmountRow("Updated Subtotal", computedTotals.subtotalPaise)
            DetailAmountRow("Updated Discount", -computedTotals.discountAmountPaise, valueColor = FigmaAmber)
            DetailAmountRow("Extra Charges", computedTotals.additionalChargesPaise)
            DetailAmountRow("Updated Grand Total", computedTotals.totalAmountPaise, bold = true)
            DetailAmountRow("Already Paid", inv.paidAmountPaise, valueColor = FigmaTeal)
          }
        }

        if (excessPaidPaise > 0L) {
          val advReq = (SubledgerCalculationEngine.parseRupeesToPaiseOrNull(advanceRetentionText, allowNegative = false, allowEmptyAsZero = true) ?: 0L).coerceAtMost(excessPaidPaise)
          val refundLiability = (excessPaidPaise - advReq).coerceAtLeast(0L)
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = FigmaAmber.copy(alpha = 0.1f),
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, FigmaAmber, RoundedCornerShape(8.dp))
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(
                text = "Overpayment Split (${SubledgerCalculationEngine.formatPaise(excessPaidPaise)} excess)",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = FigmaInk
              )
              OutlinedTextField(
                value = advanceRetentionText,
                onValueChange = { advanceRetentionText = it },
                label = { Text("Retain as Customer Advance (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("sales_excess_advance_input")
              )
              Text(
                text = "Recorded Refund Payout Liability: ${SubledgerCalculationEngine.formatPaise(refundLiability)}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = FigmaRed
              )
              if (refundLiability > 0L) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  PaymentMethod.values().forEach { pm ->
                    FilterChip(
                      selected = refundPaymentMethod == pm,
                      onClick = { refundPaymentMethod = pm },
                      label = { Text(pm.displayName, fontSize = 10.sp) }
                    )
                  }
                }
              }
            }
          }
        }

        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Invoice Notes") },
          modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
          value = editReason,
          onValueChange = {
            editReason = it
            localError = null
          },
          label = { Text("Mandatory Edit Reason *") },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("sales_edit_reason_input")
        )
        InvoiceErrorBanner(localError ?: externalError, "sales_edit_error")
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (editReason.isBlank()) {
            localError = "Mandatory edit reason is required."
            return@Button
          }
          val reqLines = mutableListOf<InvoiceLifecycleRepository.EditSalesLineRequest>()
          for (l in editLines) {
            val pcs = computeLinePieces(l)
            val rate = SubledgerCalculationEngine.parseRupeesToPaiseOrNull(l.pricePerPieceText, allowNegative = false, allowEmptyAsZero = false)
            val disc = SubledgerCalculationEngine.parseRupeesToPaiseOrNull(l.itemDiscountText, allowNegative = false, allowEmptyAsZero = true) ?: 0L
            if (pcs <= 0 || rate == null || rate < 0L) {
              localError = "Each line item must have positive quantity and valid rate."
              return@Button
            }
            reqLines.add(
              InvoiceLifecycleRepository.EditSalesLineRequest(
                productId = l.productId,
                productNameSnapshot = l.productName,
                quantityPieces = pcs,
                unitPricePaise = rate,
                unitType = l.unitType,
                unitQuantity = l.unitQuantityText.trim().toIntOrNull() ?: pcs,
                unitRatePaise = rate,
                itemDiscountPaise = disc
              )
            )
          }
          val advRetention = if (excessPaidPaise > 0L) {
            val parsed = SubledgerCalculationEngine.parseRupeesToPaiseOrNull(advanceRetentionText, allowNegative = false, allowEmptyAsZero = true)
            if (parsed == null || parsed > excessPaidPaise) {
              localError = "Advance retention must be between ₹0.00 and ${SubledgerCalculationEngine.formatPaise(excessPaidPaise)}."
              return@Button
            }
            parsed
          } else null

          val req = InvoiceLifecycleRepository.EditSalesInvoiceRequest(
            invoiceId = inv.id,
            expectedRevisionVersion = inv.revisionVersion,
            customerId = selectedCustomerId,
            partyNameSnapshot = partyNameSnapshot,
            partyPhoneSnapshot = partyPhoneSnapshot,
            partyAddressSnapshot = partyAddressSnapshot,
            invoiceDate = invoiceDate,
            dueDate = dueDate,
            discountType = discountType,
            discountValueRaw = discRaw,
            additionalChargesPaise = chargesPaise,
            paymentMethod = paymentMethod,
            notes = notes,
            lines = reqLines,
            requestedAdvanceRetentionPaise = advRetention,
            refundPaymentMethod = refundPaymentMethod,
            reason = editReason
          )
          localError = null
          onSubmit(req) { localError = it }
        },
        enabled = !isSubmitting,
        colors = ButtonDefaults.buttonColors(containerColor = FigmaTeal, contentColor = FigmaWhite),
        modifier = Modifier.testTag("confirm_edit_sales_btn")
      ) {
        Text(if (isSubmitting) "Updating..." else "Save Changes")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel", color = FigmaMuted) }
    }
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SalesReturnDialog(
  detail: SalesInvoiceDetail,
  isSubmitting: Boolean,
  onDismiss: () -> Unit,
  onSubmit: (List<ReturnRepository.RequestedSalesReturnLine>, ReturnSettlementType, PaymentMethod, String, String, (String) -> Unit) -> Unit
) {
  val qtyByItemId = remember { mutableStateMapOf<Long, String>() }
  val condByItemId = remember { mutableStateMapOf<Long, ReturnCondition>() }
  var settlement by remember { mutableStateOf(ReturnSettlementType.CREDIT_NOTE) }
  var refundMethod by remember { mutableStateOf(PaymentMethod.CASH) }
  var reason by remember { mutableStateOf("") }
  var notes by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
    modifier = Modifier
      .fillMaxWidth(0.94f)
      .widthIn(max = 540.dp)
      .testTag("sales_return_dialog"),
    title = { Text("Sales Return • #${detail.invoice.invoiceNumber}", fontWeight = FontWeight.Bold, color = FigmaInk) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        detail.items.forEach { item ->
          val prevRet = detail.returnedQtyByProductId[item.productId] ?: 0
          val remaining = (item.quantityPieces - prevRet).coerceAtLeast(0)
          val cond = condByItemId[item.id] ?: ReturnCondition.RESELLABLE
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = FigmaWhite,
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, FigmaLine, RoundedCornerShape(8.dp))
          ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(
                text = "${item.productNameSnapshot.ifBlank { "Product #${item.productId}" }} (Max returnable: $remaining pcs)",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = FigmaInk
              )
              OutlinedTextField(
                value = qtyByItemId[item.id] ?: "",
                onValueChange = { qtyByItemId[item.id] = it },
                label = { Text("Return Pieces (0 to $remaining)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
              )
              FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReturnCondition.values().forEach { rc ->
                  FilterChip(
                    selected = cond == rc,
                    onClick = { condByItemId[item.id] = rc },
                    label = { Text(rc.displayName, fontSize = 10.sp) }
                  )
                }
              }
            }
          }
        }

        Text(text = "Settlement Mode:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = FigmaInk)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(ReturnSettlementType.CREDIT_NOTE, ReturnSettlementType.ADVANCE, ReturnSettlementType.CASH_REFUND).forEach { st ->
            FilterChip(
              selected = settlement == st,
              onClick = { settlement = st },
              label = { Text(st.displayName, fontSize = 11.sp) }
            )
          }
        }
        if (settlement == ReturnSettlementType.CASH_REFUND) {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PaymentMethod.values().forEach { pm ->
              FilterChip(
                selected = refundMethod == pm,
                onClick = { refundMethod = pm },
                label = { Text(pm.displayName, fontSize = 10.sp) }
              )
            }
          }
        }

        OutlinedTextField(
          value = reason,
          onValueChange = { reason = it; error = null },
          label = { Text("Mandatory Return Reason *") },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("sales_return_reason_input")
        )
        OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes (Optional)") }, modifier = Modifier.fillMaxWidth())
        InvoiceErrorBanner(error, "sales_return_error")
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (reason.isBlank()) {
            error = "Return reason is mandatory."
            return@Button
          }
          val lines = detail.items.mapNotNull { item ->
            val q = qtyByItemId[item.id]?.trim()?.toIntOrNull() ?: 0
            if (q > 0) {
              ReturnRepository.RequestedSalesReturnLine(
                salesInvoiceItemId = item.id,
                quantityPieces = q,
                condition = condByItemId[item.id] ?: ReturnCondition.RESELLABLE
              )
            } else null
          }
          if (lines.isEmpty()) {
            error = "Enter a positive return quantity for at least one item."
            return@Button
          }
          onSubmit(lines, settlement, refundMethod, reason, notes) { error = it }
        },
        enabled = !isSubmitting,
        colors = ButtonDefaults.buttonColors(containerColor = FigmaTeal, contentColor = FigmaWhite),
        modifier = Modifier.testTag("confirm_sales_return_btn")
      ) {
        Text(if (isSubmitting) "Processing..." else "Confirm Return")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = FigmaMuted) } }
  )
}

@Composable
fun CancelInvoiceDialog(
  invoiceNumber: String,
  paidAmountPaise: Long,
  partyLabel: String,
  isSubmitting: Boolean,
  onDismiss: () -> Unit,
  onConfirm: (String, InvoiceCancellationPaymentMode, (String) -> Unit) -> Unit
) {
  var reason by remember { mutableStateOf("") }
  var mode by remember { mutableStateOf(InvoiceCancellationPaymentMode.CONVERT_TO_ADVANCE) }
  var error by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Cancel #$invoiceNumber?", fontWeight = FontWeight.Bold, color = FigmaRed) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Invoice will be permanently soft-cancelled ($partyLabel). Its number will never be reused.",
          fontSize = 12.sp,
          color = FigmaInk
        )
        if (paidAmountPaise > 0L) {
          Text(
            text = "Active Paid Amount: ${SubledgerCalculationEngine.formatPaise(paidAmountPaise)}",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = FigmaAmber
          )
          InvoiceCancellationPaymentMode.values().forEach { opt ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { mode = opt }
                .background(if (mode == opt) FigmaSoftTeal else FigmaWhite, RoundedCornerShape(8.dp))
                .border(1.dp, if (mode == opt) FigmaTeal else FigmaLine, RoundedCornerShape(8.dp))
                .padding(10.dp)
            ) {
              Text(text = opt.displayName, fontSize = 12.sp, fontWeight = if (mode == opt) FontWeight.Bold else FontWeight.Normal, color = FigmaInk)
            }
          }
        }
        OutlinedTextField(
          value = reason,
          onValueChange = { reason = it; error = null },
          label = { Text("Mandatory Cancellation Reason *") },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("cancel_invoice_reason_input")
        )
        InvoiceErrorBanner(error, "cancel_invoice_error")
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (reason.isBlank()) {
            error = "Cancellation reason is mandatory."
            return@Button
          }
          onConfirm(reason, mode) { error = it }
        },
        enabled = !isSubmitting,
        colors = ButtonDefaults.buttonColors(containerColor = FigmaRed, contentColor = FigmaWhite),
        modifier = Modifier.testTag("confirm_cancel_invoice_btn")
      ) {
        Text(if (isSubmitting) "Cancelling..." else "Cancel Invoice")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Keep Active", color = FigmaMuted) } }
  )
}
