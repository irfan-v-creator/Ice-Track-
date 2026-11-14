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
      // 1. Rebuild payment_allocations with partyType, partyId, allocationType, nullable invoiceId, notes
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

      db.execSQL(
        """
        INSERT INTO `payment_allocations_v3` (
          `id`, `paymentId`, `partyType`, `partyId`, `allocationType`, `invoiceId`,
          `allocatedAmountPaise`, `allocatedAt`, `notes`, `isReversed`
        )
        SELECT
          pa.`id`,
          pa.`paymentId`,
          COALESCE(se.`partyType`, 'CUSTOMER'),
          COALESCE(se.`partyId`, 0),
          'INVOICE',
          pa.`invoiceId`,
          pa.`allocatedAmountPaise`,
          pa.`allocatedAt`,
          'Migrated V2 invoice allocation',
          pa.`isReversed`
        FROM `payment_allocations` pa
        LEFT JOIN `subledger_entries` se ON se.`id` = pa.`paymentId`
        """.trimIndent()
      )

      db.execSQL("DROP TABLE `payment_allocations`")
      db.execSQL("ALTER TABLE `payment_allocations_v3` RENAME TO `payment_allocations`")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_allocations_paymentId` ON `payment_allocations` (`paymentId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_allocations_invoiceId` ON `payment_allocations` (`invoiceId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_allocations_partyType_partyId` ON `payment_allocations` (`partyType`, `partyId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_allocations_allocationType` ON `payment_allocations` (`allocationType`)")

      // 2. Normalize any V2 overpaid sales or purchase invoices without altering subledger_entries
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

      // 3. Reconcile any unallocated active PAYMENT subledger entries into explicit ADVANCE rows
      val paymentsCursor = db.query(
        """
        SELECT `id`, `partyType`, `partyId`, (`debitPaise` + `creditPaise`) AS `paymentTotal`, `entryDate`
        FROM `subledger_entries`
        WHERE `entryType` = 'PAYMENT' AND `isReversed` = 0 AND (`debitPaise` + `creditPaise`) > 0
        """.trimIndent()
      )
      paymentsCursor.use { cursor ->
        while (cursor.moveToNext()) {
          val paymentId = cursor.getLong(0)
          val partyType = cursor.getString(1)
          val partyId = cursor.getLong(2)
          val paymentTotal = cursor.getLong(3)
          val entryDate = cursor.getLong(4)

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
                "Migrated unallocated payment advance (V2->V3)"
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
        "SELECT `id`, `$partyIdColumn`, `invoiceNumber`, `totalAmountPaise` FROM `$tableName` WHERE `paidAmountPaise` > `totalAmountPaise`"
      )
      overpaidCursor.use { cursor ->
        while (cursor.moveToNext()) {
          val invoiceId = cursor.getLong(0)
          val partyId = cursor.getLong(1)
          val invoiceNumber = cursor.getString(2)
          val totalAmount = cursor.getLong(3)

          db.execSQL(
            "UPDATE `$tableName` SET `paidAmountPaise` = ?, `paymentStatus` = 'PAID' WHERE `id` = ?",
            arrayOf<Any>(totalAmount, invoiceId)
          )

          var remainingCap = totalAmount
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
              } else {
                val allowedOnInvoice = remainingCap
                val excessAdvance = allocAmount - allowedOnInvoice
                remainingCap = 0L

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
        }
      }
    }
  }
}
