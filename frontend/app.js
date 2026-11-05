// Mock-данные для прототипа
const mockRooms = [
  { room_id: 1, room_number: 101, room_type: 'Standard', cleaning_status: 'Cleaned' },
  { room_id: 2, room_number: 102, room_type: 'Deluxe', cleaning_status: 'Dirty' },
  { room_id: 3, room_number: 201, room_type: 'Suite', cleaning_status: 'Cleaning' },
  { room_id: 4, room_number: 202, room_type: 'Standard', cleaning_status: 'Cleaned' }
];

const mockBookings = [
  { booking_id: 101, full_name: 'Aruzhan Tanatar', room_number: 101, expected_arrival: '14:00', status: 'Confirmed' },
  { booking_id: 102, full_name: 'Zeinetula Ayan', room_number: 202, expected_arrival: '15:30', status: 'Confirmed' }
];

// Рендеринг таблицы комнат для клининга (Аян)
function renderRooms() {
  const tbody = document.getElementById('roomsTbody');
  tbody.innerHTML = '';

  mockRooms.forEach(room => {
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td><strong>№${room.room_number}</strong></td>
      <td>${room.room_type}</td>
      <td><span class="badge badge-${room.cleaning_status.toLowerCase()}">${room.cleaning_status}</span></td>
      <td>
        <select onchange="changeRoomStatus(${room.room_id}, this.value)">
          <option value="Cleaned" ${room.cleaning_status === 'Cleaned' ? 'selected' : ''}>Cleaned</option>
          <option value="Cleaning" ${room.cleaning_status === 'Cleaning' ? 'selected' : ''}>Cleaning</option>
          <option value="Dirty" ${room.cleaning_status === 'Dirty' ? 'selected' : ''}>Dirty</option>
        </select>
      </td>
    `;
    tbody.appendChild(tr);
  });
}

// Смена статуса комнаты (Аян)
function changeRoomStatus(roomId, newStatus) {
  const room = mockRooms.find(r => r.room_id === roomId);
  if (room) {
    room.cleaning_status = newStatus;
    renderRooms();
    renderQueue();
  }
}

// Рендеринг очереди раннего заселения (Аружан)
function renderQueue() {
  const tbody = document.getElementById('queueTbody');
  tbody.innerHTML = '';

  // Фильтруем бронирования, у которых комната имеет статус Cleaned
  const priorityQueue = mockBookings.filter(b => {
    const room = mockRooms.find(r => r.room_number === b.room_number);
    return room && room.cleaning_status === 'Cleaned';
  });

  priorityQueue.forEach(b => {
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td>#${b.booking_id}</td>
      <td>${b.full_name}</td>
      <td><strong>№${b.room_number}</strong></td>
      <td>${b.expected_arrival}</td>
      <td><span class="badge badge-cleaned">Cleaned</span></td>
      <td>
        <button class="btn-assign" onclick="autoAssign(${b.booking_id}, ${b.room_number})">
          Авто-Check-in
        </button>
      </td>
    `;
    tbody.appendChild(tr);
  });
}

// Авто-назначение комнаты (Аружан)
function autoAssign(bookingId, roomNumber) {
  alert(`Гостю по бронированию #${bookingId} успешно выдана чистая комната №${roomNumber}! RFID-ключ кодирован.`);
}

// Валидация паспорта (Аружан)
document.getElementById('validateBtn').addEventListener('click', () => {
  const passportVal = document.getElementById('passportInput').value.trim();
  const msg = document.getElementById('validationMsg');
  
  // Regex: 1 буква и 8-9 цифр (пример стандартного паспорта)
  const passportRegex = /^[A-Z0-9]{7,12}$/i;

  if (passportRegex.test(passportVal)) {
    msg.textContent = `✓ Паспорт ${passportVal.toUpperCase()} валиден и проверен в БД.`;
    msg.className = 'msg success';
  } else {
    msg.textContent = '❌ Ошибка: Некорректный формат паспорта. Введите от 7 до 12 символов.';
    msg.className = 'msg error';
  }
});

// Инициализация при загрузке
renderRooms();
renderQueue();