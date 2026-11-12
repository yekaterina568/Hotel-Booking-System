--liquibase formatted sql
--changeset team:003-stage0-guards splitStatements:false
-- Enforce new-write invariants without rejecting historical undated rows.
CREATE FUNCTION stage0_require_booking_dates() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.check_in_date IS NULL OR NEW.check_out_date IS NULL THEN
        RAISE EXCEPTION 'New bookings require stay dates' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER require_booking_dates BEFORE INSERT ON bookings
FOR EACH ROW EXECUTE FUNCTION stage0_require_booking_dates();
CREATE FUNCTION stage0_verified_review() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM bookings b JOIN bookings_archive a USING (booking_id)
        WHERE b.booking_id = NEW.booking_id AND b.state = 'Archived'
          AND a.state = 'CheckedIn' AND b.checked_in_at IS NOT NULL) THEN
        RAISE EXCEPTION 'Review requires a verified completed stay' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER verified_review BEFORE INSERT OR UPDATE ON reviews
FOR EACH ROW EXECUTE FUNCTION stage0_verified_review();
CREATE FUNCTION stage0_refund_payment() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.payment_id IS NULL OR NOT EXISTS (SELECT 1 FROM payments p
        WHERE p.payment_id=NEW.payment_id AND p.booking_id=NEW.booking_id AND p.state='SUCCEEDED') THEN
        RAISE EXCEPTION 'Refund requires a matching successful payment' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER refund_payment BEFORE INSERT ON refunds
FOR EACH ROW EXECUTE FUNCTION stage0_refund_payment();
