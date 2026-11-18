package com.example.domain.engine

import java.security.MessageDigest

enum class SyncStatus {
  PENDING_UPLOAD,
  SYNCED,
  CONFLICT,
  FAILED
}

enum class ConflictResolutionStrategy {
  KEEP_LOCAL_OVERWRITE_CLOUD,
  APPLY_CLOUD_TO_LOCAL
}

enum class SyncComparisonDecision {
  NO_CHANGE,
  PUSH_TO_CLOUD,
  PULL_TO_LOCAL,
  CONFLICT_NEEDS_REVIEW
}

data class ConflictCandidate(
  val entityType: String,
  val localId: Long,
  val cloudDocId: String,
  val displayLabel: String,
  val localContentHash: String,
  val cloudContentHash: String,
  val lastSyncedHash: String?,
  val localUpdatedAtMs: Long,
  val cloudUpdatedAtMs: Long,
  val localRevision: Int = 1,
  val cloudRevision: Int = 1,
  val cloudPayloadJson: String = ""
)

object CloudSyncConflictEngine {

  private val BACKOFF_DELAYS_MS = longArrayOf(
    30_000L,
    120_000L,
    600_000L,
    1_800_000L
  )

  fun computeSha256(canonicalPayload: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(canonicalPayload.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
  }

  /**
   * Canonical deterministic hash helper that orders keys alphabetically and strips transient
   * sync metadata (`createdAt`, `updatedAt`, `userId`, `contentHash`) so local and cloud payloads
   * yield the exact same digest whenever business fields match.
   */
  fun computeBusinessHash(fields: Map<String, Any?>): String {
    val canonical = fields.entries
      .filter { it.key !in IGNORED_HASH_KEYS && it.value != null }
      .sortedBy { it.key }
      .joinToString("|") { (k, v) -> "$k=$v" }
    return computeSha256(canonical.ifEmpty { "empty" })
  }

  private val IGNORED_HASH_KEYS = setOf("createdAt", "updatedAt", "userId", "contentHash")

  /**
   * Determines three-way merge action without ever silently overwriting newer revisions or
   * diverged local & cloud edits.
   */
  fun classifySyncAction(
    localHash: String,
    cloudHash: String?,
    lastSyncedHash: String?,
    localUpdatedAtMs: Long,
    cloudUpdatedAtMs: Long?,
    localRevision: Int = 1,
    cloudRevision: Int? = null
  ): SyncComparisonDecision {
    if (cloudHash == null) {
      return SyncComparisonDecision.PUSH_TO_CLOUD
    }
    if (localHash == cloudHash) {
      return SyncComparisonDecision.NO_CHANGE
    }
    if (lastSyncedHash != null) {
      val localChanged = localHash != lastSyncedHash
      val cloudChanged = cloudHash != lastSyncedHash
      return when {
        localChanged && !cloudChanged -> SyncComparisonDecision.PUSH_TO_CLOUD
        !localChanged && cloudChanged -> SyncComparisonDecision.PULL_TO_LOCAL
        else -> {
          // Both diverged since last sync -> flag explicit review, never auto-overwrite newer revision
          SyncComparisonDecision.CONFLICT_NEEDS_REVIEW
        }
      }
    }
    // Never silently drop a newer cloud revision when no baseline exists
    val effectiveCloudRev = cloudRevision ?: 1
    if (localUpdatedAtMs == 0L && (cloudUpdatedAtMs ?: 0L) > 0L) {
      return SyncComparisonDecision.PULL_TO_LOCAL
    }
    return if (effectiveCloudRev == localRevision && (cloudUpdatedAtMs ?: 0L) == localUpdatedAtMs) {
      SyncComparisonDecision.CONFLICT_NEEDS_REVIEW
    } else {
      SyncComparisonDecision.CONFLICT_NEEDS_REVIEW
    }
  }

  fun computeNextRetryAtMs(retryCount: Int, nowMs: Long = System.currentTimeMillis()): Long {
    val idx = retryCount.coerceAtLeast(0).coerceAtMost(BACKOFF_DELAYS_MS.lastIndex)
    return nowMs + BACKOFF_DELAYS_MS[idx]
  }
}
