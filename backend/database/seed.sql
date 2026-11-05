
BEGIN;

-- 1. Guests: 6 records
INSERT INTO guests
(passport_num, full_name, phone_num)
VALUES
('KZ1234567', 'Arman Sadykov', '+77010000001'),
('KZ1234568', 'Aigerim Nurlanova', '+77010000002'),
('KZ1234569', 'Dias Omarov', '+77010000003'),
('KZ1234570', 'Madina Alieva', '+77010000004'),
('KZ1234571', 'Alina Bekova', '+77010000005'),
('KZ1234572', 'Timur Askarov', '+77010000006');

-- 2. Rooms: 8 records
INSERT INTO rooms
(room_number, room_type, cleaning_status, price_per_night)
VALUES
(101, 'Standard', 'Cleaned', 20000),
(102, 'Standard', 'Dirty', 20000),
(103, 'Deluxe', 'Cleaned', 35000),
(104, 'Suite', 'Cleaned', 50000),
(201, 'Standard', 'Dirty', 22000),
(202, 'Deluxe', 'Cleaned', 38000),
(203, 'Suite', 'Cleaning', 55000),
(204, 'Standard', 'Cleaned', 24000);

-- 3. Bookings: 8 records
INSERT INTO bookings
(guest_id, room_id, expected_arrival, status)
VALUES
(1, 1, date_trunc('day', NOW()) + INTERVAL '10 hours', 'Confirmed'),
(2, 2, date_trunc('day', NOW()) + INTERVAL '11 hours', 'Confirmed'),
(3, 3, date_trunc('day', NOW()) + INTERVAL '12 hours', 'Confirmed'),
(4, 4, NOW() - INTERVAL '4 days', 'CheckedIn'),
(5, 5, NOW() - INTERVAL '45 days', 'Cancelled'),
(6, 6, NOW() - INTERVAL '60 days', 'CheckedIn'),
(1, 7, NOW() + INTERVAL '1 day', 'Confirmed'),
(2, 8, NOW() - INTERVAL '70 days', 'Cancelled');

-- 4. Refunds: 3 records
INSERT INTO refunds
(booking_id, amount, refund_reason, processed_at, status)
VALUES
(5, 5000.00, 'Booking cancellation',
 NOW() - INTERVAL '40 days', 'Completed'),
(8, 8000.00, 'Guest cancellation',
 NULL, 'Pending'),
(6, 2000.00, 'Early checkout adjustment',
 NOW() - INTERVAL '50 days', 'Failed');

-- 5. Keycards: 4 records
INSERT INTO keycards
(booking_id, card_uid, is_active)
VALUES
(4, 'RFID-CARD-1001', TRUE),
(4, 'RFID-CARD-1002', TRUE),
(6, 'RFID-CARD-1003', FALSE),
(6, 'RFID-CARD-1004', FALSE);

-- 6. Archive: 2 demonstration copies
INSERT INTO bookings_archive
(booking_id, guest_id, room_id,
 expected_arrival, status, created_at)
SELECT
    booking_id,
    guest_id,
    room_id,
    expected_arrival,
    status,
    created_at
FROM bookings
WHERE booking_id IN (5, 8);

COMMIT;
