import { Platform } from 'react-native';
import { WebSttService } from './sttWeb';
import { ReactNativeVoiceSttService } from './sttNative';
import type { SttOptions, SttService } from './sttTypes';

export type { SttOptions, SttService };

let sttServiceInstance: SttService | null = null;

export function getSttService(): SttService {
  if (sttServiceInstance) return sttServiceInstance;
  sttServiceInstance =
    Platform.OS === 'web' ? new WebSttService() : new ReactNativeVoiceSttService();
  return sttServiceInstance;
}
