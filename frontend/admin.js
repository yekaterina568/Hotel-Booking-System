const ADMIN_API = 'http://localhost:8080/api/admin';
const reviewStatuses = { PENDING: 'Pending', PUBLISHED: 'Published', HIDDEN: 'Hidden' };
const dateFormat = new Intl.DateTimeFormat('en', { dateStyle: 'medium' });
const dateTimeFormat = new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'short' });
const moneyFormat = new Intl.NumberFormat('en', { style: 'currency', currency: 'USD' });
const element = id => document.getElementById(id);

function displayValue(value) {
  return value == null || value === '' ? '—' : String(value);
}

function displayId(id) {
  return id == null ? '—' : `#${id}`;
}

function displayDateTime(value) {
  return value ? dateTimeFormat.format(new Date(value)) : '—';
}

function displayAmount(amount) {
  return amount == null ? '—' : moneyFormat.format(amount);
}

function displayStatus(status) {
  if (status == null) return '—';
  if (status === 'CHECKEDIN') return 'Checked in';
  if (status === 'CHECKEDOUT') return 'Checked out';
  return status.charAt(0) + status.slice(1).toLowerCase();
}

function displayActive(active) {
  return active == null ? '—' : active ? 'Active' : 'Inactive';
}

const bookingColumns = [
  ['Booking ID', 'bookingId', displayId],
  ['Guest ID', 'guestId', displayId],
  ['Room ID', 'roomId', displayId],
  ['Status', 'state', displayStatus],
  ['Expected arrival', 'expectedArrival', displayDateTime],
  ['Expected departure', 'expectedDeparture', displayDateTime],
  ['Total', 'totalPrice', displayAmount],
  ['Checked out', 'checkedOutAt', displayDateTime],
  ['Created', 'createdAt', displayDateTime]
];

const operationTables = {
  rooms: {
    title: 'Rooms', noun: 'room', sortKey: 'roomNumber', ascending: true, statusKey: 'cleaningState', actions: roomActions,
    columns: [
      ['Room number', 'roomNumber'],
      ['Room ID', 'roomId', displayId],
      ['Hotel', 'hotelId', (id, hotelsById) => hotelsById.get(id) ?? displayId(id)],
      ['Type', 'roomType', displayStatus],
      ['Cleaning state', 'cleaningState', displayStatus],
      ['Price per night', 'pricePerNight', displayAmount]
    ]
  },
  guests: {
    title: 'Guests', noun: 'guest', sortKey: 'guestId',
    columns: [
      ['Guest name', guest => `${guest.firstName} ${guest.lastName}`],
      ['Guest ID', 'guestId', displayId],
      ['Passport', 'passportNum'],
      ['Phone', 'phoneNum'],
      ['Registered', 'createdAt', displayDateTime]
    ]
  },
  bookings: {
    title: 'Bookings', noun: 'booking', statusKey: 'state', actions: bookingActions, serverPaged: true,
    columns: [
      ['Booking ID', 'bookingId', displayId], ['Guest ID', 'guestId', displayId], ['Hotel', 'hotel'],
      ['Room number', 'roomNumber'], ['Status', 'state', displayStatus],
      ['Expected arrival', 'expectedArrival', displayDateTime], ['Expected departure', 'expectedDeparture', displayDateTime]
    ]
  },
  'booking-archive': {
    title: 'Booking Archive', noun: 'archived booking', sortKey: 'bookingId', statusKey: 'state', actions: bookingActions,
    columns: [...bookingColumns, ['Archived', 'archivedAt', displayDateTime]]
  },
  refunds: {
    title: 'Refunds', noun: 'refund', sortKey: 'refundId', statusKey: 'state',
    columns: [
      ['Refund ID', 'refundId', displayId],
      ['Booking ID', 'bookingId', displayId],
      ['Amount', 'amount', displayAmount],
      ['Reason', 'refundReason'],
      ['Status', 'state', displayStatus],
      ['Processed', 'processedAt', displayDateTime]
    ]
  },
  keycards: {
    title: 'Keycards', noun: 'keycard', sortKey: 'keycardId', statusKey: 'isActive',
    columns: [
      ['Card UID', 'cardUid'],
      ['Keycard ID', 'keycardId', displayId],
      ['Booking ID', 'bookingId', displayId],
      ['Status', 'isActive', displayActive],
      ['Issued', 'issueTimestamp', displayDateTime]
    ]
  }
};

