import test from 'node:test';
import assert from 'node:assert/strict';
import { request, retryBody, hotelArrival } from '../app.js';

test('API paths and request payloads match backend contracts', async () => {
  let called;
  const result = await request('/refunds', { method: 'POST', body: { bookingId: 5, amount: 10, refundReason: 'Cancelled', idempotencyKey: 'retry-1' }, fetchImpl: async (...args) => { called = args; return { ok: true, status: 201, json: async () => ({ refundId: 7 }) }; } });
  assert.equal(called[0], '/api/refunds'); assert.equal(called[1].method, 'POST');
  assert.equal(JSON.parse(called[1].body).idempotencyKey, 'retry-1'); assert.equal(result.refundId, 7);
});
test('backend validation failure rejects instead of inventing success', async () => {
  await assert.rejects(request('/bookings/5/check-in', { method: 'POST', fetchImpl: async () => ({ ok: false, status: 409, json: async () => ({ detail: 'Room must be Cleaned before check-in' }) }) }), /Room must be Cleaned/);
});
test('network and non-JSON failures remain visible errors', async () => {
  await assert.rejects(request('/rooms', { fetchImpl: async () => { throw new Error('Backend offline'); } }), /Backend offline/);
  await assert.rejects(request('/rooms', { fetchImpl: async () => ({ ok: false, status: 503, json: async () => { throw new Error('HTML'); } }) }), /503/);
});
test('identical payment/refund retries keep the same idempotency key', () => {
  const form = { dataset: {} }; let n = 0;
  const a = retryBody(form, { bookingId: 1, amount: 10 }, () => `key-${++n}`);
  const b = retryBody(form, { bookingId: 1, amount: 10 }, () => `key-${++n}`);
  assert.equal(a.idempotencyKey, b.idempotencyKey);
  assert.notEqual(a.idempotencyKey, retryBody(form, { bookingId: 1, amount: 20 }, () => `key-${++n}`).idempotencyKey);
});
test('arrival uses hotel timezone rather than browser timezone', () => {
  assert.equal(hotelArrival('2026-11-12', '14:00', 'Asia/Almaty'), '2026-11-12T09:00:00.000Z');
  assert.equal(hotelArrival('2026-11-12', '14:00', 'UTC'), '2026-11-12T14:00:00.000Z');
});
