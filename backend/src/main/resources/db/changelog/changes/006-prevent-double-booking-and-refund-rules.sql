--liquibase formatted sql

--changeset developer:006-cancelled-at
-- When the booking was cancelled: the refund percentage depends on how early the guest cancelled.
ALTER TABLE bookings ADD COLUMN cancelled_at TIMESTAMPTZ;

--changeset developer:006-clean-existing-double-bookings
-- Old data may already contain double bookings, and the constraint below can't be created while they exist.
-- For every pair of active bookings of the same room with overlapping dates, the later booking is cancelled.
UPDATE bookings b
SET state = 'CANCELLED',
    cancelled_at = NOW()
WHERE b.state IN ('CONFIRMED', 'CHECKEDIN')
  AND EXISTS (
      SELECT 1
      FROM bookings o
      WHERE o.room_id = b.room_id
        AND o.booking_id < b.booking_id
        AND o.state IN ('CONFIRMED', 'CHECKEDIN')
        AND o.expected_arrival < b.expected_departure
        AND o.expected_departure > b.expected_arrival
  );

--changeset developer:006-no-double-booking
-- Every booking has a departure date since migration 005.
ALTER TABLE bookings ALTER COLUMN expected_departure SET NOT NULL;

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- The database itself refuses two active bookings of one room with overlapping stays [arrival, departure).
-- This also protects against two requests that arrive at the same moment.
ALTER TABLE bookings ADD CONSTRAINT excl_bookings_room_no_overlap
    EXCLUDE USING gist (
        room_id WITH =,
        tstzrange(expected_arrival, expected_departure, '[)') WITH &&
    )
    WHERE (state IN ('CONFIRMED', 'CHECKEDIN'));
