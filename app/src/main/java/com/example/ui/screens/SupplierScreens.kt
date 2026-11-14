package com.example.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.ui.components.CurrencyText
import com.example.ui.components.EmptyStateView
import com.example.ui.theme.FigmaBackground
import com.example.ui.theme.FigmaInk
import com.example.ui.theme.FigmaLine
import com.example.ui.theme.FigmaMuted
import com.example.ui.theme.FigmaRed
import com.example.ui.theme.FigmaSoftTeal
import com.example.ui.theme.FigmaTeal
import com.example.ui.theme.FigmaWhite
import com.example.ui.viewmodel.SupplierViewModel
import com.example.ui.viewmodel.SupplierWithBalance

@Composable
fun SupplierListScreen(
  viewModel: SupplierViewModel,
  modifier: Modifier = Modifier
) {
  val suppliersWithBalance by viewModel.suppliersWithBalance.collectAsState()
  val searchQuery by viewModel.searchQuery.collectAsState()
  val formState by viewModel.formState.collectAsState()

  var showDialog by remember { mutableStateOf(false) }
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  Scaffold(
    containerColor = FigmaBackground,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    floatingActionButton = {
      FloatingActionButton(
        onClick = {
          viewModel.openAddDialog()
          showDialog = true
        },
        containerColor = FigmaTeal,
        contentColor = FigmaWhite,
        modifier = Modifier.testTag("add_supplier_fab")
      ) {
        Icon(Icons.Default.Add, contentDescription = "Add Supplier")
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
      // Search Input (White, Line border)
      OutlinedTextField(
        value = searchQuery,
        onValueChange = viewModel::onSearchQueryChanged,
        placeholder = { Text("Search supplier name or contact...", color = FigmaMuted) },
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
          .testTag("supplier_search_input")
      )

      Spacer(modifier = Modifier.height(14.dp))

      if (suppliersWithBalance.isEmpty()) {
        EmptyStateView(
          icon = Icons.Default.Business,
          title = "No Suppliers Registered",
          message = "Register your ice cream factories and production suppliers to track purchasing and payables.",
          actionButton = {
            Button(
              onClick = {
                viewModel.openAddDialog()
                showDialog = true
              },
              colors = ButtonDefaults.buttonColors(containerColor = FigmaTeal, contentColor = FigmaWhite),
              shape = RoundedCornerShape(12.dp)
            ) {
              Text("Add Supplier")
            }
          }
        )
      } else {
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier
            .fillMaxSize()
            .testTag("supplier_list")
        ) {
          items(suppliersWithBalance, key = { it.supplier.id }) { item ->
            SupplierCard(
              item = item,
              onEdit = {
                viewModel.openEditDialog(item.supplier)
                showDialog = true
              },
              onDelete = { viewModel.deleteSupplier(item.supplier.id) },
              onDialError = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
            )
          }
        }
      }
    }
  }

  if (showDialog) {
    AddEditSupplierDialog(
      formState = formState,
      onDismiss = { showDialog = false },
      onSave = {
        viewModel.saveSupplier {
          showDialog = false
        }
      },
      onUpdateForm = viewModel::updateForm
    )
  }
}

