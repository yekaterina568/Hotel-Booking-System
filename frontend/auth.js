const API_BASE_URL = 'http://localhost:8080/api';
const credentialsKey = 'hotelApiCredentials';

function getCredentials() {
  try {
    const credentials = JSON.parse(sessionStorage.getItem(credentialsKey));
    return credentials?.username && credentials?.password ? credentials : null;
  } catch {
    return null;
  }
}

function setCredentials(credentials) {
  sessionStorage.setItem(credentialsKey, JSON.stringify(credentials));
}

function clearCredentials() {
  sessionStorage.removeItem(credentialsKey);
}

function authorizationHeader(credentials) {
  try {
    return `Basic ${btoa(`${credentials.username}:${credentials.password}`)}`;
  } catch {
    throw new Error('The username or password is incorrect.');
  }
}

async function authenticatedFetch(url, options = {}, credentials = getCredentials()) {
  if (!credentials) throw new Error('Sign in to continue.');

  const headers = new Headers(options.headers);
  headers.set('Authorization', authorizationHeader(credentials));
  const response = await fetch(url, { ...options, headers });
  if (response.status === 401) {
    clearCredentials();
    window.dispatchEvent(new Event('hotel-auth-required'));
  }
  return response;
}

async function authenticate(username, password) {
  const credentials = { username: username.trim(), password };
  const check = path => authenticatedFetch(`${API_BASE_URL}${path}`, {
    signal: AbortSignal.timeout(10000)
  }, credentials);
  let response = await check('/auth/check');
  if (response.status === 404) {
    // Older running backend builds may not expose the lightweight auth-check endpoint yet.
    response = await check('/rooms');
    if (response.status === 401) throw new Error('The username or password is incorrect.');
    if (response.status >= 500) {
      // The authenticated request reached the API, but room data may be unavailable.
      setCredentials(credentials);
      return credentials;
    }
  }
  if (response.status === 401) throw new Error('The username or password is incorrect.');
  if (!response.ok) throw new Error(`Could not verify credentials (HTTP ${response.status}).`);
  setCredentials(credentials);
  return credentials;
}

window.hotelAuth = {
  getCredentials,
  setCredentials,
  clearCredentials,
  fetch: authenticatedFetch,
  authenticate
};

const frontDeskSignInForm = document.getElementById('frontDeskSignInForm');
if (frontDeskSignInForm) {
  const loginPanel = document.getElementById('loginPanel');
  const frontDesk = document.querySelector('.app-layout');
  const feedback = document.getElementById('frontDeskSignInFeedback');

  function showFrontDeskLogin(message = '') {
    loginPanel.hidden = false;
    frontDesk.hidden = true;
    feedback.textContent = message;
    feedback.className = message ? 'feedback-banner error' : 'feedback-banner';
  }

  function showFrontDeskApp() {
    loginPanel.hidden = true;
    frontDesk.hidden = false;
  }

  frontDeskSignInForm.addEventListener('submit', async event => {
    event.preventDefault();
    const username = document.getElementById('frontDeskUsername').value;
    const password = document.getElementById('frontDeskPassword').value;
    const submit = frontDeskSignInForm.querySelector('button[type="submit"]');
    submit.disabled = true;
    submit.textContent = 'Signing in…';
    feedback.textContent = '';
    feedback.className = 'feedback-banner';

    try {
      await authenticate(username, password);
      frontDeskSignInForm.reset();
      showFrontDeskApp();
      window.dispatchEvent(new Event('hotel-authenticated'));
    } catch (error) {
      showFrontDeskLogin(error.message || 'Could not sign in.');
    } finally {
      submit.disabled = false;
      submit.textContent = 'Sign in';
    }
  });

  document.getElementById('frontDeskSignOut').addEventListener('click', () => {
    clearCredentials();
    window.dispatchEvent(new Event('hotel-auth-required'));
  });

  window.addEventListener('hotel-auth-required', () => showFrontDeskLogin('Sign in again to continue.'));

  if (getCredentials()) showFrontDeskApp();
  else showFrontDeskLogin();
}
