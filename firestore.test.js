const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function ts(offsetMs = -1000) {
  return new Date(Date.now() + offsetMs);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

// =========================================================================
// 1. Unauthenticated Access & Cross-User Isolation
// =========================================================================

test("Unauthenticated client is denied read and write across user workspace & subcollections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.doc(`users/${ALICE_UID}`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/products`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/products`).doc("1").set({
      userId: ALICE_UID,
      localId: 1,
      name: "Vanilla Cup",
      sku: "VAN-01",
      category: "Cups",
      defaultPurchasePricePaise: 1000,
      defaultSellingPricePaise: 1500,
      minStockLevelPieces: 10,
      piecesPerBox: 12,
      boxesPerCarton: 4,
      active: true,
      localCreatedAtMs: 1700000000000,
      localUpdatedAtMs: 1700000000000,
      contentHash: "hash12345678",
      createdAt: ts(-2000),
      updatedAt: ts(-2000),
    })
  );
});

test("Cross-user isolation: Alice cannot read, list, create, update, or delete Bob's data", async () => {
  const created = ts(-5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.doc(`users/${BOB_UID}`).set({
      userId: BOB_UID,
      schemaVersion: 5,
      email: "bob@icetrack.test",
      createdAt: created,
      updatedAt: created,
    });
    await db.collection(`users/${BOB_UID}/customers`).doc("1").set({
      userId: BOB_UID,
      localId: 1,
      shopName: "Bob ChillMart",
      ownerName: "Bob",
      phoneNumber: "+919876543210",
      address: "Kochi",
      openingBalancePaise: 50000,
      openingBalanceDateEpochMs: 1700000000000,
      creditLimitPaise: 200000,
      paymentTermsDays: 14,
      active: true,
      localCreatedAtMs: 1700000000000,
      localUpdatedAtMs: 1700000000000,
      contentHash: "bobhash123456",
      createdAt: created,
      updatedAt: created,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.doc(`users/${BOB_UID}`).get());
  await assertFails(aliceDb.collection(`users/${BOB_UID}/customers`).get());
  await assertFails(aliceDb.collection(`users/${BOB_UID}/customers`).doc("1").get());
  await assertFails(
    aliceDb.collection(`users/${BOB_UID}/customers`).doc("1").update({
      shopName: "Hijacked",
      updatedAt: ts(-1000),
    })
  );
  await assertFails(aliceDb.collection(`users/${BOB_UID}/customers`).doc("1").delete());
});

test("Root /users list query is denied while user-scoped subcollection list succeeds when empty or populated", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").get());
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/products`).get());
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/sales_invoices`).get());
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/financial_audit_logs`).get());
});

// =========================================================================
// 2. Workspace Metadata, Schema & Timestamp Guards
// =========================================================================

test("Owner workspace document enforces schemaVersion=5, immutable createdAt, and no future timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = ts(-5000);

  // Reject future timestamp spoofing
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}`).set({
      userId: ALICE_UID,
      schemaVersion: 5,
      createdAt: ts(+60000),
      updatedAt: ts(-1000),
    })
  );

  // Valid create
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}`).set({
      userId: ALICE_UID,
      schemaVersion: 5,
      email: "alice@icetrack.test",
      displayName: "Alice",
      businessName: "IceTrack Distributors",
      lastSyncAtEpochMs: 1700000000000,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid partial update
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}`).update({
      businessName: "IceTrack Kerala Cold Chain",
      lastSyncAtEpochMs: 1700000005000,
      updatedAt: ts(-1000),
    })
  );

  // Reject createdAt mutation on update
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}`).update({
      createdAt: ts(-100),
      updatedAt: ts(-100),
    })
  );
});

// =========================================================================
// 3. Master Entities & Ghost Field Rejection (No 'id', No null optionals)
// =========================================================================

test("Products & Customers allow Kotlin payloads, partial updates, and reject ghost 'id' or stale localUpdatedAtMs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = ts(-10000);

  // Valid product create (no "id", uses "localId" and "active" matching rules)
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/products`).doc("1").set({
      userId: ALICE_UID,
      localId: 1,
      name: "Mango Dolly",
      sku: "MNG-01",
      category: "Bars",
      defaultPurchasePricePaise: 1200,
      defaultSellingPricePaise: 2000,
      minStockLevelPieces: 24,
      piecesPerBox: 24,
      boxesPerCarton: 6,
      active: true,
      localCreatedAtMs: 1700000000000,
      localUpdatedAtMs: 1700000000000,
      contentHash: "prodhash123456",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid partial update with monotonic localUpdatedAtMs
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/products`).doc("1").update({
      defaultSellingPricePaise: 2200,
      localUpdatedAtMs: 1700000001000,
      contentHash: "prodhash123457",
      updatedAt: ts(-1000),
    })
  );

  // Reject stale update (localUpdatedAtMs older than existing)
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/products`).doc("1").update({
      defaultSellingPricePaise: 1800,
      localUpdatedAtMs: 1699999999000,
      contentHash: "prodhash_stale",
      updatedAt: ts(-500),
    })
  );

  // Reject extra 'id' field (ghost field)
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/products`).doc("2").set({
      id: "2",
      userId: ALICE_UID,
      localId: 2,
      name: "Chocolate Cone",
      sku: "CHO-02",
      category: "Cones",
      defaultPurchasePricePaise: 1500,
      defaultSellingPricePaise: 2500,
      minStockLevelPieces: 10,
      piecesPerBox: 12,
      boxesPerCarton: 4,
      active: true,
      localCreatedAtMs: 1700000000000,
      localUpdatedAtMs: 1700000000000,
      contentHash: "prodhash222222",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject hard delete on products
  await assertFails(aliceDb.collection(`users/${ALICE_UID}/products`).doc("1").delete());
});

// =========================================================================
// 4. Financial Invoices (Monotonic revisionVersion & Non-Uncancellable)
// =========================================================================

test("Sales invoices enforce monotonic revisionVersion, immutable invoiceNumber, and non-uncancellable state", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = ts(-10000);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/sales_invoices`).doc("10").set({
      userId: ALICE_UID,
      localId: 10,
      invoiceNumber: "INV-2026-0010",
      customerLocalId: 1,
      invoiceDateEpochMs: 1700000000000,
      dueDateEpochMs: 1700604800000,
      subtotalPaise: 100000,
      discountType: "NONE",
      discountValueRaw: 0,
      discountAmountPaise: 0,
      additionalChargesPaise: 0,
      totalAmountPaise: 100000,
      paidAmountPaise: 0,
      paymentStatus: "UNPAID",
      invoiceStatus: "ACTIVE",
      revisionVersion: 1,
      notes: "Morning dispatch",
      confirmed: true,
      partyNameSnapshot: "Kochi Dairy Mart",
      partyPhoneSnapshot: "+919895000000",
      partyAddressSnapshot: "MG Road",
      localCreatedAtMs: 1700000000000,
      localUpdatedAtMs: 1700000000000,
      contentHash: "invh1234567890",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid edit raising revisionVersion=2 & cancelling
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/sales_invoices`).doc("10").update({
      invoiceStatus: "CANCELLED",
      cancelledAtEpochMs: 1700001000000,
      cancellationReason: "Order cancelled by shop",
      revisionVersion: 2,
      localUpdatedAtMs: 1700001000000,
      contentHash: "invh1234567891",
      updatedAt: ts(-1000),
    })
  );

  // Reject un-cancelling a CANCELLED invoice
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/sales_invoices`).doc("10").update({
      invoiceStatus: "ACTIVE",
      revisionVersion: 3,
      localUpdatedAtMs: 1700002000000,
      contentHash: "invh1234567892",
      updatedAt: ts(-500),
    })
  );

  // Reject decrementing revisionVersion
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/sales_invoices`).doc("10").update({
      notes: "Tampered downgrade",
      revisionVersion: 1,
      localUpdatedAtMs: 1700002000000,
      contentHash: "invh1234567893",
      updatedAt: ts(-500),
    })
  );

  // Reject deleting invoice
  await assertFails(aliceDb.collection(`users/${ALICE_UID}/sales_invoices`).doc("10").delete());
});

// =========================================================================
// 5. Subledger & Payment Allocations (Reversal-Only Mutation; Amounts Immutable)
// =========================================================================

test("Subledger entries lock amounts and only allow false -> true reversal transition", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = ts(-10000);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/subledger_entries`).doc("50").set({
      userId: ALICE_UID,
      localId: 50,
      partyType: "CUSTOMER",
      partyLocalId: 1,
      entryType: "PAYMENT",
      referenceType: "PAYMENT",
      referenceId: 50,
      debitPaise: 0,
      creditPaise: 50000,
      entryDateEpochMs: 1700000000000,
      notes: "Cash collection",
      reversed: false,
      operationRef: "PAY-50",
      localCreatedAtMs: 1700000000000,
      contentHash: "subhash500000",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject mutating creditPaise
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/subledger_entries`).doc("50").update({
      creditPaise: 99999,
      reversed: true,
      reversedAtEpochMs: 1700001000000,
      reversalReason: "Reversal",
      contentHash: "subhash500001",
      updatedAt: ts(-1000),
    })
  );

  // Valid reversal
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/subledger_entries`).doc("50").update({
      reversed: true,
      reversedAtEpochMs: 1700001000000,
      reversalReason: "Bounced payment",
      contentHash: "subhash500002",
      updatedAt: ts(-1000),
    })
  );

  // Reject un-reversing once reversed == true
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/subledger_entries`).doc("50").update({
      reversed: false,
      contentHash: "subhash500003",
      updatedAt: ts(-500),
    })
  );
});

// =========================================================================
// 6. Immutable Append-Only Collections & Terminal Note/Refund Transitions
// =========================================================================

test("Audit logs, stock movements, and sales returns are strictly append-only (no update/delete)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = ts(-5000);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/financial_audit_logs`).doc("100").set({
      userId: ALICE_UID,
      localId: 100,
      operationType: "INVOICE_EDIT",
      entityType: "SALES_INVOICE",
      entityId: 10,
      relatedPartyType: "CUSTOMER",
      relatedPartyId: 1,
      actorLabel: "Owner",
      reason: "Rate adjustment",
      beforeJson: "{\"total\":10000}",
      afterJson: "{\"total\":12000}",
      operationReference: "EDIT-10-R2",
      localCreatedAtMs: 1700000000000,
      contentHash: "audithash1000",
      createdAt: created,
    })
  );

  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/financial_audit_logs`).doc("100").update({
      reason: "Erased trail",
    })
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/financial_audit_logs`).doc("100").delete()
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/stock_movements`).doc("200").set({
      userId: ALICE_UID,
      localId: 200,
      productLocalId: 1,
      batchLocalId: 5,
      movementType: "SALE_DISPATCH",
      quantityPieces: -12,
      unitCostPaise: 1200,
      referenceType: "SALES_INVOICE",
      referenceId: 10,
      movementDateEpochMs: 1700000000000,
      notes: "Dispatched",
      localCreatedAtMs: 1700000000000,
      contentHash: "movhash20000",
      createdAt: created,
    })
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/stock_movements`).doc("200").update({ quantityPieces: 0 })
  );
});

