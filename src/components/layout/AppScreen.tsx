import React from 'react';
import { ScrollView, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';
import { useTheme } from 'react-native-paper';
import { SafeAreaView } from 'react-native-safe-area-context';
import { TOKENS } from '../../theme/tokens';
import { PageHeader } from '../PageHeader';
import { ScreenContent } from './ScreenContent';

interface AppScreenProps {
  title?: string;
  onBack?: () => void;
  action?: React.ReactNode;
  children: React.ReactNode;
  scroll?: boolean;
  maxWidth?: number;
  edges?: ('top' | 'left' | 'right' | 'bottom')[];
  contentStyle?: StyleProp<ViewStyle>;
}

export function AppScreen({
  title,
  onBack,
  action,
  children,
  scroll = true,
  maxWidth,
  edges = ['top', 'left', 'right'],
  contentStyle,
}: AppScreenProps) {
  const theme = useTheme();

  const content = (
    <ScreenContent maxWidth={maxWidth} style={contentStyle}>
      {children}
    </ScreenContent>
  );

  return (
    <SafeAreaView
      edges={edges}
      style={[styles.root, { backgroundColor: theme.colors.background }]}
    >
      {title && onBack ? <PageHeader title={title} onBack={onBack} action={action} /> : null}

      {scroll ? (
        <ScrollView contentContainerStyle={styles.scrollContent}>{content}</ScrollView>
      ) : (
        <View style={styles.flexFill}>{content}</View>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    paddingHorizontal: TOKENS.spacing.xxs,
  },
  scrollContent: {
    paddingBottom: TOKENS.spacing.xxl * 2,
  },
  flexFill: {
    flex: 1,
  },
});
