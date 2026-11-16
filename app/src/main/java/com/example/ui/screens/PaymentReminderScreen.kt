package com.example.ui.screens

import android.content.ActivityNotFoundException
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.domain.model.Customer
import com.example.domain.model.CustomerPaymentReminder
import com.example.domain.model.ReminderDueKind
import com.example.domain.model.ReminderStatus
import com.example.service.WhatsAppAppOption
import com.example.service.WhatsAppIntentHelper
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
import com.example.ui.viewmodel.CustomerReminderRowUi
import com.example.ui.viewmodel.LaunchWhatsAppCommand
import com.example.ui.viewmodel.PaymentReminderViewModel
import com.example.ui.viewmodel.ReminderPreviewUiState
import com.example.ui.viewmodel.ReminderStatusFilter
import kotlinx.coroutines.launch

@Composable
fun PaymentReminderDashboardScreen(
  viewModel: PaymentReminderViewModel,
  onViewCustomerLedger: (Customer) -> Unit,
  modifier: Modifier = Modifier,
  showPendingConfirmationDialog: Boolean = true,
  onLaunchWhatsAppIntent: ((LaunchWhatsAppCommand) -> Unit)? = null
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val state by viewModel.dashboardState.collectAsState()
  val previewState by viewModel.previewState.collectAsState()
  val pendingConfirmation by viewModel.pendingConfirmation.collectAsState()
  val customerShopNamesById by viewModel.customerShopNamesById.collectAsState()
  val historyCustomer by viewModel.historyCustomer.collectAsState()
  val customerHistory by viewModel.customerHistory.collectAsState()
  val isBusy by viewModel.isBusy.collectAsState()
  val feedbackMessage by viewModel.feedbackMessage.collectAsState()

  var whatsAppUnavailableMessage by remember { mutableStateOf<String?>(null) }
  var unavailableFallbackMessage by remember { mutableStateOf<String?>(null) }
  var appChooserCommand by remember { mutableStateOf<Pair<ReminderPreviewUiState, List<WhatsAppAppOption>>?>(null) }
  var resumeTick by remember { mutableStateOf(0) }

  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        resumeTick++
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LaunchedEffect(feedbackMessage) {
    val msg = feedbackMessage ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(msg)
    viewModel.clearFeedbackMessage()
  }

  LaunchedEffect(viewModel) {
    viewModel.launchEvents.collect { command ->
      try {
        if (onLaunchWhatsAppIntent != null) {
          onLaunchWhatsAppIntent(command)
        } else {
          val intent = WhatsAppIntentHelper.buildWhatsAppLaunchIntent(
            context = context,
            whatsappPhoneDigits = command.whatsappPhoneDigits,
            message = command.message,
            targetPackage = command.targetPackage
          )
          context.startActivity(intent)
        }
      } catch (_: ActivityNotFoundException) {
        unavailableFallbackMessage = command.message
        whatsAppUnavailableMessage = "WhatsApp is not installed on this device. You can copy the reminder message below and send it manually."
        viewModel.onWhatsAppLaunchFailed(
          command.reminderId,
          "WhatsApp app not installed on device"
        )
      } catch (e: Exception) {
        unavailableFallbackMessage = command.message
        whatsAppUnavailableMessage = "Unable to open WhatsApp: ${e.message ?: "Unknown error"}"
        viewModel.onWhatsAppLaunchFailed(
          command.reminderId,
          e.message ?: "Unable to open WhatsApp"
        )
      }
    }
  }

  Scaffold(
    containerColor = FigmaBackground,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    modifier = modifier
      .fillMaxSize()
      .testTag("payment_reminder_dashboard_screen")
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(FigmaBackground)
        .padding(padding)
        .padding(horizontal = 20.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // SSOT Summary Header
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = FigmaWhite,
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, FigmaLine, RoundedCornerShape(12.dp))
          .testTag("reminder_ssot_summary")
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Total Customer Outstanding",
              style = MaterialTheme.typography.labelMedium,
              color = FigmaMuted
            )
            Text(
              text = SubledgerCalculationEngine.formatPaise(state.totalOutstandingReceivablesPaise),
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = FigmaInk
            )
            Text(
              text = "${state.totalEligibleCount} eligible customers with valid phone",
              style = MaterialTheme.typography.labelSmall,
              color = FigmaTeal
            )
          }

          Surface(
            shape = RoundedCornerShape(10.dp),
            color = FigmaSoftTeal
          ) {
            Icon(
              imageVector = Icons.Default.NotificationsActive,
              contentDescription = "Payment Reminders",
              tint = FigmaTeal,
              modifier = Modifier
                .padding(10.dp)
                .size(24.dp)
            )
          }
        }
      }

      // Search Input
      OutlinedTextField(
        value = state.searchQuery,
        onValueChange = viewModel::onSearchQueryChanged,
        placeholder = { Text("Search shop, owner or phone...", color = FigmaMuted) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = FigmaMuted) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedContainerColor = FigmaWhite,
          unfocusedContainerColor = FigmaWhite,
          focusedBorderColor = FigmaTeal,
          unfocusedBorderColor = FigmaLine
        ),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("reminder_search_input")
      )

      // Outstanding-Only Toggle (Default ON)
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = FigmaWhite,
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, FigmaLine, RoundedCornerShape(10.dp))
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Outstanding balances only",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
              color = FigmaInk
            )
            Text(
              text = "Show customers with positive ledger balance",
              style = MaterialTheme.typography.labelSmall,
              color = FigmaMuted
            )
          }
          Switch(
            checked = state.outstandingOnly,
            onCheckedChange = viewModel::onToggleOutstandingOnly,
            colors = SwitchDefaults.colors(
              checkedThumbColor = FigmaWhite,
              checkedTrackColor = FigmaTeal,
              uncheckedThumbColor = FigmaMuted,
              uncheckedTrackColor = FigmaLine
            ),
            modifier = Modifier.testTag("reminder_outstanding_only_switch")
          )
        }
      }

      // Reminder Status Filter Chips
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        ReminderStatusFilter.entries.forEach { filter ->
          val selected = state.statusFilter == filter
          FilterChip(
            selected = selected,
            onClick = { viewModel.onStatusFilterSelected(filter) },
            label = { Text(filter.label, maxLines = 1) },
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
              selected = selected
            ),
            modifier = Modifier.testTag("reminder_status_filter_${filter.name}")
          )
        }
      }

      if (state.rows.isEmpty()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
          EmptyStateView(
            icon = Icons.Default.NotificationsActive,
            title = "No Matching Customers",
            message = if (state.outstandingOnly) {
              "No customers currently have a positive outstanding balance matching your filters."
            } else {
              "No customers match the selected search or reminder status filter."
            }
          )
        }
      } else {
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .testTag("reminder_customer_list")
        ) {
          items(state.rows, key = { it.customer.id }) { row ->
            CustomerReminderCard(
              row = row,
              isBusy = isBusy,
              onViewLedger = { onViewCustomerLedger(row.customer) },
              onOpenHistory = { viewModel.openHistory(row.customer) },
              onPrepareReminder = { viewModel.prepareReminderPreview(row.customer) }
            )
          }
        }
      }
    }
  }

  // Reminder Message Preview & Live Verification Dialog
  previewState?.let { preview ->
    ReminderPreviewDialog(
      preview = preview,
      onDismiss = viewModel::dismissPreview,
      onCopyMessage = {
        WhatsAppIntentHelper.copyMessageToClipboard(context, preview.composed.renderedMessage)
        scope.launch { snackbarHostState.showSnackbar("Reminder message copied to clipboard.") }
      },
      onLaunchWhatsApp = {
        val availability = WhatsAppIntentHelper.detectInstalledWhatsAppApps(context)
        when {
          availability.installedApps.size > 1 -> {
            appChooserCommand = preview to availability.installedApps
          }
          availability.installedApps.size == 1 -> {
            viewModel.openWhatsAppFromPreview(availability.installedApps.first().packageName)
          }
          availability.hasAnyHandler -> {
            viewModel.openWhatsAppFromPreview(null)
          }
          else -> {
            // Even if no WhatsApp package detected via PackageManager, attempt repository verification
            // and launch so explicit ActivityNotFoundException fallback records FAILED accurately.
            viewModel.openWhatsAppFromPreview(WhatsAppIntentHelper.WHATSAPP_PACKAGE)
          }
        }
      }
    )
  }

  // Dual WhatsApp App Selector (WhatsApp vs WhatsApp Business)
  appChooserCommand?.let { (_, options) ->
    AlertDialog(
      onDismissRequest = { appChooserCommand = null },
      containerColor = FigmaWhite,
      title = {
        Text("Choose WhatsApp App", fontWeight = FontWeight.Bold, color = FigmaInk)
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Select which WhatsApp application to open with the pre-filled reminder.",
            style = MaterialTheme.typography.bodySmall,
            color = FigmaMuted
          )
          options.forEach { option ->
            OutlinedButton(
              onClick = {
                appChooserCommand = null
                viewModel.openWhatsAppFromPreview(option.packageName)
              },
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .testTag("whatsapp_app_option_${option.packageName}")
            ) {
              Text(option.displayLabel, color = FigmaInk, fontWeight = FontWeight.SemiBold)
            }
          }
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(onClick = { appChooserCommand = null }) {
          Text("Cancel", color = FigmaMuted)
        }
      }
    )
  }

  // Return-to-App Confirmation Dialog (persists across process death)
  val confirmationReminder = pendingConfirmation
  if (showPendingConfirmationDialog && confirmationReminder != null && resumeTick >= 0) {
    val customerName = customerShopNamesById[confirmationReminder.customerId]
      ?: state.rows.firstOrNull { it.customer.id == confirmationReminder.customerId }?.customer?.shopName
      ?: "Customer #${confirmationReminder.customerId}"

    ReminderSendConfirmationDialog(
      customerName = customerName,
      reminder = confirmationReminder,
      onConfirmSent = { viewModel.confirmPendingReminderSent(confirmationReminder.id) },
      onNotSent = { viewModel.markPendingReminderNotSent(confirmationReminder.id) },
      onFailed = { viewModel.markPendingReminderFailed(confirmationReminder.id) }
    )
  }

  // Customer Reminder Audit History Dialog
  historyCustomer?.let { customer ->
    CustomerReminderHistoryDialog(
      customer = customer,
      history = customerHistory,
      onDismiss = viewModel::closeHistory
    )
  }

  // WhatsApp Missing / Copy Fallback Dialog
  whatsAppUnavailableMessage?.let { msg ->
    AlertDialog(
      onDismissRequest = {
        whatsAppUnavailableMessage = null
        unavailableFallbackMessage = null
      },
      containerColor = FigmaWhite,
      title = {
        Text("WhatsApp Unavailable", fontWeight = FontWeight.Bold, color = FigmaRed)
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(msg, style = MaterialTheme.typography.bodyMedium, color = FigmaInk)
          unavailableFallbackMessage?.let { fallbackText ->
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = FigmaBackground,
              modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, FigmaLine, RoundedCornerShape(8.dp))
            ) {
              Text(
                text = fallbackText,
                style = MaterialTheme.typography.bodySmall,
                color = FigmaInk,
                modifier = Modifier.padding(10.dp)
              )
            }
          }
        }
      },
      confirmButton = {
        if (unavailableFallbackMessage != null) {
          Button(
            onClick = {
              WhatsAppIntentHelper.copyMessageToClipboard(context, unavailableFallbackMessage!!)
              whatsAppUnavailableMessage = null
              unavailableFallbackMessage = null
              scope.launch { snackbarHostState.showSnackbar("Message copied to clipboard.") }
            },
            colors = ButtonDefaults.buttonColors(containerColor = FigmaTeal, contentColor = FigmaWhite)
          ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Copy Message")
          }
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            whatsAppUnavailableMessage = null
            unavailableFallbackMessage = null
          }
        ) {
          Text("Close", color = FigmaMuted)
        }
      }
    )
  }
}

