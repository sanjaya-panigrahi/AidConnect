import ContactCard from './ContactCard';
import BotCard from './BotCard';

/**
 * Left contacts sidebar.
 * Shows: guest notice OR search bar, human contact list, AI assistant section.
 */
export default function AppSidebar({
  mobileOpen,
  isGuestSession,
  searchQuery,
  onSearchChange,
  showActivity,
  activityToggleTitle,
  onToggleActivity,
  filteredHumanUsers,
  filteredBotUsers,
  selectedUser,
  unreadCounts,
  lastMessages,
  userActivityMap,
  botsExpanded,
  onToggleBots,
  onSelectUser,
  currentUsername,
  formatPresenceTime,
  formatMessageTime,
  getInitials
}) {
  return (
    <aside className={`sidebar${mobileOpen ? ' open' : ''}`}>

      {isGuestSession ? (
        <div className="guest-sidebar-note">
          <strong>Guest assistant mode</strong>
          <span>Chat only with <strong>@bot</strong> and <strong>@aid</strong>. Booking works via <strong>@aid</strong>. Sign in to reach other users and support admins.</span>
        </div>
      ) : (
        <div className="search-wrap">
          <input
            className="search-input" type="text" placeholder="Search users"
            value={searchQuery}
            onChange={(e) => onSearchChange(e.target.value)}
            autoCorrect="on" spellCheck
          />
        </div>
      )}

      {/* Activity toggle — visible only on mobile for non-guests */}
      {!isGuestSession && (
        <button
          className={`mobile-activity-toggle${showActivity ? ' active' : ''}`}
          type="button" title={activityToggleTitle} aria-label={activityToggleTitle}
          onClick={onToggleActivity}
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
          </svg>
          <span>Activity</span>
        </button>
      )}

      {/* Human contact list */}
      {!isGuestSession && (
        <div className="contact-list">
          {filteredHumanUsers.map((user) => (
            <ContactCard
              key={user.id}
              user={user}
              isSelected={selectedUser?.id === user.id}
              unreadCount={unreadCounts[user.username] || 0}
              lastMessage={lastMessages[user.username] || null}
              activity={userActivityMap[user.username] || { loginCount: 0, chatPeerCount: 0 }}
              showActivity={showActivity}
              currentUsername={currentUsername}
              formatPresenceTime={formatPresenceTime}
              formatMessageTime={formatMessageTime}
              onClick={() => onSelectUser(user)}
            />
          ))}
        </div>
      )}

      {/* AI Assistance section */}
      {filteredBotUsers.length > 0 && (
        <div className="ai-assist-section">
          <button className="ai-assist-header" onClick={onToggleBots} aria-expanded={botsExpanded}>
            <span className="section-label">AI Assistance</span>
            <svg className={`chevron${botsExpanded ? ' open' : ''}`} width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="2 5 7 10 12 5" />
            </svg>
          </button>
          {botsExpanded && (
            <div className="ai-assist-body">
              {filteredBotUsers.map((user) => (
                <BotCard
                  key={user.id}
                  user={user}
                  isSelected={selectedUser?.id === user.id}
                  unreadCount={unreadCounts[user.username] || 0}
                  lastMessage={lastMessages[user.username] || null}
                  currentUsername={currentUsername}
                  getInitials={getInitials}
                  onClick={() => onSelectUser(user)}
                />
              ))}
            </div>
          )}
        </div>
      )}
    </aside>
  );
}

