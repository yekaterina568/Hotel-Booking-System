// Mock data matching Task 9 Database Schema
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

// Render Rooms Table for Housekeeping (Ayan)
function renderRooms() {
  const tbody = document.getElementById('roomsTbody');
  tbody.innerHTML = '';

  mockRooms.forEach(room => {
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td><strong>No. ${room.room_number}</strong></td>
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

// Change Room Cleaning Status (Ayan)
function changeRoomStatus(roomId, newStatus) {
  const room = mockRooms.find(r => r.room_id === roomId);
  if (room) {
    room.cleaning_status = newStatus;
    renderRooms();
    renderQueue();
  }
}

// Render Priority Queue for Front-Desk (Aruzhan)
function renderQueue() {
  const tbody = document.getElementById('queueTbody');
  tbody.innerHTML = '';

  // Filter bookings where assigned room status is 'Cleaned'
  const priorityQueue = mockBookings.filter(b => {
    const room = mockRooms.find(r => r.room_number === b.room_number);
    return room && room.cleaning_status === 'Cleaned';
  });

  priorityQueue.forEach(b => {
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td>#${b.booking_id}</td>
      <td>${b.full_name}</td>
      <td><strong>No. ${b.room_number}</strong></td>
      <td>${b.expected_arrival}</td>
      <td><span class="badge badge-cleaned">Cleaned</span></td>
      <td>
        <button class="btn-assign" onclick="autoAssign(${b.booking_id}, ${b.room_number})">
          Auto Check-in
        </button>
      </td>
    `;
    tbody.appendChild(tr);
  });
}

// Auto Check-in Action (Aruzhan)
function autoAssign(bookingId, roomNumber) {
  alert(`Guest booking #${bookingId} successfully checked in to Room No. ${roomNumber}! RFID Keycard encoded.`);
}

// Passport Regex Validation (Aruzhan / Ayan)
document.getElementById('validateBtn').addEventListener('click', () => {
  const passportVal = document.getElementById('passportInput').value.trim();
  const msg = document.getElementById('validationMsg');
  
  // Regex: 1-2 letters + 7-9 digits
  const passportRegex = /^[A-Z0-9]{7,12}$/i;

  if (passportRegex.test(passportVal)) {
    msg.textContent = `✓ Passport ${passportVal.toUpperCase()} is valid and verified in DB.`;
    msg.className = 'msg success';
  } else {
    msg.textContent = '❌ Error: Invalid passport format. Expected 7 to 12 alphanumeric characters.';
    msg.className = 'msg error';
  }
});

// Initial Render
renderRooms();
renderQueue();