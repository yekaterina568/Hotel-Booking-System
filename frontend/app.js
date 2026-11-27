const API_BASE_URL = 'http://localhost:8080/api';

// Авторизация для Spring Security (admin:1234)
const AUTH_HEADER = 'Basic ' + btoa('admin:1234');
const originalFetch = window.fetch;
window.fetch = (url, options = {}) => {
  if (typeof url === 'string' && url.startsWith(API_BASE_URL)) {
    options.headers = {
      ...options.headers,
      'Authorization': AUTH_HEADER
    };
  }
  return originalFetch(url, options);
};

// База зарегистрированных операторов Front-Desk (сохраняется в localStorage)
const DEFAULT_OPERATORS = [
  { username: 'admin', password: '1234', fullName: 'System Administrator', role: 'Front-Desk Admin' }
];

function getOperators() {
  return JSON.parse(localStorage.getItem('pms_operators')) || DEFAULT_OPERATORS;
}

function saveOperators(list) {
  localStorage.setItem('pms_operators', JSON.stringify(list));
}

// Управление экраном входа / регистрации оператора
function checkOperatorSession() {
  const activeUser = JSON.parse(sessionStorage.getItem('pms_active_operator'));
  const authScreen = document.getElementById('authScreen');
  const appLayout = document.getElementById('appLayout');

  if (activeUser) {
    authScreen.style.display = 'none';
    appLayout.style.display = 'flex';
    document.getElementById('currentOperatorName').textContent = activeUser.fullName;
    document.getElementById('currentOperatorRole').textContent = activeUser.role;
    loadBookingOptions();
    loadPriorityQueue();
    loadRefunds();
    renderReviews();
  } else {
    authScreen.style.display = 'flex';
    appLayout.style.display = 'none';
  }
}

// Переключение вкладок Sign In / Register на стартовом экране
document.getElementById('tabLoginBtn').addEventListener('click', () => {
  document.getElementById('tabLoginBtn').classList.add('active');
  document.getElementById('tabRegisterBtn').classList.remove('active');
  document.getElementById('operatorLoginForm').style.display = 'flex';
  document.getElementById('operatorRegisterForm').style.display = 'none';
  document.getElementById('authMsg').textContent = '';
  document.getElementById('authMsg').className = '';
});

document.getElementById('tabRegisterBtn').addEventListener('click', () => {
  document.getElementById('tabRegisterBtn').classList.add('active');
  document.getElementById('tabLoginBtn').classList.remove('active');
  document.getElementById('operatorRegisterForm').style.display = 'flex';
  document.getElementById('operatorLoginForm').style.display = 'none';
  document.getElementById('authMsg').textContent = '';
  document.getElementById('authMsg').className = '';
});

// Регистрация нового Front-Desk оператора
document.getElementById('operatorRegisterForm').addEventListener('submit', (e) => {
  e.preventDefault();
  const fullName = document.getElementById('regOpFullName').value.trim();
  const username = document.getElementById('regOpUsername').value.trim();
  const role = document.getElementById('regOpRole').value;
  const password = document.getElementById('regOpPassword').value;
  const authMsg = document.getElementById('authMsg');

  const operators = getOperators();
  if (operators.some(op => op.username.toLowerCase() === username.toLowerCase())) {
    authMsg.textContent = `❌ Username "${username}" is already registered.`;
    authMsg.className = 'msg error';
    return;
  }

  const newOperator = { username, password, fullName, role };
  operators.push(newOperator);
  saveOperators(operators);
  sessionStorage.setItem('pms_active_operator', JSON.stringify(newOperator));
  e.target.reset();
  checkOperatorSession();
});

// Вход существующего Front-Desk оператора
document.getElementById('operatorLoginForm').addEventListener('submit', (e) => {
  e.preventDefault();
  const username = document.getElementById('loginUsername').value.trim();
  const password = document.getElementById('loginPassword').value;
  const authMsg = document.getElementById('authMsg');

  const operators = getOperators();
  const found = operators.find(
    op => op.username.toLowerCase() === username.toLowerCase() && op.password === password
  );

  if (!found) {
    authMsg.textContent = '❌ Invalid username or password. Register a new account or use admin / 1234.';
    authMsg.className = 'msg error';
    return;
  }

  sessionStorage.setItem('pms_active_operator', JSON.stringify(found));
  e.target.reset();
  checkOperatorSession();
});

// Выход из аккаунта (Sign out)
document.getElementById('logoutBtn').addEventListener('click', () => {
  sessionStorage.removeItem('pms_active_operator');
  checkOperatorSession();
});

// Стартовые отзывы об отеле в стиле 2GIS
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

