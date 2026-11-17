package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import com.example.data.repository.SubledgerRepository
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.model.AdvanceReversalStrategy
import com.example.domain.model.PartyType
import com.example.domain.model.SubledgerEntry
import com.example.domain.model.SubledgerEntryType
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
import com.example.ui.viewmodel.FinanceLifecycleViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubledgerScreen(
  subledgerRepository: SubledgerRepository,
  modifier: Modifier = Modifier,
  filteredCustomerId: Long? = null,
  filteredCustomerName: String? = null,
  onClearCustomerFilter: (() -> Unit)? = null,
  financeViewModel: FinanceLifecycleViewModel? = null,
  onOpenNotesAndAudit: (() -> Unit)? = null
) {
  val entries by subledgerRepository.allEntries.collectAsState(initial = emptyList())
  var selectedFilter by remember(filteredCustomerId) {
    mutableStateOf(if (filteredCustomerId != null) "CUSTOMER" else "ALL")
  }
  var entryToReverse by remember { mutableStateOf<SubledgerEntry?>(null) }
  var reverseError by remember { mutableStateOf<String?>(null) }
  var reverseReason by remember { mutableStateOf("") }
  var reverseStrategy by remember { mutableStateOf(AdvanceReversalStrategy.BLOCK_IF_CONSUMED) }
  val isSubmitting = financeViewModel?.uiState?.collectAsState()?.value?.isSubmitting ?: false

  val filteredEntries = entries.filter { entry ->
    if (filteredCustomerId != null) {
      entry.partyType == PartyType.CUSTOMER && entry.partyId == filteredCustomerId
    } else {
      when (selectedFilter) {
        "CUSTOMER" -> entry.partyType == PartyType.CUSTOMER
        "SUPPLIER" -> entry.partyType == PartyType.SUPPLIER
        else -> true
      }
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(FigmaBackground)
      .padding(horizontal = 24.dp, vertical = 16.dp)
      .testTag("subledger_screen")
  ) {
    // Header explanation card (White, Line border)
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = FigmaWhite,
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, FigmaLine, RoundedCornerShape(12.dp))
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Text(
          text = "Immutable Transaction Subledger",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = FigmaInk
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = "Customer receivables and supplier payables are derived dynamically from these records. No arbitrary balance modifications permitted.",
          style = MaterialTheme.typography.bodySmall,
          color = FigmaMuted
        )
        if (onOpenNotesAndAudit != null) {
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedButton(
            onClick = onOpenNotesAndAudit,
            modifier = Modifier
              .fillMaxWidth()
              .defaultMinSize(minHeight = 48.dp)
              .testTag("open_notes_refunds_audit_btn")
          ) {
            Text("Credit/Debit Notes, Refunds & Audit Log", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FigmaNavy)
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    if (filteredCustomerId != null) {
      val customerBalance = SubledgerCalculationEngine.calculateCustomerBalance(filteredEntries.filter { !it.isReversed })
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = FigmaSoftTeal,
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, FigmaTeal, RoundedCornerShape(10.dp))
          .testTag("subledger_customer_filter_banner")
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Customer Ledger: ${filteredCustomerName ?: "Customer #$filteredCustomerId"}",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              color = FigmaInk
            )
            Text(
              text = "Live SSOT Outstanding: ${SubledgerCalculationEngine.formatPaise(customerBalance)} • ${filteredEntries.size} entries",
              style = MaterialTheme.typography.labelSmall,
              color = FigmaTeal
            )
          }
          if (onClearCustomerFilter != null) {
            androidx.compose.material3.TextButton(
              onClick = onClearCustomerFilter,
              modifier = Modifier.testTag("clear_subledger_customer_filter_btn")
            ) {
              Text("Show All", color = FigmaNavy, fontWeight = FontWeight.SemiBold)
            }
          }
        }
      }
      Spacer(modifier = Modifier.height(12.dp))
    } else {
      // Filter Chips (Soft Teal container for active)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FilterChip(
        selected = selectedFilter == "ALL",
        onClick = { selectedFilter = "ALL" },
        label = { Text("All Records (${entries.size})") },
        colors = FilterChipDefaults.filterChipColors(
          selectedContainerColor = FigmaSoftTeal,
          selectedLabelColor = FigmaTeal,
          containerColor = FigmaWhite,
          labelColor = FigmaInk
        ),
        border = FilterChipDefaults.filterChipBorder(
          borderColor = FigmaLine,
          selectedBorderColor = FigmaTeal,
          enabled = true,
          selected = selectedFilter == "ALL"
        )
      )
      FilterChip(
        selected = selectedFilter == "CUSTOMER",
        onClick = { selectedFilter = "CUSTOMER" },
        label = { Text("Shop AR") },
        colors = FilterChipDefaults.filterChipColors(
          selectedContainerColor = FigmaSoftTeal,
          selectedLabelColor = FigmaTeal,
          containerColor = FigmaWhite,
          labelColor = FigmaInk
        ),
        border = FilterChipDefaults.filterChipBorder(
          borderColor = FigmaLine,
          selectedBorderColor = FigmaTeal,
          enabled = true,
          selected = selectedFilter == "CUSTOMER"
        )
      )
      FilterChip(
        selected = selectedFilter == "SUPPLIER",
        onClick = { selectedFilter = "SUPPLIER" },
        label = { Text("Supplier AP") },
        colors = FilterChipDefaults.filterChipColors(
          selectedContainerColor = FigmaSoftTeal,
          selectedLabelColor = FigmaTeal,
          containerColor = FigmaWhite,
          labelColor = FigmaInk
        ),
        border = FilterChipDefaults.filterChipBorder(
          borderColor = FigmaLine,
          selectedBorderColor = FigmaTeal,
          enabled = true,
          selected = selectedFilter == "SUPPLIER"
        )
      )
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    if (filteredEntries.isEmpty()) {
      EmptyStateView(
        icon = Icons.Default.AccountBalanceWallet,
        title = "No Subledger Transactions Yet",
        message = "Opening balances and transactions will be recorded here immutably as you add shops and suppliers."
      )
    } else {
      LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
          .fillMaxSize()
          .testTag("subledger_entry_list")
      ) {
        items(filteredEntries, key = { it.id }) { entry ->
          SubledgerEntryCard(
            entry = entry,
            canReversePayment = financeViewModel != null &&
              entry.entryType == SubledgerEntryType.PAYMENT &&
              !entry.isReversed,
            onReverseClick = {
              reverseReason = ""
              reverseError = null
              reverseStrategy = AdvanceReversalStrategy.BLOCK_IF_CONSUMED
              entryToReverse = entry
            }
          )
        }
      }
    }
  }

  entryToReverse?.let { target ->
    AlertDialog(
      onDismissRequest = { entryToReverse = null },
      title = { Text("Reverse Payment #${target.id}", fontWeight = FontWeight.Bold, color = FigmaRed) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          val amtPaise = maxOf(target.debitPaise, target.creditPaise)
          Text(
            text = "${if (target.partyType == PartyType.CUSTOMER) "Shop Receipt" else "Supplier Payment"}: ${SubledgerCalculationEngine.formatPaise(amtPaise)}",
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = FigmaInk
          )
          Text("If part of this payment became Advance Credit that was later used:", fontSize = 11.sp, color = FigmaMuted)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AdvanceReversalStrategy.values().forEach { strategy ->
              FilterChip(
                selected = reverseStrategy == strategy,
                onClick = { reverseStrategy = strategy },
                label = { Text(strategy.displayName, fontSize = 10.sp) }
              )
            }
          }
          OutlinedTextField(
            value = reverseReason,
            onValueChange = { reverseReason = it; reverseError = null },
            label = { Text("Mandatory Reversal Reason *") },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("reverse_payment_reason_input")
          )
          InvoiceErrorBanner(reverseError, "reverse_payment_error")
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (reverseReason.isBlank()) {
              reverseError = "Mandatory reversal reason is required."
              return@Button
            }
            financeViewModel?.reversePayment(
              entryId = target.id,
              reason = reverseReason,
              strategy = reverseStrategy,
              onSuccess = { entryToReverse = null },
              onError = { reverseError = it }
            )
          },
          enabled = !isSubmitting,
          colors = ButtonDefaults.buttonColors(containerColor = FigmaRed, contentColor = FigmaWhite),
          modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag("confirm_reverse_payment_btn")
        ) {
          Text(if (isSubmitting) "Reversing..." else "Reverse Payment")
        }
      },
      dismissButton = {
        TextButton(onClick = { entryToReverse = null }) {
          Text("Cancel", color = FigmaMuted)
        }
      }
    )
  }
}

