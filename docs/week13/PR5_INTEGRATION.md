# PR #5 integration verification

The integration merged main `a6f01ce154a26e91b0d0a0c54495b0922ae00e4c` into the Week 13 branch from `1df103093dc3ec650d55c2f4635ea278a112a8b9`. The common ancestor was `20ecfecaf435a0eb4a20d66064dd034ca07646e9`. The working database and its volumes were left untouched. The integration used temporary PostgreSQL 17.11 databases, Java 26.0.2.1, and Chromium 158.0.8127.88.

## Resolution

- `AdminApiErrors.java` combines the global problem response with the admin validation details. Framework request errors retain their HTTP status. Database constraint conflicts take precedence over the generic error handler and return 409.
- `BookingRepository.java` and `BookingService.java` retain main’s room locks, overlap checks, stay extension, and cancellation timestamp. Extension takes the same booking-row lock as cancellation, checkout, and archiving before taking the room lock. The PostgreSQL exclusion constraint remains a second layer of protection.
- `frontend/app.js` and `index.html` combine the updated registration, booking, review, and billing screens with the existing Departures & Check-out workflow. The reservation picker still identifies the hotel when multiple hotels use the same room number.
- The Dashboard, six charts, report filters, CSV and printable exports, room management, booking history, and protected admin APIs remain active.

## Migration impact

The master changelog runs `week13-admin.sql`, then an additive backfill, then `006-prevent-double-booking-and-refund-rules.sql`. Both original SQL files and their changeset identifiers are unchanged. The backfill covers records written while the departure column still allowed null after migration 005. It uses the same one-night default as 005 before migration 006 makes the column mandatory.

Migration 006 marks each active booking that overlaps a lower-ID active booking in the same room as cancelled. It sets `cancelled_at` to the migration transaction time. In an overlap chain, a later booking can be cancelled because it overlaps a booking that the same statement also cancels. In the Week 13 upgrade fixture, two of four active bookings were cancelled. Both kept their original records and gained a timestamp and one status history event. The first booking and a booking that started exactly when the preceding stay ended remained active.

For databases that already ran 006 on main, Liquibase applied the missing Week 13 changes without repeating the cancellations. Existing cancellation timestamps were preserved. History starts with the installation of the trigger, so those earlier cancellations have no history events. Migration 006 directly updates the booking state and timestamp. It leaves refund records and keycards as they were.

To preview the records affected on a copy of a database that has not run 006, use this read-only query. It includes the one-night departure fallback used by the integration.

```sql
WITH active_stays AS (
    SELECT booking_id, room_id, state, expected_arrival,
           coalesce(expected_departure, expected_arrival + interval '1 day') AS departure
    FROM bookings
    WHERE state IN ('CONFIRMED', 'CHECKEDIN')
)
SELECT b.booking_id, b.room_id, b.state, b.expected_arrival, b.departure
FROM active_stays b
WHERE EXISTS (
    SELECT 1 FROM active_stays older
    WHERE older.room_id = b.room_id
      AND older.booking_id < b.booking_id
      AND older.expected_arrival < b.departure
      AND older.departure > b.expected_arrival
)
ORDER BY b.room_id, b.booking_id;
```

## Verification

- The complete backend suite ran with `./mvnw clean test` and `WEEK13_TEST_JDBC_URL` pointing to an isolated PostgreSQL 17.11 database. Maven reported **121 tests, 120 passed, 1 skipped, zero failures or errors**. All 12 tests in `Week13PostgresTest` passed. Coverage includes a fresh schema, upgrades from main and Week 13, the real exclusion constraint, a 409 for duplicate reservations, and an admin cancellation that drives both the refund and the history event. The run included the tests from PR #6. The existing dirty-room check-in case in `FoundBugsTest` remains disabled.
- The expanded `frontend/tests/week13-browser.cjs` ran against a separate seeded demo database in the temporary cluster. **All 11 browser check groups passed with zero uncaught JavaScript errors.** They covered admin sign-in, all charts, date and hotel filters, CSV and PDF output, room edits, guest and operator profile registration, reservation creation and extension, duplicate prevention, checkout, refund and duplicate refund handling, cancellation, archive and history, moderation, and mobile rendering.
- Operator profile registration uses an existing system account. The name and position are browser-local. Refunds use main’s cancellation policy and must be issued before the booking is archived. Payment reporting remains separate from the merged functionality.

[Browser output](evidence/pr5-browser-integration.txt)
