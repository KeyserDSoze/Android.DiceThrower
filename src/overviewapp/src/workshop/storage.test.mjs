import test from 'node:test';
import assert from 'node:assert/strict';
import { instantiateTemplate, listSaved, removeDocument, saveDocument } from './character.js';

/**
 * Minimal asynchronous IndexedDB stand-in: requests succeed before their
 * transaction settles, exactly like the browser API, so the tests can tell
 * "request succeeded" apart from "transaction committed".
 */
function fakeIndexedDB({ openError, openThrows, blocked, abortWith, failRequest } = {}) {
  const records = new Map();
  const state = { closed: 0, opened: 0 };
  const later = run => setTimeout(run, 0);
  const database = {
    objectStoreNames: { contains: () => true },
    close() { state.closed++; },
    transaction() {
      const tx = { error: null };
      const pending = [];
      const request = (operation) => {
        const req = {};
        pending.push(() => {
          if (failRequest) {
            req.error = failRequest;
            tx.onerror?.({ target: req });
            tx.error = failRequest;
            tx.onabort?.();
            return;
          }
          req.result = operation();
          req.onsuccess?.();
          if (abortWith) { tx.error = abortWith; tx.onabort?.(); }
          else tx.oncomplete?.();
        });
        later(() => pending.shift()?.());
        return req;
      };
      tx.objectStore = () => ({
        getAll: () => request(() => [...records.values()].map(value => structuredClone(value))),
        put: doc => request(() => { records.set(doc.character.id, structuredClone(doc)); return doc.character.id; }),
        delete: id => request(() => { records.delete(id); }),
      });
      return tx;
    },
  };
  return {
    state, records,
    open() {
      if (openThrows) throw openThrows;
      const req = {};
      later(() => {
        if (blocked) return req.onblocked?.();
        if (openError) { req.error = openError; return req.onerror?.(); }
        state.opened++;
        req.result = database;
        req.onsuccess?.();
      });
      return req;
    },
  };
}

async function withIndexedDB(fake, run) {
  const previous = Object.getOwnPropertyDescriptor(globalThis, 'indexedDB');
  Object.defineProperty(globalThis, 'indexedDB', { configurable: true, ...fake });
  try { await run(); } finally {
    if (previous) Object.defineProperty(globalThis, 'indexedDB', previous);
    else delete globalThis.indexedDB;
  }
}

test('saving resolves after the transaction commits and connections are closed', async () => {
  const fake = fakeIndexedDB();
  await withIndexedDB({ value: fake }, async () => {
    const doc = instantiateTemplate(6);
    await saveDocument(doc);
    assert.deepEqual((await listSaved()).map(saved => saved.character.id), [doc.character.id]);
    await removeDocument(doc.character.id);
    assert.deepEqual(await listSaved(), []);
    assert.equal(fake.state.closed, fake.state.opened);
  });
});

test('a write whose transaction aborts after request success is reported as failed', async () => {
  const quota = new DOMException('Quota exceeded', 'QuotaExceededError');
  await withIndexedDB({ value: fakeIndexedDB({ abortWith: quota }) }, async () => {
    await assert.rejects(saveDocument(instantiateTemplate(6)), error => error.code === 'storage-quota');
  });
  const constraint = new DOMException('Key already exists', 'ConstraintError');
  await withIndexedDB({ value: fakeIndexedDB({ failRequest: constraint }) }, async () => {
    await assert.rejects(saveDocument(instantiateTemplate(6)),
      error => error.code === 'storage-failed' && /Key already exists/.test(error.message));
  });
});

test('unavailable, denied or blocked IndexedDB gives friendly coded errors', async () => {
  await withIndexedDB({ value: undefined }, async () => {
    await assert.rejects(listSaved(), error => error.code === 'storage-unavailable');
  });
  await withIndexedDB({ get() { throw new DOMException('denied', 'SecurityError'); } }, async () => {
    await assert.rejects(listSaved(), error => error.code === 'storage-unavailable');
  });
  await withIndexedDB({ value: fakeIndexedDB({ openThrows: new DOMException('insecure', 'SecurityError') }) }, async () => {
    await assert.rejects(listSaved(), error => error.code === 'storage-unavailable');
  });
  await withIndexedDB({ value: fakeIndexedDB({ openError: new DOMException('denied', 'UnknownError') }) }, async () => {
    await assert.rejects(listSaved(), error => error.code === 'storage-unavailable');
  });
  await withIndexedDB({ value: fakeIndexedDB({ blocked: true }) }, async () => {
    await assert.rejects(listSaved(), error => error.code === 'storage-blocked');
  });
});

test('stricter formula validation does not delete or overwrite legacy browser records', async () => {
  const fake = fakeIndexedDB();
  const legacy = instantiateTemplate(6);
  legacy.rolls[0].expression = '1d7'; // Accepted by the previous web-only regex.
  fake.records.set(legacy.character.id, structuredClone(legacy));
  await withIndexedDB({ value: fake }, async () => {
    assert.deepEqual(await listSaved(), [legacy]);
    assert.throws(() => saveDocument(legacy), /unsupported die/);
    assert.deepEqual(await listSaved(), [legacy], 'invalid save must leave the original record intact');
    const corrected = structuredClone(legacy);
    corrected.rolls[0].expression = '1d6';
    await saveDocument(corrected);
    assert.deepEqual(await listSaved(), [corrected]);
    assert.equal(legacy.rolls[0].expression, '1d7');
  });
});
