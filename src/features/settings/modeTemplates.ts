import type { ModeStep } from '@/types/models';
import { defaultCompoundParams } from './compoundSteps';

export type ModeTemplateId = 'blank' | 'classic' | 'audio';

export const MODE_TEMPLATE_IDS: ModeTemplateId[] = ['blank', 'classic', 'audio'];

export function createModeFromTemplate(id: ModeTemplateId): ModeStep[] {
  switch (id) {
    case 'blank':
      return [];
    case 'classic':
      return [
        { type: 'compound', version: 1, params: defaultCompoundParams('present_front') },
        { type: 'compound', version: 1, params: defaultCompoundParams('flip_reveal') },
      ];
    case 'audio':
      return [
        { type: 'compound', version: 1, params: defaultCompoundParams('present_front') },
        { type: 'compound', version: 1, params: defaultCompoundParams('listen_grade') },
      ];
  }
}
