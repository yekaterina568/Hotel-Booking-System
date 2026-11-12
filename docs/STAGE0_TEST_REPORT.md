# Stage 0 executed test report

Executed November 12, 2026 (Asia/Almaty). Final result: **28/28 backend and migration tests Passed, 5/5 frontend tests Passed, 10/10 asserted browser/SQL checkpoints Passed; none skipped.** The browser checkpoints are assertions within one end-to-end scenario, not ten separate JUnit cases.

Environment: macOS aarch64, Temurin Java 25.0.1, Maven wrapper 3.10.0, Spring Boot 4.1.2, Hibernate 7.4.13.Final, Docker 28.3.2, PostgreSQL 16.15 (`postgres:16-alpine`), Node 22 and Playwright 1.65.0 with local Google Chrome. Tests used only isolated `stage0_test` and `stage0_demo` databases in `hotel-stage0-verification`, plus unique test schemas. The existing working-project Compose volume was untouched.

## Commands and evidence

- `DEBUG=false ./backend/mvnw -f backend/pom.xml verify`: compilation, all 28 tests, packaged/repackaged JAR; BUILD SUCCESS. [Saved build summary](evidence/STAGE0_BUILD_RESULT.txt), [JUnit results parsed from Surefire XML](evidence/STAGE0_JUNIT_RESULTS.json). Original local XML is under `backend/target/surefire-reports/`.
- `npm test --prefix frontend`: five executed frontend tests, zero failures. [Saved TAP output](evidence/STAGE0_FRONTEND_TESTS.txt).
- `npm run test:e2e --prefix frontend`: real Chrome requests to `127.0.0.1:18080`; SQL assertions via Docker psql. [Saved browser results](evidence/STAGE0_BROWSER_RESULTS.json), [desktop screenshot](evidence/stage0-desktop.png), [mobile screenshot](evidence/stage0-mobile.png).
- `docker exec -i hotel-stage0-verification psql -U stage0 -d stage0_demo < backend/database/stage0-demo-queries.sql`: executed joins, migration checksums, constraints and indexes. [Saved SQL output](evidence/STAGE0_SQL_OUTPUT.txt).
- `git diff --check` passed; `git diff --exit-code bef47c2 -- backend/src/main/resources/db/changelog/changes/001-create-initial-tables.sql` confirmed original 001 unchanged.

## Backend and PostgreSQL cases

Each name below maps directly to an executed testcase in STAGE0_JUNIT_RESULTS.json. API cases use a real random-port application and PostgreSQL, not mocked repositories or H2.

