// All hotel data comes from authenticated, same-origin backend requests.
let authorization = '';
let rooms = [];
let hotels = [];

export async function request(path, { method = 'GET', body, fetchImpl = globalThis.fetch } = {}) {
  const response = await fetchImpl(`/api${path}`, {
    method,
    headers: { Accept: 'application/json', ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...(authorization ? { Authorization: authorization } : {}) },
    ...(body === undefined ? {} : { body: JSON.stringify(body) })
  });
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    throw new Error(problem.detail || problem.message || `Backend request failed (${response.status})`);
  }
  return response.status === 204 ? null : response.json();
}

const byId = id => document.getElementById(id);
function message(id, text, error = false) {
  const element = byId(id); element.textContent = text; element.className = `msg ${error ? 'error' : 'success'}`;
}
function cell(row, text) { const td = document.createElement('td'); td.textContent = text ?? '—'; row.append(td); return td; }
function button(parent, label, action) { const btn = document.createElement('button'); btn.type = 'button'; btn.textContent = label; btn.addEventListener('click', action); parent.append(btn); }
function rows(id, data, render) {
  const body = byId(id); body.replaceChildren();
  for (const item of data) { const row = document.createElement('tr'); render(row, item); body.append(row); }
  if (!data.length) { const row = document.createElement('tr'); cell(row, 'No records'); body.append(row); }
}
function options(id, items, label, value) {
  const select = byId(id), previous = select.value; select.replaceChildren();
  for (const item of items) { const option = document.createElement('option'); option.value = value(item); option.textContent = label(item); select.append(option); }
  if ([...select.options].some(o => o.value === previous)) select.value = previous;
}
async function refresh() {
  // Fetch everything before replacing tables, so a failure cannot render mock or partial success.
  const [roomData, hotelData, guests, bookings, queue, payments, refunds, reviews] = await Promise.all([
    request('/rooms'), request('/hotels'), request('/guests'), request('/bookings'),
    request('/bookings/priority-queue'), request('/payments'), request('/refunds'), request('/reviews')
  ]);
  rooms = roomData; hotels = hotelData;
  options('guestSelect', guests, g => `${g.guestId}: ${g.firstName} ${g.lastName}`, g => g.guestId);
  options('roomSelect', rooms, r => `${r.hotelName} / ${r.roomNumber} (${r.roomId})`, r => r.roomId);
  rows('roomsTbody', rooms, (row, r) => {
    cell(row, `${r.hotelName} / ${r.roomNumber}`); cell(row, r.roomType); cell(row, r.pricePerNight);
    const select = document.createElement('select'); select.setAttribute('aria-label', `Cleaning state for room ${r.roomId}`);
    for (const state of ['CLEANED', 'CLEANING', 'DIRTY']) { const opt = document.createElement('option'); opt.value = state; opt.textContent = state; opt.selected = state === r.cleaningState; select.append(opt); }
    select.addEventListener('change', () => perform('roomMsg', select, async () => {
      await request(`/rooms/${r.roomId}`, { method: 'PATCH', body: { cleaningState: select.value } }); return 'Cleaning state saved in the database.';
    }).finally(() => { select.value = rooms.find(room => room.roomId === r.roomId)?.cleaningState ?? r.cleaningState; }));
    cell(row, '').append(select);
  });
  rows('bookingsTbody', bookings, (row, b) => {
    cell(row, b.bookingId); cell(row, b.guestName); cell(row, `${b.hotelId} / ${b.roomNumber}`);
    cell(row, b.checkInDate ? `${b.checkInDate} → ${b.checkOutDate}` : 'Legacy dates unknown'); cell(row, b.state);
    const actions = cell(row, '');
    button(actions, 'Cost', async () => { try { const c = await request(`/bookings/${b.bookingId}/cost`); message('bookingMsg', `${c.nights} nights × ${c.nightlyRate} + ${c.serviceFee} fee = ${c.total} ${c.currency}${c.estimatedLegacy ? ' (legacy estimate)' : ''}`); } catch (e) { message('bookingMsg', e.message, true); } });
    if (b.state === 'CONFIRMED') button(actions, 'Cancel', e => perform('bookingMsg', e.currentTarget, async () => { await request(`/bookings/${b.bookingId}/cancel`, { method: 'POST' }); return `Booking ${b.bookingId} cancelled.`; }));
    if (['CHECKEDIN', 'CANCELLED'].includes(b.state)) button(actions, b.state === 'CHECKEDIN' ? 'Check out & archive' : 'Archive', e => perform('bookingMsg', e.currentTarget, async () => { await request(`/archive/bookings/${b.bookingId}`, { method: 'POST' }); return `Booking ${b.bookingId} archived; issued keycards revoked.`; }));
  });
  rows('queueTbody', queue, (row, b) => {
    cell(row, b.bookingId); cell(row, b.guestName); cell(row, `${b.hotelId} / ${b.roomNumber}`); cell(row, b.expectedArrival);
    button(cell(row, ''), 'Check in', e => perform('bookingMsg', e.currentTarget, async () => { await request(`/bookings/${b.bookingId}/check-in`, { method: 'POST' }); return `Booking ${b.bookingId} checked in; simulated keycard record issued.`; }));
  });
  rows('paymentsTbody', payments, (row, p) => { cell(row, p.paymentId); cell(row, p.bookingId); cell(row, p.amount); cell(row, p.state); });
  rows('refundsTbody', refunds, (row, r) => { cell(row, r.refundId); cell(row, r.bookingId); cell(row, r.paymentId); cell(row, r.amount); cell(row, r.state); });
  byId('reviewsList').replaceChildren();
  for (const r of reviews) { const li = document.createElement('li'); li.textContent = `Booking ${r.bookingId}: ${r.rating}/5 — ${r.comment}`; byId('reviewsList').append(li); }
}
async function perform(msg, control, action) {
  control.disabled = true; message(msg, 'Submitting…');
  try {
    const result = await action();
    try { await refresh(); message(msg, typeof result === 'string' ? result : result.message, Boolean(result.error)); }
    catch (e) { message(msg, `${typeof result === 'string' ? result : result.message} Refresh failed: ${e.message}. Reconnect to reload records.`, true); }
  } catch (e) { message(msg, e.message, true); }
  finally { control.disabled = false; }
}
function fields(form) { return Object.fromEntries(new FormData(form)); }
// Preserve the key for an identical retry (including an uncertain network result).
// A changed payload is a new request, and a successful explicit resubmit remains a replay.
export function retryBody(form, body, createKey = () => crypto.randomUUID()) {
  const signature = JSON.stringify(body);
  if (form.dataset.signature !== signature) { form.dataset.signature = signature; form.dataset.requestKey = createKey(); }
  return { ...body, idempotencyKey: form.dataset.requestKey };
}
function formAction(id, msg, action) {
  const form = byId(id); form.addEventListener('submit', event => { event.preventDefault(); perform(msg, form.querySelector('button'), () => action(form, fields(form))); });
}
// Convert a date/time in the hotel's IANA zone to an offset-bearing timestamp.
export function hotelArrival(date, time, timezone) {
  const target = Date.parse(`${date}T${time}:00Z`);
  let instant = target;
  const fmt = new Intl.DateTimeFormat('en-CA', { timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' });
  for (let n = 0; n < 3; n++) {
    const parts = Object.fromEntries(fmt.formatToParts(instant).map(p => [p.type, p.value]));
    const shown = Date.parse(`${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}:${parts.second}Z`);
    instant += target - shown;
  }
  return new Date(instant).toISOString();
}
if (typeof document !== 'undefined') {
  formAction('loginForm', 'connectionMsg', async (form, f) => {
    authorization = `Basic ${btoa(unescape(encodeURIComponent(`${f.username}:${f.password}`)))}`;
    await request('/rooms'); return 'Connected to the backend. Live database records loaded.';
  });
  byId('logoutBtn').addEventListener('click', () => {
    authorization = ''; byId('loginForm').reset();
    for (const id of ['roomsTbody', 'bookingsTbody', 'queueTbody', 'paymentsTbody', 'refundsTbody', 'reviewsList', 'guestSelect', 'roomSelect']) byId(id).replaceChildren();
    rooms = []; hotels = []; message('connectionMsg', 'Disconnected.');
  });
  formAction('guestForm', 'guestMsg', async (_, f) => { const g = await request('/guests', { method: 'POST', body: f }); return `Guest ${g.guestId} registered.`; });
  formAction('bookingForm', 'bookingMsg', async (_, f) => {
    const room = rooms.find(r => r.roomId === Number(f.roomId));
    if (!room) throw new Error('Connect and select a room first.');
    const hotel = hotels.find(h => h.hotelId === room.hotelId);
    const b = await request('/bookings', { method: 'POST', body: { guestId: Number(f.guestId), roomId: Number(f.roomId), checkInDate: f.checkInDate, checkOutDate: f.checkOutDate, expectedArrival: hotelArrival(f.checkInDate, f.arrivalTime, hotel.timezone) } });
    return `Booking ${b.bookingId} created and cost saved.`;
  });
  byId('availabilityBtn').addEventListener('click', async () => {
    const f = fields(byId('bookingForm'));
    try { const a = await request(`/bookings/availability?${new URLSearchParams({ roomId: f.roomId, checkInDate: f.checkInDate, checkOutDate: f.checkOutDate })}`); message('bookingMsg', a.available ? 'Room is available for these dates. Availability is checked again when booking.' : 'Room is already booked for these dates.', !a.available); }
    catch (e) { message('bookingMsg', e.message, true); }
  });
  formAction('paymentForm', 'paymentMsg', async (form, f) => {
    const p = await request('/payments', { method: 'POST', body: retryBody(form, { bookingId: Number(f.bookingId), simulateDecline: f.outcome === 'decline' }) });
    if (p.state !== 'SUCCEEDED') return { message: `Test payment ${p.paymentId} was DECLINED. No successful payment was recorded.`, error: true };
    return `Test payment ${p.paymentId} saved: ${p.amount} ${p.currency}.`;
  });
  formAction('refundForm', 'refundMsg', async (form, f) => {
    const r = await request('/refunds', { method: 'POST', body: retryBody(form, { bookingId: Number(f.bookingId), amount: Number(f.amount), refundReason: f.refundReason }) }); return `Refund ${r.refundId} saved: ${r.state}.`;
  });
  formAction('reviewForm', 'reviewMsg', async (_, f) => { const r = await request('/reviews', { method: 'POST', body: { bookingId: Number(f.bookingId), rating: Number(f.rating), comment: f.comment } }); return `Verified stay review ${r.reviewId} saved.`; });
}
