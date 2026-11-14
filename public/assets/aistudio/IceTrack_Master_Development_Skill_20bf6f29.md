# ICE TRACK --- MASTER DEVELOPMENT SKILL

## Existing Kotlin Android Application \| Google AI Studio Instructions

**Repository:** https://github.com/irfan-v-creator/Ice-Track-\
**Application:** IceTrack\
**Project type:** One-Man Business Management Application\
**Primary stack:** Existing Kotlin + Jetpack Compose + Room architecture
(must be verified from source)

------------------------------------------------------------------------

# 1. PURPOSE

Improve and extend the existing IceTrack Android application without
rebuilding it or unnecessarily changing its established architecture.

The application is intended for a **one-man business workflow**. Do not
introduce salesman roles, multi-user role management, or salesman
authentication.

The existing application is the foundation. Inspect the actual source
code before proposing changes. Preserve existing functionality and
historical business data.

# 2. STRICT PROJECT RULES

1.  Work only inside the existing IceTrack Android application.
2.  Do not convert the project to Flutter, Java, React, or another
    framework.
3.  Do not rebuild the application from scratch.
4.  Preserve the existing Kotlin architecture, Jetpack Compose UI, Room
    database, repositories, ViewModels, calculation engines, and
    reusable components wherever they are present and appropriate.
5.  Never assume a class, table, field, function, dependency,
    integration, or feature exists. Verify against the actual source.
6.  Do not remove, rename, move, or replace existing files or features
    without explicit authorization.
7.  Do not introduce dummy data, fake functionality, simulated
    integrations, or placeholder success messages.
8.  Do not expose API keys, credentials, personal data, or financial
    records.
9.  Do not claim that a feature works or a test passes unless it has
    actually been verified.
10. Keep changes modular, maintainable, secure, accessible,
    mobile-first, and performance-conscious.

# 3. APPLICATION SCOPE --- ONE-MAN BUSINESS

IceTrack is a one-man business application.

The following are **not requirements** for this project:

-   Salesman role.
-   Salesman login or authentication.
-   Admin/Salesman role separation.
-   Multi-user role management.
-   Salesman-specific permissions or dashboards.

Do not add these features or create architecture for them.

Do not spend audit time searching for existing implementations of the
following items, because the project owner has confirmed they are not
part of the current project:

-   Existing Login / Authentication system.
-   Existing user data structure for multi-user or salesman accounts.
-   Existing Customer Database as a pre-existing module for the proposed
    visit-tracking feature.
-   Existing GPS / Location permission implementation.
-   Existing Map dependencies or map-provider integration.

**Important distinction:** Do not infer that these systems exist. Do not
audit them as existing functionality. If a future approved feature
genuinely requires new data structures or permissions, first propose the
minimum necessary design and wait for authorization.

# 4. READ-ONLY PROJECT INSPECTION --- FIRST STAGE

Initially operate in **READ-ONLY MODE**.

Inspect the actual project files and report what is present. Do not make
changes during this stage.

Inspect relevant existing areas, including:

-   Project structure and Gradle configuration.
-   Kotlin and Android Gradle Plugin versions.
-   Jetpack Compose setup and theme.
-   Room database, entities, DAOs, and migrations that actually exist.
-   Repositories and ViewModels.
-   Purchase and sales workflows.
-   Inventory, batches, FEFO calculations, and stock movements.
-   Customer and supplier ledger-related code that actually exists.
-   Payment allocation and financial calculation engines.
-   Reports, PDF, XLSX, and CSV exports.
-   Settings and business-profile implementation.
-   Backup and restore implementation, if present.
-   Existing tests and test infrastructure.
-   Existing security, data-protection, and Android backup
    configuration.

Do not inspect excluded items from Section 3 as though they were
existing systems.

# 5. MANDATORY FINANCIAL AND DATA-INTEGRITY AUDIT

Verify the following previous audit findings against the current source
code. Do not blindly accept earlier reports; confirm each finding using
actual code.

## Critical and high-priority candidates

1.  Duplicate-product overselling in sales invoices.
2.  Atomic stock deduction and SQL quantity guards.
3.  Missing or incomplete Backup and Restore.
4.  Financial transaction reversal, cancellation, returns, and
    audit-trail consistency.
5.  Opening-balance and ledger reconciliation.
6.  Overpayments, advances, and unallocated payment handling.
7.  Outstanding-balance consistency across dashboard, reports, and
    exports.
8.  Atomicity of multi-step database writes.
9.  Invoice-number collision risks.
10. Misleading settings statuses for features that are not implemented.

## Other calculation, reporting, and security candidates