| # | Case | Expected result | Actual result | Status / evidence |
|---|---|---|---|---|
| 1 | Create/retrieve booking and persist cost | 201/200; 2 nights, total 31,000 KZT; one cost row | Booking retrieved as CONFIRMED; snapshot assertions succeeded | Passed — `bookingCreatesDatedCostSnapshotAndCanBeRetrieved` |
| 2 | Cancel booking | Cancellation succeeds, repeat 409, dates reusable | Cancel and rebooking succeeded; duplicate cancellation returned 409 | Passed — `cancellationReleasesDatesAndCannotRepeat` |
| 3 | Overlapping versus adjacent dates | Overlap 409; adjacent [) stay 201 | Both status assertions matched | Passed — `overlapIsRejectedButAdjacentStayIsAllowed` |
| 4 | Two simultaneous booking requests | One 201, one 409; one database booking | Sorted results [201,409]; SQL count 1 | Passed — `concurrentBookingRequestsHaveOneWinner` |
| 5 | Date-specific availability | Overlapping dates false; later dates true | Both boolean assertions matched | Passed — `availabilityUsesRequestedDates` |
| 6 | Valid check-in and duplicate issuance | Check-in 200; repeated check-in 409; one active keycard | All responses matched; SQL active-card count 1 | Passed — `cleanCheckInIssuesOneKeycardAndCannotRepeat` |
| 7 | Dirty room guard | 409 and no keycard | 409; SQL card count 0 | Passed — `dirtyRoomCannotCheckIn` |
| 8 | Cleaning room guard and cleaning update | 409 while Cleaning; 200 after PATCH Cleaned | Both check-in results and cleaning PATCH matched | Passed — `cleaningRoomCannotCheckInUntilCleaned` |
| 9 | Booking-state and stay-date guards | Cancelled and future check-in both 409 | Both requests returned 409 | Passed — `cancelledOrFutureBookingCannotCheckIn` |
| 10 | Real priority queue integration | Dirty booking absent, Cleaned present, checked-in absent | All three queue membership assertions succeeded | Passed — `priorityQueueReflectsCleaningChangesAndCheckIn` |
| 11 | Successful test payment and duplicate protection | Same key returns same payment; new success key 409 | Payment IDs matched; one SQL row; duplicate success 409 | Passed — `paymentPersistsAndRetryIsIdempotent` |
| 12 | Decline then successful test payment | DECLINED and SUCCEEDED records retained | Both states matched; SQL count 2 | Passed — `declinedPaymentIsStoredAndCanRetryWithNewKey` |
| 13 | Invalid financial actions | Changed key payload and new payment after cancel return 409 | Both requests returned 409 | Passed — `cancelledBookingCannotBePaidAndKeyCannotChangePayload` |
| 14 | Refund creation/retrieval and persistence | Same key returns same completed refund; payment FK present | IDs matched; one linked SQL refund; GET succeeded | Passed — `refundPersistsAndRetryDoesNotDuplicate` |
| 15 | Refund validation and paid balance | Unpaid/active 409; negative 400; balance cannot be exceeded | Invalid requests rejected; 30,000 + 1,000 accepted; excess 0.01 rejected | Passed — `refundRejectsUnpaidActiveNegativeAndOverBalance` |
| 16 | Uniqueness and FK constraints | Same hotel/room 409; different hotels allowed; bad FK fails; referenced guest cannot delete | API status assertions matched; direct SQL FK insert raised expected exception | Passed — `hotelRoomUniquenessAndGuestForeignKeyAreEnforced` |
| 17 | PostgreSQL overlap constraint independently of service | Direct dated overlap insert raises integrity exception | PostgreSQL rejected the direct INSERT | Passed — `databaseRejectsDirectOverlappingDatedInsert` |
| 18 | Caffeine hit and mutation invalidation | Hit count increases; changed/new/deleted room results match DB operations | All hit, cleaning, create and delete assertions succeeded | Passed — `roomCacheHitsAndInvalidatesAfterUpdateCreateAndDelete` |
| 19 | Response data exposure | No passportNum, phoneNum, cardUid or idempotencyKey in six API response lists | All response field-absence assertions succeeded | Passed — `dtoResponsesOmitSensitiveAndInternalFields` |
| 20 | Staff access control and frontend serving | Protected endpoints 401; HTML 200 includes payment/refund forms | Four unauthenticated APIs denied; static HTML assertions succeeded | Passed — `unauthenticatedApiDeniedAndStaticFrontendAvailable` |
| 21 | Input validation and HTTP errors | Bad guest/dates/enum/path 400; missing booking 404 | All expected client status codes matched | Passed — `invalidFieldsAndMissingRecordsGiveClientErrors` |
| 22 | Archive repair and checkout lifecycle | Confirmed cannot archive; checked-in snapshot retains IDs/state; card revoked, room Dirty | Response snapshot matched; SQL active cards 0; room Dirty; repeat 409 | Passed — `archiveCopiesFieldsRevokesKeyAndMarksRoomDirty` |
| 23 | Verified reviews and rating/uniqueness constraints | Active review 409; rating 6 rejected; completed stay review saved once; direct invalid rating rejected | All API status assertions and direct SQL integrity assertion succeeded | Passed — `reviewRequiresVerifiedStayAndRatingIsValidated` |
| 24 | Transactional check-in under injected database failure | 500 from injected card trigger; booking remains Confirmed; zero cards | Expected failure observed; SQL state Confirmed and card count 0 | Passed — `keycardFailureRollsBackWholeCheckIn` |
| 25 | Persisted pricing snapshot | After room-rate update old booking total stays 31,000 KZT | Cost total assertion remained 31,000 | Passed — `pricingSnapshotSurvivesLaterRoomPriceChange` |
| 26 | Cache/transaction consistency | Rolled-back cleaning change leaves cached and database room Cleaned | Cache result CLEANED and SQL value Cleaned matched | Passed — `cacheEvictionWaitsForCommitAndRollbackKeepsData` |
| 27 | Upgrade a populated 001 database | Preserve 1,001 original bookings, rooms/refunds/cards/archive, 001 checksum; add estimated costs; no dev seed; rerun safe | Original-value comparisons matched; 1,001 legacy costs/unknown dates; migration rerun succeeded | Passed — `populated001UpgradePreservesLegacyRowsAndBenchmarks` |
| 28 | Fresh migrations and opt-in dev seed | Fresh test context has no demo records; dev applies seed once | Initial empty guests; dev created two rooms/one guest; rerun unchanged | Passed — `freshDatabaseMigrationsAndDevelopmentSeedAreOptIn` |

## Frontend automated cases

