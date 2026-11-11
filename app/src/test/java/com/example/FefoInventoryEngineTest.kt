package com.example

import com.example.domain.engine.FefoInventoryEngine
import com.example.domain.model.ProductBatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FefoInventoryEngineTest {

  private val now = 1700000000000L // arbitrary fixed time
  private val dayMillis = 86400000L

  @Test
  fun `allocateFefo issues stock from earliest expiring batch first`() {
    val batchA = ProductBatch(
      id = 1,
      productId = 10,
      batchNumber = "B-MAR",
      purchaseDate = now - (10 * dayMillis),
      expiryDate = now + (30 * dayMillis), // expires in 30 days
      initialQuantityPieces = 50,
      currentQuantityPieces = 50,
      purchaseCostPerPiecePaise = 2000
    )

    val batchB = ProductBatch(
      id = 2,
      productId = 10,
      batchNumber = "B-JAN",
      purchaseDate = now - (5 * dayMillis),
      expiryDate = now + (10 * dayMillis), // expires in 10 days (Earlier!)
      initialQuantityPieces = 40,
      currentQuantityPieces = 40,
      purchaseCostPerPiecePaise = 2000
    )

    // Request 60 pieces: Should take all 40 from Batch B (earliest expiry), then 20 from Batch A
    val result = FefoInventoryEngine.allocateFefo(
      requestedPieces = 60,
      availableBatches = listOf(batchA, batchB),
      allowExpired = false,
      currentTimestamp = now
    )

    assertEquals(60, result.fulfilledPieces)
    assertEquals(0, result.remainingUnfulfilledPieces)
    assertEquals(2, result.allocations.size)

    assertEquals(2L, result.allocations[0].batch.id)
    assertEquals(40, result.allocations[0].quantityPiecesToIssue)

    assertEquals(1L, result.allocations[1].batch.id)
    assertEquals(20, result.allocations[1].quantityPiecesToIssue)
  }

  @Test
  fun `allocateFefo falls back to FIFO when expiry dates are identical`() {
    val batch1 = ProductBatch(
      id = 1,
      productId = 10,
      batchNumber = "B1",
      purchaseDate = now - (20 * dayMillis), // older purchase
      expiryDate = now + (30 * dayMillis),
      initialQuantityPieces = 30,
      currentQuantityPieces = 30,
      purchaseCostPerPiecePaise = 2000
    )

    val batch2 = ProductBatch(
      id = 2,
      productId = 10,
      batchNumber = "B2",
      purchaseDate = now - (5 * dayMillis), // newer purchase
      expiryDate = now + (30 * dayMillis), // same expiry
      initialQuantityPieces = 30,
      currentQuantityPieces = 30,
      purchaseCostPerPiecePaise = 2000
    )

    val result = FefoInventoryEngine.allocateFefo(
      requestedPieces = 25,
      availableBatches = listOf(batch2, batch1),
      allowExpired = false,
      currentTimestamp = now
    )

    assertEquals(25, result.fulfilledPieces)
    assertEquals(1, result.allocations.size)
    assertEquals(1L, result.allocations[0].batch.id) // Took from older purchase batch1
    assertEquals(25, result.allocations[0].quantityPiecesToIssue)
  }

  @Test
  fun `allocateFefo rejects expired stock by default`() {
    val expiredBatch = ProductBatch(
      id = 1,
      productId = 10,
      batchNumber = "B-EXP",
      purchaseDate = now - (40 * dayMillis),
      expiryDate = now - (5 * dayMillis), // expired 5 days ago!
      initialQuantityPieces = 50,
      currentQuantityPieces = 50,
      purchaseCostPerPiecePaise = 2000
    )

    val freshBatch = ProductBatch(
      id = 2,
      productId = 10,
      batchNumber = "B-FRESH",
      purchaseDate = now - (5 * dayMillis),
      expiryDate = now + (20 * dayMillis),
      initialQuantityPieces = 30,
      currentQuantityPieces = 30,
      purchaseCostPerPiecePaise = 2000
    )

    val result = FefoInventoryEngine.allocateFefo(
      requestedPieces = 40,
      availableBatches = listOf(expiredBatch, freshBatch),
      allowExpired = false,
      currentTimestamp = now
    )

    // Only fresh batch (30 pcs) should be issued; 10 pieces remain unfulfilled
    assertEquals(30, result.fulfilledPieces)
    assertEquals(10, result.remainingUnfulfilledPieces)
    assertEquals(1, result.allocations.size)
    assertEquals(2L, result.allocations[0].batch.id)
  }

  @Test
  fun `getExpiringBatches flags items expiring within threshold`() {
    val nearExpiry = ProductBatch(
      id = 1,
      productId = 10,
      batchNumber = "B-NEAR",
      purchaseDate = now - (10 * dayMillis),
      expiryDate = now + (3 * dayMillis), // in 3 days
      initialQuantityPieces = 10,
      currentQuantityPieces = 10,
      purchaseCostPerPiecePaise = 2000
    )

    val farExpiry = ProductBatch(
      id = 2,
      productId = 10,
      batchNumber = "B-FAR",
      purchaseDate = now - (10 * dayMillis),
      expiryDate = now + (30 * dayMillis), // in 30 days
      initialQuantityPieces = 10,
      currentQuantityPieces = 10,
      purchaseCostPerPiecePaise = 2000
    )

    val expiring = FefoInventoryEngine.getExpiringBatches(
      batches = listOf(nearExpiry, farExpiry),
      thresholdDays = 7,
      currentTimestamp = now
    )

    assertEquals(1, expiring.size)
    assertEquals(1L, expiring[0].id)
  }
}
