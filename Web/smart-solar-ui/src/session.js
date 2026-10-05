const KEY = "smartsolar.session";
const FLASH = "smartsolar.flash";

export function loadSession() {
  try {
    const raw = sessionStorage.getItem(KEY);
    if (!raw) return null;
    const session = JSON.parse(raw);
    if (session?.preview || session?.token === "preview") {
      sessionStorage.removeItem(KEY);
      return null;
    }
    return session;
  } catch {
    return null;
  }
}

export function saveSession(session) {
  sessionStorage.setItem(KEY, JSON.stringify(session));
}

export function clearSession() {
  sessionStorage.removeItem(KEY);
}

export function setFlash(message, tone = "ok") {
  sessionStorage.setItem(FLASH, JSON.stringify({ message, tone }));
}

export function consumeFlash() {
  const raw = sessionStorage.getItem(FLASH);
  sessionStorage.removeItem(FLASH);
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}