@Composable
fun SubledgerEntryCard(
  entry: SubledgerEntry,
  canReversePayment: Boolean = false,
  onReverseClick: () -> Unit = {}
) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = if (entry.isReversed) FigmaBackground else FigmaWhite),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, if (entry.isReversed) FigmaRed.copy(alpha = 0.35f) else FigmaLine, RoundedCornerShape(12.dp))
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (entry.isReversed) FigmaRed.copy(alpha = 0.12f) else FigmaSoftTeal
          ) {
            Text(
              text = if (entry.isReversed) "REVERSED" else if (entry.partyType == PartyType.CUSTOMER) "SHOP AR" else "SUPPLIER AP",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = if (entry.isReversed) FigmaRed else FigmaTeal,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }

          Spacer(modifier = Modifier.padding(4.dp))

          Text(
            text = entry.entryType.displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (entry.isReversed) FigmaMuted else FigmaInk
          )
        }

        Text(
          text = SubledgerCalculationEngine.formatDate(entry.entryDate),
          style = MaterialTheme.typography.labelSmall,
          color = FigmaMuted
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
          Text(
            text = if (entry.notes.isNotBlank()) entry.notes else "Ref: ${entry.referenceType} #${entry.referenceId}",
            style = MaterialTheme.typography.bodySmall,
            color = FigmaMuted
          )
          if (entry.isReversed && !entry.reversalReason.isNullOrBlank()) {
            Text(
              text = "Reversed: ${entry.reversalReason}",
              style = MaterialTheme.typography.labelSmall,
              color = FigmaRed
            )
          }
        }

        Row {
          if (entry.debitPaise > 0L) {
            Column(horizontalAlignment = Alignment.End) {
              Text(
                text = "Debit",
                style = MaterialTheme.typography.labelSmall,
                color = FigmaMuted
              )
              CurrencyText(paise = entry.debitPaise, fontSize = 14)
            }
          }
          if (entry.creditPaise > 0L) {
            Column(horizontalAlignment = Alignment.End) {
              Text(
                text = "Credit",
                style = MaterialTheme.typography.labelSmall,
                color = FigmaMuted
              )
              CurrencyText(paise = entry.creditPaise, fontSize = 14, isReceivable = false)
            }
          }
        }
      }

      if (canReversePayment) {
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
          onClick = onReverseClick,
          modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .testTag("reverse_payment_btn_${entry.id}")
        ) {
          Text("Reverse Payment", fontSize = 12.sp, color = FigmaRed, fontWeight = FontWeight.SemiBold)
        }
      }
    }
  }
}