@Composable
private fun CustomerReminderCard(
  row: CustomerReminderRowUi,
  isBusy: Boolean,
  onViewLedger: () -> Unit,
  onOpenHistory: () -> Unit,
  onPrepareReminder: () -> Unit
) {
  val customer = row.customer
  val latest = row.latestReminder

  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = FigmaWhite),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, FigmaLine, RoundedCornerShape(12.dp))
      .testTag("reminder_customer_card_${customer.id}")
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = customer.shopName,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = FigmaInk,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            if (!customer.isActive) {
              StatusPill(text = "Archived", textColor = FigmaMuted, containerColor = FigmaBackground)
            }
          }
          if (customer.ownerName.isNotBlank()) {
            Text(
              text = customer.ownerName,
              style = MaterialTheme.typography.bodySmall,
              color = FigmaMuted
            )
          }
          Text(
            text = if (row.phoneValidation.isValid) {
              "WhatsApp: ${row.phoneValidation.normalizedE164}"
            } else {
              "Phone: ${customer.phoneNumber.ifBlank { "Missing" }} (Invalid)"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (row.phoneValidation.isValid) FigmaMuted else FigmaRed
          )
        }

        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = if (row.netOutstandingPaise < 0L) "Advance" else "Outstanding",
            style = MaterialTheme.typography.labelSmall,
            color = FigmaMuted
          )
          Text(
            text = if (row.netOutstandingPaise < 0L) {
              SubledgerCalculationEngine.formatPaise(kotlin.math.abs(row.netOutstandingPaise))
            } else {
              SubledgerCalculationEngine.formatPaise(row.netOutstandingPaise)
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = when {
              row.netOutstandingPaise > 0L -> FigmaInk
              row.netOutstandingPaise < 0L -> FigmaTeal
              else -> FigmaMuted
            }
          )
          Text(
            text = if (row.unpaidInvoiceCount > 0) {
              "${row.unpaidInvoiceCount} pending invoice(s)"
            } else if (row.netOutstandingPaise > 0L) {
              "Opening / Account Due"
            } else {
              "Settled"
            },
            style = MaterialTheme.typography.labelSmall,
            color = FigmaMuted
          )
        }
      }

      HorizontalDivider(thickness = 1.dp, color = FigmaLine)

      // Last Reminder Date & Status Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        val lastTimestamp = latest?.confirmedSentAt ?: latest?.whatsappOpenedAt ?: latest?.createdAt
        Text(
          text = "Last reminder: ${if (lastTimestamp != null) SubledgerCalculationEngine.formatDate(lastTimestamp) else "Never"}",
          style = MaterialTheme.typography.labelSmall,
          color = FigmaMuted
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          ReminderStatusBadge(status = latest?.status)
          IconButton(
            onClick = onOpenHistory,
            modifier = Modifier
              .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
              .testTag("reminder_history_btn_${customer.id}")
          ) {
            Icon(
              imageVector = Icons.Default.History,
              contentDescription = "Reminder History for ${customer.shopName}",
              tint = FigmaNavy,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }

      // Blocking notice if phone missing/invalid for debtor
      if (row.netOutstandingPaise > 0L && !row.phoneValidation.isValid) {
        Text(
          text = row.phoneValidation.errorMessage ?: "Invalid customer phone number",
          style = MaterialTheme.typography.labelSmall,
          color = FigmaRed
        )
      }

      // Actions: View Ledger + WhatsApp Reminder
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedButton(
          onClick = onViewLedger,
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = FigmaNavy),
          modifier = Modifier
            .weight(1f)
            .defaultMinSize(minHeight = 48.dp)
            .testTag("view_ledger_btn_${customer.id}")
        ) {
          Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("View Ledger", fontSize = 13.sp, maxLines = 1)
        }

        Button(
          onClick = onPrepareReminder,
          enabled = row.isEligibleForReminder && !isBusy,
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = FigmaTeal,
            contentColor = FigmaWhite,
            disabledContainerColor = FigmaLine,
            disabledContentColor = FigmaMuted
          ),
          modifier = Modifier
            .weight(1.25f)
            .defaultMinSize(minHeight = 48.dp)
            .testTag("whatsapp_reminder_btn_${customer.id}")
        ) {
          Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("WhatsApp Reminder", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}

@Composable
private fun ReminderStatusBadge(status: ReminderStatus?) {
  val (label, textColor, containerColor) = when (status) {
    null -> Triple("Not Reminded", FigmaMuted, FigmaBackground)
    ReminderStatus.DRAFT -> Triple("Draft", FigmaAmber, FigmaAmber.copy(alpha = 0.12f))
    ReminderStatus.WHATSAPP_OPENED -> Triple("WhatsApp Opened", FigmaNavy, FigmaSoftTeal)
    ReminderStatus.USER_CONFIRMED_SENT -> Triple("Confirmed Sent", FigmaTeal, FigmaSoftTeal)
    ReminderStatus.FAILED -> Triple("Failed", FigmaRed, FigmaRed.copy(alpha = 0.10f))
  }
  StatusPill(text = label, textColor = textColor, containerColor = containerColor)
}

@Composable
private fun StatusPill(
  text: String,
  textColor: Color,
  containerColor: Color
) {
  Surface(
    shape = RoundedCornerShape(6.dp),
    color = containerColor
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.SemiBold,
      color = textColor,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
    )
  }
}

