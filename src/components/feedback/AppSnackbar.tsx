import React from 'react';
import { StyleSheet } from 'react-native';
import { Portal, Snackbar } from 'react-native-paper';
import { TOKENS } from '@/theme/tokens';
import { useI18n } from '@/i18n';

interface AppSnackbarProps {
  visible: boolean;
  message: string;
  onDismiss: () => void;
  actionLabel?: string;
  duration?: number;
}

export function AppSnackbar({
  visible,
  message,
  onDismiss,
  actionLabel,
  duration = TOKENS.motion.duration.snackbar,
}: AppSnackbarProps) {
  const { t } = useI18n();
  return (
    <Portal>
      <Snackbar
        visible={visible}
        onDismiss={onDismiss}
        duration={duration}
        action={{ label: actionLabel ?? t('common.ok'), onPress: onDismiss }}
        wrapperStyle={styles.wrapper}
        style={styles.snackbar}
      >
        {message}
      </Snackbar>
    </Portal>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    alignSelf: 'center',
    width: '100%',
    maxWidth: TOKENS.layout.snackbarMaxWidth,
    paddingHorizontal: TOKENS.spacing.lg,
  },
  snackbar: {
    borderRadius: TOKENS.radius.lg,
  },
});
