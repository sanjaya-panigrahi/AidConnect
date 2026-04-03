/** Conversation header — shows selected user name, type badge, and assistant tips. */
export default function ChatHeader({ selectedUser, isGuestSession, onMobileMenuOpen }) {
  const assistantUsername = (selectedUser?.username || '').toLowerCase();

  return (
    <header className="chat-header">
      <button className="mobile-menu" type="button" onClick={onMobileMenuOpen}>
        {isGuestSession ? 'Assistants' : 'Contacts'}
      </button>

      <div>
        <div className="eyebrow">{isGuestSession ? 'Guest chat' : 'Conversation'}</div>
        <h3>
          {selectedUser
            ? `${selectedUser.firstName} ${selectedUser.lastName}`
            : isGuestSession ? 'Select an assistant' : 'Select a contact'}
        </h3>
        <p>
          {selectedUser
            ? ['aid', 'bot'].includes(assistantUsername)
              ? `@${selectedUser.username}`
              : 'Active conversation'
            : isGuestSession
              ? 'Choose @bot or @aid to start chatting without signing in.'
              : 'Choose a user or bot to start chatting.'}
        </p>
      </div>

      {assistantUsername === 'aid' && (
        <div className="doc-hint">
          Aid · Book doctor appointments using live doctor availability.
          Try: &quot;Appointment with Dr X tomorrow at 10 AM&quot;.
          You can also type <strong>@aid</strong> in any chat to route privately here.
        </div>
      )}
      {assistantUsername === 'bot' && (
        <div className="doc-hint">
          Bot · Ask AI questions and get instant help.
          Try: &quot;@bot explain this error in simple terms&quot;.
          You can also type <strong>@bot</strong> in any chat to route privately here.
        </div>
      )}
    </header>
  );
}

