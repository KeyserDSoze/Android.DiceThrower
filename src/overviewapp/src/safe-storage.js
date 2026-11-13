/**
 * Web Storage is optional for this site: it can be missing, or throw on access
 * when cookies/site data are blocked (SecurityError), in sandboxed frames, or
 * when the quota is exhausted. Never let a preference read or write break
 * rendering or an event handler.
 */
function storage() {
  try {
    return globalThis.localStorage ?? null;
  } catch {
    return null;
  }
}

/** Stored string for key, or null when absent or storage is unavailable. */
export function readStorage(key) {
  try {
    return storage()?.getItem(key) ?? null;
  } catch {
    return null;
  }
}

/** Returns false when the value could not be persisted. */
export function writeStorage(key, value) {
  try {
    const target = storage();
    if (!target) return false;
    target.setItem(key, value);
    return true;
  } catch {
    return false;
  }
}
