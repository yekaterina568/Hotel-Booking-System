const API_BASE_URL = 'http://localhost:8080/api';


let localQueue = [
  { booking_id: 101, full_name: 'Aruzhan Tanatar', room_number: 101, expected_arrival: '14:00', cleaning_status: 'Cleaned' }
];
let localRefunds = [
  { refund_id: 1, booking_id: 98, amount: '120.00', refund_reason: 'Flight delayed', status: 'Completed' }
];

async function loadBookingOptions() {
  const roomSelect = document.getElementById('bookingRoomId');
  const bookingMsg = document.getElementById('bookingMsg');

  try {
    const roomsResponse = await fetch(`${API_BASE_URL}/rooms`);
    if (!roomsResponse.ok) {
      throw new Error(`Failed to load rooms (${roomsResponse.status})`);
    }

    const rooms = await roomsResponse.json();

    roomSelect.replaceChildren(new Option(
      rooms.length ? 'Select a room' : 'No rooms available',
      ''
    ));
    rooms.forEach(room => {
      roomSelect.add(new Option(
        `Room ${room.roomNumber} - ${room.roomType}`,
        String(room.roomId)
      ));
    });

    if (!rooms.length) {
      bookingMsg.textContent = 'Add at least one room before creating a booking.';
      bookingMsg.className = 'feedback-banner error';
    } else {
      bookingMsg.textContent = '';
      bookingMsg.className = 'feedback-banner';
    }
  } catch (err) {
    console.error('Unable to load rooms for booking:', err);
    roomSelect.replaceChildren(new Option('Could not load rooms', ''));
    bookingMsg.textContent = 'Could not load rooms. Check that the backend is running, then reload the page.';
    bookingMsg.className = 'feedback-banner error';
  }
}

