const API_BASE_URL = 'http://localhost:8080/api';

// Стартовые отзывы об отеле в стиле 2GIS (сохраняются в localStorage)
const DEFAULT_REVIEWS = [
  {
    id: 1,
    author: 'Aruzhan Tanatar',
    rating: 5,
    date: 'Nov 24, 2026',
    comment: 'Отличный отель в центре Алматы! Быстрое заселение на ресепшене, номера идеально чистые, вид на горы потрясающий.'
  },
  {
    id: 2,
    author: 'Dias Kassymov',
    rating: 5,
    date: 'Nov 21, 2026',
    comment: 'Очень вежливый персонал. Выдали RFID-ключ за пару минут, завтраки вкусные, всё на высшем уровне.'
  },
  {
    id: 3,
    author: 'Elena Kim',
    rating: 4,
    date: 'Nov 18, 2026',
    comment: 'Уютные комнаты и удобное расположение рядом с проспектом Достык. В следующий раз снова выберу Grand Almaty.'
  },
  {
    id: 4,
    author: 'Arman Suleimenov',
    rating: 5,
    date: 'Nov 15, 2026',
    comment: 'Останавливался в командировке. Стабильный Wi-Fi, тишина в номерах и оперативное решение любых вопросов.'
  }
];

let hotelReviews = JSON.parse(localStorage.getItem('grand_almaty_2gis_reviews')) || DEFAULT_REVIEWS;
let selectedRating = 5;

// 0. Переключение страниц в боковом меню
function switchTab(tabName) {
  const sections = {
    checkin: ['checkin-section', 'priority-queue-section'],
    registration: ['registration-section'],
    booking: ['booking-section'],
    refunds: ['refunds-section'],
    reviews: ['reviews-section']
  };

  const titles = {
    checkin: 'Front-Desk Operations & Arrival Management',
    registration: 'Guest Registration & Onboarding',
    booking: 'New Reservation & Room Assignment',
    refunds: 'Billing & Refund Management',
    reviews: '2GIS Hotel Feedback & Guest Rating'
  };

  document.querySelectorAll('.nav-item[data-tab]').forEach(item => {
    item.classList.toggle('active', item.dataset.tab === tabName);
  });

  Object.values(sections).flat().forEach(id => {
    const el = document.getElementById(id);
    if (el) el.style.display = 'none';
  });

  (sections[tabName] || sections.checkin).forEach(id => {
    const el = document.getElementById(id);
    if (el) el.style.display = 'block';
  });

  document.getElementById('pageTitle').textContent = titles[tabName] || titles.checkin;
}

document.querySelectorAll('.nav-item[data-tab]').forEach(link => {
  link.addEventListener('click', (e) => {
    e.preventDefault();
    switchTab(link.dataset.tab);
  });
});

// 1. Регистрация нового гостя (POST /api/guests -> сохранение в PostgreSQL)
const registrationForm = document.getElementById('registrationForm');
if (registrationForm) {
  registrationForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const firstName = document.getElementById('regFirstName').value.trim();
    const lastName = document.getElementById('regLastName').value.trim();
    const passportNum = document.getElementById('regPassport').value.trim().toUpperCase();
    const phone = document.getElementById('regPhone').value.trim();
    const regMsg = document.getElementById('registrationMsg');
    const btn = document.getElementById('regSubmitBtn');

    btn.disabled = true;
    btn.textContent = 'Registering...';

    try {
      const response = await fetch(`${API_BASE_URL}/guests`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          firstName,
          lastName,
          passportNum,
          phone
        })
      });

      if (response.ok) {
        const created = await response.json();
        regMsg.textContent = `✓ Guest ${created.firstName} ${created.lastName} (Passport: ${created.passportNum}) registered in Database!`;
        regMsg.className = 'msg success';
        registrationForm.reset();
        loadPriorityQueue();
      } else {
        const errText = await response.text();
        regMsg.textContent = `❌ Registration failed (${response.status}): ${errText || 'Check passport uniqueness'}`;
        regMsg.className = 'msg error';
      }
    } catch (err) {
      regMsg.textContent = '❌ Server connection error. Make sure Spring Boot is running on port 8080.';
      regMsg.className = 'msg error';
    } finally {
      btn.disabled = false;
      btn.textContent = 'Register Guest';
    }
  });
}

// 2. Загрузка списка комнат для формы создания бронирования
async function loadBookingOptions() {
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
        option.textContent = `Room ${room.roomNumber} (${room.roomType || 'Standard'})${price}`;
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
        bookingMsg.textContent = `❌ Guest with passport ${passport} not found. Register guest first.`;
        bookingMsg.className = 'msg error';
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

// 3. Загрузка очереди раннего заселения из БД
async function loadPriorityQueue() {
  const tbody = document.getElementById('queueTbody');
  tbody.innerHTML = '';

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

      const queueData = bookings
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

      if (queueData.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:#888;">No confirmed arrivals ready for check-in.</td></tr>';
        return;
      }

      queueData.forEach(b => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
          <td>#${b.booking_id}</td>
          <td>${b.full_name}</td>
          <td><strong>No. ${b.room_number}</strong></td>
          <td>${b.expected_arrival}</td>
          <td><span class="badge badge-cleaned">${b.cleaning_status}</span></td>
          <td class="text-right">
            <button class="btn-assign" onclick="autoAssign(${b.booking_id}, ${b.room_number})">
              Auto Check-in
            </button>
          </td>
        `;
        tbody.appendChild(tr);
      });
      return;
    }
  } catch (err) {
    console.warn('Backend offline:', err);
  }

  tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:#b91c1c;">Backend unavailable. Start Spring Boot on port 8080.</td></tr>';
}

// 4. Автоматическое заселение
async function autoAssign(bookingId, roomNumber) {
  try {
    const response = await hotelAuth.fetch(`${API_BASE_URL}/bookings/${bookingId}/check-in`, {
      method: 'POST'
    });

    if (response.ok) {
      alert(`✓ Database updated: Booking #${bookingId} checked into Room No. ${roomNumber}. RFID Keycard encoded!`);
      loadPriorityQueue();
      return;
    }
  } catch (err) {
    console.warn('Check-in error:', err);
  }
  alert('❌ Could not complete check-in. Verify backend endpoint.');
}

