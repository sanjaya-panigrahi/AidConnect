// ─── Service endpoint URLs (override via .env) ───────────────────────────────
export const userServiceUrl = import.meta.env.VITE_USER_SERVICE_URL || 'http://localhost:8081';
export const chatServiceUrl = import.meta.env.VITE_CHAT_SERVICE_URL || 'http://localhost:8082';
export const chatWsUrl = import.meta.env.VITE_CHAT_WS_URL || `${chatServiceUrl}/ws-chat`;

// ─── Assets ───────────────────────────────────────────────────────────────────
export const defaultUserAvatar = '/default-user.svg';

// ─── Session storage ──────────────────────────────────────────────────────────
export const SESSION_STORAGE_KEY = 'chat-session';
export const GUEST_USERNAME_PREFIX = 'guest-';

// ─── Display limits ───────────────────────────────────────────────────────────
export const MAX_RAIL_USERNAME_CHARS = 12;

// ─── Input validation limits ──────────────────────────────────────────────────
export const MAX_USERNAME_LENGTH = 50;
export const MAX_NAME_LENGTH = 50;
export const MAX_EMAIL_LENGTH = 255;
export const MIN_LOGIN_PASSWORD_LENGTH = 6;
export const MIN_REGISTER_PASSWORD_LENGTH = 8;
export const MAX_PASSWORD_LENGTH = 100;
export const MAX_MESSAGE_LENGTH = 4000;

// ─── Validation patterns ──────────────────────────────────────────────────────
export const USERNAME_PATTERN = /^[A-Za-z0-9._-]+$/;
export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
export const PASSWORD_NO_WHITESPACE_PATTERN = /^\S+$/;
export const PASSWORD_UPPERCASE_PATTERN = /[A-Z]/;
export const PASSWORD_LOWERCASE_PATTERN = /[a-z]/;
export const PASSWORD_NUMBER_PATTERN = /\d/;
export const PASSWORD_SPECIAL_PATTERN = /[^A-Za-z0-9]/;

// ─── Empty form defaults ──────────────────────────────────────────────────────
export const emptyRegister = {
  firstName: '',
  lastName: '',
  username: '',
  password: '',
  confirmPassword: '',
  email: ''
};

export const emptyLogin = {
  username: '',
  password: ''
};