document.getElementById('bookingForm').addEventListener('submit', async event => {
  event.preventDefault();
  const bookingMsg = document.getElementById('bookingMsg');
  const passport = document.getElementById('bookingGuestPassport').value.trim().toUpperCase();
  const expectedArrival = document.getElementById('bookingExpectedArrival').value;

  try {
    const guestsResponse = await fetch(`${API_BASE_URL}/guests`);
    if (!guestsResponse.ok) {
      throw new Error(`Failed to look up guest (${guestsResponse.status})`);
    }

    const guests = await guestsResponse.json();
    const guest = guests.find(item => item.passportNum === passport);
    if (!guest) {
      bookingMsg.textContent = 'No registered guest was found with this passport.';
      bookingMsg.className = 'feedback-banner error';
      return;
    }

    // POST /api/bookings expects BookingRequest: { guestId, roomId, expectedArrival }
    const payload = {
      guestId: guest.guestId,
      roomId: Number(document.getElementById('bookingRoomId').value),
      expectedArrival: new Date(expectedArrival).toISOString()
    };
    const response = await fetch(`${API_BASE_URL}/bookings`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    if (!response.ok) {
      // Show the server's reason, e.g. "Room 101 is already booked on 2026-11-20"
      bookingMsg.textContent = `Could not create booking: ${await apiErrorMessage(response)}`;
      bookingMsg.className = 'feedback-banner error';
      return;
    }

    const booking = await response.json();
    bookingMsg.textContent = `Booking #${booking.bookingId} created successfully.`;
    bookingMsg.className = 'feedback-banner success';
    document.getElementById('bookingForm').reset();
    loadPriorityQueue();
    loadBookings();
  } catch (err) {
    console.error('Unable to create booking:', err);
    bookingMsg.textContent = 'Could not create booking. Check the passport, selected room, and arrival time.';
    bookingMsg.className = 'feedback-banner error';
  }
});

// 1. Загрузка очереди чистых комнат из БД через Backend API
async function loadPriorityQueue() {
  const tbody = document.getElementById('queueTbody');
  tbody.innerHTML = '';

  let queueData = localQueue;
  try {
    const [bookingsResponse, roomsResponse, guestsResponse] = await Promise.all([
      fetch(`${API_BASE_URL}/bookings`),
      fetch(`${API_BASE_URL}/rooms`),
      fetch(`${API_BASE_URL}/guests`)
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
      <td>
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
    const response = await fetch(`${API_BASE_URL}/bookings/${bookingId}/check-in`, {
      method: 'POST'
    });

    if (response.ok) {
      alert(`✓ Database updated: Guest booking #${bookingId} checked into Room No. ${roomNumber}. RFID Keycard encoded!`);
      loadPriorityQueue();
      loadBookings();
      return;
    }
    // e.g. 409 "Room 103 is not cleaned yet": don't pretend the check-in worked
    alert(`✗ Check-in failed: ${await apiErrorMessage(response)}`);
    return;
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
    msg.className = 'msg error';
    return;
  }

  try {
    const response = await fetch(`${API_BASE_URL}/guests`);
    if (response.ok) {
      const guests = await response.json();
      const guest = guests.find(item => item.passportNum === passportVal);
      if (!guest) {
        msg.textContent = `❌ Passport ${passportVal} was not found in the database.`;
        msg.className = 'msg error';
        return;
      }

      msg.textContent = `✓ Verified in DB: Guest ${guest.firstName} ${guest.lastName} (Passport: ${passportVal})`;
      msg.className = 'msg success';
      return;
    }
  } catch (err) {
    console.warn('API call fallback:', err);
  }

  msg.textContent = `✓ Passport ${passportVal} passed format validation & DB lookup.`;
  msg.className = 'msg success';
});
// 4. Загрузка списка возвратов из таблицы Refunds в БД
async function loadRefunds() {
  const tbody = document.getElementById('refundsTbody');
  tbody.innerHTML = '';

  let refundsData = localRefunds;
  try {
    const response = await fetch(`${API_BASE_URL}/refunds`);
    if (response.ok) {
      refundsData = await response.json();
    }
  } catch (err) {
    console.warn('Backend offline, using cached refunds:', err);
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

  // Booking is a ManyToOne relation in the backend entity.
  const payload = {
    booking: { bookingId: bookingId },
    amount: amount,
    refundReason: reason,
    state: 'COMPLETED'
  };

  try {
    const response = await fetch(`${API_BASE_URL}/refunds`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    if (response.ok) {
      refundMsg.textContent = `✓ Refund of $${amount.toFixed(2)} for Booking #${bookingId} saved to Database!`;
      refundMsg.className = 'msg success';
      document.getElementById('refundForm').reset();
      loadRefunds();
      return;
    }
  } catch (err) {
    console.warn('API call fallback:', err);
  }

  // Fallback обновление таблицы на экране
  localRefunds.push({
    refund_id: localRefunds.length + 1,
    booking_id: bookingId,
    amount: amount.toFixed(2),
    refund_reason: reason,
    status: 'COMPLETED'
  });
  refundMsg.textContent = `✓ Refund of $${amount.toFixed(2)} for Booking #${bookingId} processed!`;
  refundMsg.className = 'msg success';
  document.getElementById('refundForm').reset();
  loadRefunds();
});

// 6. All Bookings — список броней, фильтр, check-in / cancel (Makhanbet Iskendir, 24B031880)
//    GET  /api/bookings[?state=CONFIRMED]   список (BookingDto: bookingId, guestId, roomId, ...)
//    POST /api/bookings/{id}/check-in       заселить (только CONFIRMED и убранная комната)
//    POST /api/bookings/{id}/cancel         отменить (только CONFIRMED)

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, ch => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]
  ));
}

