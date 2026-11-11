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
}
