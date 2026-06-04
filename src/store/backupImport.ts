export const IMPORT_STATE_JSON_ERROR = 'app_settings.import_error';

export function parseBackupJson(json: string): unknown {
  try {
    return JSON.parse(json);
  } catch {
    throw new Error(IMPORT_STATE_JSON_ERROR);
  }
}
