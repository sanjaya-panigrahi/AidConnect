import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const { requestMock } = vi.hoisted(() => ({
  requestMock: vi.fn()
}));

vi.mock('./api/client', () => ({
  request: (...args) => requestMock(...args)
}));

vi.mock('@stomp/stompjs', () => ({
  Client: class {
    activate() {}
    deactivate() {}
    subscribe() {}
  }
}));

vi.mock('sockjs-client', () => ({
  default: function SockJS() {
    return {};
  }
}));

vi.mock('./components/auth/AuthPage', () => ({
  default: function MockAuthPage({ onLoginSuccess, onGuestAccess }) {
    return (
      <>
        <button
          type="button"
          onClick={() =>
            onLoginSuccess({
              userId: 1,
              username: 'ajit',
              firstName: 'Ajit',
              lastName: 'Kumar',
              email: 'ajit@example.com',
              token: null
            })
          }
        >
          Mock Login
        </button>
        <button type="button" onClick={onGuestAccess}>
          Mock Guest Access
        </button>
      </>
    );
  }
}));

vi.mock('./components/chat/AppRail', () => ({
  default: function MockAppRail({ onLogout }) {
    return (
      <button type="button" data-testid="app-rail" onClick={onLogout}>
        Mock Logout
      </button>
    );
  }
}));

vi.mock('./components/chat/AppSidebar', () => ({
  default: function MockAppSidebar() {
    return <div data-testid="app-sidebar" />;
  }
}));

vi.mock('./components/chat/ChatPanel', () => ({
  default: function MockChatPanel() {
    return <div data-testid="chat-panel" />;
  }
}));

import App from './App';

describe('App server-session auth flow', () => {
  beforeEach(() => {
    requestMock.mockReset();
    requestMock.mockImplementation(async (url) => {
      if (url.includes('/api/users/session')) {
        const err = new Error('Unauthorized');
        err.status = 401;
        throw err;
      }
      if (url.includes('/assistants')) return [];
      if (url.includes('/api/users?excludeUsername=')) return [];
      if (url.includes('/api/users/activity/today')) return [];
      if (url.includes('/api/chats/activity/today')) return [];
      if (url.includes('/api/users/ajit/activity/today')) return { username: 'ajit', loginCount: 0, logoutCount: 0 };
      if (url.includes('/api/chats/ajit/activity/today')) return { username: 'ajit', chatPeerCount: 0 };
      if (url.includes('/online')) return null;
      if (url.includes('/logout')) return null;
      return [];
    });
  });

  afterEach(() => {
    requestMock.mockReset();
  });

  it('restores an authenticated session from server bootstrap', async () => {
    requestMock.mockImplementation(async (url) => {
      if (url.includes('/api/users/session')) {
        return {
          userId: 1,
          username: 'ajit',
          firstName: 'Ajit',
          lastName: 'Kumar',
          email: 'ajit@example.com',
          token: null,
          message: 'Session active'
        };
      }
      if (url.includes('/assistants')) return [];
      if (url.includes('/api/users?excludeUsername=')) return [];
      if (url.includes('/api/users/activity/today')) return [];
      if (url.includes('/api/chats/activity/today')) return [];
      if (url.includes('/api/users/ajit/activity/today')) return { username: 'ajit', loginCount: 0, logoutCount: 0 };
      if (url.includes('/api/chats/ajit/activity/today')) return { username: 'ajit', chatPeerCount: 0 };
      if (url.includes('/online')) return null;
      return [];
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Mock Logout' })).toBeInTheDocument();
    });

    const onlineCalls = requestMock.mock.calls.filter(([url]) => String(url).includes('/api/users/ajit/online'));
    expect(onlineCalls.length).toBe(1);
  });

  it('marks user online once after explicit login', async () => {
    render(<App />);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Mock Login' })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: 'Mock Login' }));

    await waitFor(() => {
      const onlineCalls = requestMock.mock.calls.filter(([url]) => String(url).includes('/api/users/ajit/online'));
      expect(onlineCalls.length).toBe(1);
    });
  });

  it('waits for logout API completion before returning to auth screen', async () => {
    let resolveLogout;
    requestMock.mockImplementation(async (url) => {
      if (url.includes('/api/users/session')) {
        const err = new Error('Unauthorized');
        err.status = 401;
        throw err;
      }
      if (url.includes('/assistants')) return [];
      if (url.includes('/api/users?excludeUsername=')) return [];
      if (url.includes('/api/users/activity/today')) return [];
      if (url.includes('/api/chats/activity/today')) return [];
      if (url.includes('/api/users/ajit/activity/today')) return { username: 'ajit', loginCount: 0, logoutCount: 0 };
      if (url.includes('/api/chats/ajit/activity/today')) return { username: 'ajit', chatPeerCount: 0 };
      if (url.includes('/online')) return null;
      if (url.includes('/logout')) {
        return await new Promise((resolve) => {
          resolveLogout = resolve;
        });
      }
      return [];
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Mock Login' })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: 'Mock Login' }));

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Mock Logout' })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: 'Mock Logout' }));

    await waitFor(() => {
      const logoutCalls = requestMock.mock.calls.filter(([url]) => String(url).includes('/api/users/logout'));
      expect(logoutCalls.length).toBe(1);
    });

    expect(screen.queryByRole('button', { name: 'Mock Login' })).not.toBeInTheDocument();

    resolveLogout(null);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Mock Login' })).toBeInTheDocument();
    });
  });

  it('guest logout returns to auth screen without calling server logout API', async () => {
    render(<App />);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Mock Guest Access' })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: 'Mock Guest Access' }));
    fireEvent.click(screen.getByRole('button', { name: 'Mock Logout' }));

    const logoutCalls = requestMock.mock.calls.filter(([url]) =>
      String(url).includes('/api/users/logout')
    );
    expect(logoutCalls.length).toBe(0);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Mock Login' })).toBeInTheDocument();
    });
  });
});

