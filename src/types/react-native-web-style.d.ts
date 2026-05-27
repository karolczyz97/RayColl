import 'react-native';

declare module 'react-native' {
  interface ViewStyle {
    cursor?: 'auto' | 'default' | 'pointer' | 'text' | 'wait' | 'move' | 'not-allowed';
    transformOrigin?:
      | 'center'
      | 'top'
      | 'top left'
      | 'top right'
      | 'bottom'
      | 'bottom left'
      | 'bottom right';
  }
}
