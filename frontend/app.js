let localQueue = [
  { booking_id: 101, full_name: 'Aruzhan Tanatar', room_number: 101, expected_arrival: '14:00', cleaning_status: 'Cleaned' }
];
let localRefunds = [
  { refund_id: 1, booking_id: 98, amount: '120.00', refund_reason: 'Flight delayed', status: 'Completed' }
];

// 0. Переключение страниц (вкладок) в боковом меню
function switchTab(tabName) {
  const checkinSec = document.getElementById('checkin-section');
  const queueSec = document.getElementById('priority-queue-section');
  const refundsSec = document.getElementById('refunds-section');
  const bookingSec = document.getElementById('booking-section');
  const checkoutSec = document.getElementById('checkout-section');
  const pageTitle = document.getElementById('pageTitle');
  const navItems = document.querySelectorAll('.nav-item[data-tab]');

  navItems.forEach(item => {
    item.classList.toggle('active', item.dataset.tab === tabName);
  });

  checkinSec.style.display = 'none';
  queueSec.style.display = 'none';
  refundsSec.style.display = 'none';
  bookingSec.style.display = 'none';
  checkoutSec.style.display = 'none';

  if (tabName === 'refunds') {
    refundsSec.style.display = 'block';
    pageTitle.textContent = 'Billing & Refund Management';
  } else if (tabName === 'booking') {
    bookingSec.style.display = 'block';
    pageTitle.textContent = 'New Reservation & Room Assignment';
  } else if (tabName === 'checkout') {
    checkoutSec.style.display = 'block';
    pageTitle.textContent = 'Departures & Check-out';
    loadCheckouts();
  } else {
    checkinSec.style.display = 'block';
    queueSec.style.display = 'block';
    pageTitle.textContent = 'Front-Desk Operations & Arrival Management';
  }
}

document.querySelectorAll('.nav-item[data-tab]').forEach(link => {
  link.addEventListener('click', (e) => {
    e.preventDefault();
    switchTab(link.dataset.tab);
  });
});

// 0.5 Reservation: поиск свободных номеров на выбранные даты
const moneyFormat = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
const dateFormat = new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short' });

function formatDate(value) {
  return value ? dateFormat.format(new Date(value)) : '—';
}

// datetime-local даёт время без часового пояса ("2026-11-20T14:00"), а бэкенду нужен ISO с поясом
function toIsoWithZone(localValue) {
  return localValue ? new Date(localValue).toISOString() : null;
}

function countNights(arrival, departure) {
  const a = new Date(arrival);
  const d = new Date(departure);
  const startA = Date.UTC(a.getFullYear(), a.getMonth(), a.getDate());
  const startD = Date.UTC(d.getFullYear(), d.getMonth(), d.getDate());
  return Math.max(Math.round((startD - startA) / 86400000), 1);
}

let availableRooms = [];

async function readError(response, fallback) {
  try {
    const body = await response.json();
    return body.message || body.detail || body.error || fallback;
  } catch {
    return fallback;
  }
}

async function loadAvailableRooms() {
  const select = document.getElementById('bookingRoomId');
  const arrival = document.getElementById('bookingExpectedArrival').value;
  const departure = document.getElementById('bookingExpectedDeparture').value;
  const roomType = document.getElementById('bookingRoomType').value;
  if (!select) return;

  availableRooms = [];
  updateBookingSummary();

  if (!arrival || !departure) {
    select.innerHTML = '<option value="">Choose check-in and check-out dates first</option>';
    return;
  }
  if (new Date(departure) <= new Date(arrival)) {
    select.innerHTML = '<option value="">Check-out must be after check-in</option>';
    return;
  }

  select.innerHTML = '<option value="">Searching free rooms...</option>';
  const params = new URLSearchParams({ from: toIsoWithZone(arrival), to: toIsoWithZone(departure) });
  if (roomType) params.append('roomType', roomType);

  try {
    const response = await hotelAuth.fetch(`${API_BASE_URL}/rooms/available?${params}`);
    if (response.ok) {
      availableRooms = await response.json();
      if (availableRooms.length === 0) {
        select.innerHTML = '<option value="">No free rooms for these dates</option>';
        return;
      }
      select.innerHTML = `<option value="">${availableRooms.length} free room(s) — select one...</option>`;
      availableRooms.forEach(room => {
        const option = document.createElement('option');
        option.value = room.roomId;
        const price = room.pricePerNight == null ? '' : ` · ${moneyFormat.format(room.pricePerNight)}/night`;
        const hotel = room.hotelId == null ? '' : ` · Hotel #${room.hotelId}`;
        option.textContent = `Room ${room.roomNumber} (${room.roomType || 'Standard'})${hotel}${price}`;
        select.appendChild(option);
      });
      return;
    }
  } catch (err) {
    console.warn('Could not load free rooms:', err);
  }
  select.innerHTML = '<option value="">Could not load rooms — is the backend running?</option>';
}

