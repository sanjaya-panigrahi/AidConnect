import RailIcon from '../icons/RailIcon';

/**
 * Vertical left navigation rail.
 * Contains: user avatar pill, today's activity stats (optional),
 * Chat / Activity / Logout nav items.
 */
export default function AppRail({
  loggedInUsername,
  railUsernameDisplay,
  isGuestSession,
  showActivity,
  activitySummary,
  activityToggleTitle,
  onToggleActivity,
  onLogout
}) {
  return (
    <aside className="app-rail">
      <div className="rail-avatar">
        <img src="/default-user.svg" alt="Your profile" />
      </div>
      <div className="rail-user-pill" title={loggedInUsername}>
        <strong>{railUsernameDisplay}</strong>
      </div>

      {/* Today's login / chat-peer counts — hidden for guests */}
      {!isGuestSession && showActivity && activitySummary && (
        <div
          className="rail-activity"
          title={`Logins today: ${activitySummary.loginCount}  ·  Users chatted today: ${activitySummary.chatPeerCount ?? 0}`}
        >
          <span className="rail-activity-label">Today</span>
          <div className="rail-activity-row">
            <span className="rail-activity-login">L{activitySummary.loginCount}</span>
            <span className="rail-activity-sep">·</span>
            <span className="rail-activity-chat-users">U{activitySummary.chatPeerCount ?? 0}</span>
          </div>
        </div>
      )}

      <button className="rail-item active" type="button">
        <span className="rail-icon"><RailIcon type="chat" /></span>
        <span>Chat</span>
      </button>

      <button
        className={`rail-item rail-activity-toggle${showActivity ? ' active' : ''}`}
        type="button"
        title={activityToggleTitle}
        aria-label={activityToggleTitle}
        onClick={onToggleActivity}
        disabled={isGuestSession}
      >
        <span className="rail-icon">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
          </svg>
        </span>
        <span>{isGuestSession ? 'Guest' : 'Activity'}</span>
      </button>

      <button className="rail-item rail-logout" type="button" onClick={onLogout}>
        <span className="rail-icon"><RailIcon type="settings" /></span>
        <span>{isGuestSession ? 'Exit' : 'Logout'}</span>
      </button>
    </aside>
  );
}

