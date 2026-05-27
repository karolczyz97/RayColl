import { validateBackupData } from '../../utils/backupValidation';
import type { UserData } from './firestoreSchema';
import { saveUserDataV2 } from './firestoreV2Persistence';

export async function migrateLegacyUserDataToV2(uid: string, data: UserData): Promise<UserData> {
  validateBackupData(data);
  await saveUserDataV2(uid, data, { legacyBackup: data });
  return data;
}
