function statusTicks(status) {
  if (status === 'SEEN') return '✓✓';
  if (status === 'DELIVERED') return '✓✓';
  return '✓';
}

/**
 * Split content into plain text lines and numbered-option lines.
 * Option lines match the pattern:  [N]  some text
 */
function parseContent(content) {
  const lines = (content || '').split('\n');
  const textLines = [];
  const options = [];

  for (const line of lines) {
    const match = line.match(/^\s*\[(\d+)]\s{1,4}(.+)$/);
    if (match) {
      options.push({ number: parseInt(match[1], 10), label: match[2].trim() });
    } else {
      textLines.push(line);
    }
  }

  // Remove trailing blank lines from text section
  while (textLines.length > 0 && textLines[textLines.length - 1].trim() === '') {
    textLines.pop();
  }

  return { textLines, options };
}

/**
 * Render **bold** markdown inside a string as <strong> spans.
 */
function renderBold(text) {
  const parts = text.split(/\*\*(.+?)\*\*/g);
  return parts.map((part, i) =>
    i % 2 === 1 ? <strong key={i}>{part}</strong> : part
  );
}

export default function MessageBubble({ message, mine, onOptionSelect }) {
  const defaultUserAvatar = '/default-user.svg';
  const sender = (message.senderUsername || '').toLowerCase();
  const isAid = sender === 'aid';
  const senderIsAssistant = sender === 'aid' || sender === 'bot';
  const isDocAssistantMessage = message.senderUsername === 'aid' || message.receiverUsername === 'aid';
  
  // Special case for bot/aid: show full "BOT"/"AID", otherwise use first 2 chars
  let senderLabel;
  if (sender === 'bot') {
    senderLabel = 'BOT';
  } else if (sender === 'aid') {
    senderLabel = 'AID';
  } else {
    senderLabel = (message.senderUsername || 'U').slice(0, 2).toUpperCase();
  }

  const { textLines, options } = isAid ? parseContent(message.content) : { textLines: [message.content], options: [] };
  const hasOptions = options.length > 0;

  return (
    <div className={`message-row ${mine ? 'mine' : 'theirs'}`}>
      {!mine && (
        <div className="message-avatar">
          {senderIsAssistant
            ? senderLabel
            : <img src={defaultUserAvatar} alt={`${message.senderUsername} profile`} />}
        </div>
      )}

      <div className={`message-bubble ${mine ? 'mine' : 'theirs'} ${isDocAssistantMessage ? 'doc' : ''}`}>
        {!mine && (
          <div className="message-author">{message.senderUsername}</div>
        )}

        <div className="message-content">
          {/* Plain text section */}
          {isAid ? (
            <div className="aid-message-text">
              {textLines.map((line, i) => (
                <span key={i}>
                  {renderBold(line)}
                  {i < textLines.length - 1 && <br />}
                </span>
              ))}
            </div>
          ) : (
            message.content
          )}

          {/* Clickable option buttons */}
          {hasOptions && !mine && (
            <div className="aid-options">
              {options.map((opt) => (
                <button
                  key={opt.number}
                  className="aid-option-btn"
                  type="button"
                  onClick={() => onOptionSelect && onOptionSelect(String(opt.number))}
                >
                  <span className="aid-option-num">{opt.number}</span>
                  <span className="aid-option-label">{renderBold(opt.label)}</span>
                </button>
              ))}
            </div>
          )}
        </div>

        <div className="message-meta">
          <span>{new Date(message.sentAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
          {mine && (
            <span className={`message-status ${message.status === 'SEEN' ? 'seen' : ''}`}>
              {statusTicks(message.status)}
            </span>
          )}
        </div>
      </div>

      {mine && (
        <div className="message-avatar mine">
          <img src={defaultUserAvatar} alt="Your profile" />
        </div>
      )}
    </div>
  );
}
