import { saveUserData, loadUserData, UserData } from '../../services/firebase';

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
    return await loadUserData(userId);
  } catch (err) {
    console.error('Failed to load cloud data:', err);
    return null;
  }
}
