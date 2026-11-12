# Three-week roadmap after Stage 0

Planning baseline: Stage 0 was implemented and verified on November 12, 2026. It has dated reservations, transactional simulated keycards, test payments/refunds, hotels/reviews/costs, staff-only DTO APIs, local Caffeine caching and an integrated frontend. The executed baseline is 28 backend/migration tests, 5 frontend tests and 10 browser/SQL checkpoints.

**Stages 1–3 below are plans only. Begin each stage only after the user explicitly requests it in a future session.** Course weeks 12–14 use the dates below, all in 2026. No Stage 1 code has been implemented.

The remaining gaps come from the updated repository: legacy undated bookings and estimated costs need staff reconciliation; lists are unpaginated; queue readiness currently filters entities in Java; staff share one role/account; cache is local and direct SQL updates wait for TTL; pricing is a fixed demonstration policy; retry keys live only in the browser page; CI/load/restore/deployment evidence is absent. Real payments, physical RFID, passport verification and public production launch are outside this three-week commitment unless separately approved and scoped.

For every student, assignments below include future coding, tests, peer review and demonstration. The speaking notes are intended for 20–40 seconds at a clear pace. Use “I implemented” only for code that the student actually wrote. When reviewing or presenting AI-assisted work, say so and explain the behavior shown.

## Stage 1 / Week 12 — November 16–22

### Objective

Make the Stage 0 staff workflow dependable with representative legacy data and a repeatable team verification process.

### Features, fixes and likely files

- Add an explicit staff-only legacy reconciliation workflow. Capture real check-in/out dates and reviewed pricing; preview conflicts before writing, retain the original values in a history record, and reject unresolved overlaps. Do not silently guess dates or migrate historical refunds into fictional payments.
- Add pagination/filter parameters to room, booking, payment and refund lists; move priority readiness and occupied-room filtering into database queries. Retain existing simple requests with bounded defaults and update frontend consumers deliberately.
- Improve booking/financial form state, show the remaining refundable balance, preserve retry identifiers across an uncertain reload, and provide clear handling for 400/401/404/409 responses.
- Establish continuous PostgreSQL-backed build/test execution with no real secrets, plus a reproducible environment/setup guide.
- Likely modules: `booking/`, `cost/`, `payment/`, `refund/`, `room/`, `api/ApiDtos.java`, new additive Liquibase changesets starting at 005, `frontend/app.js`, `frontend/index.html`, frontend tests, `backend/src/test/`, `.github/workflows/`, `scripts/`, and docs. Never edit 001–004 after deployment.

### Dependencies and sequence

The user must authorize Stage 1. The team must supply the intended legacy stay dates and agree which records are synthetic benchmark data. Reconciliation needs a reviewed conflict/pricing policy before write endpoints are built. Add tests and CI first; develop paginated DTO contracts before adapting frontend tables. Keep unreconciled bookings readable and safe throughout.

Suggested order: November 16 baseline CI and policy review; November 17–19 reconciliation and API/query work; November 20 frontend integration; November 21–22 regression testing and demo rehearsal.

### Acceptance criteria

1. Staff can preview and confirm dates/costs for one legacy booking; conflicting dates return 409 and leave original data/history unchanged. Original financial records remain intact. Payment/check-in remain blocked while a booking is unreconciled.
2. Lists have documented bounded pages and deterministic ordering; frontend pages show the same records as PostgreSQL without loading the full dataset. Existing dated reservation/check-in behavior remains valid.
3. A page reload after an uncertain financial submission reuses its original key and does not create another payment/refund. Refund UI shows the server-derived remaining balance.
4. CI runs the Stage 0 suite plus new tests against a fresh PostgreSQL service and checks an upgrade fixture. No test is reported Passed without execution.

### Tests and demonstration

Add reconciliation preview/commit/rollback tests, legacy conflict preservation, reviewed-cost provenance, pagination boundaries/order, queue readiness after cleaning/checkout, reload/retry and refund-balance UI tests. Run the whole Stage 0 suite in CI. Demonstrate an undated legacy record, preview a conflict, reconcile with verified dates, then create a booking from a paginated room page and recover an interrupted refund request.

### Assignments and speaking notes

