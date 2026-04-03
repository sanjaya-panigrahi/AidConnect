export function sortByTime(messages) {
  return [...messages].sort((a, b) => new Date(a.sentAt) - new Date(b.sentAt));
}

export function mergeMessages(existing, incoming) {
  const map = new Map(existing.map((m) => [m.id, m]));
  incoming.forEach((m) => { map.set(m.id, { ...map.get(m.id), ...m }); });
  return sortByTime(Array.from(map.values()));
}

export function getInitials(user) {
  if (!user) return 'NA';
  const u = (user.username || '').toLowerCase();
  if (u === 'bot') return 'BOT';
  if (u === 'aid') return 'AID';
  const first = (user.firstName || '').trim().charAt(0);
  const last  = (user.lastName  || '').trim().charAt(0);
  const fb    = (user.username  || '').trim().charAt(0);
  return (first + (last || fb)).toUpperCase();
}

export function formatPresenceTime(user) {
  if (user.online) return 'now';
  if (!user.lastActive) return 'away';
  const t = new Date(user.lastActive).getTime();
  if (Number.isNaN(t)) return 'away';
  const mins = Math.max(1, Math.floor((Date.now() - t) / 60000));
  if (mins < 60) return `Away · ${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `Away · ${hrs}h ago`;
  return `Away · ${Math.floor(hrs / 24)}d ago`;
}

export function formatMessageTime(sentAt) {
  if (!sentAt) return '';
  const date = new Date(sentAt);
  if (Number.isNaN(date.getTime())) return '';
  const mins = Math.floor((Date.now() - date.getTime()) / 60000);
  if (mins < 1) return 'now';
  if (mins < 60) return `${mins}m`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h`;
  const days = Math.floor(hrs / 24);
  if (days < 7) return `${days}d`;
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

