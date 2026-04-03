/** A single human user row in the contact list. */
export default function ContactCard({
  user,
  isSelected,
  unreadCount,
  lastMessage,
  activity,
  showActivity,
  currentUsername,
  formatPresenceTime,
  formatMessageTime,
  onClick
}) {
  return (
    <button className={`contact-card ${isSelected ? 'active' : ''}`} onClick={onClick}>
      <div className="contact-avatar-wrap">
        <div className="contact-avatar">
          <img src="/default-user.svg" alt={`${user.firstName} ${user.lastName} profile`} />
        </div>
        {unreadCount > 0 && (
          <span className="contact-avatar-badge">{unreadCount > 99 ? '99+' : unreadCount}</span>
        )}
      </div>

      <div className="contact-meta">
        <div className="contact-title-row">
          <strong>{user.firstName} {user.lastName}</strong>
          {showActivity && (
            <span className="contact-activity-row">
              <span className="contact-activity-login" title="Logins today">L{activity.loginCount}</span>
              <span className="contact-activity-sep">·</span>
              <span className="contact-activity-chat-users" title="Users chatted today">U{activity.chatPeerCount}</span>
            </span>
          )}
        </div>
        {lastMessage ? (
          <span className={`contact-preview${unreadCount > 0 ? ' unread' : ''}`}>
            {lastMessage.senderUsername === currentUsername ? 'You: ' : ''}
            {lastMessage.content}
          </span>
        ) : (
          <span className="contact-preview muted">{user.online ? 'Online' : formatPresenceTime(user)}</span>
        )}
      </div>

      <div className="contact-right">
        <span
          className={`contact-time${unreadCount > 0 ? ' unread' : lastMessage ? '' : user.online ? '' : ' away'}`}
          title={user.lastActive ? `Last active: ${new Date(user.lastActive).toLocaleString()}` : ''}
        >
          {lastMessage ? formatMessageTime(lastMessage.sentAt) : user.online ? 'now' : ''}
        </span>
        <span className={`status-badge ${user.online ? 'online' : 'offline'}`}>
          <span className="status-dot" />
        </span>
      </div>
    </button>
  );
}

