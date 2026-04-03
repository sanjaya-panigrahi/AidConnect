import ChatHeader from './ChatHeader';
import MessageList from './MessageList';
import MessageComposer from './MessageComposer';

/**
 * Main chat panel — composes header, message list, and composer.
 * All state and callbacks come from App (the orchestrator).
 */
export default function ChatPanel({
  selectedUser,
  session,
  isGuestSession,
  messages,
  loadingConversation,
  draft,
  composerError,
  error,
  showBotRoutingHint,
  showAidRoutingHint,
  scrollRef,
  onMobileMenuOpen,
  onDraftChange,
  onSend,
  onOptionSelect
}) {
  return (
    <main className="chat-panel">
      <ChatHeader
        selectedUser={selectedUser}
        isGuestSession={isGuestSession}
        onMobileMenuOpen={onMobileMenuOpen}
      />
      <MessageList
        messages={messages}
        session={session}
        selectedUser={selectedUser}
        loadingConversation={loadingConversation}
        scrollRef={scrollRef}
        onOptionSelect={onOptionSelect}
      />
      <MessageComposer
        draft={draft}
        onDraftChange={onDraftChange}
        onSend={onSend}
        selectedUser={selectedUser}
        composerError={composerError}
        error={error}
        showBotRoutingHint={showBotRoutingHint}
        showAidRoutingHint={showAidRoutingHint}
      />
    </main>
  );
}

