import type { StoreData } from '@/types/models';

export function shouldTriggerMigration(
  hasUserLocalCache: boolean,
  guestHasData: boolean,
): boolean {
  return !hasUserLocalCache && guestHasData;
}

export function getGuestHasData(guestData: StoreData | null): boolean {
  return (guestData?.groups?.length ?? 0) > 0;
}
