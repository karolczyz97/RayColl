import 'react-native';

declare module 'react-native' {
  interface ViewStyle {
    cursor?: 'auto' | 'default' | 'pointer' | 'text' | 'wait' | 'move' | 'not-allowed';
    overflowX?: 'auto' | 'hidden' | 'scroll' | 'visible';
    overflowY?: 'auto' | 'hidden' | 'scroll' | 'visible';
    scrollbarWidth?: 'auto' | 'thin' | 'none';
    scrollbarGutter?: 'auto' | 'stable' | 'stable both-edges';
    scrollbarColor?: string;
    transformOrigin?:
      | 'center'
      | 'top'
      | 'top left'
      | 'top right'
      | 'bottom'
      | 'bottom left'
      | 'bottom right';
  }

  interface ViewProps {
    className?: string;
  }

  interface ScrollViewProps {
    className?: string;
  }

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  interface FlatListProps<ItemT> {
    className?: string;
  }

  interface TextInputProps {
    onPaste?: (event: { nativeEvent: { clipboardData?: { getData: (type: string) => string } } }) => void;
  }
}
