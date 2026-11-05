--liquibase formatted sql

--changeset developer:001

CREATE TYPE cleaning_state_enum AS ENUM (
    'Cleaned',
    'Cleaning',
    'Dirty'
);

CREATE TYPE booking_state_enum AS ENUM (
    'Confirmed',
    'CheckedIn',
    'Cancelled',
    'Archived'
);

CREATE TYPE refund_state_enum AS ENUM (
    'Pending',
    'Completed',
    'Failed'
);

CREATE TYPE room_type_enum AS ENUM (
    'Single',
    'Double',
    'Twin',
    'Triple',
    'Family',
    'Suite',
    'Deluxe',
    'Presidential'
    );

CREATE TABLE guests (
                        guest_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        passport_num VARCHAR(20) NOT NULL UNIQUE,
                        first_name VARCHAR(30) NOT NULL,
                        last_name VARCHAR(30) NOT NULL,
                        phone_num VARCHAR(15) NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                        CONSTRAINT chk_passport_format
                            CHECK (passport_num ~ '^[A-Z0-9]{6,20}$')
);

CREATE TABLE rooms (
                       room_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                       room_number INTEGER NOT NULL UNIQUE,
                       room_type room_type_enum NOT NULL DEFAULT 'Single',
                       cleaning_state cleaning_state_enum NOT NULL DEFAULT 'Dirty',
                       price_per_night NUMERIC(10,2) NOT NULL,

                       CONSTRAINT chk_room_price
                           CHECK (price_per_night >= 0)
);

CREATE TABLE bookings (
                          booking_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                          guest_id BIGINT NOT NULL,
                          room_id BIGINT NOT NULL,
                          expected_arrival TIMESTAMPTZ NOT NULL,
                          state booking_state_enum NOT NULL DEFAULT 'Confirmed',
                          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                          CONSTRAINT fk_bookings_guest
                              FOREIGN KEY (guest_id)
                                  REFERENCES guests(guest_id)
                                  ON DELETE RESTRICT,

                          CONSTRAINT fk_bookings_room
                              FOREIGN KEY (room_id)
                                  REFERENCES rooms(room_id)
                                  ON DELETE RESTRICT
);

CREATE TABLE refunds (
                         refund_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                         booking_id BIGINT NOT NULL,
                         amount NUMERIC(10,2) NOT NULL,
                         refund_reason TEXT,
                         processed_at TIMESTAMPTZ,
                         state refund_state_enum NOT NULL DEFAULT 'Pending',

                         CONSTRAINT chk_refund_amount
                             CHECK (amount > 0),

                         CONSTRAINT fk_refunds_booking
                             FOREIGN KEY (booking_id)
                                 REFERENCES bookings(booking_id)
                                 ON DELETE RESTRICT
);

CREATE TABLE keycards (
                          keycard_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                          booking_id BIGINT NOT NULL,
                          card_uid VARCHAR(32) NOT NULL UNIQUE,
                          issue_timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                          is_active BOOLEAN NOT NULL DEFAULT TRUE,

                          CONSTRAINT fk_keycards_booking
                              FOREIGN KEY (booking_id)
                                  REFERENCES bookings(booking_id)
                                  ON DELETE RESTRICT
);

CREATE TABLE bookings_archive (
                                  booking_id BIGINT PRIMARY KEY,
                                  guest_id BIGINT NOT NULL,
                                  room_id BIGINT NOT NULL,
                                  expected_arrival TIMESTAMPTZ NOT NULL,
                                  state booking_state_enum NOT NULL DEFAULT 'Confirmed',
                                  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                  archived_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                                  CONSTRAINT fk_bookings_archive_guest
                                      FOREIGN KEY (guest_id)
                                          REFERENCES guests(guest_id)
                                          ON DELETE RESTRICT,

                                  CONSTRAINT fk_bookings_archive_room
                                      FOREIGN KEY (room_id)
                                          REFERENCES rooms(room_id)
                                          ON DELETE RESTRICT
);

CREATE INDEX idx_booking_status_arrival
    ON bookings(state, expected_arrival);