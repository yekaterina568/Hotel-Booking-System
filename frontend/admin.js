const ADMIN_API = 'http://localhost:8080/api/admin';
const reviewStatuses = { PENDING: 'Pending', PUBLISHED: 'Published', HIDDEN: 'Hidden' };
const dateFormat = new Intl.DateTimeFormat('en', { dateStyle: 'medium' });
const element = id => document.getElementById(id);

const signInForm = element('signInForm');
const signInFeedback = element('signInFeedback');
const dashboardFeedback = element('dashboardFeedback');
const hotelForm = element('hotelForm');
const reviewForm = element('reviewForm');
const deleteForm = element('deleteForm');
const hotelPanel = element('hotelPanel');
const reviewPanel = element('reviewPanel');
const addRecord = element('addRecord');
const refreshData = element('refreshData');
const signOut = element('signOut');

let token = '';
let hotels = [];
let reviews = [];
let busy = false;
let editingHotelId = null;
let deletion = null;

async function request(path, { method = 'GET', body } = {}, accessToken = token) {
  const headers = { Authorization: `Bearer ${accessToken}` };
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let response;
  try {
    response = await fetch(`${ADMIN_API}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body)
    });
  } catch {
    throw new Error('Could not reach the backend. Check that it is running and try again.');
  }

  if (response.ok) return response.status === 204 ? null : response.json();

  const problem = await response.json().catch(() => null);
  const message = {
    401: 'The admin access token is invalid.',
    503: 'Set HOTEL_ADMIN_TOKEN on the backend and restart it to enable administration.'
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
  token = '';
  hotels = [];
  reviews = [];
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
  element('adminToken').focus();
}

async function loadRecords(accessToken) {
  [hotels, reviews] = await Promise.all([
    request('/hotels', {}, accessToken),
    request('/reviews', {}, accessToken)
  ]);
}

signInForm.addEventListener('submit', async event => {
  event.preventDefault();
  const tokenInput = element('adminToken');
  const submit = signInForm.querySelector('button[type="submit"]');
  if (submit.disabled) return;

  submit.disabled = true;
  showFeedback(signInFeedback);
  try {
    const candidate = tokenInput.value.trim();
    await loadRecords(candidate);
    token = candidate;
    signInForm.reset();
    showSignedIn(true);
    showSection(true);
    render();
  } catch (error) {
    showFeedback(signInFeedback, error.message, 'error');
    tokenInput.focus();
  } finally {
    submit.disabled = false;
  }
});

signOut.addEventListener('click', () => clearSession());

function reviewsSelected() {
  return location.hash === '#reviews';
}

function showSection(moveFocus = false) {
  const showReviews = reviewsSelected();
  hotelPanel.hidden = showReviews;
  reviewPanel.hidden = !showReviews;
  const heading = element('dashboardHeading');
  heading.textContent = showReviews ? 'Reviews' : 'Hotels';
  addRecord.textContent = showReviews ? 'Add review' : 'Add hotel';
  element('adminNav').querySelectorAll('a').forEach(link => {
    const selected = link.hash === (showReviews ? '#reviews' : '#hotels');
    if (selected) link.setAttribute('aria-current', 'page');
    else link.removeAttribute('aria-current');
  });
  if (moveFocus) heading.focus();
}

window.addEventListener('hashchange', () => {
  if (token) {
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
  const updated = await perform(() => loadRecords(token), dashboardFeedback);
  if (updated) {
    render();
    showFeedback(dashboardFeedback, 'Lists refreshed.', 'success');
  }
  if (token) refreshData.focus();
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
    if (token) {
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
  if (reviewsSelected()) openReviewEditor();
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