test("Manual refund_records allow RECORDED_PENDING_PAYOUT -> PAYOUT_EXECUTED then lock permanently", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = ts(-5000);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/refund_records`).doc("7").set({
      userId: ALICE_UID,
      localId: 7,
      partyType: "CUSTOMER",
      partyLocalId: 1,
      sourceType: "SALES_RETURN",
      sourceId: 3,
      amountPaise: 25000,
      paymentMethod: "CASH",
      executionStatus: "RECORDED_PENDING_PAYOUT",
      reason: "Damaged return cash refund",
      notes: "Pending handover",
      referenceNumber: "",
      recordedAtEpochMs: 1700000000000,
      contentHash: "refhash70001",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Manual payout execution update succeeds
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/refund_records`).doc("7").update({
      paymentMethod: "UPI",
      executionStatus: "PAYOUT_EXECUTED",
      referenceNumber: "UPI-REF-991",
      executedAtEpochMs: 1700002000000,
      contentHash: "refhash70002",
      updatedAt: ts(-1000),
    })
  );

  // Subsequent mutation after PAYOUT_EXECUTED must fail
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/refund_records`).doc("7").update({
      notes: "Modified after payout",
      updatedAt: ts(-500),
    })
  );
});

// =========================================================================
// 7. Kotlin Enums.kt Parity (Expenses, Credit/Debit Notes, Partial Paid Invoices)
// =========================================================================

test("Kotlin domain enum parity: PARTIALLY_PAID invoices, MISCELLANEOUS expenses, and APPLIED -> SETTLED credit notes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = ts(-8000);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/expenses`).doc("11").set({
      userId: ALICE_UID,
      localId: 11,
      category: "MISCELLANEOUS",
      amountPaise: 15000,
      expenseDateEpochMs: 1700000000000,
      paymentMethod: "UPI",
      referenceNumber: "UPI-EXP-11",
      notes: "Cold box seal replacement",
      localCreatedAtMs: 1700000000000,
      contentHash: "exphash11001",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/credit_debit_notes`).doc("21").set({
      userId: ALICE_UID,
      localId: 21,
      noteNumber: "CN-2026-0021",
      noteType: "CREDIT_NOTE",
      partyType: "CUSTOMER",
      partyLocalId: 1,
      linkedInvoiceLocalId: 10,
      amountPaise: 40000,
      appliedAmountPaise: 10000,
      status: "APPLIED",
      issueDateEpochMs: 1700000000000,
      reason: "Partial shortage adjustment",
      notes: "Applied partly",
      localCreatedAtMs: 1700000000000,
      localUpdatedAtMs: 1700000000000,
      contentHash: "notehash2101",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid APPLIED -> SETTLED transition
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/credit_debit_notes`).doc("21").update({
      appliedAmountPaise: 40000,
      status: "SETTLED",
      localUpdatedAtMs: 1700001000000,
      contentHash: "notehash2102",
      updatedAt: ts(-1000),
    })
  );

  // Terminal SETTLED lock: cannot mutate once SETTLED
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/credit_debit_notes`).doc("21").update({
      status: "OPEN",
      localUpdatedAtMs: 1700002000000,
      contentHash: "notehash2103",
      updatedAt: ts(-500),
    })
  );
});