// 5. Проверка паспорта по таблице guests в БД
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
    console.warn('Validation error:', err);
  }

  msg.textContent = '❌ Server connection error during passport lookup.';
  msg.className = 'msg error';
});

// 6. Загрузка списка возвратов из БД
async function loadRefunds() {
  const tbody = document.getElementById('refundsTbody');
  tbody.innerHTML = '';

  try {
    const response = await hotelAuth.fetch(`${API_BASE_URL}/refunds`);
    if (response.ok) {
      const refundsData = await response.json();

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
      return;
    }
  } catch (err) {
    console.warn('Refunds load error:', err);
  }

  tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:#b91c1c;">Could not load refunds from database.</td></tr>';
}

// 7. Создание нового возврата (POST /api/refunds)
document.getElementById('refundForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const bookingId = Number(document.getElementById('refundBookingId').value);
  const amount = parseFloat(document.getElementById('refundAmount').value);
  const reason = document.getElementById('refundReason').value;
  const refundMsg = document.getElementById('refundMsg');
  const btn = document.getElementById('refundSubmitBtn');

  btn.disabled = true;
  btn.textContent = 'Saving...';

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
    } else {
      refundMsg.textContent = `❌ Error (${response.status}): Booking #${bookingId} does not exist or invalid refund data.`;
      refundMsg.className = 'msg error';
    }
  } catch (err) {
    refundMsg.textContent = '❌ Server connection error. Check Spring Boot on port 8080.';
    refundMsg.className = 'msg error';
  } finally {
    btn.disabled = false;
    btn.textContent = 'Issue Refund';
  }
});

// 8. Отмена / удаление возврата (DELETE /api/refunds/{id})
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
    console.warn('Delete error:', err);
  }

  refundMsg.textContent = `❌ Failed to cancel Refund #${refundId}.`;
  refundMsg.className = 'msg error';
}

// 9. 2GIS Отзывы и рейтинг об отеле
function renderReviews() {
  const listEl = document.getElementById('reviewsList');
  const avgEl = document.getElementById('gisAvgScore');
  const countEl = document.getElementById('gisTotalCount');
  const barsEl = document.getElementById('gisBarsContainer');

  if (!listEl) return;

  const total = hotelReviews.length;
  const sum = hotelReviews.reduce((acc, r) => acc + r.rating, 0);
  const avg = total > 0 ? (sum / total).toFixed(1) : '5.0';

  avgEl.textContent = avg;
  countEl.textContent = `${total} reviews on 2GIS`;

  // Прогресс-бары от 5 до 1 звезды
  barsEl.innerHTML = '';
  for (let star = 5; star >= 1; star--) {
    const count = hotelReviews.filter(r => r.rating === star).length;
    const pct = total > 0 ? Math.round((count / total) * 100) : 0;
    barsEl.innerHTML += `
      <div class="gis-bar-row">
        <span>${star}★</span>
        <div class="gis-bar-track">
          <div class="gis-bar-fill" style="width: ${pct}%"></div>
        </div>
        <span>${count}</span>
      </div>
    `;
  }

  // Лента отзывов
  listEl.innerHTML = '';
  hotelReviews.forEach(rev => {
    const initials = rev.author
      .split(' ')
      .map(p => p[0])
      .join('')
      .slice(0, 2)
      .toUpperCase();
    const starsStr = '★'.repeat(rev.rating) + '☆'.repeat(5 - rev.rating);

    const item = document.createElement('div');
    item.className = 'review-item';
    item.innerHTML = `
      <div class="review-top">
        <div class="review-author">
          <div class="review-avatar">${initials}</div>
          <div>
            <div class="review-name">${rev.author}</div>
            <div class="review-date">${rev.date} • Verified Stay via 2GIS</div>
          </div>
        </div>
        <div class="review-stars">${starsStr} (${rev.rating}.0)</div>
      </div>
      <div class="review-text">${rev.comment}</div>
    `;
    listEl.appendChild(item);
  });
}

// Выбор звёзд в форме отзыва
document.querySelectorAll('.star-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    selectedRating = Number(btn.dataset.rating);
    document.querySelectorAll('.star-btn').forEach(b => {
      b.classList.toggle('selected', Number(b.dataset.rating) === selectedRating);
    });
  });
});

// Добавление нового 2GIS отзыва
const reviewForm = document.getElementById('reviewForm');
if (reviewForm) {
  reviewForm.addEventListener('submit', (e) => {
    e.preventDefault();
    const author = document.getElementById('reviewAuthor').value.trim();
    const comment = document.getElementById('reviewComment').value.trim();
    const reviewMsg = document.getElementById('reviewMsg');

    const today = new Date().toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric'
    });

    hotelReviews.unshift({
      id: Date.now(),
      author,
      rating: selectedRating,
      date: today,
      comment
    });

    localStorage.setItem('grand_almaty_2gis_reviews', JSON.stringify(hotelReviews));
    reviewMsg.textContent = '✓ Thank you! Your 2GIS review has been published and hotel rating updated.';
    reviewMsg.className = 'msg success';
    reviewForm.reset();
    renderReviews();
  });
}

// Инициализация при открытии страницы
loadBookingOptions();
loadPriorityQueue();
loadRefunds();
renderReviews();
