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
 * The error carries `.status`, `.fieldErrors`, and `.payload`.
 */
export async function request(url, options = {}) {
  const response = await fetch(url, {
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options
  });
  const body = await readResponseBody(response);
  if (!response.ok) {
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

