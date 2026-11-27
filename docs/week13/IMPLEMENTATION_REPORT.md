# Week 13 implementation report

## Scope and baseline

The assignment called for a database-backed admin dashboard, visual statistics, filtered reports and exports, room management, and booking administration. It also required preserving hotel and review workflows, using additive migrations, and verifying the results against PostgreSQL and a browser.

The baseline is `20ecfecaf435a0eb4a20d66064dd034ca07646e9` from `origin/main`. After the merge of PR #3 (`47efc2b`), the team added HTTP Basic sign-in with Spring Security, reservation availability and stay dates, checkout and keycard deactivation, and the Week 12 tests from PR #4. The payment implementation was still on separate feature branches at the verified baseline. [Administration PR #3](https://github.com/yekaterina568/Hotel-Booking-System/pull/3), [Week 12 PR #4](https://github.com/yekaterina568/Hotel-Booking-System/pull/4)

## Dashboard

| Scope | Indicators |
| --- | --- |
| Current inventory, scoped by hotel | Total hotels, rooms, and guests. At one hotel, guests are distinct people with a booking at that property. For all hotels, the count includes all registered guests. |
| Activity, scoped by hotel and date | Bookings in total and by current state, total refunds, and average published review rating. The booking states are confirmed, checked in, checked out, cancelled, and archived. |
| Charts | Bookings over time, booking status distribution, room cleaning status, bookings per hotel, published review ratings, and refunds over time. |

Bookings and reviews use creation dates. Refunds use the processed date. Refunds without a processed date appear in the all-time total and the all-time Refund report, and the dashboard shows how many have no date. Archived bookings are counted once from the booking table, where they remain after archiving. Review analytics use published ratings only. The seven- and thirty-day filters include today. PostgreSQL filters and groups timestamps using the configured reporting zone, which defaults to `Asia/Almaty`. The custom end date is inclusive.

The Spring JDBC repository runs aggregate SQL over PostgreSQL. The service assembles one consistent dashboard snapshot in a read-only repeatable-read transaction. Chart.js renders the aggregated results, and every populated chart has a data table that also works when the CDN is unavailable.

## Reports

| Report | Filter options | Report content |
| --- | --- | --- |
| Booking | Hotel, creation dates, booking status | IDs, property, room number, status, stay dates, creation date |
| Room inventory | Hotel, cleaning status | IDs, property, number, type, cleaning status, nightly price |
| Hotel performance | Hotel, creation dates, booking status | Current room count, matching bookings and distinct guests, matching published reviews and average rating |
| Refund | Hotel, processing dates, refund status | Refund and booking IDs, property, amount, processing date, status, and a completed-refund total |
| Guest review | Hotel, creation dates, moderation status | Review ID, property, rating, status, creation date |

The booking status filter in Hotel performance applies to its booking and guest figures. The room count reflects current inventory. Review figures follow the selected hotel and dates. The preview is paged at 50 rows. Preview and printable HTML display the title, generation timestamp, applied filters, and summaries. The CSV has a header row and every matching record. CSV and printable HTML use the same parameterized query as the preview. CSV uses a UTF‑8 BOM, CRLF lines, escaped quotes, and text prefixes for formula-like values. Reports omit guest identity documents, phone numbers, names, review text, and refund reasons.

## Administration and API

All routes below use the existing `/api/admin` prefix and require the `ADMIN` role.

| Method and route | Purpose |
| --- | --- |
| GET `/dashboard` | Dashboard summary and chart series. Accepts `period`, `hotelId`, `from`, and `to`. |
| GET `/reports` | Preview and summaries. Accepts `type`, `hotelId`, `from`, `to`, `status`, and `page`. |
| GET `/reports/export` | CSV for the selected report filters. |
| GET `/reports/print` | Full printable HTML for the selected report filters. Use the browser to save a PDF. |
| GET, POST `/rooms` | Read existing inventory and create a room assigned to a selected property. |
| PUT, DELETE `/rooms/{id}` | Edit room number, hotel, type, cleaning status, and price. Delete a room with no booking references. |
| GET `/bookings/search` | Server-paged booking search by booking ID, guest ID, room number, or hotel name. Also accepts hotel and status filters. |
| GET `/bookings/{id}` | Booking details, known timestamps, and recorded status changes. |
| POST `/bookings/{id}/cancel`, `/bookings/{id}/archive` | Delegate to the existing booking services. |

Cancellation requires a confirmed booking. Archiving requires a cancelled or checked-out booking. The service locks each booking while changing its state. Review moderation retains Pending, Published, and Hidden. The admin UI confirms permanent deletion and booking actions and refreshes affected lists on success.

The existing account is assigned `ADMIN`, preserving the shared front-desk and admin sign-in. Admin routes and legacy room writes enforce the role server-side. Other front-desk routes still use the existing authenticated-user model. Allowed browser origins use `HOTEL_FRONTEND_ORIGINS`, which defaults to the frontend on port 3000 at `localhost` and `127.0.0.1`. Credentials are supplied through environment variables. The existing browser sign-in stores credentials in session storage. The project still uses a shared account by default and has no operator-level attribution or account administration. Serve the application over HTTPS when deployed beyond local development.

## Database changes

`week13-admin.sql` adds two changesets. The first replaces global room-number uniqueness with a unique constraint on `(hotel_id, room_number)`. The second creates `booking_status_history` and a trigger that records future state changes inside the same transaction as the booking update. The previous changesets and the default property for the legacy room creation route are preserved. A room with booking history stays assigned to its original property so earlier reports preserve their hotel attribution. Database foreign keys protect rooms referenced by current or archived bookings. The reporting queries reuse the existing schema and indexes. The new unique constraint and history primary key enforce the new data rules.

## Changed files

- `backend/src/main/java/com/hotelbooking/admin/` adds dashboard aggregation, report preview and exports, room mutations, booking details, and booking search.
- `backend/src/main/java/com/hotelbooking/{booking,room,config}/` updates booking transitions, room persistence, and administrative access controls.
- `backend/src/main/resources/` configures reporting and CORS and registers the additive migration.
- `frontend/admin.html`, `admin.css`, `admin.js`, `admin-insights.js`, and `admin-management.js` extend the existing sidebar and screens. `frontend/app.js` displays a hotel ID in the reservation room picker to distinguish repeated room numbers.
- `backend/src/test/`, `frontend/tests/week13-browser.cjs`, and `docs/week13/` contain the regression tests, reproducible demo seed, and verification evidence.

## Known limitations

- Main had no integrated payments table or workflow at the baseline. The dashboard therefore displays refund counts and a refund trend. The Refund report shows amounts recorded as completed refunds.
- Historical occupancy requires reliable actual check-in times, old archive stay dates, and room inventory by date. The dashboard displays booking states and current room cleaning status using the available data.
- Status changes before the migration may lack history entries. Known creation, checkout, and archive timestamps are visible when available.
- Existing booking totals in front-desk views still use the current nightly room price. Week 13 reports avoid treating those estimates as historic payments.
- Booking search and report previews are server-paged. The other existing operational lists still use client-side filtering and pagination.