const signInForm = element('signInForm');
const signInFeedback = element('signInFeedback');
const dashboardFeedback = element('dashboardFeedback');
const hotelForm = element('hotelForm');
const reviewForm = element('reviewForm');
const deleteForm = element('deleteForm');
const hotelPanel = element('hotelPanel');
const reviewPanel = element('reviewPanel');
const operationPanel = element('operationPanel');
const operationSearch = element('operationSearch');
const operationStatus = element('operationStatus');
const operationHotel = element('operationHotel');
const operationRows = element('operationRows');
const operationResults = element('operationResults');
const previousPage = element('previousPage');
const nextPage = element('nextPage');
const addRecord = element('addRecord');
const refreshData = element('refreshData');
const signOut = element('signOut');

let credentials = hotelAuth.getCredentials();
let hotels = [];
let reviews = [];
let reviewsLoaded = false;
let directoryLoadId = 0;
let busy = false;
let editingHotelId = null;
let deletion = null;
let operationRecords = [];
let operationTotal = 0;
let operationSearchTimer;
let operationPage = 1;
let operationLoadId = 0;
const operationPageSize = 25;

async function request(path, { method = 'GET', body, raw = false } = {}, accessCredentials = credentials) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let response;
  try {
    response = await hotelAuth.fetch(`${ADMIN_API}${path}`, {
      method,
      headers,
      credentials: 'omit',
      signal: AbortSignal.timeout(raw ? 120000 : 15000),
      body: body === undefined ? undefined : JSON.stringify(body)
    }, accessCredentials);
  } catch {
    throw new Error('Could not reach the backend. Check that it is running and try again.');
  }

  if (response.ok) return raw ? response : response.status === 204 ? null : response.json();

  const problem = await response.json().catch(() => null);
  const message = {
    401: 'The username or password is incorrect.',
    403: 'This account does not have access to administration.'
  }[response.status] ?? problem?.detail ?? problem?.message ?? `Request failed (${response.status}). Try again.`;
  throw Object.assign(new Error(message), { status: response.status });
}

function showFeedback(target, message = '', tone = '') {
  target.textContent = message;
  target.className = tone ? `feedback-banner ${tone}` : 'feedback-banner';
}

function showSignedIn(signedIn) {
  element('signInPanel').hidden = signedIn;
  element('dashboard').hidden = !signedIn;
  element('adminNav').hidden = !signedIn;
  signOut.hidden = !signedIn;
}

function clearSession(message = '') {
  credentials = null;
  hotelAuth.clearCredentials();
  hotels = [];
  reviews = [];
  reviewsLoaded = false;
  directoryLoadId++;
  operationLoadId++;
  clearTimeout(operationSearchTimer);
  operationRecords = [];
  operationRows.replaceChildren();
  operationSearch.value = '';
  operationStatus.replaceChildren();
  operationResults.textContent = '';
  [hotelForm, reviewForm, deleteForm, element('roomForm')].forEach(form => {
    form.closest('dialog').close();
    form.reset();
  });
  ['hotelSearch', 'reviewSearch', 'hotelFilter', 'statusFilter'].forEach(id => {
    element(id).value = '';
  });
  resetBookingDetails();
  resetInsights();
  render();
  showSignedIn(false);
  showFeedback(dashboardFeedback);
  showFeedback(signInFeedback, message, message ? 'error' : '');
  element('adminUsername').focus();
}

async function loadDirectory() {
  const loadId = ++directoryLoadId;
  const status = dashboardFeedback;
  showFeedback(status, 'Loading hotel directory…');
  hotelPanel.inert = true;
  reviewPanel.inert = true;
  addRecord.disabled = true;
  try {
    const [updatedHotels, updatedReviews] = await Promise.all([request('/hotels'), request('/reviews')]);
    if (loadId !== directoryLoadId || !credentials) return;
    hotels = updatedHotels;
    reviews = updatedReviews;
    reviewsLoaded = true;
    render();
    if (['hotels', 'reviews'].includes(currentSection())) showFeedback(status);
  } catch (error) {
    if (loadId === directoryLoadId && credentials && ['hotels', 'reviews'].includes(currentSection())) {
      showFeedback(status, error.message, 'error');
    }
  } finally {
    if (loadId === directoryLoadId) {
      hotelPanel.inert = false;
      reviewPanel.inert = false;
      addRecord.disabled = busy;
    }
  }
}