-   Indian currency digit grouping.
-   Dashboard low-stock and expiry calculations.
-   Calendar-day versus rolling-24-hour date filtering.
-   Credit limits and payment terms.
-   Business profile persistence and use in documents.
-   Stock movement cost accuracy.
-   CSV formula injection.
-   Invalid money parsing and input validation.
-   Thread safety of date and number formatters.
-   Exception handling and coroutine cancellation.
-   Soft-delete and unique-SKU behavior.
-   Foreign-key integrity and migration safety.
-   Export-file retention and sensitive-data handling.
-   Release-build optimization and dependency usage.

For every finding, provide:

-   Severity: Critical / High / Medium / Low.
-   Exact file, class, and method.
-   Evidence from the source.
-   Reproduction scenario or test evidence.
-   Business and data impact.
-   Recommended correction.
-   Data migration implications.
-   Whether the fix is additive, modifying, or potentially destructive.
-   Verification required.

# 6. FINANCE SYSTEM INTEGRATION

Inspect and map the real data flow between these components if they
exist:

1.  `Repositories.kt` --- data management.
2.  `BusinessReportEngine.kt` --- business reports and Profit & Loss.
3.  `SubledgerCalculationEngine.kt` --- ledger calculations.
4.  `PaymentAllocationEngine.kt` --- payment allocation.
5.  `ReportsViewModel.kt` --- financial reports.

Do not assume these files or exact names exist until verified.

Requirements:

-   Identify the actual source of truth for each financial value.
-   Avoid duplicate or inconsistent calculation logic.
-   Reconcile debit, credit, balance, collections, payables,
    receivables, and payment allocations.
-   Connect reports to actual persisted records.
-   Handle empty data, invalid values, rounding, and date boundaries.
-   Preserve historical financial records.
-   Use database transactions for logically atomic financial operations.
-   Add appropriate unit and database integration tests.

Do not replace an existing calculation engine unless a documented
technical reason justifies it and the owner authorizes the change.

# 7. FEATURE ONE --- ADVANCED FINANCIAL DASHBOARD

This is a proposed new feature. First inspect the existing dashboard and
financial calculation architecture. Then prepare a design and
implementation plan. Do not implement before authorization.

Proposed metrics:

-   Total Sales.
-   Total Collections.
-   Total Outstanding.
-   Total Expenses.
-   Gross Profit.
-   Net Profit.
-   Customer Receivables, if supported by the verified ledger model.
-   Supplier Payables, if supported by the verified ledger model.
-   Cash In.
-   Cash Out.

Proposed date filters:

-   Today.
-   Yesterday.
-   This Week.
-   This Month.
-   Custom Date Range.

Potential visualizations and summaries:

-   Revenue and expense charts.
-   Daily, weekly, and monthly comparisons.
-   Top customers by sales, if the required data is available.
-   Outstanding-payment summary.
-   Collection performance.

Requirements:

-   All figures must be derived from actual database records and
    verified calculation logic.
-   No dummy, hard-coded, or fabricated financial values.
-   Define each metric precisely, including date basis and
    inclusion/exclusion rules.
-   Avoid counting the same transaction twice.
-   Use Room Flow / StateFlow or the existing reactive data pattern for
    updates.
-   Include loading, empty, and error states.
-   Ensure calculations remain correct for refunds, reversals, advances,
    and date-range boundaries once those workflows are supported.
-   Do not add Admin/Salesman role-based visibility. This is a one-man
    application.

# 8. FEATURE TWO --- CUSTOMER VISIT TRACKING

This is a proposed new feature. The current project owner has confirmed
that an existing Customer Database module, GPS permission
implementation, and map integration are not present as existing project
components. Do not pretend otherwise.

First prepare a feasibility assessment and a minimal data-design
proposal. Do not implement before authorization.

## Proposed visit workflow

1.  Open Customer Visit Tracking.
2.  Select or create the necessary customer record using a newly
    proposed customer data structure, if approved.
3.  Start a visit.
4.  Record visit date and time.
5.  Optionally capture the visit location with explicit user permission.
6.  Record visit purpose.
7.  Add discussion notes.
8.  Record visit outcome:
    -   Order Received.
    -   Follow-up Required.
    -   No Order.
    -   Payment Collected.
9.  Optionally enter a next follow-up date.
10. Save the visit.
11. View visit history and follow-up information.

## Visit history

Propose support for:

-   Customer-wise visit history.
-   Date-wise visit history.
-   Previous visit notes.
-   Next follow-up dates and reminders.
-   Visit outcome/status.

Do not add salesman-wise history or salesman assignment because IceTrack
is a one-man application.

## GPS, location accuracy, and privacy

If GPS capture is approved:

-   Request location permission only when the user invokes a
    location-related action.
