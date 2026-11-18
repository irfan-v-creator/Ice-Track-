package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.IceTrackDatabase
import com.example.data.local.entity.*
import com.example.data.remote.FirestoreFinanceMappers
import com.example.data.remote.FirestoreSyncMappers
import com.example.data.sync.CloudRestoreApplicator
import com.example.data.sync.IceTrackSyncDatabase
import com.example.data.sync.SyncCollectionSpec
import com.example.data.sync.SyncRecordStateEntity
import com.example.domain.engine.CloudSyncConflictEngine
import com.example.domain.engine.SyncComparisonDecision
import com.example.domain.engine.SyncStatus
import com.example.domain.model.*
import com.example.service.BackupRestoreService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FirebaseSyncAndConflictTest {

  private lateinit var localDb: IceTrackDatabase
  private lateinit var syncDb: IceTrackSyncDatabase

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    localDb = Room.inMemoryDatabaseBuilder(context, IceTrackDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    syncDb = Room.inMemoryDatabaseBuilder(context, IceTrackSyncDatabase::class.java)
      .allowMainThreadQueries()
      .build()
  }

  @After
  fun tearDown() {
    localDb.close()
    syncDb.close()
  }

  @Test
  fun conflictEngine_threeWayDetectionNeverSilentlyOverwritesDivergedEdits() {
    val baseline = "hash_v1"
    // Both local & cloud changed from baseline -> must flag CONFLICT_NEEDS_REVIEW
    val diverged = CloudSyncConflictEngine.classifySyncAction(
      localHash = "hash_local_v2",
      cloudHash = "hash_cloud_v2",
      lastSyncedHash = baseline,
      localUpdatedAtMs = 2000L,
      cloudUpdatedAtMs = 3000L,
      localRevision = 2,
      cloudRevision = 3
    )
    assertEquals(SyncComparisonDecision.CONFLICT_NEEDS_REVIEW, diverged)

    // Only local edited -> PUSH_TO_CLOUD
    val localOnly = CloudSyncConflictEngine.classifySyncAction(
      localHash = "hash_local_v2",
      cloudHash = baseline,
      lastSyncedHash = baseline,
      localUpdatedAtMs = 2000L,
      cloudUpdatedAtMs = 1000L
    )
    assertEquals(SyncComparisonDecision.PUSH_TO_CLOUD, localOnly)

    // Only cloud edited -> PULL_TO_LOCAL
    val cloudOnly = CloudSyncConflictEngine.classifySyncAction(
      localHash = baseline,
      cloudHash = "hash_cloud_v2",
      lastSyncedHash = baseline,
      localUpdatedAtMs = 1000L,
      cloudUpdatedAtMs = 2000L
    )
    assertEquals(SyncComparisonDecision.PULL_TO_LOCAL, cloudOnly)

    // Matching hashes -> NO_CHANGE (prevents duplicate upload loops)
    val unchanged = CloudSyncConflictEngine.classifySyncAction(
      localHash = "hash_v2",
      cloudHash = "hash_v2",
      lastSyncedHash = baseline,
      localUpdatedAtMs = 2000L,
      cloudUpdatedAtMs = 2000L
    )
    assertEquals(SyncComparisonDecision.NO_CHANGE, unchanged)
  }

  @Test
  fun backoffSchedule_computesIncreasingDelaysCappedSafely() {
    val base = 1_000_000L
    assertEquals(base + 30_000L, CloudSyncConflictEngine.computeNextRetryAtMs(0, base))
    assertEquals(base + 120_000L, CloudSyncConflictEngine.computeNextRetryAtMs(1, base))
    assertEquals(base + 600_000L, CloudSyncConflictEngine.computeNextRetryAtMs(2, base))
    assertEquals(base + 1_800_000L, CloudSyncConflictEngine.computeNextRetryAtMs(3, base))
    assertEquals(base + 1_800_000L, CloudSyncConflictEngine.computeNextRetryAtMs(99, base))
  }

  @Test
  fun mappers_preserveNullOmissionNoGhostIdAndConsistentHashRoundTrip() = runBlocking {
    val uid = "uid_test_123"
    val product = ProductEntity(
      id = 7L,
      name = "Butterscotch Cone",
      sku = "BS-07",
      category = "Cones",
      defaultPurchasePricePaise = 1800L,
      defaultSellingPricePaise = 3000L,
      minStockLevelPieces = 12,
      isActive = true,
      createdAt = 1_700_000_000_000L,
      deletedAt = null
    )
    val unit = ProductUnitEntity(id = 7L, productId = 7L, piecesPerBox = 12, boxesPerCarton = 4)

    val (fields, hash1) = FirestoreSyncMappers.toProductFields(uid, product, unit, 1_700_000_000_000L)
    assertFalse("Firestore payload must never contain 'id'", fields.containsKey("id"))
    assertFalse("Optional null deletedAtEpochMs must be omitted", fields.containsKey("deletedAtEpochMs"))
    assertTrue("No is-prefixed boolean field; must use 'active'", fields.containsKey("active"))
    assertFalse(fields.containsKey("isActive"))

    val (rebuiltProduct, rebuiltUnit) = FirestoreSyncMappers.fromProductDoc(fields)
    val (_, hash2) = FirestoreSyncMappers.toProductFields(uid, rebuiltProduct, rebuiltUnit, 1_700_000_000_000L)
    assertEquals(hash1, hash2)
  }

  @Test
  fun syncDatabaseAndRestoreApplicator_restoreSoftCancelledInvoiceAuditAndV5Backup() = runBlocking {
    val uid = "uid_restore_test"
    val now = 1_700_050_000_000L

    // Seed remote payload bundle representing cancelled invoice, reversal subledger, and audit trail
    val customer = CustomerEntity(
      id = 1L,
      shopName = "Malabar Cool Bar",
      ownerName = "Hameed",
      phoneNumber = "+919895111222",
      address = "Calicut",
      openingBalancePaise = 15000L,
      openingBalanceDate = now - 10_000L,
      creditLimitPaise = 500000L,
      paymentTermsDays = 7,
      isActive = true,
      createdAt = now - 10_000L
    )
    val cancelledInvoice = SalesInvoiceEntity(
      id = 12L,
      invoiceNumber = "INV-2026-0012",
      customerId = 1L,
      invoiceDate = now - 5_000L,
      dueDate = now + 86_400_000L,
      totalAmountPaise = 45000L,
      paidAmountPaise = 0L,
      paymentStatus = PaymentStatus.UNPAID,
      paymentMethod = null,
      notes = "Cancelled due to shop closure",
      isConfirmed = true,
      createdAt = now - 5_000L,
      subtotalPaise = 45000L,
      invoiceStatus = InvoiceStatus.CANCELLED,
      cancelledAt = now - 1_000L,
      cancellationReason = "Shop closed",
      revisionVersion = 2,
      updatedAt = now - 1_000L,
      partyNameSnapshot = "Malabar Cool Bar"
    )
    val auditLog = FinancialAuditLogEntity(
      id = 99L,
      operationType = AuditOperationType.INVOICE_CANCEL,
      entityType = "SALES_INVOICE",
      entityId = 12L,
      relatedPartyType = PartyType.CUSTOMER,
      relatedPartyId = 1L,
      actorLabel = "Owner",
      reason = "Shop closed",
      beforeJson = "{\"status\":\"ACTIVE\"}",
      afterJson = "{\"status\":\"CANCELLED\"}",
      operationReference = "CAN-12",
      createdAt = now - 1_000L
    )

    val (custMap, _) = FirestoreSyncMappers.toCustomerFields(uid, customer, now)
    val (invMap, _) = FirestoreSyncMappers.toSalesInvoiceFields(uid, cancelledInvoice)
    val (auditMap, _) = FirestoreFinanceMappers.toAuditLogFields(uid, auditLog)

    CloudRestoreApplicator.applyBundleTransactionally(
      db = localDb,
      bundle = CloudRestoreApplicator.RemoteSnapshotBundle(
        customers = listOf(FirestoreSyncMappers.fromCustomerDoc(custMap)),
        salesInvoices = listOf(FirestoreSyncMappers.fromSalesInvoiceDoc(invMap)),
        auditLogs = listOf(FirestoreFinanceMappers.fromAuditLogDoc(auditMap))
      )
    )

    // Verify Room V5 persisted pulled entities cleanly including openingBalance, cancellation, & audit log
    val restoredCustomer = localDb.customerDao().getCustomerSync(1L)
    assertEquals(15000L, restoredCustomer?.openingBalancePaise)
    val restoredInvoice = localDb.salesInvoiceDao().getById(12L)
    assertEquals(InvoiceStatus.CANCELLED, restoredInvoice?.invoiceStatus)
    assertEquals(2, restoredInvoice?.revisionVersion)
    assertEquals("Shop closed", restoredInvoice?.cancellationReason)
    assertEquals(1, localDb.financialAuditLogDao().getAllSync().size)

    // Verify SyncStateDao tracks pending/conflict/backoff queue cleanly in separate DB
    syncDb.syncStateDao().upsertRecord(
      SyncRecordStateEntity(
        ownerUid = uid,
        entityType = "sales_invoices",
        localKey = "12",
        localId = 12L,
        cloudDocId = "12",
        displayLabel = "Sales INV-2026-0012",
        syncStatus = SyncStatus.CONFLICT.name,
        lastSyncedHash = "prev",
        currentLocalHash = "local_cur",
        remoteConflictHash = "remote_cur",
        remotePayloadJson = SyncCollectionSpec.serializeRemotePayloadForConflict(invMap),
        localRevision = 2,
        remoteRevision = 3
      )
    )
    assertEquals(1, syncDb.syncStateDao().getConflictsSync(uid).size)

    // Verify V5 JSON backup & restore compatibility remains intact
    val backupJson = BackupRestoreService.exportDatabaseFromDatabase(localDb)
    val validation = BackupRestoreService.validateBackupJson(backupJson)
    assertTrue(validation.isValid)
    assertEquals(5, validation.schemaVersion)
    assertEquals(1, validation.customerCount)
    assertEquals(1, validation.salesCount)
    assertEquals(1, validation.auditLogCount)
    assertNotEquals("", backupJson)
  }
}