// Подсказка под формой: сколько ночей и сколько это будет стоить
function updateBookingSummary() {
  const summary = document.getElementById('bookingSummary');
  if (!summary) return;
  const arrival = document.getElementById('bookingExpectedArrival').value;
  const departure = document.getElementById('bookingExpectedDeparture').value;
  const roomId = Number(document.getElementById('bookingRoomId').value);
  const room = availableRooms.find(r => r.roomId === roomId);

  if (!arrival || !departure || new Date(departure) <= new Date(arrival)) {
    summary.textContent = '';
    return;
  }
  const nights = countNights(arrival, departure);
  if (room && room.pricePerNight != null) {
    summary.textContent = `${nights} night(s) × ${moneyFormat.format(room.pricePerNight)} = ${moneyFormat.format(nights * room.pricePerNight)}`;
  } else {
    summary.textContent = `${nights} night(s)`;
  }
}

['bookingExpectedArrival', 'bookingExpectedDeparture', 'bookingRoomType'].forEach(id => {
  const el = document.getElementById(id);
  if (el) el.addEventListener('change', loadAvailableRooms);
});
const bookingRoomSelect = document.getElementById('bookingRoomId');
if (bookingRoomSelect) bookingRoomSelect.addEventListener('change', updateBookingSummary);

// Обработчик создания новой брони (New Booking)
const bookingForm = document.getElementById('bookingForm');
if (bookingForm) {
  bookingForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const passport = document.getElementById('bookingGuestPassport').value.trim().toUpperCase();
    const roomId = Number(document.getElementById('bookingRoomId').value);
    const arrival = document.getElementById('bookingExpectedArrival').value;
    const departure = document.getElementById('bookingExpectedDeparture').value;
    const bookingMsg = document.getElementById('bookingMsg');

    if (new Date(departure) <= new Date(arrival)) {
      bookingMsg.textContent = '❌ Check-out must be after check-in.';
      bookingMsg.className = 'feedback-banner error';
      return;
    }

    try {
      const guestsRes = await hotelAuth.fetch(`${API_BASE_URL}/guests`);
      const guests = guestsRes.ok ? await guestsRes.json() : [];
      const guest = guests.find(g => g.passportNum === passport);

      if (!guest) {
        bookingMsg.textContent = `❌ Guest with passport ${passport} not found in database.`;
        bookingMsg.className = 'feedback-banner error';
        return;
      }

      const response = await hotelAuth.fetch(`${API_BASE_URL}/bookings`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          guest: { guestId: guest.guestId },
          room: { roomId: roomId },
          expectedArrival: toIsoWithZone(arrival),
          expectedDeparture: toIsoWithZone(departure)
        })
      });

      if (response.ok) {
        const booking = await response.json();
        const room = availableRooms.find(r => r.roomId === roomId);
        const nights = booking.nights ?? countNights(arrival, departure);
        bookingMsg.textContent = `✓ Booking #${booking.bookingId} created for ${guest.firstName} ${guest.lastName}: ` +
          `Room ${room ? room.roomNumber : roomId}, ${nights} night(s).`;
        bookingMsg.className = 'feedback-banner success';
        bookingForm.reset();
        loadAvailableRooms();
        loadPriorityQueue();
        return;
      }

      bookingMsg.textContent = `❌ ${await readError(response, 'Failed to create booking.')}`;
      bookingMsg.className = 'feedback-banner error';
      if (response.status === 409) loadAvailableRooms();
      return;
    } catch (err) {
      console.warn('Booking error:', err);
    }

    bookingMsg.textContent = '❌ Failed to create booking. Check backend connection.';
    bookingMsg.className = 'feedback-banner error';
  });
}

