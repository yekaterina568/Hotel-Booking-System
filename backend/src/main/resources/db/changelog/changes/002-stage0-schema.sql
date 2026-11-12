--liquibase formatted sql
--changeset team:002-stage0
-- Keep 001 and all existing rows unchanged. Legacy dates remain unknown (NULL).
CREATE TABLE hotels (
    hotel_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    city VARCHAR(100) NOT NULL,
    timezone VARCHAR(60) NOT NULL DEFAULT 'Asia/Almaty'
);
INSERT INTO hotels(name, city) VALUES ('Legacy Hotel', 'Almaty');
ALTER TABLE rooms ADD COLUMN hotel_id BIGINT;
UPDATE rooms SET hotel_id = (SELECT hotel_id FROM hotels WHERE name = 'Legacy Hotel');
ALTER TABLE rooms ALTER COLUMN hotel_id SET NOT NULL;
-- Default preserves old SQL room inserts; new API requests require hotelId.
ALTER TABLE rooms ALTER COLUMN hotel_id SET DEFAULT 1;
ALTER TABLE rooms ADD CONSTRAINT fk_rooms_hotel FOREIGN KEY (hotel_id) REFERENCES hotels(hotel_id);
ALTER TABLE rooms DROP CONSTRAINT rooms_room_number_key;
ALTER TABLE rooms ADD CONSTRAINT uq_hotel_room_number UNIQUE(hotel_id, room_number);
ALTER TABLE bookings ADD COLUMN check_in_date DATE;
ALTER TABLE bookings ADD COLUMN check_out_date DATE;
ALTER TABLE bookings ADD COLUMN checked_in_at TIMESTAMPTZ;
ALTER TABLE bookings ADD CONSTRAINT chk_booking_dates CHECK (
    (check_in_date IS NULL AND check_out_date IS NULL) OR
    (check_in_date IS NOT NULL AND check_out_date IS NOT NULL AND check_out_date > check_in_date)
);
ALTER TABLE bookings_archive ADD COLUMN check_in_date DATE;
ALTER TABLE bookings_archive ADD COLUMN check_out_date DATE;
CREATE EXTENSION IF NOT EXISTS btree_gist;
-- Existing undated benchmark bookings stay intact. The API also checks them using
-- a documented one-night fallback in the hotel's timezone under a room row lock.
ALTER TABLE bookings ADD CONSTRAINT ex_active_booking_dates EXCLUDE USING gist (
    room_id WITH =, daterange(check_in_date, check_out_date, '[)') WITH &&
) WHERE (state IN ('Confirmed', 'CheckedIn') AND check_in_date IS NOT NULL);
CREATE INDEX idx_bookings_guest ON bookings(guest_id);
CREATE INDEX idx_bookings_room ON bookings(room_id);
CREATE TABLE booking_costs (
    cost_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id BIGINT NOT NULL UNIQUE REFERENCES bookings(booking_id),
    nights INTEGER NOT NULL CHECK(nights > 0),
    nightly_rate NUMERIC(10,2) NOT NULL CHECK(nightly_rate >= 0),
    subtotal NUMERIC(14,2) NOT NULL CHECK(subtotal = nights * nightly_rate),
    service_fee NUMERIC(14,2) NOT NULL CHECK(service_fee >= 0),
    total NUMERIC(14,2) NOT NULL CHECK(total = subtotal + service_fee),
    currency VARCHAR(3) NOT NULL CHECK(currency = 'KZT'),
    estimated_legacy BOOLEAN NOT NULL DEFAULT FALSE
);
-- Legacy amount is explicitly an estimate, never silently called a confirmed quote.
INSERT INTO booking_costs(booking_id,nights,nightly_rate,subtotal,service_fee,total,currency,estimated_legacy)
SELECT b.booking_id,1,r.price_per_night,r.price_per_night,0,r.price_per_night,'KZT',TRUE
FROM bookings b JOIN rooms r ON r.room_id=b.room_id;
CREATE TABLE payments (
    payment_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES bookings(booking_id),
    amount NUMERIC(14,2) NOT NULL CHECK(amount >= 0),
    currency VARCHAR(3) NOT NULL CHECK(currency = 'KZT'),
    state VARCHAR(15) NOT NULL CHECK(state IN ('SUCCEEDED','DECLINED')),
    idempotency_key VARCHAR(80) NOT NULL UNIQUE,
    provider_reference VARCHAR(40) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX uq_payment_success ON payments(booking_id) WHERE state='SUCCEEDED';
CREATE INDEX idx_payments_booking ON payments(booking_id);
ALTER TABLE refunds ADD COLUMN payment_id BIGINT REFERENCES payments(payment_id);
ALTER TABLE refunds ADD COLUMN idempotency_key VARCHAR(80) UNIQUE;
-- Legacy refunds have no recorded provider payment; preserve them with NULL payment_id.
ALTER TABLE refunds ALTER COLUMN amount TYPE NUMERIC(14,2);
CREATE INDEX idx_refunds_booking ON refunds(booking_id);
CREATE INDEX idx_refunds_payment ON refunds(payment_id);
CREATE INDEX idx_keycards_booking ON keycards(booking_id);
CREATE TABLE reviews (
    review_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id BIGINT NOT NULL UNIQUE REFERENCES bookings(booking_id),
    rating INTEGER NOT NULL CHECK(rating BETWEEN 1 AND 5),
    comment VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
