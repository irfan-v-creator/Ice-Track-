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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.model.DiscountType
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.PaymentMethod
import com.example.domain.model.Product
import com.example.domain.model.ProductUnit
import com.example.domain.model.PurchaseInvoiceDetail
import com.example.domain.model.ReturnCondition
import com.example.domain.model.ReturnSettlementType
import com.example.domain.model.Supplier
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

@Composable
fun PurchaseInvoiceDetailDialog(
  detail: PurchaseInvoiceDetail,
  onDismiss: () -> Unit,
  onEditClick: () -> Unit,
  onReturnClick: () -> Unit,
  onCancelClick: () -> Unit,
  onShareClick: () -> Unit
) {
  val inv = detail.invoice
  val isCancelled = inv.invoiceStatus == InvoiceStatus.CANCELLED
  val partyName = inv.partyNameSnapshot.ifBlank { detail.supplier?.name ?: "Supplier #${inv.supplierId}" }

  AlertDialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
    modifier = Modifier
      .fillMaxWidth(0.94f)
      .widthIn(max = 540.dp)
      .testTag("purchase_detail_dialog"),
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(text = "Purchase #${inv.invoiceNumber}", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = FigmaInk)
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
            Text(text = "Supplier: $partyName", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FigmaInk)
            if (detail.supplier != null && detail.supplier.name != partyName) {
              Text(text = "Linked Account: ${detail.supplier.name}", fontSize = 11.sp, color = FigmaNavy)
            }
            if (inv.partyPhoneSnapshot.isNotBlank()) {
              Text(text = "Phone: ${inv.partyPhoneSnapshot}", fontSize = 11.sp, color = FigmaMuted)
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

        Text(text = "Batches & Line Items (${detail.items.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = FigmaInk)
        detail.items.forEach { item ->
          val soldQty = detail.soldQtyByBatchNumber[item.batchNumber] ?: 0
          val liveRem = detail.batches.find { it.batchNumber == item.batchNumber && it.productId == item.productId }?.currentQuantityPieces ?: 0
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
                  text = "${item.productNameSnapshot.ifBlank { "Product #${item.productId}" }} [${item.batchNumber}]",
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 12.sp,
                  color = FigmaInk
                )
                Text(
                  text = "${item.quantityPieces} pcs @ ${SubledgerCalculationEngine.formatPaise(item.costPerPiecePaise)} • Live Stock: $liveRem pcs (Sold/Used: $soldQty)",
                  fontSize = 10.sp,
                  color = FigmaMuted
                )
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
              DetailAmountRow("Discount", -inv.discountAmountPaise, valueColor = FigmaAmber)
            }
            if (inv.additionalChargesPaise > 0L) {
              DetailAmountRow("Additional Charges", inv.additionalChargesPaise)
            }
            HorizontalDivider(color = FigmaLine)
            DetailAmountRow("Grand Total", inv.totalAmountPaise, bold = true)
            DetailAmountRow("Paid", inv.paidAmountPaise, valueColor = FigmaTeal)
            DetailAmountRow("Balance Payable", inv.balanceDuePaise, bold = true, valueColor = if (inv.balanceDuePaise > 0L) FigmaRed else FigmaTeal)
          }
        }

        if (detail.returns.isNotEmpty()) {
          Text(text = "Purchase Returns (${detail.returns.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = FigmaInk)
          detail.returns.forEach { ret ->
            Text(
              text = "• ${ret.returnNumber}: ${SubledgerCalculationEngine.formatPaise(ret.totalReturnAmountPaise)} (${ret.settlementType.displayName}) — ${ret.reason}",
              fontSize = 11.sp,
              color = FigmaNavy
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
              .testTag("purchase_detail_share_btn")
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
                .testTag("purchase_detail_edit_btn")
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
                .testTag("purchase_detail_return_btn")
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
                .testTag("purchase_detail_cancel_btn")
            ) {
              Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Cancel Bill", fontSize = 12.sp)
            }
          }
          TextButton(
            onClick = onDismiss,
            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
          ) { Text("Close", color = FigmaMuted) }
        }
      }
    }
  )
}

