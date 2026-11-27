const roomTypes = ['SINGLE', 'DOUBLE', 'TWIN', 'TRIPLE', 'FAMILY', 'SUITE', 'DELUXE', 'PRESIDENTIAL'];
let editingRoomId = null;
let bookingDetailRequest = 0;

function initManagement() {
  element('roomType').append(...roomTypes.map(type => new Option(displayStatus(type), type)));
  element('roomCleaning').append(...roomCleaningStates.map(state => new Option(displayStatus(state), state)));
  handleDialogSubmit(element('roomForm'), async body => {
    const creating = editingRoomId === null;
    const url = creating ? '/rooms' : '/rooms/' + editingRoomId;
    body.hotelId = Number(body.hotelId);
    body.roomNumber = Number(body.roomNumber);
    body.pricePerNight = Number(body.pricePerNight);
    await request(url, { method: creating ? 'POST' : 'PUT', body });
    element('operationSearch').value = '';
    element('operationStatus').value = '';
    operationPage = 1;
    await loadOperationRecords('rooms');
    return creating ? 'Room added.' : 'Room updated.';
  });
  element('bookingDialog').addEventListener('close', () => { bookingDetailRequest++; });
}

function openRoomEditor(room) {
  const form = element('roomForm');
  form.reset();
  editingRoomId = room?.roomId ?? null;
  element('roomDialogHeading').textContent = room ? 'Edit room' : 'Add room';
  const selectedHotel = room?.hotelId ?? (hotels.length === 1 ? hotels[0].hotelId : '');
  setHotelOptions(element('roomHotel'), 'Select a hotel', String(selectedHotel));
  form.elements.namedItem('roomNumber').value = room?.roomNumber ?? '';
  form.elements.namedItem('roomType').value = room?.roomType ?? 'SINGLE';
  form.elements.namedItem('cleaningState').value = room?.cleaningState ?? 'DIRTY';
  form.elements.namedItem('pricePerNight').value = room?.pricePerNight ?? '';
  openDialog(form);
}

function roomActions(cell, room) {
  cell.append(
    makeButton('Edit', 'btn-secondary', () => openRoomEditor(room)),
    makeButton('Delete', 'btn-danger', () => {
      deletion = { type: 'room', id: room.roomId };
      openConfirmation('Delete room?', 'Room ' + room.roomNumber
        + ' will be deleted if it has no current or archived bookings.', 'Delete room');
    })
  );
}

function bookingActions(cell, booking) {
  cell.append(makeButton('View details', 'btn-secondary', () => openBookingDetails(booking.bookingId)));
  const state = booking.state;
  const action = state === 'CONFIRMED' ? 'cancel' : ['CANCELLED', 'CHECKEDOUT'].includes(state) ? 'archive' : null;
  if (!action) return;
  const label = action === 'cancel' ? 'Cancel booking' : 'Archive booking';
  cell.append(makeButton(label, action === 'cancel' ? 'btn-danger' : 'btn-secondary', () => {
    deletion = { type: 'booking', id: booking.bookingId, action };
    openConfirmation(label + '?', 'Booking #' + booking.bookingId
      + (action === 'cancel' ? ' will be cancelled and the room released. Issue any eligible refund before archiving.' : ' will move to Booking Archive.'), label);
  }));
}

async function openBookingDetails(id) {
  const dialog = element('bookingDialog');
  const status = element('bookingDialogStatus');
  const content = element('bookingDetailContent');
  const loadId = ++bookingDetailRequest;
  element('bookingDialogHeading').textContent = 'Booking #' + id;
  content.hidden = true;
  showFeedback(status, 'Loading booking…');
  dialog.showModal();
  try {
    const detail = await request('/bookings/' + id);
    if (!dialog.open || loadId !== bookingDetailRequest) return;
    const row = detail.booking;
    renderFields(element('bookingDetailValues'), [
      { name: 'Guest ID', value: displayId(row.guestId) },
      { name: 'Hotel', value: row.hotel },
      { name: 'Room', value: row.roomNumber },
      { name: 'Current status', value: displayStatus(row.state) },
      { name: 'Created', value: displayDateTime(row.createdAt) },
      { name: 'Expected arrival', value: displayDateTime(row.expectedArrival) },
      { name: 'Expected departure', value: displayDateTime(row.expectedDeparture) },
      { name: 'Checked out', value: displayDateTime(row.checkedOutAt) },
      { name: 'Archived', value: displayDateTime(detail.archivedAt) }
    ]);
    const history = element('bookingHistory');
    history.replaceChildren();
    for (const event of detail.history) {
      const item = document.createElement('li');
      item.textContent = displayStatus(event.previousState) + ' → ' + displayStatus(event.newState)
        + ' · ' + displayDateTime(event.changedAt);
      history.append(item);
    }
    if (!detail.history.length) {
      const item = document.createElement('li');
      item.textContent = 'No recorded status changes.';
      history.append(item);
    }
    showFeedback(status);
    content.hidden = false;
  } catch (error) {
    if (dialog.open && loadId === bookingDetailRequest) showFeedback(status, error.message, 'error');
  }
}

function resetBookingDetails() {
  bookingDetailRequest++;
  element('bookingDialog').close();
  element('bookingDetailValues').replaceChildren();
  element('bookingHistory').replaceChildren();
}
