import { useEffect, useMemo, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

// ── Utilities ─────────────────────────────────────────────────────────────────
import {
  userServiceUrl, chatServiceUrl, chatWsUrl,
  SESSION_STORAGE_KEY, MAX_RAIL_USERNAME_CHARS
} from './utils/constants';
import { sortByTime, mergeMessages, getInitials, formatPresenceTime, formatMessageTime } from './utils/formatting';
import { loadInitialSession, createGuestSession } from './utils/session';
import { validateMessageContent } from './utils/validation';
import { request } from './api/client';

// ── Components ────────────────────────────────────────────────────────────────
import AuthPage   from './components/auth/AuthPage';
import AppRail    from './components/chat/AppRail';
import AppSidebar from './components/chat/AppSidebar';
import ChatPanel  from './components/chat/ChatPanel';

// ─────────────────────────────────────────────────────────────────────────────

export default function App() {
  // ── Session & layout state ─────────────────────────────────────────────────
  const [session, setSession]               = useState(loadInitialSession);
  const [users, setUsers]                   = useState([]);
  const [assistants, setAssistants]         = useState([]);
  const [selectedUser, setSelectedUser]     = useState(null);
  const [messages, setMessages]             = useState([]);
  const [draft, setDraft]                   = useState('');
  const [error, setError]                   = useState('');
  const [composerError, setComposerError]   = useState('');
  const [loadingConversation, setLoadingConversation] = useState(false);
  const [botsExpanded, setBotsExpanded]     = useState(true);
  const [searchQuery, setSearchQuery]       = useState('');
  const [mobileContactsOpen, setMobileContactsOpen] = useState(false);
  const [unreadCounts, setUnreadCounts]     = useState({});
  const [lastMessages, setLastMessages]     = useState({});
  const [activitySummary, setActivitySummary] = useState(null);
  const [userActivityMap, setUserActivityMap] = useState({});
  const [showActivity, setShowActivity]     = useState(false);

  // ── Refs (stable across renders) ──────────────────────────────────────────
  const clientRef             = useRef(null);
  const sessionRef            = useRef(null);
  const selectedUserRef       = useRef(null);
  const lastDirectoryRefreshRef = useRef(0);
  const processedMessageIdsRef  = useRef(new Set());
  const scrollRef             = useRef(null);
  // Track which conversation is currently loaded to avoid full-replace on refresh
  const loadedConvUsernameRef = useRef(null);

  // ── Derived / memoised values ─────────────────────────────────────────────
  const isGuestSession = Boolean(session?.isGuest);

  const humanUsers = useMemo(() => {
    return [...users].sort((a, b) => {
      const ra = a.online ? 0 : a.lastActive ? 1 : 2;
      const rb = b.online ? 0 : b.lastActive ? 1 : 2;
      if (ra !== rb) return ra - rb;
      if (ra === 1) return new Date(b.lastActive).getTime() - new Date(a.lastActive).getTime();
      return `${a.firstName} ${a.lastName}`.localeCompare(`${b.firstName} ${b.lastName}`);
    });
  }, [users]);

  const botUsers = useMemo(() => assistants, [assistants]);

  const botAssistant = useMemo(() => botUsers.find((u) => (u.username || '').toLowerCase() === 'bot') || null, [botUsers]);
  const aidAssistant = useMemo(() => botUsers.find((u) => (u.username || '').toLowerCase() === 'aid') || null, [botUsers]);

  const filteredHumanUsers = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return humanUsers;
    return humanUsers.filter((u) =>
      `${u.firstName} ${u.lastName}`.toLowerCase().includes(q) || (u.username || '').toLowerCase().includes(q)
    );
  }, [humanUsers, searchQuery]);

  const showBotRoutingHint = useMemo(() => {
    if (!selectedUser) return false;
    const t = (selectedUser.username || '').toLowerCase();
    return t !== 'bot' && t !== 'aid' && /@bot/i.test(draft);
  }, [selectedUser, draft]);

  const showAidRoutingHint = useMemo(() => {
    if (!selectedUser) return false;
    const t = (selectedUser.username || '').toLowerCase();
    return t !== 'bot' && t !== 'aid' && /@aid/i.test(draft);
  }, [selectedUser, draft]);

  const loggedInUsername = useMemo(
    () => session?.isGuest ? 'guest mode' : (session?.username || 'user'),
    [session]
  );
  const railUsernameDisplay = useMemo(() => {
    if (!loggedInUsername) return 'user';
    return loggedInUsername.length <= MAX_RAIL_USERNAME_CHARS
      ? loggedInUsername
      : `${loggedInUsername.slice(0, MAX_RAIL_USERNAME_CHARS)}...`;
  }, [loggedInUsername]);

  const activityToggleTitle = isGuestSession
    ? 'Guest mode only includes assistant chat and does not show user activity.'
    : showActivity
      ? 'Hide activity details: your logins today and users chatted today, plus each user\'s activity stats.'
      : 'Show activity details: your logins today and users chatted today, plus each user\'s activity stats.';

  // ── Keep refs in sync ──────────────────────────────────────────────────────
  useEffect(() => { sessionRef.current     = session;     }, [session]);
  useEffect(() => { selectedUserRef.current = selectedUser; }, [selectedUser]);

  // ── Clear unread count when selecting a user ───────────────────────────────
  useEffect(() => {
    if (selectedUser) {
      setUnreadCounts((prev) => {
        const next = { ...prev };
        delete next[selectedUser.username];
        return next;
      });
    }
  }, [selectedUser?.username]);

  // ── Load conversation when selected user changes ───────────────────────────
  useEffect(() => {
    if (!selectedUser || !session) return;
    loadConversation(selectedUser.username);
  }, [selectedUser?.username, session?.username]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Mobile panel: open contacts when no user selected ─────────────────────
  useEffect(() => {
    if (!selectedUser) setMobileContactsOpen(true);
    setComposerError('');
  }, [selectedUser?.username]);

  // ── Auto-scroll to bottom on new messages ─────────────────────────────────
  useEffect(() => {
    scrollRef.current?.scrollIntoView({ behavior: 'auto' });
  }, [messages]);

  // ── Session lifecycle: directory, presence, WebSocket, polling ────────────
  useEffect(() => {
    if (!session) return;

    window.localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
    refreshDirectory(true);

    if (!session.isGuest) {
      updatePresence(session.username, true);
      loadActivitySummary(session.username);
      loadAllUsersActivity();
    } else {
      setActivitySummary(null);
      setUserActivityMap({});
      setShowActivity(false);
    }

    // Refresh presence + directory every 30 s (increased from 10 s to reduce flicker)
    const dirInterval = setInterval(() => refreshDirectory(true), 30000);

    // Refresh activity counters every 60 s
    const actInterval = session.isGuest ? null : setInterval(() => {
      const active = sessionRef.current;
      if (active?.username && !active.isGuest) {
        loadActivitySummary(active.username);
        loadAllUsersActivity();
      }
    }, 60000);

    const handleFocus      = () => refreshDirectory(true);
    const handleVisibility = () => { if (!document.hidden) refreshDirectory(true); };
    window.addEventListener('focus', handleFocus);
    document.addEventListener('visibilitychange', handleVisibility);

    // WebSocket / STOMP
    const client = new Client({
      webSocketFactory: () => new SockJS(chatWsUrl),
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe(`/topic/messages/${session.username}`, (frame) => handleIncomingMessage(JSON.parse(frame.body)));
        client.subscribe(`/topic/status/${session.username}`,  (frame) => handleIncomingMessage(JSON.parse(frame.body)));
      }
    });
    client.activate();
    clientRef.current = client;

    return () => {
      clearInterval(dirInterval);
      if (actInterval) clearInterval(actInterval);
      window.removeEventListener('focus', handleFocus);
      document.removeEventListener('visibilitychange', handleVisibility);
      client.deactivate();
    };
  }, [session]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Mark offline on page unload ───────────────────────────────────────────
  useEffect(() => {
    if (!session?.username || session.isGuest) return;
    const handleExit = () => {
      const u = sessionRef.current?.username;
      if (!u) return;
      fetch(`${userServiceUrl}/api/users/${encodeURIComponent(u)}/offline`, {
        method: 'PUT', keepalive: true, headers: { 'Content-Type': 'application/json' }
      }).catch(() => undefined);
    };
    window.addEventListener('beforeunload', handleExit);
    window.addEventListener('pagehide', handleExit);
    return () => {
      window.removeEventListener('beforeunload', handleExit);
      window.removeEventListener('pagehide', handleExit);
    };
  }, [session?.username]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Presence helpers ──────────────────────────────────────────────────────
  async function updatePresence(username, isOnline) {
    if (!username) return;
    try {
      await request(`${userServiceUrl}/api/users/${encodeURIComponent(username)}/${isOnline ? 'online' : 'offline'}`, { method: 'PUT' });
    } catch { /* best-effort */ }
  }

  // ── Activity helpers ──────────────────────────────────────────────────────
  async function loadActivitySummary(username) {
    if (!username) return;
    try {
      const [summary, chatSummary] = await Promise.all([
        request(`${userServiceUrl}/api/users/${encodeURIComponent(username)}/activity/today`),
        request(`${chatServiceUrl}/api/chats/${encodeURIComponent(username)}/activity/today`)
      ]);
      setActivitySummary({ ...summary, chatPeerCount: chatSummary?.chatPeerCount ?? 0 });
    } catch { /* best-effort */ }
  }

  async function loadAllUsersActivity() {
    try {
      const [summaries, chatSummaries] = await Promise.all([
        request(`${userServiceUrl}/api/users/activity/today`),
        request(`${chatServiceUrl}/api/chats/activity/today`)
      ]);
      const chatMap = {};
      chatSummaries.forEach((s) => { chatMap[s.username] = s.chatPeerCount; });
      const map = {};
      summaries.forEach((s) => { map[s.username] = { loginCount: s.loginCount, chatPeerCount: chatMap[s.username] ?? 0 }; });
      chatSummaries.forEach((s) => { if (!map[s.username]) map[s.username] = { loginCount: 0, chatPeerCount: s.chatPeerCount }; });
      setUserActivityMap(map);
    } catch { /* best-effort */ }
  }

  // ── Directory ─────────────────────────────────────────────────────────────
  function refreshDirectory(force = false) {
    const active = sessionRef.current;
    if (!active?.username) return;
    const now = Date.now();
    if (!force && now - lastDirectoryRefreshRef.current < 4000) return;
    lastDirectoryRefreshRef.current = now;
    loadDirectory(active.username);
  }

  async function loadDirectory(excludeUsername) {
    try {
      const guestMode = Boolean(sessionRef.current?.isGuest);
      const assistantUsers = await request(`${userServiceUrl}/api/users/assistants`);
      const dbUsers = guestMode ? [] : await request(`${userServiceUrl}/api/users?excludeUsername=${excludeUsername}`);

      setUsers(dbUsers || []);
      setAssistants(assistantUsers);
      setSelectedUser((current) => {
        const all = [...(dbUsers || []), ...assistantUsers];
        const fallback = guestMode ? assistantUsers[0] || null : dbUsers[0] || assistantUsers[0] || null;
        if (!current) return fallback;
        const found = all.find((u) => u.username === current.username);
        if (!found) return fallback;
        // Only swap reference when presence actually changed (prevents triggering loadConversation)
        if (found.online === current.online && found.lastActive === current.lastActive) return current;
        return { ...current, ...found };
      });
    } catch (err) {
      setError(err.message);
    }
  }

  // ── Conversation ──────────────────────────────────────────────────────────
  async function loadConversation(otherUsername) {
    if (!session) return;

    const isSameConv = loadedConvUsernameRef.current === otherUsername;
    // For a new conversation, clear immediately to avoid stale messages flashing
    if (!isSameConv) {
      setMessages([]);
      loadedConvUsernameRef.current = otherUsername;
    }

    setLoadingConversation(true);
    try {
      const data = await request(
        `${chatServiceUrl}/api/chats/conversation?userA=${session.username}&userB=${otherUsername}`
      );
      const sorted = sortByTime(data);
      // Anti-flicker: merge (not replace) when refreshing the same conversation
      if (isSameConv) {
        setMessages((prev) => mergeMessages(prev, sorted));
      } else {
        setMessages(sorted);
      }
      if (sorted.length > 0) {
        const last = sorted[sorted.length - 1];
        setLastMessages((prev) => {
          const existing = prev[otherUsername];
          if (!existing || new Date(last.sentAt) >= new Date(existing.sentAt)) return { ...prev, [otherUsername]: last };
          return prev;
        });
      }
      await markSeenMessages(data, otherUsername);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoadingConversation(false);
    }
  }

  async function markSeenMessages(conversation, otherUsername) {
    if (!session || !selectedUserRef.current || selectedUserRef.current.username !== otherUsername) return;
    const unread = conversation.filter((m) => m.senderUsername === otherUsername && m.status !== 'SEEN');
    await Promise.all(unread.map((m) =>
      request(`${chatServiceUrl}/api/chats/messages/status`, {
        method: 'PATCH', body: JSON.stringify({ messageId: m.id, status: 'SEEN' })
      })
    ));
  }

  // ── Incoming WebSocket message handler ───────────────────────────────────
  function handleIncomingMessage(message) {
    // Deduplicate (backend broadcasts to both /topic/messages and /topic/status)
    if (message.id) {
      if (processedMessageIdsRef.current.has(message.id)) return;
      processedMessageIdsRef.current.add(message.id);
      if (processedMessageIdsRef.current.size > 500) {
        processedMessageIdsRef.current.delete(processedMessageIdsRef.current.values().next().value);
      }
    }

    const active   = sessionRef.current;
    const peer     = selectedUserRef.current;

    const inActive = peer && active && (
      (message.senderUsername === peer.username && message.receiverUsername === active.username) ||
      (message.senderUsername === active.username && message.receiverUsername === peer.username) ||
      (message.contextUsername != null && (
        (message.receiverUsername === active.username && message.contextUsername === peer.username) ||
        (message.receiverUsername === peer.username   && message.contextUsername === active.username)
      ))
    );

    if (inActive) {
      setMessages((prev) => mergeMessages(prev, [message]));
    } else if (active && !inActive) {
      let senderForUnread = null;
      if (message.senderUsername !== active.username && message.contextUsername === null) {
        senderForUnread = message.senderUsername;
      } else if (message.contextUsername && message.receiverUsername === active.username) {
        senderForUnread = message.contextUsername;
      }
      if (senderForUnread) {
        setUnreadCounts((prev) => ({ ...prev, [senderForUnread]: (prev[senderForUnread] || 0) + 1 }));
      }
    }

    // Update last-message preview for the peer
    const peerUsername = active && (
      message.senderUsername === active.username ? message.receiverUsername : message.senderUsername
    );
    if (peerUsername && peerUsername !== active?.username) {
      setLastMessages((prev) => {
        const existing = prev[peerUsername];
        if (!existing || new Date(message.sentAt) >= new Date(existing.sentAt)) return { ...prev, [peerUsername]: message };
        return prev;
      });
    }

    // Mark seen for incoming messages in the active thread
    if (peer && active && message.senderUsername === peer.username && message.receiverUsername === active.username && message.status !== 'SEEN') {
      request(`${chatServiceUrl}/api/chats/messages/status`, {
        method: 'PATCH', body: JSON.stringify({ messageId: message.id, status: 'SEEN' })
      }).catch(() => undefined);
    }
  }

  // ── Send message ──────────────────────────────────────────────────────────
  async function sendMessageText(text) {
    if (!selectedUser || !session) return;
    setError(''); setComposerError('');

    const validationError = validateMessageContent(text);
    if (validationError) { setComposerError(validationError); return; }

    const targetUsername = (selectedUser.username || '').toLowerCase();
    const inAssistant = targetUsername === 'bot' || targetUsername === 'aid';
    const routeBot = !inAssistant && /@bot/i.test(text);
    const routeAid = !inAssistant && /@aid/i.test(text);
    const target = routeBot ? botAssistant : routeAid ? aidAssistant : selectedUser;

    if (!target) {
      setError(`${routeAid ? '@aid' : '@bot'} assistant is not available right now.`);
      return;
    }

    const payload = {
      senderId: session.userId,
      senderUsername: session.username,
      receiverId: target.id,
      receiverUsername: target.username,
      content: text,
      messageType: ['bot', 'aid'].includes((target.username || '').toLowerCase()) ? 'BOT' : 'USER',
      contextUsername: null
    };

    try {
      const response = await request(`${chatServiceUrl}/api/chats/messages`, { method: 'POST', body: JSON.stringify(payload) });
      if (routeBot || routeAid) {
        setSelectedUser(target);
        setMessages([response]);
        loadedConvUsernameRef.current = target.username;
      } else {
        setMessages((prev) => mergeMessages(prev, [response]));
      }
      setDraft(''); setComposerError('');
    } catch (err) {
      if (err.fieldErrors?.content) { setComposerError(err.fieldErrors.content); return; }
      setError(err.message);
    }
  }

  async function handleSendMessage(event) { event.preventDefault(); await sendMessageText(draft); }
  async function handleOptionSelect(opt)  { setDraft(opt); await sendMessageText(opt); }

  // ── Auth / session handlers ───────────────────────────────────────────────
  async function handleLoginSuccess(sessionData) {
    await updatePresence(sessionData.username, true);
    setSession(sessionData);
  }

  function handleGuestAccess() {
    setSearchQuery(''); setDraft(''); setMessages([]); setUsers([]); setAssistants([]);
    setUnreadCounts({}); setLastMessages({}); setActivitySummary(null); setUserActivityMap({});
    setShowActivity(false); processedMessageIdsRef.current.clear();
    loadedConvUsernameRef.current = null;
    setSession(createGuestSession());
  }

  async function handleLogout() {
    const username = sessionRef.current?.username || session?.username;
    if (username && !sessionRef.current?.isGuest && !session?.isGuest) {
      try { await request(`${userServiceUrl}/api/users/${encodeURIComponent(username)}/logout`, { method: 'POST' }); }
      catch { /* best-effort */ }
    }
    window.localStorage.removeItem(SESSION_STORAGE_KEY);
    setSession(null); setUsers([]); setAssistants([]); setSelectedUser(null); setMessages([]);
    setUnreadCounts({}); setLastMessages({}); setUserActivityMap({});
    setDraft(''); setActivitySummary(null); setShowActivity(false);
    loadedConvUsernameRef.current = null;
    processedMessageIdsRef.current.clear();
    clientRef.current?.deactivate();
  }

  function handleSelectUser(user) {
    setSelectedUser(user);
    setMobileContactsOpen(false);
    setComposerError('');
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Render
  // ─────────────────────────────────────────────────────────────────────────

  if (!session) {
    return <AuthPage onLoginSuccess={handleLoginSuccess} onGuestAccess={handleGuestAccess} />;
  }

  return (
    <div className="app-shell">
      <AppRail
        loggedInUsername={loggedInUsername}
        railUsernameDisplay={railUsernameDisplay}
        isGuestSession={isGuestSession}
        showActivity={showActivity}
        activitySummary={activitySummary}
        activityToggleTitle={activityToggleTitle}
        onToggleActivity={() => setShowActivity((v) => !v)}
        onLogout={handleLogout}
      />

      <AppSidebar
        mobileOpen={mobileContactsOpen}
        isGuestSession={isGuestSession}
        searchQuery={searchQuery}
        onSearchChange={setSearchQuery}
        showActivity={showActivity}
        activityToggleTitle={activityToggleTitle}
        onToggleActivity={() => setShowActivity((v) => !v)}
        filteredHumanUsers={filteredHumanUsers}
        filteredBotUsers={botUsers}
        selectedUser={selectedUser}
        unreadCounts={unreadCounts}
        lastMessages={lastMessages}
        userActivityMap={userActivityMap}
        botsExpanded={botsExpanded}
        onToggleBots={() => setBotsExpanded((v) => !v)}
        onSelectUser={handleSelectUser}
        currentUsername={session.username}
        formatPresenceTime={formatPresenceTime}
        formatMessageTime={formatMessageTime}
        getInitials={getInitials}
      />

      {mobileContactsOpen && (
        <button className="mobile-overlay" type="button" onClick={() => setMobileContactsOpen(false)} aria-label="Close contacts" />
      )}

      <ChatPanel
        selectedUser={selectedUser}
        session={session}
        isGuestSession={isGuestSession}
        messages={messages}
        loadingConversation={loadingConversation}
        draft={draft}
        composerError={composerError}
        error={error}
        showBotRoutingHint={showBotRoutingHint}
        showAidRoutingHint={showAidRoutingHint}
        scrollRef={scrollRef}
        onMobileMenuOpen={() => setMobileContactsOpen(true)}
        onDraftChange={(v) => { setDraft(v); setComposerError(''); setError(''); }}
        onSend={handleSendMessage}
        onOptionSelect={handleOptionSelect}
      />
    </div>
  );
}