| Student | Planned work and responsibility | Speaking notes (20–40 seconds) |
|---|---|---|
| 24B031804 | Code/review durable payment retry handling and reviewed-cost payment eligibility; test reload and duplicate-success guards; demonstrate interrupted-payment recovery. | “I will show how a payment retry keeps the same request key after a reload. The server returns the saved result instead of adding another payment. A legacy booking also needs reviewed pricing before payment. I will explain the code I wrote or reviewed and show the PostgreSQL result. All payments remain simulations.” |
| 24B032049 | Code booking/refund pagination UI and server-derived refundable balance; test failure messages and retry recovery; review API contracts with 24B031814. | “The refund page will show the remaining paid balance from the server. I will choose a cancelled booking and request a valid amount. An excessive amount must show a clear error. I will also show how the table moves between pages. My presentation will identify the frontend code I changed and the integration tests I ran.” |
| 24B031814 | Code bounded list DTOs/query responses and review cache keys/eviction for pages; review data exposure; demonstrate consistent paginated results after updates. | “We will add small, ordered pages to the API. I will show that a room update appears in the next response and that sensitive guest fields stay out of the JSON. The cache must use the correct page and filter values. I will explain the parts I implemented or reviewed and show the test evidence.” |
| 24B031718 | Design/code additive reconciliation history schema and reviewed-cost provenance; prepare legacy fixtures and join queries; review preservation evidence. | “Old bookings do not contain complete stay dates. We will keep their original records and add a reviewed correction with history. I will show the old values, the confirmed dates and the related cost. A conflicting correction must be rejected. I will explain the migration I wrote or reviewed and the data-preservation checks.” |
| 24B031882 | Code reconciliation conflict preview/commit and database-side queue filtering; test locked updates with 24B031880; demonstrate safe transition into dated booking workflow. | “I will demonstrate the date review for an old booking. First, the system checks for another active stay in that room. If dates conflict, nothing is changed. With correct dates, staff can confirm the record and continue the normal workflow. I will show the reservation rules and identify the code I contributed or reviewed.” |
| 24B031880 | Set up CI PostgreSQL jobs and regression evidence; code upgrade/reconciliation/pagination/retry tests; review all acceptance criteria; lead recorded test walkthrough. | “I will show the automated checks running in a clean environment. They include the existing Stage 0 cases and the new legacy-data and pagination tests. We will compare expected and actual results and keep failure evidence. My role is to write tests, review the changes and explain which results were actually executed successfully.” |

## Stage 2 / Week 13 — November 23–29

### Objective

Add accountable staff operations and configurable business rules while keeping financial processing simulated.

### Features, fixes and likely files

- Replace the single shared staff account with individual staff identities and least-privilege roles for reception, housekeeping and finance. Choose a browser authentication/session approach and its CSRF protection together; keep environment-managed secrets and password hashing.
- Add append-only staff audit events for reconciliation, booking transitions, cleaning changes and financial actions. Never log passport/phone/card details or raw passwords. Add restricted audit views.
- Extract the fixed demonstration fee/currency policy into a versioned pricing configuration. Snapshot the policy version with costs so old quotes remain unchanged.
- Add finance-facing payment/refund filtering and totals for simulated operations; review data access by role.
- Likely modules: `config/AppConfig.java`, new staff/security/audit packages, `api/`, `cost/`, `payment/`, `refund/`, `booking/`, new Liquibase changesets, `application*.yaml`, frontend staff/finance views, backend/frontend tests and docs.

### Dependencies and sequence

Stage 2 requires a separate user request and an accepted Stage 1 baseline, especially reconciliation history and paginated APIs. Agree a role/action matrix and authentication/CSRF design before implementing the UI. Agree pricing-policy ownership and effective dates before creating new quotes. Audit storage depends on reliable staff identity; finance views depend on role checks.

Suggested order: November 23 design/role matrix; November 24–26 staff auth, audit and pricing code; November 27 integration; November 28–29 role/security/regression tests and demo.

### Acceptance criteria

1. Housekeeping can change cleaning state but cannot create refunds or read restricted guest/finance data; reception and finance have documented permitted actions. Denied actions return 403 without mutation.
2. Staff login/logout, expired authentication and CSRF behavior match the agreed design. No shared default production password or secret is committed.
3. Each audited mutation records actor, action, entity, timestamp and safe before/after values once per operation, including idempotent financial retries. Audit access is restricted.
4. New bookings use the configured pricing-policy version; changing policy does not change existing cost/payment snapshots.
5. Finance totals reconcile to stored successful payments and completed refunds, with no duplication from joins or retry records.