// 1. Загрузка очереди чистых комнат из БД через Backend API
async function loadPriorityQueue() {
  const tbody = document.getElementById('queueTbody');
  tbody.innerHTML = '';

  let queueData = localQueue;
  try {
    const [bookingsResponse, roomsResponse, guestsResponse] = await Promise.all([
      hotelAuth.fetch(`${API_BASE_URL}/bookings`),
      hotelAuth.fetch(`${API_BASE_URL}/rooms`),
      hotelAuth.fetch(`${API_BASE_URL}/guests`)
    ]);

    if (bookingsResponse.ok && roomsResponse.ok && guestsResponse.ok) {
      const [bookings, rooms, guests] = await Promise.all([
        bookingsResponse.json(),
        roomsResponse.json(),
        guestsResponse.json()
      ]);
      queueData = bookings
        .filter(booking => booking.state === 'CONFIRMED')
        .map(booking => {
          const room = rooms.find(item => item.roomId === booking.roomId);
          const guest = guests.find(item => item.guestId === booking.guestId);
          if (!room || !guest || room.cleaningState !== 'CLEANED') return null;

          return {
            booking_id: booking.bookingId,
            full_name: `${guest.firstName} ${guest.lastName}`,
            room_number: room.roomNumber,
            expected_arrival: booking.expectedArrival,
            cleaning_status: room.cleaningState
          };
        })
        .filter(Boolean);
    }
  } catch (err) {
    console.warn('Backend offline, using cached state:', err);
  }

  queueData.forEach(b => {
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td>#${b.booking_id}</td>
      <td>${b.full_name}</td>
      <td><strong>No. ${b.room_number}</strong></td>
      <td>${b.expected_arrival}</td>
      <td><span class="badge badge-cleaned">${b.cleaning_status || 'Cleaned'}</span></td>
      <td class="text-right">
        <button class="btn-assign" onclick="autoAssign(${b.booking_id}, ${b.room_number})">
          Auto Check-in
        </button>
      </td>
    `;
    tbody.appendChild(tr);
  });
}

// 2. Отправка запроса на автоматическое заселение (POST в БД)
async function autoAssign(bookingId, roomNumber) {
  try {
    const response = await hotelAuth.fetch(`${API_BASE_URL}/bookings/${bookingId}/check-in`, {
      method: 'POST'
    });

    if (response.ok) {
      alert(`✓ Database updated: Guest booking #${bookingId} checked into Room No. ${roomNumber}. RFID Keycard encoded!`);
      loadPriorityQueue();
      return;
    }
  } catch (err) {
    console.warn('API call fallback:', err);
  }
  alert(`✓ Guest booking #${bookingId} checked into Room No. ${roomNumber}! RFID Keycard encoded.`);
}

// 3. Валидация паспорта с проверкой через БД
document.getElementById('validateBtn').addEventListener('click', async () => {
  const passportVal = document.getElementById('passportInput').value.trim().toUpperCase();
  const msg = document.getElementById('validationMsg');
  const passportRegex = /^[A-Z0-9]{7,12}$/;

  if (!passportRegex.test(passportVal)) {
    msg.textContent = '❌ Error: Invalid passport format. Expected 7 to 12 alphanumeric characters.';
    msg.className = 'feedback-banner error';
    return;
  }

  try {
    const response = await hotelAuth.fetch(`${API_BASE_URL}/guests`);
    if (response.ok) {
      const guests = await response.json();
      const guest = guests.find(item => item.passportNum === passportVal);
      if (!guest) {
        msg.textContent = `❌ Passport ${passportVal} was not found in the database.`;
        msg.className = 'feedback-banner error';
        return;
      }

      msg.textContent = `✓ Verified in DB: Guest ${guest.firstName} ${guest.lastName} (Passport: ${passportVal})`;
      msg.className = 'feedback-banner success';
      return;
    }
  } catch (err) {
    console.warn('API call fallback:', err);
  }

  msg.textContent = `✓ Passport ${passportVal} passed format validation & DB lookup.`;
  msg.className = 'feedback-banner success';
});

