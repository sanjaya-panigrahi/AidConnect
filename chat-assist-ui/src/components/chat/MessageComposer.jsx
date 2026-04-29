import { MAX_MESSAGE_LENGTH } from '../../utils/constants';

/**
 * Message input form + routing hints + inline errors.
 * Cmd/Ctrl+Enter submits without requiring the send button.
 */
export default function MessageComposer({
  draft,
  onDraftChange,
  onSend,
  selectedUser,
  composerError,
  error,
  showBotRoutingHint,
  showAidRoutingHint
}) {
  const placeholder =
    selectedUser?.username === 'aid' ? 'Ask @aid to book an appointment, for example: Dr X tomorrow at 10 AM.' :
    selectedUser?.username === 'bot' ? 'Ask @bot anything…' :
    'Type a message — use @bot for AI help, @aid for booking, or aid-admin support (9 AM-5 PM).';

  function handleKeyDown(e) {
    if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') onSend(e);
  }

  return (
    <>
      <form className="composer" onSubmit={onSend}>
        <textarea
          rows="2"
          placeholder={placeholder}
          value={draft}
          onChange={(e) => onDraftChange(e.target.value)}
          onKeyDown={handleKeyDown}
          disabled={!selectedUser}
          maxLength={MAX_MESSAGE_LENGTH}
          aria-invalid={Boolean(composerError)}
          className={composerError ? 'input-invalid' : ''}
          autoCorrect="on" autoCapitalize="sentences" spellCheck
        />
        <button type="submit" disabled={!selectedUser || !draft.trim()}>
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <line x1="22" y1="2" x2="11" y2="13" />
            <polygon points="22 2 15 22 11 13 2 9 22 2" />
          </svg>
          Send
        </button>
      </form>

      {composerError && <div className="field-error composer-error">{composerError}</div>}
      {showBotRoutingHint && (
        <div className="composer-hint">
          This message will be routed to <strong>@bot</strong> only and will not be sent to the peer user.
        </div>
      )}
      {showAidRoutingHint && (
        <div className="composer-hint">
          This message will be routed to <strong>@aid</strong> only and will not be sent to the peer user.
        </div>
      )}
      {error && <div className="error-banner inline">{error}</div>}
    </>
  );
}

