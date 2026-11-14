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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Store
import com.example.domain.engine.SubledgerCalculationEngine
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.CurrencyText
import com.example.ui.components.StatCard
import com.example.ui.navigation.Screen
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
import com.example.ui.viewmodel.DashboardViewModel

@Composable
fun DashboardScreen(
  viewModel: DashboardViewModel,
  onNavigate: (Screen) -> Unit,
  onQuickAddProduct: () -> Unit,
  onQuickAddCustomer: () -> Unit,
  onQuickAddSupplier: () -> Unit,
  modifier: Modifier = Modifier
) {
  val state by viewModel.uiState.collectAsState()
  val scrollState = rememberScrollState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(FigmaBackground)
      .testTag("dashboard_screen")
      .verticalScroll(scrollState)
      .padding(horizontal = 24.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Header Headline
    Column {
      Text(
        text = "Business, at a glance.",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = FigmaInk
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = "${state.businessName} · Offline first",
        style = MaterialTheme.typography.bodySmall,
        color = FigmaMuted
      )
    }

    // Hero Performance Card (White card, Line border)
    Card(
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = FigmaWhite),
      elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, FigmaLine, RoundedCornerShape(12.dp))
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(
          text = "Today's sales",
          style = MaterialTheme.typography.bodySmall,
          color = FigmaMuted
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = SubledgerCalculationEngine.formatPaise(state.todaySalesPaise),
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
          color = FigmaInk
        )
        Text(
          text = "${state.todaySalesCount} sales invoices recorded",
          style = MaterialTheme.typography.labelSmall,
          color = FigmaMuted
        )

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(thickness = 1.dp, color = FigmaLine)
        Spacer(modifier = Modifier.height(12.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(text = "Purchases", style = MaterialTheme.typography.labelSmall, color = FigmaMuted)
            Text(text = SubledgerCalculationEngine.formatPaise(state.todayPurchasesPaise), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = FigmaInk)
            Text(text = "Today", style = MaterialTheme.typography.labelSmall, color = FigmaMuted)
          }

          Column(modifier = Modifier.weight(1f)) {
            Text(text = "Near Expiry Batches", style = MaterialTheme.typography.labelSmall, color = FigmaMuted)
            Text(text = "${state.expiringBatchCount} Batches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (state.expiringBatchCount > 0) FigmaRed else FigmaInk)
            Text(text = "Within 7 days", style = MaterialTheme.typography.labelSmall, color = FigmaMuted)
          }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(thickness = 1.dp, color = FigmaLine)
        Spacer(modifier = Modifier.height(12.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(text = "Sellable stock value", style = MaterialTheme.typography.labelSmall, color = FigmaMuted)
            Text(
              text = SubledgerCalculationEngine.formatPaise(state.sellableStockValuePaise),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = FigmaInk
            )
            Text(
              text = if (state.expiredStockValuePaise > 0L) {
                "Expired: ${SubledgerCalculationEngine.formatPaise(state.expiredStockValuePaise)}"
              } else {
                "${state.productCount} active products"
              },
              style = MaterialTheme.typography.labelSmall,
              color = if (state.expiredStockValuePaise > 0L) FigmaRed else FigmaMuted
            )
          }

          Column(modifier = Modifier.weight(1f)) {
            Text(text = "Low stock alerts", style = MaterialTheme.typography.labelSmall, color = FigmaMuted)
            Text(
              text = "${state.lowStockCount} Low Stock",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = if (state.lowStockCount > 0) FigmaAmber else FigmaTeal
            )
            Text(
              text = "${state.supplierCount} suppliers",
              style = MaterialTheme.typography.labelSmall,
              color = FigmaMuted
            )
          }
        }
      }
    }

    // Prominent "+ New sale" action button (Teal #397D7A, White text, 48dp height)
    Button(
      onClick = { onNavigate(Screen.SALES) },
      colors = ButtonDefaults.buttonColors(containerColor = FigmaTeal, contentColor = FigmaWhite),
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .testTag("new_sale_action_btn")
    ) {
      Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(modifier = Modifier.width(8.dp))
      Text("New sale", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }

    // Credit Overview Card
    Text(
      text = "Credit overview",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = FigmaInk
    )

    Card(
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = FigmaWhite),
      elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, FigmaLine, RoundedCornerShape(12.dp))
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(text = "Customer outstanding", style = MaterialTheme.typography.bodyMedium, color = FigmaMuted)
            Text(
              text = if (state.totalCustomerAdvancesPaise > 0L) {
                "${state.customerCount} shops · Adv credit: ${SubledgerCalculationEngine.formatPaise(state.totalCustomerAdvancesPaise)}"
              } else {
                "${state.customerCount} retail shops"
              },
              style = MaterialTheme.typography.labelSmall,
              color = if (state.totalCustomerAdvancesPaise > 0L) FigmaTeal else FigmaMuted
            )
          }
          CurrencyText(
            paise = state.totalReceivablesPaise,
            isReceivable = true,
            fontSize = 17,
            fontWeight = FontWeight.Bold
          )
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(thickness = 1.dp, color = FigmaLine)
        Spacer(modifier = Modifier.height(12.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(text = "Supplier payables", style = MaterialTheme.typography.bodyMedium, color = FigmaMuted)
            Text(
              text = if (state.totalSupplierAdvancesPaise > 0L) {
                "${state.supplierCount} units · Adv paid: ${SubledgerCalculationEngine.formatPaise(state.totalSupplierAdvancesPaise)}"
              } else {
                "${state.supplierCount} production units"
              },
              style = MaterialTheme.typography.labelSmall,
              color = if (state.totalSupplierAdvancesPaise > 0L) FigmaTeal else FigmaMuted
            )
          }
          CurrencyText(
            paise = state.totalPayablesPaise,
            isReceivable = false,
            fontSize = 17,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }

    // Master Directory Cards
    Text(
      text = "Master Directory",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = FigmaInk
    )

    StatCard(
      title = "Catalog Products",
      value = "${state.productCount} Products",
      subtitle = "Multi-unit conversion configured",
      icon = Icons.Default.Inventory2,
      accentColor = FigmaTeal,
      onClick = { onNavigate(Screen.PRODUCTS) },
      modifier = Modifier.testTag("products_stat_card")
    )

    StatCard(
      title = "Registered Retail Shops",
      value = "${state.customerCount} Customers",
      subtitle = "Shop-specific pricing and credit limits",
      icon = Icons.Default.Store,
      accentColor = FigmaTeal,
      onClick = { onNavigate(Screen.CUSTOMERS) },
      modifier = Modifier.testTag("customers_stat_card")
    )

    StatCard(
      title = "Factory Suppliers",
      value = "${state.supplierCount} Suppliers",
      subtitle = "Purchase price mapping & credit terms",
      icon = Icons.Default.Business,
      accentColor = FigmaNavy,
      onClick = { onNavigate(Screen.SUPPLIERS) },
      modifier = Modifier.testTag("suppliers_stat_card")
    )

    // Quick Action Buttons
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Button(
        onClick = onQuickAddProduct,
        colors = ButtonDefaults.buttonColors(containerColor = FigmaNavy, contentColor = FigmaWhite),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .weight(1f)
          .height(44.dp)
          .testTag("quick_add_product_btn")
      ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Product", fontSize = 12.sp)
      }

      OutlinedButton(
        onClick = onQuickAddCustomer,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = FigmaInk),
        modifier = Modifier
          .weight(1f)
          .height(44.dp)
          .border(1.dp, FigmaLine, RoundedCornerShape(12.dp))
          .testTag("quick_add_customer_btn")
      ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Shop", fontSize = 12.sp)
      }

      OutlinedButton(
        onClick = onQuickAddSupplier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = FigmaInk),
        modifier = Modifier
          .weight(1f)
          .height(44.dp)
          .border(1.dp, FigmaLine, RoundedCornerShape(12.dp))
          .testTag("quick_add_supplier_btn")
      ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Supplier", fontSize = 12.sp)
      }
    }
  }
}
