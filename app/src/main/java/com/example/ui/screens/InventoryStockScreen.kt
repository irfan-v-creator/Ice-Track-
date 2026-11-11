package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.model.ProductBatch
import com.example.domain.model.StockMovement
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
import com.example.ui.viewmodel.InventoryViewModel
import com.example.ui.viewmodel.ProductStockSummary

@Composable
fun InventoryStockScreen(
  viewModel: InventoryViewModel,
  onNavigateToPurchase: () -> Unit,
  modifier: Modifier = Modifier
) {
  val state by viewModel.uiState.collectAsState()
  var showAdjustmentDialog by remember { mutableStateOf<ProductBatch?>(null) }

  Scaffold(
    containerColor = FigmaBackground,
    floatingActionButton = {
      FloatingActionButton(
        onClick = onNavigateToPurchase,
        containerColor = FigmaTeal,
        contentColor = FigmaWhite,
        modifier = Modifier.testTag("add_stock_fab")
      ) {
        Icon(Icons.Default.Add, contentDescription = "Receive Stock")
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
        .testTag("inventory_screen"),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Header & Valuation Card
      Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = FigmaWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, FigmaLine, RoundedCornerShape(12.dp))
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(text = "Total inventory stock value", style = MaterialTheme.typography.bodySmall, color = FigmaMuted)
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = SubledgerCalculationEngine.formatPaise(state.totalStockValuePaise),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = FigmaInk
          )

          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = if (state.lowStockItemCount > 0) FigmaAmber.copy(alpha = 0.15f) else FigmaSoftTeal,
              modifier = Modifier.weight(1f)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                if (state.lowStockItemCount > 0) {
                  Icon(Icons.Default.Warning, contentDescription = null, tint = FigmaAmber, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                  text = "${state.lowStockItemCount} Low Stock",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = if (state.lowStockItemCount > 0) FigmaAmber else FigmaTeal
                )
              }
            }

            Surface(
              shape = RoundedCornerShape(8.dp),
              color = if (state.expiringBatchCount > 0) FigmaRed.copy(alpha = 0.12f) else FigmaSoftTeal,
              modifier = Modifier.weight(1f)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                if (state.expiringBatchCount > 0) {
                  Icon(Icons.Default.Warning, contentDescription = null, tint = FigmaRed, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                  text = "${state.expiringBatchCount} Near Expiry",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = if (state.expiringBatchCount > 0) FigmaRed else FigmaTeal
                )
              }
            }
          }
        }
      }

      // Tab selector: Products vs Movement History
      TabRow(
        selectedTabIndex = state.selectedTab,
        containerColor = FigmaWhite,
        contentColor = FigmaTeal,
        indicator = { tabPositions ->
          TabRowDefaults.SecondaryIndicator(
            Modifier.tabIndicatorOffset(tabPositions[state.selectedTab]),
            color = FigmaTeal
          )
        },
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, FigmaLine, RoundedCornerShape(8.dp))
      ) {
        Tab(
          selected = state.selectedTab == 0,
          onClick = { viewModel.setSelectedTab(0) },
          text = { Text("Stock by Product", fontWeight = FontWeight.SemiBold) }
        )
        Tab(
          selected = state.selectedTab == 1,
          onClick = { viewModel.setSelectedTab(1) },
          text = { Text("Stock Movements", fontWeight = FontWeight.SemiBold) }
        )
      }

      if (state.selectedTab == 0) {
        // Search Input
        OutlinedTextField(
          value = state.searchQuery,
          onValueChange = viewModel::setSearchQuery,
          placeholder = { Text("Search product name, SKU or category...") },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = FigmaMuted) },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = FigmaTeal,
            unfocusedBorderColor = FigmaLine,
            focusedContainerColor = FigmaWhite,
            unfocusedContainerColor = FigmaWhite
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("inventory_search_input")
        )

        if (state.stockSummaries.isEmpty()) {
          EmptyStateView(
            icon = Icons.Default.Inventory2,
            title = "No Inventory Found",
            message = "Receive purchases from suppliers to track batches and FEFO stock levels."
          )
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            items(state.stockSummaries, key = { it.product.id }) { summary ->
              ProductStockCard(
                summary = summary,
                onAdjustBatch = { batch -> showAdjustmentDialog = batch }
              )
            }
          }
        }
      } else {
        // Movement history
        if (state.recentMovements.isEmpty()) {
          EmptyStateView(
            icon = Icons.Default.History,
            title = "No Movements Recorded",
            message = "Purchases, sales dispatches, and adjustments will appear here."
          )
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(state.recentMovements, key = { it.id }) { movement ->
              StockMovementCard(movement = movement)
            }
          }
        }
      }
    }
  }

  // Stock Adjustment Dialog
  showAdjustmentDialog?.let { batch ->
    StockAdjustmentDialog(
      batch = batch,
      onDismiss = { showAdjustmentDialog = null },
      onConfirm = { pieces, reason ->
        viewModel.adjustStock(batch.id, pieces, reason) {
          showAdjustmentDialog = null
        }
      }
    )
  }
}

