import { getStoredToken } from '../utils/session';

/** Normalises the fieldErrors map returned by the API (private helper). */
function normalizeFieldErrors(fieldErrors) {
  if (!fieldErrors || typeof fieldErrors !== 'object' || Array.isArray(fieldErrors)) return {};
  return Object.entries(fieldErrors).reduce((acc, [field, msg]) => {
    if (typeof msg === 'string' && msg.trim()) acc[field] = msg;
    return acc;
  }, {});
}

async function readResponseBody(response) {
  if (response.status === 204) return null;
  const ct = response.headers.get('content-type') || '';
  if (ct.includes('application/json')) {
    try { return await response.json(); } catch { return null; }
  }
  const text = await response.text();
  if (!text) return null;
  try { return JSON.parse(text); } catch { return text; }
}

/**
 * Thin fetch wrapper that throws a typed Error on non-2xx responses.
 *
 * <p>Authentication strategy:
 * <ul>
 *   <li>If a JWT token is stored in localStorage it is sent as
 *       {@code Authorization: Bearer <token>} (primary).</li>
 *   <li>Browser session cookies ({@code credentials: 'include'}) are always
 *       included as a fallback for environments where the token is absent
 *       (e.g. server-rendered pages, deep links).</li>
 * </ul>
 *
 * The error carries {@code .status}, {@code .fieldErrors}, and {@code .payload}.
 */
export async function request(url, options = {}) {
  const incomingHeaders = options.headers || {};

  // Attach JWT Bearer token when available (preferred auth mechanism)
  const jwtToken = getStoredToken();
  const authHeader = jwtToken ? { Authorization: `Bearer ${jwtToken}` } : {};

  const headers = {
    'Content-Type': 'application/json',
    ...authHeader,
    ...incomingHeaders, // caller-supplied headers can override, but not replace auth
  };

  const response = await fetch(url, {
    credentials: 'include', // always include session cookies as fallback
    headers,
    ...options
  });
  const body = await readResponseBody(response);
  if (!response.ok) {
    if (response.status === 401 && typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('chatassist:unauthorized'));
    }
    const message = typeof body === 'string'
      ? body
      : body?.message || body?.error || response.statusText || 'Request failed';
    const err = new Error(message || 'Request failed');
    err.status = response.status;
    err.fieldErrors = normalizeFieldErrors(body?.fieldErrors);
    err.payload = body;
    throw err;
  }
  return body;
}