### Tests and demonstration

Add an endpoint/role permission matrix, credential/session/CSRF tests, unauthorized/no-mutation checks, audit integrity/redaction tests, pricing effective-date/snapshot tests and financial reconciliation tests. Continue all earlier migration and booking-concurrency tests. Demonstrate a housekeeping user changing cleaning state and being denied a refund; a finance user sees the allowed refund and audit event; a pricing change affects only a newly created quote.

### Assignments and speaking notes

| Student | Planned work and responsibility | Speaking notes (20–40 seconds) |
|---|---|---|
| 24B031804 | Code versioned pricing use in simulated payment processing and payment/audit reconciliation; test policy snapshots and retries; review finance totals. | “I will compare two booking quotes created under different pricing policies. The old payment amount must stay unchanged. I will also show that a retry does not add another payment or audit event. These checks help us explain the stored totals. I will identify my coding and review work, and the provider will still be simulated.” |
| 24B032049 | Code role-aware reception/finance frontend views, login/logout/error states and refund filters; test UI permission handling; review server enforcement with 24B031814. | “The interface will show actions that match the staff role. A finance user can open the refund view, while a housekeeping user cannot. I will demonstrate a denied request and its message. Hiding a button is not the security rule; the backend checks permission. I will explain the interface changes I wrote or reviewed.” |
| 24B031814 | Lead staff identity/role enforcement and agreed authentication/CSRF implementation; code safe staff/audit DTOs; review redaction and permission matrix. | “We will give staff separate accounts and roles. I will show that the backend rejects an action when the user lacks permission. I will also explain how login and request protection work together. The response DTOs still limit exposed fields. My presentation will separate the security code I wrote from AI-assisted code I reviewed.” |
| 24B031718 | Code additive staff/audit/pricing-version schema and indexes; review immutable history and FK retention; demonstrate audit/pricing joins without personal details. | “I will show how audit events link to a staff account and an operation. The event stores the action and time without copying sensitive guest details. Pricing versions are also stored so a booking keeps its original quote. I will explain the database changes I contributed or reviewed and show the migration and join tests.” |
| 24B031882 | Code actor-aware booking/cleaning transitions and safe audit hooks; review transition consistency with finance actions; demonstrate role-based reservation handling. | “I will demonstrate a booking transition with the staff member recorded. The normal reservation and cleaning rules must still apply. If an action is denied or fails, the booking must keep its previous state. I will show the related audit event and the tests, and explain which service changes I wrote or reviewed.” |
| 24B031880 | Code permission, CSRF, audit/redaction and pricing-regression tests; verify migration upgrades and finance totals; maintain CI/report evidence. | “My tests will check the same operation with different staff roles. Allowed requests must work, and denied requests must leave the database unchanged. I will also check that logs and audit responses do not expose sensitive fields. I will show executed results and explain the tests and reviews I contributed to the team.” |

## Stage 3 / Week 14 — November 30–December 6

### Objective

Produce a reproducible final course release with measured performance, recoverable demonstration data and a rehearsed end-to-end presentation.

### Features, fixes and likely files

- Profile the updated paginated queue/availability queries and cache on a synthetic, isolated benchmark dataset. Add indexes only when query plans and timings justify them. Define the demonstrated single-instance cache boundary; defer distributed caching unless measurements and deployment scope require it.
- Extend concurrency tests for check-in/keycard issuance, payment retries, simultaneous partial refunds, checkout/cleaning transitions and reconciled-date updates. Fix failures within the accepted features.
- Package reproducible local/staging startup, health/readiness checks and environment templates. Exercise a PostgreSQL backup/restore into a separate database and recheck integrity.
- Improve accessibility and keyboard/error-state behavior in the existing frontend, then rehearse the six-person scenario with seeded synthetic data and final test evidence.
- Likely modules: repository queries and indexes, cache configuration, regression/performance tests, `compose.yaml`, optional backend Dockerfile, environment examples, `scripts/`, frontend files, CI and final documentation. Additive migrations only if a measured fix needs one.

### Dependencies and sequence

Stage 3 requires another explicit user request and acceptance of Stage 2 permissions/audit/pricing behavior. Agree a benchmark size, machine specification and response-time targets before measuring. Use synthetic data and a separate restore target. Hosting/publication is a separate decision; prepare and verify a runnable release artifact before any deployment approval.

