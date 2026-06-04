import { useEffect, useState } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { STORAGE_KEYS } from '@/constants/storageKeys';
import { releaseInfo } from '@/config/releaseInfo';

interface UpdateNotificationState {
  showSnackbar: boolean;
  showDialog: boolean;
  dismiss: () => void;
  openChangelog: () => void;
  closeChangelog: () => void;
}

export function useUpdateNotification(): UpdateNotificationState {
  const [showSnackbar, setShowSnackbar] = useState(false);
  const [showDialog, setShowDialog] = useState(false);

  useEffect(() => {
    // Skip in dev mode
    if (releaseInfo.webBuild === 'dev') {
      return;
    }

    async function checkBuild() {
      try {
        const lastSeen = await AsyncStorage.getItem(STORAGE_KEYS.LAST_SEEN_BUILD);
        const current = releaseInfo.webBuild;

        // Save current build immediately — prevents re-showing on subsequent starts
        await AsyncStorage.setItem(STORAGE_KEYS.LAST_SEEN_BUILD, current);

        if (lastSeen === null) {
          // First run / reinstall / cleared data — no notification
          return;
        }

        if (lastSeen !== current) {
          setShowSnackbar(true);
        }
      } catch {
        // AsyncStorage error — treat as first run, no notification
      }
    }

    void checkBuild();
  }, []);

  const dismiss = () => setShowSnackbar(false);

  const openChangelog = () => {
    setShowSnackbar(false);
    setShowDialog(true);
  };

  const closeChangelog = () => setShowDialog(false);

  return { showSnackbar, showDialog, dismiss, openChangelog, closeChangelog };
}
