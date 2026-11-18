package com.example.data.sync

import com.example.data.local.IceTrackDatabase
import com.example.data.remote.FirestoreFinanceMappers
import com.example.data.remote.FirestoreSyncMappers
import com.example.service.StrictJson

internal data class LocalSyncRow(
  val localKey: String,
  val localId: Long,
  val cloudDocId: String,
  val displayLabel: String,
  val fields: Map<String, Any>,
  val contentHash: String,
  val localUpdatedAtMs: Long,
  val revisionVersion: Int
)

internal class SyncCollectionSpec(
  val collectionName: String,
  val appendOnly: Boolean,
  val immutableUpdateKeys: Set<String>,
  val loadLocalRows: suspend (uid: String, db: IceTrackDatabase, nowMs: Long) -> List<LocalSyncRow>,
  val canUpdateRemote: (localFields: Map<String, Any>, remoteData: Map<String, Any?>) -> Boolean = { _, _ -> true },
  val extractRemoteRevision: (Map<String, Any?>) -> Int = { (it["revisionVersion"] as? Number)?.toInt() ?: 1 },
  val extractRemoteUpdatedAtMs: (Map<String, Any?>) -> Long = {
    (it["localUpdatedAtMs"] as? Number)?.toLong()
      ?: (it["localCreatedAtMs"] as? Number)?.toLong()
      ?: 0L
  }
) {
  companion object {
    private fun sanitizedKey(raw: String): String =
      raw.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "default" }.take(128)

    fun buildAllSpecs(): List<SyncCollectionSpec> = listOf(
      SyncCollectionSpec(
        collectionName = "products",
        appendOnly = false,
        immutableUpdateKeys = setOf("userId", "localId", "localCreatedAtMs"),
        loadLocalRows = { uid, db, now ->
          val unitsByProduct = db.productUnitDao().getAllSync().associateBy { it.productId }
          db.productDao().getAllIncludingArchivedSync().map { p ->
            val (fields, hash) = FirestoreSyncMappers.toProductFields(uid, p, unitsByProduct[p.id], now)
            LocalSyncRow(
              localKey = p.id.toString(),
              localId = p.id,
              cloudDocId = p.id.toString(),
              displayLabel = "Product: ${p.name} (${p.sku})",
              fields = fields,
              contentHash = hash,
              localUpdatedAtMs = (fields["localUpdatedAtMs"] as Number).toLong(),
              revisionVersion = 1
            )
          }
        },
        canUpdateRemote = { local, remote ->
          val localUpd = (local["localUpdatedAtMs"] as? Number)?.toLong() ?: 0L
          val remoteUpd = (remote["localUpdatedAtMs"] as? Number)?.toLong() ?: 0L
          localUpd >= remoteUpd
        }
      ),
      SyncCollectionSpec(
        collectionName = "suppliers",
        appendOnly = false,
        immutableUpdateKeys = setOf("userId", "localId", "localCreatedAtMs"),
        loadLocalRows = { uid, db, now ->
          db.supplierDao().getAllIncludingArchivedSync().map { s ->
            val (fields, hash) = FirestoreSyncMappers.toSupplierFields(uid, s, now)
            LocalSyncRow(
              localKey = s.id.toString(),
              localId = s.id,
              cloudDocId = s.id.toString(),
              displayLabel = "Supplier: ${s.name}",
              fields = fields,
              contentHash = hash,
              localUpdatedAtMs = (fields["localUpdatedAtMs"] as Number).toLong(),
              revisionVersion = 1
            )
          }
        },
        canUpdateRemote = { local, remote ->
          val localUpd = (local["localUpdatedAtMs"] as? Number)?.toLong() ?: 0L
          val remoteUpd = (remote["localUpdatedAtMs"] as? Number)?.toLong() ?: 0L
          localUpd >= remoteUpd
        }
      ),
      SyncCollectionSpec(
        collectionName = "customers",
        appendOnly = false,
        immutableUpdateKeys = setOf("userId", "localId", "localCreatedAtMs"),
        loadLocalRows = { uid, db, now ->
          db.customerDao().getAllIncludingArchivedSync().map { c ->
            val (fields, hash) = FirestoreSyncMappers.toCustomerFields(uid, c, now)
            LocalSyncRow(
              localKey = c.id.toString(),
              localId = c.id,
              cloudDocId = c.id.toString(),
              displayLabel = "Customer: ${c.shopName}",
              fields = fields,
              contentHash = hash,
              localUpdatedAtMs = (fields["localUpdatedAtMs"] as Number).toLong(),
              revisionVersion = 1
            )
          }
        },
        canUpdateRemote = { local, remote ->
          val localUpd = (local["localUpdatedAtMs"] as? Number)?.toLong() ?: 0L
          val remoteUpd = (remote["localUpdatedAtMs"] as? Number)?.toLong() ?: 0L
          localUpd >= remoteUpd
        }
      ),
      SyncCollectionSpec(
        collectionName = "customer_product_prices",
        appendOnly = false,
        immutableUpdateKeys = setOf("userId", "localId"),
        loadLocalRows = { uid, db, now ->
          db.customerProductPriceDao().getAllSync().map { cp ->
            val (fields, hash) = FirestoreSyncMappers.toCustomerPriceFields(uid, cp, now)
            LocalSyncRow(
              localKey = cp.id.toString(),
              localId = cp.id,
              cloudDocId = cp.id.toString(),
              displayLabel = "Customer Price #${cp.id}",
              fields = fields,
              contentHash = hash,
              localUpdatedAtMs = now,
              revisionVersion = 1
            )
          }
        }
      ),
      SyncCollectionSpec(
        collectionName = "product_suppliers",
        appendOnly = false,
        immutableUpdateKeys = setOf("userId", "localId"),
        loadLocalRows = { uid, db, now ->
          db.productSupplierDao().getAllSync().map { ps ->
            val (fields, hash) = FirestoreSyncMappers.toProductSupplierFields(uid, ps, now)
            LocalSyncRow(
              localKey = ps.id.toString(),
              localId = ps.id,
              cloudDocId = ps.id.toString(),
              displayLabel = "Product Supplier #${ps.id}",
              fields = fields,
              contentHash = hash,
              localUpdatedAtMs = now,
              revisionVersion = 1
            )
          }
        }
      ),
      SyncCollectionSpec(
        collectionName = "product_batches",
        appendOnly = false,
        immutableUpdateKeys = setOf("userId", "localId", "productLocalId", "purchaseInvoiceLocalId", "purchaseDateEpochMs", "localCreatedAtMs"),
        loadLocalRows = { uid, db, now ->
          db.productBatchDao().getAllBatchesSync().map { b ->
            val (fields, hash) = FirestoreSyncMappers.toBatchFields(uid, b, now)
            LocalSyncRow(
              localKey = b.id.toString(),
              localId = b.id,
              cloudDocId = b.id.toString(),
              displayLabel = "Batch ${b.batchNumber}",
              fields = fields,
              contentHash = hash,
              localUpdatedAtMs = (fields["localUpdatedAtMs"] as Number).toLong(),
              revisionVersion = 1
            )
          }
        },
        canUpdateRemote = { local, remote ->
          val localUpd = (local["localUpdatedAtMs"] as? Number)?.toLong() ?: 0L
          val remoteUpd = (remote["localUpdatedAtMs"] as? Number)?.toLong() ?: 0L
          val sameProd = (local["productLocalId"] as? Number)?.toLong() == (remote["productLocalId"] as? Number)?.toLong()
          localUpd >= remoteUpd && sameProd
        }
      ),
      SyncCollectionSpec(
        collectionName = "stock_movements",
        appendOnly = true,
        immutableUpdateKeys = emptySet(),
        loadLocalRows = { uid, db, _ ->
          db.stockMovementDao().getAllSync().map { m ->
            val (fields, hash) = FirestoreSyncMappers.toStockMovementFields(uid, m)
            LocalSyncRow(
              localKey = m.id.toString(),
              localId = m.id,
              cloudDocId = m.id.toString(),
              displayLabel = "Stock ${m.movementType.name} #${m.id}",
              fields = fields,
              contentHash = hash,
              localUpdatedAtMs = m.createdAt,
              revisionVersion = 1
            )
          }
        }
      ),
      SyncCollectionSpec(
        collectionName = "purchase_invoices",
        appendOnly = false,
        immutableUpdateKeys = setOf("userId", "localId", "invoiceNumber", "localCreatedAtMs"),
        loadLocalRows = { uid, db, _ ->
          db.purchaseInvoiceDao().getAllSync().map { inv ->
            val (fields, hash) = FirestoreSyncMappers.toPurchaseInvoiceFields(uid, inv)
            LocalSyncRow(
              localKey = inv.id.toString(),
              localId = inv.id,
              cloudDocId = inv.id.toString(),
              displayLabel = "Purchase ${inv.invoiceNumber}",
              fields = fields,
              contentHash = hash,
              localUpdatedAtMs = (fields["localUpdatedAtMs"] as Number).toLong(),
              revisionVersion = inv.revisionVersion
            )
          }
        },
        canUpdateRemote = { local, remote ->
          val localRev = (local["revisionVersion"] as? Number)?.toInt() ?: 1
          val remoteRev = (remote["revisionVersion"] as? Number)?.toInt() ?: 1
          val localUpd = (local["localUpdatedAtMs"] as? Number)?.toLong() ?: 0L
          val remoteUpd = (remote["localUpdatedAtMs"] as? Number)?.toLong() ?: 0L
          val remoteStatus = remote["invoiceStatus"] as? String
          val localStatus = local["invoiceStatus"] as? String
          localRev >= remoteRev && localUpd >= remoteUpd && (remoteStatus != "CANCELLED" || localStatus == "CANCELLED")
        }
      ),
      SyncCollectionSpec(
        collectionName = "purchase_invoice_items",
        appendOnly = false,
        immutableUpdateKeys = setOf("userId", "localId"),
        loadLocalRows = { uid, db, now ->
          db.purchaseInvoiceItemDao().getAllItemsSync().map { item ->
            val (fields, hash) = FirestoreSyncMappers.toPurchaseInvoiceItemFields(uid, item, now)
            LocalSyncRow(
              localKey = item.id.toString(),
              localId = item.id,
              cloudDocId = item.id.toString(),
              displayLabel = "Purchase Item #${item.id}",
              fields = fields,
              contentHash = hash,
              localUpdatedAtMs = now,
              revisionVersion = 1
            )
          }
        }
      ),
      SyncCollectionSpec(
        collectionName = "sales_invoices",
        appendOnly = false,
        immutableUpdateKeys = setOf("userId", "localId", "invoiceNumber", "localCreatedAtMs"),
        loadLocalRows = { uid, db, _ ->
          db.salesInvoiceDao().getAllSync().map { inv ->
            val (fields, hash) = FirestoreSyncMappers.toSalesInvoiceFields(uid, inv)
            LocalSyncRow(
              localKey = inv.id.toString(),
              localId = inv.id,
              cloudDocId = inv.id.toString(),
              displayLabel = "Sales ${inv.invoiceNumber}",
              fields = fields,
              contentHash = hash,
              localUpdatedAtMs = (fields["localUpdatedAtMs"] as Number).toLong(),
              revisionVersion = inv.revisionVersion
            )
          }
        },
        canUpdateRemote = { local, remote ->
          val localRev = (local["revisionVersion"] as? Number)?.toInt() ?: 1
          val remoteRev = (remote["revisionVersion"] as? Number)?.toInt() ?: 1
          val localUpd = (local["localUpdatedAtMs"] as? Number)?.toLong() ?: 0L
          val remoteUpd = (remote["localUpdatedAtMs"] as? Number)?.toLong() ?: 0L
          val remoteStatus = remote["invoiceStatus"] as? String
          val localStatus = local["invoiceStatus"] as? String
          localRev >= remoteRev && localUpd >= remoteUpd && (remoteStatus != "CANCELLED" || localStatus == "CANCELLED")
        }
      ),
      SyncCollectionSpec(
        collectionName = "sales_invoice_items",
        appendOnly = false,
        immutableUpdateKeys = setOf("userId", "localId"),
        loadLocalRows = { uid, db, now ->
          db.salesInvoiceItemDao().getAllItemsSync().map { item ->
            val (fields, hash) = FirestoreSyncMappers.toSalesInvoiceItemFields(uid, item, now)
            LocalSyncRow(
              localKey = item.id.toString(),
              localId = item.id,
              cloudDocId = item.id.toString(),
              displayLabel = "Sales Item #${item.id}",
              fields = fields,
              contentHash = hash,
              localUpdatedAtMs = now,
              revisionVersion = 1
            )
          }
        }
      ),
      SyncCollectionSpec(
        collectionName = "subledger_entries",
        appendOnly = false,
        immutableUpdateKeys = setOf(
          "userId", "localId", "partyType", "partyLocalId", "entryType",
          "referenceType", "referenceId", "debitPaise", "creditPaise",
          "entryDateEpochMs", "notes", "operationRef", "localCreatedAtMs"
        ),
        loadLocalRows = { uid, db, _ ->
          db.subledgerDao().getAllEntriesSync().map { s ->
            val (fields, hash) = FirestoreFinanceMappers.toSubledgerFields(uid, s)
            LocalSyncRow(
              localKey = s.id.toString(),
              localId = s.id,
              cloudDocId = s.id.toString(),
              displayLabel = "Ledger ${s.entryType.name} #${s.id}",
              fields = fields,
              contentHash = hash,
              localUpdatedAtMs = s.reversedAt ?: s.createdAt,
              revisionVersion = if (s.isReversed) 2 else 1
            )
          }
        },
        canUpdateRemote = { local, remote ->
          val remoteRev = (remote["reversed"] as? Boolean) ?: false
          val localRev = (local["reversed"] as? Boolean) ?: false
          !remoteRev && localRev
        },
        extractRemoteRevision = { if ((it["reversed"] as? Boolean) == true) 2 else 1 }
      ),
      SyncCollectionSpec(
        collectionName = "payment_allocations",
        appendOnly = false,
        immutableUpdateKeys = setOf(
          "userId", "localId", "paymentLocalId", "partyType", "partyLocalId",
          "allocationType", "invoiceLocalId", "allocatedAmountPaise",
          "allocatedAtEpochMs", "notes", "fromAdvanceConsumption"
        ),
        loadLocalRows = { uid, db, _ ->
          db.paymentAllocationDao().getAllSync().map { a ->
            val (fields, hash) = FirestoreFinanceMappers.toPaymentAllocationFields(uid, a)
            LocalSyncRow(
              localKey = a.id.toString(),
              localId = a.id,
              cloudDocId = a.id.toString(),
              displayLabel = "Allocation #${a.id}",
              fields = fields,
              contentHash = hash,
              localUpdatedAtMs = a.reversedAt ?: a.allocatedAt,
              revisionVersion = if (a.isReversed) 2 else 1
            )
          }
        },
        canUpdateRemote = { local, remote ->
          val remoteRev = (remote["reversed"] as? Boolean) ?: false
          val localRev = (local["reversed"] as? Boolean) ?: false
          !remoteRev && localRev
        },
        extractRemoteRevision = { if ((it["reversed"] as? Boolean) == true) 2 else 1 }
      ),
      SyncCollectionSpec(
        collectionName = "sales_returns",
        appendOnly = true,
        immutableUpdateKeys = emptySet(),
        loadLocalRows = { uid, db, _ ->
          db.salesReturnDao().getAllSync().map { r ->
            val (fields, hash) = FirestoreFinanceMappers.toSalesReturnFields(uid, r)
            LocalSyncRow(r.id.toString(), r.id, r.id.toString(), "Sales Return ${r.returnNumber}", fields, hash, r.createdAt, 1)
          }
        }
      ),
      SyncCollectionSpec(
        collectionName = "sales_return_items",
        appendOnly = true,
        immutableUpdateKeys = emptySet(),
        loadLocalRows = { uid, db, _ ->
          db.salesReturnDao().getAllItemsSync().map { item ->
            val (fields, hash) = FirestoreFinanceMappers.toSalesReturnItemFields(uid, item)
            LocalSyncRow(item.id.toString(), item.id, item.id.toString(), "Sales Return Item #${item.id}", fields, hash, 0L, 1)
          }
        }
      ),
      SyncCollectionSpec(
        collectionName = "purchase_returns",
        appendOnly = true,
        immutableUpdateKeys = emptySet(),
        loadLocalRows = { uid, db, _ ->
          db.purchaseReturnDao().getAllSync().map { r ->
            val (fields, hash) = FirestoreFinanceMappers.toPurchaseReturnFields(uid, r)
            LocalSyncRow(r.id.toString(), r.id, r.id.toString(), "Purchase Return ${r.returnNumber}", fields, hash, r.createdAt, 1)
          }
        }
      ),
      SyncCollectionSpec(
        collectionName = "purchase_return_items",
        appendOnly = true,
        immutableUpdateKeys = emptySet(),
        loadLocalRows = { uid, db, _ ->
          db.purchaseReturnDao().getAllItemsSync().map { item ->
            val (fields, hash) = FirestoreFinanceMappers.toPurchaseReturnItemFields(uid, item)
            LocalSyncRow(item.id.toString(), item.id, item.id.toString(), "Purchase Return Item #${item.id}", fields, hash, 0L, 1)
          }
        }
      ),
      SyncCollectionSpec(
        collectionName = "credit_debit_notes",
        appendOnly = false,
        immutableUpdateKeys = setOf(
          "userId", "localId", "noteNumber", "noteType", "partyType", "partyLocalId",
          "linkedInvoiceLocalId", "linkedReturnLocalId", "amountPaise", "issueDateEpochMs",
          "reason", "idempotencyKey", "localCreatedAtMs"
        ),
        loadLocalRows = { uid, db, _ ->
          db.creditDebitNoteDao().getAllSync().map { n ->
            val (fields, hash) = FirestoreFinanceMappers.toCreditDebitNoteFields(uid, n)
            LocalSyncRow(
              localKey = n.id.toString(),
              localId = n.id,
              cloudDocId = n.id.toString(),
              displayLabel = "Note ${n.noteNumber}",
              fields = fields,
              contentHash = hash,
              localUpdatedAtMs = n.updatedAt,
              revisionVersion = 1
            )
          }
        },
        canUpdateRemote = { _, remote ->
          val st = remote["status"] as? String
          st == "OPEN" || st == "APPLIED"
        }
      ),
      SyncCollectionSpec(
        collectionName = "refund_records",
        appendOnly = false,
        immutableUpdateKeys = setOf(
          "userId", "localId", "partyType", "partyLocalId", "sourceType", "sourceId",
          "amountPaise", "reason", "recordedAtEpochMs", "idempotencyKey"
        ),
        loadLocalRows = { uid, db, _ ->
          db.refundRecordDao().getAllSync().map { r ->
            val (fields, hash) = FirestoreFinanceMappers.toRefundRecordFields(uid, r)
            LocalSyncRow(
              localKey = r.id.toString(),
              localId = r.id,
              cloudDocId = r.id.toString(),
              displayLabel = "Refund #${r.id} (${r.executionStatus.name})",
              fields = fields,
              contentHash = hash,
              localUpdatedAtMs = r.executedAt ?: r.recordedAt,
              revisionVersion = if (r.executionStatus.name == "PAYOUT_EXECUTED") 2 else 1
            )
          }
        },
        canUpdateRemote = { local, remote ->
          val rStatus = remote["executionStatus"] as? String
          val lStatus = local["executionStatus"] as? String
          rStatus == "RECORDED_PENDING_PAYOUT" && lStatus == "PAYOUT_EXECUTED"
        },
        extractRemoteRevision = { if ((it["executionStatus"] as? String) == "PAYOUT_EXECUTED") 2 else 1 }
      ),
      SyncCollectionSpec(
        collectionName = "financial_audit_logs",
        appendOnly = true,
        immutableUpdateKeys = emptySet(),
        loadLocalRows = { uid, db, _ ->
          db.financialAuditLogDao().getAllSync().map { a ->
            val (fields, hash) = FirestoreFinanceMappers.toAuditLogFields(uid, a)
            LocalSyncRow(a.id.toString(), a.id, a.id.toString(), "Audit #${a.id} (${a.operationType.name})", fields, hash, a.createdAt, 1)
          }
        }
      ),
      SyncCollectionSpec(
        collectionName = "expenses",
        appendOnly = false,
        immutableUpdateKeys = setOf("userId", "localId", "localCreatedAtMs"),
        loadLocalRows = { uid, db, _ ->
          db.expenseDao().getAllIncludingDeletedSync().map { e ->
            val (fields, hash) = FirestoreFinanceMappers.toExpenseFields(uid, e)
            LocalSyncRow(
              localKey = e.id.toString(),
              localId = e.id,
              cloudDocId = e.id.toString(),
              displayLabel = "Expense ${e.category.name} #${e.id}",
              fields = fields,
              contentHash = hash,
              localUpdatedAtMs = e.deletedAt ?: e.createdAt,
              revisionVersion = 1
            )
          }
        }
      ),
      SyncCollectionSpec(
        collectionName = "payment_reminders",
        appendOnly = false,
        immutableUpdateKeys = setOf("userId", "localId", "localCreatedAtMs"),
        loadLocalRows = { uid, db, _ ->
          db.customerPaymentReminderDao().getAllRemindersSync().map { r ->
            val (fields, hash) = FirestoreFinanceMappers.toPaymentReminderFields(uid, r)
            LocalSyncRow(
              localKey = r.id.toString(),
              localId = r.id,
              cloudDocId = r.id.toString(),
              displayLabel = "Reminder #${r.id} (${r.status.name})",
              fields = fields,
              contentHash = hash,
              localUpdatedAtMs = r.updatedAt,
              revisionVersion = 1
            )
          }
        }
      ),
      SyncCollectionSpec(
        collectionName = "payment_reminder_invoices",
        appendOnly = true,
        immutableUpdateKeys = emptySet(),
        loadLocalRows = { uid, db, _ ->
          db.customerPaymentReminderDao().getAllReminderInvoicesSync().map { item ->
            val (fields, hash) = FirestoreFinanceMappers.toPaymentReminderInvoiceFields(uid, item)
            LocalSyncRow(item.id.toString(), item.id, item.id.toString(), "Reminder Invoice #${item.id}", fields, hash, 0L, 1)
          }
        }
      ),
      SyncCollectionSpec(
        collectionName = "app_settings",
        appendOnly = false,
        immutableUpdateKeys = setOf("userId", "settingKey"),
        loadLocalRows = { uid, db, now ->
          db.appSettingDao().getAllSync().map { s ->
            val (fields, hash) = FirestoreFinanceMappers.toAppSettingFields(uid, s, now)
            LocalSyncRow(
              localKey = s.key,
              localId = 0L,
              cloudDocId = sanitizedKey(s.key),
              displayLabel = "Setting: ${s.key}",
              fields = fields,
              contentHash = hash,
              localUpdatedAtMs = now,
              revisionVersion = 1
            )
          }
        }
      )
    )

    fun serializeRemotePayloadForConflict(data: Map<String, Any?>): String {
      val jsonSafe = linkedMapOf<String, Any?>()
      for ((k, v) in data) {
        if (k != "createdAt" && k != "updatedAt") {
          jsonSafe[k] = v
        }
      }
      return StrictJson.stringify(jsonSafe, indent = 0)
    }

    fun parseRemotePayloadForConflict(json: String): Map<String, Any?> =
      StrictJson.parseObject(json)
  }
}
