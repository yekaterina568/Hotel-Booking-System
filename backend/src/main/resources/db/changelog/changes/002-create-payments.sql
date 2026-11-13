--liquibase formatted sql

--changeset ayan:002-create-payments

CREATE TABLE payments (
                          payment_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                          booking_id BIGINT NOT NULL,
                          amount NUMERIC(10,2) NOT NULL,
                          method VARCHAR(20) NOT NULL,
                          status VARCHAR(20) NOT NULL DEFAULT 'PAID',
                          transaction_ref VARCHAR(40) NOT NULL UNIQUE,
                          paid_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                          CONSTRAINT chk_payment_amount
                              CHECK (amount > 0),

                          CONSTRAINT chk_payment_method
                              CHECK (method IN ('CASH', 'CARD', 'TRANSFER')),

                          CONSTRAINT chk_payment_status
                              CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'REFUNDED')),

                          CONSTRAINT fk_payments_booking
                              FOREIGN KEY (booking_id)
                                  REFERENCES bookings(booking_id)
                                  ON DELETE RESTRICT
);

CREATE INDEX idx_payments_booking ON payments(booking_id);
