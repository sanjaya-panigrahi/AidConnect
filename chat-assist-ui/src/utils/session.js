import { SESSION_STORAGE_KEY, GUEST_USERNAME_PREFIX } from './constants';

export function loadInitialSession() {
  if (typeof window === 'undefined') return null;
  const stored = window.localStorage.getItem(SESSION_STORAGE_KEY);
  if (!stored) return null;
  try {
    return JSON.parse(stored);
  } catch {
    window.localStorage.removeItem(SESSION_STORAGE_KEY);
    return null;
  }
}

export function createGuestSession() {
  const timestamp = Date.now();
  return {
    userId: timestamp,
    username: `${GUEST_USERNAME_PREFIX}${timestamp.toString(36)}`,
    firstName: 'Guest',
    lastName: 'Assistant',
    email: 'guest@chatassist.local',
    message: 'Guest assistant access enabled.',
    isGuest: true
  };
}

