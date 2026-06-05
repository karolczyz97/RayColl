export interface SttOptions {
  language: string;
  timeoutMs?: number;
  onPartialResult?: (text: string) => void;
  onListeningStateChange?: (listening: boolean) => void;
}

export const DEFAULT_STT_TIMEOUT_MS = 15000;
export const DEFAULT_STT_INACTIVITY_MS = 1500;

export interface SttSession {
  resolved: () => boolean;
  setResolved: () => void;
  cancelTimers: () => void;
  resetInactivity: () => void;
}

export interface SttService {
  startListening(options: SttOptions): Promise<string>;
  stopListening(): Promise<void>;
  isSupported(): boolean;
}

export function normalizeSttResult(text: string): string {
  return text.replace(/\s+/g, ' ').trim();
}

export function combineRecognizedText(finalText: string, interimText: string): string {
  const normalizedFinal = normalizeSttResult(finalText);
  const normalizedInterim = normalizeSttResult(interimText);

  if (!normalizedFinal) return normalizedInterim;
  if (!normalizedInterim) return normalizedFinal;

  const finalLower = normalizedFinal.toLocaleLowerCase();
  const interimLower = normalizedInterim.toLocaleLowerCase();

  if (interimLower === finalLower || interimLower.startsWith(finalLower)) {
    return normalizedInterim;
  }
  if (finalLower.endsWith(interimLower)) {
    return normalizedFinal;
  }

  return `${normalizedFinal} ${normalizedInterim}`;
}
