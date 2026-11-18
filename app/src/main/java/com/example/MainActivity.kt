package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.IceTrackDatabase
import com.example.data.sync.CloudSyncRepository
import com.example.data.sync.IceTrackSyncDatabase
import com.example.ui.auth.GoogleSignInManager
import com.example.ui.screens.AuthScreen
import com.example.ui.viewmodel.CloudSyncViewModel
import kotlinx.coroutines.launch
import com.example.data.repository.AuditRepository
import com.example.data.repository.CreditDebitNoteRepository
import com.example.data.repository.CustomerRepository
import com.example.data.repository.ExpenseRepository
import com.example.data.repository.InventoryRepository
import com.example.data.repository.InvoiceLifecycleRepository
import com.example.data.repository.PaymentReminderRepository
import com.example.data.repository.ProductRepository
import com.example.data.repository.PurchaseRepository
import com.example.data.repository.RefundRepository
import com.example.data.repository.ReturnRepository
import com.example.data.repository.SalesRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.SubledgerRepository
import com.example.data.repository.SupplierRepository
import com.example.service.NotificationService
import com.example.ui.navigation.Screen
import com.example.ui.screens.CreditDebitNotesAuditScreen
import com.example.ui.screens.CustomerListScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExpensesScreen
import com.example.ui.screens.InventoryStockScreen
import com.example.ui.screens.MoreScreen
import com.example.ui.screens.OperationsPlaceholderScreen
import com.example.ui.screens.PaymentReminderDashboardScreen
import com.example.ui.screens.ProductListScreen
import com.example.ui.screens.PurchaseManagementScreen
import com.example.ui.screens.ReminderSendConfirmationDialog
import com.example.ui.screens.SalesBillingScreen
import com.example.ui.screens.ReportsAnalyticsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SubledgerScreen
import com.example.ui.screens.SupplierListScreen
import com.example.ui.theme.FigmaBackground
import com.example.ui.theme.FigmaInk
import com.example.ui.theme.FigmaLine
import com.example.ui.theme.FigmaMuted
import com.example.ui.theme.FigmaNavy
import com.example.ui.theme.FigmaSoftTeal
import com.example.ui.theme.FigmaTeal
import com.example.ui.theme.FigmaWhite
import com.example.ui.theme.IceTrackTheme
import com.example.ui.viewmodel.CustomerViewModel
import com.example.ui.viewmodel.DashboardViewModel
import com.example.ui.viewmodel.ExpenseViewModel
import com.example.ui.viewmodel.FinanceLifecycleViewModel
import com.example.ui.viewmodel.InventoryViewModel
import com.example.ui.viewmodel.PaymentReminderViewModel
import com.example.ui.viewmodel.ProductViewModel
import com.example.ui.viewmodel.PurchaseViewModel
import com.example.ui.viewmodel.ReportsViewModel
import com.example.ui.viewmodel.SalesViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.example.ui.viewmodel.SupplierViewModel

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    NotificationService.createNotificationChannels(applicationContext)

    val database = IceTrackDatabase.getInstance(applicationContext)
    val productRepo = ProductRepository(database)
    val supplierRepo = SupplierRepository(database)
    val customerRepo = CustomerRepository(database)
    val subledgerRepo = SubledgerRepository(database)
    val settingsRepo = SettingsRepository(database)
    val inventoryRepo = InventoryRepository(database)
    val purchaseRepo = PurchaseRepository(database)
    val salesRepo = SalesRepository(database)
    val expenseRepo = ExpenseRepository(database)
    val reminderRepo = PaymentReminderRepository(database)
    val returnRepo = ReturnRepository(database)
    val noteRepo = CreditDebitNoteRepository(database)
    val refundRepo = RefundRepository(database)
    val auditRepo = AuditRepository(database)
    val lifecycleRepo = InvoiceLifecycleRepository(database)

    val dashboardViewModel by viewModels<DashboardViewModel> {
      DashboardViewModel.Factory(
        productRepo,
        supplierRepo,
        customerRepo,
        subledgerRepo,
        salesRepo,
        purchaseRepo,
        inventoryRepo
      )
    }
    val productViewModel by viewModels<ProductViewModel> {
      ProductViewModel.Factory(productRepo, supplierRepo, inventoryRepo)
    }
    val supplierViewModel by viewModels<SupplierViewModel> {
      SupplierViewModel.Factory(supplierRepo, subledgerRepo)
    }
    val customerViewModel by viewModels<CustomerViewModel> {
      CustomerViewModel.Factory(customerRepo, productRepo, subledgerRepo)
    }
    val settingsViewModel by viewModels<SettingsViewModel> {
      SettingsViewModel.Factory(settingsRepo, database)
    }
    val inventoryViewModel by viewModels<InventoryViewModel> {
      InventoryViewModel.Factory(inventoryRepo, productRepo)
    }
    val purchaseViewModel by viewModels<PurchaseViewModel> {
      PurchaseViewModel.Factory(purchaseRepo, supplierRepo, productRepo, subledgerRepo, lifecycleRepo, returnRepo)
    }
    val salesViewModel by viewModels<SalesViewModel> {
      SalesViewModel.Factory(salesRepo, customerRepo, productRepo, inventoryRepo, subledgerRepo, lifecycleRepo, returnRepo)
    }
    val expenseViewModel by viewModels<ExpenseViewModel> {
      ExpenseViewModel.Factory(expenseRepo)
    }
    val reportsViewModel by viewModels<ReportsViewModel> {
      ReportsViewModel.Factory(
        salesRepo = salesRepo,
        purchaseRepo = purchaseRepo,
        productRepo = productRepo,
        customerRepo = customerRepo,
        supplierRepo = supplierRepo,
        inventoryRepo = inventoryRepo,
        expenseRepo = expenseRepo,
        subledgerRepo = subledgerRepo,
        returnRepo = returnRepo
      )
    }
    val paymentReminderViewModel by viewModels<PaymentReminderViewModel> {
      PaymentReminderViewModel.Factory(
        customerRepository = customerRepo,
        salesRepository = salesRepo,
        subledgerRepository = subledgerRepo,
        reminderRepository = reminderRepo
      )
    }
    val financeLifecycleViewModel by viewModels<FinanceLifecycleViewModel> {
      FinanceLifecycleViewModel.Factory(
        subledgerRepo = subledgerRepo,
        noteRepo = noteRepo,
        refundRepo = refundRepo,
        auditRepo = auditRepo,
        customerRepo = customerRepo,
        supplierRepo = supplierRepo,
        salesRepo = salesRepo,
        purchaseRepo = purchaseRepo
      )
    }

    val syncDb = IceTrackSyncDatabase.getInstance(applicationContext)
    val cloudSyncRepo = CloudSyncRepository(
      context = applicationContext,
      localDb = database,
      syncDb = syncDb
    )

    setContent {
      IceTrackTheme {
        val currentUser by GoogleSignInManager.authStateFlow()
          .collectAsStateWithLifecycle(initialValue = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser)
        val context = LocalContext.current
        val appScope = rememberCoroutineScope()

        if (currentUser == null) {
          AuthScreen(
            onAuthenticated = {
              // authStateFlow updates automatically when signInWithCredential completes
            }
          )
        } else {
          val signedInUser = currentUser!!
          key(signedInUser.uid) {
            val cloudSyncViewModel: CloudSyncViewModel = viewModel(
              key = "cloud_sync_${signedInUser.uid}",
              factory = CloudSyncViewModel.Factory(
                uid = signedInUser.uid,
                userEmail = signedInUser.email ?: "",
                userDisplayName = signedInUser.displayName ?: "",
                syncRepository = cloudSyncRepo
              )
            )
            MainAppContent(
              dashboardViewModel = dashboardViewModel,
              productViewModel = productViewModel,
              supplierViewModel = supplierViewModel,
              customerViewModel = customerViewModel,
              settingsViewModel = settingsViewModel,
              inventoryViewModel = inventoryViewModel,
              purchaseViewModel = purchaseViewModel,
              salesViewModel = salesViewModel,
              expenseViewModel = expenseViewModel,
              reportsViewModel = reportsViewModel,
              paymentReminderViewModel = paymentReminderViewModel,
              financeLifecycleViewModel = financeLifecycleViewModel,
              subledgerRepo = subledgerRepo,
              cloudSyncViewModel = cloudSyncViewModel,
              onSignOut = {
                appScope.launch {
                  GoogleSignInManager.signOutSafely(
                    context = context,
                    onBeforeSignOutCleanup = { cloudSyncViewModel.cleanupOnSignOut() }
                  )
                }
              }
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
  dashboardViewModel: DashboardViewModel,
  productViewModel: ProductViewModel,
  supplierViewModel: SupplierViewModel,
  customerViewModel: CustomerViewModel,
  settingsViewModel: SettingsViewModel,
  inventoryViewModel: InventoryViewModel,
  purchaseViewModel: PurchaseViewModel,
  salesViewModel: SalesViewModel,
  expenseViewModel: ExpenseViewModel,
  reportsViewModel: ReportsViewModel,
  paymentReminderViewModel: PaymentReminderViewModel,
  financeLifecycleViewModel: FinanceLifecycleViewModel,
  subledgerRepo: SubledgerRepository,
  cloudSyncViewModel: CloudSyncViewModel? = null,
  onSignOut: (() -> Unit)? = null
) {
  var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }
  var selectedLedgerCustomerId by remember { mutableStateOf<Long?>(null) }
  var selectedLedgerCustomerName by remember { mutableStateOf<String?>(null) }
  var returnToRemindersFromLedger by remember { mutableStateOf(false) }
  val globalPendingConfirmation by paymentReminderViewModel.pendingConfirmation.collectAsState()
  val customerShopNamesById by paymentReminderViewModel.customerShopNamesById.collectAsState()

  // BackHandler: Return from customer-filtered ledger to Reminders, or sub-screens to Dashboard
  if (currentScreen != Screen.DASHBOARD) {
    BackHandler {
      if (currentScreen == Screen.LEDGER && returnToRemindersFromLedger) {
        selectedLedgerCustomerId = null
        selectedLedgerCustomerName = null
        returnToRemindersFromLedger = false
        currentScreen = Screen.PAYMENT_REMINDERS
      } else if (currentScreen == Screen.NOTES_REFUNDS_AUDIT) {
        currentScreen = Screen.MORE
      } else {
        selectedLedgerCustomerId = null
        selectedLedgerCustomerName = null
        returnToRemindersFromLedger = false
        currentScreen = Screen.DASHBOARD
      }
    }
  }

  val isRootDestination = currentScreen in listOf(
    Screen.DASHBOARD,
    Screen.STOCK,
    Screen.SALES,
    Screen.PURCHASE,
    Screen.LEDGER,
    Screen.REPORTS
  ) && !(currentScreen == Screen.LEDGER && returnToRemindersFromLedger)

  Scaffold(
    containerColor = FigmaBackground,
    topBar = {
      CenterAlignedTopAppBar(
        title = {
          Text(
            text = when (currentScreen) {
              Screen.DASHBOARD -> "IceTrack"
              Screen.PRODUCTS -> "Product Catalog"
              Screen.CUSTOMERS -> "Retail Shops"
              Screen.SUPPLIERS -> "Suppliers"
              Screen.PAYMENT_REMINDERS -> "Payment Reminders"
              Screen.NOTES_REFUNDS_AUDIT -> "Notes, Refunds & Audit"
              Screen.LEDGER -> "Transaction Subledger"
              Screen.SETTINGS -> "Business Settings"
              Screen.STOCK -> "Inventory Stock"
              Screen.SALES -> "Sales & Billing"
              Screen.PURCHASE -> "Purchases"
              Screen.EXPENSES -> "Business Expenses"
              Screen.REPORTS -> "Reports & Analytics"
              Screen.MORE -> "More Menu"
            },
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = FigmaInk
          )
        },
        navigationIcon = {
          if (!isRootDestination) {
            IconButton(
              onClick = {
                if (currentScreen == Screen.LEDGER && returnToRemindersFromLedger) {
                  selectedLedgerCustomerId = null
                  selectedLedgerCustomerName = null
                  returnToRemindersFromLedger = false
                  currentScreen = Screen.PAYMENT_REMINDERS
                } else if (currentScreen == Screen.NOTES_REFUNDS_AUDIT) {
                  currentScreen = Screen.MORE
                } else {
                  selectedLedgerCustomerId = null
                  selectedLedgerCustomerName = null
                  returnToRemindersFromLedger = false
                  currentScreen = Screen.DASHBOARD
                }
              },
              modifier = Modifier.testTag("nav_back_button")
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = if (currentScreen == Screen.LEDGER && returnToRemindersFromLedger) {
                  "Back to Payment Reminders"
                } else {
                  "Back to Dashboard"
                },
                tint = FigmaNavy
              )
            }
          }
        },
        actions = {
          IconButton(
            onClick = { currentScreen = Screen.SETTINGS },
            modifier = Modifier.testTag("nav_settings_button")
          ) {
            Icon(
              imageVector = Icons.Default.Settings,
              contentDescription = "Settings",
              tint = if (currentScreen == Screen.SETTINGS) FigmaTeal else FigmaMuted
            )
          }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
          containerColor = FigmaWhite,
          titleContentColor = FigmaInk
        ),
        modifier = Modifier.border(0.5.dp, FigmaLine)
      )
    },
    bottomBar = {
      NavigationBar(
        containerColor = FigmaWhite,
        modifier = Modifier
          .testTag("bottom_nav_bar")
          .border(0.5.dp, FigmaLine)
      ) {
        NavigationBarItem(
          selected = currentScreen == Screen.DASHBOARD,
          onClick = { currentScreen = Screen.DASHBOARD },
          icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
          label = { Text("Dashboard", fontSize = 10.sp, maxLines = 1, fontWeight = if (currentScreen == Screen.DASHBOARD) FontWeight.Bold else FontWeight.Normal) },
          colors = NavigationBarItemDefaults.colors(
            indicatorColor = FigmaSoftTeal,
            selectedIconColor = FigmaTeal,
            selectedTextColor = FigmaTeal,
            unselectedIconColor = FigmaMuted,
            unselectedTextColor = FigmaMuted
          )
        )
        NavigationBarItem(
          selected = currentScreen == Screen.STOCK,
          onClick = { currentScreen = Screen.STOCK },
          icon = { Icon(Icons.Default.Inventory2, contentDescription = "Stock") },
          label = { Text("Stock", fontSize = 10.sp, maxLines = 1, fontWeight = if (currentScreen == Screen.STOCK) FontWeight.Bold else FontWeight.Normal) },
          colors = NavigationBarItemDefaults.colors(
            indicatorColor = FigmaSoftTeal,
            selectedIconColor = FigmaTeal,
            selectedTextColor = FigmaTeal,
            unselectedIconColor = FigmaMuted,
            unselectedTextColor = FigmaMuted
          )
        )
        NavigationBarItem(
          selected = currentScreen == Screen.SALES,
          onClick = { currentScreen = Screen.SALES },
          icon = { Icon(Icons.Default.PointOfSale, contentDescription = "Sales") },
          label = { Text("Sales", fontSize = 10.sp, maxLines = 1, fontWeight = if (currentScreen == Screen.SALES) FontWeight.Bold else FontWeight.Normal) },
          colors = NavigationBarItemDefaults.colors(
            indicatorColor = FigmaSoftTeal,
            selectedIconColor = FigmaTeal,
            selectedTextColor = FigmaTeal,
            unselectedIconColor = FigmaMuted,
            unselectedTextColor = FigmaMuted
          )
        )
        NavigationBarItem(
          selected = currentScreen == Screen.PURCHASE,
          onClick = { currentScreen = Screen.PURCHASE },
          icon = { Icon(Icons.Default.ShoppingCart, contentDescription = "Purchase") },
          label = { Text("Purchase", fontSize = 10.sp, maxLines = 1, fontWeight = if (currentScreen == Screen.PURCHASE) FontWeight.Bold else FontWeight.Normal) },
          colors = NavigationBarItemDefaults.colors(
            indicatorColor = FigmaSoftTeal,
            selectedIconColor = FigmaTeal,
            selectedTextColor = FigmaTeal,
            unselectedIconColor = FigmaMuted,
            unselectedTextColor = FigmaMuted
          )
        )
        NavigationBarItem(
          selected = currentScreen == Screen.LEDGER,
          onClick = {
            selectedLedgerCustomerId = null
            selectedLedgerCustomerName = null
            returnToRemindersFromLedger = false
            currentScreen = Screen.LEDGER
          },
          icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Ledger") },
          label = { Text("Ledger", fontSize = 10.sp, maxLines = 1, fontWeight = if (currentScreen == Screen.LEDGER) FontWeight.Bold else FontWeight.Normal) },
          colors = NavigationBarItemDefaults.colors(
            indicatorColor = FigmaSoftTeal,
            selectedIconColor = FigmaTeal,
            selectedTextColor = FigmaTeal,
            unselectedIconColor = FigmaMuted,
            unselectedTextColor = FigmaMuted
          )
        )
        NavigationBarItem(
          selected = currentScreen in listOf(Screen.REPORTS, Screen.MORE, Screen.PRODUCTS, Screen.SUPPLIERS, Screen.CUSTOMERS, Screen.EXPENSES, Screen.PAYMENT_REMINDERS, Screen.NOTES_REFUNDS_AUDIT),
          onClick = { currentScreen = Screen.MORE },
          icon = { Icon(Icons.Default.Assessment, contentDescription = "More") },
          label = { Text("More", fontSize = 10.sp, maxLines = 1, fontWeight = if (currentScreen in listOf(Screen.REPORTS, Screen.MORE, Screen.PRODUCTS, Screen.SUPPLIERS, Screen.CUSTOMERS, Screen.EXPENSES, Screen.PAYMENT_REMINDERS, Screen.NOTES_REFUNDS_AUDIT)) FontWeight.Bold else FontWeight.Normal) },
          colors = NavigationBarItemDefaults.colors(
            indicatorColor = FigmaSoftTeal,
            selectedIconColor = FigmaTeal,
            selectedTextColor = FigmaTeal,
            unselectedIconColor = FigmaMuted,
            unselectedTextColor = FigmaMuted
          )
        )
      }
    },
    modifier = Modifier.fillMaxSize()
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(FigmaBackground)
        .padding(innerPadding)
    ) {
      when (currentScreen) {
        Screen.DASHBOARD -> DashboardScreen(
          viewModel = dashboardViewModel,
          onNavigate = { currentScreen = it },
          onQuickAddProduct = {
            productViewModel.openAddDialog()
            currentScreen = Screen.PRODUCTS
          },
          onQuickAddCustomer = {
            customerViewModel.openAddDialog()
            currentScreen = Screen.CUSTOMERS
          },
          onQuickAddSupplier = {
            supplierViewModel.openAddDialog()
            currentScreen = Screen.SUPPLIERS
          }
        )
        Screen.PRODUCTS -> ProductListScreen(viewModel = productViewModel)
        Screen.CUSTOMERS -> CustomerListScreen(viewModel = customerViewModel)
        Screen.SUPPLIERS -> SupplierListScreen(viewModel = supplierViewModel)
        Screen.PAYMENT_REMINDERS -> PaymentReminderDashboardScreen(
          viewModel = paymentReminderViewModel,
          showPendingConfirmationDialog = false,
          onViewCustomerLedger = { customer ->
            selectedLedgerCustomerId = customer.id
            selectedLedgerCustomerName = customer.shopName
            returnToRemindersFromLedger = true
            currentScreen = Screen.LEDGER
          }
        )
        Screen.NOTES_REFUNDS_AUDIT -> CreditDebitNotesAuditScreen(viewModel = financeLifecycleViewModel)
        Screen.LEDGER -> SubledgerScreen(
          subledgerRepository = subledgerRepo,
          filteredCustomerId = selectedLedgerCustomerId,
          filteredCustomerName = selectedLedgerCustomerName,
          onClearCustomerFilter = {
            selectedLedgerCustomerId = null
            selectedLedgerCustomerName = null
            returnToRemindersFromLedger = false
          },
          financeViewModel = financeLifecycleViewModel,
          onOpenNotesAndAudit = { currentScreen = Screen.NOTES_REFUNDS_AUDIT }
        )
        Screen.SETTINGS -> SettingsScreen(
          viewModel = settingsViewModel,
          cloudSyncViewModel = cloudSyncViewModel,
          onSignOut = onSignOut
        )
        Screen.MORE -> MoreScreen(onNavigate = {
          if (it == Screen.LEDGER) {
            selectedLedgerCustomerId = null
            selectedLedgerCustomerName = null
            returnToRemindersFromLedger = false
          }
          currentScreen = it
        })
        Screen.STOCK -> InventoryStockScreen(
          viewModel = inventoryViewModel,
          onNavigateToPurchase = { currentScreen = Screen.PURCHASE }
        )
        Screen.SALES -> SalesBillingScreen(viewModel = salesViewModel)
        Screen.PURCHASE -> PurchaseManagementScreen(viewModel = purchaseViewModel)
        Screen.EXPENSES -> ExpensesScreen(viewModel = expenseViewModel)
        Screen.REPORTS -> ReportsAnalyticsScreen(viewModel = reportsViewModel)
      }
    }
  }

  // Global process-death recovery prompt: surfaces even if OS kills app during WhatsApp switch and restarts at Dashboard
  globalPendingConfirmation?.let { pending ->
    val customerName = customerShopNamesById[pending.customerId] ?: "Customer #${pending.customerId}"
    ReminderSendConfirmationDialog(
      customerName = customerName,
      reminder = pending,
      onConfirmSent = { paymentReminderViewModel.confirmPendingReminderSent(pending.id) },
      onNotSent = { paymentReminderViewModel.markPendingReminderNotSent(pending.id) },
      onFailed = { paymentReminderViewModel.markPendingReminderFailed(pending.id) }
    )
  }
}