| Case | Expected result | Actual result | Status / evidence |
|---|---|---|---|
| API contract | POST refund to `/api/refunds` with expected JSON/key | Exact URL, method, key and returned ID assertions matched | Passed — TAP case 1 |
| Backend conflict | Reject 409; expose real cleaning error | Promise rejected with server detail; no success fallback | Passed — TAP case 2 |
| Offline/non-JSON backend | Display/reject request failure | Network error and HTTP 503 both rejected | Passed — TAP case 3 |
| Retry state | Same form payload reuses key; changed payload gets new key | All key comparisons matched | Passed — TAP case 4 |
| Hotel-local arrival | Convert hotel time independently of browser zone | Almaty 14:00 became 09:00Z; UTC 14:00 stayed 14:00Z | Passed — TAP case 5 |

## Actual browser and SQL checkpoints

| Checkpoint | Expected result | Actual result | Status / evidence |
|---|---|---|---|
| Browser guest registration | one PostgreSQL guest; response lists omit passport/phone | one PostgreSQL guest; response lists omit passport/phone | Passed — browser result JSON / SQL assertions |
| Browser booking and cost join | two nights; persisted total 31000.00 KZT | two nights; persisted total 31000.00 KZT | Passed — browser result JSON / SQL assertions |
| Browser declined payment | decline is displayed as an error and stored as DECLINED | decline is displayed as an error and stored as DECLINED | Passed — browser result JSON / SQL assertions |
| Browser payment retry | one successful PostgreSQL payment after two identical submissions | one successful PostgreSQL payment after two identical submissions | Passed — browser result JSON / SQL assertions |
| Browser backend failure | visible error; zero phantom refund records | visible error; zero phantom refund records | Passed — browser result JSON / SQL assertions |
| Browser refund retry and persistence | one PostgreSQL refund for 10000.00 KZT | one PostgreSQL refund for 10000.00 KZT | Passed — browser result JSON / SQL assertions |
| Browser cleaning, queue and check-in | Dirty excluded from queue; Cleaned check-in creates one active keycard | Dirty excluded from queue; Cleaned check-in creates one active keycard | Passed — browser result JSON / SQL assertions |
| Browser checkout/archive | archive persisted; room Dirty; keycard inactive | archive persisted; room Dirty; keycard inactive | Passed — browser result JSON / SQL assertions |
| Browser verified review and safe text rendering | rating 5 persisted; HTML rendered as text | rating 5 persisted; HTML rendered as text | Passed — browser result JSON / SQL assertions |
| Browser layout and JavaScript | no document overflow at 390px; zero uncaught JavaScript errors | no document overflow at 390px; zero uncaught JavaScript errors | Passed — browser result JSON / SQL assertions |

The saved demonstration run created booking 1 (cancelled), successful payment 2 for 31,000 KZT, refund 1 for 10,000 KZT, booking 2 (checked in then archived), and verified review 1 with rating 5. IDs are local evidence, not stable seed identifiers. The SQL output shows the booking/cost/hotel and payment/refund/review joins, 34 listed constraints and 23 relevant indexes. The desktop screenshot was visually inspected; mobile overflow and zero uncaught browser errors were asserted automatically.

## Migration verification

The fresh application database was empty before dev startup. Liquibase applied 001, 002-stage0, 003-stage0-guards and the explicit 004-dev-seed. Separate tests also applied the non-dev fresh path, enabled the seed explicitly, then reran it without duplicates.

The populated upgrade fixture first ran 001 alone, then inserted 1,001 bookings (including 1,000 deliberately overlapping undated active rows), a guest/room, an old refund, a keycard and an archive record. Original booking/room/refund values and the original 001 checksum matched after upgrade. All 1,001 cost rows were marked legacy estimates. Dev seed was absent in the test context. Migration replay applied no extra changes. Unique fixture schema names are recorded in the build summary and remain in the isolated test database for inspection.

## Failures found and limits

Initial live authentication failed because a BCrypt hash lacked its encoder prefix; it was fixed before the passing suites. Inspection also found that a TEST reference plus a full UUID would exceed its column width; the generated reference now fits. Docker was initially stopped and was started successfully. These development failures are not reported as passing attempts.

The keycard rollback case deliberately creates a database trigger that raises an exception. Its expected 500/logged stack trace is part of the test; success means the state/card SQL rollback assertions passed. The browser 503 is deliberately injected through Playwright routing; no real backend outage is claimed.

No final verification test was blocked or skipped. The user's actual working database/private benchmark data was not migrated; upgrade evidence comes from the populated isolated fixture. Only the local PostgreSQL 16 single-instance setup and Chrome were exercised. This is basic access/data-exposure testing, not a penetration test or a production load certification. Real payment providers, physical keycard hardware, distributed cache behavior and Stages 1–3 were not tested or implemented.