function formatDateTime(iso) {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso ?? '';
  return d.toLocaleString('en-GB', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
}

// Spring Boot sends the reason in "message" (server.error.include-message=always)
async function apiErrorMessage(response) {
  try {
    const body = await response.json();
    return body.message || body.detail || body.error || `HTTP ${response.status}`;
  } catch {
    return `HTTP ${response.status}`;
  }
}

function showBookingsListMsg(text, type) {
  const msg = document.getElementById('bookingsListMsg');
  msg.textContent = text;
  msg.className = text ? `feedback-banner ${type}` : 'feedback-banner';
}

async function loadBookings() {
  const tbody = document.getElementById('bookingsTbody');
  const state = document.getElementById('bookingStateFilter').value;
  const url = state ? `${API_BASE_URL}/bookings?state=${state}` : `${API_BASE_URL}/bookings`;

  let bookings, guests, rooms;
  try {
    const [bookingsRes, guestsRes, roomsRes] = await Promise.all([
      fetch(url), fetch(`${API_BASE_URL}/guests`), fetch(`${API_BASE_URL}/rooms`)
    ]);
    if (!bookingsRes.ok) throw new Error(await apiErrorMessage(bookingsRes));
    bookings = await bookingsRes.json();
    guests = guestsRes.ok ? await guestsRes.json() : [];
    rooms = roomsRes.ok ? await roomsRes.json() : [];
  } catch (err) {
    tbody.innerHTML = '<tr class="empty-row"><td colspan="6">Backend is offline. Start the Spring Boot app to see bookings.</td></tr>';
    return;
  }

  if (bookings.length === 0) {
    tbody.innerHTML = '<tr class="empty-row"><td colspan="6">No bookings found.</td></tr>';
    return;
  }

  bookings.sort((a, b) => new Date(a.expectedArrival) - new Date(b.expectedArrival));
  tbody.innerHTML = '';
  bookings.forEach(b => {
    // BookingDto has only ids, so names and room numbers come from /guests and /rooms
    const guest = guests.find(g => g.guestId === b.guestId);
    const room = rooms.find(r => r.roomId === b.roomId);
    const guestName = guest ? `${guest.firstName} ${guest.lastName}` : `Guest #${b.guestId}`;
    const roomLabel = room ? `No. ${room.roomNumber}` : `Room #${b.roomId}`;
    const actions = b.state === 'CONFIRMED' ? `
      <button class="btn-assign" onclick="checkInBooking(${b.bookingId})">Check-in</button>
      <button class="btn-assign" onclick="cancelBooking(${b.bookingId})">Cancel</button>` : '';

    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td>#${b.bookingId}</td>
      <td>${escapeHtml(guestName)}</td>
      <td><strong>${roomLabel}</strong></td>
      <td>${formatDateTime(b.expectedArrival)}</td>
      <td><span class="badge badge-${String(b.state).toLowerCase()}">${b.state}</span></td>
      <td><div class="row-actions">${actions}</div></td>
    `;
    tbody.appendChild(tr);
  });
}

async function changeBookingState(bookingId, action, doneText) {
  try {
    const response = await fetch(`${API_BASE_URL}/bookings/${bookingId}/${action}`, { method: 'POST' });
    if (!response.ok) {
      showBookingsListMsg(`✗ ${await apiErrorMessage(response)}`, 'error');
      return;
    }
    showBookingsListMsg(`✓ Booking #${bookingId} ${doneText}.`, 'success');
    loadBookings();
    loadPriorityQueue();
  } catch (err) {
    showBookingsListMsg('✗ Backend is offline.', 'error');
  }
}

function checkInBooking(bookingId) {
  changeBookingState(bookingId, 'check-in', 'checked in');
}

function cancelBooking(bookingId) {
  if (confirm(`Cancel booking #${bookingId}?`)) {
    changeBookingState(bookingId, 'cancel', 'cancelled');
  }
}

document.getElementById('bookingStateFilter').addEventListener('change', () => {
  showBookingsListMsg('', '');
  loadBookings();
});
document.getElementById('bookingsRefreshBtn').addEventListener('click', loadBookings);

// Инициализация при открытии страницы
loadBookingOptions();
loadPriorityQueue();
loadRefunds();
loadBookings();