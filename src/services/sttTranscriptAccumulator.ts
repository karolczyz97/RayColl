import { combineRecognizedText, normalizeSttResult } from './sttTypes';

export interface SttTranscriptAccumulator {
  addFinal: (text?: string | null) => string;
  setInterim: (text?: string | null) => string;
  getText: () => string;
  getFinalText: () => string;
}

export function createSttTranscriptAccumulator(): SttTranscriptAccumulator {
  let finalText = '';
  let interimText = '';

  return {
    addFinal: (text) => {
      const normalized = normalizeSttResult(text ?? '');
      if (!normalized) return combineRecognizedText(finalText, interimText);

      finalText = combineRecognizedText(finalText, normalized);
      interimText = '';
      return finalText;
    },
    setInterim: (text) => {
      interimText = normalizeSttResult(text ?? '');
      return combineRecognizedText(finalText, interimText);
    },
    getText: () => combineRecognizedText(finalText, interimText),
    getFinalText: () => finalText,
  };
}
