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
    title: 'Rooms', noun: 'room', sortKey: 'roomNumber', ascending: true, statusKey: 'cleaningState',
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
    title: 'Bookings', noun: 'booking', sortKey: 'bookingId', statusKey: 'state',
    columns: bookingColumns
  },
  'booking-archive': {
    title: 'Booking archive', noun: 'archived booking', sortKey: 'bookingId', statusKey: 'state',
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
let busy = false;
let editingHotelId = null;
let deletion = null;
let operationRecords = [];
let operationPage = 1;
let operationLoadId = 0;
const operationPageSize = 25;

async function request(path, { method = 'GET', body } = {}, accessCredentials = credentials) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let response;
  try {
    response = await hotelAuth.fetch(`${ADMIN_API}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body)
    }, accessCredentials);
  } catch {
    throw new Error('Could not reach the backend. Check that it is running and try again.');
  }

  if (response.ok) return response.status === 204 ? null : response.json();

  const problem = await response.json().catch(() => null);
  const message = {
    401: 'The username or password is incorrect.'
  }[response.status] ?? problem?.detail ?? `Request failed (${response.status}). Try again.`;
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
  operationLoadId++;
  operationRecords = [];
  operationRows.replaceChildren();
  operationSearch.value = '';
  operationStatus.replaceChildren();
  operationResults.textContent = '';
  [hotelForm, reviewForm, deleteForm].forEach(form => {
    form.closest('dialog').close();
    form.reset();
  });
  ['hotelSearch', 'reviewSearch', 'hotelFilter', 'statusFilter'].forEach(id => {
    element(id).value = '';
  });
  render();
  showSignedIn(false);
  showFeedback(dashboardFeedback);
  showFeedback(signInFeedback, message, message ? 'error' : '');
  element('adminUsername').focus();
}

async function loadRecords(accessCredentials) {
  [hotels, reviews] = await Promise.all([
    request('/hotels', {}, accessCredentials),
    request('/reviews', {}, accessCredentials)
  ]);
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
    await loadRecords(candidate);
    credentials = candidate;
    hotelAuth.setCredentials(candidate);
    signInForm.reset();
    showSignedIn(true);
    showSection(true);
    render();
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
  return section === 'reviews' || Object.hasOwn(operationTables, section) ? section : 'hotels';
}

function showSection(moveFocus = false) {
  const section = currentSection();
  const table = operationTables[section];
  hotelPanel.hidden = section !== 'hotels';
  reviewPanel.hidden = section !== 'reviews';
  operationPanel.hidden = !table;
  element('adminMetrics').hidden = Boolean(table);
  const heading = element('dashboardHeading');
  heading.textContent = table?.title ?? (section === 'reviews' ? 'Reviews' : 'Hotels');
  addRecord.hidden = Boolean(table);
  addRecord.textContent = section === 'reviews' ? 'Add review' : 'Add hotel';
  element('adminNav').querySelectorAll('a').forEach(link => {
    const selected = link.hash === `#${section}`;
    if (selected) link.setAttribute('aria-current', 'page');
    else link.removeAttribute('aria-current');
  });
  if (moveFocus) heading.focus();
  if (table) {
    configureOperationTable(table);
    loadOperationRecords(section);
  }
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
  if (operationTables[section]) {
    await loadOperationRecords(section);
    if (credentials && section === currentSection()) refreshData.focus();
    return;
  }

  const updated = await perform(() => loadRecords(credentials), dashboardFeedback);
  if (updated) {
    render();
    showFeedback(dashboardFeedback, 'Lists refreshed.', 'success');
  }
  if (credentials) refreshData.focus();
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
  element('operationSearchLabel').textContent = `Search ${table.noun}s`;
  element('operationTableRegion').setAttribute('aria-label', `${table.title} table`);
  element('operationStatusField').hidden = !table.statusKey;
  const statusColumn = table.columns.find(([, key]) => key === table.statusKey);
  element('operationStatusLabel').textContent = statusColumn?.[0] ?? 'Status';
  element('operationColumns').replaceChildren(...table.columns.map(([label]) => {
    const header = document.createElement('th');
    header.scope = 'col';
    header.textContent = label;
    return header;
  }));
  operationSearch.value = '';
  operationStatus.replaceChildren(new Option('All statuses', ''));
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
  showEmpty(operationRows, table.columns.length, message);
  operationResults.textContent = '';
  element('operationPages').hidden = true;
  operationSearch.disabled = true;
  operationStatus.disabled = true;
}