signInForm.addEventListener('submit', async event => {
  event.preventDefault();
  const usernameInput = element('adminUsername');
  const passwordInput = element('adminPassword');
  const submit = signInForm.querySelector('button[type="submit"]');
  if (submit.disabled) return;

  submit.disabled = true;
  submit.textContent = 'Signing in…';
  showFeedback(signInFeedback);
  try {
    const candidate = { username: usernameInput.value.trim(), password: passwordInput.value };
    await hotelAuth.authenticate(candidate.username, candidate.password);
    hotels = await request('/hotels', {}, candidate);
    credentials = candidate;
    hotelAuth.setCredentials(candidate);
    signInForm.reset();
    showSignedIn(true);
    render();
    showSection(true);
  } catch (error) {
    showFeedback(signInFeedback, error.message, 'error');
    usernameInput.focus();
  } finally {
    submit.disabled = false;
    submit.textContent = 'Sign in';
  }
});

signOut.addEventListener('click', () => clearSession());

function currentSection() {
  const section = location.hash.slice(1);
  return ['hotels', 'reviews', 'reports'].includes(section) || Object.hasOwn(operationTables, section)
    ? section : 'dashboard';
}

function showSection(moveFocus = false) {
  const section = currentSection();
  const table = operationTables[section];
  operationLoadId++;
  clearTimeout(operationSearchTimer);
  hotelPanel.hidden = section !== 'hotels';
  reviewPanel.hidden = section !== 'reviews';
  operationPanel.hidden = !table;
  element('overviewPanel').hidden = section !== 'dashboard';
  element('reportsPanel').hidden = section !== 'reports';
  refreshData.hidden = section === 'reports';
  const heading = element('dashboardHeading');
  heading.textContent = table?.title ?? { hotels: 'Hotels', reviews: 'Reviews', dashboard: 'Dashboard', reports: 'Reports' }[section];
  const addLabel = { hotels: 'Add hotel', reviews: 'Add review', rooms: 'Add room' }[section];
  addRecord.hidden = !addLabel;
  addRecord.textContent = addLabel ?? '';
  element('adminNav').querySelectorAll('a').forEach(link => {
    if (link.hash === '#' + section) link.setAttribute('aria-current', 'page');
    else link.removeAttribute('aria-current');
  });
  if (moveFocus) heading.focus();
  if (section === 'dashboard') loadDashboard();
  if (table) {
    configureOperationTable(table);
    loadOperationRecords(section);
  }
  if (['hotels', 'reviews'].includes(section) && !reviewsLoaded) loadDirectory();
}

window.addEventListener('hashchange', () => {
  if (credentials) {
    showFeedback(dashboardFeedback);
    showSection(true);
  }
});

function setBusy(value) {
  busy = value;
  refreshData.disabled = value;
  addRecord.disabled = value;
  signOut.disabled = value;
  hotelPanel.inert = value;
  reviewPanel.inert = value;
  operationPanel.inert = value;
}

async function perform(action, feedback) {
  if (busy) return false;

  setBusy(true);
  showFeedback(feedback);
  try {
    await action();
    return true;
  } catch (error) {
    if (error.status === 401) clearSession('Sign in again to continue.');
    else showFeedback(feedback, error.message, 'error');
    return false;
  } finally {
    setBusy(false);
  }
}

refreshData.addEventListener('click', async () => {
  const section = currentSection();
  if (operationTables[section]) await loadOperationRecords(section);
  else if (section === 'dashboard') await loadDashboard();
  else await loadDirectory();
  if (credentials && section === currentSection()) refreshData.focus();
});

function makeButton(label, className, handler) {
  const button = document.createElement('button');
  button.textContent = label;
  button.className = `btn ${className}`;
  button.type = 'button';
  button.addEventListener('click', handler);
  return button;
}

function addRow(rows) {
  const row = rows.insertRow();
  row.setAttribute('role', 'row');
  return row;
}

function addCell(row, value, { className = '', label } = {}) {
  const cell = row.insertCell();
  cell.setAttribute('role', 'cell');
  cell.className = className;
  if (label) cell.dataset.label = label;
  if (value instanceof Node) cell.append(value);
  else cell.textContent = value;
  return cell;
}