Suggested order: November 30 benchmark definition and baseline measurements; December 1–2 concurrency/query fixes; December 3 packaging and restore drill; December 4 accessibility/regression; December 5–6 final evidence review and team rehearsal.

### Acceptance criteria

1. A clean checkout can start PostgreSQL and the packaged app using documented steps and environment variables. Fresh and populated-upgrade paths both pass; no destructive volume reset is required.
2. Under the agreed load, overlapping booking races and duplicate-payment races produce one accepted operation; concurrent refunds never exceed the paid balance; keycard failures roll back check-in. Record request counts and observed responses rather than claiming unlimited concurrency safety.
3. Publish measured latency/query-plan/cache-hit evidence for the agreed dataset and machine. Performance claims must state load, sample size and limits; all agreed thresholds are either met or explicitly reported as unresolved.
4. A backup restores into an isolated database with expected row counts, FKs, booked dates, financial balances and review verification intact. No original data is overwritten.
5. The core browser scenario works by keyboard, errors are announced clearly, and desktop/mobile layouts remain usable. The final report distinguishes simulations, measured behavior and future work.
6. Each of six members can present one 20–40 second part and name their actual coding, testing or review contribution. The release is reviewable; no main-branch merge or public deployment occurs automatically.

### Tests and demonstration

Run all earlier suites plus measured concurrency/load tests, packaged-app smoke tests, backup/restore reconciliation, upgrade replay, keyboard/accessibility checks and a final browser regression. Demonstrate clean startup, booking/cost, payment/refund, cleaned-room check-in, checkout/review and safe audit history. Finish by showing the restored database and the measured test report, with any remaining limits stated.

### Assignments and speaking notes

| Student | Planned work and responsibility | Speaking notes (20–40 seconds) |
|---|---|---|
| 24B031804 | Code/fix simulated payment race and reconciliation issues found by load tests; verify restored payment totals; own payment part of final rehearsal. | “I will show the payment part of our final demonstration. Several identical requests must produce one successful payment. I will compare the saved totals before and after the database restore. The payment provider is still a simulation. I will describe the fixes or tests I contributed and show the measured results without claiming real banking integration.” |
| 24B032049 | Code/fix keyboard, loading and error behavior across booking/refund UI; test final browser flow at desktop/mobile widths; lead refund demonstration. | “I will complete a booking cancellation and test refund using the final interface. The page must show the real saved result and explain errors clearly. I will also show keyboard navigation and the mobile layout. I will describe the frontend changes and tests I contributed, including any AI-assisted work that I reviewed.” |
| 24B031814 | Profile cache/query effects and role-safe caching; code only measured improvements; review environment/security configuration and final response exposure. | “I will show the measured effect of caching and query changes. The report will state the dataset, computer and test conditions. After a room update, the next result must still be correct. I will also check that role restrictions and safe responses remain in place. I will explain the performance and review work I actually contributed.” |
| 24B031718 | Code backup/restore and migration-check scripts; review measured index proposals; verify restored joins/constraints and preserve benchmark/source databases. | “I will demonstrate restoring a backup into a separate database. We will compare record counts, foreign keys and financial balances with the source. The original database is not replaced. I will also show the migration history and any index supported by our measurements. I will identify the scripts and database checks I wrote or reviewed.” |
| 24B031882 | Code/fix booking/check-in/checkout races revealed by stress tests; review consistent lock order and keycard rollback; lead final reservation/check-in scenario. | “I will show the reservation and check-in flow under concurrent requests. Only one overlapping booking should succeed. A failed keycard operation must keep the booking unchanged. I will then complete a clean-room check-in and checkout. I will explain the service fixes or tests I contributed and the limits of the measured scenario.” |
| 24B031880 | Lead packaged-release smoke, load/concurrency, restore and accessibility verification; retain actual failures/results; coordinate six-person rehearsal and final report. | “I will present the final evidence from the packaged application. The report will include the environment, test cases and actual results. We will show concurrency checks, browser actions and a database restore. Any unresolved issue will stay visible. My contribution is to write and run tests, review the release and coordinate a clear team demonstration.” |

## Stage gates and stop rule

At each stage end, review changed files, executed test evidence, known issues and each member's real contribution. A later stage may be replanned if tests reveal new priorities. Stage 0 completion authorizes none of the planned code above. Stop here until the user explicitly requests Stage 1.
