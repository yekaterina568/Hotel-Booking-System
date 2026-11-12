# Stage 0 implementation report

Completed and verified on November 12, 2026, on `codex/stage0-team-commitments`. Stage 1 has not started. All Stage 0 code in this session is AI-assisted work; the student IDs below identify the commitment areas and suggested review/demo owners, not claims of personal authorship.

## Commitments delivered

| Student ID | Completed scope | Executed evidence |
|---|---|---|
| 24B031804 | Simulated success/decline payment API and UI; payment/booking FK; server-calculated amount; idempotent retries; at most one successful payment per booking | Backend payment tests; browser payment retries; SQL records |
| 24B032049 | Real frontend requests for rooms, queue, check-in, reservations and refunds; persisted refund records; cancellation/paid-balance checks; visible backend failures; removed all mock successes | Refund API tests; browser refund/503/cleaning/queue scenarios |
| 24B031814 | Caffeine manager and cached DTO room list; transaction-aware create/update/delete/archive invalidation; validated request/response DTOs; staff authentication; minimized guest/keycard output | Cache hit/update/create/delete/rollback tests; unauthenticated 401 and DTO exposure tests |
| 24B031718 | Additive hotel/review/cost schema and JPA/API support; room uniqueness per hotel; dated bookings; snapshot cost components; review verification/rating constraints; dev-only seed; joins and indexes | Fresh DB and populated-001 upgrade tests; browser cost/review joins; constraint/index SQL |
| 24B031882 | Create/get/cancel and dated availability; concurrent conflict prevention; cleaning/date/state check-in guards; priority queue; transactional keycard issuance; populated archive snapshots and checkout lifecycle | Booking race, direct SQL overlap, check-in failure rollback, cleaning, queue and archive tests |
| 24B031880 | 28 PostgreSQL/backend/migration automated cases, 5 frontend tests, and 10 asserted browser/SQL checkpoints; saved results and demonstration instructions | STAGE0_TEST_REPORT.md and docs/evidence/ |

## Verified working behavior

- Spring Boot 4.1.2 / Hibernate 7.4.13.Final compiles and packages using the existing Java 25 target and Maven wrapper 3.10.0. Runtime verification used Temurin 25.0.1 and PostgreSQL 16.15.
- Each new booking requires guest/room IDs, an arrival timestamp and 1–365 stay nights. Expected arrival must fall on the check-in date in the hotel's timezone. Date ranges use `[check-in, check-out)`, allowing adjacent stays.
- API booking creation locks the room before checking all active bookings. PostgreSQL's GiST exclusion constraint independently rejects overlapping new dated Confirmed/CheckedIn bookings. The two-request race produced one 201 and one 409.
- State mutations acquire room, then booking locks and refresh state after waiting. Check-in requires a Confirmed booking, Cleaned room and a stay active today. It rejects an unclosed checked-in guest in the same room. The priority queue uses these readiness conditions, sorted by expected arrival then booking ID.
- Check-in creates a simulated RFID keycard record in the same transaction. Injecting a database error during keycard insertion returned 500 and left the booking Confirmed with zero cards. No physical RFID device is connected.
- Archiving copies the original booking ID, guest, room, arrival, dates, creation timestamp and previous state. It accepts Cancelled or CheckedIn bookings, then marks the booking Archived. For a checked-in stay this is an explicit checkout, including early checkout: the room becomes Dirty and keycards are revoked. Bookings remain in place for payment/review FKs.
- Each cost snapshot stores nights, booked nightly rate, subtotal, a demonstration service fee of 1,000 KZT, total and currency. Price changes do not alter existing snapshots. This fee is a course-demo policy, not a tax calculation.
- Payments use the persisted total, store SUCCEEDED or DECLINED and a TEST provider reference, accept no card details and make no external provider call. An identical key/payload replays the stored record. A changed payload with the same key returns 409. A unique partial index allows at most one successful payment per booking.
- Refunds require a cancelled booking (also after its cancellation has been archived), a matching successful test payment and an amount within the remaining paid balance. New simulated refunds complete immediately; failed/pending/completed historical rows remain readable. Booking locks serialize financial mutations. Exact retries return the existing refund. Financial history cannot be deleted through the API.
- A review requires an actually checked-in, then archived stay. Both service checks and a database trigger enforce verification; rating is 1–5 and one review is allowed per booking.
- Caffeine caches immutable room DTO lists, with a five-minute TTL and bounded storage. Eviction is deferred until transaction commit. Price/cleaning changes and checkout invalidate the list. Availability and booking-state checks always read PostgreSQL, never this cache.
- The frontend is served from the backend and uses same-origin `/api/...` routes. It displays persisted records and server errors, retains a retry key for the same payment/refund submission within the page, renders user text via textContent, and does not claim passport-format checking verifies identity.

## Database migration and preservation decisions

`001-create-initial-tables.sql` is byte-for-byte unchanged. New changesets:

1. `002-stage0-schema.sql`: default Legacy Hotel and room FK, composite hotel/room-number uniqueness, nullable legacy stay dates, dated-booking exclusion constraint, meaningful cost snapshots, payments/refund links, reviews and indexes.
2. `003-stage0-guards.sql`: new bookings must have dates; new reviews require verified completed stays; new refunds require a matching successful payment.
3. `004-dev-seed.sql`: explicit `@dev` Liquibase context, enabled by the `dev` Spring profile. Adds one demonstration hotel, one synthetic guest and two rooms; it does not seed operational bookings or financial results.

Existing room IDs, booking IDs, original booking columns, enum labels, guests, refunds, cards and archive rows are preserved. Existing rooms are assigned to Legacy Hotel. Each legacy booking receives an explicitly estimated one-night cost with zero fee; it is not an approved quote and cannot be paid through the new flow.