function addTitleCell(row, title, subtitle) {
  const heading = document.createElement('strong');
  heading.textContent = title;
  const cell = addCell(row, heading);
  if (subtitle) {
    const detail = document.createElement('p');
    detail.className = 'record-subtitle';
    detail.textContent = subtitle;
    cell.append(detail);
  }
}

function showEmpty(rows, columns, message) {
  const cell = addCell(addRow(rows), message, { className: 'empty-state' });
  cell.colSpan = columns;
}

function updateResultCount(id, shown, total, singular) {
  element(id).textContent = shown === total
    ? `${total} ${singular}${total === 1 ? '' : 's'}`
    : `Showing ${shown} of ${total} ${singular}${total === 1 ? '' : 's'}`;
}

function configureOperationTable(table) {
  element('operationSearchLabel').textContent = 'Search ' + table.noun + 's';
  operationSearch.placeholder = table.serverPaged ? 'Booking ID, guest ID, room number or hotel' : '';
  element('operationTableRegion').setAttribute('aria-label', table.title + ' table');
  element('operationStatusField').hidden = !table.statusKey;
  element('operationHotelField').hidden = !table.serverPaged;
  operationHotel.closest('.operation-filters').classList.toggle('has-hotel', Boolean(table.serverPaged));
  setHotelOptions(operationHotel, 'All hotels', '');
  const statusColumn = table.columns.find(([, key]) => key === table.statusKey);
  element('operationStatusLabel').textContent = statusColumn?.[0] ?? 'Status';
  const headings = table.columns.map(([label]) => label);
  if (table.actions) headings.push('Actions');
  setColumnHeadings(element('operationColumns'), headings);
  operationSearch.value = '';
  operationStatus.replaceChildren(new Option('All statuses', ''));
  if (table.serverPaged) operationStatus.append(...bookingStates.map(state => new Option(displayStatus(state), state)));
  operationPage = 1;
}

function updateOperationStatus(table) {
  const selected = operationStatus.value;
  operationStatus.replaceChildren(new Option('All statuses', ''));
  if (!table.statusKey) return;

  const [, key, format] = table.columns.find(([, columnKey]) => columnKey === table.statusKey);
  const values = new Set(operationRecords.map(record => record[key]));
  const options = [...values]
    .filter(value => value != null)
    .map(value => new Option(format(value), String(value)))
    .sort((left, right) => left.text.localeCompare(right.text));
  operationStatus.append(...options);
  if (options.some(option => option.value === selected)) operationStatus.value = selected;
}

function showOperationMessage(table, message) {
  operationRows.replaceChildren();
  showEmpty(operationRows, table.columns.length + Number(Boolean(table.actions)), message);
  operationResults.textContent = '';
  element('operationPages').hidden = true;
  operationSearch.disabled = !table.serverPaged;
  operationStatus.disabled = !table.serverPaged;
}

async function loadOperationRecords(section) {
  const table = operationTables[section];
  const loadId = ++operationLoadId;
  const isCurrent = () => loadId === operationLoadId && section === currentSection() && Boolean(credentials);
  showOperationMessage(table, 'Loading records…');

  try {
    if (table.serverPaged) {
      const filters = new URLSearchParams({ page: operationPage });
      if (operationSearch.value.trim()) filters.set('search', operationSearch.value.trim());
      if (operationStatus.value) filters.set('state', operationStatus.value);
      if (operationHotel.value) filters.set('hotelId', operationHotel.value);
      const page = await request('/bookings/search?' + filters);
      if (!isCurrent()) return;
      operationRecords = page.rows;
      operationTotal = page.total;
      if (operationPage > 1 && !operationRecords.length) {
        operationPage = Math.max(1, Math.ceil(operationTotal / operationPageSize));
        return loadOperationRecords(section);
      }
    } else {
      const records = await request('/' + section);
      if (!isCurrent()) return;
      const direction = table.ascending ? 1 : -1;
      operationRecords = records.sort((left, right) => direction * (left[table.sortKey] - right[table.sortKey]));
      updateOperationStatus(table);
    }
    operationSearch.disabled = false;
    operationStatus.disabled = false;
    renderOperationRecords();
  } catch (error) {
    if (!isCurrent()) return;
    showOperationMessage(table, error.message);
  }
}