@Composable
private fun ReminderPreviewDialog(
  preview: ReminderPreviewUiState,
  onDismiss: () -> Unit,
  onCopyMessage: () -> Unit,
  onLaunchWhatsApp: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = FigmaWhite,
    title = {
      Column {
        Text(
          text = "WhatsApp Reminder Preview",
          fontWeight = FontWeight.Bold,
          color = FigmaInk
        )
        Text(
          text = "${preview.customer.shopName} • ${preview.composed.targetPhoneE164}",
          style = MaterialTheme.typography.bodySmall,
          color = FigmaMuted
        )
      }
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
      ) {
        if (preview.balanceChangeNotice != null) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = FigmaAmber.copy(alpha = 0.12f),
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, FigmaAmber, RoundedCornerShape(8.dp))
              .testTag("reminder_balance_changed_notice")
          ) {
            Text(
              text = preview.balanceChangeNotice,
              style = MaterialTheme.typography.bodySmall,
              color = FigmaAmber,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(10.dp)
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          StatusPill(
            text = preview.composed.dueKind.displayName,
            textColor = FigmaTeal,
            containerColor = FigmaSoftTeal
          )
          Text(
            text = "Due: ${SubledgerCalculationEngine.formatPaise(preview.composed.netOutstandingPaise)}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = FigmaInk
          )
        }

        Surface(
          shape = RoundedCornerShape(10.dp),
          color = FigmaBackground,
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FigmaLine, RoundedCornerShape(10.dp))
        ) {
          Text(
            text = preview.composed.renderedMessage,
            style = MaterialTheme.typography.bodySmall,
            color = FigmaInk,
            modifier = Modifier
              .padding(12.dp)
              .testTag("reminder_preview_message_text")
          )
        }

        TextButton(
          onClick = onCopyMessage,
          modifier = Modifier.align(Alignment.End)
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp), tint = FigmaTeal)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Copy Message", color = FigmaTeal)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = onLaunchWhatsApp,
        enabled = !preview.isLaunching,
        colors = ButtonDefaults.buttonColors(containerColor = FigmaTeal, contentColor = FigmaWhite),
        modifier = Modifier
          .defaultMinSize(minHeight = 48.dp)
          .testTag("open_whatsapp_btn")
      ) {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(if (preview.isLaunching) "Verifying..." else "Open WhatsApp")
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        enabled = !preview.isLaunching,
        modifier = Modifier.testTag("cancel_preview_btn")
      ) {
        Text("Cancel", color = FigmaMuted)
      }
    }
  )
}