// Переключение разделов портала
function switchTab(tabName) {
  const sections = {
    checkin: ['checkin-section', 'priority-queue-section'],
    booking: ['booking-section'],
    refunds: ['refunds-section'],
    reviews: ['reviews-section']
  };

  const titles = {
    checkin: 'Front-Desk Operations & Arrival Management',
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

// Загрузка списка комнат
async function loadBookingOptions() {
  const select = document.getElementById('bookingRoomId');
  if (!select) return;

  try {
    const response = await fetch(`${API_BASE_URL}/rooms`);
    if (response.ok) {
      const rooms = await response.json();
      select.innerHTML = '<option value="">Select a room...</option>';
      rooms.forEach(room => {
        const option = document.createElement('option');
        option.value = room.roomId;
        option.textContent = `Room ${room.roomNumber} (${room.roomType || 'Standard'} — ${room.cleaningState})`;
        select.appendChild(option);
      });
      return;
    }
  } catch (err) {
    console.warn('Could not load rooms:', err);
  }
  select.innerHTML = '<option value="">No rooms available</option>';
}

// Создание новой брони
const bookingForm = document.getElementById('bookingForm');
if (bookingForm) {
  bookingForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const passport = document.getElementById('bookingGuestPassport').value.trim().toUpperCase();
    const roomId = Number(document.getElementById('bookingRoomId').value);
    const expectedArrival = document.getElementById('bookingExpectedArrival').value;
    const bookingMsg = document.getElementById('bookingMsg');

    try {
      const guestsRes = await fetch(`${API_BASE_URL}/guests`);
      const guests = guestsRes.ok ? await guestsRes.json() : [];
      const guest = guests.find(g => g.passportNum === passport);

      if (!guest) {
        bookingMsg.textContent = `❌ Guest with passport ${passport} not found in database.`;
        bookingMsg.className = 'msg error';
        return;
      }

      const response = await fetch(`${API_BASE_URL}/bookings`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          guestId: guest.guestId,
          roomId: roomId,
          expectedArrival: expectedArrival,
          state: 'CONFIRMED'
        })
      });

      if (response.ok) {
        bookingMsg.textContent = `✓ Booking created for ${guest.firstName} ${guest.lastName}!`;
        bookingMsg.className = 'msg success';
        bookingForm.reset();
        loadPriorityQueue();
        return;
      }
    } catch (err) {
      console.warn('Booking error:', err);
    }

    bookingMsg.textContent = '❌ Failed to create booking.';
    bookingMsg.className = 'msg error';
  });
}

// Очередь раннего заселения
async function loadPriorityQueue() {
  const tbody = document.getElementById('queueTbody');
  tbody.innerHTML = '';

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

// Автозаселение
async function autoAssign(bookingId, roomNumber) {
  try {
    const response = await fetch(`${API_BASE_URL}/bookings/${bookingId}/check-in`, {
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
  alert('❌ Could not complete check-in.');
}

// Проверка паспорта
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
    console.warn('Validation error:', err);
  }

  msg.textContent = '❌ Server connection error during passport lookup.';
  msg.className = 'msg error';
});

// Возвраты (Refunds)
async function loadRefunds() {
  const tbody = document.getElementById('refundsTbody');
  tbody.innerHTML = '';

  try {
    const response = await fetch(`${API_BASE_URL}/refunds`);
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

document.getElementById('refundForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const bookingId = Number(document.getElementById('refundBookingId').value);
  const amount = parseFloat(document.getElementById('refundAmount').value);
  const reason = document.getElementById('refundReason').value;
  const refundMsg = document.getElementById('refundMsg');
  const btn = document.getElementById('refundSubmitBtn');

  btn.disabled = true;
  btn.textContent = 'Saving...';

  try {
    const response = await fetch(`${API_BASE_URL}/refunds`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        bookingId,
        booking: { bookingId },
        amount,
        refundReason: reason,
        state: 'COMPLETED'
      })
    });

    if (response.ok) {
      refundMsg.textContent = `✓ Refund of $${amount.toFixed(2)} for Booking #${bookingId} saved to Database!`;
      refundMsg.className = 'msg success';
      document.getElementById('refundForm').reset();
      loadRefunds();
    } else {
      refundMsg.textContent = `❌ Error (${response.status}): Booking #${bookingId} does not exist or invalid refund data.`;
      refundMsg.className = 'msg error';
    }
  } catch (err) {
    refundMsg.textContent = '❌ Server connection error.';
    refundMsg.className = 'msg error';
  } finally {
    btn.disabled = false;
    btn.textContent = 'Issue Refund';
  }
});

async function cancelRefund(refundId) {
  if (!confirm(`Cancel and remove Refund #${refundId} from database?`)) return;

  const refundMsg = document.getElementById('refundMsg');
  try {
    const response = await fetch(`${API_BASE_URL}/refunds/${refundId}`, { method: 'DELETE' });
    if (response.ok) {
      refundMsg.textContent = `✓ Refund #${refundId} cancelled and removed from Database!`;
      refundMsg.className = 'msg success';
      loadRefunds();
      return;
    }
  } catch (err) {
    console.warn('Delete error:', err);
  }

  refundMsg.textContent = `❌ Failed to cancel Refund #${refundId}.`;
  refundMsg.className = 'msg error';
}

// 2GIS Отзывы и рейтинг об отеле
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

  listEl.innerHTML = '';
  hotelReviews.forEach(rev => {
    const initials = rev.author.split(' ').map(p => p[0]).join('').slice(0, 2).toUpperCase();
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

document.querySelectorAll('.star-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    selectedRating = Number(btn.dataset.rating);
    document.querySelectorAll('.star-btn').forEach(b => {
      b.classList.toggle('selected', Number(b.dataset.rating) === selectedRating);
    });
  });
});

const reviewForm = document.getElementById('reviewForm');
if (reviewForm) {
  reviewForm.addEventListener('submit', (e) => {
    e.preventDefault();
    const author = document.getElementById('reviewAuthor').value.trim();
    const comment = document.getElementById('reviewComment').value.trim();
    const reviewMsg = document.getElementById('reviewMsg');

    const today = new Date().toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
    hotelReviews.unshift({ id: Date.now(), author, rating: selectedRating, date: today, comment });
    localStorage.setItem('grand_almaty_2gis_reviews', JSON.stringify(hotelReviews));

    reviewMsg.textContent = '✓ Thank you! Your 2GIS review has been published and hotel rating updated.';
    reviewMsg.className = 'msg success';
    reviewForm.reset();
    renderReviews();
  });
}

// Запуск проверки сессии при загрузке страницы
checkOperatorSession();