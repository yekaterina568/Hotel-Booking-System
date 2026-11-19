--liquibase formatted sql

--changeset developer:003-seed-demo-rooms
INSERT INTO rooms (room_number, room_type, cleaning_state, price_per_night)
VALUES
    (101, 'SINGLE', 'CLEANED', 100.00),
    (102, 'DOUBLE', 'CLEANED', 150.00),
    (103, 'TWIN', 'DIRTY', 130.00),
    (201, 'SUITE', 'CLEANED', 250.00)
ON CONFLICT (room_number) DO NOTHING;