@Composable
internal fun ReminderSendConfirmationDialog(
  customerName: String,
  reminder: CustomerPaymentReminder,
  onConfirmSent: () -> Unit,
  onNotSent: () -> Unit,
  onFailed: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onNotSent,
    containerColor = FigmaWhite,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = FigmaTeal)
        Text("Confirm Reminder Sent?", fontWeight = FontWeight.Bold, color = FigmaInk)
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "Did you review and send the WhatsApp reminder to $customerName (${reminder.targetPhoneE164})?",
          style = MaterialTheme.typography.bodyMedium,
          color = FigmaInk
        )
        Text(
          text = "Outstanding shown: ${SubledgerCalculationEngine.formatPaise(reminder.snapshotNetOutstandingPaise)}",
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.SemiBold,
          color = FigmaMuted
        )
        Text(
          text = "Opening WhatsApp does not automatically mark a reminder as sent. Confirm only if you actually tapped Send in WhatsApp.",
          style = MaterialTheme.typography.labelSmall,
          color = FigmaMuted
        )
      }
    },
    confirmButton = {
      Button(
        onClick = onConfirmSent,
        colors = ButtonDefaults.buttonColors(containerColor = FigmaTeal, contentColor = FigmaWhite),
        modifier = Modifier
          .defaultMinSize(minHeight = 48.dp)
          .testTag("confirm_sent_yes_btn")
      ) {
        Text("Yes, Sent")
      }
    },
    dismissButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(
          onClick = onFailed,
          modifier = Modifier.testTag("confirm_sent_failed_btn")
        ) {
          Text("Failed", color = FigmaRed)
        }
        TextButton(
          onClick = onNotSent,
          modifier = Modifier.testTag("confirm_sent_no_btn")
        ) {
          Text("No, Not Sent", color = FigmaMuted)
        }
      }
    }
  )
}

