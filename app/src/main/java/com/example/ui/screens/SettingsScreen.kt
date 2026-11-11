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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.FigmaAmber
import com.example.ui.theme.FigmaBackground
import com.example.ui.theme.FigmaInk
import com.example.ui.theme.FigmaLine
import com.example.ui.theme.FigmaMuted
import com.example.ui.theme.FigmaNavy
import com.example.ui.theme.FigmaSoftTeal
import com.example.ui.theme.FigmaTeal
import com.example.ui.theme.FigmaWhite
import com.example.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
  viewModel: SettingsViewModel,
  modifier: Modifier = Modifier
) {
  val state by viewModel.uiState.collectAsState()
  val scrollState = rememberScrollState()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  val textFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = FigmaTeal,
    unfocusedBorderColor = FigmaLine,
    focusedLabelColor = FigmaTeal,
    unfocusedLabelColor = FigmaMuted,
    focusedTextColor = FigmaInk,
    unfocusedTextColor = FigmaInk
  )

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(FigmaBackground)
      .verticalScroll(scrollState)
      .padding(horizontal = 24.dp, vertical = 16.dp)
      .testTag("settings_screen"),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Text(
      text = "Business Profile & Settings",
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      color = FigmaInk
    )

    // Business Profile Card
    Card(
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = FigmaWhite),
      elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, FigmaLine, RoundedCornerShape(12.dp))
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text(
          text = "Distributor Information",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = FigmaNavy
        )

        OutlinedTextField(
          value = state.businessName,
          onValueChange = viewModel::updateBusinessName,
          label = { Text("Firm / Trading Name") },
          colors = textFieldColors,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("business_name_input")
        )

        OutlinedTextField(
          value = state.contactPhone,
          onValueChange = viewModel::updatePhone,
          label = { Text("Business Phone Number") },
          colors = textFieldColors,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = state.address,
          onValueChange = viewModel::updateAddress,
          label = { Text("Cold Storage / Office Address") },
          colors = textFieldColors,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = state.fssaiNumber,
          onValueChange = viewModel::updateFssai,
          label = { Text("FSSAI License / Registration No.") },
          colors = textFieldColors,
          modifier = Modifier.fillMaxWidth()
        )

        Button(
          onClick = {
            viewModel.saveProfile {
              scope.launch {
                snackbarHostState.showSnackbar("Business profile saved successfully")
              }
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = FigmaTeal, contentColor = FigmaWhite),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("save_profile_btn")
        ) {
          Icon(Icons.Default.Save, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Save Profile", fontWeight = FontWeight.SemiBold)
        }
      }
    }

    // Pending Items & Configuration Status
    Text(
      text = "System Configuration Status",
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
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.AccessTime, contentDescription = null, tint = FigmaAmber)
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "Morning Business Reminder",
              fontWeight = FontWeight.SemiBold,
              style = MaterialTheme.typography.bodyMedium,
              color = FigmaInk
            )
            Text(
              text = "Status: ${state.morningReminderStatus}",
              style = MaterialTheme.typography.bodySmall,
              color = FigmaMuted
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.CloudQueue, contentDescription = null, tint = FigmaNavy)
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "Google Drive Cloud Backup",
              fontWeight = FontWeight.SemiBold,
              style = MaterialTheme.typography.bodyMedium,
              color = FigmaInk
            )
            Text(
              text = "Status: ${state.cloudBackupStatus}",
              style = MaterialTheme.typography.bodySmall,
              color = FigmaMuted
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Security, contentDescription = null, tint = FigmaTeal)
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "Offline-First Data Storage",
              fontWeight = FontWeight.SemiBold,
              style = MaterialTheme.typography.bodyMedium,
              color = FigmaInk
            )
            Text(
              text = "Active on device with SQLite Write-Ahead Logging (WAL)",
              style = MaterialTheme.typography.bodySmall,
              color = FigmaMuted
            )
          }
        }
      }
    }

    // App Version info
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = FigmaSoftTeal,
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, FigmaLine, RoundedCornerShape(12.dp))
    ) {
      Row(
        modifier = Modifier.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = FigmaTeal)
        Spacer(modifier = Modifier.width(10.dp))
        Column {
          Text(
            text = "IceTrack v1.0",
            fontWeight = FontWeight.Bold,
            color = FigmaInk,
            fontSize = 13.sp
          )
          Text(
            text = "Figma Design System • Room SQLite • Jetpack Compose",
            color = FigmaMuted,
            fontSize = 12.sp
          )
        }
      }
    }

    SnackbarHost(hostState = snackbarHostState)
  }
}