function renderOperationRecords() {
  const table = operationTables[currentSection()];
  if (!table) return;
  const search = operationSearch.value.trim().toLowerCase();
  const status = operationStatus.value;
  const hotelsById = new Map(hotels.map(hotel => [hotel.hotelId, hotel.name]));
  const formatted = operationRecords
    .filter(record => table.serverPaged || !status || String(record[table.statusKey]) === status)
    .map(record => ({
      record,
      values: table.columns.map(([, field, format = displayValue]) =>
        format(typeof field === 'function' ? field(record) : record[field], hotelsById))
    }))
    .filter(({ values }) => table.serverPaged || values.some(value => String(value).toLowerCase().includes(search)));

  const total = table.serverPaged ? operationTotal : formatted.length;
  const pages = Math.max(1, Math.ceil(total / operationPageSize));
  operationPage = Math.min(operationPage, pages);
  const start = (operationPage - 1) * operationPageSize;
  const shown = table.serverPaged ? formatted : formatted.slice(start, start + operationPageSize);
  operationRows.replaceChildren();
  shown.forEach(({ record, values }) => {
    const row = addRow(operationRows);
    values.forEach((value, index) => addCell(row, value, { label: table.columns[index][0] }));
    if (table.actions) {
      const cell = addCell(row, '', { className: 'operation-actions', label: 'Actions' });
      table.actions(cell, record);
    }
  });

  if (!shown.length) {
    const filtered = search || status || (table.serverPaged && operationHotel.value);
    const message = filtered ? 'No records match your filters.' : 'No ' + table.noun + 's yet.';
    showEmpty(operationRows, table.columns.length + Number(Boolean(table.actions)), message);
  }
  if (pages > 1) {
    operationResults.textContent = 'Showing ' + (start + 1) + '–' + (start + shown.length) + ' of ' + total;
  } else {
    updateResultCount('operationResults', total, table.serverPaged ? total : operationRecords.length, table.noun);
  }
  element('operationPages').hidden = pages < 2;
  element('pageNumber').textContent = 'Page ' + operationPage + ' of ' + pages;
  previousPage.disabled = operationPage === 1;
  nextPage.disabled = operationPage === pages;
}

function applyOperationFilters(debounce = false) {
  operationPage = 1;
  clearTimeout(operationSearchTimer);
  if (currentSection() !== 'bookings') {
    renderOperationRecords();
    return;
  }
  operationLoadId++;
  if (debounce) operationSearchTimer = setTimeout(() => loadOperationRecords('bookings'), 250);
  else loadOperationRecords('bookings');
}

async function changeOperationPage(offset) {
  operationPage += offset;
  if (currentSection() === 'bookings') {
    await loadOperationRecords('bookings');
    if (currentSection() === 'bookings') element('operationTableRegion').focus();
  } else {
    renderOperationRecords();
    if (previousPage.disabled) nextPage.focus();
    if (nextPage.disabled) previousPage.focus();
  }
}

operationSearch.addEventListener('input', () => applyOperationFilters(true));
operationStatus.addEventListener('change', () => applyOperationFilters());
operationHotel.addEventListener('change', () => applyOperationFilters());
previousPage.addEventListener('click', () => changeOperationPage(-1));
nextPage.addEventListener('click', () => changeOperationPage(1));

function renderHotels() {
  const rows = element('hotelRows');
  const search = element('hotelSearch').value.trim().toLowerCase();
  const visible = hotels.filter(hotel => [hotel.name, hotel.city, hotel.address]
    .some(value => value?.toLowerCase().includes(search)));
  const reviewCounts = new Map();
  reviews.forEach(review => {
    reviewCounts.set(review.hotelId, (reviewCounts.get(review.hotelId) ?? 0) + 1);
  });

  rows.replaceChildren();
  visible.forEach(hotel => {
    const row = addRow(rows);
    addTitleCell(row, hotel.name, hotel.defaultHotel ? 'Default hotel' : '');
    addCell(row, hotel.city, { label: 'City' });
    addCell(row, hotel.address || '—', { label: 'Address' });
    addCell(row, reviewCounts.get(hotel.hotelId) ?? 0, { label: 'Reviews' });
    const actions = document.createElement('div');
    actions.className = 'row-actions';
    addCell(row, actions);
    actions.append(makeButton('Edit', 'btn-secondary', () => openHotelEditor(hotel)));
    if (!hotel.defaultHotel) {
      actions.append(makeButton('Delete', 'btn-danger', () => openDelete('hotel', hotel)));
    }
  });
  if (!visible.length) {
    showEmpty(rows, 5, hotels.length ? 'No hotels match your search.' : 'No hotels yet.');
  }
  updateResultCount('hotelResults', visible.length, hotels.length, 'hotel');
}

