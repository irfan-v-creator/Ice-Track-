package com.example.data.sync

import android.content.Context
import com.example.R
import com.example.data.local.IceTrackDatabase
import com.example.data.remote.FirestoreErrorHandler
import com.example.data.remote.FirestoreSyncMappers
import com.example.data.remote.OperationType
import com.example.domain.engine.CloudSyncConflictEngine
import com.example.domain.engine.ConflictCandidate
import com.example.domain.engine.ConflictResolutionStrategy
import com.example.domain.engine.SyncComparisonDecision
import com.example.domain.engine.SyncStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.firestoreSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class SyncSummary(
  val uploadedCount: Int,
  val pulledCount: Int,
  val conflictCount: Int,
  val skippedRetryBackoffCount: Int,
  val syncedAtEpochMs: Long
)

class CloudSyncRepository(
  private val context: Context,
  private val localDb: IceTrackDatabase,
  private val syncDb: IceTrackSyncDatabase,
  private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
  private val firestoreProvider: () -> FirebaseFirestore = {
    createConfiguredFirestore(context)
  }
) {
  private val syncMutex = Mutex()
  private val specs = SyncCollectionSpec.buildAllSpecs()
  private val activeListeners = mutableListOf<ListenerRegistration>()

  private val firestore: FirebaseFirestore by lazy { firestoreProvider() }

  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("Google Sign-In required before accessing Firestore.")

  fun observeWorkspaceMeta(uid: String): Flow<SyncWorkspaceMetaEntity?> =
    syncDb.syncStateDao().observeWorkspaceMeta(uid)

  fun observeConflicts(uid: String): Flow<List<ConflictCandidate>> =
    syncDb.syncStateDao().observeConflicts(uid).map { rows ->
      rows.map { r ->
        ConflictCandidate(
          entityType = r.entityType,
          localId = r.localId,
          cloudDocId = r.cloudDocId,
          displayLabel = r.displayLabel,
          localContentHash = r.currentLocalHash,
          cloudContentHash = r.remoteConflictHash ?: "",
          lastSyncedHash = r.lastSyncedHash,
          localUpdatedAtMs = r.localUpdatedAtMs,
          cloudUpdatedAtMs = r.remoteUpdatedAtMs,
          localRevision = r.localRevision,
          cloudRevision = r.remoteRevision,
          cloudPayloadJson = r.remotePayloadJson ?: "{}"
        )
      }
    }

  /**
   * User-scoped realtime workspace observer so changes pushed from another signed-in device
   * surface immediately in UI state. Emits Pair(lastSyncAtEpochMs, errorOrNull).
   */
  fun observeRemoteWorkspaceVersion(uid: String): Flow<Long?> = callbackFlow {
    if (auth.currentUser?.uid != uid) {
      trySend(null)
      awaitClose { }
      return@callbackFlow
    }
    val docPath = "users/$uid"
    val registration = firestore.document(docPath)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          FirestoreErrorHandler.handleFirestoreError(error, OperationType.GET, docPath)
          close(error)
          return@addSnapshotListener
        }
        val remoteSyncMs = snapshot?.getLong("lastSyncAtEpochMs")
        trySend(remoteSyncMs)
      }
    synchronized(activeListeners) { activeListeners.add(registration) }
    awaitClose {
      registration.remove()
      synchronized(activeListeners) { activeListeners.remove(registration) }
    }
  }

  suspend fun detachAllListeners() {
    val snapshot = synchronized(activeListeners) {
      val copy = activeListeners.toList()
      activeListeners.clear()
      copy
    }
    snapshot.forEach { runCatching { it.remove() } }
  }

  suspend fun refreshLocalPendingQueueSummary(): SyncWorkspaceMetaEntity? = withContext(Dispatchers.IO) {
    val uid = auth.currentUser?.uid ?: return@withContext null
    val now = System.currentTimeMillis()
    val dao = syncDb.syncStateDao()
    val existing = dao.getAllForUserSync(uid).associateBy { "${it.entityType}:${it.localKey}" }
    val updates = mutableListOf<SyncRecordStateEntity>()

    for (spec in specs) {
      val localRows = spec.loadLocalRows(uid, localDb, now)
      for (row in localRows) {
        val stateKey = "${spec.collectionName}:${row.localKey}"
        val prev = existing[stateKey]
        if (prev == null || prev.lastSyncedHash != row.contentHash) {
          if (prev?.syncStatus != SyncStatus.CONFLICT.name) {
            updates.add(
              SyncRecordStateEntity(
                ownerUid = uid,
                entityType = spec.collectionName,
                localKey = row.localKey,
                localId = row.localId,
                cloudDocId = row.cloudDocId,
                displayLabel = row.displayLabel,
                syncStatus = SyncStatus.PENDING_UPLOAD.name,
                lastSyncedHash = prev?.lastSyncedHash,
                currentLocalHash = row.contentHash,
                localRevision = row.revisionVersion,
                localUpdatedAtMs = row.localUpdatedAtMs,
                lastSyncedAtMs = prev?.lastSyncedAtMs,
                retryCount = prev?.retryCount ?: 0,
                nextRetryAtMs = prev?.nextRetryAtMs ?: 0L,
                lastError = prev?.lastError
              )
            )
          }
        }
      }
    }
    if (updates.isNotEmpty()) dao.upsertAllRecords(updates)
    recomputeWorkspaceMeta(uid, lastSyncedAtMs = null, lastError = null)
  }

  suspend fun syncNow(forceRetryFailed: Boolean = true): Result<SyncSummary> = syncMutex.withLock {
    withContext(Dispatchers.IO) {
      val uid = try {
        requireUserId()
      } catch (e: Exception) {
        return@withContext Result.failure(e)
      }

      val now = System.currentTimeMillis()
      val syncDao = syncDb.syncStateDao()
      val userDocPath = "users/$uid"

      try {
        val user = auth.currentUser
        val businessName = localDb.appSettingDao().getSync("business_name")?.value
        val workspaceRef = firestore.document(userDocPath)
        val workspaceSnap = workspaceRef.get().await()
        if (!workspaceSnap.exists()) {
          workspaceRef.set(
            FirestoreSyncMappers.workspaceCreateMap(
              uid = uid,
              email = user?.email,
              displayName = user?.displayName,
              businessName = businessName,
              lastSyncAtEpochMs = now
            )
          ).await()
        } else {
          workspaceRef.update(
            FirestoreSyncMappers.workspaceUpdateMap(
              email = user?.email,
              displayName = user?.displayName,
              businessName = businessName,
              lastSyncAtEpochMs = now
            )
          ).await()
        }

        val existingStates = syncDao.getAllForUserSync(uid).associateBy { "${it.entityType}:${it.localKey}" }
        var uploadedCount = 0
        var conflictCount = 0
        var skippedRetry = 0
        var pulledBundle = CloudRestoreApplicator.RemoteSnapshotBundle()
        val stateUpserts = mutableListOf<SyncRecordStateEntity>()

        for (spec in specs) {
          val colPath = "$userDocPath/${spec.collectionName}"
          val colRef = firestore.collection(colPath)
          val remoteQuerySnap = try {
            colRef.get().await()
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.LIST, colPath)
            throw e
          }

          val remoteByDocId = remoteQuerySnap.documents.associateBy { it.id }
          val localRows = spec.loadLocalRows(uid, localDb, now)
          val localByDocId = localRows.associateBy { it.cloudDocId }

          // 1. Evaluate all local records against remote snapshots + baseline
          for (row in localRows) {
            val stateKey = "${spec.collectionName}:${row.localKey}"
            val prevState = existingStates[stateKey]
            val remoteDoc = remoteByDocId[row.cloudDocId]
            val remoteData = remoteDoc?.let { FirestoreSyncMappers.serverEstimate(it) }
            val remoteHash = remoteData?.get("contentHash") as? String
            val remoteRev = remoteData?.let { spec.extractRemoteRevision(it) }
            val remoteUpd = remoteData?.let { spec.extractRemoteUpdatedAtMs(it) }

            if (!forceRetryFailed &&
              prevState?.syncStatus == SyncStatus.FAILED.name &&
              now < prevState.nextRetryAtMs
            ) {
              skippedRetry++
              continue
            }

            if (prevState?.syncStatus == SyncStatus.CONFLICT.name &&
              prevState.remoteConflictHash == remoteHash &&
              prevState.currentLocalHash == row.contentHash
            ) {
              conflictCount++
              continue
            }

            val decision = CloudSyncConflictEngine.classifySyncAction(
              localHash = row.contentHash,
              cloudHash = remoteHash,
              lastSyncedHash = prevState?.lastSyncedHash,
              localUpdatedAtMs = row.localUpdatedAtMs,
              cloudUpdatedAtMs = remoteUpd,
              localRevision = row.revisionVersion,
              cloudRevision = remoteRev
            )

            when (decision) {
              SyncComparisonDecision.NO_CHANGE -> {
                stateUpserts.add(
                  SyncRecordStateEntity(
                    ownerUid = uid,
                    entityType = spec.collectionName,
                    localKey = row.localKey,
                    localId = row.localId,
                    cloudDocId = row.cloudDocId,
                    displayLabel = row.displayLabel,
                    syncStatus = SyncStatus.SYNCED.name,
                    lastSyncedHash = row.contentHash,
                    currentLocalHash = row.contentHash,
                    localRevision = row.revisionVersion,
                    remoteRevision = remoteRev ?: row.revisionVersion,
                    localUpdatedAtMs = row.localUpdatedAtMs,
                    remoteUpdatedAtMs = remoteUpd ?: row.localUpdatedAtMs,
                    lastSyncedAtMs = now
                  )
                )
              }

              SyncComparisonDecision.PULL_TO_LOCAL -> {
                if (remoteData != null && remoteHash != null) {
                  pulledBundle = CloudRestoreApplicator.parseConflictCandidateToApply(
                    entityType = spec.collectionName,
                    payload = remoteData,
                    bundle = pulledBundle
                  )
                  stateUpserts.add(
                    SyncRecordStateEntity(
                      ownerUid = uid,
                      entityType = spec.collectionName,
                      localKey = row.localKey,
                      localId = row.localId,
                      cloudDocId = row.cloudDocId,
                      displayLabel = row.displayLabel,
                      syncStatus = SyncStatus.SYNCED.name,
                      lastSyncedHash = remoteHash,
                      currentLocalHash = remoteHash,
                      localRevision = remoteRev ?: 1,
                      remoteRevision = remoteRev ?: 1,
                      localUpdatedAtMs = remoteUpd ?: row.localUpdatedAtMs,
                      remoteUpdatedAtMs = remoteUpd ?: row.localUpdatedAtMs,
                      lastSyncedAtMs = now
                    )
                  )
                }
              }

              SyncComparisonDecision.CONFLICT_NEEDS_REVIEW -> {
                conflictCount++
                stateUpserts.add(
                  SyncRecordStateEntity(
                    ownerUid = uid,
                    entityType = spec.collectionName,
                    localKey = row.localKey,
                    localId = row.localId,
                    cloudDocId = row.cloudDocId,
                    displayLabel = row.displayLabel,
                    syncStatus = SyncStatus.CONFLICT.name,
                    lastSyncedHash = prevState?.lastSyncedHash,
                    currentLocalHash = row.contentHash,
                    remoteConflictHash = remoteHash,
                    remotePayloadJson = remoteData?.let { SyncCollectionSpec.serializeRemotePayloadForConflict(it) },
                    localRevision = row.revisionVersion,
                    remoteRevision = remoteRev ?: 1,
                    localUpdatedAtMs = row.localUpdatedAtMs,
                    remoteUpdatedAtMs = remoteUpd ?: 0L,
                    lastSyncedAtMs = prevState?.lastSyncedAtMs
                  )
                )
              }

              SyncComparisonDecision.PUSH_TO_CLOUD -> {
                val docPath = "$colPath/${row.cloudDocId}"
                try {
                  if (remoteDoc == null || !remoteDoc.exists()) {
                    val createPayload = FirestoreSyncMappers.attachCreateTimestamps(
                      row.fields.toMutableMap(),
                      hasUpdatedAt = !spec.appendOnly
                    )
                    colRef.document(row.cloudDocId).set(createPayload).await()
                    uploadedCount++
                    stateUpserts.add(
                      SyncRecordStateEntity(
                        ownerUid = uid,
                        entityType = spec.collectionName,
                        localKey = row.localKey,
                        localId = row.localId,
                        cloudDocId = row.cloudDocId,
                        displayLabel = row.displayLabel,
                        syncStatus = SyncStatus.SYNCED.name,
                        lastSyncedHash = row.contentHash,
                        currentLocalHash = row.contentHash,
                        localRevision = row.revisionVersion,
                        remoteRevision = row.revisionVersion,
                        localUpdatedAtMs = row.localUpdatedAtMs,
                        remoteUpdatedAtMs = row.localUpdatedAtMs,
                        lastSyncedAtMs = now
                      )
                    )
                  } else if (spec.appendOnly || remoteData == null || !spec.canUpdateRemote(row.fields, remoteData)) {
                    conflictCount++
                    stateUpserts.add(
                      SyncRecordStateEntity(
                        ownerUid = uid,
                        entityType = spec.collectionName,
                        localKey = row.localKey,
                        localId = row.localId,
                        cloudDocId = row.cloudDocId,
                        displayLabel = row.displayLabel,
                        syncStatus = SyncStatus.CONFLICT.name,
                        lastSyncedHash = prevState?.lastSyncedHash,
                        currentLocalHash = row.contentHash,
                        remoteConflictHash = remoteHash,
                        remotePayloadJson = remoteData?.let { SyncCollectionSpec.serializeRemotePayloadForConflict(it) },
                        localRevision = row.revisionVersion,
                        remoteRevision = remoteRev ?: 1,
                        localUpdatedAtMs = row.localUpdatedAtMs,
                        remoteUpdatedAtMs = remoteUpd ?: 0L,
                        lastSyncedAtMs = prevState?.lastSyncedAtMs
                      )
                    )
                  } else {
                    val updatePayload = FirestoreSyncMappers.attachUpdateTimestamp(
                      fullMap = row.fields,
                      immutableKeys = spec.immutableUpdateKeys
                    )
                    colRef.document(row.cloudDocId).update(updatePayload).await()
                    uploadedCount++
                    stateUpserts.add(
                      SyncRecordStateEntity(
                        ownerUid = uid,
                        entityType = spec.collectionName,
                        localKey = row.localKey,
                        localId = row.localId,
                        cloudDocId = row.cloudDocId,
                        displayLabel = row.displayLabel,
                        syncStatus = SyncStatus.SYNCED.name,
                        lastSyncedHash = row.contentHash,
                        currentLocalHash = row.contentHash,
                        localRevision = row.revisionVersion,
                        remoteRevision = row.revisionVersion,
                        localUpdatedAtMs = row.localUpdatedAtMs,
                        remoteUpdatedAtMs = row.localUpdatedAtMs,
                        lastSyncedAtMs = now
                      )
                    )
                  }
                } catch (e: CancellationException) {
                  throw e
                } catch (e: Exception) {
                  FirestoreErrorHandler.handleFirestoreError(e, OperationType.WRITE, docPath)
                  val nextRetryCount = (prevState?.retryCount ?: 0) + 1
                  stateUpserts.add(
                    SyncRecordStateEntity(
                      ownerUid = uid,
                      entityType = spec.collectionName,
                      localKey = row.localKey,
                      localId = row.localId,
                      cloudDocId = row.cloudDocId,
                      displayLabel = row.displayLabel,
                      syncStatus = SyncStatus.FAILED.name,
                      lastSyncedHash = prevState?.lastSyncedHash,
                      currentLocalHash = row.contentHash,
                      localRevision = row.revisionVersion,
                      remoteRevision = remoteRev ?: 1,
                      localUpdatedAtMs = row.localUpdatedAtMs,
                      remoteUpdatedAtMs = remoteUpd ?: 0L,
                      lastSyncedAtMs = prevState?.lastSyncedAtMs,
                      retryCount = nextRetryCount,
                      nextRetryAtMs = CloudSyncConflictEngine.computeNextRetryAtMs(nextRetryCount - 1, now),
                      lastError = FirestoreErrorHandler.userFriendlyMessage(e)
                    )
                  )
                }
              }
            }
          }

          // 2. Evaluate cloud-only documents (e.g., created on another device / new install restore)
          for ((docId, remoteDoc) in remoteByDocId) {
            if (docId in localByDocId) continue
            val remoteData = FirestoreSyncMappers.serverEstimate(remoteDoc)
            val remoteHash = remoteData["contentHash"] as? String ?: continue
            val localKey = (remoteData["settingKey"] as? String)
              ?: (remoteData["localId"] as? Number)?.toLong()?.toString()
              ?: docId
            val localId = (remoteData["localId"] as? Number)?.toLong() ?: 0L

            pulledBundle = CloudRestoreApplicator.parseConflictCandidateToApply(
              entityType = spec.collectionName,
              payload = remoteData,
              bundle = pulledBundle
            )
            stateUpserts.add(
              SyncRecordStateEntity(
                ownerUid = uid,
                entityType = spec.collectionName,
                localKey = localKey,
                localId = localId,
                cloudDocId = docId,
                displayLabel = "${spec.collectionName} #$localKey",
                syncStatus = SyncStatus.SYNCED.name,
                lastSyncedHash = remoteHash,
                currentLocalHash = remoteHash,
                localRevision = spec.extractRemoteRevision(remoteData),
                remoteRevision = spec.extractRemoteRevision(remoteData),
                localUpdatedAtMs = spec.extractRemoteUpdatedAtMs(remoteData),
                remoteUpdatedAtMs = spec.extractRemoteUpdatedAtMs(remoteData),
                lastSyncedAtMs = now
              )
            )
          }
        }

        val pulledCount = pulledBundle.totalCount()
        if (pulledCount > 0) {
          CloudRestoreApplicator.applyBundleTransactionally(localDb, pulledBundle)
        }
        if (stateUpserts.isNotEmpty()) {
          syncDao.upsertAllRecords(stateUpserts)
        }
        recomputeWorkspaceMeta(uid, lastSyncedAtMs = now, lastError = null)

        Result.success(
          SyncSummary(
            uploadedCount = uploadedCount,
            pulledCount = pulledCount,
            conflictCount = conflictCount,
            skippedRetryBackoffCount = skippedRetry,
            syncedAtEpochMs = now
          )
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        val msg = FirestoreErrorHandler.userFriendlyMessage(e)
        recomputeWorkspaceMeta(uid, lastSyncedAtMs = null, lastError = msg)
        Result.failure(e)
      }
    }
  }

  suspend fun resolveConflict(
    candidate: ConflictCandidate,
    strategy: ConflictResolutionStrategy
  ): Result<Unit> = syncMutex.withLock {
    withContext(Dispatchers.IO) {
      val uid = try {
        requireUserId()
      } catch (e: Exception) {
        return@withContext Result.failure(e)
      }
      val now = System.currentTimeMillis()
      val syncDao = syncDb.syncStateDao()
      val spec = specs.firstOrNull { it.collectionName == candidate.entityType }
        ?: return@withContext Result.failure(IllegalArgumentException("Unknown entityType ${candidate.entityType}"))

      val localKey = if (spec.collectionName == "app_settings") {
        SyncCollectionSpec.parseRemotePayloadForConflict(candidate.cloudPayloadJson)["settingKey"] as? String
          ?: candidate.cloudDocId
      } else {
        candidate.localId.toString()
      }

      val docPath = "users/$uid/${spec.collectionName}/${candidate.cloudDocId}"
      try {
        when (strategy) {
          ConflictResolutionStrategy.APPLY_CLOUD_TO_LOCAL -> {
            val payload = SyncCollectionSpec.parseRemotePayloadForConflict(candidate.cloudPayloadJson)
            val bundle = CloudRestoreApplicator.parseConflictCandidateToApply(
              entityType = spec.collectionName,
              payload = payload,
              bundle = CloudRestoreApplicator.RemoteSnapshotBundle()
            )
            CloudRestoreApplicator.applyBundleTransactionally(localDb, bundle)
            syncDao.upsertRecord(
              SyncRecordStateEntity(
                ownerUid = uid,
                entityType = spec.collectionName,
                localKey = localKey,
                localId = candidate.localId,
                cloudDocId = candidate.cloudDocId,
                displayLabel = candidate.displayLabel,
                syncStatus = SyncStatus.SYNCED.name,
                lastSyncedHash = candidate.cloudContentHash,
                currentLocalHash = candidate.cloudContentHash,
                localRevision = candidate.cloudRevision,
                remoteRevision = candidate.cloudRevision,
                localUpdatedAtMs = candidate.cloudUpdatedAtMs,
                remoteUpdatedAtMs = candidate.cloudUpdatedAtMs,
                lastSyncedAtMs = now
              )
            )
          }

          ConflictResolutionStrategy.KEEP_LOCAL_OVERWRITE_CLOUD -> {
            val row = spec.loadLocalRows(uid, localDb, now).firstOrNull { it.cloudDocId == candidate.cloudDocId }
              ?: return@withContext Result.failure(IllegalStateException("Local record no longer exists."))
            val docRef = firestore.document(docPath)
            val remoteSnap = docRef.get().await()
            if (!remoteSnap.exists()) {
              val createMap = FirestoreSyncMappers.attachCreateTimestamps(row.fields.toMutableMap(), !spec.appendOnly)
              docRef.set(createMap).await()
            } else {
              if (spec.appendOnly) {
                return@withContext Result.failure(
                  IllegalStateException("Immutable financial/audit records cannot overwrite cloud history. Choose Apply Cloud instead.")
                )
              }
              val adjustedFields = row.fields.toMutableMap()
              if (adjustedFields.containsKey("localUpdatedAtMs")) {
                adjustedFields["localUpdatedAtMs"] = maxOf(
                  (adjustedFields["localUpdatedAtMs"] as? Number)?.toLong() ?: 0L,
                  candidate.cloudUpdatedAtMs + 1L
                )
              }
              if (adjustedFields.containsKey("revisionVersion")) {
                adjustedFields["revisionVersion"] = maxOf(
                  (adjustedFields["revisionVersion"] as? Number)?.toInt() ?: 1,
                  candidate.cloudRevision
                )
              }
              val (rehashedFields, rehash) = FirestoreSyncMappers.businessFieldsWithHash(adjustedFields)
              val remoteData = FirestoreSyncMappers.serverEstimate(remoteSnap)
              if (!spec.canUpdateRemote(rehashedFields, remoteData)) {
                return@withContext Result.failure(
                  IllegalStateException("Cloud record reached a locked financial state and cannot be overwritten.")
                )
              }
              val updateMap = FirestoreSyncMappers.attachUpdateTimestamp(rehashedFields, spec.immutableUpdateKeys)
              docRef.update(updateMap).await()
              syncDao.upsertRecord(
                SyncRecordStateEntity(
                  ownerUid = uid,
                  entityType = spec.collectionName,
                  localKey = row.localKey,
                  localId = row.localId,
                  cloudDocId = row.cloudDocId,
                  displayLabel = row.displayLabel,
                  syncStatus = SyncStatus.SYNCED.name,
                  lastSyncedHash = rehash,
                  currentLocalHash = rehash,
                  localRevision = row.revisionVersion,
                  remoteRevision = row.revisionVersion,
                  localUpdatedAtMs = row.localUpdatedAtMs,
                  remoteUpdatedAtMs = row.localUpdatedAtMs,
                  lastSyncedAtMs = now
                )
              )
              recomputeWorkspaceMeta(uid, lastSyncedAtMs = now, lastError = null)
              return@withContext Result.success(Unit)
            }
          }
        }
        recomputeWorkspaceMeta(uid, lastSyncedAtMs = now, lastError = null)
        Result.success(Unit)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, docPath)
        Result.failure(e)
      }
    }
  }

  private suspend fun recomputeWorkspaceMeta(
    uid: String,
    lastSyncedAtMs: Long?,
    lastError: String?
  ): SyncWorkspaceMetaEntity {
    val dao = syncDb.syncStateDao()
    val prev = dao.getWorkspaceMetaSync(uid)
    val meta = SyncWorkspaceMetaEntity(
      ownerUid = uid,
      lastSuccessfulSyncAtMs = lastSyncedAtMs ?: prev?.lastSuccessfulSyncAtMs,
      lastPullAtMs = lastSyncedAtMs ?: prev?.lastPullAtMs,
      lastErrorMessage = lastError,
      pendingCount = dao.countByStatusSync(uid, SyncStatus.PENDING_UPLOAD.name),
      syncedCount = dao.countByStatusSync(uid, SyncStatus.SYNCED.name),
      conflictCount = dao.countByStatusSync(uid, SyncStatus.CONFLICT.name),
      failedCount = dao.countByStatusSync(uid, SyncStatus.FAILED.name)
    )
    dao.upsertWorkspaceMeta(meta)
    return meta
  }

  companion object {
    fun createConfiguredFirestore(context: Context): FirebaseFirestore {
      val databaseId = context.getString(R.string.firestore_database_id)
      val instance = FirebaseFirestore.getInstance(databaseId)
      runCatching {
        instance.firestoreSettings = firestoreSettings {
          setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
        }
      }
      return instance
    }
  }
}
