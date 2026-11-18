package com.example.data.sync

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.domain.engine.SyncStatus
import kotlinx.coroutines.flow.Flow

@Entity(
  tableName = "sync_record_states",
  primaryKeys = ["ownerUid", "entityType", "localKey"],
  indices = [
    Index(value = ["ownerUid", "syncStatus"]),
    Index(value = ["ownerUid", "entityType", "cloudDocId"])
  ]
)
data class SyncRecordStateEntity(
  val ownerUid: String,
  val entityType: String,
  val localKey: String,
  val localId: Long,
  val cloudDocId: String,
  val displayLabel: String,
  val syncStatus: String = SyncStatus.PENDING_UPLOAD.name,
  val lastSyncedHash: String? = null,
  val currentLocalHash: String = "",
  val remoteConflictHash: String? = null,
  val remotePayloadJson: String? = null,
  val localRevision: Int = 1,
  val remoteRevision: Int = 1,
  val localUpdatedAtMs: Long = 0L,
  val remoteUpdatedAtMs: Long = 0L,
  val lastSyncedAtMs: Long? = null,
  val retryCount: Int = 0,
  val nextRetryAtMs: Long = 0L,
  val lastError: String? = null
)

@Entity(
  tableName = "sync_workspace_meta",
  primaryKeys = ["ownerUid"]
)
data class SyncWorkspaceMetaEntity(
  val ownerUid: String,
  val lastSuccessfulSyncAtMs: Long? = null,
  val lastPullAtMs: Long? = null,
  val lastErrorMessage: String? = null,
  val pendingCount: Int = 0,
  val syncedCount: Int = 0,
  val conflictCount: Int = 0,
  val failedCount: Int = 0
)

@Dao
interface SyncStateDao {
  @Query("SELECT * FROM sync_record_states WHERE ownerUid = :uid")
  suspend fun getAllForUserSync(uid: String): List<SyncRecordStateEntity>

  @Query("SELECT * FROM sync_record_states WHERE ownerUid = :uid AND entityType = :entityType AND localKey = :localKey LIMIT 1")
  suspend fun getRecordSync(uid: String, entityType: String, localKey: String): SyncRecordStateEntity?

  @Query("SELECT * FROM sync_record_states WHERE ownerUid = :uid AND syncStatus = 'CONFLICT' ORDER BY localUpdatedAtMs DESC")
  fun observeConflicts(uid: String): Flow<List<SyncRecordStateEntity>>

  @Query("SELECT * FROM sync_record_states WHERE ownerUid = :uid AND syncStatus = 'CONFLICT' ORDER BY localUpdatedAtMs DESC")
  suspend fun getConflictsSync(uid: String): List<SyncRecordStateEntity>

  @Query("SELECT COUNT(*) FROM sync_record_states WHERE ownerUid = :uid AND syncStatus = :status")
  suspend fun countByStatusSync(uid: String, status: String): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertRecord(record: SyncRecordStateEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAllRecords(records: List<SyncRecordStateEntity>)

  @Query("DELETE FROM sync_record_states WHERE ownerUid = :uid")
  suspend fun clearForUser(uid: String)

  @Query("SELECT * FROM sync_workspace_meta WHERE ownerUid = :uid LIMIT 1")
  fun observeWorkspaceMeta(uid: String): Flow<SyncWorkspaceMetaEntity?>

  @Query("SELECT * FROM sync_workspace_meta WHERE ownerUid = :uid LIMIT 1")
  suspend fun getWorkspaceMetaSync(uid: String): SyncWorkspaceMetaEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertWorkspaceMeta(meta: SyncWorkspaceMetaEntity)
}

/**
 * Companion Room database storing per-user Firestore sync state, retry backoff schedule,
 * content hashes, and explicit conflict candidates. Kept separate from [com.example.data.local.IceTrackDatabase]
 * so Room V5 financial schemas (1.json..5.json) and offline V5 JSON backup structure remain 100% untouched.
 */
@Database(
  entities = [SyncRecordStateEntity::class, SyncWorkspaceMetaEntity::class],
  version = 1,
  exportSchema = false
)
abstract class IceTrackSyncDatabase : RoomDatabase() {
  abstract fun syncStateDao(): SyncStateDao

  companion object {
    @Volatile
    private var INSTANCE: IceTrackSyncDatabase? = null

    fun getInstance(context: Context): IceTrackSyncDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          IceTrackSyncDatabase::class.java,
          "icetrack_sync.db"
        ).build()
        INSTANCE = instance
        instance
      }
    }
  }
}
