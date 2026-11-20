// Вкладка «Payments»: оплата брони, баланс (ночи × цена), история платежей и ссылки на квитанции.
// Работает только с настоящим API: при ошибке показывается причина, «успеха» без записи в БД не бывает.
(function () {
  const API = (typeof API_BASE_URL !== 'undefined') ? API_BASE_URL : 'http://localhost:8080/api';
  const money = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

  const form = document.getElementById('paymentForm');
  if (!form) return;
  const bookingInput = document.getElementById('paymentBookingId');
  const amountInput = document.getElementById('paymentAmount');
  const methodInput = document.getElementById('paymentMethod');
  const submitBtn = document.getElementById('paymentSubmit');
  const msg = document.getElementById('paymentMsg');
  const summary = document.getElementById('paymentBalance');
  const tbody = document.getElementById('paymentsTbody');

  function esc(value) {
    return String(value ?? '').replace(/[&<>"']/g, c => (
      { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }

  function showMsg(text, ok) {
    msg.textContent = text;
    msg.className = text ? `feedback-banner ${ok ? 'success' : 'error'}` : 'feedback-banner';
  }

  async function readError(response) {
    try {
      const body = await response.json();
      return body.message || body.detail || body.error || `Ошибка сервера (HTTP ${response.status})`;
    } catch (_) {
      return `Ошибка сервера (HTTP ${response.status})`;
    }
  }

  async function api(path, options) {
    let response;
    try {
      response = await fetch(`${API}${path}`, options);
    } catch (_) {
      throw new Error('Нет связи с сервером (backend на localhost:8080 не запущен?)');
    }
    if (!response.ok) throw new Error(await readError(response));
    return response;
  }

  // ---- Ключ идемпотентности -------------------------------------------------
  // Один ключ на одну «попытку оплаты». При повторном клике или повторе после обрыва связи
  // уходит тот же ключ, и сервер не создаёт второй платёж. Меняем данные формы или платёж
  // прошёл — ключ обновляется.
  let pendingKey = null;
  function newKey() {
    if (window.crypto && typeof window.crypto.randomUUID === 'function') return window.crypto.randomUUID();
    return 'k-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 12);
  }
  function currentKey() {
    if (!pendingKey) pendingKey = newKey();
    return pendingKey;
  }
  [bookingInput, amountInput, methodInput].forEach(el => el.addEventListener('input', () => { pendingKey = null; }));

  // ---- Баланс брони -----------------------------------------------------------
  async function loadBalance() {
    const id = Number(bookingInput.value);
    if (!Number.isInteger(id) || id < 1) {
      summary.textContent = '';
      summary.className = 'feedback-banner';
      return null;
    }
    try {
      const b = await (await api(`/payments/booking/${id}/balance`)).json();
      const owes = Number(b.balance);
      const tail = owes > 0
        ? `К оплате: ${money.format(owes)}`
        : owes < 0 ? `Переплата / к возврату: ${money.format(-owes)}` : 'Оплачено полностью';
      summary.textContent =
        `Бронь #${b.bookingId} (${b.bookingState}): ${b.nights} ноч. × ${money.format(b.pricePerNight ?? 0)} = ` +
        `${money.format(b.totalDue)} · оплачено ${money.format(b.totalPaid)} · возвращено ${money.format(b.totalRefunded)} · ${tail}`;
      summary.className = 'feedback-banner success';
      amountInput.placeholder = owes > 0 ? `Остаток ${owes.toFixed(2)} (пусто = весь остаток)` : '—';
      return b;
    } catch (err) {
      summary.textContent = `❌ ${err.message}`;
      summary.className = 'feedback-banner error';
      amountInput.placeholder = 'пусто = весь остаток';
      return null;
    }
  }
  bookingInput.addEventListener('change', loadBalance);

  // ---- Приём оплаты -----------------------------------------------------------
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const bookingId = Number(bookingInput.value);
    const amountRaw = amountInput.value.trim();
    const amount = amountRaw === '' ? null : Number(amountRaw);

    if (!Number.isInteger(bookingId) || bookingId < 1) {
      showMsg('Укажите корректный номер брони.', false);
      return;
    }
    if (amount !== null && (!Number.isFinite(amount) || amount <= 0)) {
      showMsg('Сумма должна быть больше 0 (или оставьте пустой, чтобы оплатить весь остаток).', false);
      return;
    }

    submitBtn.disabled = true; // защита от двойного клика; от повторов после обрыва связи защищает ключ
    try {
      const response = await api('/payments', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Idempotency-Key': currentKey() },
        body: JSON.stringify({ bookingId, amount, method: methodInput.value })
      });
      const saved = await response.json();
      const duplicate = response.status === 200;
      showMsg(
        duplicate
          ? `ℹ Этот платёж уже был принят (${saved.transactionRef}) — дубликат не создан.`
          : `✓ Оплата ${money.format(saved.amount)} (${saved.method}) по брони #${saved.bookingId} сохранена. Транзакция ${saved.transactionRef}.`,
        true);
      pendingKey = null;
      amountInput.value = '';
      await Promise.all([loadPayments(), loadBalance()]);
    } catch (err) {
      showMsg(`❌ ${err.message}`, false); // ключ остаётся: повторная отправка безопасна
    } finally {
      submitBtn.disabled = false;
    }
  });

  // ---- История платежей --------------------------------------------------------
  async function loadPayments() {
    try {
      const payments = await (await api('/payments')).json();
      tbody.innerHTML = '';
      if (payments.length === 0) {
        tbody.innerHTML = '<tr><td colspan="8">Платежей пока нет.</td></tr>';
        return;
      }
      payments.forEach(p => {
        const refunded = p.status === 'REFUNDED';
        const tr = document.createElement('tr');
        tr.innerHTML = `
          <td>#${esc(p.paymentId)}</td>
          <td>#${esc(p.bookingId)}</td>
          <td>${refunded ? '−' : ''}${money.format(p.amount)}</td>
          <td>${esc(p.method ?? (refunded ? 'Refund' : ''))}</td>
          <td><span class="badge ${refunded ? 'badge-neutral' : 'badge-cleaned'}">${esc(p.status)}</span></td>
          <td>${esc(p.transactionRef)}</td>
          <td>${esc(p.paidAt ? new Date(p.paidAt).toLocaleString() : '')}</td>
          <td class="text-right"><a class="btn-assign" href="receipt.html?payment=${encodeURIComponent(p.paymentId)}" target="_blank" rel="noopener">Receipt</a></td>`;
        tbody.appendChild(tr);
      });
    } catch (err) {
      tbody.innerHTML = `<tr><td colspan="8">${esc(err.message)}</td></tr>`;
    }
  }

  window.loadPaymentsTab = loadPayments; // вызывается из switchTab() в app.js
  loadPayments();
})();
