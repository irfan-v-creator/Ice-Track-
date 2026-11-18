package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.engine.ConflictResolutionStrategy
import com.example.ui.theme.FigmaAmber
import com.example.ui.theme.FigmaInk
import com.example.ui.theme.FigmaLine
import com.example.ui.theme.FigmaMuted
import com.example.ui.theme.FigmaNavy
import com.example.ui.theme.FigmaRed
import com.example.ui.theme.FigmaSoftTeal
import com.example.ui.theme.FigmaTeal
import com.example.ui.theme.FigmaWhite
import com.example.ui.viewmodel.CloudSyncViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CloudSyncSection(
  cloudSyncViewModel: CloudSyncViewModel,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier
) {
  val syncState by cloudSyncViewModel.uiState.collectAsStateWithLifecycle()
  val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
  val lastSyncLabel = syncState.lastSyncedAtEpochMs?.let { formatter.format(Date(it)) } ?: "Not synced yet"

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text(
      text = "Cloud Sync & Google Account (Firestore)",
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
        .testTag("cloud_sync_card")
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Icon(Icons.Default.CloudDone, contentDescription = null, tint = FigmaTeal)
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = syncState.userDisplayName.ifBlank { "Signed-in Business Owner" },
              fontWeight = FontWeight.SemiBold,
              style = MaterialTheme.typography.bodyMedium,
              color = FigmaInk
            )
            Text(
              text = syncState.userEmail.ifBlank { "UID: ${syncState.uid}" },
              style = MaterialTheme.typography.bodySmall,
              color = FigmaMuted
            )
          }
        }

        Text(
          text = "Synced: ${syncState.syncedCount} | Pending: ${syncState.pendingCount} | Conflicts: ${syncState.conflictCount} | Failed: ${syncState.failedCount}",
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.Medium,
          color = FigmaNavy
        )
        Text(
          text = "Last Cloud Sync: $lastSyncLabel",
          style = MaterialTheme.typography.bodySmall,
          color = FigmaMuted
        )

        syncState.lastSummaryMessage?.let { summary ->
          Surface(
            color = FigmaSoftTeal,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, FigmaTeal, RoundedCornerShape(8.dp))
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(summary, fontSize = 12.sp, color = FigmaInk, modifier = Modifier.weight(1f))
              TextButton(onClick = cloudSyncViewModel::clearMessages) {
                Text("Dismiss", fontSize = 11.sp, color = FigmaNavy)
              }
            }
          }
        }

        syncState.errorMessage?.let { err ->
          Surface(
            color = FigmaRed.copy(alpha = 0.08f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, FigmaRed.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
              .testTag("cloud_sync_error_banner")
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(err, fontSize = 12.sp, color = FigmaRed, modifier = Modifier.weight(1f))
              TextButton(onClick = cloudSyncViewModel::clearMessages) {
                Text("Dismiss", fontSize = 11.sp, color = FigmaRed)
              }
            }
          }
        }

        if (syncState.conflicts.isNotEmpty()) {
          Surface(
            color = FigmaAmber.copy(alpha = 0.08f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, FigmaAmber.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = FigmaAmber)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "${syncState.conflicts.size} Sync Conflicts Require Review",
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp,
                  color = FigmaInk
                )
              }
              syncState.conflicts.take(5).forEach { conflict ->
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, FigmaLine, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Text(conflict.displayLabel, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = FigmaInk)
                  Text(
                    "Local Rev ${conflict.localRevision} vs Cloud Rev ${conflict.cloudRevision}",
                    fontSize = 11.sp,
                    color = FigmaMuted
                  )
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                      onClick = {
                        cloudSyncViewModel.resolveConflict(
                          conflict,
                          ConflictResolutionStrategy.KEEP_LOCAL_OVERWRITE_CLOUD
                        )
                      },
                      enabled = !syncState.isSyncing,
                      modifier = Modifier.weight(1f)
                    ) {
                      Text("Keep Local", fontSize = 11.sp, color = FigmaNavy)
                    }
                    OutlinedButton(
                      onClick = {
                        cloudSyncViewModel.resolveConflict(
                          conflict,
                          ConflictResolutionStrategy.APPLY_CLOUD_TO_LOCAL
                        )
                      },
                      enabled = !syncState.isSyncing,
                      modifier = Modifier.weight(1f)
                    ) {
                      Text("Use Cloud", fontSize = 11.sp, color = FigmaTeal)
                    }
                  }
                }
              }
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Button(
            onClick = { cloudSyncViewModel.syncNow(forceRetryFailed = true) },
            enabled = !syncState.isSyncing,
            colors = ButtonDefaults.buttonColors(containerColor = FigmaTeal, contentColor = FigmaWhite),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .weight(1f)
              .defaultMinSize(minHeight = 48.dp)
              .testTag("cloud_sync_now_btn")
          ) {
            if (syncState.isSyncing) {
              CircularProgressIndicator(color = FigmaWhite, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Syncing...")
            } else {
              Icon(Icons.Default.Sync, contentDescription = null)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Sync Now", fontWeight = FontWeight.SemiBold)
            }
          }

          OutlinedButton(
            onClick = onSignOut,
            enabled = !syncState.isSyncing,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .defaultMinSize(minHeight = 48.dp)
              .testTag("cloud_sign_out_btn")
          ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = FigmaRed)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Sign Out", color = FigmaRed, fontWeight = FontWeight.SemiBold)
          }
        }
      }
    }
  }
}