@Composable
private fun CustomerReminderHistoryDialog(
  customer: Customer,
  history: List<CustomerPaymentReminder>,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = FigmaWhite,
    title = {
      Column {
        Text("Reminder Audit History", fontWeight = FontWeight.Bold, color = FigmaInk)
        Text(customer.shopName, style = MaterialTheme.typography.bodySmall, color = FigmaMuted)
      }
    },
    text = {
      if (history.isEmpty()) {
        Text(
          text = "No reminder attempts recorded for this customer yet.",
          style = MaterialTheme.typography.bodySmall,
          color = FigmaMuted
        )
      } else {
        Column(
          verticalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .testTag("reminder_history_list")
        ) {
          history.forEach { item ->
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = FigmaBackground,
              modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, FigmaLine, RoundedCornerShape(10.dp))
                .testTag("reminder_history_item_${item.id}")
            ) {
              Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  ReminderStatusBadge(status = item.status)
                  Text(
                    text = SubledgerCalculationEngine.formatPaise(item.snapshotNetOutstandingPaise),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = FigmaInk
                  )
                }
                Text(
                  text = "${item.dueKind.displayName} • ${item.targetPhoneE164}",
                  style = MaterialTheme.typography.labelSmall,
                  color = FigmaMuted
                )
                Text(
                  text = "Prepared: ${SubledgerCalculationEngine.formatDate(item.createdAt)}" +
                    (item.whatsappOpenedAt?.let { " • Opened: ${SubledgerCalculationEngine.formatDate(it)}" } ?: "") +
                    (item.confirmedSentAt?.let { " • Confirmed: ${SubledgerCalculationEngine.formatDate(it)}" } ?: ""),
                  style = MaterialTheme.typography.labelSmall,
                  color = FigmaMuted
                )
                if (!item.failureReason.isNullOrBlank()) {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = FigmaRed, modifier = Modifier.size(14.dp))
                    Text(
                      text = item.failureReason,
                      style = MaterialTheme.typography.labelSmall,
                      color = FigmaRed
                    )
                  }
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Close", color = FigmaTeal, fontWeight = FontWeight.SemiBold)
      }
    }
  )
}
