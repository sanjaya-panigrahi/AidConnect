import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';

function installLocalStorageShim() {
  const hasUsableLocalStorage =
    typeof window !== 'undefined' &&
    window.localStorage &&
    typeof window.localStorage.getItem === 'function' &&
    typeof window.localStorage.setItem === 'function' &&
    typeof window.localStorage.removeItem === 'function';

  if (hasUsableLocalStorage) return;

  const store = new Map();
  const shim = {
    getItem: (key) => (store.has(key) ? store.get(key) : null),
    setItem: (key, value) => {
      store.set(String(key), String(value));
    },
    removeItem: (key) => {
      store.delete(String(key));
    },
    clear: () => {
      store.clear();
    }
  };

  Object.defineProperty(window, 'localStorage', {
    value: shim,
    configurable: true
  });
}

installLocalStorageShim();

afterEach(() => {
  cleanup();
});

