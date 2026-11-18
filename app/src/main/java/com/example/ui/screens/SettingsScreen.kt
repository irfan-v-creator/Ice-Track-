package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.FigmaAmber
import com.example.ui.theme.FigmaRed
import com.example.ui.theme.FigmaBackground
import com.example.ui.theme.FigmaInk
import com.example.ui.theme.FigmaLine
import com.example.ui.theme.FigmaMuted
import com.example.ui.theme.FigmaNavy
import com.example.ui.theme.FigmaSoftTeal
import com.example.ui.theme.FigmaTeal
import com.example.ui.theme.FigmaWhite
import com.example.ui.viewmodel.CloudSyncViewModel
import com.example.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
  viewModel: SettingsViewModel,
  cloudSyncViewModel: CloudSyncViewModel? = null,
  onSignOut: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val state by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  val scrollState = rememberScrollState()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  val exportBackupLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/json")
  ) { uri ->
    if (uri != null) {
      viewModel.exportBackupToUri(context, uri)
    }
  }
  val importBackupLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null) {
      viewModel.inspectBackupFromUri(context, uri)
    }
  }

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

    if (cloudSyncViewModel != null && onSignOut != null) {
      CloudSyncSection(
        cloudSyncViewModel = cloudSyncViewModel,
        onSignOut = onSignOut
      )
    }

    // Backup & Restore (Zero-Permission SAF JSON V5)
    Text(
      text = "Backup & Restore (Full Database JSON)",
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
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text(
          text = "Exports and restores all 25 SQLite tables including batches, subledger, returns, credit/debit notes, refunds, and financial audit logs.",
          style = MaterialTheme.typography.bodySmall,
          color = FigmaMuted
        )

        state.backupMessage?.let { msg ->
          Surface(
            color = FigmaSoftTeal,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, FigmaTeal, RoundedCornerShape(8.dp))
              .testTag("backup_status_banner")
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(text = msg, fontSize = 12.sp, color = FigmaInk, modifier = Modifier.weight(1f))
              TextButton(onClick = { viewModel.clearBackupMessage() }) {
                Text("Dismiss", fontSize = 11.sp, color = FigmaNavy)
              }
            }
          }
        }

        Button(
          onClick = {
            exportBackupLauncher.launch("icetrack-backup-${System.currentTimeMillis()}.json")
          },
          enabled = !state.isBackupBusy,
          colors = ButtonDefaults.buttonColors(containerColor = FigmaTeal, contentColor = FigmaWhite),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .testTag("export_backup_btn")
        ) {
          Icon(Icons.Default.CloudUpload, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(if (state.isBackupBusy) "Working..." else "Export Full JSON Backup", fontWeight = FontWeight.SemiBold)
        }

        OutlinedButton(
          onClick = {
            importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
          },
          enabled = !state.isBackupBusy,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .testTag("restore_backup_btn")
        ) {
          Icon(Icons.Default.CloudDownload, contentDescription = null, tint = FigmaNavy)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Restore From JSON File", fontWeight = FontWeight.SemiBold, color = FigmaNavy)
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

  state.pendingRestoreValidation?.let { preview ->
    AlertDialog(
      onDismissRequest = { viewModel.dismissRestorePreview() },
      title = { Text("Confirm Full Backup Restore?", fontWeight = FontWeight.Bold, color = FigmaRed) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            "Schema v${preview.schemaVersion} validated. Current local data will be atomically replaced:",
            fontSize = 12.sp,
            color = FigmaInk
          )
          Text("• Products: ${preview.productCount} | Shops: ${preview.customerCount} | Suppliers: ${preview.supplierCount}", fontSize = 12.sp, color = FigmaInk)
          Text("• Sales: ${preview.salesCount} | Purchases: ${preview.purchaseCount} | Subledger: ${preview.subledgerCount}", fontSize = 12.sp, color = FigmaInk)
          Text("• Returns: ${preview.returnCount} | Notes: ${preview.noteCount} | Refunds: ${preview.refundCount} | Audit Logs: ${preview.auditLogCount}", fontSize = 12.sp, color = FigmaInk)
        }
      },
      confirmButton = {
        Button(
          onClick = { viewModel.confirmRestoreBackup() },
          enabled = !state.isBackupBusy,
          colors = ButtonDefaults.buttonColors(containerColor = FigmaRed, contentColor = FigmaWhite),
          modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag("confirm_restore_backup_btn")
        ) { Text("Replace & Restore") }
      },
      dismissButton = {
        TextButton(onClick = { viewModel.dismissRestorePreview() }) {
          Text("Cancel", color = FigmaMuted)
        }
      }
    )
  }
}