private data class EditPurchaseLineState(
  val productId: Long,
  val productName: String,
  val batchNumber: String,
  val expiryDate: Long,
  val unitType: UnitType,
  val unitQuantityText: String,
  val costPerPieceText: String,
  val itemDiscountText: String,
  val minSoldOrConsumedPieces: Int,
  val productUnit: ProductUnit?
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditPurchaseInvoiceDialog(
  detail: PurchaseInvoiceDetail,
  suppliers: List<Supplier>,
  products: List<Product>,
  isSubmitting: Boolean,
  onGetProductUnit: suspend (Long) -> ProductUnit?,
  onDismiss: () -> Unit,
  onSubmit: (InvoiceLifecycleRepository.EditPurchaseInvoiceRequest, (String) -> Unit) -> Unit
) {
  val inv = detail.invoice
  val scope = rememberCoroutineScope()
  var selectedSupplierId by remember { mutableStateOf(inv.supplierId) }
  var partyNameSnapshot by remember { mutableStateOf(inv.partyNameSnapshot.ifBlank { detail.supplier?.name.orEmpty() }) }
  var partyPhoneSnapshot by remember { mutableStateOf(inv.partyPhoneSnapshot.ifBlank { detail.supplier?.phoneNumber.orEmpty() }) }
  var partyAddressSnapshot by remember { mutableStateOf(inv.partyAddressSnapshot.ifBlank { detail.supplier?.address.orEmpty() }) }
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
  var error by remember { mutableStateOf<String?>(null) }

  val unitCache = remember { mutableStateMapOf<Long, ProductUnit?>() }
  val editLines = remember { mutableStateListOf<EditPurchaseLineState>() }

  LaunchedEffect(inv.id) {
    detail.items.forEach { item ->
      val unit = onGetProductUnit(item.productId)
      unitCache[item.productId] = unit
      val soldMin = detail.soldQtyByBatchNumber[item.batchNumber] ?: 0
      editLines.add(
        EditPurchaseLineState(
          productId = item.productId,
          productName = item.productNameSnapshot.ifBlank { products.find { it.id == item.productId }?.name ?: "Product #${item.productId}" },
          batchNumber = item.batchNumber,
          expiryDate = item.expiryDate,
          unitType = UnitType.PIECE,
          unitQuantityText = item.quantityPieces.toString(),
          costPerPieceText = SubledgerCalculationEngine.formatPaiseForInput(item.costPerPiecePaise),
          itemDiscountText = SubledgerCalculationEngine.formatPaiseForInput(item.itemDiscountPaise),
          minSoldOrConsumedPieces = soldMin,
          productUnit = unit
        )
      )
    }
  }

  var addProductId by remember { mutableStateOf(products.firstOrNull()?.id ?: 0L) }

  fun computeLinePieces(line: EditPurchaseLineState): Int {
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
    val rate = SubledgerCalculationEngine.parseRupeesToPaiseOrNull(line.costPerPieceText, allowNegative = false, allowEmptyAsZero = true) ?: 0L
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
    advanceRetentionText = if (excessPaidPaise > 0L) SubledgerCalculationEngine.formatPaiseForInput(excessPaidPaise) else ""
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
    modifier = Modifier
      .fillMaxWidth(0.95f)
      .widthIn(max = 560.dp)
      .testTag("edit_purchase_dialog"),
    title = { Text("Edit Purchase #${inv.invoiceNumber}", fontWeight = FontWeight.Bold, color = FigmaInk) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text(text = "Supplier Account:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FigmaInk)
        suppliers.forEach { sup ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                selectedSupplierId = sup.id
                partyNameSnapshot = sup.name
                partyPhoneSnapshot = sup.phoneNumber
                partyAddressSnapshot = sup.address
              }
              .background(if (selectedSupplierId == sup.id) FigmaSoftTeal else FigmaWhite, RoundedCornerShape(6.dp))
              .padding(6.dp)
          ) {
            Text(sup.name, fontWeight = if (selectedSupplierId == sup.id) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp, color = FigmaInk)
          }
        }

        OutlinedTextField(value = partyNameSnapshot, onValueChange = { partyNameSnapshot = it }, label = { Text("Supplier Snapshot Name") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          IceTrackDatePickerField("Invoice Date", invoiceDate, { invoiceDate = it }, Modifier.weight(1f))
          IceTrackDatePickerField("Due Date", dueDate, { dueDate = it }, Modifier.weight(1f))
        }

        HorizontalDivider(color = FigmaLine)
        Text(text = "Batch Lines (${editLines.size}):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FigmaInk)

        editLines.forEachIndexed { idx, line ->
          val pcs = computeLinePieces(line)
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = FigmaWhite,
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, FigmaLine, RoundedCornerShape(8.dp))
          ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                  Text("${line.productName} [${line.batchNumber}] ($pcs pcs)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = FigmaInk)
                  if (line.minSoldOrConsumedPieces > 0) {
                    Text(
                      text = "Sold/Used: ${line.minSoldOrConsumedPieces} pcs (cannot drop below)",
                      fontSize = 10.sp,
                      color = FigmaAmber
                    )
                  }
                }
                if (editLines.size > 1 && line.minSoldOrConsumedPieces == 0) {
                  IconButton(onClick = { editLines.removeAt(idx) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove batch line", tint = FigmaRed, modifier = Modifier.size(16.dp))
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
                  value = line.costPerPieceText,
                  onValueChange = { editLines[idx] = line.copy(costPerPieceText = it) },
                  label = { Text("Cost ₹/pc") },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                  modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                  value = line.itemDiscountText,
                  onValueChange = { editLines[idx] = line.copy(itemDiscountText = it) },
                  label = { Text("Disc ₹") },
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
            ) { Text(picked.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp) }
            Button(
              onClick = {
                scope.launch {
                  val unit = onGetProductUnit(picked.id)
                  unitCache[picked.id] = unit
                  editLines.add(
                    EditPurchaseLineState(
                      productId = picked.id,
                      productName = picked.name,
                      batchNumber = "B-${(100..999).random()}",
                      expiryDate = System.currentTimeMillis() + 90L * 86400000L,
                      unitType = UnitType.PIECE,
                      unitQuantityText = "1",
                      costPerPieceText = SubledgerCalculationEngine.formatPaiseForInput(picked.defaultPurchasePricePaise),
                      itemDiscountText = "0",
                      minSoldOrConsumedPieces = 0,
                      productUnit = unit
                    )
                  )
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = FigmaNavy, contentColor = FigmaWhite)
            ) {
              Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Text("Add Batch", fontSize = 11.sp)
            }
          }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          DiscountType.values().forEach { dt ->
            FilterChip(selected = discountType == dt, onClick = { discountType = dt }, label = { Text(dt.displayName, fontSize = 11.sp) })
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
            DetailAmountRow("Updated Total", computedTotals.totalAmountPaise, bold = true)
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
              Text("Overpayment Split (${SubledgerCalculationEngine.formatPaise(excessPaidPaise)} excess)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = FigmaInk)
              OutlinedTextField(
                value = advanceRetentionText,
                onValueChange = { advanceRetentionText = it },
                label = { Text("Retain as Supplier Advance (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
              )
              Text("Recorded Refund Receivable Liability: ${SubledgerCalculationEngine.formatPaise(refundLiability)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FigmaRed)
              if (refundLiability > 0L) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  PaymentMethod.values().forEach { pm ->
                    FilterChip(selected = refundPaymentMethod == pm, onClick = { refundPaymentMethod = pm }, label = { Text(pm.displayName, fontSize = 10.sp) })
                  }
                }
              }
            }
          }
        }

        OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
          value = editReason,
          onValueChange = { editReason = it; error = null },
          label = { Text("Mandatory Edit Reason *") },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("purchase_edit_reason_input")
        )
        InvoiceErrorBanner(error, "purchase_edit_error")
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (editReason.isBlank()) {
            error = "Mandatory edit reason is required."
            return@Button
          }
          val reqLines = mutableListOf<InvoiceLifecycleRepository.EditPurchaseLineRequest>()
          for (l in editLines) {
            val pcs = computeLinePieces(l)
            val cost = SubledgerCalculationEngine.parseRupeesToPaiseOrNull(l.costPerPieceText, allowNegative = false, allowEmptyAsZero = false)
            val disc = SubledgerCalculationEngine.parseRupeesToPaiseOrNull(l.itemDiscountText, allowNegative = false, allowEmptyAsZero = true) ?: 0L
            if (pcs <= 0 || cost == null || cost < 0L) {
              error = "Each line item must have positive quantity and valid cost."
              return@Button
            }
            reqLines.add(
              InvoiceLifecycleRepository.EditPurchaseLineRequest(
                productId = l.productId,
                productNameSnapshot = l.productName,
                batchNumber = l.batchNumber,
                expiryDate = l.expiryDate,
                quantityPieces = pcs,
                costPerPiecePaise = cost,
                unitType = l.unitType,
                unitQuantity = l.unitQuantityText.trim().toIntOrNull() ?: pcs,
                unitRatePaise = cost,
                itemDiscountPaise = disc
              )
            )
          }
          val advRetention = if (excessPaidPaise > 0L) {
            val parsed = SubledgerCalculationEngine.parseRupeesToPaiseOrNull(advanceRetentionText, allowNegative = false, allowEmptyAsZero = true)
            if (parsed == null || parsed > excessPaidPaise) {
              error = "Advance retention must not exceed excess paid amount."
              return@Button
            }
            parsed
          } else null

          val req = InvoiceLifecycleRepository.EditPurchaseInvoiceRequest(
            invoiceId = inv.id,
            expectedRevisionVersion = inv.revisionVersion,
            supplierId = selectedSupplierId,
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
          onSubmit(req) { error = it }
        },
        enabled = !isSubmitting,
        colors = ButtonDefaults.buttonColors(containerColor = FigmaTeal, contentColor = FigmaWhite),
        modifier = Modifier.testTag("confirm_edit_purchase_btn")
      ) { Text(if (isSubmitting) "Updating..." else "Save Changes") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = FigmaMuted) } }
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PurchaseReturnDialog(
  detail: PurchaseInvoiceDetail,
  isSubmitting: Boolean,
  onDismiss: () -> Unit,
  onSubmit: (List<ReturnRepository.RequestedPurchaseReturnLine>, ReturnSettlementType, PaymentMethod, String, String, (String) -> Unit) -> Unit
) {
  val qtyByItemId = remember { mutableStateMapOf<Long, String>() }
  val condByItemId = remember { mutableStateMapOf<Long, ReturnCondition>() }
  var settlement by remember { mutableStateOf(ReturnSettlementType.DEBIT_NOTE) }
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
      .testTag("purchase_return_dialog"),
    title = { Text("Purchase Return • #${detail.invoice.invoiceNumber}", fontWeight = FontWeight.Bold, color = FigmaInk) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        detail.items.forEach { item ->
          val liveStock = detail.batches.find { it.productId == item.productId && it.batchNumber == item.batchNumber }?.currentQuantityPieces ?: 0
          val prevRet = detail.returnedQtyByProductId[item.productId] ?: 0
          val maxRet = kotlin.math.min(liveStock, (item.quantityPieces - prevRet).coerceAtLeast(0))
          val cond = condByItemId[item.id] ?: ReturnCondition.DAMAGED
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = FigmaWhite,
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, FigmaLine, RoundedCornerShape(8.dp))
          ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(
                text = "${item.productNameSnapshot.ifBlank { "Product #${item.productId}" }} [${item.batchNumber}] (Available to return: $maxRet pcs)",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = FigmaInk
              )
              OutlinedTextField(
                value = qtyByItemId[item.id] ?: "",
                onValueChange = { qtyByItemId[item.id] = it },
                label = { Text("Return Pieces (0 to $maxRet)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
              )
              FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReturnCondition.values().forEach { rc ->
                  FilterChip(selected = cond == rc, onClick = { condByItemId[item.id] = rc }, label = { Text(rc.displayName, fontSize = 10.sp) })
                }
              }
            }
          }
        }

        Text(text = "Settlement Mode:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = FigmaInk)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(ReturnSettlementType.DEBIT_NOTE, ReturnSettlementType.ADVANCE, ReturnSettlementType.CASH_REFUND).forEach { st ->
            FilterChip(selected = settlement == st, onClick = { settlement = st }, label = { Text(st.displayName, fontSize = 11.sp) })
          }
        }
        if (settlement == ReturnSettlementType.CASH_REFUND) {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PaymentMethod.values().forEach { pm ->
              FilterChip(selected = refundMethod == pm, onClick = { refundMethod = pm }, label = { Text(pm.displayName, fontSize = 10.sp) })
            }
          }
        }

        OutlinedTextField(value = reason, onValueChange = { reason = it; error = null }, label = { Text("Mandatory Return Reason *") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes (Optional)") }, modifier = Modifier.fillMaxWidth())
        InvoiceErrorBanner(error, "purchase_return_error")
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
              ReturnRepository.RequestedPurchaseReturnLine(
                purchaseInvoiceItemId = item.id,
                quantityPieces = q,
                condition = condByItemId[item.id] ?: ReturnCondition.DAMAGED
              )
            } else null
          }
          if (lines.isEmpty()) {
            error = "Enter a positive return quantity for at least one batch."
            return@Button
          }
          onSubmit(lines, settlement, refundMethod, reason, notes) { error = it }
        },
        enabled = !isSubmitting,
        colors = ButtonDefaults.buttonColors(containerColor = FigmaTeal, contentColor = FigmaWhite),
        modifier = Modifier.testTag("confirm_purchase_return_btn")
      ) { Text(if (isSubmitting) "Processing..." else "Confirm Return") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = FigmaMuted) } }
  )
}
