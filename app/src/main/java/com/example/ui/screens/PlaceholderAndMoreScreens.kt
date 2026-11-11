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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.EmptyStateView
import com.example.ui.navigation.Screen
import com.example.ui.theme.FigmaBackground
import com.example.ui.theme.FigmaInk
import com.example.ui.theme.FigmaLine
import com.example.ui.theme.FigmaMuted
import com.example.ui.theme.FigmaSoftTeal
import com.example.ui.theme.FigmaTeal
import com.example.ui.theme.FigmaWhite

@Composable
fun MoreScreen(
  onNavigate: (Screen) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .background(FigmaBackground)
      .padding(horizontal = 24.dp, vertical = 16.dp)
      .testTag("more_screen"),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text(
      text = "All Modules & Settings",
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      color = FigmaInk
    )

    Spacer(modifier = Modifier.height(2.dp))

    MoreMenuItem(
      title = "Product Catalog & Units",
      subtitle = "Manage items, SKU codes, piece/box/carton ratios",
      icon = Icons.Default.Inventory2,
      onClick = { onNavigate(Screen.PRODUCTS) }
    )

    MoreMenuItem(
      title = "Retail Shops (Customers)",
      subtitle = "Manage shops, custom prices, credit limits & AR balances",
      icon = Icons.Default.Store,
      onClick = { onNavigate(Screen.CUSTOMERS) }
    )

    MoreMenuItem(
      title = "Suppliers & Factories",
      subtitle = "Manage production units, credit terms & AP payables",
      icon = Icons.Default.Business,
      onClick = { onNavigate(Screen.SUPPLIERS) }
    )

    MoreMenuItem(
      title = "Business Expenses",
      subtitle = "Vehicle fuel, maintenance, cold storage electricity bills",
      icon = Icons.Default.ReceiptLong,
      onClick = { onNavigate(Screen.EXPENSES) }
    )

    MoreMenuItem(
      title = "Transaction Subledger",
      subtitle = "View immutable AR and AP ledger records",
      icon = Icons.Default.AccountBalanceWallet,
      onClick = { onNavigate(Screen.LEDGER) }
    )

    MoreMenuItem(
      title = "Business Settings",
      subtitle = "Trading details, reminder configuration status",
      icon = Icons.Default.Settings,
      onClick = { onNavigate(Screen.SETTINGS) }
    )
  }
}

@Composable
fun MoreMenuItem(
  title: String,
  subtitle: String,
  icon: ImageVector,
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
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = FigmaSoftTeal,
        modifier = Modifier.size(44.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(icon, contentDescription = null, tint = FigmaTeal, modifier = Modifier.size(24.dp))
        }
      }

      Spacer(modifier = Modifier.width(14.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = FigmaInk
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = FigmaMuted
        )
      }

      Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
        contentDescription = null,
        tint = FigmaMuted,
        modifier = Modifier.size(20.dp)
      )
    }
  }
}

@Composable
fun OperationsPlaceholderScreen(
  moduleName: String,
  description: String,
  icon: ImageVector,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .background(FigmaBackground)
      .padding(horizontal = 24.dp, vertical = 24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    EmptyStateView(
      icon = icon,
      title = moduleName,
      message = description
    )
  }
}