-   Explain why location is requested.
-   Do not continuously track location.
-   Do not implement background tracking.
-   Allow a visit to be saved without GPS.
-   Handle permission denial, location-service disablement, timeout, and
    unavailable location gracefully.
-   Record accuracy metadata where available.
-   Define an acceptable accuracy threshold and clearly indicate when
    the captured location is approximate or insufficient.
-   Do not silently treat an inaccurate location as an exact visit
    location.
-   Store only the minimum location data required.
-   Restrict location access to the local app workflow.
-   Provide a clear way to review or remove visit location data, subject
    to the approved data-retention design.
-   Never transmit location data to a cloud service unless a separate
    integration and privacy decision is approved.

## Map integration

No map provider has been selected.

Do not add a map SDK, API key, dependency, or paid service
automatically.

First compare suitable options, including their:

-   Cost and usage limits.
-   API-key requirements.
-   Android/Kotlin compatibility.
-   Privacy implications.
-   Offline limitations.
-   Licensing and attribution requirements.
-   Maintenance and long-term reliability.

Present a recommendation and wait for explicit approval before
implementing map functionality.

# 9. DATABASE, OFFLINE SUPPORT, AND FUTURE SYNC

-   Inspect the actual Room database before proposing schema changes.
-   Reuse verified existing structures where appropriate.
-   Add only the minimum necessary entities and migrations for approved
    features.
-   Never reset, drop, or overwrite existing business data.
-   Preserve historical records.
-   Support offline visit creation if the feature is approved.
-   Ensure duplicate visit prevention and safe local persistence.
-   Do not assume Firebase Authentication, Firestore, or another backend
    is configured.
-   Do not introduce cloud sync automatically.
-   Treat cloud synchronization as a separate future decision requiring
    architecture, privacy, cost, and conflict-resolution planning.

# 10. UI / UX REQUIREMENTS

-   Professional, clean, minimal, premium business-app design.
-   Preserve the existing app's visual identity and theme.
-   Mobile-first Jetpack Compose implementation.
-   Malayalam-friendly labels where appropriate; retain standard English
    technical and accounting terms where clearer.
-   Clear financial hierarchy and readable currency values.
-   Consistent spacing, typography, and components.
-   Accessible controls and appropriate content descriptions.
-   Proper loading, empty, success, and error states.
-   No decorative controls that do not work.
-   No fake charts or placeholder financial values.

# 11. IMPLEMENTATION GOVERNANCE

Follow this sequence:

**Phase 0 --- Read-only project inspection and architecture report.**

**Phase 1 --- Verify and prioritize existing data-integrity defects.**

**Phase 2 --- Prepare a safe Backup and Restore design and
implementation plan.**

**Phase 3 --- Prepare the Finance System correction and integration
plan.**

**Phase 4 --- Prepare the Advanced Financial Dashboard design and
plan.**

**Phase 5 --- Prepare Customer Visit Tracking, GPS privacy, and
data-model plan.**

**Phase 6 --- Evaluate map-provider options separately.**

**Phase 7 --- Implement only individually authorized phases.**

**Phase 8 --- Run relevant unit tests, Room database tests, migration
tests, and build verification.**

**Phase 9 --- Regression test existing workflows and perform a final
audit.**

# 12. AUTHORIZATION GATE

At the end of the initial inspection:

STOP.

Do not edit, delete, rename, move, or generate replacement project
files.

Provide:

1.  Existing architecture report.
2.  Verified existing feature inventory.
3.  Confirmed issues and potential issues.
4.  Finance data-flow map.
5.  Database and migration assessment.
6.  Proposed files to modify and why.
7.  Proposed new files and why.
8.  Dashboard feasibility and plan.
9.  Customer Visit Tracking feasibility and plan.
10. GPS privacy and accuracy proposal.
11. Map-provider comparison, without installing anything.
12. Test and regression strategy.
13. Phased implementation roadmap.
14. Risks, limitations, and unresolved decisions.

Wait for the project owner's explicit authorization before implementing
any phase.

Authorization for one phase does not authorize other phases.

# 13. FINAL REPORTING STANDARD

For every authorized implementation phase, report separately:

-   Completed.
-   Pending.
-   Failed.
-   Not tested.
-   Files modified.
-   Files created.
-   Database changes and migrations.
-   Tests actually executed and their real results.
-   Build result.
-   Regression results.
-   Remaining risks.

Never fabricate test results, build success, or feature completion.

# FINAL PRINCIPLE

**Inspect → Understand → Audit → Plan → Obtain Authorization → Implement
→ Test → Regression Audit.**

Improve the existing IceTrack application incrementally. Preserve its
architecture and business data. Build only what is approved. Never
rebuild unnecessarily.
