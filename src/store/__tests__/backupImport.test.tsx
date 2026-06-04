import { IMPORT_STATE_JSON_ERROR, parseBackupJson } from '../backupImport';

describe('parseBackupJson', () => {
  it('parses valid JSON', () => {
    expect(parseBackupJson('{"groups":[]}')).toEqual({ groups: [] });
  });

  it('throws a stable translation key for invalid JSON', () => {
    expect(() => parseBackupJson('{bad json')).toThrow(IMPORT_STATE_JSON_ERROR);
  });
});
