// Печатная квитанция: GET /api/payments/{id}/receipt
(function () {
  const API = 'http://localhost:8080/api';
  const money = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
  const date = new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short' });
  const esc = v => String(v ?? '').replace(/[&<>"']/g, c => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const fmtDate = v => (v ? date.format(new Date(v)) : '—');

  function fail(text) {
    const el = document.getElementById('error');
    el.textContent = text;
    el.style.display = 'block';
  }

  async function load() {
    const id = Number(new URLSearchParams(location.search).get('payment'));
    if (!Number.isInteger(id) || id < 1) {
      fail('Не указан номер платежа (receipt.html?payment=ID).');
      return;
    }
    if (!hotelAuth.getCredentials()) {
      fail('Сначала войдите в систему на главной странице портала (index.html), затем откройте квитанцию снова.');
      return;
    }
    let response;
    try {
      response = await hotelAuth.fetch(`${API}/payments/${id}/receipt`);
    } catch (_) {
      fail('Нет связи с сервером (backend на localhost:8080 не запущен?)');
      return;
    }
    if (response.status === 401) {
      fail('Сессия истекла. Войдите в систему на главной странице портала и откройте квитанцию снова.');
      return;
    }
    if (!response.ok) {
      let detail = `Ошибка сервера (HTTP ${response.status})`;
      try { detail = (await response.json()).message || detail; } catch (_) { /* не JSON */ }
      fail(detail);
      return;
    }
    render(await response.json());
  }

  function render(r) {
    const refund = r.status === 'REFUNDED';
    const owes = Number(r.balance);
    const balanceText = owes > 0 ? `${money.format(owes)} due` : owes < 0 ? `${money.format(-owes)} credit` : 'Paid in full';
    const el = document.getElementById('receipt');
    el.innerHTML = `
      <h1>Grand Almaty</h1>
      <div class="sub">${refund ? 'Refund receipt' : 'Payment receipt'} · ${esc(r.receiptNumber)}</div>
      <span class="kind ${refund ? 'refund' : ''}">${esc(r.status)}</span>
      <table>
        <tr><td>Date</td><td>${esc(fmtDate(r.paidAt))}</td></tr>
        <tr><td>Transaction</td><td>${esc(r.transactionRef)}</td></tr>
        <tr><td>Payment method</td><td>${esc(r.method ?? (refund ? 'Refund to guest' : '—'))}</td></tr>
        <tr><td>Booking</td><td>#${esc(r.bookingId)}</td></tr>
        <tr><td>Guest</td><td>${esc(r.guestName)}</td></tr>
        <tr><td>Room</td><td>No. ${esc(r.roomNumber)} (${esc(r.roomType)})</td></tr>
        <tr><td>Stay</td><td>${esc(fmtDate(r.arrival))} → ${esc(fmtDate(r.departure))}</td></tr>
        <tr><td>Nights × rate</td><td>${esc(r.nights)} × ${money.format(r.pricePerNight ?? 0)} = ${money.format(r.totalDue)}</td></tr>
        <tr><td>Balance on booking</td><td>${esc(balanceText)}</td></tr>
        <tr class="total"><td>${refund ? 'Refunded' : 'Amount paid'}</td><td>${refund ? '−' : ''}${money.format(r.amount)}</td></tr>
      </table>
      <div class="foot">Thank you for staying with us.</div>`;
    el.style.display = 'block';
    document.title = `${r.receiptNumber} — Receipt`;
  }

  load();
})();
