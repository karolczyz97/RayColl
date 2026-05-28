import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Button, Text, useTheme } from 'react-native-paper';
import { TOKENS } from '../../theme/tokens';

interface AppErrorBoundaryProps {
  children: React.ReactNode;
  resetKey?: string | number | null;
  title?: string;
}

interface AppErrorBoundaryState {
  error: Error | null;
}

function ErrorFallback({
  error,
  onReset,
  title,
}: {
  error: Error;
  onReset: () => void;
  title: string;
}) {
  const theme = useTheme();

  return (
    <View style={[styles.container, { backgroundColor: theme.colors.background }]}>
      <Text variant="headlineSmall" style={styles.title}>
        {title}
      </Text>
      <Text variant="bodyMedium" style={[styles.message, { color: theme.colors.onSurfaceVariant }]}>
        {error.message}
      </Text>
      <Button mode="contained" onPress={onReset} accessibilityLabel="Try rendering this screen again">
        Try again
      </Button>
    </View>
  );
}

export class AppErrorBoundary extends React.Component<
  AppErrorBoundaryProps,
  AppErrorBoundaryState
> {
  state: AppErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): AppErrorBoundaryState {
    return { error };
  }

  componentDidUpdate(prevProps: AppErrorBoundaryProps) {
    if (prevProps.resetKey !== this.props.resetKey && this.state.error) {
      this.setState({ error: null });
    }
  }

  private reset = () => {
    this.setState({ error: null });
  };

  render() {
    if (this.state.error) {
      return (
        <ErrorFallback
          error={this.state.error}
          onReset={this.reset}
          title={this.props.title ?? 'Something went wrong'}
        />
      );
    }

    return this.props.children;
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: TOKENS.spacing.lg,
    padding: TOKENS.spacing.xl,
  },
  title: {
    fontWeight: TOKENS.typography.weight.bold,
    textAlign: 'center',
  },
  message: {
    textAlign: 'center',
  },
});
