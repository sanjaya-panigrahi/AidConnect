import { useEffect, useMemo, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import MessageBubble from './components/MessageBubble';

const userServiceUrl = import.meta.env.VITE_USER_SERVICE_URL || 'http://localhost:8081';
const chatServiceUrl = import.meta.env.VITE_CHAT_SERVICE_URL || 'http://localhost:8082';
const chatWsUrl = import.meta.env.VITE_CHAT_WS_URL || `${chatServiceUrl}/ws-chat`;
const defaultUserAvatar = '/default-user.svg';

const emptyRegister = {
  firstName: '',
  lastName: '',
  username: '',
  password: '',
  confirmPassword: '',
  email: ''
};

const emptyLogin = {
  username: '',
  password: ''
};

const SESSION_STORAGE_KEY = 'chat-session';
const GUEST_USERNAME_PREFIX = 'guest-';

function loadInitialSession() {
  if (typeof window === 'undefined') {
    return null;
  }

  const storedSession = window.localStorage.getItem(SESSION_STORAGE_KEY);
  if (!storedSession) {
    return null;
  }

  try {
    return JSON.parse(storedSession);
  } catch {
    window.localStorage.removeItem(SESSION_STORAGE_KEY);
    return null;
  }
}

const MAX_RAIL_USERNAME_CHARS = 12;
const MAX_USERNAME_LENGTH = 50;
const MAX_NAME_LENGTH = 50;
const MAX_EMAIL_LENGTH = 255;
const MIN_LOGIN_PASSWORD_LENGTH = 6;
const MIN_REGISTER_PASSWORD_LENGTH = 8;
const MAX_PASSWORD_LENGTH = 100;
const MAX_MESSAGE_LENGTH = 4000;
const USERNAME_PATTERN = /^[A-Za-z0-9._-]+$/;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PASSWORD_NO_WHITESPACE_PATTERN = /^\S+$/;
const PASSWORD_UPPERCASE_PATTERN = /[A-Z]/;
const PASSWORD_LOWERCASE_PATTERN = /[a-z]/;
const PASSWORD_NUMBER_PATTERN = /\d/;
const PASSWORD_SPECIAL_PATTERN = /[^A-Za-z0-9]/;

function sortByTime(messages) {
  return [...messages].sort((left, right) => new Date(left.sentAt) - new Date(right.sentAt));
}

function mergeMessages(existing, incoming) {
  const map = new Map(existing.map((message) => [message.id, message]));
  incoming.forEach((message) => {
    map.set(message.id, { ...map.get(message.id), ...message });
  });
  return sortByTime(Array.from(map.values()));
}

function normalizeFieldErrors(fieldErrors) {
  if (!fieldErrors || typeof fieldErrors !== 'object' || Array.isArray(fieldErrors)) {
    return {};
  }

  return Object.entries(fieldErrors).reduce((errors, [field, message]) => {
    if (typeof message === 'string' && message.trim()) {
      errors[field] = message;
    }
    return errors;
  }, {});
}

async function readResponseBody(response) {
  if (response.status === 204) {
    return null;
  }

  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    try {
      return await response.json();
    } catch {
      return null;
    }
  }

  const text = await response.text();
  if (!text) {
    return null;
  }

  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

function validateLoginValues(form) {
  const payload = {
    username: form.username.trim(),
    password: form.password
  };
  const errors = {};

  if (!payload.username) {
    errors.username = 'Username is required.';
  } else if (payload.username.length < 3 || payload.username.length > MAX_USERNAME_LENGTH) {
    errors.username = `Username must be between 3 and ${MAX_USERNAME_LENGTH} characters.`;
  } else if (!USERNAME_PATTERN.test(payload.username)) {
    errors.username = 'Username can contain letters, numbers, dots, underscores, and hyphens only.';
  }

  if (!payload.password.trim()) {
    errors.password = 'Password is required.';
  } else if (payload.password.length < MIN_LOGIN_PASSWORD_LENGTH || payload.password.length > MAX_PASSWORD_LENGTH) {
    errors.password = `Password must be between ${MIN_LOGIN_PASSWORD_LENGTH} and ${MAX_PASSWORD_LENGTH} characters.`;
  }

  return { payload, errors };
}

function validateRegisterValues(form) {
  const payload = {
    firstName: form.firstName.trim(),
    lastName: form.lastName.trim(),
    username: form.username.trim(),
    password: form.password,
    email: form.email.trim()
  };
  const errors = {};
  const password = payload.password;

  if (!payload.firstName) {
    errors.firstName = 'First name is required.';
  } else if (payload.firstName.length > MAX_NAME_LENGTH) {
    errors.firstName = `First name must be ${MAX_NAME_LENGTH} characters or fewer.`;
  }

  if (!payload.lastName) {
    errors.lastName = 'Last name is required.';
  } else if (payload.lastName.length > MAX_NAME_LENGTH) {
    errors.lastName = `Last name must be ${MAX_NAME_LENGTH} characters or fewer.`;
  }

  if (!payload.username) {
    errors.username = 'Username is required.';
  } else if (payload.username.length < 3 || payload.username.length > MAX_USERNAME_LENGTH) {
    errors.username = `Username must be between 3 and ${MAX_USERNAME_LENGTH} characters.`;
  } else if (!USERNAME_PATTERN.test(payload.username)) {
    errors.username = 'Username can contain letters, numbers, dots, underscores, and hyphens only.';
  }

  if (!payload.email) {
    errors.email = 'Email is required.';
  } else if (payload.email.length > MAX_EMAIL_LENGTH) {
    errors.email = `Email must be ${MAX_EMAIL_LENGTH} characters or fewer.`;
  } else if (!EMAIL_PATTERN.test(payload.email)) {
    errors.email = 'Email must be a valid email address.';
  }

  if (!password.trim()) {
    errors.password = 'Password is required.';
  } else if (password.length < MIN_REGISTER_PASSWORD_LENGTH || password.length > MAX_PASSWORD_LENGTH) {
    errors.password = `Password must be between ${MIN_REGISTER_PASSWORD_LENGTH} and ${MAX_PASSWORD_LENGTH} characters.`;
  } else if (!PASSWORD_NO_WHITESPACE_PATTERN.test(password)) {
    errors.password = 'Password cannot contain spaces.';
  } else if (
    !PASSWORD_UPPERCASE_PATTERN.test(password) ||
    !PASSWORD_LOWERCASE_PATTERN.test(password) ||
    !PASSWORD_NUMBER_PATTERN.test(password) ||
    !PASSWORD_SPECIAL_PATTERN.test(password)
  ) {
    errors.password = 'Password must include uppercase, lowercase, number, and special character.';
  }

  if (!form.confirmPassword.trim()) {
    errors.confirmPassword = 'Please confirm your password.';
  } else if (form.confirmPassword !== password) {
    errors.confirmPassword = 'Passwords do not match.';
  }

  return { payload, errors };
}

function validateMessageContent(text) {
  if (!text.trim()) {
    return 'Message cannot be empty.';
  }

  if (text.length > MAX_MESSAGE_LENGTH) {
    return `Message content must be ${MAX_MESSAGE_LENGTH} characters or fewer.`;
  }

  return '';
}

function getPasswordChecks(password) {
  return {
    minLength: password.length >= MIN_REGISTER_PASSWORD_LENGTH,
    upper: PASSWORD_UPPERCASE_PATTERN.test(password),
    lower: PASSWORD_LOWERCASE_PATTERN.test(password),
    number: PASSWORD_NUMBER_PATTERN.test(password),
    special: PASSWORD_SPECIAL_PATTERN.test(password),
    noWhitespace: password.length > 0 ? PASSWORD_NO_WHITESPACE_PATTERN.test(password) : false
  };
}

function getPasswordStrength(password) {
  if (!password) {
    return { label: 'Enter a password', level: 'none' };
  }

  const checks = getPasswordChecks(password);
  const score = Object.values(checks).filter(Boolean).length;
  if (score <= 2) {
    return { label: 'Weak password', level: 'weak' };
  }
  if (score <= 4) {
    return { label: 'Medium password', level: 'medium' };
  }
  return { label: 'Strong password', level: 'strong' };
}

function createGuestSession() {
  const timestamp = Date.now();
  const suffix = timestamp.toString(36);

  return {
    userId: timestamp,
    username: `${GUEST_USERNAME_PREFIX}${suffix}`,
    firstName: 'Guest',
    lastName: 'Assistant',
    email: 'guest@chatassist.local',
    message: 'Guest assistant access enabled.',
    isGuest: true
  };
}

function RailIcon({ type }) {
  if (type === 'chat') {
    return (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M4 5h16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H9l-5 4v-4H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z" />
      </svg>
    );
  }

  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 8.2a3.8 3.8 0 1 0 0 7.6 3.8 3.8 0 0 0 0-7.6zm9 3.8-2 .6a7.7 7.7 0 0 1-.5 1.2l1.2 1.7-1.8 1.8-1.7-1.2a7.7 7.7 0 0 1-1.2.5l-.6 2h-2.6l-.6-2a7.7 7.7 0 0 1-1.2-.5l-1.7 1.2-1.8-1.8 1.2-1.7a7.7 7.7 0 0 1-.5-1.2L3 12l.6-2.6a7.7 7.7 0 0 1 .5-1.2L2.9 6.5 4.7 4.7l1.7 1.2a7.7 7.7 0 0 1 1.2-.5l.6-2h2.6l.6 2a7.7 7.7 0 0 1 1.2.5l1.7-1.2 1.8 1.8-1.2 1.7a7.7 7.7 0 0 1 .5 1.2L21 12z" />
      </svg>
  );
}

