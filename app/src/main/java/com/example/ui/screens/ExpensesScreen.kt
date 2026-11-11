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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.model.Expense
import com.example.domain.model.ExpenseCategory
import com.example.domain.model.PaymentMethod
import com.example.ui.components.EmptyStateView
import com.example.ui.theme.FigmaBackground
import com.example.ui.theme.FigmaInk
import com.example.ui.theme.FigmaLine
import com.example.ui.theme.FigmaMuted
import com.example.ui.theme.FigmaNavy
import com.example.ui.theme.FigmaRed
import com.example.ui.theme.FigmaSoftTeal
import com.example.ui.theme.FigmaTeal
import com.example.ui.theme.FigmaWhite
import com.example.ui.viewmodel.ExpenseViewModel

@Composable
fun ExpensesScreen(
  viewModel: ExpenseViewModel,
  modifier: Modifier = Modifier
) {
  val expenses by viewModel.allExpenses.collectAsState()
  var showAddDialog by remember { mutableStateOf(false) }

  val totalExpensePaise = expenses.sumOf { it.amountPaise }

  Scaffold(
    containerColor = FigmaBackground,
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showAddDialog = true },
        containerColor = FigmaTeal,
        contentColor = FigmaWhite,
        modifier = Modifier.testTag("add_expense_fab")
      ) {
        Icon(Icons.Default.Add, contentDescription = "Record Expense")
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
        .testTag("expenses_screen"),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Header Total Card
      Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = FigmaWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, FigmaLine, RoundedCornerShape(12.dp))
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(text = "Total Recorded Expenses", style = MaterialTheme.typography.bodySmall, color = FigmaMuted)
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = SubledgerCalculationEngine.formatPaise(totalExpensePaise),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = FigmaInk
          )
          Text(
            text = "Vehicle fuel, maintenance, storage electricity & operational costs",
            style = MaterialTheme.typography.labelSmall,
            color = FigmaMuted
          )
        }
      }

      if (expenses.isEmpty()) {
        EmptyStateView(
          icon = Icons.Default.ReceiptLong,
          title = "No Expenses Logged",
          message = "Record distribution fuel, vehicle repairs, dry ice, and cold storage bills to track net business profitability."
        )
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(expenses, key = { it.id }) { expense ->
            ExpenseItemCard(
              expense = expense,
              onDelete = { viewModel.softDeleteExpense(expense.id) }
            )
          }
        }
      }
    }
  }

  if (showAddDialog) {
    RecordExpenseDialog(
      onDismiss = { showAddDialog = false },
      onSubmit = { cat, amount, date, method, ref, notes, onError ->
        viewModel.recordExpense(
          category = cat,
          amountPaise = amount,
          expenseDate = date,
          paymentMethod = method,
          referenceNumber = ref,
          notes = notes,
          onSuccess = { showAddDialog = false },
          onError = onError
        )
      }
    )
  }
}

@Composable
fun ExpenseItemCard(
  expense: Expense,
  onDelete: () -> Unit
) {
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
          text = expense.category.displayName,
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp,
          color = FigmaInk
        )
        Text(
          text = "${SubledgerCalculationEngine.formatDate(expense.expenseDate)} · ${expense.paymentMethod.displayName}" +
                 if (expense.notes.isNotBlank()) " · ${expense.notes}" else "",
          fontSize = 11.sp,
          color = FigmaMuted
        )
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = SubledgerCalculationEngine.formatPaise(expense.amountPaise),
          fontWeight = FontWeight.Bold,
          fontSize = 14.sp,
          color = FigmaInk
        )
        Spacer(modifier = Modifier.width(6.dp))
        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
          Icon(Icons.Default.Delete, contentDescription = "Delete", tint = FigmaRed, modifier = Modifier.size(16.dp))
        }
      }
    }
  }
}

@Composable
fun RecordExpenseDialog(
  onDismiss: () -> Unit,
  onSubmit: (ExpenseCategory, Long, Long, PaymentMethod, String, String, (String) -> Unit) -> Unit
) {
  var selectedCategory by remember { mutableStateOf(ExpenseCategory.VEHICLE_FUEL) }
  var amountInput by remember { mutableStateOf("") }
  var selectedMethod by remember { mutableStateOf(PaymentMethod.UPI) }
  var referenceInput by remember { mutableStateOf("") }
  var notesInput by remember { mutableStateOf("") }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Record Business Expense", fontWeight = FontWeight.Bold, color = FigmaInk) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = "Category:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FigmaInk)
        ExpenseCategory.values().take(4).forEach { cat ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { selectedCategory = cat }
              .background(
                if (selectedCategory == cat) FigmaSoftTeal else FigmaWhite,
                RoundedCornerShape(6.dp)
              )
              .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = cat.displayName,
              fontSize = 12.sp,
              fontWeight = if (selectedCategory == cat) FontWeight.Bold else FontWeight.Normal,
              color = FigmaInk
            )
          }
        }

        OutlinedTextField(
          value = amountInput,
          onValueChange = { amountInput = it },
          label = { Text("Amount (₹)") },
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = notesInput,
          onValueChange = { notesInput = it },
          label = { Text("Notes (e.g. 50L Diesel, Refrigeration Gas)") },
          modifier = Modifier.fillMaxWidth()
        )

        errorMessage?.let {
          Text(text = it, color = FigmaRed, fontSize = 12.sp)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val paise = SubledgerCalculationEngine.parseRupeesToPaise(amountInput)
          if (paise <= 0) {
            errorMessage = "Please enter a valid expense amount"
            return@Button
          }
          onSubmit(
            selectedCategory,
            paise,
            System.currentTimeMillis(),
            selectedMethod,
            referenceInput,
            notesInput
          ) { err ->
            errorMessage = err
          }
        },
        colors = ButtonDefaults.buttonColors(containerColor = FigmaTeal, contentColor = FigmaWhite)
      ) {
        Text("Save Expense")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel", color = FigmaMuted) }
    }
  )
}
