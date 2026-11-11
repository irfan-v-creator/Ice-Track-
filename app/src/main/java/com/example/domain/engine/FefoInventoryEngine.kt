package com.example.domain.engine

import com.example.domain.model.ProductBatch

data class BatchAllocation(
  val batch: ProductBatch,
  val quantityPiecesToIssue: Int
)

data class FefoIssueResult(
  val allocations: List<BatchAllocation>,
  val fulfilledPieces: Int,
  val remainingUnfulfilledPieces: Int
)

object FefoInventoryEngine {

  /**
   * Allocates stock for a requested quantity of pieces using FEFO (First Expiry, First Out).
   * Batches are sorted primarily by expiry date (earliest first), and secondarily by purchase date (FIFO fallback).
   * By default, batches that are already expired are excluded to prevent sale of expired stock.
   */
  fun allocateFefo(
    requestedPieces: Int,
    availableBatches: List<ProductBatch>,
    allowExpired: Boolean = false,
    currentTimestamp: Long = System.currentTimeMillis()
  ): FefoIssueResult {
    require(requestedPieces >= 0) { "Requested quantity cannot be negative" }
    if (requestedPieces == 0) {
      return FefoIssueResult(emptyList(), 0, 0)
    }

    // Filter available batches with stock > 0
    val validBatches = availableBatches
      .filter { it.isActive && it.currentQuantityPieces > 0 }
      .filter { allowExpired || it.expiryDate > currentTimestamp }
      // Primary sort: expiryDate ASC (FEFO)
      // Secondary sort: purchaseDate ASC (FIFO fallback when expiries are identical)
      .sortedWith(compareBy<ProductBatch> { it.expiryDate }.thenBy { it.purchaseDate })

    var unfulfilled = requestedPieces
    val allocations = mutableListOf<BatchAllocation>()

    for (batch in validBatches) {
      if (unfulfilled <= 0) break
      val canIssue = kotlin.math.min(unfulfilled, batch.currentQuantityPieces)
      if (canIssue > 0) {
        allocations.add(BatchAllocation(batch = batch, quantityPiecesToIssue = canIssue))
        unfulfilled -= canIssue
      }
    }

    val fulfilled = requestedPieces - unfulfilled
    return FefoIssueResult(
      allocations = allocations,
      fulfilledPieces = fulfilled,
      remainingUnfulfilledPieces = unfulfilled
    )
  }

  /**
   * Calculates total available pieces for a product across active, non-expired batches.
   */
  fun calculateAvailableStock(
    batches: List<ProductBatch>,
    allowExpired: Boolean = false,
    currentTimestamp: Long = System.currentTimeMillis()
  ): Int {
    return batches
      .filter { it.isActive && it.currentQuantityPieces > 0 }
      .filter { allowExpired || it.expiryDate > currentTimestamp }
      .sumOf { it.currentQuantityPieces }
  }

  /**
   * Checks for batches expiring within a threshold (e.g. within next 7 days).
   */
  fun getExpiringBatches(
    batches: List<ProductBatch>,
    thresholdDays: Int = 7,
    currentTimestamp: Long = System.currentTimeMillis()
  ): List<ProductBatch> {
    val thresholdMillis = thresholdDays * 24L * 60L * 60L * 1000L
    val targetTime = currentTimestamp + thresholdMillis

    return batches
      .filter { it.isActive && it.currentQuantityPieces > 0 }
      .filter { it.expiryDate in currentTimestamp..targetTime }
      .sortedBy { it.expiryDate }
  }
}
