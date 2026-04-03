import { MAX_USERNAME_LENGTH, MAX_NAME_LENGTH, MAX_EMAIL_LENGTH, MAX_PASSWORD_LENGTH } from '../../utils/constants';

/**
 * Registration form with password-strength meter and rules checklist.
 * All state and validation live in the parent (AuthPage).
 */
export default function RegisterForm({ form, errors, passwordChecks, passwordStrength, onFieldChange, onSubmit }) {
  return (
    <form className="auth-form" onSubmit={onSubmit} noValidate>
      <div className="auth-input-group">
        <div className="auth-form-field">
          <label htmlFor="reg-first">First name</label>
          <input
            id="reg-first" placeholder="first name"
            value={form.firstName}
            onChange={(e) => onFieldChange('firstName', e.target.value)}
            autoComplete="given-name" maxLength={MAX_NAME_LENGTH}
            aria-invalid={Boolean(errors.firstName)}
            className={errors.firstName ? 'input-invalid' : ''}
          />
          {errors.firstName && <span className="field-error">{errors.firstName}</span>}
        </div>
        <div className="auth-form-field">
          <label htmlFor="reg-last">Last name</label>
          <input
            id="reg-last" placeholder="last name"
            value={form.lastName}
            onChange={(e) => onFieldChange('lastName', e.target.value)}
            autoComplete="family-name" maxLength={MAX_NAME_LENGTH}
            aria-invalid={Boolean(errors.lastName)}
            className={errors.lastName ? 'input-invalid' : ''}
          />
          {errors.lastName && <span className="field-error">{errors.lastName}</span>}
        </div>
      </div>

      <div className="auth-form-field">
        <label htmlFor="reg-username">Username</label>
        <input
          id="reg-username" placeholder="username"
          value={form.username}
          onChange={(e) => onFieldChange('username', e.target.value)}
          autoComplete="username" maxLength={MAX_USERNAME_LENGTH}
          aria-invalid={Boolean(errors.username)}
          className={errors.username ? 'input-invalid' : ''}
        />
        {errors.username && <span className="field-error">{errors.username}</span>}
      </div>

      <div className="auth-form-field">
        <label htmlFor="reg-email">Email</label>
        <input
          id="reg-email" type="email" placeholder="email@chatassist.com"
          value={form.email}
          onChange={(e) => onFieldChange('email', e.target.value)}
          autoComplete="email" maxLength={MAX_EMAIL_LENGTH}
          aria-invalid={Boolean(errors.email)}
          className={errors.email ? 'input-invalid' : ''}
        />
        {errors.email && <span className="field-error">{errors.email}</span>}
      </div>

      <div className="auth-form-field">
        <label htmlFor="reg-password">Password</label>
        <input
          id="reg-password" type="password" placeholder="••••••••"
          value={form.password}
          onChange={(e) => onFieldChange('password', e.target.value)}
          autoComplete="new-password" maxLength={MAX_PASSWORD_LENGTH}
          aria-invalid={Boolean(errors.password)}
          className={errors.password ? 'input-invalid' : ''}
        />
        {errors.password && <span className="field-error">{errors.password}</span>}
        <div className={`password-strength ${passwordStrength.level}`}>{passwordStrength.label}</div>
        <div className="password-rules" aria-live="polite">
          <span className={passwordChecks.minLength ? 'met' : ''}>8+ chars</span>
          <span className={passwordChecks.upper     ? 'met' : ''}>uppercase</span>
          <span className={passwordChecks.lower     ? 'met' : ''}>lowercase</span>
          <span className={passwordChecks.number    ? 'met' : ''}>number</span>
          <span className={passwordChecks.special   ? 'met' : ''}>special</span>
          <span className={passwordChecks.noWhitespace ? 'met' : ''}>no spaces</span>
        </div>
      </div>

      <div className="auth-form-field">
        <label htmlFor="reg-confirm-password">Confirm password</label>
        <input
          id="reg-confirm-password" type="password" placeholder="••••••••"
          value={form.confirmPassword}
          onChange={(e) => onFieldChange('confirmPassword', e.target.value)}
          autoComplete="new-password" maxLength={MAX_PASSWORD_LENGTH}
          aria-invalid={Boolean(errors.confirmPassword)}
          className={errors.confirmPassword ? 'input-invalid' : ''}
        />
        {errors.confirmPassword && <span className="field-error">{errors.confirmPassword}</span>}
      </div>

      <button type="submit">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
          <circle cx="9" cy="7" r="4" />
          <line x1="19" y1="8" x2="19" y2="14" /><line x1="22" y1="11" x2="16" y2="11" />
        </svg>
        Create account
      </button>
    </form>
  );
}

