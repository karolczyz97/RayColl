import { Platform } from 'react-native';

export function useStableScrollbarProps() {
  return {
    className: Platform.OS === 'web' ? 'raycoll-stable-scrollbar' : undefined,
    style: Platform.OS === 'web' ? { scrollbarGutter: 'stable both-edges' as const } : undefined,
  };
}
