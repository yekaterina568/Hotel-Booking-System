# Week 13 test report

## Environment and results

Verification ran on 26 November 2026 in Asia/Almaty using Temurin Java 26, Spring Boot 4.1.2, a dedicated PostgreSQL 14.18 cluster, Node 22, and Playwright in Chromium. The Compose file targets PostgreSQL 17. Docker Desktop was unavailable during verification, so the database and static frontend ran locally.

The baseline `20ecfecaf435a0eb4a20d66064dd034ca07646e9` ran 64 tests with 10 errors on Java 26. All 10 were in the existing `AdminAccessTest` web slice, which failed to configure `HttpSecurity`. An initial attempt with the machine’s Java 25 could not compile the Java 26 project. The final run used Java 26 and passed all **73 tests**, including nine PostgreSQL end-to-end tests. The browser script passed all **seven check groups** with zero uncaught JavaScript errors.

## Automated tests

| Case | Expected result | Actual result | Status |
| --- | --- | --- | --- |
| Empty database | KPI counts are zero, average rating is missing, report returns no rows | Counts, missing rating, and empty lists matched the assertions | Pass |
| Dashboard on seeded data | Booking and refund counts match the database, archived bookings count once, rating includes published reviews | All totals and status groups matched the fixtures | Pass |
| Hotel and date filtering | Time range includes both Almaty date boundaries and excludes other hotels. Inventory counts use only the hotel filter | Filtered counts, dated refunds, room status, trend buckets, and rating matched | Pass |
| Invalid request parameters | Inverted, malformed and incomplete dates and incompatible report filters return 400 | All six tested invalid requests returned 400 | Pass |
| Report preview, CSV and print | Shared filters yield the same records. CSV keeps UTF‑8 and treats formula-like values as text. Printable values are HTML-escaped | Matching booking ID across formats, spreadsheet-safe Cyrillic text, and no guest document or phone data. All five report types returned expected results | Pass |
| Pagination | Preview returns 50 rows per page, booking search returns 25, and CSV returns every matching row | Page two returned 11 of 61 report rows and 25 of 61 booking rows. CSV contained all 61 records | Pass |
| Authorization and CORS | Missing credentials return 401, a signed-in clerk receives 403 for admin routes and legacy room writes, and an unlisted origin is rejected | Read, write, download, and preflight cases returned the expected HTTP statuses | Pass |
| Room management | Selected property, number, type, cleaning status, and price persist. The same number is allowed across hotels. An in-use room cannot move or be deleted | PostgreSQL confirmed the changes. Duplicate number and unsafe actions returned 409. An unused room was deleted | Pass |
| Concurrent room creation | Two requests for the same hotel and number create one room | One 201 response, one 409, and one matching row | Pass |
| Booking and checkout regression | Only eligible transitions succeed. Archive and history persist. Checkout marks the room Dirty and deactivates the keycard | API and PostgreSQL matched all expected states and events | Pass |
| Liquibase upgrade | Apply changesets 001–005 first, add legacy records, then apply the Week 13 changes | The original six changeset entries and legacy records survived. Two new changesets applied, and the history table began empty | Pass |

## Browser checks

| Case | Expected result | Actual result | Status |
| --- | --- | --- | --- |
| Sign-in and dashboard | Wrong password rejected, valid session opens Dashboard, six chart types rendered | Rejection shown and all six Chart.js canvases contained real series | Pass |
| Filters and error recovery | Hotel and preset filters change activity, empty range shows zero, invalid range and network errors are explained | Observed all expected states. Refresh recovered after the simulated network failure | Pass |
| Reports | Preview, CSV download, and printable HTML use the same filters | Verified Riverside cancelled bookings across all three formats | Pass |
| Rooms | Create, edit, reassign, reprice, and reload | All edits persisted. The SQL evidence shows the final property, type, cleaning status, and price | Pass |
| Front-desk and booking management | Submit a front-desk reservation, then cancel and archive it in administration | Confirmations, archive list, and both state changes matched the database | Pass |
| Existing hotel and review controls | Add/delete a hotel and moderate a review through Pending, Published, and Hidden | All requested controls returned successful results | Pass |
| Mobile and sign-out | One-column charts, dashboard and report preview fit 390px, logout hides admin navigation | All assertions passed with zero uncaught JavaScript errors | Pass |

## Commands and evidence

The database variables below refer to a dedicated verification database. The integration tests create a fresh schema inside the configured database for the upgrade and write tests.

```sh
export SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:55413/week13_demo
export WEEK13_TEST_JDBC_URL="$SPRING_DATASOURCE_URL"
# Set POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD, HOTEL_AUTH_USERNAME and HOTEL_AUTH_PASSWORD for the test environment.
(cd backend && ./mvnw clean test)

for script in frontend/*.js frontend/tests/*.cjs; do node --check "$script" || exit; done
WEEK13_CHROME_CHANNEL=chrome node frontend/tests/week13-browser.cjs
```

The environment already had Playwright 1.65.0. To install it for a new checkout, run `npm install --prefix frontend --no-save --package-lock=false playwright@1.65.0` and `frontend/node_modules/.bin/playwright install chromium`. Omit the channel variable to use Playwright’s bundled browser.

The recorded browser run used `WEEK13_CHROME_CHANNEL=chrome` to select the available local Chrome executable. The frontend was served by Python's threaded HTTP server with an increased request queue after intermittent connection resets from its default queue. The final browser run used the feature backend and the development seed. The database snapshot was collected directly with `psql`.

- [Baseline and final Maven summaries](evidence/backend-tests.txt)
- [Playwright output](evidence/browser-tests.txt)
- [PostgreSQL query output](evidence/postgresql.txt)
- [Dashboard](evidence/dashboard.png), [Statistics](evidence/statistics.png), [Reports](evidence/reports.png), [Room management](evidence/room-management.png), [Mobile dashboard](evidence/dashboard-mobile.png)
