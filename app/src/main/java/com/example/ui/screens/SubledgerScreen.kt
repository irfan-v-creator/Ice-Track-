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
import com.example.data.repository.SubledgerRepository
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.model.PartyType
import com.example.domain.model.SubledgerEntry
import com.example.ui.components.CurrencyText
import com.example.ui.components.EmptyStateView
import com.example.ui.theme.FigmaAmber
import com.example.ui.theme.FigmaBackground
import com.example.ui.theme.FigmaInk
import com.example.ui.theme.FigmaLine
import com.example.ui.theme.FigmaMuted
import com.example.ui.theme.FigmaNavy
import com.example.ui.theme.FigmaSoftTeal
import com.example.ui.theme.FigmaTeal
import com.example.ui.theme.FigmaWhite

@Composable
fun SubledgerScreen(
  subledgerRepository: SubledgerRepository,
  modifier: Modifier = Modifier
) {
  val entries by subledgerRepository.allEntries.collectAsState(initial = emptyList())
  var selectedFilter by remember { mutableStateOf("ALL") }

  val filteredEntries = entries.filter { entry ->
    when (selectedFilter) {
      "CUSTOMER" -> entry.partyType == PartyType.CUSTOMER
      "SUPPLIER" -> entry.partyType == PartyType.SUPPLIER
      else -> true
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
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

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
          SubledgerEntryCard(entry = entry)
        }
      }
    }
  }
}

@Composable
fun SubledgerEntryCard(entry: SubledgerEntry) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = FigmaWhite),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, FigmaLine, RoundedCornerShape(12.dp))
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
            color = FigmaSoftTeal
          ) {
            Text(
              text = if (entry.partyType == PartyType.CUSTOMER) "SHOP AR" else "SUPPLIER AP",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = FigmaTeal,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }

          Spacer(modifier = Modifier.padding(4.dp))

          Text(
            text = entry.entryType.displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = FigmaInk
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
        Text(
          text = if (entry.notes.isNotBlank()) entry.notes else "Ref: ${entry.referenceType} #${entry.referenceId}",
          style = MaterialTheme.typography.bodySmall,
          color = FigmaMuted
        )

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
    }
  }
}
