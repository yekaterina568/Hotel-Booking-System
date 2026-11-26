--liquibase formatted sql

--changeset developer:week13-room-number-per-hotel
ALTER TABLE rooms ADD CONSTRAINT uq_rooms_hotel_number UNIQUE (hotel_id, room_number);
ALTER TABLE rooms DROP CONSTRAINT rooms_room_number_key;

--changeset developer:week13-booking-status-history splitStatements:false
CREATE TABLE booking_status_history (
    booking_id BIGINT NOT NULL REFERENCES bookings (booking_id) ON DELETE CASCADE,
    event_id BIGINT GENERATED ALWAYS AS IDENTITY,
    previous_state booking_state_enum NOT NULL,
    new_state booking_state_enum NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (booking_id, event_id)
);

CREATE FUNCTION capture_booking_status_change() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO booking_status_history (booking_id, previous_state, new_state)
    VALUES (NEW.booking_id, OLD.state, NEW.state);
    RETURN NEW;
END;
$$;

CREATE TRIGGER booking_status_changed
AFTER UPDATE OF state ON bookings FOR EACH ROW
WHEN (OLD.state IS DISTINCT FROM NEW.state)
EXECUTE FUNCTION capture_booking_status_change();
