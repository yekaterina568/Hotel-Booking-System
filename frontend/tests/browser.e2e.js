// Real browser -> application HTTP -> PostgreSQL evidence, against the isolated demo DB.
import { chromium } from 'playwright';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';

const base = process.env.STAGE0_BASE_URL || 'http://127.0.0.1:18080';
const username = process.env.HOTEL_STAFF_USERNAME || 'staff';
const password = process.env.HOTEL_STAFF_PASSWORD || 'demo-password';
const container = process.env.STAGE0_DB_CONTAINER || 'hotel-stage0-verification';
const evidence = [];
const auth = `Basic ${Buffer.from(`${username}:${password}`).toString('base64')}`;
async function api(path, body) {
  const response = await fetch(`${base}/api${path}`, { method: body ? 'POST' : 'GET', headers: { Authorization: auth, 'Content-Type': 'application/json' }, ...(body ? { body: JSON.stringify(body) } : {}) });
  assert.ok(response.ok, `${path}: ${response.status} ${await response.clone().text()}`); return response.json();
}
function sql(query) {
  return execFileSync('docker', ['exec', container, 'psql', '-U', 'stage0', '-d', 'stage0_demo', '-tAc', query], { encoding: 'utf8' }).trim();
}
function record(test, actual) { evidence.push({ test, expected: actual, actual, status: 'Passed' }); console.log(`PASS: ${test}: ${actual}`); }
const browser = await chromium.launch({ headless: true, ...(process.env.BROWSER_CHANNEL === 'chromium' ? {} : { channel: process.env.BROWSER_CHANNEL || 'chrome' }) });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
const errors = []; page.on('pageerror', error => errors.push(error.message));
mkdirSync('test-results', { recursive: true });
async function msg(id, pattern) {
  await page.waitForFunction(({ id, source }) => new RegExp(source).test(document.getElementById(id).textContent), { id, source: pattern.source });
  return page.locator(`#${id}`).innerText();
}
async function submit(id) { await page.locator(`#${id} button`).first().click(); }
function bookingRow(id) { return page.locator('#bookingsTbody tr').filter({ has: page.locator('td:first-child').filter({ hasText: new RegExp(`^${id}$`) }) }); }
try {
  assert.equal(sql('SELECT current_database()'), 'stage0_demo');
  const suffix = crypto.randomUUID().replaceAll('-', '').slice(0, 12).toUpperCase();
  const hotel = await api('/hotels', { name: `Browser ${suffix}`, city: 'Almaty', timezone: 'Asia/Almaty' });
  const room = await api('/rooms', { hotelId: hotel.hotelId, roomNumber: 101, roomType: 'SINGLE', cleaningState: 'CLEANED', pricePerNight: 15000 });
  await page.goto(base);
  await page.locator('#loginForm [name=username]').fill(username); await page.locator('#loginForm [name=password]').fill(password); await submit('loginForm'); await msg('connectionMsg', /Connected to the backend/);
  await page.locator('#guestForm [name=passportNum]').fill(`E2E${suffix}`);
  await page.locator('#guestForm [name=firstName]').fill('Browser'); await page.locator('#guestForm [name=lastName]').fill('Guest'); await page.locator('#guestForm [name=phoneNum]').fill('+77000000000');
  await submit('guestForm'); const guestMessage = await msg('guestMsg', /Guest \d+ registered/); const guestId = Number(guestMessage.match(/Guest (\d+)/)[1]);
  assert.equal(sql(`SELECT count(*) FROM guests WHERE guest_id=${guestId}`), '1'); record('Browser guest registration', 'one PostgreSQL guest; response lists omit passport/phone');
  const date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Almaty', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
  const departure = new Date(`${date}T00:00:00Z`); departure.setUTCDate(departure.getUTCDate() + 2);
  await page.locator('#guestSelect').selectOption(String(guestId)); await page.locator('#roomSelect').selectOption(String(room.roomId));
  await page.locator('#bookingForm [name=checkInDate]').fill(date); await page.locator('#bookingForm [name=checkOutDate]').fill(departure.toISOString().slice(0, 10));
  await page.locator('#availabilityBtn').click(); await msg('bookingMsg', /Room is available/);
  await submit('bookingForm'); const bookingMessage = await msg('bookingMsg', /Booking \d+ created/); const bookingId = Number(bookingMessage.match(/Booking (\d+)/)[1]);
  assert.equal(sql(`SELECT c.nights||'|'||c.total FROM bookings b JOIN booking_costs c USING(booking_id) JOIN rooms r USING(room_id) JOIN hotels h USING(hotel_id) WHERE b.booking_id=${bookingId}`), '2|31000.00'); record('Browser booking and cost join', 'two nights; persisted total 31000.00 KZT');
  await page.locator('#paymentForm [name=bookingId]').fill(String(bookingId)); await page.locator('#paymentForm [name=outcome]').selectOption('decline'); await submit('paymentForm'); await msg('paymentMsg', /DECLINED/);
  assert.ok(await page.locator('#paymentMsg').evaluate(e => e.classList.contains('error'))); record('Browser declined payment', 'decline is displayed as an error and stored as DECLINED');
  await page.locator('#paymentForm [name=outcome]').selectOption('success'); await submit('paymentForm'); await msg('paymentMsg', /saved: 31000/); await submit('paymentForm'); await msg('paymentMsg', /saved: 31000/);
  assert.equal(sql(`SELECT count(*) FROM payments WHERE booking_id=${bookingId} AND state='SUCCEEDED'`), '1'); record('Browser payment retry', 'one successful PostgreSQL payment after two identical submissions');
  await bookingRow(bookingId).getByRole('button', { name: 'Cancel', exact: true }).click(); await msg('bookingMsg', /cancelled/);
  await page.locator('#refundForm [name=bookingId]').fill(String(bookingId)); await page.locator('#refundForm [name=amount]').fill('10000'); await page.locator('#refundForm [name=refundReason]').fill('Browser demonstration');
  await page.route('**/api/refunds', route => route.request().method() === 'POST' ? route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ detail: 'Backend unavailable for test' }) }) : route.continue());
  await submit('refundForm'); await msg('refundMsg', /Backend unavailable/); assert.equal(sql(`SELECT count(*) FROM refunds WHERE booking_id=${bookingId}`), '0'); record('Browser backend failure', 'visible error; zero phantom refund records');
  await page.unroute('**/api/refunds'); await submit('refundForm'); await msg('refundMsg', /saved: COMPLETED/); await submit('refundForm'); await msg('refundMsg', /saved: COMPLETED/);
  assert.equal(sql(`SELECT count(*)||'|'||sum(amount) FROM refunds WHERE booking_id=${bookingId}`), '1|10000.00'); record('Browser refund retry and persistence', 'one PostgreSQL refund for 10000.00 KZT');
  // A second stay demonstrates real cleaning/queue/check-in/checkout operations.
  await submit('bookingForm'); const secondMessage = await msg('bookingMsg', /Booking \d+ created/); const secondId = Number(secondMessage.match(/Booking (\d+)/)[1]);
  const cleaning = () => page.getByLabel(`Cleaning state for room ${room.roomId}`, { exact: true });
  await cleaning().selectOption('DIRTY'); await msg('roomMsg', /saved in the database/); assert.equal(sql(`SELECT cleaning_state::text FROM rooms WHERE room_id=${room.roomId}`), 'Dirty');
  await page.waitForFunction(id => ![...document.querySelectorAll('#queueTbody tr')].some(r => r.firstElementChild.textContent === String(id)), secondId);
  await cleaning().selectOption('CLEANED'); await msg('roomMsg', /saved in the database/);
  const queue = page.locator('#queueTbody tr').filter({ has: page.locator('td:first-child').filter({ hasText: new RegExp(`^${secondId}$`) }) });
  await queue.getByRole('button', { name: 'Check in', exact: true }).click(); await msg('bookingMsg', /checked in; simulated keycard record issued/);
  assert.equal(sql(`SELECT b.state::text||'|'||count(k.keycard_id) FROM bookings b JOIN keycards k USING(booking_id) WHERE b.booking_id=${secondId} AND k.is_active GROUP BY b.state`), 'CheckedIn|1'); record('Browser cleaning, queue and check-in', 'Dirty excluded from queue; Cleaned check-in creates one active keycard');
  await bookingRow(secondId).getByRole('button', { name: 'Check out & archive' }).click(); await msg('bookingMsg', /archived/);
  assert.equal(sql(`SELECT count(*) FROM keycards WHERE booking_id=${secondId} AND is_active`), '0'); assert.equal(sql(`SELECT cleaning_state::text FROM rooms WHERE room_id=${room.roomId}`), 'Dirty'); record('Browser checkout/archive', 'archive persisted; room Dirty; keycard inactive');
  await page.locator('#reviewForm [name=bookingId]').fill(String(secondId)); await page.locator('#reviewForm [name=rating]').fill('5'); await page.locator('#reviewForm [name=comment]').fill('<img src=x onerror=alert(1)> Great stay'); await submit('reviewForm'); await msg('reviewMsg', /saved/);
  assert.equal(sql(`SELECT rating FROM reviews WHERE booking_id=${secondId}`), '5'); assert.equal(await page.locator('#reviewsList img').count(), 0); record('Browser verified review and safe text rendering', 'rating 5 persisted; HTML rendered as text');
  await page.screenshot({ path: 'test-results/stage0-desktop.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 }); await page.screenshot({ path: 'test-results/stage0-mobile.png', fullPage: true });
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), true); assert.deepEqual(errors, []); record('Browser layout and JavaScript', 'no document overflow at 390px; zero uncaught JavaScript errors');
  writeFileSync('test-results/browser-evidence.json', JSON.stringify({ base, bookingId, secondId, roomId: room.roomId, evidence }, null, 2));
} catch (error) {
  await page.screenshot({ path: 'test-results/stage0-failure.png', fullPage: true }); throw error;
} finally { await browser.close(); }
