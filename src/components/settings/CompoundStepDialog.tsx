import React, { useMemo, useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import { Button, Dialog, Divider, Portal, Switch, Text } from 'react-native-paper';
import { AppSelect } from '@/components/AppSelect';
import { AppNumberInput } from '@/components/forms/AppNumberInput';
import {
  COMPOUND_KINDS_ORDER,
  COMPOUND_LABEL_KEYS,
  defaultCompoundParams,
} from '@/features/settings/compoundSteps';
import { useI18n } from '@/i18n';
import { MAX_PAUSE_MULTIPLIER } from '@/constants/studySteps';
import { TOKENS } from '@/theme/tokens';
import { dialogStyles } from '@/theme/dialogStyles';
import type {
  CompoundBranch,
  CompoundParams,
  CompoundPause,
  CompoundStep,
  CompoundStepKind,
} from '@/types/models';

interface CompoundStepDialogProps {
  visible: boolean;
  mode: 'add' | 'edit';
  initialStep: CompoundStep | null;
  pageCount: number;
  onDismiss: () => void;
  onConfirm: (params: CompoundParams) => void;
}

const FIRST_KIND = COMPOUND_KINDS_ORDER[0];

function asKind(value: string): CompoundStepKind {
  return COMPOUND_KINDS_ORDER.includes(value as CompoundStepKind)
    ? (value as CompoundStepKind)
    : FIRST_KIND;
}

function pageMax(pageCount: number): number {
  return Math.max(1, pageCount);
}

function switchRow(label: string, value: boolean, onValueChange: (value: boolean) => void) {
  return (
    <View style={styles.switchRow}>
      <Text variant="bodyLarge" selectable style={styles.switchLabel}>
        {label}
      </Text>
      <Switch value={value} onValueChange={onValueChange} />
    </View>
  );
}

export function CompoundStepDialog({
  visible,
  mode,
  initialStep,
  pageCount,
  onDismiss,
  onConfirm,
}: CompoundStepDialogProps) {
  const { t } = useI18n();
  const [kind, setKind] = useState<CompoundStepKind>(initialStep?.params.kind ?? FIRST_KIND);
  const [params, setParams] = useState<CompoundParams>(
    initialStep?.params ?? defaultCompoundParams(FIRST_KIND, { pageCount }),
  );

  const kindOptions = useMemo(
    () =>
      COMPOUND_KINDS_ORDER.map((value) => ({
        label: t(COMPOUND_LABEL_KEYS[value]),
        value,
      })),
    [t],
  );

  const setNextKind = (value: string) => {
    const nextKind = asKind(value);
    setKind(nextKind);
    if (initialStep && nextKind === initialStep.params.kind) {
      setParams(initialStep.params);
    } else {
      setParams(defaultCompoundParams(nextKind, { pageCount }));
    }
  };

  const updateBranch = (
    field: 'onCorrect' | 'onWrong',
    next: Partial<CompoundBranch>,
  ) => {
    if (params.kind !== 'listen_grade' && params.kind !== 'grade_after_listen') return;
    setParams({
      ...params,
      [field]: { ...params[field], ...next },
    });
  };

  const updatePause = (
    field: 'onCorrect' | 'onWrong',
    pause: CompoundPause | null,
  ) => updateBranch(field, { pause });

  const renderBranch = (
    title: string,
    field: 'onCorrect' | 'onWrong',
    branch: CompoundBranch,
  ) => {
    const pauseKind = branch.pause?.kind ?? 'none';
    return (
      <View style={styles.branch}>
        <Text variant="titleSmall" selectable>
          {title}
        </Text>
        {switchRow(t('compound.branch.feedback'), branch.feedback, (feedback) =>
          updateBranch(field, { feedback }),
        )}
        {switchRow(t('compound.branch.speak_enabled'), branch.speakPage !== null, (enabled) =>
          updateBranch(field, { speakPage: enabled ? 0 : null }),
        )}
        {branch.speakPage !== null ? (
          <AppNumberInput
            label={t('compound.field.speak_page')}
            value={branch.speakPage + 1}
            onChange={(page) => updateBranch(field, { speakPage: page - 1 })}
            min={1}
            max={pageMax(pageCount)}
          />
        ) : null}
        <AppSelect
          label={t('compound.field.pause')}
          value={pauseKind}
          options={[
            { label: t('compound.pause.none'), value: 'none' },
            { label: t('compound.pause.fixed'), value: 'fixed' },
            { label: t('compound.pause.dynamic'), value: 'dynamic' },
          ]}
          onChange={(value) => {
            if (value === 'fixed') updatePause(field, { kind: 'fixed', ms: 1000 });
            else if (value === 'dynamic') {
              updatePause(field, { kind: 'dynamic', page: 0, multiplier: 1 });
            } else {
              updatePause(field, null);
            }
          }}
        />
        {branch.pause?.kind === 'fixed' ? (
          <AppNumberInput
            label={t('compound.field.wait_ms')}
            value={branch.pause.ms}
            onChange={(ms) => updatePause(field, { kind: 'fixed', ms })}
            min={0}
          />
        ) : null}
        {branch.pause?.kind === 'dynamic' ? (
          <>
            <AppNumberInput
              label={t('compound.field.pause_page')}
              value={branch.pause.page + 1}
              onChange={(page) =>
                updatePause(field, {
                  kind: 'dynamic',
                  page: page - 1,
                  multiplier: branch.pause?.kind === 'dynamic' ? branch.pause.multiplier : 1,
                })
              }
              min={1}
              max={pageMax(pageCount)}
            />
            <AppNumberInput
              label={t('compound.field.multiplier')}
              value={branch.pause.multiplier}
              onChange={(multiplier) =>
                updatePause(field, {
                  kind: 'dynamic',
                  page: branch.pause?.kind === 'dynamic' ? branch.pause.page : 0,
                  multiplier,
                })
              }
              min={0}
              max={MAX_PAUSE_MULTIPLIER}
            />
          </>
        ) : null}
        {switchRow(t('compound.branch.reveal_all'), branch.revealAll, (revealAll) =>
          updateBranch(field, { revealAll }),
        )}
        {switchRow(t('compound.branch.mark_failed'), branch.markFailed, (markFailed) =>
          updateBranch(field, { markFailed }),
        )}
        <AppSelect
          label={t('compound.field.rate')}
          value={branch.rate}
          options={[
            { label: t('compound.rate.from_answer'), value: 'fromAnswer' },
            { label: t('compound.rate.fixed'), value: 'fixed' },
            { label: t('compound.rate.none'), value: 'none' },
          ]}
          onChange={(value) => {
            if (value === 'fromAnswer' || value === 'fixed' || value === 'none') {
              updateBranch(field, { rate: value });
            }
          }}
        />
        {branch.rate === 'fixed' ? (
          <AppNumberInput
            label={t('compound.field.rating')}
            value={branch.fixedRating}
            onChange={(fixedRating) => updateBranch(field, { fixedRating })}
            min={1}
            max={4}
          />
        ) : null}
        {switchRow(t('compound.branch.advance'), branch.advance, (advance) =>
          updateBranch(field, { advance }),
        )}
      </View>
    );
  };

  const renderFields = () => {
    switch (params.kind) {
      case 'present_front':
        return (
          <>
            <AppNumberInput
              label={t('compound.field.page')}
              value={params.page + 1}
              onChange={(page) => setParams({ ...params, page: page - 1 })}
              min={1}
              max={pageMax(pageCount)}
            />
            {switchRow(t('compound.field.speak'), params.speak, (speak) =>
              setParams({ ...params, speak }),
            )}
          </>
        );
      case 'flip_reveal':
        return (
          <AppSelect
            label={t('compound.field.reveal_style')}
            value={params.revealStyle}
            options={[
              { label: t('compound.reveal.all'), value: 'all' },
              { label: t('compound.reveal.next'), value: 'next' },
            ]}
            onChange={(revealStyle) => {
              if (revealStyle === 'all' || revealStyle === 'next') {
                setParams({ ...params, revealStyle });
              }
            }}
          />
        );
      case 'show_all_grade':
      case 'fail_next':
        return null;
      case 'speak_pause_next':
        return (
          <>
            <AppNumberInput
              label={t('compound.field.page')}
              value={params.page + 1}
              onChange={(page) => setParams({ ...params, page: page - 1 })}
              min={1}
              max={pageMax(pageCount)}
            />
            <AppNumberInput
              label={t('compound.field.next_page')}
              value={params.nextPage + 1}
              onChange={(nextPage) => setParams({ ...params, nextPage: nextPage - 1 })}
              min={1}
              max={pageMax(pageCount)}
            />
            <AppNumberInput
              label={t('compound.field.multiplier')}
              value={params.multiplier}
              onChange={(multiplier) => setParams({ ...params, multiplier })}
              min={0}
              max={MAX_PAUSE_MULTIPLIER}
            />
          </>
        );
      case 'auto_flip':
        return (
          <>
            <AppNumberInput
              label={t('compound.field.question_page')}
              value={params.questionPage + 1}
              onChange={(questionPage) => setParams({ ...params, questionPage: questionPage - 1 })}
              min={1}
              max={pageMax(pageCount)}
            />
            <AppNumberInput
              label={t('compound.field.answer_page')}
              value={params.answerPage + 1}
              onChange={(answerPage) => setParams({ ...params, answerPage: answerPage - 1 })}
              min={1}
              max={pageMax(pageCount)}
            />
            <AppNumberInput
              label={t('compound.field.multiplier')}
              value={params.multiplier}
              onChange={(multiplier) => setParams({ ...params, multiplier })}
              min={0}
              max={MAX_PAUSE_MULTIPLIER}
            />
            {switchRow(t('compound.field.speak_question'), params.speakQuestion, (speakQuestion) =>
              setParams({ ...params, speakQuestion }),
            )}
            {switchRow(t('compound.field.speak_answer'), params.speakAnswer, (speakAnswer) =>
              setParams({ ...params, speakAnswer }),
            )}
          </>
        );
      case 'listen_grade':
      case 'grade_after_listen':
        return (
          <>
            {params.kind === 'listen_grade' ? (
              <>
                <AppNumberInput
                  label={t('compound.field.answer_page')}
                  value={params.answerPage + 1}
                  onChange={(answerPage) => setParams({ ...params, answerPage: answerPage - 1 })}
                  min={1}
                  max={pageMax(pageCount)}
                />
                <AppNumberInput
                  label={t('compound.field.threshold')}
                  value={params.threshold}
                  onChange={(threshold) => setParams({ ...params, threshold })}
                  min={0}
                  max={100}
                />
              </>
            ) : null}
            {renderBranch(t('compound.branch.correct'), 'onCorrect', params.onCorrect)}
            <Divider />
            {renderBranch(t('compound.branch.wrong'), 'onWrong', params.onWrong)}
            <Divider />
            {switchRow(t('compound.field.manual_fallback'), params.manualFallback, (manualFallback) =>
              setParams({ ...params, manualFallback }),
            )}
          </>
        );
      case 'auto_pass_next':
        return (
          <AppNumberInput
            label={t('compound.field.rating')}
            value={params.rating}
            onChange={(rating) => setParams({ ...params, rating })}
            min={1}
            max={4}
          />
        );
    }
  };

  return (
    <Portal>
      <Dialog visible={visible} onDismiss={onDismiss} style={dialogStyles.dialog}>
        <Dialog.Title>
          {mode === 'edit' ? t('settings.dialog.compound.edit_title') : t('settings.dialog.compound.add_title')}
        </Dialog.Title>
        <Dialog.Content>
          <ScrollView
            contentContainerStyle={styles.content}
            keyboardShouldPersistTaps="handled"
            style={styles.scroll}
          >
            <AppSelect
              label={t('settings.dialog.add_step.type')}
              value={kind}
              options={kindOptions}
              onChange={setNextKind}
            />
            {renderFields()}
          </ScrollView>
        </Dialog.Content>
        <Dialog.Actions>
          <Button onPress={onDismiss}>{t('btn.cancel')}</Button>
          <Button mode="contained" onPress={() => onConfirm(params)}>
            {mode === 'edit' ? t('btn.save') : t('btn.add')}
          </Button>
        </Dialog.Actions>
      </Dialog>
    </Portal>
  );
}

const styles = StyleSheet.create({
  scroll: {
    maxHeight: 560,
  },
  content: {
    gap: TOKENS.spacing.md,
    paddingBottom: TOKENS.spacing.sm,
  },
  switchRow: {
    minHeight: TOKENS.control.height,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: TOKENS.spacing.md,
  },
  switchLabel: {
    flex: 1,
  },
  branch: {
    gap: TOKENS.spacing.sm,
  },
});
