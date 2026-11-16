package com.example

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.IceTrackDatabase
import com.example.data.repository.CustomerRepository
import com.example.data.repository.PaymentReminderRepository
import com.example.data.repository.SalesRepository
import com.example.data.repository.SubledgerRepository
import com.example.domain.model.Customer
import com.example.domain.model.ReminderStatus
import com.example.ui.screens.PaymentReminderDashboardScreen
import com.example.ui.screens.SubledgerScreen
import com.example.ui.theme.IceTrackTheme
import com.example.ui.viewmodel.LaunchWhatsAppCommand
import com.example.ui.viewmodel.PaymentReminderViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-420dpi")
class CustomerPaymentReminderUiTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var context: Context
  private lateinit var db: IceTrackDatabase
  private lateinit var customerRepo: CustomerRepository
  private lateinit var salesRepo: SalesRepository
  private lateinit var subledgerRepo: SubledgerRepository
  private lateinit var reminderRepo: PaymentReminderRepository

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, IceTrackDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    customerRepo = CustomerRepository(db)
    salesRepo = SalesRepository(db)
    subledgerRepo = SubledgerRepository(db)
    reminderRepo = PaymentReminderRepository(db)
  }

  @After
  fun tearDown() {
    db.close()
  }

  private fun createViewModel(): PaymentReminderViewModel {
    return PaymentReminderViewModel(customerRepo, salesRepo, subledgerRepo, reminderRepo)
  }

  private fun waitForTag(tag: String) {
    composeTestRule.waitUntil(timeoutMillis = 5000L) {
      ShadowLooper.idleMainLooper()
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
  }

  @Test
  fun dashboard_outstandingOnlyDefaultAndFiltersAndDisabledInvalidPhone(): Unit {
    var debtorValidId = 0L
    var debtorInvalidId = 0L
    var settledId = 0L

    runBlocking {
      debtorValidId = customerRepo.saveCustomer(
        Customer(shopName = "Alpha Dairy", ownerName = "Arjun", phoneNumber = "9876543210", address = "Kochi", openingBalancePaise = 45000L)
      )
      debtorInvalidId = customerRepo.saveCustomer(
        Customer(shopName = "Beta Bakery", ownerName = "Binu", phoneNumber = "0484222", address = "Kochi", openingBalancePaise = 20000L)
      )
      settledId = customerRepo.saveCustomer(
        Customer(shopName = "Gamma Settled", ownerName = "Chitra", phoneNumber = "9847000111", address = "Kochi", openingBalancePaise = 0L)
      )
    }

    val vm = createViewModel()
    composeTestRule.setContent {
      IceTrackTheme {
        PaymentReminderDashboardScreen(
          viewModel = vm,
          onViewCustomerLedger = {}
        )
      }
    }

    composeTestRule.waitUntil(timeoutMillis = 5000L) {
      ShadowLooper.idleMainLooper()
      vm.dashboardState.value.rows.size == 2
    }
    composeTestRule.waitForIdle()

    // Default outstandingOnly = true hides settled customer
    composeTestRule.onNodeWithTag("reminder_customer_list").performScrollToKey(debtorValidId)
    composeTestRule.onNodeWithTag("reminder_customer_card_$debtorValidId").assertIsDisplayed()
    composeTestRule.onNodeWithTag("whatsapp_reminder_btn_$debtorValidId").assertIsEnabled()

    composeTestRule.onNodeWithTag("reminder_customer_list").performScrollToKey(debtorInvalidId)
    composeTestRule.onNodeWithTag("reminder_customer_card_$debtorInvalidId").assertIsDisplayed()
    composeTestRule.onNodeWithTag("whatsapp_reminder_btn_$debtorInvalidId").assertIsNotEnabled()
    assertTrue(composeTestRule.onAllNodesWithTag("reminder_customer_card_$settledId").fetchSemanticsNodes().isEmpty())

    // Toggle outstandingOnly OFF -> settled customer appears (disabled button)
    composeTestRule.onNodeWithTag("reminder_outstanding_only_switch").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5000L) {
      ShadowLooper.idleMainLooper()
      vm.dashboardState.value.rows.size == 3
    }
    composeTestRule.onNodeWithTag("reminder_customer_list").performScrollToKey(settledId)
    composeTestRule.onNodeWithTag("whatsapp_reminder_btn_$settledId").assertIsNotEnabled()

    // Search filter narrows to Alpha Dairy
    composeTestRule.onNodeWithTag("reminder_search_input").performTextInput("Alpha")
    composeTestRule.waitUntil(timeoutMillis = 5000L) {
      ShadowLooper.idleMainLooper()
      vm.dashboardState.value.rows.size == 1
    }
    composeTestRule.onNodeWithTag("reminder_customer_card_$debtorValidId").assertIsDisplayed()
  }

  @Test
  fun dashboard_viewLedgerNavigatesToCustomerFilteredSubledger(): Unit {
    var cust1Id = 0L
    var cust2Id = 0L
    runBlocking {
      cust1Id = customerRepo.saveCustomer(
        Customer(shopName = "Palakkad Sweets", ownerName = "Hari", phoneNumber = "9876543210", address = "Palakkad", openingBalancePaise = 30000L)
      )
      cust2Id = customerRepo.saveCustomer(
        Customer(shopName = "Wayanad Parlour", ownerName = "Jijo", phoneNumber = "9847000222", address = "Wayanad", openingBalancePaise = 60000L)
      )
    }

    val vm = createViewModel()
    var navigatedCustomer by mutableStateOf<Customer?>(null)

    composeTestRule.setContent {
      IceTrackTheme {
        val selected = navigatedCustomer
        if (selected == null) {
          PaymentReminderDashboardScreen(
            viewModel = vm,
            onViewCustomerLedger = { navigatedCustomer = it }
          )
        } else {
          SubledgerScreen(
            subledgerRepository = subledgerRepo,
            filteredCustomerId = selected.id,
            filteredCustomerName = selected.shopName
          )
        }
      }
    }

    waitForTag("view_ledger_btn_$cust2Id")
    composeTestRule.onNodeWithTag("view_ledger_btn_$cust2Id").performClick()

    waitForTag("subledger_customer_filter_banner")
    assertEquals(cust2Id, navigatedCustomer?.id)
    composeTestRule.onNodeWithText("Customer Ledger: Wayanad Parlour").assertIsDisplayed()
    assertTrue(composeTestRule.onAllNodesWithTag("view_ledger_btn_$cust1Id").fetchSemanticsNodes().isEmpty())
  }

  @Test
  fun previewWhatsAppConfirmationAndDuplicateTapProtection(): Unit {
    var custId = 0L
    runBlocking {
      custId = customerRepo.saveCustomer(
        Customer(shopName = "Ernakulam Scoops", ownerName = "Manu", phoneNumber = "9876543210", address = "Ernakulam", openingBalancePaise = 50000L)
      )
    }

    val vm = createViewModel()
    val launchedCommands = mutableListOf<LaunchWhatsAppCommand>()

    composeTestRule.setContent {
      IceTrackTheme {
        PaymentReminderDashboardScreen(
          viewModel = vm,
          onViewCustomerLedger = {},
          onLaunchWhatsAppIntent = { launchedCommands.add(it) }
        )
      }
    }

    waitForTag("whatsapp_reminder_btn_$custId")

    val cust = runBlocking { customerRepo.allActiveCustomers.first().first { it.id == custId } }
    vm.prepareReminderPreview(cust, nowMs = 1000L)
    vm.prepareReminderPreview(cust, nowMs = 1100L)

    waitForTag("open_whatsapp_btn")
    composeTestRule.onNodeWithTag("reminder_preview_message_text").assertIsDisplayed()

    composeTestRule.onNodeWithTag("open_whatsapp_btn").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5000L) {
      ShadowLooper.idleMainLooper()
      launchedCommands.size == 1 &&
        composeTestRule.onAllNodesWithTag("confirm_sent_yes_btn").fetchSemanticsNodes().isNotEmpty()
    }
    assertEquals("919876543210", launchedCommands.single().whatsappPhoneDigits)

    composeTestRule.onNodeWithTag("confirm_sent_yes_btn").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5000L) {
      ShadowLooper.idleMainLooper()
      composeTestRule.onAllNodesWithTag("confirm_sent_yes_btn").fetchSemanticsNodes().isEmpty() &&
        runBlocking { reminderRepo.observeHistoryForCustomer(custId).first().any { it.status == ReminderStatus.USER_CONFIRMED_SENT } }
    }

    val history = runBlocking { reminderRepo.observeHistoryForCustomer(custId).first() }
    assertEquals(1, history.size)
    assertEquals(ReminderStatus.USER_CONFIRMED_SENT, history.single().status)
  }

  @Test
  fun missingWhatsAppApp_showsFallbackDialogAndMarksReminderFailed(): Unit {
    var custId = 0L
    runBlocking {
      custId = customerRepo.saveCustomer(
        Customer(shopName = "Fallback Cafe", ownerName = "Raju", phoneNumber = "9876543210", address = "Kochi", openingBalancePaise = 25000L)
      )
    }

    val vm = createViewModel()
    composeTestRule.setContent {
      IceTrackTheme {
        PaymentReminderDashboardScreen(
          viewModel = vm,
          onViewCustomerLedger = {},
          onLaunchWhatsAppIntent = {
            throw ActivityNotFoundException("No WhatsApp")
          }
        )
      }
    }

    waitForTag("whatsapp_reminder_btn_$custId")
    composeTestRule.onNodeWithTag("whatsapp_reminder_btn_$custId").performClick()
    waitForTag("open_whatsapp_btn")

    composeTestRule.onNodeWithTag("open_whatsapp_btn").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5000L) {
      ShadowLooper.idleMainLooper()
      composeTestRule.onAllNodesWithText("WhatsApp Unavailable").fetchSemanticsNodes().isNotEmpty() &&
        runBlocking {
          reminderRepo.observeHistoryForCustomer(custId).first().any {
            it.status == ReminderStatus.FAILED
          }
        }
    }
    composeTestRule.onNodeWithText("WhatsApp Unavailable").assertIsDisplayed()
    composeTestRule.onNodeWithText("Copy Message").assertIsDisplayed()

    val latest = runBlocking { reminderRepo.observeHistoryForCustomer(custId).first().first() }
    assertEquals(ReminderStatus.FAILED, latest.status)
    assertEquals("WhatsApp app not installed on device", latest.failureReason)
  }
}
