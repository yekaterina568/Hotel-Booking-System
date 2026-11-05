
BEGIN;

-- ENUM types
CREATE TYPE cleaning_status_enum AS ENUM (
    'Cleaned', 'Cleaning', 'Dirty'
);

CREATE TYPE booking_status_enum AS ENUM (
    'Confirmed', 'CheckedIn', 'Cancelled', 'Archived'
);

CREATE TYPE refund_status_enum AS ENUM (
    'Pending', 'Completed', 'Failed'
);

-- 1. Guests
CREATE TABLE guests (
    guest_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    passport_num VARCHAR(20) NOT NULL UNIQUE,
    full_name VARCHAR(150) NOT NULL,
    phone_num VARCHAR(15) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_passport_format
        CHECK (passport_num ~ '^[A-Z0-9]{6,20}$')
);

-- 2. Rooms
CREATE TABLE rooms (
    room_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    room_number INTEGER NOT NULL UNIQUE,
    room_type VARCHAR(50) NOT NULL,
    cleaning_status cleaning_status_enum
        NOT NULL DEFAULT 'Dirty',
    price_per_night NUMERIC(10,2) NOT NULL
        CHECK (price_per_night >= 0)
);

-- 3. Bookings
CREATE TABLE bookings (
    booking_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    guest_id INTEGER NOT NULL,
    room_id INTEGER NOT NULL,
    expected_arrival TIMESTAMPTZ NOT NULL,
    status booking_status_enum
        NOT NULL DEFAULT 'Confirmed',
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

-- 4. Refunds
CREATE TABLE refunds (
    refund_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id INTEGER NOT NULL,
    amount NUMERIC(10,2) NOT NULL
        CHECK (amount > 0),
    refund_reason TEXT,
    processed_at TIMESTAMPTZ,
    status refund_status_enum
        NOT NULL DEFAULT 'Pending',

    CONSTRAINT fk_refunds_booking
        FOREIGN KEY (booking_id)
        REFERENCES bookings(booking_id)
        ON DELETE RESTRICT
);

-- 5. Keycards
CREATE TABLE keycards (
    keycard_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id INTEGER NOT NULL,
    card_uid VARCHAR(32) NOT NULL UNIQUE,
    issue_timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_keycards_booking
        FOREIGN KEY (booking_id)
        REFERENCES bookings(booking_id)
        ON DELETE RESTRICT
);

-- 6. Bookings Archive
CREATE TABLE bookings_archive (
    booking_id INTEGER PRIMARY KEY,
    guest_id INTEGER NOT NULL,
    room_id INTEGER NOT NULL,
    expected_arrival TIMESTAMPTZ NOT NULL,
    status booking_status_enum
        NOT NULL DEFAULT 'Confirmed',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    archived_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    FOREIGN KEY (guest_id)
        REFERENCES guests(guest_id)
        ON DELETE RESTRICT,

    FOREIGN KEY (room_id)
        REFERENCES rooms(room_id)
        ON DELETE RESTRICT
);

-- Composite index from Task 9
CREATE INDEX idx_booking_status_arrival
    ON bookings(status, expected_arrival);

COMMIT;
