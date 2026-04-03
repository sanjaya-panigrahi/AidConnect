/** A single AI assistant row (@bot / @aid) in the sidebar. */
export default function BotCard({ user, isSelected, unreadCount, lastMessage, currentUsername, getInitials, onClick }) {
  return (
    <button className={`contact-card bot-card ${isSelected ? 'active' : ''}`} onClick={onClick}>
      <div className="contact-avatar-wrap">
        <div className="contact-avatar bot">{getInitials(user)}</div>
        {unreadCount > 0 && (
          <span className="contact-avatar-badge">{unreadCount > 99 ? '99+' : unreadCount}</span>
        )}
      </div>

      <div className="contact-meta">
        <strong>{`${user.firstName} ${user.lastName}`.trim()}</strong>
        {lastMessage && (
          <span className={`contact-preview${unreadCount > 0 ? ' unread' : ''}`}>
            {lastMessage.senderUsername === currentUsername ? 'You: ' : ''}
            {lastMessage.content}
          </span>
        )}
      </div>

      <div className="contact-right">
        <span className="contact-tag bot">{user.username === 'aid' ? '@aid' : '@bot'}</span>
        <span className={`status-badge ${user.online ? 'online' : 'offline'}`}>
          <span className="status-dot" />
        </span>
      </div>
    </button>
  );
}

