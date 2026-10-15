import {readFile} from 'node:fs/promises';
import {after, before, beforeEach, test} from 'node:test';
import {initializeTestEnvironment, assertSucceeds, assertFails} from '@firebase/rules-unit-testing';
import {doc, getDoc, setDoc, updateDoc, deleteDoc, writeBatch, serverTimestamp, increment} from 'firebase/firestore';
import assert from 'node:assert/strict';

let env;
const now = Date.now();
const deck = 'users/alice/decks/d';
const card = `${deck}/cards/c`;
const mode = 'users/alice/studyModes/m';
const day = 'users/alice/activity/2026-10-15';
const google = () => env.authenticatedContext('alice', {firebase: {sign_in_provider: 'google.com'}}).firestore();
const guest = () => env.authenticatedContext('alice', {firebase: {sign_in_provider: 'anonymous'}}).firestore();
const put = (path, data) => env.withSecurityRulesDisabled(ctx => setDoc(doc(ctx.firestore(), path), data));
before(async () => {
  env = await initializeTestEnvironment({projectId: 'demo-raycoll', firestore: {
    host: '127.0.0.1', port: 8089, rules: await readFile('../../firestore.rules', 'utf8'),
  }});
});
beforeEach(async () => env.clearFirestore());
after(async () => { await env?.cleanup(); });

for (const path of ['users/alice', deck, card, mode, day]) {
  test(`owner alone can read ${path}`, async () => {
    await put(path, {updatedAt: now});
    await assertSucceeds(getDoc(doc(google(), path)));
    await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), path)));
    await assertFails(getDoc(doc(env.authenticatedContext('bob').firestore(), path)));
  });
}

test('root write blocks an entire old application transaction', async () => {
  const db = google();
  const batch = writeBatch(db);
  batch.set(doc(db, 'users/alice'), {syncToken: 'old'});
  batch.set(doc(db, deck), {name: 'old', updatedAt: now});
  await assertFails(batch.commit());
  assert.equal((await getDoc(doc(db, deck))).exists(), false);
});

for (const path of [deck, mode]) {
  test(`newer date wins and future date is rejected: ${path}`, async () => {
    const ref = doc(google(), path);
    await assertSucceeds(setDoc(ref, {name: 'new', updatedAt: now}));
    await assertFails(updateDoc(ref, {name: 'stale', updatedAt: now - 1}));
    await assertFails(updateDoc(ref, {name: 'future', updatedAt: now + 3_600_000}));
    await assertSucceeds(updateDoc(ref, {name: 'newest', updatedAt: now + 1}));
    assert.equal((await getDoc(ref)).data().name, 'newest');
  });
  test(`deleted data rejects an ordinary edit and accepts full restore: ${path}`, async () => {
    await put(path, {name: 'deleted', deletedAt: now, updatedAt: now});
    const ref = doc(google(), path);
    await assertFails(updateDoc(ref, {name: 'edit', updatedAt: now + 1}));
    await assertSucceeds(setDoc(ref, {name: 'restored', updatedAt: now + 1}));
    assert.equal((await getDoc(ref)).data().deletedAt, undefined);
  });
}

test('card content and SRS have independent dates and can both survive', async () => {
  await put(card, {pages: ['q', 'a'], srsState: {state: 0}, contentUpdatedAt: now, srsUpdatedAt: now});
  const ref = doc(google(), card);
  await assertSucceeds(updateDoc(ref, {pages: ['new', 'a'], contentUpdatedAt: now + 2}));
  await assertSucceeds(updateDoc(ref, {srsState: {state: 2}, srsUpdatedAt: now + 1}));
  await assertFails(updateDoc(ref, {pages: ['old', 'a'], contentUpdatedAt: now - 1}));
  await assertFails(updateDoc(ref, {srsState: {state: 1}, srsUpdatedAt: now - 1}));
  assert.equal((await getDoc(ref)).data().pages[0], 'new');
  assert.equal((await getDoc(ref)).data().srsState.state, 2);
});

test('card deletion must be newer than both content and review', async () => {
  await put(card, {contentUpdatedAt: now, srsUpdatedAt: now + 5});
  const ref = doc(google(), card);
  await assertFails(updateDoc(ref, {deletedAt: now + 4}));
  await assertSucceeds(updateDoc(ref, {deletedAt: now + 6}));
  await assertFails(updateDoc(ref, {pages: ['edit'], contentUpdatedAt: now + 7}));
  await assertSucceeds(setDoc(ref, {pages: ['restored'], contentUpdatedAt: now + 8, srsUpdatedAt: now + 8}));
});

test('a future content or SRS date is rejected on create and update', async () => {
  const ref = doc(google(), card);
  await assertFails(setDoc(ref, {contentUpdatedAt: now + 3_600_000, srsUpdatedAt: now}));
  await assertFails(setDoc(ref, {contentUpdatedAt: now, srsUpdatedAt: now + 3_600_000}));
  await assertSucceeds(setDoc(ref, {contentUpdatedAt: now, srsUpdatedAt: now}));
  await assertFails(updateDoc(ref, {srsUpdatedAt: now + 3_600_000}));
});

for (const path of [deck, card, mode]) {
  test(`Google deletes only tombstones older than 30 days; guest may clean all: ${path}`, async () => {
    await put(path, {updatedAt: now, contentUpdatedAt: now, srsUpdatedAt: now});
    await assertFails(deleteDoc(doc(google(), path)));
    await put(path, {deletedAt: now - 29 * 86_400_000});
    await assertFails(deleteDoc(doc(google(), path)));
    await put(path, {deletedAt: now - 31 * 86_400_000});
    await assertSucceeds(deleteDoc(doc(google(), path)));
    await put(path, {updatedAt: now});
    await assertSucceeds(deleteDoc(doc(guest(), path)));
  });
}

test('cleanup may delete cards of an old deleted parent before the parent', async () => {
  await put(deck, {deletedAt: now - 31 * 86_400_000});
  await put(card, {contentUpdatedAt: now, srsUpdatedAt: now});
  const db = google();
  const batch = writeBatch(db);
  batch.delete(doc(db, card));
  batch.delete(doc(db, deck));
  await assertSucceeds(batch.commit());
});

test('activity requires server time, sums increments and is deletable only by guest', async () => {
  const ref = doc(google(), day);
  await assertFails(setDoc(ref, {count: 1, updatedAt: now}));
  await assertSucceeds(setDoc(ref, {count: increment(2), updatedAt: serverTimestamp()}, {merge: true}));
  await assertSucceeds(setDoc(ref, {count: increment(3), updatedAt: serverTimestamp()}, {merge: true}));
  assert.equal((await getDoc(ref)).data().count, 5);
  await assertFails(deleteDoc(ref));
  await assertSucceeds(deleteDoc(doc(guest(), day)));
});

test('foreign accounts cannot create documents in any collection', async () => {
  const db = env.authenticatedContext('bob').firestore();
  for (const path of [deck, card, mode, day]) await assertFails(setDoc(doc(db, path), {updatedAt: serverTimestamp()}));
});
