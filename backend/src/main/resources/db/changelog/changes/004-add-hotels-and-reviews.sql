--liquibase formatted sql

--changeset developer:004-add-hotels-and-reviews
CREATE TABLE hotels (
    hotel_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    city VARCHAR(100) NOT NULL,
    address VARCHAR(200),
    description VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Existing rooms and room creation requests belong to the original property.
INSERT INTO hotels (hotel_id, name, city)
OVERRIDING SYSTEM VALUE VALUES (1, 'Grand Almaty', 'Almaty');

ALTER TABLE hotels ALTER COLUMN hotel_id RESTART WITH 2;
ALTER TABLE rooms ADD COLUMN hotel_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE rooms ADD CONSTRAINT fk_rooms_hotel
    FOREIGN KEY (hotel_id) REFERENCES hotels (hotel_id) ON DELETE RESTRICT;
CREATE INDEX idx_rooms_hotel ON rooms (hotel_id);

CREATE TABLE reviews (
    review_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    hotel_id BIGINT NOT NULL,
    author_name VARCHAR(100) NOT NULL,
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment VARCHAR(2000) NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PUBLISHED', 'HIDDEN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_reviews_hotel
        FOREIGN KEY (hotel_id) REFERENCES hotels (hotel_id) ON DELETE RESTRICT
);

CREATE INDEX idx_reviews_hotel ON reviews (hotel_id);