async function request(url, options = {}) {
  const response = await fetch(url, {
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    },
    ...options
  });

  const body = await readResponseBody(response);

  if (!response.ok) {
    const message = typeof body === 'string'
      ? body
      : body?.message || body?.error || response.statusText || 'Request failed';
    const requestError = new Error(message || 'Request failed');
    requestError.status = response.status;
    requestError.fieldErrors = normalizeFieldErrors(body?.fieldErrors);
    requestError.payload = body;
    throw requestError;
  }

  return body;
}

export default function App() {
  const [mode, setMode] = useState('login');
  const [registerForm, setRegisterForm] = useState(emptyRegister);
  const [loginForm, setLoginForm] = useState(emptyLogin);
  const [session, setSession] = useState(loadInitialSession);
  const [users, setUsers] = useState([]);
  const [assistants, setAssistants] = useState([]);
  const [selectedUser, setSelectedUser] = useState(null);
  const [messages, setMessages] = useState([]);
  const [draft, setDraft] = useState('');
  const [error, setError] = useState('');
  const [loginErrors, setLoginErrors] = useState({});
  const [registerErrors, setRegisterErrors] = useState({});
  const [composerError, setComposerError] = useState('');
  const [loadingConversation, setLoadingConversation] = useState(false);
  const [botsExpanded, setBotsExpanded] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [mobileContactsOpen, setMobileContactsOpen] = useState(false);
  const [unreadCounts, setUnreadCounts] = useState({});
  const [lastMessages, setLastMessages] = useState({});
  const [activitySummary, setActivitySummary] = useState(null);
  const [userActivityMap, setUserActivityMap] = useState({});
  const [showActivity, setShowActivity] = useState(false);
  const clientRef = useRef(null);
  const sessionRef = useRef(null);
  const selectedUserRef = useRef(null);
  const lastDirectoryRefreshRef = useRef(0);
  const processedMessageIdsRef = useRef(new Set());
  const scrollRef = useRef(null);

  const humanUsers = useMemo(() => {
    return users
      .sort((left, right) => {
        // Rank: online first, then away users (with lastActive), then logged-off/unknown last.
        const leftRank = left.online ? 0 : left.lastActive ? 1 : 2;
        const rightRank = right.online ? 0 : right.lastActive ? 1 : 2;
        if (leftRank !== rightRank) {
          return leftRank - rightRank;
        }

        // Within away users, sort by recency (most recently active first).
        if (leftRank === 1) {
          const leftActive = new Date(left.lastActive).getTime();
          const rightActive = new Date(right.lastActive).getTime();
          if (leftActive !== rightActive) {
            return rightActive - leftActive;
          }
        }

        // Stable fallback by display name.
        const leftName = `${left.firstName} ${left.lastName}`.trim().toLowerCase();
        const rightName = `${right.firstName} ${right.lastName}`.trim().toLowerCase();
        return leftName.localeCompare(rightName);
      });
  }, [users]);

  const botUsers = useMemo(() => assistants, [assistants]);
  const isGuestSession = Boolean(session?.isGuest);
  const botAssistant = useMemo(
    () => botUsers.find((assistant) => (assistant.username || '').toLowerCase() === 'bot') || null,
    [botUsers]
  );
  const aidAssistant = useMemo(
    () => botUsers.find((assistant) => (assistant.username || '').toLowerCase() === 'aid') || null,
    [botUsers]
  );
  // Show routing hint when @bot is typed in a user-user chat (message will be rerouted to bot)
  const showBotRoutingHint = useMemo(() => {
    if (!selectedUser) return false;
    const target = (selectedUser.username || '').toLowerCase();
    const isAssistantThread = target === 'bot' || target === 'aid';
    return !isAssistantThread && /@bot/i.test(draft);
  }, [selectedUser, draft]);
  // Show routing hint when @aid is typed in a user-user chat (private reroute to aid)
  const showAidRoutingHint = useMemo(() => {
    if (!selectedUser) return false;
    const target = (selectedUser.username || '').toLowerCase();
    const isAssistantThread = target === 'bot' || target === 'aid';
    return !isAssistantThread && /@aid/i.test(draft);
  }, [selectedUser, draft]);

  const filteredHumanUsers = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) {
      return humanUsers;
    }

    return humanUsers.filter((user) => {
      const fullName = `${user.firstName} ${user.lastName}`.toLowerCase();
      return fullName.includes(query) || (user.username || '').toLowerCase().includes(query);
    });
  }, [humanUsers, searchQuery]);

  const filteredBotUsers = useMemo(() => botUsers, [botUsers]);
  const loggedInUsername = useMemo(
    () => (session?.isGuest ? 'guest mode' : (session?.username || 'user')),
    [session]
  );
  const railUsernameDisplay = useMemo(() => {
    if (!loggedInUsername) {
      return 'user';
    }
    if (loggedInUsername.length <= MAX_RAIL_USERNAME_CHARS) {
      return loggedInUsername;
    }
    return `${loggedInUsername.slice(0, MAX_RAIL_USERNAME_CHARS)}...`;
  }, [loggedInUsername]);
  const activityToggleTitle = isGuestSession
    ? 'Guest mode only includes assistant chat and does not show user activity.'
    : showActivity
      ? 'Hide activity details: your logins today and users chatted today, plus each user’s activity stats.'
      : 'Show activity details: your logins today and users chatted today, plus each user’s activity stats.';
  const registerPasswordChecks = useMemo(
    () => getPasswordChecks(registerForm.password),
    [registerForm.password]
  );
  const registerPasswordStrength = useMemo(
    () => getPasswordStrength(registerForm.password),
    [registerForm.password]
  );

  async function updatePresence(username, isOnline) {
    if (!username) {
      return;
    }

    const action = isOnline ? 'online' : 'offline';
    try {
      await request(`${userServiceUrl}/api/users/${encodeURIComponent(username)}/${action}`, {
        method: 'PUT'
      });
    } catch {
      // Presence update is best-effort; chat should continue even if it fails.
    }
  }

  async function loadActivitySummary(username) {
    if (!username) {
      return;
    }
    try {
      const [summary, chatPeerSummary] = await Promise.all([
        request(`${userServiceUrl}/api/users/${encodeURIComponent(username)}/activity/today`),
        request(`${chatServiceUrl}/api/chats/${encodeURIComponent(username)}/activity/today`)
      ]);
      setActivitySummary({
        ...summary,
        chatPeerCount: chatPeerSummary?.chatPeerCount ?? 0
      });
    } catch {
      // Activity summary is best-effort.
    }
  }

  async function loadAllUsersActivity() {
    try {
      const [summaries, chatPeerSummaries] = await Promise.all([
        request(`${userServiceUrl}/api/users/activity/today`),
        request(`${chatServiceUrl}/api/chats/activity/today`)
      ]);
      const chatPeerMap = {};
      chatPeerSummaries.forEach((summary) => {
        chatPeerMap[summary.username] = summary.chatPeerCount;
      });

      const map = {};
      summaries.forEach((summary) => {
        map[summary.username] = {
          loginCount: summary.loginCount,
          chatPeerCount: chatPeerMap[summary.username] ?? 0
        };
      });
      chatPeerSummaries.forEach((summary) => {
        if (!map[summary.username]) {
          map[summary.username] = {
            loginCount: 0,
            chatPeerCount: summary.chatPeerCount
          };
        }
      });
      setUserActivityMap(map);
    } catch {
      // Best-effort; no disruption if it fails.
    }
  }

  function refreshDirectory(force = false) {
    const activeSession = sessionRef.current;
    if (!activeSession?.username) {
      return;
    }

    const now = Date.now();
    // Throttle background refreshes to avoid request bursts during active chats.
    if (!force && now - lastDirectoryRefreshRef.current < 4000) {
      return;
    }

    lastDirectoryRefreshRef.current = now;
    loadDirectory(activeSession.username);
  }


  useEffect(() => {
    sessionRef.current = session;
  }, [session]);

  useEffect(() => {
    selectedUserRef.current = selectedUser;
  }, [selectedUser]);

  useEffect(() => {
    if (selectedUser) {
      setUnreadCounts((prev) => {
        const updated = { ...prev };
        delete updated[selectedUser.username];
        return updated;
      });
    }
  }, [selectedUser]);

  useEffect(() => {
    if (!session) {
      return;
    }

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

    // Keep presence reasonably fresh for online/offline indicators.
    const userRefreshInterval = setInterval(() => {
      refreshDirectory(true);
    }, 10000);

    // Refresh login/logout counts every minute.
    const activityRefreshInterval = session.isGuest
      ? null
      : setInterval(() => {
        const active = sessionRef.current;
        if (active?.username && !active.isGuest) {
          loadActivitySummary(active.username);
          loadAllUsersActivity();
        }
      }, 60000);

    const handleWindowFocus = () => refreshDirectory(true);
    const handleVisibilityChange = () => {
      if (!document.hidden) {
        refreshDirectory(true);
      }
    };
    window.addEventListener('focus', handleWindowFocus);
    document.addEventListener('visibilitychange', handleVisibilityChange);

    const client = new Client({
      webSocketFactory: () => new SockJS(chatWsUrl),
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe(`/topic/messages/${session.username}`, (frame) => {
          const payload = JSON.parse(frame.body);
          handleIncomingMessage(payload);
        });
        client.subscribe(`/topic/status/${session.username}`, (frame) => {
          const payload = JSON.parse(frame.body);
          handleIncomingMessage(payload);
        });
      }
    });

    client.activate();
    clientRef.current = client;

    return () => {
      clearInterval(userRefreshInterval);
      if (activityRefreshInterval) {
        clearInterval(activityRefreshInterval);
      }
      window.removeEventListener('focus', handleWindowFocus);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      client.deactivate();
    };
  }, [session]);

  useEffect(() => {
    if (!session?.username || session.isGuest) {
      return;
    }

    const handlePageExit = () => {
      const username = sessionRef.current?.username;
      if (!username) {
        return;
      }
      fetch(`${userServiceUrl}/api/users/${encodeURIComponent(username)}/offline`, {
        method: 'PUT',
        keepalive: true,
        headers: {
          'Content-Type': 'application/json'
        }
      }).catch(() => undefined);
    };

    window.addEventListener('beforeunload', handlePageExit);
    window.addEventListener('pagehide', handlePageExit);

    return () => {
      window.removeEventListener('beforeunload', handlePageExit);
      window.removeEventListener('pagehide', handlePageExit);
    };
  }, [session?.username]);

  useEffect(() => {
    scrollRef.current?.scrollIntoView({ behavior: 'auto' });
  }, [messages]);

  useEffect(() => {
    if (!selectedUser || !session) {
      return;
    }
    loadConversation(selectedUser.username);
  }, [selectedUser, session]);

  useEffect(() => {
    if (!selectedUser) {
      setMobileContactsOpen(true);
    }
    setComposerError('');
  }, [selectedUser]);

  function updateLoginField(field, value) {
    setLoginForm((current) => ({ ...current, [field]: value }));
    setError('');
    setLoginErrors((current) => {
      if (!current[field]) {
        return current;
      }
      const next = { ...current };
      delete next[field];
      return next;
    });
  }

  function updateRegisterField(field, value) {
    setRegisterForm((current) => ({ ...current, [field]: value }));
    setError('');
    setRegisterErrors((current) => {
      if (!current[field]) {
        return current;
      }
      const next = { ...current };
      delete next[field];
      return next;
    });
  }

  function switchAuthMode(nextMode) {
    setMode(nextMode);
    setError('');
    setLoginErrors({});
    setRegisterErrors({});
  }

  async function loadDirectory(excludeUsername) {
    try {
      const guestMode = Boolean(sessionRef.current?.isGuest);
      const assistantUsers = await request(`${userServiceUrl}/api/users/assistants`);
      const dbUsers = guestMode
        ? []
        : await request(`${userServiceUrl}/api/users?excludeUsername=${excludeUsername}`);

      setUsers(dbUsers || []);
      setAssistants(assistantUsers);
      setSelectedUser((current) => {
        const availableUsers = [...(dbUsers || []), ...assistantUsers];
        if (!current) {
          return guestMode ? assistantUsers[0] || null : dbUsers[0] || assistantUsers[0] || null;
        }

        return availableUsers.find((user) => user.username === current.username)
          || (guestMode ? assistantUsers[0] || null : dbUsers[0] || assistantUsers[0] || null);
      });
    } catch (requestError) {
      setError(requestError.message);
    }
  }

  async function loadConversation(otherUsername) {
    if (!session) {
      return;
    }

    setLoadingConversation(true);
    try {
      const data = await request(
        `${chatServiceUrl}/api/chats/conversation?userA=${session.username}&userB=${otherUsername}`
      );
      const sorted = sortByTime(data);
      setMessages(sorted);
      // Track last message for contact preview
      if (sorted.length > 0) {
        const last = sorted[sorted.length - 1];
        setLastMessages((prev) => {
          const existing = prev[otherUsername];
          if (!existing || new Date(last.sentAt) >= new Date(existing.sentAt)) {
            return { ...prev, [otherUsername]: last };
          }
          return prev;
        });
      }
      await markSeenMessages(data, otherUsername);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoadingConversation(false);
    }
  }

  async function markSeenMessages(conversation, otherUsername) {
    if (!session || !selectedUser || selectedUser.username !== otherUsername) {
      return;
    }

    const unread = conversation.filter(
      (message) => message.senderUsername === otherUsername && message.status !== 'SEEN'
    );

    await Promise.all(
      unread.map((message) =>
        request(`${chatServiceUrl}/api/chats/messages/status`, {
          method: 'PATCH',
          body: JSON.stringify({
            messageId: message.id,
            status: 'SEEN'
          })
        })
      )
    );
  }

  function handleIncomingMessage(message) {
    // The backend broadcasts every message to both /topic/messages/ and /topic/status/,
    // so the same message arrives twice. Deduplicate by message ID to prevent
    // double-counting unread badges and duplicate chat entries.
    if (message.id) {
      if (processedMessageIdsRef.current.has(message.id)) {
        return; // already handled — skip the duplicate
      }
      processedMessageIdsRef.current.add(message.id);
      // Evict old IDs to avoid unbounded memory growth (keep last 500)
      if (processedMessageIdsRef.current.size > 500) {
        const oldest = processedMessageIdsRef.current.values().next().value;
        processedMessageIdsRef.current.delete(oldest);
      }
    }

    const activeSession = sessionRef.current;
    const activeSelectedUser = selectedUserRef.current;

    const inActiveConversation =
      activeSelectedUser &&
      activeSession &&
      (
        // Normal user-to-user message
        (message.senderUsername === activeSelectedUser.username && message.receiverUsername === activeSession.username) ||
        (message.senderUsername === activeSession.username && message.receiverUsername === activeSelectedUser.username) ||
        // Assistant reply with contextUsername: surfaces in the original user-user thread.
        // e.g. sender=aid, receiver=userA, contextUsername=userB  →  visible in userA↔userB chat.
        (message.contextUsername != null && (
          (message.receiverUsername === activeSession.username && message.contextUsername === activeSelectedUser.username) ||
          (message.receiverUsername === activeSelectedUser.username && message.contextUsername === activeSession.username)
        ))
      );

    if (inActiveConversation) {
      setMessages((current) => mergeMessages(current, [message]));
    } else if (activeSession && !inActiveConversation) {
      // Incoming message for non-active conversation - track unread
      let senderForUnread = null;
      if (message.senderUsername !== activeSession.username && message.contextUsername === null) {
        // Direct message from sender
        senderForUnread = message.senderUsername;
      } else if (message.contextUsername && message.receiverUsername === activeSession.username) {
        // Assistant reply with context - show unread for the context user
        senderForUnread = message.contextUsername;
      }

      if (senderForUnread) {
        setUnreadCounts((prev) => ({
          ...prev,
          [senderForUnread]: (prev[senderForUnread] || 0) + 1
        }));
      }
    }

    // Track last message per peer for contact card preview
    const peerUsername =
      activeSession &&
      (message.senderUsername === activeSession.username
        ? message.receiverUsername
        : message.senderUsername);
    if (peerUsername && peerUsername !== activeSession?.username) {
      setLastMessages((prev) => {
        const existing = prev[peerUsername];
        if (!existing || new Date(message.sentAt) >= new Date(existing.sentAt)) {
          return { ...prev, [peerUsername]: message };
        }
        return prev;
      });
    }

    // Refresh directory in background so status changes (online/offline) stay current.
    refreshDirectory();


    if (
      activeSelectedUser &&
      activeSession &&
      message.senderUsername === activeSelectedUser.username &&
      message.receiverUsername === activeSession.username &&
      message.status !== 'SEEN'
    ) {
      request(`${chatServiceUrl}/api/chats/messages/status`, {
        method: 'PATCH',
        body: JSON.stringify({
          messageId: message.id,
          status: 'SEEN'
        })
      }).catch(() => undefined);
    }
  }

  async function handleRegister(event) {
    event.preventDefault();
    setError('');
    setRegisterErrors({});

    const { payload, errors } = validateRegisterValues(registerForm);
    if (Object.keys(errors).length > 0) {
      setRegisterErrors(errors);
      return;
    }

    try {
      const response = await request(`${userServiceUrl}/api/users/register`, {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      await updatePresence(response.username, true);
      setSession(response);
      setRegisterForm(emptyRegister);
      setRegisterErrors({});
    } catch (requestError) {
      if (Object.keys(requestError.fieldErrors || {}).length > 0) {
        setRegisterErrors(requestError.fieldErrors);
        setError(requestError.message === 'Validation failed.' ? 'Please correct the highlighted fields.' : requestError.message);
        return;
      }
      setError(requestError.message);
    }
  }

  async function handleLogin(event) {
    event.preventDefault();
    setError('');
    setLoginErrors({});

    const { payload, errors } = validateLoginValues(loginForm);
    if (Object.keys(errors).length > 0) {
      setLoginErrors(errors);
      return;
    }

    try {
      const response = await request(`${userServiceUrl}/api/users/login`, {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      await updatePresence(response.username, true);
      setSession(response);
      setLoginForm(emptyLogin);
      setLoginErrors({});
    } catch (requestError) {
      if (Object.keys(requestError.fieldErrors || {}).length > 0) {
        setLoginErrors(requestError.fieldErrors);
        setError(requestError.message === 'Validation failed.' ? 'Please correct the highlighted fields.' : requestError.message);
        return;
      }
      setError(requestError.message);
    }
  }

  function handleGuestAccess() {
    setError('');
    setLoginErrors({});
    setRegisterErrors({});
    setLoginForm(emptyLogin);
    setRegisterForm(emptyRegister);
    setSearchQuery('');
    setDraft('');
    setMessages([]);
    setUsers([]);
    setAssistants([]);
    setUnreadCounts({});
    setLastMessages({});
    setActivitySummary(null);
    setUserActivityMap({});
    setShowActivity(false);
    processedMessageIdsRef.current.clear();
    setSession(createGuestSession());
  }

  async function sendMessageText(text) {
    if (!selectedUser || !session) return;

    setError('');
    setComposerError('');

    const messageValidationError = validateMessageContent(text);
    if (messageValidationError) {
      setComposerError(messageValidationError);
      return;
    }

    const currentTargetUsername = (selectedUser.username || '').toLowerCase();
    const inAssistantThread = currentTargetUsername === 'bot' || currentTargetUsername === 'aid';
    const shouldRouteToBot = !inAssistantThread && /@bot/i.test(text);
    const shouldRouteToAid = !inAssistantThread && /@aid/i.test(text);
    const targetUser = shouldRouteToBot
      ? botAssistant
      : shouldRouteToAid
        ? aidAssistant
        : selectedUser;

    if (!targetUser) {
      const assistantName = shouldRouteToAid ? '@aid' : '@bot';
      setError(`${assistantName} assistant is not available right now. Try again in a moment.`);
      return;
    }

    const payload = {
      senderId: session.userId,
      senderUsername: session.username,
      receiverId: targetUser.id,
      receiverUsername: targetUser.username,
      content: text,
      messageType: ['bot', 'aid'].includes((targetUser.username || '').toLowerCase()) ? 'BOT' : 'USER',
      contextUsername: null
    };

    try {
      const response = await request(`${chatServiceUrl}/api/chats/messages`, {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      if (shouldRouteToBot || shouldRouteToAid) {
        setSelectedUser(targetUser);
        setMessages([response]);
      } else {
        setMessages((current) => mergeMessages(current, [response]));
      }
      setDraft('');
      setComposerError('');
    } catch (requestError) {
      if (requestError.fieldErrors?.content) {
        setComposerError(requestError.fieldErrors.content);
        return;
      }
      setError(requestError.message);
    }
  }

  async function handleOptionSelect(optionNumber) {
    setDraft(optionNumber);
    await sendMessageText(optionNumber);
  }

  async function handleSendMessage(event) {
    event.preventDefault();
    await sendMessageText(draft);
  }

  async function handleLogout() {
    const username = sessionRef.current?.username || session?.username;
    // POST /logout records the LOGOUT event in the DB and marks the user offline.
    if (username && !sessionRef.current?.isGuest && !session?.isGuest) {
      try {
        await request(`${userServiceUrl}/api/users/${encodeURIComponent(username)}/logout`, {
          method: 'POST'
        });
      } catch {
        // best-effort — still clear the local session even if the call fails
      }
    }
    window.localStorage.removeItem(SESSION_STORAGE_KEY);
    setSession(null);
    setUsers([]);
    setAssistants([]);
    setSelectedUser(null);
    setMessages([]);
    setUnreadCounts({});
    setLastMessages({});
    setUserActivityMap({});
    processedMessageIdsRef.current.clear();
    setDraft('');
    setActivitySummary(null);
    setShowActivity(false);
    clientRef.current?.deactivate();
  }

  function handleSelectUser(user) {
    setSelectedUser(user);
    setMobileContactsOpen(false);
    setComposerError('');
  }

  function getInitials(user) {
    if (!user) {
      return 'NA';
    }

    // Special case for bot and aid assistants
    const username = (user.username || '').toLowerCase();
    if (username === 'bot') return 'BOT';
    if (username === 'aid') return 'AID';

    const first = (user.firstName || '').trim().charAt(0);
    const last = (user.lastName || '').trim().charAt(0);
    const fallback = (user.username || '').trim().charAt(0);
    return `${first}${last || fallback}`.toUpperCase();
  }

  function formatPresenceTime(user) {
    if (user.online) {
      return 'now';
    }

    if (!user.lastActive) {
      return 'away';
    }

    const lastActive = new Date(user.lastActive).getTime();
    if (Number.isNaN(lastActive)) {
      return 'away';
    }

    const diffMs = Date.now() - lastActive;
    const diffMinutes = Math.max(1, Math.floor(diffMs / 60000));

    if (diffMinutes < 60) {
      return `Away · ${diffMinutes}m ago`;
    }

    const diffHours = Math.floor(diffMinutes / 60);
    if (diffHours < 24) {
      return `Away · ${diffHours}h ago`;
    }

    const diffDays = Math.floor(diffHours / 24);
    return `Away · ${diffDays}d ago`;
  }

  function formatMessageTime(sentAt) {
    if (!sentAt) return '';
    const date = new Date(sentAt);
    if (Number.isNaN(date.getTime())) return '';
    const diffMs = Date.now() - date.getTime();
    const diffMinutes = Math.floor(diffMs / 60000);
    if (diffMinutes < 1) return 'now';
    if (diffMinutes < 60) return `${diffMinutes}m`;
    const diffHours = Math.floor(diffMinutes / 60);
    if (diffHours < 24) return `${diffHours}h`;
    const diffDays = Math.floor(diffHours / 24);
    if (diffDays < 7) return `${diffDays}d`;
    return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  }

  if (!session) {
    return (
      <div className="auth-shell">
        <div className="auth-panel">

          {/* ── Brand panel ── */}
          <div className="auth-brand">
            <div className="auth-brand-mark">CA</div>
            <h1>Chat Assist</h1>
            <p className="auth-brand-tagline">
              Connect with people and AI assistants in real time. Ask questions and get instant help.
            </p>
            <div className="auth-brand-features">
              <div className="auth-feature">
                <div className="auth-feature-icon">
                  <svg viewBox="0 0 24 24"><path d="M4 5h16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H9l-5 4v-4H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z" /></svg>
                </div>
                <span>Real-time messaging with other users</span>
              </div>
              <div className="auth-feature">
                <div className="auth-feature-icon">
                  <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="3"/><path d="M19.1 4.9a9 9 0 0 1 0 14.2M4.9 4.9a9 9 0 0 0 0 14.2"/></svg>
                </div>
                <span>AI chat assistant with <strong style={{color:'#fff'}}>@bot</strong></span>
              </div>
              <div className="auth-feature">
                <div className="auth-feature-icon">
                  <svg viewBox="0 0 24 24"><path d="M4 5h7l2 2h7a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z"/></svg>
                </div>
                <span>Doctor appointment booking with <strong style={{color:'#fff'}}>@aid</strong></span>
              </div>
            </div>
            <div className="auth-brand-divider" />
          </div>

          {/* ── Form panel ── */}
          <div className="auth-card">
            <div className="auth-card-header">
              <h2>{mode === 'login' ? 'Welcome back' : 'Create an account'}</h2>
              <p>{mode === 'login' ? 'Sign in to continue your conversations.' : 'Join ChatAssist and start connecting.'}</p>
            </div>

            <div className="auth-switcher">
              <button className={mode === 'login' ? 'active' : ''} onClick={() => switchAuthMode('login')}>Sign in</button>
              <button className={mode === 'register' ? 'active' : ''} onClick={() => switchAuthMode('register')}>Register</button>
            </div>

            {mode === 'login' ? (
              <form className="auth-form" onSubmit={handleLogin} noValidate>
                <div className="auth-form-field">
                  <label htmlFor="login-username">Username</label>
                  <input
                    id="login-username"
                    placeholder="e.g. username"
                    value={loginForm.username}
                    onChange={(event) => updateLoginField('username', event.target.value)}
                    autoComplete="username"
                    maxLength={MAX_USERNAME_LENGTH}
                    aria-invalid={Boolean(loginErrors.username)}
                    className={loginErrors.username ? 'input-invalid' : ''}
                  />
                  {loginErrors.username && <span className="field-error">{loginErrors.username}</span>}
                </div>
                <div className="auth-form-field">
                  <label htmlFor="login-password">Password</label>
                  <input
                    id="login-password"
                    type="password"
                    placeholder="••••••••"
                    value={loginForm.password}
                    onChange={(event) => updateLoginField('password', event.target.value)}
                    autoComplete="current-password"
                    maxLength={MAX_PASSWORD_LENGTH}
                    aria-invalid={Boolean(loginErrors.password)}
                    className={loginErrors.password ? 'input-invalid' : ''}
                  />
                  {loginErrors.password && <span className="field-error">{loginErrors.password}</span>}
                </div>
                <button type="submit">
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <path d="M15 3h6v18h-6M10 17l5-5-5-5M14 12H3"/>
                  </svg>
                  Sign in
                </button>
              </form>
            ) : (
              <form className="auth-form" onSubmit={handleRegister} noValidate>
                <div className="auth-input-group">
                  <div className="auth-form-field">
                    <label htmlFor="reg-first">First name</label>
                    <input
                      id="reg-first"
                      placeholder="first name"
                      value={registerForm.firstName}
                      onChange={(event) => updateRegisterField('firstName', event.target.value)}
                      autoComplete="given-name"
                      maxLength={MAX_NAME_LENGTH}
                      aria-invalid={Boolean(registerErrors.firstName)}
                      className={registerErrors.firstName ? 'input-invalid' : ''}
                    />
                    {registerErrors.firstName && <span className="field-error">{registerErrors.firstName}</span>}
                  </div>
                  <div className="auth-form-field">
                    <label htmlFor="reg-last">Last name</label>
                    <input
                      id="reg-last"
                      placeholder="last name"
                      value={registerForm.lastName}
                      onChange={(event) => updateRegisterField('lastName', event.target.value)}
                      autoComplete="family-name"
                      maxLength={MAX_NAME_LENGTH}
                      aria-invalid={Boolean(registerErrors.lastName)}
                      className={registerErrors.lastName ? 'input-invalid' : ''}
                    />
                    {registerErrors.lastName && <span className="field-error">{registerErrors.lastName}</span>}
                  </div>
                </div>
                <div className="auth-form-field">
                  <label htmlFor="reg-username">Username</label>
                  <input
                    id="reg-username"
                    placeholder="username"
                    value={registerForm.username}
                    onChange={(event) => updateRegisterField('username', event.target.value)}
                    autoComplete="username"
                    maxLength={MAX_USERNAME_LENGTH}
                    aria-invalid={Boolean(registerErrors.username)}
                    className={registerErrors.username ? 'input-invalid' : ''}
                  />
                  {registerErrors.username && <span className="field-error">{registerErrors.username}</span>}
                </div>
                <div className="auth-form-field">
                  <label htmlFor="reg-email">Email</label>
                  <input
                    id="reg-email"
                    type="email"
                    placeholder="email@chatassist.com"
                    value={registerForm.email}
                    onChange={(event) => updateRegisterField('email', event.target.value)}
                    autoComplete="email"
                    maxLength={MAX_EMAIL_LENGTH}
                    aria-invalid={Boolean(registerErrors.email)}
                    className={registerErrors.email ? 'input-invalid' : ''}
                  />
                  {registerErrors.email && <span className="field-error">{registerErrors.email}</span>}
                </div>
                <div className="auth-form-field">
                  <label htmlFor="reg-password">Password</label>
                  <input
                    id="reg-password"
                    type="password"
                    placeholder="••••••••"
                    value={registerForm.password}
                    onChange={(event) => updateRegisterField('password', event.target.value)}
                    autoComplete="new-password"
                    maxLength={MAX_PASSWORD_LENGTH}
                    aria-invalid={Boolean(registerErrors.password)}
                    className={registerErrors.password ? 'input-invalid' : ''}
                  />
                  {registerErrors.password && <span className="field-error">{registerErrors.password}</span>}
                  <div className={`password-strength ${registerPasswordStrength.level}`}>
                    {registerPasswordStrength.label}
                  </div>
                  <div className="password-rules" aria-live="polite">
                    <span className={registerPasswordChecks.minLength ? 'met' : ''}>8+ chars</span>
                    <span className={registerPasswordChecks.upper ? 'met' : ''}>uppercase</span>
                    <span className={registerPasswordChecks.lower ? 'met' : ''}>lowercase</span>
                    <span className={registerPasswordChecks.number ? 'met' : ''}>number</span>
                    <span className={registerPasswordChecks.special ? 'met' : ''}>special</span>
                    <span className={registerPasswordChecks.noWhitespace ? 'met' : ''}>no spaces</span>
                  </div>
                </div>
                <div className="auth-form-field">
                  <label htmlFor="reg-confirm-password">Confirm password</label>
                  <input
                    id="reg-confirm-password"
                    type="password"
                    placeholder="••••••••"
                    value={registerForm.confirmPassword}
                    onChange={(event) => updateRegisterField('confirmPassword', event.target.value)}
                    autoComplete="new-password"
                    maxLength={MAX_PASSWORD_LENGTH}
                    aria-invalid={Boolean(registerErrors.confirmPassword)}
                    className={registerErrors.confirmPassword ? 'input-invalid' : ''}
                  />
                  {registerErrors.confirmPassword && <span className="field-error">{registerErrors.confirmPassword}</span>}
                </div>
                <button type="submit">
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><line x1="19" y1="8" x2="19" y2="14"/><line x1="22" y1="11" x2="16" y2="11"/>
                  </svg>
                  Create account
                </button>
              </form>
            )}
            {error && (
              <div className="error-banner">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" style={{flexShrink:0}}>
                  <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
                </svg>
                {error}
              </div>
            )}
            {mode === 'login' && (
              <div className="auth-guest-entry" aria-label="Assistant-only guest access">
                <button
                  type="button"
                  className="auth-secret-trigger"
                  onClick={handleGuestAccess}
                  title="Open assistant-only guest chat"
                >
                  Try assistant-only mode
                </button>
              </div>
            )}
          </div>

        </div>
      </div>
    );
  }

  return (
    <div className="app-shell">
      <aside className="app-rail">
        <div className="rail-avatar">
          <img src={defaultUserAvatar} alt="Your profile" />
        </div>
        <div className="rail-user-pill" title={loggedInUsername}>
          <strong>{railUsernameDisplay}</strong>
        </div>

        {!isGuestSession && showActivity && activitySummary && (
          <div
            className="rail-activity"
            title={`Logins today: ${activitySummary.loginCount}  ·  Users chatted today: ${activitySummary.chatPeerCount ?? 0}`}
          >
            <span className="rail-activity-label">Today</span>
            <div className="rail-activity-row">
              <span className="rail-activity-login">L{activitySummary.loginCount}</span>
              <span className="rail-activity-sep">·</span>
              <span className="rail-activity-chat-users">U{activitySummary.chatPeerCount ?? 0}</span>
            </div>
          </div>
        )}
        <button className="rail-item active" type="button">
          <span className="rail-icon"><RailIcon type="chat" /></span>
          <span>Chat</span>
        </button>
        <button
          className={`rail-item rail-activity-toggle${showActivity ? ' active' : ''}`}
          type="button"
          title={activityToggleTitle}
          aria-label={activityToggleTitle}
          onClick={() => setShowActivity((value) => !value)}
          disabled={isGuestSession}
        >
          <span className="rail-icon">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
            </svg>
          </span>
          <span>{isGuestSession ? 'Guest' : 'Activity'}</span>
        </button>
        <button className="rail-item rail-logout" type="button" onClick={handleLogout}>
          <span className="rail-icon"><RailIcon type="settings" /></span>
          <span>{isGuestSession ? 'Exit' : 'Logout'}</span>
        </button>
      </aside>

      <aside className={`sidebar ${mobileContactsOpen ? 'open' : ''}`}>

        {isGuestSession ? (
          <div className="guest-sidebar-note">
            <strong>Guest assistant mode</strong>
            <span>Chat only with <strong>@bot</strong> and <strong>@aid</strong>. Sign in to reach other users.</span>
          </div>
        ) : (
          <div className="search-wrap">
            <input
              className="search-input"
              type="text"
              placeholder="Search users"
              value={searchQuery}
              onChange={(event) => setSearchQuery(event.target.value)}
              autoCorrect="on"
              spellCheck
            />
          </div>
        )}

        {!isGuestSession && (
          <button
            className={`mobile-activity-toggle${showActivity ? ' active' : ''}`}
            type="button"
            title={activityToggleTitle}
            aria-label={activityToggleTitle}
            onClick={() => setShowActivity((value) => !value)}
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
            </svg>
            <span>Activity</span>
          </button>
        )}

        {!isGuestSession && (
          <div className="contact-list">
            {filteredHumanUsers.map((user) => {
            const activity = userActivityMap[user.username] || { loginCount: 0, chatPeerCount: 0 };
            const lastMsg = lastMessages[user.username];
            const unreadCount = unreadCounts[user.username] || 0;
            return (
            <button
              key={user.id}
              className={`contact-card ${selectedUser?.id === user.id ? 'active' : ''}`}
              onClick={() => handleSelectUser(user)}
            >
              <div className="contact-avatar-wrap">
                <div className="contact-avatar">
                  <img src={defaultUserAvatar} alt={`${user.firstName} ${user.lastName} profile`} />
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
                {lastMsg ? (
                  <span className={`contact-preview${unreadCount > 0 ? ' unread' : ''}`}>
                    {lastMsg.senderUsername === session?.username ? 'You: ' : ''}
                    {lastMsg.content}
                  </span>
                ) : (
                  <span className="contact-preview muted">{user.online ? 'Online' : formatPresenceTime(user)}</span>
                )}
              </div>
              <div className="contact-right">
                <span
                  className={`contact-time${unreadCount > 0 ? ' unread' : (lastMsg ? '' : (user.online ? '' : ' away'))}`}
                  title={user.lastActive ? `Last active: ${new Date(user.lastActive).toLocaleString()}` : ''}
                >
                  {lastMsg ? formatMessageTime(lastMsg.sentAt) : (user.online ? 'now' : '')}
                </span>
                <span className={`status-badge ${user.online ? 'online' : 'offline'}`}>
                  <span className="status-dot" />
                </span>
              </div>
            </button>
            );
            })}
          </div>
        )}

        {filteredBotUsers.length > 0 && (
          <div className="ai-assist-section">
            <button
              className="ai-assist-header"
              onClick={() => setBotsExpanded((v) => !v)}
              aria-expanded={botsExpanded}
            >
              <span className="section-label">AI Assistance</span>
              <svg
                className={`chevron ${botsExpanded ? 'open' : ''}`}
                width="14"
                height="14"
                viewBox="0 0 14 14"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <polyline points="2 5 7 10 12 5" />
              </svg>
            </button>
            {botsExpanded && (
              <div className="ai-assist-body">
                {filteredBotUsers.map((user) => {
                  const botUnread = unreadCounts[user.username] || 0;
                  const botLastMsg = lastMessages[user.username];
                  return (
                  <button
                    key={user.id}
                    className={`contact-card bot-card ${selectedUser?.id === user.id ? 'active' : ''}`}
                    onClick={() => handleSelectUser(user)}
                  >
                    <div className="contact-avatar-wrap">
                      <div className="contact-avatar bot">{getInitials(user)}</div>
                      {botUnread > 0 && (
                        <span className="contact-avatar-badge">{botUnread > 99 ? '99+' : botUnread}</span>
                      )}
                    </div>
                    <div className="contact-meta">
                      <strong>{`${user.firstName} ${user.lastName}`.trim()}</strong>
                      {botLastMsg && (
                        <span className={`contact-preview${botUnread > 0 ? ' unread' : ''}`}>
                          {botLastMsg.senderUsername === session?.username ? 'You: ' : ''}
                          {botLastMsg.content}
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
                })}
              </div>
            )}
          </div>
        )}
      </aside>

      {mobileContactsOpen && <button className="mobile-overlay" type="button" onClick={() => setMobileContactsOpen(false)} aria-label="Close contacts" />}

      <main className="chat-panel">
        <header className="chat-header">
          <button className="mobile-menu" type="button" onClick={() => setMobileContactsOpen(true)}>
            {isGuestSession ? 'Assistants' : 'Contacts'}
          </button>
          <div>
            <div className="eyebrow">{isGuestSession ? 'Guest chat' : 'Conversation'}</div>
            <h3>{selectedUser ? `${selectedUser.firstName} ${selectedUser.lastName}` : (isGuestSession ? 'Select an assistant' : 'Select a contact')}</h3>
            <p>
              {selectedUser
                ? ['aid', 'bot'].includes((selectedUser.username || '').toLowerCase())
                  ? `@${selectedUser.username}`
                  : 'Active conversation'
                : isGuestSession
                  ? 'Choose @bot or @aid to start chatting without signing in.'
                  : 'Choose a user or bot to start chatting.'}
            </p>
          </div>
          {(() => {
            const assistantUsername = (selectedUser?.username || '').toLowerCase();
            if (assistantUsername === 'aid') {
              return (
                <div className="doc-hint">Aid · Book doctor appointments using live doctor availability. Try: “Appointment with Dr X tomorrow at 10 AM”. You can also type <strong>@aid</strong> in any chat to route privately here.</div>
              );
            }
            if (assistantUsername === 'bot') {
              return (
                <div className="doc-hint">Bot · Ask AI questions and get instant help. Try: “@bot explain this error in simple terms”. You can also type <strong>@bot</strong> in any chat to route privately here.</div>
              );
            }
            return null;
          })()}
        </header>

        <section className="message-list">
          {loadingConversation && <div className="loading-state">Loading conversation...</div>}
          {!loadingConversation && messages.length === 0 && (
            <div className="empty-state">No messages yet. Start the conversation.</div>
          )}
          {messages.map((message) => {
            const mine = message.senderUsername === session.username;
            return (
              <MessageBubble
                key={message.id}
                message={message}
                mine={mine}
                peerUser={selectedUser}
                currentUser={session}
                onOptionSelect={handleOptionSelect}
              />
            );
          })}
          <div ref={scrollRef} />
        </section>

        <form className="composer" onSubmit={handleSendMessage}>
          <textarea
            rows="2"
            placeholder={
              selectedUser?.username === 'aid'
                ? 'Ask @aid to book an appointment, for example: Dr X tomorrow at 10 AM.'
                : selectedUser?.username === 'bot'
                  ? 'Ask @bot anything…'
                  : 'Type a message — use @bot for AI help or @aid for doctor appointment booking.'
            }
            value={draft}
            onChange={(event) => {
              setDraft(event.target.value);
              setComposerError('');
              setError('');
            }}
            onKeyDown={(event) => {
              if ((event.metaKey || event.ctrlKey) && event.key === 'Enter') {
                handleSendMessage(event);
              }
            }}
            disabled={!selectedUser}
            maxLength={MAX_MESSAGE_LENGTH}
            aria-invalid={Boolean(composerError)}
            className={composerError ? 'input-invalid' : ''}
            autoCorrect="on"
            autoCapitalize="sentences"
            spellCheck
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
      </main>
    </div>
  );
}
