import MessageBubble from '../MessageBubble';

/**
 * Scrollable message list with loading/empty states.
 * scrollRef is attached to a sentinel div so the parent can call scrollIntoView.
 */
export default function MessageList({ messages, session, selectedUser, loadingConversation, scrollRef, onOptionSelect }) {
  return (
    <section className="message-list">
      {loadingConversation && <div className="loading-state">Loading conversation...</div>}
      {!loadingConversation && messages.length === 0 && (
        <div className="empty-state">No messages yet. Start the conversation.</div>
      )}
      {messages.map((message) => (
        <MessageBubble
          key={message.id}
          message={message}
          mine={message.senderUsername === session.username}
          peerUser={selectedUser}
          currentUser={session}
          onOptionSelect={onOptionSelect}
        />
      ))}
      <div ref={scrollRef} />
    </section>
  );
}

