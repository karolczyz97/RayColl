import { saveUserData, loadUserData, UserData } from '../../services/firebase';
import { validateBackupData } from '../../utils/backupValidation';

export async function saveCloudData(userId: string, data: UserData): Promise<void> {
  try {
    await saveUserData(userId, data);
  } catch (err) {
    console.error('Failed to save cloud data:', err);
    throw err;
  }
}

export async function loadCloudData(userId: string): Promise<UserData | null> {
  try {
    const data = await loadUserData(userId);
    if (!data) return null;
    validateBackupData(data);
    return data;
  } catch (err) {
    console.error('Failed to load cloud data:', err);
    return null;
  }
}
