import {readFile} from 'node:fs/promises';
import {after, before, test} from 'node:test';
import {initializeTestEnvironment, assertSucceeds, assertFails} from '@firebase/rules-unit-testing';
import {doc, getDoc, setDoc, runTransaction, Timestamp} from 'firebase/firestore';
import assert from 'node:assert/strict';

let env;
before(async () => {
  env = await initializeTestEnvironment({projectId: 'demo-raycoll', firestore: {
    host: '127.0.0.1', port: 8089, rules: await readFile('../../firestore.rules', 'utf8'),
  }});
});
after(async () => { await env?.cleanup(); });

for (const path of ['users/alice', 'users/alice/decks/deck', 'users/alice/decks/deck/cards/card']) {
  for (const identity of ['anonymous', 'alice', 'bob']) {
    test(`${identity} read/write ${path}`, async () => {
      const db = identity === 'anonymous' ? env.unauthenticatedContext().firestore()
        : env.authenticatedContext(identity).firestore();
      const check = identity === 'alice' ? assertSucceeds : assertFails;
      await check(setDoc(doc(db, path), {example: true}));
      await check(getDoc(doc(db, path)));
    });
  }
}

test('a stale transaction cannot roll back a concurrent replacement', async () => {
  const db = env.authenticatedContext('alice').firestore();
  const root = doc(db, 'users/alice');
  const old = {updatedAt: Timestamp.now(), syncToken: 'old', replacementAt: 0, replacementId: ''};
  await setDoc(root, old);
  await setDoc(root, {...old, syncToken: 'reset', replacementAt: 20, replacementId: 'new-generation'});
  await assert.rejects(runTransaction(db, async tx => {
    const current = (await tx.get(root)).data();
    if (current.syncToken !== old.syncToken || current.replacementAt !== old.replacementAt) {
      throw new Error('reload required');
    }
    tx.set(root, old);
  }), /reload required/);
  assert.equal((await getDoc(root)).data().replacementId, 'new-generation');
});
