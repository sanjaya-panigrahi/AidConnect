# Chat Assist UI

## Overview

The **Chat Assist UI** is a React 18 single-page application that provides the browser-based
chat interface for Chat Assist. It supports user authentication, real-time messaging via
WebSocket (STOMP/SockJS), assistant interactions (bot and aid), presence tracking, and
daily activity summaries.

## Port

| Environment | Port |
|---|---|
| Local dev (Vite) | `3000` |
| Docker (Nginx) | `80` (proxied through gateway at `8080`) |

## Key Features

- Login / Registration forms with JWT token persistence
- Guest mode (assistant-only, no account required)
- Server-side session restoration on page load
- Contact list (human users + assistants) with online status and last-seen time
- Real-time message delivery via STOMP WebSocket
- Message status: SENT → DELIVERED → SEEN (tick marks)
- `@bot` and `@aid` mention routing with routing hint in composer
- Unread message badge per contact
- Last message preview per contact
- Daily activity panel (login count + chat peer count)
- Mobile-responsive layout with overlay contact panel

## Folder Structure

```
chat-assist-ui/src/
├── App.jsx                 # Root component; all state, effects, handlers
├── main.jsx                # React DOM entry point
├── styles.css              # Global stylesheet
├── api/
│   └── client.js           # Fetch wrapper (credentials + JWT + 401 event)
├── components/
│   ├── auth/
│   │   └── AuthPage.jsx    # Login / Register / Guest access
│   ├── chat/
│   │   ├── AppRail.jsx     # Left icon rail (user info, logout, activity toggle)
│   │   ├── AppSidebar.jsx  # Contact list + search + activity overlay
│   │   └── ChatPanel.jsx   # Message list + composer
│   └── MessageBubble.jsx   # Single message bubble with status indicator
└── utils/
    ├── constants.js        # Service URLs, config constants
    ├── formatting.js       # Date/time, initials, message sort/merge helpers
    ├── session.js          # Load/create guest session from localStorage
    └── validation.js       # Message content validation
```

## Technology Stack

| Layer | Technology |
|---|---|
| Framework | React 18 |
| Language | JavaScript (ES2022 modules) |
| Bundler | Vite 6.x |
| WebSocket | @stomp/stompjs 7.x + sockjs-client 1.x |
| HTTP | Native `fetch` (custom `request()` wrapper in `api/client.js`) |
| Testing | Vitest + @testing-library/react + jsdom |
| Containerisation | Docker + Nginx |

## Local Development

```bash
cd chat-assist-ui
npm install
npm run dev        # starts on http://localhost:3000
```

## Build

```bash
npm run build      # outputs to dist/
```

## Tests

```bash
npm test
```