Unknown legacy dates remain NULL rather than inventing historical stays. Availability considers an undated active booking as one provisional night starting from expected arrival in the hotel's timezone. The exclusion constraint covers dated active bookings; historical overlaps are retained. Legacy bookings can be retrieved, cancelled and archived when their state permits, but cannot check in or be paid until dates/pricing are reconciled. No date-reconciliation feature was implemented in Stage 0. New API writes cannot omit dates.

The upgrade test applied 001, inserted 1,001 bookings including 1,000 deliberately overlapping benchmark-like rows and existing financial/keycard/archive data, then applied 002–003. It compared original values and the 001 checksum, verified new hotel/cost links, and reran Liquibase. This verifies a populated 001 upgrade fixture. The user's working database and private benchmark dataset were not opened or migrated. Very large deployments and other PostgreSQL versions were not load-tested.

The preserved `backend/database/seed.sql` is an old, manual 001-era fixture from the pre-existing work. Do not run it after Stage 0 migrations: its undated inserts and unlinked refunds intentionally violate the new-write guards. Use the `dev` profile instead.

## Security and compatibility

The existing route prefixes remain: `/api/guests`, `/api/rooms`, `/api/bookings`, `/api/keycards`, `/api/refunds`, `/api/archive`. New routes are `/api/hotels`, `/api/payments`, `/api/reviews`, `/api/bookings/{id}/cost`, `/api/bookings/availability`, `/api/bookings/priority-queue`, and PATCH `/api/rooms/{id}`. JSON now uses explicit IDs and validated DTOs instead of binding nested persistence entities. There were no working frontend API callers to preserve; clients that sent raw entities must adapt.

APIs require the STAFF role using stateless HTTP Basic credentials configured through the environment. Static assets and health are public. The server binds to loopback by default. Normal startup requires `HOTEL_STAFF_PASSWORD`; the dev profile alone has a local demonstration default. Browser requests attach the header explicitly; there is no session/cookie login and cross-origin access is not enabled. CSRF is disabled for this stateless local demo. A future deployed browser-login design must revisit authentication and CSRF together.

DTOs omit passports, phone numbers, card UIDs and idempotency keys from responses. DTOs are not authorization: Spring Security provides the current staff access control. There is one staff account and one role; individual staff identities, permissions, audit logging, TLS deployment and identity verification remain roadmap work. No real secrets are committed.

Native PostgreSQL enums retain their existing title-case values. Java enum names stay uppercase for JSON/API compatibility; JPA `@EnumeratedValue` maps their database labels and `NAMED_ENUM` uses explicit existing SQL type names. All repository identifiers consistently use Long.

## Verification and environment

`./scripts/start-stage0-postgres.sh` created only a dedicated `hotel-stage0-verification` container on loopback port 55433 with tmpfs storage. It did not start or reset the project's existing Compose volume. Fresh application startup applied all dev changesets to the empty `stage0_demo` database. Tests used `stage0_test` and unique migration-test schemas; tests guard the database name before Liquibase startup. Test records and schemas are intentionally retained for inspection.

See the test report for executed results. The two initial runtime defects found during development—password encoder prefix and a too-long TEST provider reference—were corrected before final verification. The initial Docker daemon blocker was resolved by starting Docker Desktop. No remaining environment blocker prevented the required Stage 0 checks.

The demonstration backend was left running at `http://127.0.0.1:18080` for inspection during this session. Its database is temporary: stopping the tmpfs container discards its contents, and the startup script can recreate the named demo database on its next start. No working-project volume is involved.

## Remaining issues and Stage 1 readiness

Stage 0 is ready for the assessment demonstration. Remaining work is concrete: reconcile legacy dates/pricing; add list pagination and database-side queue filtering; give staff separate roles/identities; externalize business pricing policies; add CI, more concurrency/load coverage, backup/restore evidence and deployment configuration. Local Caffeine cache is single-instance; direct SQL edits are reflected after its TTL or application restart. Browser retry keys do not survive page reload. Payments and RFID are deliberately simulations.

Stages 1–3 are documented in PROJECT_ROADMAP_3_WEEKS.md and require a separate explicit request before implementation. No remote push, Pull Request or merge was performed.

## Technical references consulted

- [Hibernate enum and named-enum mapping](https://docs.hibernate.org/orm/7.3/introduction/html_single/#enums): informed the mapping choice; actual behavior was verified against the project's resolved Hibernate version.
- [PostgreSQL range and exclusion constraints](https://www.postgresql.org/docs/15/rangetypes.html#RANGETYPES-CONSTRAINT): informed `[)` stay ranges and the GiST overlap guard; actual execution used PostgreSQL 16.15.

## Local commits and changed files

- `f73b613`: preserve the pre-existing local startup/ID/README/seed changes as a baseline snapshot.
- `ad5d332`: additive Stage 0 schema, guards, dev-only seed and demonstration SQL.
- `57e4679`: transactional APIs, DTOs/security/cache, PostgreSQL tests and isolated DB script.
- `c045744`: real frontend operations, retry handling, and frontend/browser tests.
- A final documentation commit contains the five requested reports, setup documentation and saved evidence. The final HEAD is reported in the session response; no PR URL exists because no remote push or PR was made.

The full changed-file list, including the preserved baseline work, is in [STAGE0_CHANGED_FILES.txt](evidence/STAGE0_CHANGED_FILES.txt). The main changed modules are booking/archive/keycards, room/guest/refund, new hotel/cost/payment/review packages, API/configuration, Liquibase/test resources, frontend HTML/JS/CSS/tests and setup/docs. The original migration 001 is excluded because it is unchanged.