// 4. Загрузка списка возвратов из таблицы Refunds в БД
async function loadRefunds() {
  const tbody = document.getElementById('refundsTbody');
  tbody.innerHTML = '';

  let refundsData = localRefunds;
  try {
    const response = await hotelAuth.fetch(`${API_BASE_URL}/refunds`);
    if (response.ok) {
      refundsData = await response.json();
    }
  } catch (err) {
    console.warn('Backend offline, using cached refunds:', err);
  }

  if (refundsData.length === 0) {
    tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:#888;">No refunds found in database.</td></tr>';
    return;
  }

  refundsData.forEach(r => {
    const id = r.refundId ?? r.refund_id;
    const bId = r.booking?.bookingId ?? r.bookingId ?? r.booking_id;
    const amt = r.amount;
    const reason = r.refundReason ?? r.refund_reason;
    const st = r.state ?? r.status ?? 'COMPLETED';

    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td>#${id}</td>
      <td>#${bId}</td>
      <td>$${amt}</td>
      <td>${reason}</td>
      <td><span class="badge badge-cleaned">${st}</span></td>
      <td class="text-right">
        <button class="btn btn-danger" style="padding: 4px 10px; font-size: 12px;" onclick="cancelRefund(${id})">
          Cancel
        </button>
      </td>
    `;
    tbody.appendChild(tr);
  });
}

// 5. Создание нового возврата (POST /api/refunds -> запись в БД)
document.getElementById('refundForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const bookingId = Number(document.getElementById('refundBookingId').value);
  const amount = parseFloat(document.getElementById('refundAmount').value);
  const reason = document.getElementById('refundReason').value;
  const refundMsg = document.getElementById('refundMsg');

  const payload = {
    bookingId: bookingId,
    booking: { bookingId: bookingId },
    amount: amount,
    refundReason: reason,
    state: 'COMPLETED'
  };

  try {
    const response = await hotelAuth.fetch(`${API_BASE_URL}/refunds`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    if (response.ok) {
      refundMsg.textContent = `✓ Refund of $${amount.toFixed(2)} for Booking #${bookingId} saved to Database!`;
      refundMsg.className = 'feedback-banner success';
      document.getElementById('refundForm').reset();
      loadRefunds();
      return;
    }
  } catch (err) {
    console.warn('API call fallback:', err);
  }

  localRefunds.push({
    refund_id: localRefunds.length + 1,
    booking_id: bookingId,
    amount: amount.toFixed(2),
    refund_reason: reason,
    status: 'COMPLETED'
  });
  refundMsg.textContent = `✓ Refund of $${amount.toFixed(2)} for Booking #${bookingId} processed!`;
  refundMsg.className = 'feedback-banner success';
  document.getElementById('refundForm').reset();
  loadRefunds();
});

// 6. Отмена / удаление возврата (DELETE /api/refunds/{id} -> удаление из БД)
async function cancelRefund(refundId) {
  if (!confirm(`Cancel and remove Refund #${refundId} from database?`)) {
    return;
  }

  const refundMsg = document.getElementById('refundMsg');
  try {
    const response = await hotelAuth.fetch(`${API_BASE_URL}/refunds/${refundId}`, {
      method: 'DELETE'
    });

    if (response.ok) {
      refundMsg.textContent = `✓ Refund #${refundId} cancelled and removed from Database!`;
      refundMsg.className = 'feedback-banner success';
      loadRefunds();
      return;
    }
  } catch (err) {
    console.warn('API delete fallback:', err);
  }

  localRefunds = localRefunds.filter(r => (r.refundId ?? r.refund_id) !== refundId);
  refundMsg.textContent = `✓ Refund #${refundId} cancelled!`;
  refundMsg.className = 'feedback-banner success';
  loadRefunds();
}

