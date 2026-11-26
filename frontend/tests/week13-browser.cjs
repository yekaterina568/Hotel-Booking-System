const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const { chromium } = require('playwright');

const front = process.env.WEEK13_FRONTEND_URL || 'http://127.0.0.1:3000';
const evidence = process.env.WEEK13_EVIDENCE_DIR || path.resolve(__dirname, '../../docs/week13/evidence');
const username = process.env.HOTEL_AUTH_USERNAME;
const password = process.env.HOTEL_AUTH_PASSWORD;
const viewport = { width: 1440, height: 1000 };
const browserContext = { viewport, locale: 'en-US', timezoneId: 'Asia/Almaty' };

async function hasText(locator, expected) {
  await locator.filter({ hasText: expected }).first().waitFor({ state: 'visible' });
}

async function login(page, suppliedPassword) {
  await page.getByLabel('Username', { exact: true }).fill(username);
  await page.getByLabel('Password', { exact: true }).fill(suppliedPassword);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
}

function nav(page, label) {
  return page.getByRole('link', { name: label, exact: true });
}

async function screenshot(page, filename) {
  await page.screenshot({ path: path.join(evidence, filename), animations: 'disabled' });
}

function dateAfter(offset) {
  const day = new Date(Date.now() + offset * 86400000);
  return day.toISOString().slice(0, 10);
}

