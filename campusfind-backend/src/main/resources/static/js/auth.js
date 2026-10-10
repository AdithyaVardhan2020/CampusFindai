/**
 * CampusFind AI - Authentication Service
 * Manages user credentials in sessionStorage for local development.
 * Note: Uses HTTP Basic authentication headers. For production, JWT or HTTP-only
 * secure cookies should be used instead of client-side credentials caching.
 */
const auth = {
  STORAGE_KEY: 'campusfind_auth_session',

  getUser() {
    try {
      const data = sessionStorage.getItem(this.STORAGE_KEY);
      return data ? JSON.parse(data) : null;
    } catch (e) {
      console.warn('Failed to parse auth session:', e);
      return null;
    }
  },

  setUser(user, basicToken) {
    const sessionData = {
      id: user.id,
      fullName: user.fullName,
      email: user.email,
      role: user.role,
      token: basicToken
    };
    sessionStorage.setItem(this.STORAGE_KEY, JSON.stringify(sessionData));
  },

  clearUser() {
    sessionStorage.removeItem(this.STORAGE_KEY);
  },

  isAuthenticated() {
    const user = this.getUser();
    return !!(user && user.token);
  },

  isAdmin() {
    const user = this.getUser();
    return !!(user && user.role === 'ROLE_ADMIN');
  },

  encodeBasicToken(str) {
    try {
      return btoa(unescape(encodeURIComponent(str)));
    } catch {
      return btoa(str);
    }
  },

  getBasicHeader() {
    const user = this.getUser();
    return user && user.token ? `Basic ${user.token}` : null;
  },

  async login(email, password) {
    if (!email || !password) {
      throw new Error('Please provide both campus email and password.');
    }
    const cleanEmail = email.trim();
    const token = this.encodeBasicToken(`${cleanEmail}:${password}`);
    const baseUrl = (window.CAMPUSFIND_CONFIG && window.CAMPUSFIND_CONFIG.apiBaseUrl) || '/api/v1';

    let response;
    try {
      response = await fetch(`${baseUrl}/auth/me`, {
        method: 'GET',
        headers: {
          'Authorization': `Basic ${token}`,
          'Accept': 'application/json',
          'X-Requested-With': 'XMLHttpRequest'
        }
      });
    } catch (networkErr) {
      throw new Error('Unable to connect to CampusFind server. Please verify the backend is running.');
    }

    if (response.status === 401) {
      throw new Error('Invalid campus email or password. Please verify your credentials.');
    }

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      throw new Error(errorData.message || `Login failed (HTTP ${response.status})`);
    }

    const userData = await response.json();
    this.setUser(userData, token);
    return userData;
  },

  async register(fullName, email, password) {
    if (!fullName || !email || !password) {
      throw new Error('Full name, campus email, and password are required.');
    }

    const cleanEmail = email.trim();
    const baseUrl = (window.CAMPUSFIND_CONFIG && window.CAMPUSFIND_CONFIG.apiBaseUrl) || '/api/v1';

    let response;
    try {
      response = await fetch(`${baseUrl}/auth/register`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json',
          'X-Requested-With': 'XMLHttpRequest'
        },
        body: JSON.stringify({
          fullName: fullName.trim(),
          email: cleanEmail,
          password: password
        })
      });
    } catch (networkErr) {
      throw new Error('Unable to connect to CampusFind server. Please verify the backend is running.');
    }

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      if (errorData.fieldErrors) {
        const details = Object.values(errorData.fieldErrors).join(', ');
        throw new Error(details || errorData.message || 'Validation error');
      }
      throw new Error(errorData.message || 'Registration failed. The email may already be registered.');
    }

    const registeredUser = await response.json();
    // Auto-login after successful registration
    const token = this.encodeBasicToken(`${cleanEmail}:${password}`);
    this.setUser(registeredUser, token);
    return registeredUser;
  },

  logout() {
    this.clearUser();
    window.location.href = 'index.html';
  }
};

window.auth = auth;
