--liquibase formatted sql
--changeset team:004-dev-seed context:@dev
INSERT INTO hotels(name,city,timezone) VALUES ('Stage 0 Demo Hotel','Almaty','Asia/Almaty');
INSERT INTO guests(passport_num,first_name,last_name,phone_num)
VALUES ('DEMO000001','Demo','Guest','+77000000000') ON CONFLICT(passport_num) DO NOTHING;
INSERT INTO rooms(hotel_id,room_number,room_type,cleaning_state,price_per_night)
SELECT hotel_id,101,'Single','Cleaned',15000 FROM hotels WHERE name='Stage 0 Demo Hotel';
INSERT INTO rooms(hotel_id,room_number,room_type,cleaning_state,price_per_night)
SELECT hotel_id,102,'Double','Dirty',25000 FROM hotels WHERE name='Stage 0 Demo Hotel';
