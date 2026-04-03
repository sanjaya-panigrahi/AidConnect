import { useState, useMemo } from 'react';
import AuthBrand from './AuthBrand';
import LoginForm from './LoginForm';
import RegisterForm from './RegisterForm';
import { emptyLogin, emptyRegister, userServiceUrl } from '../../utils/constants';
import { validateLoginValues, validateRegisterValues, getPasswordChecks, getPasswordStrength } from '../../utils/validation';
import { request } from '../../api/client';

/**
 * Complete auth screen (login + register).
 * Owns form state, field validation, and API calls.
 * Calls onLoginSuccess(session) on successful auth.
 * Calls onGuestAccess() when the guest button is clicked.
 */
export default function AuthPage({ onLoginSuccess, onGuestAccess }) {
  const [mode, setMode] = useState('login');
  const [loginForm, setLoginForm] = useState(emptyLogin);
  const [loginErrors, setLoginErrors] = useState({});
  const [registerForm, setRegisterForm] = useState(emptyRegister);
  const [registerErrors, setRegisterErrors] = useState({});
  const [error, setError] = useState('');

  const passwordChecks   = useMemo(() => getPasswordChecks(registerForm.password),   [registerForm.password]);
  const passwordStrength = useMemo(() => getPasswordStrength(registerForm.password), [registerForm.password]);

  function switchMode(next) {
    setMode(next);
    setError('');
    setLoginErrors({});
    setRegisterErrors({});
  }

  function updateLoginField(field, value) {
    setLoginForm((prev) => ({ ...prev, [field]: value }));
    setError('');
    setLoginErrors((prev) => { if (!prev[field]) return prev; const n = { ...prev }; delete n[field]; return n; });
  }

  function updateRegisterField(field, value) {
    setRegisterForm((prev) => ({ ...prev, [field]: value }));
    setError('');
    setRegisterErrors((prev) => { if (!prev[field]) return prev; const n = { ...prev }; delete n[field]; return n; });
  }

  async function handleLogin(event) {
    event.preventDefault();
    setError(''); setLoginErrors({});
    const { payload, errors } = validateLoginValues(loginForm);
    if (Object.keys(errors).length > 0) { setLoginErrors(errors); return; }
    try {
      const response = await request(`${userServiceUrl}/api/users/login`, { method: 'POST', body: JSON.stringify(payload) });
      setLoginForm(emptyLogin); setLoginErrors({});
      onLoginSuccess(response);
    } catch (err) {
      if (Object.keys(err.fieldErrors || {}).length > 0) {
        setLoginErrors(err.fieldErrors);
        setError(err.message === 'Validation failed.' ? 'Please correct the highlighted fields.' : err.message);
        return;
      }
      setError(err.message);
    }
  }

  async function handleRegister(event) {
    event.preventDefault();
    setError(''); setRegisterErrors({});
    const { payload, errors } = validateRegisterValues(registerForm);
    if (Object.keys(errors).length > 0) { setRegisterErrors(errors); return; }
    try {
      const response = await request(`${userServiceUrl}/api/users/register`, { method: 'POST', body: JSON.stringify(payload) });
      setRegisterForm(emptyRegister); setRegisterErrors({});
      onLoginSuccess(response);
    } catch (err) {
      if (Object.keys(err.fieldErrors || {}).length > 0) {
        setRegisterErrors(err.fieldErrors);
        setError(err.message === 'Validation failed.' ? 'Please correct the highlighted fields.' : err.message);
        return;
      }
      setError(err.message);
    }
  }

  return (
    <div className="auth-shell">
      <div className="auth-panel">
        <AuthBrand />

        <div className="auth-card">
          <div className="auth-card-header">
            <h2>{mode === 'login' ? 'Welcome back' : 'Create an account'}</h2>
            <p>{mode === 'login' ? 'Sign in to continue your conversations.' : 'Join ChatAssist and start connecting.'}</p>
          </div>

          <div className="auth-switcher">
            <button className={mode === 'login'    ? 'active' : ''} onClick={() => switchMode('login')}>Sign in</button>
            <button className={mode === 'register' ? 'active' : ''} onClick={() => switchMode('register')}>Register</button>
          </div>

          {mode === 'login' ? (
            <LoginForm form={loginForm} errors={loginErrors} onFieldChange={updateLoginField} onSubmit={handleLogin} />
          ) : (
            <RegisterForm
              form={registerForm} errors={registerErrors}
              passwordChecks={passwordChecks} passwordStrength={passwordStrength}
              onFieldChange={updateRegisterField} onSubmit={handleRegister}
            />
          )}

          {error && (
            <div className="error-banner">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" style={{ flexShrink: 0 }}>
                <circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="12" /><line x1="12" y1="16" x2="12.01" y2="16" />
              </svg>
              {error}
            </div>
          )}

          {mode === 'login' && (
            <div className="auth-guest-entry" aria-label="Assistant-only guest access">
              <button type="button" className="auth-secret-trigger" onClick={onGuestAccess} title="Open assistant-only guest chat">
                Try assistant-only mode
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