function reviewStatusControl(review) {
  const select = document.createElement('select');
  select.className = `status-select status-${review.status.toLowerCase()}`;
  select.setAttribute('aria-label', `Status of review by ${review.authorName}`);
  select.dataset.reviewId = review.reviewId;
  Object.entries(reviewStatuses).forEach(([value, label]) => select.add(new Option(label, value)));
  select.value = review.status;
  select.addEventListener('change', async () => {
    const status = select.value;
    const updated = await perform(async () => {
      const saved = await request(`/reviews/${review.reviewId}/status`, { method: 'PUT', body: { status } });
      reviews = reviews.map(item => item.reviewId === saved.reviewId ? saved : item);
    }, dashboardFeedback);
    if (updated) {
      render();
      showFeedback(dashboardFeedback, `Review status changed to ${reviewStatuses[status].toLowerCase()}.`, 'success');
    } else {
      select.value = review.status;
    }
    if (credentials) {
      const control = element('reviewRows').querySelector(`[data-review-id="${review.reviewId}"]`);
      (control ?? element('statusFilter')).focus();
    }
  });
  return select;
}

function renderReviews() {
  const rows = element('reviewRows');
  const search = element('reviewSearch').value.trim().toLowerCase();
  const hotel = element('hotelFilter').value;
  const status = element('statusFilter').value;
  const hotelNames = new Map(hotels.map(item => [item.hotelId, item.name]));
  const visible = reviews.filter(review =>
    (!hotel || String(review.hotelId) === hotel)
    && (!status || review.status === status)
    && [review.authorName, review.comment].some(value => value.toLowerCase().includes(search))
  );

  rows.replaceChildren();
  visible.forEach(review => {
    const row = addRow(rows);
    addTitleCell(row, review.authorName, review.comment);
    addCell(row, hotelNames.get(review.hotelId) ?? `#${review.hotelId}`, { label: 'Hotel' });
    addCell(row, `${review.rating} / 5`, { label: 'Rating', className: 'rating-cell' });
    addCell(row, reviewStatusControl(review), { label: 'Status' });
    addCell(row, dateFormat.format(new Date(review.createdAt)), { label: 'Date', className: 'date-cell' });
    addCell(row, makeButton('Delete', 'btn-danger', () => openDelete('review', review)), { className: 'review-actions' });
  });
  if (!visible.length) {
    showEmpty(rows, 6, reviews.length ? 'No reviews match the current filters.' : 'No reviews yet.');
  }
  updateResultCount('reviewResults', visible.length, reviews.length, 'review');
}

function setHotelOptions(select, placeholder, selected) {
  select.replaceChildren(new Option(placeholder, ''));
  hotels.forEach(hotel => select.add(new Option(hotel.name, String(hotel.hotelId))));
  select.value = hotels.some(hotel => String(hotel.hotelId) === selected)
    ? selected : '';
}

function render() {
  hotels.sort((left, right) => left.name.localeCompare(right.name) || left.hotelId - right.hotelId);
  const pending = reviews.filter(review => review.status === 'PENDING').length;
  const navPending = element('navPending');
  navPending.textContent = pending;
  navPending.hidden = pending === 0;

  const hotelFilter = element('hotelFilter');
  setHotelOptions(hotelFilter, 'All hotels', hotelFilter.value);
  updateInsightsHotelOptions();
  renderHotels();
  renderReviews();
}

element('hotelSearch').addEventListener('input', renderHotels);
element('reviewSearch').addEventListener('input', renderReviews);
element('hotelFilter').addEventListener('change', renderReviews);
element('statusFilter').addEventListener('change', renderReviews);

function openDialog(form) {
  showFeedback(form.querySelector('.feedback-banner'));
  form.closest('dialog').showModal();
}

function openHotelEditor(hotel) {
  hotelForm.reset();
  editingHotelId = hotel?.hotelId ?? null;
  element('hotelDialogHeading').textContent = hotel ? 'Edit hotel' : 'Add hotel';
  ['name', 'city', 'address', 'description'].forEach(name => {
    hotelForm.elements.namedItem(name).value = hotel?.[name] ?? '';
  });
  openDialog(hotelForm);
}

