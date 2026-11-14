package com.example

import com.example.service.BackupRestoreService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreServiceTest {

  @Test
  fun testValidateBackupJson_validJsonStructure() {
    val json = """
      {
        "app": "IceTrack",
        "schemaVersion": 2,
        "exportedAt": 1715000000000,
        "products": [
          {"id": 1, "name": "Choco Bar", "sku": "CH-1", "category": "Bars", "defaultPurchasePricePaise": 1500, "defaultSellingPricePaise": 2500, "minStockLevelPieces": 20, "isActive": true, "createdAt": 1715000000000}
        ],
        "customers": [
          {"id": 1, "shopName": "SuperMart", "ownerName": "John", "phoneNumber": "9876543210", "address": "Market", "openingBalancePaise": 0, "openingBalanceDate": 1715000000000, "creditLimitPaise": 5000000, "paymentTermsDays": 15, "isActive": true, "createdAt": 1715000000000}
        ],
        "suppliers": [
          {"id": 1, "name": "Milkyway Dairy", "contactPerson": "Manager", "phoneNumber": "9876543211", "address": "Estate", "openingBalancePaise": 0, "openingBalanceDate": 1715000000000, "creditTermsDays": 30, "creditLimitPaise": 10000000, "isActive": true, "createdAt": 1715000000000}
        ],
        "sales_invoices": [
          {"id": 1, "invoiceNumber": "INV-100", "customerId": 1, "invoiceDate": 1715000000000, "dueDate": 1715000000000, "totalAmountPaise": 50000, "paidAmountPaise": 50000, "paymentStatus": "PAID", "isConfirmed": true, "createdAt": 1715000000000}
        ],
        "purchase_invoices": []
      }
    """.trimIndent()

    val result = BackupRestoreService.validateBackupJson(json)

    assertTrue("Valid backup JSON must pass validation", result.isValid)
    assertEquals(1, result.productCount)
    assertEquals(1, result.customerCount)
    assertEquals(1, result.supplierCount)
    assertEquals(1, result.salesCount)
    assertEquals(0, result.purchaseCount)
  }

  @Test
  fun testValidateBackupJson_invalidAppTag() {
    val wrongAppJson = """
      {
        "app": "OtherApp",
        "schemaVersion": 2,
        "products": []
      }
    """.trimIndent()

    val result = BackupRestoreService.validateBackupJson(wrongAppJson)
    assertFalse("Non-IceTrack JSON must be rejected", result.isValid)
    assertTrue(result.errorMessage?.contains("Not an IceTrack backup") == true)
  }

  @Test
  fun testValidateBackupJson_malformedJson() {
    val brokenJson = "{ this is not valid json :; "

    val result = BackupRestoreService.validateBackupJson(brokenJson)
    assertFalse("Malformed JSON must be rejected", result.isValid)
    assertTrue(result.errorMessage?.contains("Malformed JSON") == true)
  }
}