async function main() {
  assert.ok(username && password, 'Provide the credentials for the dedicated week13_demo backend.');
  await fs.mkdir(evidence, { recursive: true });
  const browser = await chromium.launch(process.env.WEEK13_CHROME_CHANNEL
    ? { channel: process.env.WEEK13_CHROME_CHANNEL } : {});
  console.log('Chromium ' + browser.version());
  const uncaught = [];
  function watch(page) {
    page.on('pageerror', error => { uncaught.push(error.message); console.error('Page error:', error.message); });
    return page;
  }

  try {
    const context = await browser.newContext(browserContext);
    const page = watch(await context.newPage());
    await page.goto(front + '/admin.html');
    await page.waitForFunction(() => typeof restoreSession === 'function');
    await login(page, 'invalid-local-test-password');
    await hasText(page.locator('#signInFeedback'), 'incorrect');
    await login(page, password);
    await page.getByRole('heading', { name: 'Dashboard', exact: true }).waitFor();
    await page.locator('#dashboardCharts canvas').nth(5).waitFor();
    assert.equal(await page.locator('#dashboardCharts canvas').count(), 6);
    const charts = await page.locator('#dashboardCharts canvas').evaluateAll(canvases =>
      canvases.map(canvas => ({
        width: canvas.width,
        type: window.Chart.getChart(canvas).config.type,
        data: window.Chart.getChart(canvas).data.datasets[0].data
      })));
    assert.deepEqual(charts.map(chart => chart.type), ['line', 'doughnut', 'bar', 'bar', 'bar', 'line']);
    assert.ok(charts.every(chart => chart.width > 0 && chart.data.some(value => value > 0)));
    assert.equal(await page.locator('#dashboardHotel option').count(), 4, 'Use the dedicated seeded demo database');
    await screenshot(page, 'dashboard.png');
    await page.locator('#dashboardCharts').screenshot({ path: path.join(evidence, 'statistics.png'), animations: 'disabled' });
    console.log('PASS Admin rejected the wrong password, opened Dashboard, and rendered all six charts.');

    await page.locator('#dashboardHotel').selectOption({ label: 'Riverside Hotel (Demo)' });
    await page.locator('#dashboardPeriod').selectOption('ALL_TIME');
    await page.getByRole('button', { name: 'Apply filters' }).click();
    await hasText(page.locator('#activityDates'), 'All time');
    assert.equal(await page.locator('[data-metric="hotels"] strong').innerText(), '1');
    const scopeRooms = await page.locator('[data-metric="rooms"] strong').innerText();
    const scopeBookings = Number(await page.locator('[data-metric="bookings"] strong').innerText());
    assert.ok(scopeBookings > 0);
    await page.locator('#dashboardPeriod').selectOption('LAST_7_DAYS');
    await page.getByRole('button', { name: 'Apply filters' }).click();
    await hasText(page.locator('#activityDates'), 'through');
    assert.ok(Number(await page.locator('[data-metric="bookings"] strong').innerText()) < scopeBookings);
    await page.locator('#dashboardPeriod').selectOption('CUSTOM');
    await page.locator('#dashboardFrom').fill('2000-01-01');
    await page.locator('#dashboardTo').fill('2000-01-02');
    await page.getByRole('button', { name: 'Apply filters' }).click();
    await hasText(page.locator('#activityDates'), '2000-01-01 through 2000-01-02');
    assert.equal(await page.locator('[data-metric="bookings"] strong').innerText(), '0');
    assert.equal(await page.locator('[data-metric="rooms"] strong').innerText(), scopeRooms);
    await page.locator('#dashboardFrom').fill('2000-01-03');
    await page.getByRole('button', { name: 'Apply filters' }).click();
    await hasText(page.locator('#overviewFeedback'), 'start date must');
    await page.locator('#dashboardPeriod').selectOption('LAST_30_DAYS');
    await page.route('**/api/admin/dashboard?*', route => route.abort(), { times: 1 });
    await page.getByRole('button', { name: 'Apply filters' }).click();
    await hasText(page.locator('#overviewFeedback'), 'Could not reach the backend');
    await page.getByRole('button', { name: 'Refresh' }).click();
    await page.locator('#overviewData').waitFor();
    console.log('PASS Hotel, last-seven-days, all-time, empty and invalid custom filters and backend recovery behaved as expected.');

    await nav(page, 'Reports').click();
    await page.locator('#reportHotel').selectOption({ label: 'Riverside Hotel (Demo)' });
    await page.locator('#reportStatus').selectOption('CANCELLED');
    await page.getByRole('button', { name: 'Generate preview' }).click();
    await page.locator('#reportPreview').waitFor();
    const reports = await page.locator('#reportRows tr').allTextContents();
    assert.ok(reports.length > 0 && reports.every(row => row.includes('Riverside') && row.includes('CANCELLED')));
    assert.ok(!(await page.locator('#reportColumns').innerText()).includes('Passport'));
    await screenshot(page, 'reports.png');

    const downloadPromise = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Download CSV' }).click();
    const download = await downloadPromise;
    assert.equal(download.suggestedFilename(), 'bookings-report.csv');
    const csv = await fs.readFile(await download.path(), 'utf8');
    assert.ok(csv.startsWith('\ufeff"Booking ID","Hotel"'));
    assert.equal(csv.trimEnd().split('\r\n').length, reports.length + 1);
    assert.ok(csv.split('\r\n').slice(1, -1).every(row => row.includes('"CANCELLED"') && row.includes('Riverside')));
    const printablePromise = page.waitForEvent('popup');
    await page.getByRole('button', { name: 'Print / Save PDF' }).click();
    const printable = await printablePromise;
    await printable.waitForURL('blob:**');
    await printable.getByRole('heading', { name: 'Booking report' }).waitFor();
    assert.equal(await printable.locator('tbody tr').count(), reports.length);
    await printable.close();
    console.log('PASS Filtered report preview, CSV download, and printable HTML returned the same matching records.');

    await nav(page, 'Rooms').click();
    const roomNumber = String(920000 + Date.now() % 75000);
    await page.getByRole('button', { name: 'Add room' }).click();
    let dialog = page.getByRole('dialog', { name: 'Add room' });
    await dialog.getByLabel('Hotel', { exact: true }).selectOption({ label: 'Riverside Hotel (Demo)' });
    await dialog.getByLabel('Room number').fill(roomNumber);
    await dialog.getByLabel('Room type').selectOption('DOUBLE');
    await dialog.getByLabel('Price per night').fill('170.50');
    const roomCreated = page.waitForResponse(response => response.url().endsWith('/api/admin/rooms')
      && response.request().method() === 'POST');
    await dialog.getByRole('button', { name: 'Save room' }).click();
    const createdResponse = await roomCreated;
    assert.equal(createdResponse.status(), 201);
    const room = await createdResponse.json();
    await hasText(page.locator('#dashboardFeedback'), 'Room added.');
    await page.getByRole('searchbox', { name: 'Search rooms' }).fill(roomNumber);
    let roomRow = page.locator('#operationRows tr').filter({ hasText: roomNumber });
    await roomRow.getByRole('button', { name: 'Edit' }).click();
    dialog = page.getByRole('dialog', { name: 'Edit room' });
    await dialog.getByLabel('Hotel', { exact: true }).selectOption({ label: 'Summit Hotel (Demo)' });
    await dialog.getByLabel('Room type').selectOption('SUITE');
    await dialog.getByLabel('Cleaning status').selectOption('CLEANED');
    await dialog.getByLabel('Price per night').fill('199.75');
    await dialog.getByRole('button', { name: 'Save room' }).click();
    await hasText(page.locator('#dashboardFeedback'), 'Room updated.');
    await page.reload();
    await hasText(page.locator('#operationResults'), 'rooms');
    await page.getByRole('searchbox', { name: 'Search rooms' }).fill(roomNumber);
    roomRow = page.locator('#operationRows tr').filter({ hasText: roomNumber });
    await hasText(roomRow, 'Summit Hotel (Demo)');
    await hasText(roomRow, '199.75');
    await roomRow.getByRole('button', { name: 'Edit' }).click();
    await screenshot(page, 'room-management.png');
    await page.getByRole('dialog', { name: 'Edit room' }).getByRole('button', { name: 'Cancel' }).click();
    console.log('PASS Room ' + room.roomId + ' (' + roomNumber + ') was created, moved, repriced, and confirmed after reload.');

    await nav(page, '← Front desk').click();
    await nav(page, 'New Booking').click();
    await page.getByLabel('Guest Passport').fill('W13DEMO001');
    await page.getByLabel('Check-in (arrival)').fill(dateAfter(9) + 'T14:00');
    await page.getByLabel('Check-out (departure)').fill(dateAfter(11) + 'T11:00');
    const available = page.getByLabel('Free room for these dates');
    const hotelRoomOption = available.locator('option[value="' + room.roomId + '"]');
    await hotelRoomOption.waitFor({ state: 'attached' });
    assert.match(await hotelRoomOption.textContent(), /Hotel #/);
    await available.selectOption(String(room.roomId));
    await page.getByRole('button', { name: 'Create Booking' }).click();
    await hasText(page.locator('#bookingMsg'), /Booking #\d+ created/);
    const bookingId = (await page.locator('#bookingMsg').innerText()).match(/Booking #(\d+)/)[1];
    await nav(page, 'Departures & Check-out').click();
    await page.locator('#checkoutTbody tr').filter({ hasText: 'Check-out' }).first().waitFor();
    await nav(page, 'Administration').click();
    await nav(page, 'Bookings').click();
    await page.getByRole('searchbox', { name: 'Search bookings' }).fill(roomNumber);
    await page.locator('#operationStatus').selectOption('CONFIRMED');
    const booking = page.locator('#operationRows tr').filter({ hasText: roomNumber });
    await booking.getByRole('button', { name: 'View details' }).click();
    await hasText(page.locator('#bookingDetailValues'), 'Summit Hotel (Demo)');
    await page.getByRole('dialog', { name: 'Booking #' + bookingId }).getByRole('button', { name: 'Close' }).click();
    await booking.getByRole('button', { name: 'Cancel booking' }).click();
    await page.getByRole('dialog', { name: 'Cancel booking?' }).getByRole('button', { name: 'Cancel booking' }).click();
    await hasText(page.locator('#dashboardFeedback'), 'Booking cancelled.');
    await page.locator('#operationStatus').selectOption('CANCELLED');
    await booking.getByRole('button', { name: 'Archive booking' }).click();
    await page.getByRole('dialog', { name: 'Archive booking?' }).getByRole('button', { name: 'Archive booking' }).click();
    await hasText(page.locator('#dashboardFeedback'), 'Booking archived.');
    await page.locator('#operationStatus').selectOption('ARCHIVED');
    await booking.getByRole('button', { name: 'View details' }).click();
    await hasText(page.locator('#bookingHistory'), 'Confirmed → Cancelled');
    await hasText(page.locator('#bookingHistory'), 'Cancelled → Archived');
    await page.getByRole('dialog', { name: 'Booking #' + bookingId }).getByRole('button', { name: 'Close' }).click();
    await nav(page, 'Booking Archive').click();
    await hasText(page.locator('#operationRows'), '#' + bookingId);
    console.log('PASS Front-desk reservation #' + bookingId + ', admin cancellation, archive, and history persisted.');

    await nav(page, 'Hotels').click();
    await hasText(page.locator('#hotelRows'), 'Grand Almaty');
    await page.getByRole('button', { name: 'Add hotel' }).click();
    const hotelDialog = page.getByRole('dialog', { name: 'Add hotel' });
    await hotelDialog.getByLabel('Name', { exact: true }).fill('Browser test property');
    await hotelDialog.getByLabel('City', { exact: true }).fill('Almaty');
    await hotelDialog.getByRole('button', { name: 'Save hotel' }).click();
    await hasText(page.locator('#dashboardFeedback'), 'Hotel added.');
    const hotelRow = page.locator('#hotelRows tr').filter({ hasText: 'Browser test property' });
    await hotelRow.getByRole('button', { name: 'Delete' }).click();
    await page.getByRole('dialog', { name: 'Delete hotel?' }).getByRole('button', { name: 'Delete', exact: true }).click();
    await hasText(page.locator('#dashboardFeedback'), 'Hotel deleted.');
    await page.locator('a[href="#reviews"]').click();
    await page.getByRole('heading', { name: 'Guest reviews' }).waitFor();
    await page.getByRole('button', { name: 'Add review' }).click();
    const reviewDialog = page.getByRole('dialog', { name: 'Add review' });
    await reviewDialog.getByLabel('Hotel', { exact: true }).selectOption({ label: 'Summit Hotel (Demo)' });
    await reviewDialog.getByLabel('Author name').fill('Browser test reviewer');
    await reviewDialog.getByLabel('Rating').selectOption('5');
    await reviewDialog.getByLabel('Review text').fill('Synthetic browser moderation check.');
    await reviewDialog.getByRole('button', { name: 'Save to pending' }).click();
    await hasText(page.locator('#dashboardFeedback'), 'moderation queue');
    const reviewRow = page.locator('#reviewRows tr').filter({ hasText: 'Browser test reviewer' });
    await reviewRow.locator('select').selectOption('PUBLISHED');
    await hasText(page.locator('#dashboardFeedback'), 'changed to published.');
    await page.locator('#statusFilter').selectOption('PUBLISHED');
    await reviewRow.locator('select').selectOption('HIDDEN');
    await hasText(page.locator('#dashboardFeedback'), 'changed to hidden.');
    await page.locator('#statusFilter').selectOption('HIDDEN');
    await reviewRow.getByRole('button', { name: 'Delete' }).click();
    await page.getByRole('dialog', { name: 'Delete review?' }).getByRole('button', { name: 'Delete', exact: true }).click();
    await hasText(page.locator('#dashboardFeedback'), 'Review deleted.');
    console.log('PASS Existing hotel add/delete and pending/published/hidden moderation worked.');

    await page.getByRole('button', { name: 'Sign out' }).click();
    await page.getByRole('heading', { name: 'Admin sign-in' }).waitFor();
    assert.equal(await page.locator('#adminNav').isVisible(), false);

    const mobileContext = await browser.newContext({ ...browserContext, viewport: { width: 390, height: 844 },
      deviceScaleFactor: 1, isMobile: true, hasTouch: true });
    const mobile = watch(await mobileContext.newPage());
    await mobile.goto(front + '/admin.html');
    await login(mobile, password);
    await mobile.locator('#dashboardCharts canvas').first().waitFor();
    assert.equal(await mobile.locator('#dashboardCharts').evaluate(chartGrid =>
      getComputedStyle(chartGrid).gridTemplateColumns.split(' ').length), 1);
    const fits = () => mobile.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth);
    assert.equal(await fits(), true);
    await screenshot(mobile, 'dashboard-mobile.png');
    await nav(mobile, 'Reports').click();
    await mobile.getByRole('button', { name: 'Generate preview' }).click();
    await mobile.locator('#reportPreview').waitFor();
    assert.equal(await fits(), true);
    await mobileContext.close();
    assert.deepEqual(uncaught, []);
    console.log('PASS Sign-out, 390px mobile dashboard and reports, and zero uncaught JavaScript errors.');
  } finally {
    await browser.close();
  }
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