function openReviewEditor() {
  reviewForm.reset();
  const selection = element('hotelFilter').value || (hotels.length === 1 ? String(hotels[0].hotelId) : '');
  setHotelOptions(element('reviewHotel'), 'Select a hotel', selection);
  openDialog(reviewForm);
}

function openConfirmation(heading, description, action) {
  element('deleteHeading').textContent = heading;
  element('deleteDescription').textContent = description;
  element('confirmRecordAction').textContent = action;
  openDialog(deleteForm);
}

function openDelete(type, record) {
  const isHotel = type === 'hotel';
  deletion = { type, id: isHotel ? record.hotelId : record.reviewId };
  const description = isHotel
    ? '“' + record.name + '” will be permanently deleted. Hotels with rooms or reviews cannot be deleted.'
    : 'The review by ' + record.authorName + ' will be permanently deleted.';
  openConfirmation(isHotel ? 'Delete hotel?' : 'Delete review?', description, 'Delete');
}

addRecord.addEventListener('click', () => {
  const section = currentSection();
  if (section === 'rooms') openRoomEditor();
  else if (section === 'reviews') openReviewEditor();
  else openHotelEditor();
});

function handleDialogSubmit(form, action) {
  const dialog = form.closest('dialog');
  const buttons = form.querySelectorAll('button');
  form.querySelector('[data-close]').addEventListener('click', () => dialog.close());
  dialog.addEventListener('cancel', event => {
    if (busy) event.preventDefault();
  });
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (busy) return;
    buttons.forEach(button => { button.disabled = true; });
    let message;
    const updated = await perform(async () => {
      message = await action(Object.fromEntries(new FormData(form)));
    }, form.querySelector('.feedback-banner'));
    buttons.forEach(button => { button.disabled = false; });
    if (updated) {
      dialog.close();
      render();
      showFeedback(dashboardFeedback, message, 'success');
      (addRecord.hidden ? element('dashboardHeading') : addRecord).focus();
    } else if (dialog.open) {
      form.querySelector('button[type="submit"]').focus();
    }
  });
}

handleDialogSubmit(hotelForm, async body => {
  const creating = editingHotelId === null;
  const path = creating ? '/hotels' : `/hotels/${editingHotelId}`;
  const saved = await request(path, { method: creating ? 'POST' : 'PUT', body });
  if (creating) {
    hotels.push(saved);
    element('hotelSearch').value = '';
  } else {
    hotels = hotels.map(hotel => hotel.hotelId === saved.hotelId ? saved : hotel);
  }
  return creating ? 'Hotel added.' : 'Hotel updated.';
});

handleDialogSubmit(reviewForm, async body => {
  body.hotelId = Number(body.hotelId);
  body.rating = Number(body.rating);
  const saved = await request('/reviews', { method: 'POST', body });
  reviews.unshift(saved);
  element('reviewSearch').value = '';
  element('hotelFilter').value = String(saved.hotelId);
  element('statusFilter').value = 'PENDING';
  return 'Review added to the moderation queue.';
});

handleDialogSubmit(deleteForm, async () => {
  const { type, id, action } = deletion;
  if (type === 'booking') {
    await request('/bookings/' + id + '/' + action, { method: 'POST' });
    await loadOperationRecords('bookings');
    return action === 'cancel' ? 'Booking cancelled.' : 'Booking archived.';
  }
  await request(`/${type}s/${id}`, { method: 'DELETE' });
  if (type === 'room') {
    await loadOperationRecords('rooms');
    return 'Room deleted.';
  }
  if (type === 'hotel') {
    hotels = hotels.filter(hotel => hotel.hotelId !== id);
    return 'Hotel deleted.';
  }
  reviews = reviews.filter(review => review.reviewId !== id);
  return 'Review deleted.';
});

window.addEventListener('hotel-auth-required', () => clearSession('Sign in again to continue.'));

async function restoreSession() {
  if (!credentials) {
    showSignedIn(false);
    return;
  }
  try {
    hotels = await request('/hotels');
    showSignedIn(true);
    render();
    showSection();
  } catch (error) {
    clearSession(error.message);
  }
}

initInsights();
initManagement();
signInForm.querySelector('button[type="submit"]').disabled = false;
restoreSession();
