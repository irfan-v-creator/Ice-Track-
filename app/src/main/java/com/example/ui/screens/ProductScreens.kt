package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Product
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
import com.example.ui.viewmodel.ProductViewModel

val categories = listOf("All", "Sticks/Bars", "Cones", "Cups", "Family Tubs", "Bulk Packs")

@Composable
fun ProductListScreen(
  viewModel: ProductViewModel,
  modifier: Modifier = Modifier
) {
  val products by viewModel.products.collectAsState()
  val searchQuery by viewModel.searchQuery.collectAsState()
  val selectedCategory by viewModel.selectedCategory.collectAsState()
  val formState by viewModel.formState.collectAsState()

  var showDialog by remember { mutableStateOf(false) }

  Scaffold(
    containerColor = FigmaBackground,
    floatingActionButton = {
      FloatingActionButton(
        onClick = {
          viewModel.openAddDialog()
          showDialog = true
        },
        containerColor = FigmaTeal,
        contentColor = FigmaWhite,
        modifier = Modifier.testTag("add_product_fab")
      ) {
        Icon(Icons.Default.Add, contentDescription = "Add Product")
      }
    },
    modifier = modifier.fillMaxSize()
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(FigmaBackground)
        .padding(padding)
        .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
      // Search Bar (White background, Line border)
      OutlinedTextField(
        value = searchQuery,
        onValueChange = viewModel::onSearchQueryChanged,
        placeholder = { Text("Search name or SKU...", color = FigmaMuted) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = FigmaMuted) },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedContainerColor = FigmaWhite,
          unfocusedContainerColor = FigmaWhite,
          focusedBorderColor = FigmaTeal,
          unfocusedBorderColor = FigmaLine
        ),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("product_search_input")
      )

      Spacer(modifier = Modifier.height(12.dp))

      // Category Filter Chips
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        items(categories) { category ->
          FilterChip(
            selected = selectedCategory == category,
            onClick = { viewModel.onCategorySelected(category) },
            label = { Text(category, fontSize = 12.sp) },
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
              selected = selectedCategory == category
            )
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Stock Summary Card (White, Line border)
      Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = FigmaWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, FigmaLine, RoundedCornerShape(12.dp))
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(text = "Inventory at cost", style = MaterialTheme.typography.bodySmall, color = FigmaMuted)
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = "₹0.00",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = FigmaInk
          )
          Text(
            text = "${products.size} products registered in catalog",
            style = MaterialTheme.typography.labelSmall,
            color = FigmaMuted
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Product List or Empty State
      if (products.isEmpty()) {
        EmptyStateView(
          icon = Icons.Default.Inventory2,
          title = "No Products in Catalog",
          message = "Add your first ice cream product to configure Piece, Box, and Carton units.",
          actionButton = {
            Button(
              onClick = {
                viewModel.openAddDialog()
                showDialog = true
              },
              colors = ButtonDefaults.buttonColors(containerColor = FigmaTeal, contentColor = FigmaWhite),
              shape = RoundedCornerShape(12.dp)
            ) {
              Text("Add Product")
            }
          }
        )
      } else {
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier
            .fillMaxSize()
            .testTag("product_list")
        ) {
          items(products, key = { it.id }) { product ->
            ProductCard(
              product = product,
              onEdit = {
                viewModel.openEditDialog(product)
                showDialog = true
              },
              onDelete = { viewModel.deleteProduct(product.id) }
            )
          }
        }
      }
    }
  }

  if (showDialog) {
    AddEditProductDialog(
      formState = formState,
      onDismiss = { showDialog = false },
      onSave = {
        viewModel.saveProduct {
          showDialog = false
        }
      },
      onUpdateForm = viewModel::updateForm
    )
  }
}

