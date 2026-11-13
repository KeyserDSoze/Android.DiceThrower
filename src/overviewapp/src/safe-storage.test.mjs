import test from 'node:test';
import assert from 'node:assert/strict';
import { readStorage, writeStorage } from './safe-storage.js';
import { initialLocale } from './i18n.js';

function withLocalStorage(descriptor, run) {
  const previous = Object.getOwnPropertyDescriptor(globalThis, 'localStorage');
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, ...descriptor });
  try { run(); } finally {
    if (previous) Object.defineProperty(globalThis, 'localStorage', previous);
    else delete globalThis.localStorage;
  }
}

function memoryStorage() {
  const values = new Map();
  return {
    getItem: key => values.has(key) ? values.get(key) : null,
    setItem: (key, value) => { values.set(key, String(value)); },
  };
}

test('blocked localStorage (SecurityError on access) never throws', () => {
  withLocalStorage({ get() { throw new DOMException('Access is denied', 'SecurityError'); } }, () => {
    assert.equal(readStorage('dicethrower-site-language'), null);
    assert.equal(writeStorage('dicethrower-site-language', 'it'), false);
    assert.doesNotThrow(() => initialLocale());
    assert.equal(typeof initialLocale(), 'string');
  });
});

test('missing localStorage and failing writes are reported, not thrown', () => {
  withLocalStorage({ value: undefined }, () => {
    assert.equal(readStorage('key'), null);
    assert.equal(writeStorage('key', 'value'), false);
  });
  const full = { getItem: () => { throw new Error('boom'); },
    setItem: () => { throw new DOMException('Quota exceeded', 'QuotaExceededError'); } };
  withLocalStorage({ value: full }, () => {
    assert.equal(readStorage('key'), null);
    assert.equal(writeStorage('key', 'value'), false);
  });
});

test('working localStorage keeps the saved site language', () => {
  withLocalStorage({ value: memoryStorage() }, () => {
    assert.equal(writeStorage('dicethrower-site-language', 'it'), true);
    assert.equal(readStorage('dicethrower-site-language'), 'it');
    assert.equal(initialLocale(), 'it');
    writeStorage('dicethrower-site-language', 'not-a-locale');
    assert.equal(typeof initialLocale(), 'string');
    assert.notEqual(initialLocale(), 'not-a-locale');
  });
});
