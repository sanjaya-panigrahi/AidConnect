/** Reusable SVG icons for the left navigation rail. */
export default function RailIcon({ type }) {
  if (type === 'chat') {
    return (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M4 5h16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H9l-5 4v-4H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z" />
      </svg>
    );
  }
  // settings / gear icon (used for logout)
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 8.2a3.8 3.8 0 1 0 0 7.6 3.8 3.8 0 0 0 0-7.6zm9 3.8-2 .6a7.7 7.7 0 0 1-.5 1.2l1.2 1.7-1.8 1.8-1.7-1.2a7.7 7.7 0 0 1-1.2.5l-.6 2h-2.6l-.6-2a7.7 7.7 0 0 1-1.2-.5l-1.7 1.2-1.8-1.8 1.2-1.7a7.7 7.7 0 0 1-.5-1.2L3 12l.6-2.6a7.7 7.7 0 0 1 .5-1.2L2.9 6.5 4.7 4.7l1.7 1.2a7.7 7.7 0 0 1 1.2-.5l.6-2h2.6l.6 2a7.7 7.7 0 0 1 1.2.5l1.7-1.2 1.8 1.8-1.2 1.7a7.7 7.7 0 0 1 .5 1.2L21 12z" />
    </svg>
  );
}

