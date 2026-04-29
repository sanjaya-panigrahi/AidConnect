/** Conversation header — shows selected user name, type badge, and assistant tips. */
export default function ChatHeader({ selectedUser, isGuestSession, onMobileMenuOpen }) {
  const assistantUsername = (selectedUser?.username || '').toLowerCase();
  const isAidAdminSupportContact = assistantUsername.startsWith('aid-admin-');

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
              : isAidAdminSupportContact
                ? 'Aid admin support contact (9 AM-5 PM). Booking requests must go through @aid.'
              : 'Active conversation'
            : isGuestSession
              ? 'Choose @bot or @aid to start chatting without signing in.'
              : 'Choose a user, assistant, or aid-admin support contact to start chatting.'}
        </p>
      </div>

      {(assistantUsername === 'aid' || isAidAdminSupportContact) && (
        <div className="doc-hint">
          Aid · Book doctor appointments using live doctor availability.
          Booking requests must go through <strong>@aid</strong>.
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

