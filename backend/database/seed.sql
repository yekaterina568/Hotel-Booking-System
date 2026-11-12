-- Демонстрационные данные для hotel_booking_dev.
-- Выполните файл целиком один раз в pgAdmin Query Tool.
-- Все имена, паспорта и телефоны вымышлены.
-- При повторном запуске UNIQUE-ограничения отменят транзакцию.
-- Служебные таблицы databasechangelog/databasechangeloglock заполняет Liquibase.

BEGIN;

-- guests:
-- guest_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY;
-- passport_num VARCHAR(20) NOT NULL UNIQUE, только A-Z и 0-9, длина 6-20;
-- first_name VARCHAR(30), last_name VARCHAR(30), phone_num VARCHAR(15): NOT NULL;
-- created_at TIMESTAMPTZ NOT NULL DEFAULT NOW().
INSERT INTO guests (passport_num, first_name, last_name, phone_num, created_at)
VALUES
    ('TESTKZ0001', 'Ayan',    'Saparov',    '+77000000001', '2026-10-01 10:00:00+05'),
    ('TESTKZ0002', 'Madina',  'Askarova',   '+77000000002', '2026-10-02 10:00:00+05'),
    ('TESTKZ0003', 'Daniyar', 'Bekov',      '+77000000003', '2026-10-03 10:00:00+05'),
    ('TESTKZ0004', 'Amina',   'Omarova',    '+77000000004', '2026-11-01 10:00:00+05'),
    ('TESTKZ0005', 'Timur',   'Nurlanov',   '+77000000005', '2026-11-02 10:00:00+05'),
    ('TESTKZ0006', 'Aliya',   'Ibragimova', '+77000000006', '2026-11-03 10:00:00+05');

-- rooms:
-- room_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY;
-- room_number INTEGER NOT NULL UNIQUE;
-- room_type room_type_enum NOT NULL DEFAULT 'Single';
-- cleaning_state cleaning_state_enum NOT NULL DEFAULT 'Dirty';
-- price_per_night NUMERIC(10,2) NOT NULL, >= 0.
-- Здесь представлены все 8 типов номера и все 3 состояния уборки.
INSERT INTO rooms (room_number, room_type, cleaning_state, price_per_night)
VALUES
    (101, 'Single',       'Cleaned',  15000.00),
    (102, 'Double',       'Cleaned',  25000.00),
    (103, 'Twin',         'Cleaned',  26000.00),
    (104, 'Triple',       'Cleaning', 35000.00),
    (201, 'Family',       'Cleaned',  45000.00),
    (202, 'Suite',        'Dirty',    70000.00),
    (301, 'Deluxe',       'Cleaned',  95000.00),
    (401, 'Presidential', 'Dirty',   200000.00);

-- bookings:
-- booking_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY;
-- guest_id BIGINT NOT NULL REFERENCES guests(guest_id);
-- room_id BIGINT NOT NULL REFERENCES rooms(room_id);
-- expected_arrival TIMESTAMPTZ NOT NULL;
-- state booking_state_enum NOT NULL DEFAULT 'Confirmed';
-- created_at TIMESTAMPTZ NOT NULL DEFAULT NOW().
-- ID гостя и номера находим по уникальным паспорту и номеру комнаты.
-- По 3 бронирования в каждом из 4 состояний.
INSERT INTO bookings (guest_id, room_id, expected_arrival, state, created_at)
SELECT g.guest_id, r.room_id, v.expected_arrival::TIMESTAMPTZ,
       v.state::booking_state_enum, v.created_at::TIMESTAMPTZ
FROM (VALUES
    -- Гости, уже заселённые 6 ноября.
    ('TESTKZ0001', 101, '2026-11-06 11:00:00+05', 'CheckedIn', '2026-11-01 12:00:00+05'),
    ('TESTKZ0002', 102, '2026-11-06 11:30:00+05', 'CheckedIn', '2026-11-02 12:00:00+05'),
    ('TESTKZ0003', 201, '2026-11-06 12:00:00+05', 'CheckedIn', '2026-11-03 12:00:00+05'),
    -- Ожидаемые заезды.
    ('TESTKZ0004', 103, '2026-11-07 14:00:00+05', 'Confirmed', '2026-11-04 12:00:00+05'),
    ('TESTKZ0005', 104, '2026-11-08 14:00:00+05', 'Confirmed', '2026-11-04 13:00:00+05'),
    ('TESTKZ0006', 202, '2026-11-09 14:00:00+05', 'Confirmed', '2026-11-05 12:00:00+05'),
    -- Отменённые бронирования для примеров возвратов.
    ('TESTKZ0001', 102, '2026-11-04 14:00:00+05', 'Cancelled', '2026-10-28 12:00:00+05'),
    ('TESTKZ0002', 301, '2026-11-05 14:00:00+05', 'Cancelled', '2026-10-29 12:00:00+05'),
    ('TESTKZ0003', 401, '2026-11-06 14:00:00+05', 'Cancelled', '2026-10-30 12:00:00+05'),
    -- Завершённые проживания для архива.
    ('TESTKZ0001', 101, '2026-10-15 14:00:00+05', 'Archived',  '2026-10-05 12:00:00+05'),
    ('TESTKZ0002', 102, '2026-10-16 14:00:00+05', 'Archived',  '2026-10-06 12:00:00+05'),
    ('TESTKZ0003', 201, '2026-10-17 14:00:00+05', 'Archived',  '2026-10-07 12:00:00+05')
) AS v(passport_num, room_number, expected_arrival, state, created_at)
JOIN guests g ON g.passport_num = v.passport_num
JOIN rooms r ON r.room_number = v.room_number;

