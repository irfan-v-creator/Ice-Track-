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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.model.AuditOperationType
import com.example.domain.model.CreditDebitNote
import com.example.domain.model.CreditDebitNoteType
import com.example.domain.model.Customer
import com.example.domain.model.FinancialAuditLog
import com.example.domain.model.NoteStatus
import com.example.domain.model.PartyType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PurchaseInvoice
import com.example.domain.model.RefundExecutionStatus
import com.example.domain.model.RefundRecord
import com.example.domain.model.SalesInvoice
import com.example.domain.model.Supplier
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
import com.example.ui.viewmodel.FinanceLifecycleUiState
import com.example.ui.viewmodel.FinanceLifecycleViewModel

@Composable
fun CreditDebitNotesAuditScreen(
  viewModel: FinanceLifecycleViewModel,
  modifier: Modifier = Modifier
) {
  val state by viewModel.uiState.collectAsState()
  var selectedTab by remember { mutableIntStateOf(0) }
  var showCreateNoteDialog by remember { mutableStateOf(false) }
  var noteForRefund by remember { mutableStateOf<CreditDebitNote?>(null) }
  var noteForCancel by remember { mutableStateOf<CreditDebitNote?>(null) }
  var refundForPayout by remember { mutableStateOf<RefundRecord?>(null) }
  var selectedAuditFilter by remember { mutableStateOf<AuditOperationType?>(null) }
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(state.statusMessage) {
    state.statusMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.clearStatusMessage()
    }
  }

  val customerNames = remember(state.customers) { state.customers.associate { it.id to it.shopName } }
  val supplierNames = remember(state.suppliers) { state.suppliers.associate { it.id to it.name } }

  Scaffold(
    containerColor = FigmaBackground,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    floatingActionButton = {
      if (selectedTab == 0) {
        FloatingActionButton(
          onClick = { showCreateNoteDialog = true },
          containerColor = FigmaTeal,
          contentColor = FigmaWhite,
          modifier = Modifier.testTag("issue_note_fab")
        ) {
          Icon(Icons.Default.Add, contentDescription = "Issue Credit or Debit Note")
        }
      }
    },
    modifier = modifier.fillMaxSize()
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .testTag("notes_refunds_audit_screen")
    ) {
      TabRow(selectedTabIndex = selectedTab, containerColor = FigmaWhite, contentColor = FigmaTeal) {
        Tab(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          text = { Text("Notes (${state.notes.size})", fontSize = 12.sp) },
          modifier = Modifier.testTag("tab_notes")
        )
        Tab(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          text = { Text("Refunds (${state.refunds.size})", fontSize = 12.sp) },
          modifier = Modifier.testTag("tab_refunds")
        )
        Tab(
          selected = selectedTab == 2,
          onClick = { selectedTab = 2 },
          text = { Text("Audit Log (${state.auditLogs.size})", fontSize = 12.sp) },
          modifier = Modifier.testTag("tab_audit_log")
        )
      }

      when (selectedTab) {
        0 -> NotesTabList(
          notes = state.notes,
          customerNames = customerNames,
          supplierNames = supplierNames,
          onApplyToInvoices = { viewModel.applyNoteToInvoices(it.id) },
          onConvertToRefund = { noteForRefund = it },
          onCancelNote = { noteForCancel = it }
        )
        1 -> RefundsTabList(
          refunds = state.refunds,
          customerNames = customerNames,
          supplierNames = supplierNames,
          onExecutePayout = { refundForPayout = it }
        )
        else -> AuditTabList(
          logs = state.auditLogs,
          selectedFilter = selectedAuditFilter,
          onSelectFilter = { selectedAuditFilter = it },
          customerNames = customerNames,
          supplierNames = supplierNames
        )
      }
    }
  }

  if (showCreateNoteDialog) {
    CreateCreditDebitNoteDialog(
      state = state,
      onDismiss = { showCreateNoteDialog = false },
      onSubmit = { type, partyType, partyId, amt, invId, reason, notes, applyToDues, onErr ->
        viewModel.createCreditDebitNote(
          noteType = type,
          partyType = partyType,
          partyId = partyId,
          amountPaise = amt,
          linkedInvoiceId = invId,
          reason = reason,
          notes = notes,
          applyToDues = applyToDues,
          onSuccess = { showCreateNoteDialog = false },
          onError = onErr
        )
      }
    )
  }

  noteForRefund?.let { note ->
    ReasonMethodActionDialog(
      title = "Convert Note #${note.noteNumber} to Refund",
      subtitle = "Remaining ${SubledgerCalculationEngine.formatPaise(note.remainingAmountPaise)} will create a recorded refund settlement.",
      confirmLabel = "Convert to Refund",
      isBusy = state.isSubmitting,
      showMethod = true,
      onDismiss = { noteForRefund = null },
      onConfirm = { method, _, reason, onErr ->
        viewModel.settleNoteAsRefund(note.id, method, reason, onSuccess = { noteForRefund = null }, onError = onErr)
      }
    )
  }

  noteForCancel?.let { note ->
    ReasonMethodActionDialog(
      title = "Cancel Unused Note #${note.noteNumber}",
      subtitle = "Amount: ${SubledgerCalculationEngine.formatPaise(note.amountPaise)}. Only unapplied notes can be cancelled.",
      confirmLabel = "Cancel Note",
      isBusy = state.isSubmitting,
      showMethod = false,
      onDismiss = { noteForCancel = null },
      onConfirm = { _, _, reason, onErr ->
        viewModel.cancelUnusedNote(note.id, reason, onSuccess = { noteForCancel = null }, onError = onErr)
      }
    )
  }

  refundForPayout?.let { refund ->
    ReasonMethodActionDialog(
      title = "Execute Refund Payout (#${refund.id})",
      subtitle = "Recorded Liability: ${SubledgerCalculationEngine.formatPaise(refund.amountPaise)}. Marks payout executed and reconciles subledger.",
      confirmLabel = "Confirm Payout",
      isBusy = state.isSubmitting,
      showMethod = true,
      showReferenceField = true,
      defaultMethod = refund.paymentMethod,
      onDismiss = { refundForPayout = null },
      onConfirm = { method, refNo, reason, onErr ->
        viewModel.executeRefundPayout(refund.id, method, refNo, reason, onSuccess = { refundForPayout = null }, onError = onErr)
      }
    )
  }
}

