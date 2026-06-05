import type { ImageSourcePropType } from 'react-native';

const fallbackAvatarSource = require('../../assets/images/icon.png') as ImageSourcePropType;

export function getUserAvatarSource(user: { photoURL?: string | null } | null | undefined): ImageSourcePropType {
  return user?.photoURL ? { uri: user.photoURL } : fallbackAvatarSource;
}