@Composable
fun ProductStockCard(
  summary: ProductStockSummary,
  onAdjustBatch: (ProductBatch) -> Unit
) {
  var expanded by remember { mutableStateOf(false) }

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
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = summary.product.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = FigmaInk
          )
          Text(
            text = "SKU: ${summary.product.sku} · ${summary.product.category}",
            style = MaterialTheme.typography.bodySmall,
            color = FigmaMuted
          )
        }

        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = "${summary.totalPiecesInStock} Pcs",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (summary.isLowStock) FigmaAmber else FigmaInk
          )
          if (summary.isLowStock) {
            Text(
              text = "Min: ${summary.product.minStockLevelPieces} Pcs",
              style = MaterialTheme.typography.labelSmall,
              color = FigmaAmber,
              fontWeight = FontWeight.SemiBold
            )
          }
        }
      }

      if (summary.batches.isNotEmpty()) {
        Spacer(modifier = Modifier.height(10.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "${summary.batches.size} active batch(es) in storage",
            style = MaterialTheme.typography.labelMedium,
            color = FigmaTeal,
            fontWeight = FontWeight.SemiBold
          )
          Text(
            text = if (expanded) "Hide Batches ▲" else "View Batches (FEFO) ▼",
            style = MaterialTheme.typography.labelSmall,
            color = FigmaMuted
          )
        }

        if (expanded) {
          Spacer(modifier = Modifier.height(8.dp))
          HorizontalDivider(thickness = 0.5.dp, color = FigmaLine)
          Spacer(modifier = Modifier.height(8.dp))

          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            summary.batches.forEach { batch ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(FigmaSoftTeal, RoundedCornerShape(6.dp))
                  .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column {
                  Text(
                    text = "Batch: ${batch.batchNumber}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = FigmaInk
                  )
                  Text(
                    text = "Exp: ${SubledgerCalculationEngine.formatDate(batch.expiryDate)}",
                    fontSize = 11.sp,
                    color = if (batch.isExpired) FigmaRed else FigmaMuted,
                    fontWeight = if (batch.isExpired) FontWeight.Bold else FontWeight.Normal
                  )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = "${batch.currentQuantityPieces} Pcs",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = FigmaInk
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = FigmaWhite,
                    modifier = Modifier
                      .border(1.dp, FigmaLine, RoundedCornerShape(6.dp))
                      .clickable { onAdjustBatch(batch) }
                      .padding(horizontal = 6.dp, vertical = 4.dp)
                  ) {
                    Text(text = "Adjust", fontSize = 11.sp, color = FigmaTeal, fontWeight = FontWeight.SemiBold)
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun StockMovementCard(movement: StockMovement) {
  Card(
    shape = RoundedCornerShape(10.dp),
    colors = CardDefaults.cardColors(containerColor = FigmaWhite),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(0.5.dp, FigmaLine, RoundedCornerShape(10.dp))
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = movement.movementType.displayName,
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp,
          color = FigmaInk
        )
        Text(
          text = "${SubledgerCalculationEngine.formatDate(movement.movementDate)} · ${movement.notes}",
          fontSize = 11.sp,
          color = FigmaMuted
        )
      }

      Text(
        text = if (movement.quantityPieces > 0) "+${movement.quantityPieces} Pcs" else "${movement.quantityPieces} Pcs",
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = if (movement.quantityPieces > 0) FigmaTeal else FigmaRed
      )
    }
  }
}

@Composable
fun StockAdjustmentDialog(
  batch: ProductBatch,
  onDismiss: () -> Unit,
  onConfirm: (Int, String) -> Unit
) {
  var piecesInput by remember { mutableStateOf("") }
  var isAddition by remember { mutableStateOf(false) }
  var reasonInput by remember { mutableStateOf("") }
  var errorText by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "Stock Adjustment: Batch ${batch.batchNumber}",
        fontWeight = FontWeight.Bold,
        color = FigmaInk
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Current Stock: ${batch.currentQuantityPieces} pieces",
          style = MaterialTheme.typography.bodyMedium,
          color = FigmaMuted
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = { isAddition = false },
            colors = ButtonDefaults.buttonColors(
              containerColor = if (!isAddition) FigmaRed else FigmaSoftTeal,
              contentColor = if (!isAddition) FigmaWhite else FigmaInk
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f)
          ) {
            Text("Reduce (-)")
          }

          Button(
            onClick = { isAddition = true },
            colors = ButtonDefaults.buttonColors(
              containerColor = if (isAddition) FigmaTeal else FigmaSoftTeal,
              contentColor = if (isAddition) FigmaWhite else FigmaInk
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f)
          ) {
            Text("Add (+)")
          }
        }

        OutlinedTextField(
          value = piecesInput,
          onValueChange = { piecesInput = it },
          label = { Text("Quantity (Pieces)") },
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = reasonInput,
          onValueChange = { reasonInput = it },
          label = { Text("Reason (e.g. damaged, wastage, physical count)") },
          modifier = Modifier.fillMaxWidth()
        )

        errorText?.let {
          Text(text = it, color = FigmaRed, fontSize = 12.sp)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val pcs = piecesInput.toIntOrNull()
          if (pcs == null || pcs <= 0) {
            errorText = "Please enter a valid positive number of pieces"
            return@Button
          }
          if (reasonInput.isBlank()) {
            errorText = "Please specify a reason"
            return@Button
          }

          val adjustment = if (isAddition) pcs else -pcs
          if (!isAddition && pcs > batch.currentQuantityPieces) {
            errorText = "Cannot reduce more than current batch stock (${batch.currentQuantityPieces} pcs)"
            return@Button
          }

          onConfirm(adjustment, reasonInput)
        },
        colors = ButtonDefaults.buttonColors(containerColor = FigmaTeal, contentColor = FigmaWhite)
      ) {
        Text("Apply Adjustment")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", color = FigmaMuted)
      }
    }
  )
}