-- refunds:
-- refund_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY;
-- booking_id BIGINT NOT NULL REFERENCES bookings(booking_id);
-- amount NUMERIC(10,2) NOT NULL, > 0;
-- refund_reason TEXT NULL, processed_at TIMESTAMPTZ NULL;
-- state refund_state_enum NOT NULL DEFAULT 'Pending'.
-- У каждого из трёх гостей выше ровно одна отменённая бронь.
INSERT INTO refunds (booking_id, amount, refund_reason, processed_at, state)
SELECT b.booking_id, v.amount, v.refund_reason, v.processed_at::TIMESTAMPTZ,
       v.state::refund_state_enum
FROM (VALUES
    ('TESTKZ0001',  25000.00, 'Guest cancelled the trip',
     '2026-11-03 15:00:00+05', 'Completed'),
    ('TESTKZ0002',  95000.00, 'Refund is awaiting payment processing',
     NULL, 'Pending'),
    ('TESTKZ0003', 200000.00, 'Payment provider rejected the refund; retry needed',
     '2026-11-05 16:00:00+05', 'Failed')
) AS v(passport_num, amount, refund_reason, processed_at, state)
JOIN guests g ON g.passport_num = v.passport_num
JOIN bookings b ON b.guest_id = g.guest_id AND b.state = 'Cancelled';

-- keycards:
-- keycard_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY;
-- booking_id BIGINT NOT NULL REFERENCES bookings(booking_id);
-- card_uid VARCHAR(32) NOT NULL UNIQUE;
-- issue_timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW();
-- is_active BOOLEAN NOT NULL DEFAULT TRUE.
-- По одной карте для 3 текущих и 3 архивных проживаний.
-- Карты архивных проживаний неактивны; UID <= 29 символов даже для BIGINT ID.
INSERT INTO keycards (booking_id, card_uid, issue_timestamp, is_active)
SELECT b.booking_id, 'DEMO-CARD-' || b.booking_id::TEXT,
       b.expected_arrival + INTERVAL '30 minutes', b.state = 'CheckedIn'
FROM bookings b
JOIN guests g ON g.guest_id = b.guest_id
WHERE g.passport_num IN ('TESTKZ0001', 'TESTKZ0002', 'TESTKZ0003')
  AND b.state IN ('CheckedIn', 'Archived');

-- bookings_archive:
-- booking_id BIGINT PRIMARY KEY (без IDENTITY, копируется из bookings);
-- guest_id BIGINT NOT NULL REFERENCES guests(guest_id);
-- room_id BIGINT NOT NULL REFERENCES rooms(room_id);
-- expected_arrival TIMESTAMPTZ NOT NULL;
-- state booking_state_enum NOT NULL DEFAULT 'Confirmed';
-- created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
-- archived_at TIMESTAMPTZ NOT NULL DEFAULT NOW().
-- Копируем данные трёх завершённых бронирований с их фактическими ID.
INSERT INTO bookings_archive
    (booking_id, guest_id, room_id, expected_arrival, state, created_at, archived_at)
SELECT b.booking_id, b.guest_id, b.room_id, b.expected_arrival, b.state,
       b.created_at, b.expected_arrival + INTERVAL '2 days'
FROM bookings b
JOIN guests g ON g.guest_id = b.guest_id
WHERE g.passport_num IN ('TESTKZ0001', 'TESTKZ0002', 'TESTKZ0003')
  AND b.state = 'Archived';

-- Ожидаемые количества для изначально пустой базы: 6, 8, 12, 3, 6, 3.
SELECT 'guests' AS table_name, COUNT(*) AS row_count FROM guests
UNION ALL SELECT 'rooms', COUNT(*) FROM rooms
UNION ALL SELECT 'bookings', COUNT(*) FROM bookings
UNION ALL SELECT 'refunds', COUNT(*) FROM refunds
UNION ALL SELECT 'keycards', COUNT(*) FROM keycards
UNION ALL SELECT 'bookings_archive', COUNT(*) FROM bookings_archive;

COMMIT;
