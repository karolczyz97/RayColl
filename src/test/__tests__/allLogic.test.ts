/* eslint-disable @typescript-eslint/no-require-imports */
import { describe, it } from '@jest/globals';

describe('Logic tests', () => {
  it('srsEngine', async () => {
    const { runTests } = require('../../srs/__tests__/srsEngine.test');
    await runTests();
  });

  it('importParser', async () => {
    const { runTests } = require('../../import/__tests__/importParser.test');
    await runTests();
  });

  it('importDeck', async () => {
    const { runTests } = require('../../import/__tests__/importDeck.test');
    await runTests();
  });

  it('importDraftHelpers', async () => {
    const { runTests } = require('../../features/import/__tests__/importDraftHelpers.test');
    await runTests();
  });

  it('sessionState', async () => {
    const { runTests } = require('../../features/study/session/__tests__/sessionState.test');
    await runTests();
  });

  it('browseFilter', async () => {
    const { runTests } = require('../../features/browse/__tests__/browseFilter.test');
    await runTests();
  });

  it('groupActions', async () => {
    const { runTests } = require('../../store/actions/__tests__/groupActions.test');
    await runTests();
  });

  it('cardActions', async () => {
    const { runTests } = require('../../store/actions/__tests__/cardActions.test');
    await runTests();
  });

  it('merge', async () => {
    const { runTests } = require('../../store/selectors/__tests__/merge.test');
    await runTests();
  });

  it('migrationLogic', async () => {
    const { runTests } = require('../../store/selectors/__tests__/migrationLogic.test');
    await runTests();
  });

  it('tombstones', async () => {
    const { runTests } = require('../../store/selectors/__tests__/tombstones.test');
    await runTests();
  });

  it('persistenceQueue', async () => {
    const { runTests } = require('../../store/persistence/__tests__/persistenceQueue.test');
    await runTests();
  });

  it('webLifecycle', async () => {
    const { runTests } = require('../../store/persistence/__tests__/webLifecycle.test');
    await runTests();
  });

  it('firestoreSchema', async () => {
    const { runTests } = require('../../store/persistence/__tests__/firestoreSchema.test');
    await runTests();
  });

  it('tokens', async () => {
    const { runTests } = require('../../theme/__tests__/tokens.test');
    await runTests();
  });

  it('gridLayout', async () => {
    const { runTests } = require('../../utils/__tests__/gridLayout.test');
    await runTests();
  });

  it('windowSizeClass', async () => {
    const { runTests } = require('../../utils/__tests__/windowSizeClass.test');
    await runTests();
  });

  it('backupValidation', async () => {
    const { runTests } = require('../../utils/__tests__/backupValidation.test');
    await runTests();
  });
});
