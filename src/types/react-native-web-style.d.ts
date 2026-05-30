import 'react-native';

declare module 'react-native' {
  interface ViewStyle {
    cursor?: 'auto' | 'default' | 'pointer' | 'text' | 'wait' | 'move' | 'not-allowed';
    overflowX?: 'auto' | 'hidden' | 'scroll' | 'visible';
    overflowY?: 'auto' | 'hidden' | 'scroll' | 'visible';
    scrollbarWidth?: 'auto' | 'thin' | 'none';
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
