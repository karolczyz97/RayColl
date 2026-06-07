import { ExpoSttService } from './sttExpo';
import type { SttOptions, SttService } from './sttTypes';

export type { SttOptions, SttService };

let sttServiceInstance: SttService | null = null;

export function getSttService(): SttService {
  if (sttServiceInstance) return sttServiceInstance;
  sttServiceInstance = new ExpoSttService();
  return sttServiceInstance;
}
