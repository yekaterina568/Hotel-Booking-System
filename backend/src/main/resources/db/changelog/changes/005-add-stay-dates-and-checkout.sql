--liquibase formatted sql

--changeset developer:005-add-checkedout-state runInTransaction:false
ALTER TYPE booking_state_enum ADD VALUE IF NOT EXISTS 'CHECKEDOUT' AFTER 'CHECKEDIN';

--changeset developer:005-add-stay-dates
ALTER TABLE bookings ADD COLUMN expected_departure TIMESTAMPTZ;
ALTER TABLE bookings ADD COLUMN checked_out_at TIMESTAMPTZ;

-- Old bookings had no departure date: treat them as a one-night stay.
UPDATE bookings
SET expected_departure = expected_arrival + INTERVAL '1 day'
WHERE expected_departure IS NULL;

ALTER TABLE bookings ADD CONSTRAINT chk_booking_dates
    CHECK (expected_departure IS NULL OR expected_departure > expected_arrival);

CREATE INDEX idx_bookings_room_dates
    ON bookings (room_id, expected_arrival, expected_departure);

ALTER TABLE bookings_archive ADD COLUMN expected_departure TIMESTAMPTZ;
ALTER TABLE bookings_archive ADD COLUMN checked_out_at TIMESTAMPTZ;
