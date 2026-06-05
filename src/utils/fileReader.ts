import type { DocumentPickerAsset } from 'expo-document-picker';
import { Platform } from 'react-native';

export async function readAssetText(asset: DocumentPickerAsset): Promise<string> {
  if (Platform.OS === 'web' && asset.file) {
    return asset.file.text();
  }
  return (await fetch(asset.uri)).text();
}
