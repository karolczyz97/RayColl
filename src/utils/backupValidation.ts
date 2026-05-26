export function validateBackupData(
  data: any,
): data is { groups: any[]; studyModes: any[]; activityHeatmap: Record<string, number> } {
  if (!data || typeof data !== 'object') {
    throw new Error('Backup data is not a valid JSON object.');
  }
  if (!Array.isArray(data.groups)) {
    throw new Error('Backup data must contain a "groups" array.');
  }
  if (!Array.isArray(data.studyModes)) {
    throw new Error('Backup data must contain a "studyModes" array.');
  }
  if (!data.activityHeatmap || typeof data.activityHeatmap !== 'object') {
    throw new Error('Backup data must contain an "activityHeatmap" object.');
  }

  for (const group of data.groups) {
    if (!group || typeof group !== 'object') {
      throw new Error('Each group must be a valid object.');
    }
    if (typeof group.id !== 'string') {
      throw new Error('Each group must have a string id.');
    }
    if (typeof group.name !== 'string') {
      throw new Error(`Group with ID ${group.id} must have a name.`);
    }
    if (!Array.isArray(group.cards)) {
      throw new Error(`Group with ID ${group.id} must contain a "cards" array.`);
    }
    for (const card of group.cards) {
      if (!card || typeof card !== 'object') {
        throw new Error(`Card in group ${group.name} is not a valid object.`);
      }
      if (typeof card.id !== 'string') {
        throw new Error(`Card in group ${group.name} is missing a string ID.`);
      }
      if (!Array.isArray(card.pages)) {
        throw new Error(`Card ${card.id} in group ${group.name} must have a "pages" array.`);
      }
      if (!card.srsState || typeof card.srsState !== 'object') {
        throw new Error(
          `Card ${card.id} in group ${group.name} is missing a valid "srsState" object.`,
        );
      }
    }
  }

  for (const mode of data.studyModes) {
    if (!mode || typeof mode !== 'object') {
      throw new Error('Each study mode must be a valid object.');
    }
    if (typeof mode.id !== 'string') {
      throw new Error('Each study mode must have a string id.');
    }
    if (typeof mode.name !== 'string') {
      throw new Error(`Study mode with ID ${mode.id} must have a name.`);
    }
    if (!Array.isArray(mode.steps)) {
      throw new Error(`Study mode with ID ${mode.id} must have a "steps" array.`);
    }
  }

  return true;
}