@Composable
private fun NotesTabList(
  notes: List<CreditDebitNote>,
  customerNames: Map<Long, String>,
  supplierNames: Map<Long, String>,
  onApplyToInvoices: (CreditDebitNote) -> Unit,
  onConvertToRefund: (CreditDebitNote) -> Unit,
  onCancelNote: (CreditDebitNote) -> Unit
) {
  if (notes.isEmpty()) {
    EmptyStateView(
      icon = Icons.Default.NoteAlt,
      title = "No Credit or Debit Notes",
      message = "Tap '+' to issue a Credit/Debit Note, or process a Sales/Purchase return with Note settlement."
    )
    return
  }
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp, vertical = 12.dp)
      .testTag("notes_list"),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    items(notes, key = { it.id }) { note ->
      val partyName = if (note.partyType == PartyType.CUSTOMER) {
        customerNames[note.partyId] ?: "Shop #${note.partyId}"
      } else {
        supplierNames[note.partyId] ?: "Supplier #${note.partyId}"
      }
      val hasRemaining = note.remainingAmountPaise > 0L && note.status != NoteStatus.CANCELLED
      Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = FigmaWhite),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, FigmaLine, RoundedCornerShape(12.dp))
          .testTag("note_card_${note.id}")
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
              Text("${note.noteNumber} • ${note.noteType.displayName}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = FigmaInk)
              Text(
                "$partyName · ${SubledgerCalculationEngine.formatDate(note.issueDate)}",
                fontSize = 12.sp,
                color = FigmaMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = when (note.status) {
                NoteStatus.CANCELLED -> FigmaRed.copy(alpha = 0.12f)
                NoteStatus.SETTLED -> FigmaSoftTeal
                else -> FigmaAmber.copy(alpha = 0.14f)
              }
            ) {
              Text(
                text = note.status.displayName,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = when (note.status) {
                  NoteStatus.CANCELLED -> FigmaRed
                  NoteStatus.SETTLED -> FigmaTeal
                  else -> FigmaAmber
                },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }
          Text("Reason: ${note.reason}", fontSize = 12.sp, color = FigmaInk)
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total: ${SubledgerCalculationEngine.formatPaise(note.amountPaise)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FigmaInk)
            Text("Remaining: ${SubledgerCalculationEngine.formatPaise(note.remainingAmountPaise)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FigmaTeal)
          }
          if (hasRemaining) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(
                onClick = { onApplyToInvoices(note) },
                modifier = Modifier
                  .weight(1f)
                  .defaultMinSize(minHeight = 48.dp)
                  .testTag("apply_note_btn_${note.id}")
              ) { Text("Apply Dues", fontSize = 11.sp) }
              OutlinedButton(
                onClick = { onConvertToRefund(note) },
                modifier = Modifier
                  .weight(1f)
                  .defaultMinSize(minHeight = 48.dp)
                  .testTag("refund_note_btn_${note.id}")
              ) { Text("To Refund", fontSize = 11.sp) }
              if (note.appliedAmountPaise == 0L) {
                OutlinedButton(
                  onClick = { onCancelNote(note) },
                  modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("cancel_note_btn_${note.id}")
                ) { Text("Cancel", fontSize = 11.sp, color = FigmaRed) }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RefundsTabList(
  refunds: List<RefundRecord>,
  customerNames: Map<Long, String>,
  supplierNames: Map<Long, String>,
  onExecutePayout: (RefundRecord) -> Unit
) {
  if (refunds.isEmpty()) {
    EmptyStateView(
      icon = Icons.Default.Payments,
      title = "No Refunds Recorded",
      message = "Overpayment liabilities, return refunds, and settled notes appear here with payout status."
    )
    return
  }
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp, vertical = 12.dp)
      .testTag("refunds_list"),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    items(refunds, key = { it.id }) { ref ->
      val partyName = if (ref.partyType == PartyType.CUSTOMER) {
        customerNames[ref.partyId] ?: "Shop #${ref.partyId}"
      } else {
        supplierNames[ref.partyId] ?: "Supplier #${ref.partyId}"
      }
      val isPending = ref.executionStatus == RefundExecutionStatus.RECORDED_PENDING_PAYOUT
      Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = FigmaWhite),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, FigmaLine, RoundedCornerShape(12.dp))
          .testTag("refund_card_${ref.id}")
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
              Text("$partyName • ${ref.sourceType.displayName}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FigmaInk)
              Text("${ref.paymentMethod.displayName} · ${SubledgerCalculationEngine.formatDate(ref.recordedAt)}", fontSize = 11.sp, color = FigmaMuted)
            }
            Text(SubledgerCalculationEngine.formatPaise(ref.amountPaise), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = FigmaNavy)
          }
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isPending) FigmaAmber.copy(alpha = 0.14f) else FigmaSoftTeal
          ) {
            Text(
              text = ref.executionStatus.displayName,
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              color = if (isPending) FigmaAmber else FigmaTeal,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }
          Text("Reason: ${ref.reason}", fontSize = 12.sp, color = FigmaInk)
          if (ref.referenceNumber.isNotBlank()) {
            Text("Ref: ${ref.referenceNumber}", fontSize = 11.sp, color = FigmaMuted)
          }
          if (isPending) {
            Button(
              onClick = { onExecutePayout(ref) },
              colors = ButtonDefaults.buttonColors(containerColor = FigmaTeal, contentColor = FigmaWhite),
              modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .testTag("execute_refund_btn_${ref.id}")
            ) {
              Text("Execute Payout / Mark Paid")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AuditTabList(
  logs: List<FinancialAuditLog>,
  selectedFilter: AuditOperationType?,
  onSelectFilter: (AuditOperationType?) -> Unit,
  customerNames: Map<Long, String>,
  supplierNames: Map<Long, String>
) {
  val filteredLogs = remember(logs, selectedFilter) {
    if (selectedFilter == null) logs else logs.filter { it.operationType == selectedFilter }
  }
  val filterOptions = listOf(
    null to "All",
    AuditOperationType.INVOICE_EDIT to "Edits",
    AuditOperationType.INVOICE_CANCEL to "Cancellations",
    AuditOperationType.PAYMENT_REVERSAL to "Reversals",
    AuditOperationType.SALES_RETURN to "Sales Ret.",
    AuditOperationType.PURCHASE_RETURN to "Purchase Ret.",
    AuditOperationType.CREDIT_NOTE to "Credit Notes",
    AuditOperationType.DEBIT_NOTE to "Debit Notes",
    AuditOperationType.OPENING_BALANCE_EDIT to "Opening Bal.",
    AuditOperationType.REFUND_PAYOUT to "Refunds",
    AuditOperationType.BACKUP_RESTORE to "Restore"
  )

  Column(modifier = Modifier.fillMaxSize()) {
    LazyRow(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(filterOptions) { (opType, label) ->
        FilterChip(
          selected = selectedFilter == opType,
          onClick = { onSelectFilter(opType) },
          label = { Text(label, fontSize = 11.sp) }
        )
      }
    }
    if (filteredLogs.isEmpty()) {
      EmptyStateView(
        icon = Icons.Default.History,
        title = "No Audit Entries",
        message = "Permanent audit entries appear automatically whenever invoices, payments, returns, notes, or balances change."
      )
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 20.dp, vertical = 6.dp)
          .testTag("audit_log_list"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(filteredLogs, key = { it.id }) { log ->
          val partyLabel = when (log.relatedPartyType) {
            PartyType.CUSTOMER -> log.relatedPartyId?.let { customerNames[it] ?: "Shop #$it" }
            PartyType.SUPPLIER -> log.relatedPartyId?.let { supplierNames[it] ?: "Supplier #$it" }
            null -> null
          }
          Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = FigmaWhite),
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, FigmaLine, RoundedCornerShape(10.dp))
          ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Surface(shape = RoundedCornerShape(4.dp), color = FigmaSoftTeal) {
                  Text(
                    text = log.operationType.displayName,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = FigmaTeal,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
                Text(SubledgerCalculationEngine.formatDate(log.createdAt), fontSize = 10.sp, color = FigmaMuted)
              }
              Text(
                text = "${log.operationReference.ifBlank { "${log.entityType} #${log.entityId}" }}${partyLabel?.let { " • $it" } ?: ""}",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = FigmaInk
              )
              Text("Reason: ${log.reason}", fontSize = 11.sp, color = FigmaInk)
              if (log.beforeJson.isNotBlank() || log.afterJson.isNotBlank()) {
                Text(
                  text = "Snapshot: ${log.afterJson.ifBlank { log.beforeJson }}",
                  fontSize = 10.sp,
                  color = FigmaMuted,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateCreditDebitNoteDialog(
  state: FinanceLifecycleUiState,
  onDismiss: () -> Unit,
  onSubmit: (CreditDebitNoteType, PartyType, Long, Long, Long?, String, String, Boolean, (String) -> Unit) -> Unit
) {
  var partyType by remember { mutableStateOf(PartyType.CUSTOMER) }
  var noteType by remember { mutableStateOf(CreditDebitNoteType.CREDIT_NOTE) }
  var selectedPartyId by remember(partyType) {
    mutableStateOf(
      if (partyType == PartyType.CUSTOMER) state.customers.firstOrNull()?.id ?: 0L
      else state.suppliers.firstOrNull()?.id ?: 0L
    )
  }
  var linkedInvoiceId by remember(partyType, selectedPartyId) { mutableStateOf<Long?>(null) }
  var amountText by remember { mutableStateOf("") }
  var applyToDues by remember { mutableStateOf(true) }
  var reason by remember { mutableStateOf("") }
  var notes by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
    modifier = Modifier
      .fillMaxWidth(0.94f)
      .widthIn(max = 520.dp)
      .testTag("create_note_dialog"),
    title = { Text("Issue Credit / Debit Note", fontWeight = FontWeight.Bold, color = FigmaInk) },
    text = {
      Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          FilterChip(selected = partyType == PartyType.CUSTOMER, onClick = { partyType = PartyType.CUSTOMER }, label = { Text("Customer") })
          FilterChip(selected = partyType == PartyType.SUPPLIER, onClick = { partyType = PartyType.SUPPLIER }, label = { Text("Supplier") })
          FilterChip(selected = noteType == CreditDebitNoteType.CREDIT_NOTE, onClick = { noteType = CreditDebitNoteType.CREDIT_NOTE }, label = { Text("Credit Note") })
          FilterChip(selected = noteType == CreditDebitNoteType.DEBIT_NOTE, onClick = { noteType = CreditDebitNoteType.DEBIT_NOTE }, label = { Text("Debit Note") })
        }
        Text("Select ${if (partyType == PartyType.CUSTOMER) "Shop" else "Supplier"}:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FigmaInk)
        if (partyType == PartyType.CUSTOMER) {
          state.customers.forEach { c ->
            Text(
              text = c.shopName,
              fontSize = 12.sp,
              fontWeight = if (selectedPartyId == c.id) FontWeight.Bold else FontWeight.Normal,
              modifier = Modifier
                .fillMaxWidth()
                .background(if (selectedPartyId == c.id) FigmaSoftTeal else FigmaWhite, RoundedCornerShape(6.dp))
                .clickable { selectedPartyId = c.id }
                .padding(8.dp)
            )
          }
        } else {
          state.suppliers.forEach { s ->
            Text(
              text = s.name,
              fontSize = 12.sp,
              fontWeight = if (selectedPartyId == s.id) FontWeight.Bold else FontWeight.Normal,
              modifier = Modifier
                .fillMaxWidth()
                .background(if (selectedPartyId == s.id) FigmaSoftTeal else FigmaWhite, RoundedCornerShape(6.dp))
                .clickable { selectedPartyId = s.id }
                .padding(8.dp)
            )
          }
        }

        val matchingInvoices = if (partyType == PartyType.CUSTOMER) {
          state.salesInvoices.filter { it.customerId == selectedPartyId }.map { it.id to "#${it.invoiceNumber} (${SubledgerCalculationEngine.formatPaise(it.totalAmountPaise)})" }
        } else {
          state.purchaseInvoices.filter { it.supplierId == selectedPartyId }.map { it.id to "#${it.invoiceNumber} (${SubledgerCalculationEngine.formatPaise(it.totalAmountPaise)})" }
        }
        if (matchingInvoices.isNotEmpty()) {
          Text("Link Invoice (Optional):", fontSize = 11.sp, color = FigmaMuted)
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
              FilterChip(selected = linkedInvoiceId == null, onClick = { linkedInvoiceId = null }, label = { Text("None", fontSize = 10.sp) })
            }
            items(matchingInvoices) { (id, label) ->
              FilterChip(selected = linkedInvoiceId == id, onClick = { linkedInvoiceId = id }, label = { Text(label, fontSize = 10.sp) })
            }
          }
        }

        OutlinedTextField(
          value = amountText,
          onValueChange = { amountText = it; error = null },
          label = { Text("Amount (₹) *") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("note_amount_input")
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { applyToDues = !applyToDues }) {
          Checkbox(checked = applyToDues, onCheckedChange = { applyToDues = it })
          Text("Auto-apply against open invoice dues if reducing balance", fontSize = 12.sp, color = FigmaInk)
        }
        OutlinedTextField(
          value = reason,
          onValueChange = { reason = it; error = null },
          label = { Text("Mandatory Reason *") },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("note_reason_input")
        )
        OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes (Optional)") }, modifier = Modifier.fillMaxWidth())
        InvoiceErrorBanner(error, "create_note_error")
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val paise = SubledgerCalculationEngine.parseRupeesToPaiseOrNull(amountText)
          if (selectedPartyId == 0L || paise == null || paise <= 0L) {
            error = "Select a valid party and positive amount."
            return@Button
          }
          if (reason.isBlank()) {
            error = "Mandatory reason is required."
            return@Button
          }
          onSubmit(noteType, partyType, selectedPartyId, paise, linkedInvoiceId, reason, notes, applyToDues) { error = it }
        },
        enabled = !state.isSubmitting,
        colors = ButtonDefaults.buttonColors(containerColor = FigmaTeal, contentColor = FigmaWhite),
        modifier = Modifier
          .defaultMinSize(minHeight = 48.dp)
          .testTag("submit_note_btn")
      ) {
        Text(if (state.isSubmitting) "Saving..." else "Issue Note")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = FigmaMuted) } }
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReasonMethodActionDialog(
  title: String,
  subtitle: String,
  confirmLabel: String,
  isBusy: Boolean,
  showMethod: Boolean,
  showReferenceField: Boolean = false,
  defaultMethod: PaymentMethod = PaymentMethod.CASH,
  onDismiss: () -> Unit,
  onConfirm: (PaymentMethod, String, String, (String) -> Unit) -> Unit
) {
  var method by remember { mutableStateOf(defaultMethod) }
  var refNo by remember { mutableStateOf("") }
  var reason by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title, fontWeight = FontWeight.Bold, color = FigmaInk) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(subtitle, fontSize = 12.sp, color = FigmaMuted)
        if (showMethod) {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PaymentMethod.values().forEach { pm ->
              FilterChip(selected = method == pm, onClick = { method = pm }, label = { Text(pm.displayName, fontSize = 10.sp) })
            }
          }
        }
        if (showReferenceField) {
          OutlinedTextField(value = refNo, onValueChange = { refNo = it }, label = { Text("Reference / Transaction #") }, modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(
          value = reason,
          onValueChange = { reason = it; error = null },
          label = { Text("Mandatory Reason *") },
          modifier = Modifier.fillMaxWidth()
        )
        InvoiceErrorBanner(error, "action_dialog_error")
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (reason.isBlank()) {
            error = "Mandatory reason is required."
            return@Button
          }
          onConfirm(method, refNo, reason) { error = it }
        },
        enabled = !isBusy,
        colors = ButtonDefaults.buttonColors(containerColor = FigmaTeal, contentColor = FigmaWhite),
        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
      ) { Text(if (isBusy) "Saving..." else confirmLabel) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = FigmaMuted) } }
  )
}
