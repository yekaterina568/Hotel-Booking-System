--liquibase formatted sql

--changeset ayan:006-create-payments

-- Платёжный журнал. Строка = одна оплата (PAID) или один завершённый возврат (REFUNDED).
-- Баланс брони = сумма за проживание - (PAID - REFUNDED); считается в PaymentService.
CREATE TABLE payments (
                          payment_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                          booking_id BIGINT NOT NULL,
                          refund_id BIGINT UNIQUE,
                          amount NUMERIC(10,2) NOT NULL,
                          method VARCHAR(20),
                          status VARCHAR(20) NOT NULL DEFAULT 'PAID',
                          transaction_ref VARCHAR(40) NOT NULL UNIQUE,
                          idempotency_key VARCHAR(64) UNIQUE,
                          paid_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                          CONSTRAINT chk_payment_amount
                              CHECK (amount > 0),

                          CONSTRAINT chk_payment_method
                              CHECK (method IS NULL OR method IN ('CASH', 'CARD', 'TRANSFER')),

                          CONSTRAINT chk_payment_status
                              CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'REFUNDED')),

                          -- у оплаты всегда есть способ; возврат привязан к строке refunds, и наоборот
                          CONSTRAINT chk_payment_paid_has_method
                              CHECK (status <> 'PAID' OR method IS NOT NULL),

                          CONSTRAINT chk_payment_refund_link
                              CHECK ((status = 'REFUNDED') = (refund_id IS NOT NULL)),

                          CONSTRAINT fk_payments_booking
                              FOREIGN KEY (booking_id)
                                  REFERENCES bookings(booking_id)
                                  ON DELETE RESTRICT,

                          -- удалили возврат -> исчезает и его строка в журнале
                          CONSTRAINT fk_payments_refund
                              FOREIGN KEY (refund_id)
                                  REFERENCES refunds(refund_id)
                                  ON DELETE CASCADE
);

CREATE INDEX idx_payments_booking ON payments(booking_id, status);
