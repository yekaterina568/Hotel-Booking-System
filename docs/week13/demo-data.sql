\set ON_ERROR_STOP on

-- Run manually only against a dedicated database named week13_demo after Liquibase starts.
DO $$
DECLARE
    riverside bigint;
    summit bigint;
    demo_guest bigint;
    demo_room bigint;
    demo_booking bigint;
    demo_state booking_state_enum;
    created_on timestamptz;
    arrival timestamptz;
    departure timestamptz;
    checkout_on timestamptz;
    n integer;
BEGIN
    IF current_database() <> 'week13_demo' THEN
        RAISE EXCEPTION 'Demo data requires the dedicated week13_demo database';
    END IF;
    PERFORM pg_advisory_xact_lock(130026);
    IF EXISTS (SELECT 1 FROM hotels WHERE name = 'Riverside Hotel (Demo)') THEN
        RAISE NOTICE 'Week 13 demo data already exists';
        RETURN;
    END IF;

    INSERT INTO hotels (name, city) VALUES ('Riverside Hotel (Demo)', 'Almaty') RETURNING hotel_id INTO riverside;
    INSERT INTO hotels (name, city) VALUES ('Summit Hotel (Demo)', 'Astana') RETURNING hotel_id INTO summit;
    INSERT INTO rooms (hotel_id, room_number, room_type, cleaning_state, price_per_night) VALUES
        (riverside, 101, 'DOUBLE', 'CLEANED', 125),
        (riverside, 102, 'TWIN', 'CLEANING', 170),
        (riverside, 103, 'SUITE', 'DIRTY', 250),
        (summit, 101, 'SINGLE', 'CLEANED', 105),
        (summit, 102, 'FAMILY', 'DIRTY', 160),
        (summit, 103, 'DELUXE', 'CLEANED', 210);

    FOR n IN 1..42 LOOP
        SELECT room_id INTO demo_room FROM rooms
            WHERE hotel_id = CASE WHEN n % 2 = 0 THEN riverside ELSE summit END
              AND room_number = 101 + (n % 3);
        INSERT INTO guests (passport_num, first_name, last_name, phone_num)
            VALUES ('W13DEMO' || lpad(n::text, 3, '0'), 'Demo', 'Guest ' || n, '0000000000')
            RETURNING guest_id INTO demo_guest;
        created_on := date_trunc('day', now(), 'Asia/Almaty') - make_interval(days => (n * 4 + n / 4) % 38)
            + interval '9 hours';
        arrival := created_on + interval '1 day';
        departure := arrival + interval '2 days';
        demo_state := (ARRAY['CONFIRMED','CHECKEDIN','CHECKEDOUT','CANCELLED','ARCHIVED']::booking_state_enum[])[1 + n % 5];
        IF demo_state = 'CONFIRMED' THEN
            arrival := date_trunc('day', now()) + make_interval(days => n + 2);
            departure := arrival + interval '2 days';
        END IF;
        checkout_on := CASE WHEN demo_state IN ('CHECKEDOUT', 'ARCHIVED') THEN departure END;

        INSERT INTO bookings (guest_id, room_id, state, created_at, expected_arrival, expected_departure, checked_out_at)
            VALUES (demo_guest, demo_room, demo_state, created_on, arrival, departure, checkout_on)
            RETURNING booking_id INTO demo_booking;
        IF demo_state = 'ARCHIVED' THEN
            INSERT INTO bookings_archive (booking_id, guest_id, room_id, state, created_at, expected_arrival,
                                          expected_departure, archived_at, checked_out_at)
            VALUES (demo_booking, demo_guest, demo_room, 'ARCHIVED', created_on, arrival, departure, departure, checkout_on);
        END IF;
        IF n % 4 = 0 THEN
            INSERT INTO refunds (booking_id, amount, refund_reason, processed_at, state)
                VALUES (demo_booking, 25 + n, 'Synthetic demo refund', created_on, 'COMPLETED');
        ELSIF n % 9 = 0 THEN
            INSERT INTO refunds (booking_id, amount, refund_reason, state)
                VALUES (demo_booking, 15 + n, 'Synthetic demo refund', 'PENDING');
        END IF;
    END LOOP;

    INSERT INTO reviews (hotel_id, author_name, rating, comment, status, created_at)
        SELECT CASE WHEN i % 2 = 0 THEN riverside ELSE summit END, 'Demo reviewer',
               (ARRAY[5,4,5,3,4,5,2,1])[1 + i % 8], 'Synthetic review for the Week 13 demonstration.',
               CASE WHEN i % 7 = 0 THEN 'PENDING' WHEN i % 9 = 0 THEN 'HIDDEN' ELSE 'PUBLISHED' END,
               date_trunc('day', now(), 'Asia/Almaty') - make_interval(days => i % 28) + interval '9 hours'
        FROM generate_series(1, 24) AS i;
END $$;
