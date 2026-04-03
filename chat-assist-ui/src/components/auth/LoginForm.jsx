import { MAX_USERNAME_LENGTH, MAX_PASSWORD_LENGTH } from '../../utils/constants';

/** Login form fields. All state and validation live in the parent (AuthPage). */
export default function LoginForm({ form, errors, onFieldChange, onSubmit }) {
  return (
    <form className="auth-form" onSubmit={onSubmit} noValidate>
      <div className="auth-form-field">
        <label htmlFor="login-username">Username</label>
        <input
          id="login-username"
          placeholder="e.g. username"
          value={form.username}
          onChange={(e) => onFieldChange('username', e.target.value)}
          autoComplete="username"
          maxLength={MAX_USERNAME_LENGTH}
          aria-invalid={Boolean(errors.username)}
          className={errors.username ? 'input-invalid' : ''}
        />
        {errors.username && <span className="field-error">{errors.username}</span>}
      </div>

      <div className="auth-form-field">
        <label htmlFor="login-password">Password</label>
        <input
          id="login-password"
          type="password"
          placeholder="••••••••"
          value={form.password}
          onChange={(e) => onFieldChange('password', e.target.value)}
          autoComplete="current-password"
          maxLength={MAX_PASSWORD_LENGTH}
          aria-invalid={Boolean(errors.password)}
          className={errors.password ? 'input-invalid' : ''}
        />
        {errors.password && <span className="field-error">{errors.password}</span>}
      </div>

      <button type="submit">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M15 3h6v18h-6M10 17l5-5-5-5M14 12H3" />
        </svg>
        Sign in
      </button>
    </form>
  );
}

