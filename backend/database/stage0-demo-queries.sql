-- Read-only evidence. Run against the intended demonstration database.
BEGIN READ ONLY;
-- Hotel -> room -> booking -> guest -> persisted cost (no passport or phone).
SELECT h.name AS hotel, r.room_number, b.booking_id,
       g.first_name, b.state, b.check_in_date, b.check_out_date,
       c.nights, c.nightly_rate, c.subtotal, c.service_fee, c.total, c.currency,
       c.estimated_legacy
FROM hotels h JOIN rooms r USING(hotel_id)
JOIN bookings b USING(room_id) JOIN guests g USING(guest_id)
LEFT JOIN booking_costs c USING(booking_id)
ORDER BY b.booking_id DESC LIMIT 20;
-- Payment/refund foreign keys and remaining balance, avoiding row multiplication.
SELECT b.booking_id, p.payment_id, p.state, p.amount,
       coalesce((SELECT sum(r.amount) FROM refunds r
                 WHERE r.payment_id=p.payment_id AND r.state<>'Failed'),0) AS refunded
FROM bookings b JOIN payments p USING(booking_id)
ORDER BY p.payment_id DESC LIMIT 20;
-- Verified reviews are tied to completed stays, rooms and hotels.
SELECT v.review_id, v.booking_id, v.rating, h.name, r.room_number, a.state AS state_before_archive
FROM reviews v JOIN bookings b USING(booking_id)
JOIN bookings_archive a USING(booking_id) JOIN rooms r ON r.room_id=b.room_id
JOIN hotels h USING(hotel_id) ORDER BY v.review_id DESC;
-- The original 001 checksum is retained; dev seed is explicit.
SELECT id, author, filename, md5sum, exectype FROM databasechangelog ORDER BY orderexecuted;
SELECT conname, pg_get_constraintdef(oid) AS definition
FROM pg_constraint WHERE connamespace=current_schema()::regnamespace
AND conrelid IN ('rooms'::regclass,'bookings'::regclass,'booking_costs'::regclass,
                'payments'::regclass,'refunds'::regclass,'reviews'::regclass)
ORDER BY conrelid, conname;
SELECT tablename,indexname,indexdef FROM pg_indexes WHERE schemaname=current_schema()
AND tablename IN ('rooms','bookings','booking_costs','payments','refunds','reviews','keycards')
ORDER BY tablename,indexname;
-- These legacy records are preserved, not presented as dated stays or verified quotes.
SELECT count(*) AS bookings, count(*) FILTER(WHERE check_in_date IS NULL) AS legacy_undated FROM bookings;
COMMIT;