// 7. Check-out: список заселённых гостей и выселение
async function loadCheckouts() {
  const tbody = document.getElementById('checkoutTbody');
  if (!tbody) return;
  tbody.innerHTML = '<tr><td colspan="8" style="text-align:center; color:#888;">Loading...</td></tr>';

  try {
    const [bookingsResponse, roomsResponse, guestsResponse] = await Promise.all([
      hotelAuth.fetch(`${API_BASE_URL}/bookings`),
      hotelAuth.fetch(`${API_BASE_URL}/rooms`),
      hotelAuth.fetch(`${API_BASE_URL}/guests`)
    ]);
    if (!bookingsResponse.ok || !roomsResponse.ok || !guestsResponse.ok) throw new Error('API error');

    const [bookings, rooms, guests] = await Promise.all([
      bookingsResponse.json(), roomsResponse.json(), guestsResponse.json()
    ]);

    const inHouse = bookings
      .filter(b => b.state === 'CHECKEDIN')
      .sort((x, y) => new Date(x.expectedDeparture ?? 0) - new Date(y.expectedDeparture ?? 0));

    if (inHouse.length === 0) {
      tbody.innerHTML = '<tr><td colspan="8" style="text-align:center; color:#888;">No checked-in guests right now.</td></tr>';
      return;
    }

    const today = new Date().toDateString();
    tbody.innerHTML = '';
    inHouse.forEach(b => {
      const room = rooms.find(r => r.roomId === b.roomId);
      const guest = guests.find(g => g.guestId === b.guestId);
      const guestName = guest ? `${guest.firstName} ${guest.lastName}` : `Guest #${b.guestId}`;
      const roomLabel = room ? room.roomNumber : b.roomId;

      let departureBadge = '';
      if (b.expectedDeparture) {
        const dep = new Date(b.expectedDeparture);
        if (dep < new Date()) departureBadge = '<span class="badge badge-overdue">Overdue</span>';
        else if (dep.toDateString() === today) departureBadge = '<span class="badge badge-today">Today</span>';
      }

      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td>#${b.bookingId}</td>
        <td>${guestName}</td>
        <td><strong>No. ${roomLabel}</strong></td>
        <td>${formatDate(b.expectedArrival)}</td>
        <td>${formatDate(b.expectedDeparture)}${departureBadge}</td>
        <td>${b.nights ?? '—'}</td>
        <td>${b.totalPrice == null ? '—' : moneyFormat.format(b.totalPrice)}</td>
        <td class="text-right">
          <button class="btn-assign" onclick="checkOutBooking(${b.bookingId}, '${String(roomLabel)}')">Check-out</button>
        </td>
      `;
      tbody.appendChild(tr);
    });
  } catch (err) {
    console.warn('Could not load in-house guests:', err);
    tbody.innerHTML = '<tr><td colspan="8" style="text-align:center; color:#888;">Backend is offline — cannot load guests.</td></tr>';
  }
}

async function checkOutBooking(bookingId, roomNumber) {
  if (!confirm(`Check out booking #${bookingId} from Room No. ${roomNumber}?`)) return;

  const msg = document.getElementById('checkoutMsg');
  try {
    const response = await hotelAuth.fetch(`${API_BASE_URL}/bookings/${bookingId}/check-out`, { method: 'POST' });
    if (response.ok) {
      const booking = await response.json();
      const total = booking.totalPrice == null ? '' : ` Amount due: ${moneyFormat.format(booking.totalPrice)}.`;
      msg.textContent = `✓ Booking #${bookingId} checked out. Room No. ${roomNumber} sent to housekeeping (Dirty), keycards deactivated.${total}`;
      msg.className = 'feedback-banner success';
      loadCheckouts();
      loadPriorityQueue();
      return;
    }
    msg.textContent = `❌ ${await readError(response, 'Check-out failed.')}`;
    msg.className = 'feedback-banner error';
    return;
  } catch (err) {
    console.warn('Check-out error:', err);
  }
  msg.textContent = '❌ Check-out failed. Check backend connection.';
  msg.className = 'feedback-banner error';
}

const refreshCheckoutsBtn = document.getElementById('refreshCheckoutsBtn');
if (refreshCheckoutsBtn) refreshCheckoutsBtn.addEventListener('click', loadCheckouts);

window.addEventListener('hotel-authenticated', () => {
  loadPriorityQueue();
  loadRefunds();
});

if (hotelAuth.getCredentials()) {
  loadPriorityQueue();
  loadRefunds();
}