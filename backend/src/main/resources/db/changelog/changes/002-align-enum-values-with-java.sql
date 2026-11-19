--liquibase formatted sql

--changeset developer:002-align-enum-values-with-java
ALTER TYPE cleaning_state_enum RENAME VALUE 'Cleaned' TO 'CLEANED';
ALTER TYPE cleaning_state_enum RENAME VALUE 'Cleaning' TO 'CLEANING';
ALTER TYPE cleaning_state_enum RENAME VALUE 'Dirty' TO 'DIRTY';

ALTER TYPE booking_state_enum RENAME VALUE 'Confirmed' TO 'CONFIRMED';
ALTER TYPE booking_state_enum RENAME VALUE 'CheckedIn' TO 'CHECKEDIN';
ALTER TYPE booking_state_enum RENAME VALUE 'Cancelled' TO 'CANCELLED';
ALTER TYPE booking_state_enum RENAME VALUE 'Archived' TO 'ARCHIVED';

ALTER TYPE refund_state_enum RENAME VALUE 'Pending' TO 'PENDING';
ALTER TYPE refund_state_enum RENAME VALUE 'Completed' TO 'COMPLETED';
ALTER TYPE refund_state_enum RENAME VALUE 'Failed' TO 'FAILED';

ALTER TYPE room_type_enum RENAME VALUE 'Single' TO 'SINGLE';
ALTER TYPE room_type_enum RENAME VALUE 'Double' TO 'DOUBLE';
ALTER TYPE room_type_enum RENAME VALUE 'Twin' TO 'TWIN';
ALTER TYPE room_type_enum RENAME VALUE 'Triple' TO 'TRIPLE';
ALTER TYPE room_type_enum RENAME VALUE 'Family' TO 'FAMILY';
ALTER TYPE room_type_enum RENAME VALUE 'Suite' TO 'SUITE';
ALTER TYPE room_type_enum RENAME VALUE 'Deluxe' TO 'DELUXE';
ALTER TYPE room_type_enum RENAME VALUE 'Presidential' TO 'PRESIDENTIAL';
