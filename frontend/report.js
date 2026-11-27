// Отчёт по платежам за период: GET /api/payments/report?from=YYYY-MM-DD&to=YYYY-MM-DD
(function () {
  const API = 'http://localhost:8080/api';
  const money = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
  const esc = v => String(v ?? '').replace(/[&<>"']/g, c => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const METHOD_NAMES = { CARD: 'Card', CASH: 'Cash', TRANSFER: 'Bank transfer' };

  const form = document.getElementById('reportForm');
  const fromInput = document.getElementById('reportFrom');
  const toInput = document.getElementById('reportTo');
  const errorEl = document.getElementById('error');
  const reportEl = document.getElementById('report');

  function fail(text) {
    errorEl.textContent = text;
    errorEl.style.display = 'block';
    reportEl.style.display = 'none';
  }

  async function load() {
    errorEl.style.display = 'none';
    if (!hotelAuth.getCredentials()) {
      fail('Сначала войдите в систему на главной странице портала (index.html), затем откройте отчёт снова.');
      return;
    }
    const params = new URLSearchParams();
    if (fromInput.value) params.set('from', fromInput.value);
    if (toInput.value) params.set('to', toInput.value);
    history.replaceState(null, '', params.toString() ? `?${params}` : location.pathname);

    let response;
    try {
      response = await hotelAuth.fetch(`${API}/payments/report${params.toString() ? `?${params}` : ''}`);
    } catch (_) {
      fail('Нет связи с сервером (backend на localhost:8080 не запущен?)');
      return;
    }
    if (response.status === 401) {
      fail('Сессия истекла. Войдите в систему на главной странице портала и откройте отчёт снова.');
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
    fromInput.value = r.from;
    toInput.value = r.to;
    const rows = (r.byMethod || []).map(m => `
      <tr>
        <td>${esc(METHOD_NAMES[m.method] || m.method)}</td>
        <td class="num">${esc(m.count)}</td>
        <td class="num">${money.format(m.total)}</td>
      </tr>`).join('');
    reportEl.innerHTML = `
      <h1>Grand Almaty — Payments report</h1>
      <div class="sub">${esc(r.from)} → ${esc(r.to)} · ${esc(r.bookingsCount)} booking(s)</div>
      <div class="tiles">
        <div class="tile"><div class="label">Collected</div><div class="value">${money.format(r.totalPaid)}</div><div class="note">${esc(r.paymentsCount)} payment(s)</div></div>
        <div class="tile refund"><div class="label">Refunded</div><div class="value">−${money.format(r.totalRefunded)}</div><div class="note">${esc(r.refundsCount)} refund(s)</div></div>
        <div class="tile net"><div class="label">Net revenue</div><div class="value">${money.format(r.netRevenue)}</div><div class="note">collected − refunded</div></div>
      </div>
      <h2>By payment method</h2>
      <table>
        <thead><tr><th>Method</th><th class="num">Payments</th><th class="num">Total</th></tr></thead>
        <tbody>${rows}</tbody>
      </table>
      <div class="foot">Generated ${esc(new Date().toLocaleString())}</div>`;
    reportEl.style.display = 'block';
    document.title = `Payments ${r.from} – ${r.to}`;
  }

  const initial = new URLSearchParams(location.search);
  if (initial.get('from')) fromInput.value = initial.get('from');
  if (initial.get('to')) toInput.value = initial.get('to');
  form.addEventListener('submit', e => { e.preventDefault(); load(); });
  load();
})();
