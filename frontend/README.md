# Hotel Booking System frontend

The interface uses the real Spring Boot APIs. Start the backend, then open
`http://127.0.0.1:8080/`. The backend serves these files from its packaged resources;
restart it after changing frontend files. Opening `index.html` with `file://` is not supported.

Connect with the staff credentials configured in the root `.env`. Credentials are
held in browser memory; Disconnect clears loaded tables. The `dev` profile uses
`staff` / `demo-password` only if `HOTEL_STAFF_PASSWORD` is absent. A value in `.env`
overrides that default. Do not enter real card or bank details.

Available actions: register a guest, check dated availability, create/cancel a booking,
view its cost, manage room cleaning, check in from the live priority queue, check out
and archive, simulate successful/declined payments, request/view refunds, and submit
verified completed-stay reviews. Every mutation waits for the backend response.
Failures display an error. Passport format validation does not verify identity.

Amounts use KZT. New booking cost is nights × the booked nightly rate + 1,000 KZT
service fee. Existing undated bookings are marked as legacy estimates and cannot be
paid or checked in until a later staff date-reconciliation workflow is provided.

## Tests

Node 22+:

```sh
cd frontend
npm ci
npm test
```

For the real browser/SQL scenario, first start the isolated database and demonstration
backend as described in [the demo guide](../docs/STAGE0_DEMO_GUIDE.md), then run:

```sh
npm run test:e2e
```

This requires local Google Chrome and Docker. Alternatively run
`npx playwright install chromium` then `BROWSER_CHANNEL=chromium npm run test:e2e`.
The browser test writes only new demo fixtures to `stage0_demo`, reads SQL through
`hotel-stage0-verification`, and saves ignored artifacts under `test-results/`.
The authentication and base URL can be overridden with `HOTEL_STAFF_USERNAME`,
`HOTEL_STAFF_PASSWORD` and `STAGE0_BASE_URL`. Use only the isolated demo database.
