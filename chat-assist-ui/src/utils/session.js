import { GUEST_USERNAME_PREFIX } from './constants';

// ── LocalStorage key used to persist the JWT token across page reloads ────────
const JWT_TOKEN_KEY = 'aidconnect.jwt';

/**
 * Reads the persisted JWT token from localStorage.
 * Returns null when none is stored (unauthenticated / guest).
 */
export function getStoredToken() {
  try {
    return localStorage.getItem(JWT_TOKEN_KEY) || null;
  } catch {
    return null;
  }
}

/**
 * Stores the JWT token that was returned by the login API.
 * Pass null / undefined to clear.
 */
export function storeToken(token) {
  try {
    if (token) {
      localStorage.setItem(JWT_TOKEN_KEY, token);
    } else {
      localStorage.removeItem(JWT_TOKEN_KEY);
    }
  } catch {
    // Ignore (private browsing may block storage writes)
  }
}

/**
 * Removes the stored JWT token (called on logout).
 */
export function clearToken() {
  storeToken(null);
}

/**
 * Attempts to restore a previous authenticated session from localStorage.
 * Returns null when the user is not logged in.
 */
export function loadInitialSession() {
  return null; // Session is validated server-side via /api/users/session on startup
}

export function createGuestSession() {
  const timestamp = Date.now();
  return {
    userId: timestamp,
    username: `${GUEST_USERNAME_PREFIX}${timestamp.toString(36)}`,
    firstName: 'Guest',
    lastName: 'Assistant',
    email: 'guest@aidconnect.local',
    message: 'Guest assistant access enabled.',
    isGuest: true
  };
}
