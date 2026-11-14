package com.example.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

  val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
      // 1. Create product_batches table
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `product_batches` (
          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          `productId` INTEGER NOT NULL,
          `batchNumber` TEXT NOT NULL,
          `purchaseInvoiceId` INTEGER,
          `purchaseDate` INTEGER NOT NULL,
          `expiryDate` INTEGER NOT NULL,
          `initialQuantityPieces` INTEGER NOT NULL,
          `currentQuantityPieces` INTEGER NOT NULL,
          `purchaseCostPerPiecePaise` INTEGER NOT NULL,
          `isActive` INTEGER NOT NULL,
          `createdAt` INTEGER NOT NULL,
          FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
        )
        """.trimIndent()
      )
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_batches_productId` ON `product_batches` (`productId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_batches_expiryDate` ON `product_batches` (`expiryDate`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_batches_purchaseDate` ON `product_batches` (`purchaseDate`)")

      // 2. Create stock_movements table
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `stock_movements` (
          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          `productId` INTEGER NOT NULL,
          `batchId` INTEGER,
          `movementType` TEXT NOT NULL,
          `quantityPieces` INTEGER NOT NULL,
          `unitCostPaise` INTEGER NOT NULL,
          `referenceType` TEXT NOT NULL,
          `referenceId` INTEGER NOT NULL,
          `movementDate` INTEGER NOT NULL,
          `notes` TEXT NOT NULL,
          `createdAt` INTEGER NOT NULL,
          FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
        )
        """.trimIndent()
      )
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_productId` ON `stock_movements` (`productId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_batchId` ON `stock_movements` (`batchId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_movementDate` ON `stock_movements` (`movementDate`)")

      // 3. Create purchase_invoices table
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `purchase_invoices` (
          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          `invoiceNumber` TEXT NOT NULL,
          `supplierId` INTEGER NOT NULL,
          `invoiceDate` INTEGER NOT NULL,
          `dueDate` INTEGER NOT NULL,
          `totalAmountPaise` INTEGER NOT NULL,
          `paidAmountPaise` INTEGER NOT NULL,
          `paymentStatus` TEXT NOT NULL,
          `paymentMethod` TEXT,
          `notes` TEXT NOT NULL,
          `isConfirmed` INTEGER NOT NULL,
          `createdAt` INTEGER NOT NULL,
          FOREIGN KEY(`supplierId`) REFERENCES `suppliers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
        )
        """.trimIndent()
      )
      db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_purchase_invoices_invoiceNumber` ON `purchase_invoices` (`invoiceNumber`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_invoices_supplierId` ON `purchase_invoices` (`supplierId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_invoices_invoiceDate` ON `purchase_invoices` (`invoiceDate`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_invoices_dueDate` ON `purchase_invoices` (`dueDate`)")

      // 4. Create purchase_invoice_items table
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `purchase_invoice_items` (
          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          `purchaseInvoiceId` INTEGER NOT NULL,
          `productId` INTEGER NOT NULL,
          `batchNumber` TEXT NOT NULL,
          `expiryDate` INTEGER NOT NULL,
          `quantityPieces` INTEGER NOT NULL,
          `costPerPiecePaise` INTEGER NOT NULL,
          `lineTotalPaise` INTEGER NOT NULL,
          FOREIGN KEY(`purchaseInvoiceId`) REFERENCES `purchase_invoices`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
          FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
        )
        """.trimIndent()
      )
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_invoice_items_purchaseInvoiceId` ON `purchase_invoice_items` (`purchaseInvoiceId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_invoice_items_productId` ON `purchase_invoice_items` (`productId`)")

      // 5. Create sales_invoices table
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `sales_invoices` (
          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          `invoiceNumber` TEXT NOT NULL,
          `customerId` INTEGER NOT NULL,
          `invoiceDate` INTEGER NOT NULL,
          `dueDate` INTEGER NOT NULL,
          `totalAmountPaise` INTEGER NOT NULL,
          `paidAmountPaise` INTEGER NOT NULL,
          `paymentStatus` TEXT NOT NULL,
          `paymentMethod` TEXT,
          `notes` TEXT NOT NULL,
          `isConfirmed` INTEGER NOT NULL,
          `createdAt` INTEGER NOT NULL,
          FOREIGN KEY(`customerId`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
        )
        """.trimIndent()
      )
      db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sales_invoices_invoiceNumber` ON `sales_invoices` (`invoiceNumber`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_invoices_customerId` ON `sales_invoices` (`customerId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_invoices_invoiceDate` ON `sales_invoices` (`invoiceDate`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_invoices_dueDate` ON `sales_invoices` (`dueDate`)")

      // 6. Create sales_invoice_items table
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `sales_invoice_items` (
          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          `salesInvoiceId` INTEGER NOT NULL,
          `productId` INTEGER NOT NULL,
          `batchId` INTEGER NOT NULL,
          `quantityPieces` INTEGER NOT NULL,
          `unitPricePaise` INTEGER NOT NULL,
          `lineTotalPaise` INTEGER NOT NULL,
          FOREIGN KEY(`salesInvoiceId`) REFERENCES `sales_invoices`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
          FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
        )
        """.trimIndent()
      )
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_invoice_items_salesInvoiceId` ON `sales_invoice_items` (`salesInvoiceId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_invoice_items_productId` ON `sales_invoice_items` (`productId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_invoice_items_batchId` ON `sales_invoice_items` (`batchId`)")

      // 7. Create expenses table
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `expenses` (
          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          `category` TEXT NOT NULL,
          `amountPaise` INTEGER NOT NULL,
          `expenseDate` INTEGER NOT NULL,
          `paymentMethod` TEXT NOT NULL,
          `referenceNumber` TEXT NOT NULL,
          `notes` TEXT NOT NULL,
          `createdAt` INTEGER NOT NULL,
          `deletedAt` INTEGER
        )
        """.trimIndent()
      )
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_category` ON `expenses` (`category`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_expenseDate` ON `expenses` (`expenseDate`)")
    }
  }

  val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
      // 1. Rebuild payment_allocations with partyType, partyId, allocationType, nullable invoiceId, notes.
      //    Never silently assign unmatched rows to partyId = 0; quarantine invalid/orphan references with isReversed = 1.
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `payment_allocations_v3` (
          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          `paymentId` INTEGER NOT NULL,
          `partyType` TEXT NOT NULL,
          `partyId` INTEGER NOT NULL,
          `allocationType` TEXT NOT NULL,
          `invoiceId` INTEGER,
          `allocatedAmountPaise` INTEGER NOT NULL,
          `allocatedAt` INTEGER NOT NULL,
          `notes` TEXT NOT NULL,
          `isReversed` INTEGER NOT NULL
        )
        """.trimIndent()
      )

      migrateExistingV2Allocations(db)

      db.execSQL("DROP TABLE `payment_allocations`")
      db.execSQL("ALTER TABLE `payment_allocations_v3` RENAME TO `payment_allocations`")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_allocations_paymentId` ON `payment_allocations` (`paymentId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_allocations_invoiceId` ON `payment_allocations` (`invoiceId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_allocations_partyType_partyId` ON `payment_allocations` (`partyType`, `partyId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_allocations_allocationType` ON `payment_allocations` (`allocationType`)")

      // 2. Reconstruct INVOICE allocations for genuine V2 invoice-time payments (SALES_PAYMENT / PURCHASE_PAYMENT)
      //    that had no V2 payment_allocations rows, verifying invoice existence and party ownership.
      reconstructV2InvoiceTimePayments(db)

      // 3. Normalize any V2 overpaid sales or purchase invoices without altering subledger_entries
      normalizeOverpaidInvoices(
        db = db,
        tableName = "sales_invoices",
        partyType = "CUSTOMER",
        partyIdColumn = "customerId"
      )
      normalizeOverpaidInvoices(
        db = db,
        tableName = "purchase_invoices",
        partyType = "SUPPLIER",
        partyIdColumn = "supplierId"
      )

      // 4. Reconcile any remaining unallocated active PAYMENT subledger entries into explicit ADVANCE rows
      reconcileUnallocatedPaymentsToAdvance(db)

      // 5. Reconcile any active negative OPENING_BALANCE credits into explicit ADVANCE rows
      reconcileNegativeOpeningBalancesToAdvance(db)
    }

    private fun migrateExistingV2Allocations(db: SupportSQLiteDatabase) {
      val v2Cursor = db.query(
        """
        SELECT
          pa.`id`,
          pa.`paymentId`,
          pa.`invoiceId`,
          pa.`allocatedAmountPaise`,
          pa.`allocatedAt`,
          pa.`isReversed`,
          se.`id` AS `seId`,
          se.`partyType`,
          se.`partyId`,
          se.`isReversed` AS `seIsReversed`
        FROM `payment_allocations` pa
        LEFT JOIN `subledger_entries` se ON se.`id` = pa.`paymentId`
        ORDER BY pa.`id` ASC
        """.trimIndent()
      )
      v2Cursor.use { cursor ->
        while (cursor.moveToNext()) {
          val id = cursor.getLong(0)
          val paymentId = cursor.getLong(1)
          val invoiceId = if (cursor.isNull(2)) null else cursor.getLong(2)
          val allocatedAmount = cursor.getLong(3)
          val allocatedAt = cursor.getLong(4)
          val paReversed = cursor.getInt(5)
          val hasSubledgerParent = !cursor.isNull(6)
          val partyType = if (!cursor.isNull(7)) cursor.getString(7) else null
          val partyId = if (!cursor.isNull(8)) cursor.getLong(8) else -1L
          val seReversed = if (!cursor.isNull(9)) cursor.getInt(9) else 1

          if (!hasSubledgerParent || partyType == null || partyId <= 0L) {
            db.execSQL(
              """
              INSERT INTO `payment_allocations_v3` (
                `id`, `paymentId`, `partyType`, `partyId`, `allocationType`, `invoiceId`,
                `allocatedAmountPaise`, `allocatedAt`, `notes`, `isReversed`
              ) VALUES (?, ?, ?, -1, 'INVOICE', ?, ?, ?, ?, 1)
              """.trimIndent(),
              arrayOf<Any>(
                id,
                paymentId,
                partyType ?: "CUSTOMER",
                invoiceId ?: -1L,
                allocatedAmount,
                allocatedAt,
                "Quarantined orphaned V2 allocation #$id (missing payment #$paymentId)"
              )
            )
            continue
          }

          val invoiceOwnedByParty = if (invoiceId != null) {
            invoiceBelongsToParty(db, partyType, partyId, invoiceId)
          } else false

          if (!invoiceOwnedByParty) {
            // Quarantine invalid invoice reference as reversed; if payment is active, Step 4 preserves the full amount as ADVANCE for partyId.
            db.execSQL(
              """
              INSERT INTO `payment_allocations_v3` (
                `id`, `paymentId`, `partyType`, `partyId`, `allocationType`, `invoiceId`,
                `allocatedAmountPaise`, `allocatedAt`, `notes`, `isReversed`
              ) VALUES (?, ?, ?, ?, 'INVOICE', ?, ?, ?, ?, 1)
              """.trimIndent(),
              arrayOf<Any>(
                id,
                paymentId,
                partyType,
                partyId,
                invoiceId ?: -1L,
                allocatedAmount,
                allocatedAt,
                "Quarantined V2 allocation #$id (invalid or mismatched invoice #${invoiceId ?: "NULL"})"
              )
            )
            continue
          }

          val finalReversed = if (paReversed == 1 || seReversed == 1) 1 else 0
          db.execSQL(
            """
            INSERT INTO `payment_allocations_v3` (
              `id`, `paymentId`, `partyType`, `partyId`, `allocationType`, `invoiceId`,
              `allocatedAmountPaise`, `allocatedAt`, `notes`, `isReversed`
            ) VALUES (?, ?, ?, ?, 'INVOICE', ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
              id,
              paymentId,
              partyType,
              partyId,
              invoiceId!!,
              allocatedAmount,
              allocatedAt,
              "Migrated V2 invoice allocation",
              finalReversed
            )
          )
        }
      }
    }

    private fun invoiceBelongsToParty(
      db: SupportSQLiteDatabase,
      partyType: String,
      partyId: Long,
      invoiceId: Long
    ): Boolean {
      val (table, partyCol) = when (partyType) {
        "CUSTOMER" -> "sales_invoices" to "customerId"
        "SUPPLIER" -> "purchase_invoices" to "supplierId"
        else -> return false
      }
      val checkCursor = db.query(
        "SELECT 1 FROM `$table` WHERE `id` = ? AND `$partyCol` = ? LIMIT 1",
        arrayOf<Any>(invoiceId, partyId)
      )
      return checkCursor.use { it.moveToFirst() }
    }

    private fun reconstructV2InvoiceTimePayments(db: SupportSQLiteDatabase) {
      val directPaymentsCursor = db.query(
        """
        SELECT `id`, `partyType`, `partyId`, `referenceType`, `referenceId`, (`debitPaise` + `creditPaise`) AS `paymentTotal`, `entryDate`
        FROM `subledger_entries`
        WHERE `entryType` = 'PAYMENT'
          AND `isReversed` = 0
          AND `partyId` > 0
          AND `referenceType` IN ('SALES_PAYMENT', 'PURCHASE_PAYMENT')
          AND (`debitPaise` + `creditPaise`) > 0
        ORDER BY `entryDate` ASC, `id` ASC
        """.trimIndent()
      )
      directPaymentsCursor.use { cursor ->
        while (cursor.moveToNext()) {
          val paymentId = cursor.getLong(0)
          val partyType = cursor.getString(1)
          val partyId = cursor.getLong(2)
          val referenceType = cursor.getString(3)
          val invoiceId = cursor.getLong(4)
          val paymentTotal = cursor.getLong(5)
          val entryDate = cursor.getLong(6)

          // Skip if V2 already had active allocation rows for this paymentId
          val existingCountCursor = db.query(
            "SELECT COUNT(*) FROM `payment_allocations` WHERE `paymentId` = ? AND `isReversed` = 0",
            arrayOf<Any>(paymentId)
          )
          val existingCount = existingCountCursor.use { ec ->
            if (ec.moveToFirst()) ec.getInt(0) else 0
          }
          if (existingCount > 0) continue

          val matchesType = (partyType == "CUSTOMER" && referenceType == "SALES_PAYMENT") ||
            (partyType == "SUPPLIER" && referenceType == "PURCHASE_PAYMENT")
          if (matchesType && invoiceBelongsToParty(db, partyType, partyId, invoiceId)) {
            db.execSQL(
              """
              INSERT INTO `payment_allocations` (
                `paymentId`, `partyType`, `partyId`, `allocationType`, `invoiceId`,
                `allocatedAmountPaise`, `allocatedAt`, `notes`, `isReversed`
              ) VALUES (?, ?, ?, 'INVOICE', ?, ?, ?, ?, 0)
              """.trimIndent(),
              arrayOf<Any>(
                paymentId,
                partyType,
                partyId,
                invoiceId,
                paymentTotal,
                entryDate,
                "Reconstructed V2 direct invoice payment (V2->V3)"
              )
            )
          }
        }
      }
    }

    private fun normalizeOverpaidInvoices(
      db: SupportSQLiteDatabase,
      tableName: String,
      partyType: String,
      partyIdColumn: String
    ) {
      val overpaidCursor = db.query(
        """
        SELECT inv.`id`, inv.`$partyIdColumn`, inv.`invoiceNumber`, inv.`totalAmountPaise`, inv.`paidAmountPaise`
        FROM `$tableName` inv
        WHERE inv.`paidAmountPaise` > inv.`totalAmountPaise`
           OR (
             SELECT COALESCE(SUM(pa.`allocatedAmountPaise`), 0)
             FROM `payment_allocations` pa
             WHERE pa.`partyType` = ?
               AND pa.`invoiceId` = inv.`id`
               AND pa.`allocationType` = 'INVOICE'
               AND pa.`isReversed` = 0
           ) > inv.`totalAmountPaise`
        """.trimIndent(),
        arrayOf<Any>(partyType)
      )
      overpaidCursor.use { cursor ->
        while (cursor.moveToNext()) {
          val invoiceId = cursor.getLong(0)
          val partyId = cursor.getLong(1)
          val invoiceNumber = cursor.getString(2)
          val totalAmount = cursor.getLong(3)
          val originalHeaderPaid = cursor.getLong(4)

          var remainingCap = totalAmount
          var activeInvoiceAllocSum = 0L
          val allocCursor = db.query(
            """
            SELECT `id`, `paymentId`, `allocatedAmountPaise`, `allocatedAt`
            FROM `payment_allocations`
            WHERE `partyType` = ? AND `invoiceId` = ? AND `allocationType` = 'INVOICE' AND `isReversed` = 0
            ORDER BY `allocatedAt` ASC, `id` ASC
            """.trimIndent(),
            arrayOf<Any>(partyType, invoiceId)
          )
          allocCursor.use { ac ->
            while (ac.moveToNext()) {
              val allocId = ac.getLong(0)
              val paymentId = ac.getLong(1)
              val allocAmount = ac.getLong(2)
              val allocatedAt = ac.getLong(3)

              if (allocAmount <= remainingCap) {
                remainingCap -= allocAmount
                activeInvoiceAllocSum += allocAmount
              } else {
                val allowedOnInvoice = remainingCap
                val excessAdvance = allocAmount - allowedOnInvoice
                remainingCap = 0L
                activeInvoiceAllocSum += allowedOnInvoice

                db.execSQL("UPDATE `payment_allocations` SET `isReversed` = 1 WHERE `id` = ?", arrayOf<Any>(allocId))
                if (allowedOnInvoice > 0L) {
                  db.execSQL(
                    """
                    INSERT INTO `payment_allocations` (
                      `paymentId`, `partyType`, `partyId`, `allocationType`, `invoiceId`,
                      `allocatedAmountPaise`, `allocatedAt`, `notes`, `isReversed`
                    ) VALUES (?, ?, ?, 'INVOICE', ?, ?, ?, ?, 0)
                    """.trimIndent(),
                    arrayOf<Any>(
                      paymentId,
                      partyType,
                      partyId,
                      invoiceId,
                      allowedOnInvoice,
                      allocatedAt,
                      "Normalized invoice allocation for #$invoiceNumber (V2->V3)"
                    )
                  )
                }
                if (excessAdvance > 0L) {
                  db.execSQL(
                    """
                    INSERT INTO `payment_allocations` (
                      `paymentId`, `partyType`, `partyId`, `allocationType`, `invoiceId`,
                      `allocatedAmountPaise`, `allocatedAt`, `notes`, `isReversed`
                    ) VALUES (?, ?, ?, 'ADVANCE', NULL, ?, ?, ?, 0)
                    """.trimIndent(),
                    arrayOf<Any>(
                      paymentId,
                      partyType,
                      partyId,
                      excessAdvance,
                      allocatedAt,
                      "Migrated overpayment advance from #$invoiceNumber (V2->V3)"
                    )
                  )
                }
              }
            }
          }

          val finalHeaderPaid = if (activeInvoiceAllocSum > 0L) {
            minOf(totalAmount, activeInvoiceAllocSum)
          } else {
            minOf(totalAmount, originalHeaderPaid)
          }
          val finalStatus = when {
            finalHeaderPaid >= totalAmount -> "PAID"
            finalHeaderPaid > 0L -> "PARTIALLY_PAID"
            else -> "UNPAID"
          }
          db.execSQL(
            "UPDATE `$tableName` SET `paidAmountPaise` = ?, `paymentStatus` = ? WHERE `id` = ?",
            arrayOf<Any>(finalHeaderPaid, finalStatus, invoiceId)
          )
        }
      }
    }

    private fun reconcileUnallocatedPaymentsToAdvance(db: SupportSQLiteDatabase) {
      val paymentsCursor = db.query(
        """
        SELECT `id`, `partyType`, `partyId`, `referenceType`, `referenceId`, (`debitPaise` + `creditPaise`) AS `paymentTotal`, `entryDate`
        FROM `subledger_entries`
        WHERE `entryType` = 'PAYMENT'
          AND `isReversed` = 0
          AND `partyId` > 0
          AND (`debitPaise` + `creditPaise`) > 0
        ORDER BY `entryDate` ASC, `id` ASC
        """.trimIndent()
      )
      paymentsCursor.use { cursor ->
        while (cursor.moveToNext()) {
          val paymentId = cursor.getLong(0)
          val partyType = cursor.getString(1)
          val partyId = cursor.getLong(2)
          val referenceType = cursor.getString(3)
          val referenceId = cursor.getLong(4)
          val paymentTotal = cursor.getLong(5)
          val entryDate = cursor.getLong(6)

          var allocatedSum = 0L
          val sumCursor = db.query(
            "SELECT COALESCE(SUM(`allocatedAmountPaise`), 0) FROM `payment_allocations` WHERE `paymentId` = ? AND `isReversed` = 0",
            arrayOf<Any>(paymentId)
          )
          sumCursor.use { sc ->
            if (sc.moveToFirst()) allocatedSum = sc.getLong(0)
          }

          val unallocatedExcess = paymentTotal - allocatedSum
          if (unallocatedExcess > 0L) {
            val advNote = if (referenceType == "SALES_PAYMENT" || referenceType == "PURCHASE_PAYMENT") {
              "Migrated unallocated V2 payment advance (missing/unmatched invoice #$referenceId)"
            } else {
              "Migrated unallocated payment advance (V2->V3)"
            }
            db.execSQL(
              """
              INSERT INTO `payment_allocations` (
                `paymentId`, `partyType`, `partyId`, `allocationType`, `invoiceId`,
                `allocatedAmountPaise`, `allocatedAt`, `notes`, `isReversed`
              ) VALUES (?, ?, ?, 'ADVANCE', NULL, ?, ?, ?, 0)
              """.trimIndent(),
              arrayOf<Any>(
                paymentId,
                partyType,
                partyId,
                unallocatedExcess,
                entryDate,
                advNote
              )
            )
          }
        }
      }
    }

    private fun reconcileNegativeOpeningBalancesToAdvance(db: SupportSQLiteDatabase) {
      val obCursor = db.query(
        """
        SELECT `id`, `partyType`, `partyId`, `debitPaise`, `creditPaise`, `entryDate`
        FROM `subledger_entries`
        WHERE `entryType` = 'OPENING_BALANCE'
          AND `isReversed` = 0
          AND `partyId` > 0
          AND (
            (`partyType` = 'CUSTOMER' AND `creditPaise` > 0)
            OR (`partyType` = 'SUPPLIER' AND `debitPaise` > 0)
          )
        ORDER BY `entryDate` ASC, `id` ASC
        """.trimIndent()
      )
      obCursor.use { cursor ->
        while (cursor.moveToNext()) {
          val entryId = cursor.getLong(0)
          val partyType = cursor.getString(1)
          val partyId = cursor.getLong(2)
          val debit = cursor.getLong(3)
          val credit = cursor.getLong(4)
          val entryDate = cursor.getLong(5)
          val advanceCredit = if (partyType == "CUSTOMER") credit else debit

          val existingAlloc = db.query(
            "SELECT COALESCE(SUM(`allocatedAmountPaise`), 0) FROM `payment_allocations` WHERE `paymentId` = ? AND `isReversed` = 0",
            arrayOf<Any>(entryId)
          ).use { sc -> if (sc.moveToFirst()) sc.getLong(0) else 0L }

          val remainingAdvance = advanceCredit - existingAlloc
          if (remainingAdvance > 0L) {
            db.execSQL(
              """
              INSERT INTO `payment_allocations` (
                `paymentId`, `partyType`, `partyId`, `allocationType`, `invoiceId`,
                `allocatedAmountPaise`, `allocatedAt`, `notes`, `isReversed`
              ) VALUES (?, ?, ?, 'ADVANCE', NULL, ?, ?, ?, 0)
              """.trimIndent(),
              arrayOf<Any>(
                entryId,
                partyType,
                partyId,
                remainingAdvance,
                entryDate,
                "Migrated negative opening balance advance credit (V2->V3)"
              )
            )
          }
        }
      }
    }
  }
}
