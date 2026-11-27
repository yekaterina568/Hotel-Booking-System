--liquibase formatted sql

--changeset developer:backfill-missing-booking-departures
-- Match the one-night default from 005 for nullable rows written after 005 ran.
UPDATE bookings
SET expected_departure = expected_arrival + INTERVAL '1 day'
WHERE expected_departure IS NULL;
