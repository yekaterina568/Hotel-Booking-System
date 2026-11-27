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

// Профили операторов (имя и должность) — дизайн экрана входа от Аружан.
// Пароль здесь НЕ хранится: вход всегда проверяется бэкендом через HTTP Basic.
const operatorProfilesKey = 'pms_operator_profiles';

function getOperatorProfiles() {
  try {
    return JSON.parse(localStorage.getItem(operatorProfilesKey)) || {};
  } catch {
    return {};
  }
}

function saveOperatorProfile(profile) {
  const profiles = getOperatorProfiles();
  profiles[profile.username.toLowerCase()] = profile;
  try {
    localStorage.setItem(operatorProfilesKey, JSON.stringify(profiles));
  } catch {
    // localStorage может быть недоступен — профиль просто не запомнится
  }
}

const authScreen = document.getElementById('authScreen');
if (authScreen) {
  const appLayout = document.getElementById('appLayout');
  const authMsg = document.getElementById('authMsg');
  const loginForm = document.getElementById('operatorLoginForm');
  const registerForm = document.getElementById('operatorRegisterForm');
  const loginTab = document.getElementById('tabLoginBtn');
  const registerTab = document.getElementById('tabRegisterBtn');

  function setAuthMessage(text, kind = 'error') {
    authMsg.textContent = text;
    authMsg.className = text ? `msg ${kind}` : '';
  }

  function showOperator(username) {
    const profile = getOperatorProfiles()[username.toLowerCase()];
    document.getElementById('currentOperatorName').textContent = profile?.fullName || username;
    document.getElementById('currentOperatorRole').textContent = profile?.role || 'Front-Desk Operator';
  }

  function showFrontDeskLogin(message = '') {
    authScreen.hidden = false;
    appLayout.hidden = true;
    setAuthMessage(message);
  }

  function showFrontDeskApp() {
    authScreen.hidden = true;
    appLayout.hidden = false;
    const credentials = getCredentials();
    if (credentials) showOperator(credentials.username);
  }

  function showAuthTab(register) {
    loginTab.classList.toggle('active', !register);
    registerTab.classList.toggle('active', register);
    loginForm.style.display = register ? 'none' : 'flex';
    registerForm.style.display = register ? 'flex' : 'none';
    setAuthMessage('');
  }

  async function signIn(form, username, password, profile) {
    const submit = form.querySelector('button[type="submit"]');
    const label = submit.textContent;
    submit.disabled = true;
    submit.textContent = 'Signing in…';
    setAuthMessage('');

    try {
      const credentials = await authenticate(username, password);
      if (profile) saveOperatorProfile({ ...profile, username: credentials.username });
      form.reset();
      showFrontDeskApp();
      window.dispatchEvent(new Event('hotel-authenticated'));
    } catch (error) {
      setAuthMessage(error.message || 'Could not sign in.');
    } finally {
      submit.disabled = false;
      submit.textContent = label;
    }
  }

  loginTab.addEventListener('click', () => showAuthTab(false));
  registerTab.addEventListener('click', () => showAuthTab(true));

  loginForm.addEventListener('submit', event => {
    event.preventDefault();
    signIn(loginForm,
      document.getElementById('loginUsername').value,
      document.getElementById('loginPassword').value);
  });

  registerForm.addEventListener('submit', event => {
    event.preventDefault();
    const fullName = document.getElementById('regOpFullName').value.trim();
    if (!fullName) {
      setAuthMessage('Enter your full name.');
      return;
    }
    signIn(registerForm,
      document.getElementById('regOpUsername').value,
      document.getElementById('regOpPassword').value,
      { fullName, role: document.getElementById('regOpRole').value });
  });

  document.getElementById('logoutBtn').addEventListener('click', () => {
    clearCredentials();
    showFrontDeskLogin();
  });

  window.addEventListener('hotel-auth-required', () => showFrontDeskLogin('Sign in again to continue.'));

  if (getCredentials()) showFrontDeskApp();
  else showFrontDeskLogin();
}
