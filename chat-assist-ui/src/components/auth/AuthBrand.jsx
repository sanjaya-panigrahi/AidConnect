/** Left brand / marketing panel shown on the auth screen. */
export default function AuthBrand() {
  return (
    <div className="auth-brand">
      <div className="auth-brand-mark">AC</div>
      <h1>AidConnect</h1>
      <p className="auth-brand-tagline">
        A connected clinical chat platform for patients, doctors, and AI assistants in one flow.
      </p>
      <div className="auth-brand-features">
        <div className="auth-feature">
          <div className="auth-feature-icon">
            <svg viewBox="0 0 24 24">
              <path d="M4 5h16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H9l-5 4v-4H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z" />
            </svg>
          </div>
          <span>Real-time messaging with other users</span>
        </div>
        <div className="auth-feature">
          <div className="auth-feature-icon">
            <svg viewBox="0 0 24 24">
              <circle cx="12" cy="12" r="3" />
              <path d="M19.1 4.9a9 9 0 0 1 0 14.2M4.9 4.9a9 9 0 0 0 0 14.2" />
            </svg>
          </div>
          <span>General AI help with <strong style={{ color: '#fff' }}>@bot</strong></span>
        </div>
        <div className="auth-feature">
          <div className="auth-feature-icon">
            <svg viewBox="0 0 24 24">
              <path d="M4 5h7l2 2h7a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z" />
            </svg>
          </div>
          <span>Appointment booking with <strong style={{ color: '#fff' }}>@aid</strong> only</span>
        </div>
      </div>
      <div className="auth-brand-divider" />
    </div>
  );
}