async function loadOperationRecords(section) {
  const table = operationTables[section];
  const loadId = ++operationLoadId;
  const isCurrent = () => loadId === operationLoadId && section === currentSection() && Boolean(credentials);
  showOperationMessage(table, 'Loading records…');

  try {
    const records = await request(`/${section}`);
    if (!isCurrent()) return;
    const direction = table.ascending ? 1 : -1;
    operationRecords = records.sort((left, right) => direction * (left[table.sortKey] - right[table.sortKey]));
    updateOperationStatus(table);
    operationSearch.disabled = false;
    operationStatus.disabled = false;
    renderOperationRecords();
  } catch (error) {
    if (!isCurrent()) return;
    if (error.status === 401) clearSession('Sign in again to continue.');
    else showOperationMessage(table, error.message);
  }
}

function renderOperationRecords() {
  const table = operationTables[currentSection()];
  if (!table) return;
  const search = operationSearch.value.trim().toLowerCase();
  const status = operationStatus.value;
  const hotelsById = new Map(hotels.map(hotel => [hotel.hotelId, hotel.name]));
  const visible = operationRecords
    .filter(record => !status || String(record[table.statusKey]) === status)
    .map(record => table.columns.map(([, field, format = displayValue]) =>
      format(typeof field === 'function' ? field(record) : record[field], hotelsById)))
    .filter(values => values.some(value => value.toLowerCase().includes(search)));

  const pages = Math.max(1, Math.ceil(visible.length / operationPageSize));
  operationPage = Math.min(operationPage, pages);
  const start = (operationPage - 1) * operationPageSize;
  const end = Math.min(start + operationPageSize, visible.length);
  operationRows.replaceChildren();
  visible.slice(start, end).forEach(values => {
    const row = addRow(operationRows);
    values.forEach((value, index) => addCell(row, value, { label: table.columns[index][0] }));
  });

  if (!visible.length) {
    const message = operationRecords.length ? 'No records match your filters.' : `No ${table.noun}s yet.`;
    showEmpty(operationRows, table.columns.length, message);
  }
  if (pages > 1) {
    operationResults.textContent = `Showing ${start + 1}–${end} of ${visible.length} ${table.noun}s`;
  } else {
    updateResultCount('operationResults', visible.length, operationRecords.length, table.noun);
  }
  element('operationPages').hidden = pages < 2;
  element('pageNumber').textContent = `Page ${operationPage} of ${pages}`;
  previousPage.disabled = operationPage === 1;
  nextPage.disabled = operationPage === pages;
}

function applyOperationFilters() {
  operationPage = 1;
  renderOperationRecords();
}

operationSearch.addEventListener('input', applyOperationFilters);
operationStatus.addEventListener('change', applyOperationFilters);
previousPage.addEventListener('click', () => {
  operationPage--;
  renderOperationRecords();
  if (previousPage.disabled) nextPage.focus();
});
nextPage.addEventListener('click', () => {
  operationPage++;
  renderOperationRecords();
  if (nextPage.disabled) previousPage.focus();
});

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
  const published = reviews.filter(review => review.status === 'PUBLISHED');
  const average = published.length && published.reduce((sum, review) => sum + review.rating, 0) / published.length;
  element('hotelCount').textContent = hotels.length;
  element('pendingCount').textContent = pending;
  element('averageRating').textContent = published.length ? `${average.toFixed(1)} / 5` : '—';
  const navPending = element('navPending');
  navPending.textContent = pending;
  navPending.hidden = pending === 0;

  const hotelFilter = element('hotelFilter');
  setHotelOptions(hotelFilter, 'All hotels', hotelFilter.value);
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

function openDelete(type, record) {
  const isHotel = type === 'hotel';
  deletion = { type, id: isHotel ? record.hotelId : record.reviewId };
  element('deleteHeading').textContent = isHotel ? 'Delete hotel?' : 'Delete review?';
  element('deleteDescription').textContent = isHotel
    ? `“${record.name}” will be permanently deleted. Hotels with rooms or reviews cannot be deleted.`
    : `The review by ${record.authorName} will be permanently deleted.`;
  openDialog(deleteForm);
}

addRecord.addEventListener('click', () => {
  if (currentSection() === 'reviews') openReviewEditor();
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
      addRecord.focus();
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
  const { type, id } = deletion;
  await request(`/${type}s/${id}`, { method: 'DELETE' });
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
    await loadRecords(credentials);
    showSignedIn(true);
    showSection();
    render();
  } catch (error) {
    clearSession(error.message);
  }
}

restoreSession();
