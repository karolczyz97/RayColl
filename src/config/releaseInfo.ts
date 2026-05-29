export interface ReleaseInfo {
  readonly appVersion: string;
  readonly webBuild: string;
  readonly commitSha: string;
  readonly commitTitle: string;
  readonly notes: readonly string[];
  readonly publishedAt: string;
}

const DEV_RELEASE: ReleaseInfo = {
  appVersion: 'dev',
  webBuild: 'dev',
  commitSha: 'dev',
  commitTitle: 'Local development build',
  notes: [],
  publishedAt: new Date().toISOString(),
};

function parseReleaseInfo(): ReleaseInfo {
  // Static access required — Metro inline'uje tylko literalne process.env.EXPO_PUBLIC_*
  const raw = process.env.EXPO_PUBLIC_RELEASE_JSON;
  if (!raw) {
    return DEV_RELEASE;
  }
  try {
    const parsed: unknown = JSON.parse(raw);
    if (
      typeof parsed !== 'object' ||
      parsed === null ||
      typeof (parsed as Record<string, unknown>).appVersion !== 'string' ||
      typeof (parsed as Record<string, unknown>).webBuild !== 'string' ||
      typeof (parsed as Record<string, unknown>).commitSha !== 'string' ||
      typeof (parsed as Record<string, unknown>).commitTitle !== 'string' ||
      !Array.isArray((parsed as Record<string, unknown>).notes) ||
      typeof (parsed as Record<string, unknown>).publishedAt !== 'string'
    ) {
      return DEV_RELEASE;
    }
    const p = parsed as Record<string, unknown>;
    return {
      appVersion: p.appVersion as string,
      webBuild: p.webBuild as string,
      commitSha: p.commitSha as string,
      commitTitle: p.commitTitle as string,
      notes: (p.notes as unknown[]).filter((n): n is string => typeof n === 'string'),
      publishedAt: p.publishedAt as string,
    };
  } catch {
    return DEV_RELEASE;
  }
}

export const releaseInfo: ReleaseInfo = parseReleaseInfo();
