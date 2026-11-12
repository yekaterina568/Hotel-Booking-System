# Stage 0 demonstration guide

This is a six-person demonstration/review plan for AI-assisted work. Describe what you can show and what you reviewed; do not claim personal authorship of code generated in this session.

## Safe demonstration startup

Requirements: JDK 25, Docker Desktop, Node 22+ for frontend tests. Start Docker Desktop, then from the repository root:

```sh
./scripts/start-stage0-postgres.sh
```

In another terminal, from the repository root:

```sh
DEBUG=false SPRING_PROFILES_ACTIVE=dev \
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:55433/stage0_demo \
POSTGRES_USER=stage0 POSTGRES_PASSWORD=stage0-test-only \
HOTEL_STAFF_USERNAME=staff HOTEL_STAFF_PASSWORD=demo-password \
SERVER_PORT=18080 ./backend/mvnw -f backend/pom.xml spring-boot:run
```

This uses only the separate temporary demonstration database. Existing Compose data is untouched. Open `http://127.0.0.1:18080/` and connect as `staff` / `demo-password`. These are explicit local test credentials. The session may already have this backend running; use it rather than starting another process on the same port.

The dev seed includes `Stage 0 Demo Hotel`, Cleaned room 101, Dirty room 102 and one synthetic guest. Other rooms/records may be left from the browser tests. Always use hotel name plus room number and copy current booking IDs from the screen. If room 101 is already booked, cancel a Confirmed demo booking or choose another demo room/date. Do not delete database volumes.

Prepare two bookings for today in Asia/Almaty, each for two nights. Use different rooms or cancel the first before reusing its room. Expected arrival can be 14:00 hotel local time. One booking is for the payment/cancellation/refund path; the other is for check-in/checkout/review.

## 24B031882 — Reservation and check-in

1. Choose a guest and a Cleaned demo room. Check availability for today through two days later; create a booking and copy its ID.
2. Try the same dates again: the server reports a conflict, not success.
3. For the stay you will keep, find it in Ready for check-in and click Check in.
4. Show state CHECKEDIN. The backend issued a simulated keycard record in the same transaction. Later use Check out & archive to complete the stay and make the room Dirty.

Speaking notes (about 30 seconds): “I will demonstrate the reservation workflow. The system checks the room and stay dates before saving. A second booking for the same room and dates is rejected. A clean room can be checked in. The booking and simulated keycard are saved together. I reviewed this AI-assisted workflow and can show the test results.”

## 24B031804 — Test payment

1. Use a different Confirmed demo booking. Click Cost to show two nights × 15,000 + 1,000 fee = 31,000 KZT if its nightly rate is 15,000.
2. Enter its ID in Test payments, choose Simulated decline, and submit. Show the DECLINED record and error message.
3. Choose Simulated success and submit. Show the persisted SUCCEEDED record.
4. Submit the identical form once more: it returns the same payment, with no second successful record.

Speaking notes (about 25 seconds): “This payment is only a simulation. It does not ask for real card details. The amount comes from the saved booking cost. I can show a declined result and a successful result in PostgreSQL. An identical retry returns the existing record. I am demonstrating and reviewing the AI-assisted payment flow.”

## 24B032049 — Refund and real frontend connection

1. Cancel the paid Confirmed booking from the previous section.
2. Enter its ID, a positive amount below its paid total and a reason in Refunds. Submit and show the COMPLETED database record.
3. Submit the unchanged form again: the refund ID is unchanged.
4. Request an amount above the remaining balance: show the error. In Housekeeping, change a demo room to Cleaning, then Cleaned; refresh/reconnect to show persistence.

Speaking notes (about 25 seconds): “The forms now send requests to the backend. I cancel a paid booking and request a test refund. The saved refund appears in the table. If the amount is too large, the server returns an error and the page displays it. I checked these frontend connections and am demonstrating the AI-assisted integration.”

## 24B031814 — Cache and response security

Run from the repository root with the isolated database running:

```sh
DEBUG=false ./backend/mvnw -f backend/pom.xml \
  -Dtest=HotelBookingApplicationTest#roomCacheHitsAndInvalidatesAfterUpdateCreateAndDelete+cacheEvictionWaitsForCommitAndRollbackKeepsData test
```

Show both executed test names and zero failures. Then show that unauthenticated access fails:

```sh
curl -i http://127.0.0.1:18080/api/guests
curl -s -u staff:demo-password http://127.0.0.1:18080/api/guests
```

The first returns 401; the second returns IDs and names without passport or phone. Explain that staff authentication controls access and DTOs limit output.

Speaking notes (about 30 seconds): “The room list uses Caffeine caching. The test checks a cache hit, changes data, and confirms that the next result is correct. A rolled-back change does not replace committed data. The API also uses response DTOs to hide passport and phone fields. DTOs limit the response; staff authentication controls access. I reviewed and tested these protections.”

## 24B031718 — Database joins, pricing and reviews

1. After a real demo check-in and Check out & archive, submit a review with rating 5 for that completed booking. An active or cancelled booking cannot receive a verified review.
2. Show the hotel/room/cost/payment/review joins and constraints:

```sh
docker exec -i hotel-stage0-verification psql -U stage0 -d stage0_demo \
  < backend/database/stage0-demo-queries.sql
```

3. Point out the composite room uniqueness, review rating check, payment/refund/booking foreign keys, GiST date constraint and the unchanged 001 checksum. Explain that legacy unknown dates remain preserved, not silently invented.

Speaking notes (about 30 seconds): “Rooms now belong to hotels, so different hotels can use the same room number. The cost table stores nights, the booked rate, a fee and the total. Reviews are linked to completed stays and ratings must be from one to five. These joins show the connected records. I reviewed the AI-assisted schema and its migration evidence.”

## 24B031880 — Executed QA evidence

Run from the repository root:

```sh
DEBUG=false ./backend/mvnw -f backend/pom.xml verify
npm ci --prefix frontend
npm test --prefix frontend
npm run test:e2e --prefix frontend
```

The browser test requires local Chrome. It creates new synthetic fixtures in `stage0_demo` and verifies them with SQL. For Chromium instead, see frontend/README.md. Show `docs/STAGE0_TEST_REPORT.md`, the 28 JUnit cases, 5 frontend tests, and the 10 asserted browser checkpoints. Browser artifacts appear under `frontend/test-results/`. A deliberately injected keycard failure logs an expected server error; that test passes only if the booking state rolls back.

Speaking notes (about 25 seconds): “The test report lists the expected and actual results. We executed backend tests with PostgreSQL, frontend request tests and a real browser scenario. The booking race has one winner, and an injected keycard error rolls back check-in. I can show the saved evidence. These results describe executed tests, not every possible production situation.”

## Stop and restart

Stop the demonstration backend with Ctrl+C in its terminal. `docker stop hotel-stage0-verification` stops only the dedicated test container and discards its tmpfs data; the startup script can initialize it again. Leave the existing Compose service and named volumes alone. Never use `docker compose down -v`, prune or database resets for this demonstration.