@Composable
fun ProductCard(
  product: Product,
  onEdit: () -> Unit,
  onDelete: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = FigmaWhite),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, FigmaLine, RoundedCornerShape(12.dp))
      .testTag("product_card_${product.id}")
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = product.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = FigmaInk
          )
          Spacer(modifier = Modifier.height(3.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = FigmaSoftTeal
            ) {
              Text(
                text = product.category,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = FigmaTeal,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "SKU: ${product.sku}",
              style = MaterialTheme.typography.labelSmall,
              color = FigmaMuted
            )
          }
        }

        Row {
          IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = FigmaTeal)
          }
          IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = FigmaRed)
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column {
          Text(
            text = "Purchase Cost",
            style = MaterialTheme.typography.labelSmall,
            color = FigmaMuted
          )
          CurrencyText(paise = product.defaultPurchasePricePaise, fontSize = 14)
        }

        Column {
          Text(
            text = "Selling Price",
            style = MaterialTheme.typography.labelSmall,
            color = FigmaMuted
          )
          CurrencyText(paise = product.defaultSellingPricePaise, fontSize = 14, isReceivable = true)
        }

        Column {
          Text(
            text = "Min Stock Alert",
            style = MaterialTheme.typography.labelSmall,
            color = FigmaMuted
          )
          Text(
            text = "${product.minStockLevelPieces} Pcs",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = FigmaInk
          )
        }
      }
    }
  }
}

@Composable
fun AddEditProductDialog(
  formState: com.example.ui.viewmodel.ProductFormState,
  onDismiss: () -> Unit,
  onSave: () -> Unit,
  onUpdateForm: ((com.example.ui.viewmodel.ProductFormState) -> com.example.ui.viewmodel.ProductFormState) -> Unit
) {
  val piecesBox = formState.piecesPerBox.toIntOrNull() ?: 1
  val boxCtn = formState.boxesPerCarton.toIntOrNull() ?: 1
  val totalPiecesInCtn = piecesBox * boxCtn

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = if (formState.id == 0L) "Add New Product" else "Edit Product",
        fontWeight = FontWeight.Bold,
        color = FigmaInk
      )
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        if (formState.errorMessage != null) {
          Surface(
            color = FigmaRed.copy(alpha = 0.08f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.border(1.dp, FigmaRed.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
          ) {
            Text(
              text = formState.errorMessage,
              color = FigmaRed,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(8.dp)
            )
          }
        }

        OutlinedTextField(
          value = formState.name,
          onValueChange = { onUpdateForm { s -> s.copy(name = it) } },
          label = { Text("Product Name *") },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("product_name_input")
        )

        OutlinedTextField(
          value = formState.sku,
          onValueChange = { onUpdateForm { s -> s.copy(sku = it) } },
          label = { Text("SKU / Product Code *") },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("product_sku_input")
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = formState.purchasePriceRupees,
            onValueChange = { onUpdateForm { s -> s.copy(purchasePriceRupees = it) } },
            label = { Text("Cost Price (₹)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
              .weight(1f)
              .testTag("product_cost_input")
          )
          OutlinedTextField(
            value = formState.sellingPriceRupees,
            onValueChange = { onUpdateForm { s -> s.copy(sellingPriceRupees = it) } },
            label = { Text("Sell Price (₹)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
              .weight(1f)
              .testTag("product_price_input")
          )
        }

        Text(
          text = "Unit Conversion Ratios",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = FigmaNavy
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = formState.piecesPerBox,
            onValueChange = { onUpdateForm { s -> s.copy(piecesPerBox = it) } },
            label = { Text("Pieces / Box (>= 1)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
              .weight(1f)
              .testTag("pieces_per_box_input")
          )
          OutlinedTextField(
            value = formState.boxesPerCarton,
            onValueChange = { onUpdateForm { s -> s.copy(boxesPerCarton = it) } },
            label = { Text("Boxes / Carton (>= 1)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
              .weight(1f)
              .testTag("boxes_per_carton_input")
          )
        }

        // Live Ratio preview
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = FigmaSoftTeal,
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FigmaLine, RoundedCornerShape(8.dp))
        ) {
          Text(
            text = "1 Carton = $boxCtn Boxes = $totalPiecesInCtn Pieces",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = FigmaTeal,
            modifier = Modifier.padding(8.dp)
          )
        }

        OutlinedTextField(
          value = formState.minStockPieces,
          onValueChange = { onUpdateForm { s -> s.copy(minStockPieces = it) } },
          label = { Text("Min Stock Alert (Pieces)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = onSave,
        colors = ButtonDefaults.buttonColors(containerColor = FigmaTeal, contentColor = FigmaWhite),
        modifier = Modifier.testTag("save_product_btn")
      ) {
        Text("Save Product")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", color = FigmaMuted)
      }
    }
  )
}