@Composable
fun SupplierCard(
  item: SupplierWithBalance,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onDialError: (String) -> Unit = {}
) {
  val context = LocalContext.current
  val supplier = item.supplier

  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = FigmaWhite),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, FigmaLine, RoundedCornerShape(12.dp))
      .testTag("supplier_card_${supplier.id}")
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = supplier.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = FigmaInk
          )
          if (supplier.contactPerson.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "Contact: ${supplier.contactPerson}",
              style = MaterialTheme.typography.bodySmall,
              color = FigmaMuted
            )
          }
          if (supplier.phoneNumber.isNotBlank()) {
            Text(
              text = "Ph: ${supplier.phoneNumber}",
              style = MaterialTheme.typography.bodySmall,
              color = FigmaMuted
            )
          }
        }

        Row {
          if (supplier.phoneNumber.isNotBlank()) {
            IconButton(
              onClick = {
                try {
                  val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${supplier.phoneNumber}"))
                  context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                  onDialError("No phone dialer app available on this device.")
                }
              }
            ) {
              Icon(Icons.Default.Call, contentDescription = "Call", tint = FigmaTeal)
            }
          }
          IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = FigmaTeal)
          }
          IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = FigmaRed)
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Credit Terms",
            style = MaterialTheme.typography.labelSmall,
            color = FigmaMuted
          )
          Text(
            text = "${supplier.creditTermsDays} Days",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = FigmaInk
          )
        }

        Column(horizontalAlignment = Alignment.End) {
          val isNetAdvance = item.outstandingPayablePaise < 0L
          Text(
            text = if (isNetAdvance) "Advance Credit Paid" else "Outstanding Payable",
            style = MaterialTheme.typography.labelSmall,
            color = FigmaMuted
          )
          if (isNetAdvance) {
            Text(
              text = SubledgerCalculationEngine.formatPaise(kotlin.math.abs(item.outstandingPayablePaise)),
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = FigmaTeal
            )
          } else {
            CurrencyText(
              paise = item.outstandingPayablePaise,
              isReceivable = false,
              fontSize = 16,
              fontWeight = FontWeight.Bold
            )
            if (item.availableAdvancePaise > 0L) {
              Text(
                text = "Adv: ${SubledgerCalculationEngine.formatPaise(item.availableAdvancePaise)}",
                style = MaterialTheme.typography.labelSmall,
                color = FigmaTeal,
                fontWeight = FontWeight.Medium
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun AddEditSupplierDialog(
  formState: com.example.ui.viewmodel.SupplierFormState,
  onDismiss: () -> Unit,
  onSave: () -> Unit,
  onUpdateForm: ((com.example.ui.viewmodel.SupplierFormState) -> com.example.ui.viewmodel.SupplierFormState) -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = if (formState.id == 0L) "Add Supplier" else "Edit Supplier",
        fontWeight = FontWeight.Bold,
        color = FigmaInk
      )
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
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
          label = { Text("Supplier / Company Name *") },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("supplier_name_input")
        )

        OutlinedTextField(
          value = formState.contactPerson,
          onValueChange = { onUpdateForm { s -> s.copy(contactPerson = it) } },
          label = { Text("Contact Person") },
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = formState.phoneNumber,
          onValueChange = { onUpdateForm { s -> s.copy(phoneNumber = it) } },
          label = { Text("Phone Number") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = formState.address,
          onValueChange = { onUpdateForm { s -> s.copy(address = it) } },
          label = { Text("Factory / Depot Address") },
          modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = formState.openingBalanceRupees,
            onValueChange = { onUpdateForm { s -> s.copy(openingBalanceRupees = it) } },
            label = { Text("Opening Balance (₹)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
              .weight(1f)
              .testTag("supplier_opening_balance_input")
          )

          OutlinedTextField(
            value = formState.creditTermsDays,
            onValueChange = { onUpdateForm { s -> s.copy(creditTermsDays = it) } },
            label = { Text("Credit Terms (Days)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
          )
        }

        OutlinedTextField(
          value = formState.creditLimitRupees,
          onValueChange = { onUpdateForm { s -> s.copy(creditLimitRupees = it) } },
          label = { Text("Credit Limit (₹, 0 = Unlimited)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("supplier_credit_limit_input")
        )

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = FigmaSoftTeal,
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FigmaLine, RoundedCornerShape(8.dp))
        ) {
          Text(
            text = "Opening date: ${SubledgerCalculationEngine.formatDate(formState.openingBalanceDate)}. Creates a baseline subledger payable entry.",
            style = MaterialTheme.typography.labelSmall,
            color = FigmaInk,
            modifier = Modifier.padding(8.dp)
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = onSave,
        enabled = !formState.isSaving,
        colors = ButtonDefaults.buttonColors(containerColor = FigmaTeal, contentColor = FigmaWhite),
        modifier = Modifier.testTag("save_supplier_btn")
      ) {
        Text(if (formState.isSaving) "Saving..." else "Save Supplier")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", color = FigmaMuted)
      }
    }
  )
}
