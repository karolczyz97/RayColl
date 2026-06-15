import React from 'react';
import { StyleSheet, View } from 'react-native';
import { HelperText, IconButton, useTheme } from 'react-native-paper';
import { getTopBarColors } from '@/theme/semanticColors';
import { AppSelect } from '@/components/AppSelect';
import { AppTextInput } from '@/components/forms/AppTextInput';
import { ActionConfirmDialog } from '@/components/dialogs/ActionConfirmDialog';
import { TOKENS } from '@/theme/tokens';
import { CARD_FILTER_OPTIONS, type CardFilter } from '@/constants/cardFilters';
import { CARD_ORDER_OPTIONS, normalizeCardOrder } from '@/constants/cardOrder';
import { ARCHIVE_RETENTION_DAYS } from '@/constants/archive';
import { AppScreen } from '@/components/layout/AppScreen';
import { AnimatedSection } from '@/components/layout/AnimatedSection';
import { SettingsSection, SettingsTile } from '@/components/settings/SettingsSection';
import { PageConfigEditor } from '@/components/pageConfig/PageConfigEditor';
import { useI18n } from '@/i18n';

function isCardFilter(value: string): value is CardFilter {
  return CARD_FILTER_OPTIONS.some((filter) => filter.value === value);
}

export function DeckSettingsScreen(
  controller: ReturnType<typeof import('./useDeckSettingsController').useDeckSettingsController>,
) {
  const { fg: topBarFg } = getTopBarColors(useTheme());
  const { t } = useI18n();
  const {
    activeGroup,
    colNames,
    deckName,
    deckNameError,
    archiveDialogOpen,
    handleColBlur,
    handleArchiveGroup,
    handleNameBlur,
    handleCreateMode,
    handleEditMode,
    movePageSetting,
    onFilterChange,
    onCardOrderChange,
    onModeChange,
    pageCount,
    pageNameErrors,
    popularLangs,
    formMaxWidth,
    setColNames,
    setDeckName,
    setArchiveDialogOpen,
    store,
    updatePageLangValue,
    adjustPageCount,
    handleBack,
  } = controller;

  if (!activeGroup) {
    return null;
  }

  const modeOptions = store.studyModes.map((mode) => ({
    label: mode.name,
    value: mode.id,
  }));

  const scopeOptions = CARD_FILTER_OPTIONS.map((filter) => ({
    label: t(filter.labelKey),
    value: filter.value,
  }));

  const orderOptions = CARD_ORDER_OPTIONS.map((option) => ({
    label: t(option.labelKey),
    value: option.value,
  }));

  return (
    <AppScreen
      title={t('settings.title', { name: activeGroup.name })}
      onBack={handleBack}
      right={
        <IconButton
          icon="archive-arrow-down-outline"
          iconColor={topBarFg}
          onPress={() => setArchiveDialogOpen(true)}
          accessibilityLabel={t('settings.archive_btn')}
        />
      }
      maxWidth={formMaxWidth}
    >
      <View style={styles.content}>
        <AnimatedSection order={0}>
          <SettingsSection title={t('settings.section.general')}>
            <SettingsTile title={t('settings.rename_label')}>
              <AppTextInput
                label={t('settings.rename_label')}
                value={deckName}
                onChangeText={setDeckName}
                onBlur={handleNameBlur}
                error={!!deckNameError}
              />
              {deckNameError ? (
                <HelperText type="error" visible>
                  {t('validation.required')}
                </HelperText>
              ) : null}
            </SettingsTile>
          </SettingsSection>
        </AnimatedSection>

        <AnimatedSection order={1}>
          <SettingsSection title={t('settings.section.cards')}>
            <SettingsTile title={t('settings.pages_config')}>
              <PageConfigEditor
                mode="settings"
                pageCount={pageCount}
                pageNames={colNames}
                pageLanguages={activeGroup.pageLanguages}
                popularLangs={popularLangs}
                onPageCountChange={adjustPageCount}
                onPageNameChange={(index, value) => {
                  setColNames((prev) => {
                    const next = [...prev];
                    next[index] = value;
                    return next;
                  });
                }}
                onPageNameBlur={handleColBlur}
                pageNameErrors={pageNameErrors}
                pageNameErrorMessage={t('validation.required')}
                onPageLanguageChange={updatePageLangValue}
              onMovePage={movePageSetting}
            />
            </SettingsTile>
          </SettingsSection>
        </AnimatedSection>

        <AnimatedSection order={2}>
          <SettingsSection title={t('settings.section.study')}>
            <SettingsTile
              title={t('settings.active_mode')}
              trailing={
                <View style={styles.modeActions}>
                  <IconButton
                    icon="pencil"
                    mode="contained"
                    size={TOKENS.iconSize.md}
                    onPress={handleEditMode}
                    accessibilityLabel="Edit study mode"
                  />
                  <IconButton
                    icon="plus"
                    mode="contained"
                    size={TOKENS.iconSize.md}
                    onPress={handleCreateMode}
                    accessibilityLabel="Create study mode"
                  />
                </View>
              }
            >
              <AppSelect
                value={activeGroup.activeModeId}
                options={modeOptions}
                onChange={onModeChange}
                accessibilityLabel="Active study mode selection"
              />
            </SettingsTile>

            <SettingsTile title={t('settings.study_scope')}>
              <AppSelect
                value={activeGroup.studyFilter}
                options={scopeOptions}
                onChange={(value) => {
                  if (isCardFilter(value)) {
                    onFilterChange(value);
                  }
                }}
                accessibilityLabel="Study scope filter selection"
              />
            </SettingsTile>

            <SettingsTile title={t('settings.card_order')}>
              <AppSelect
                value={normalizeCardOrder(activeGroup.cardOrder)}
                options={orderOptions}
                onChange={(value) => onCardOrderChange(normalizeCardOrder(value))}
                accessibilityLabel="Card order selection"
              />
            </SettingsTile>
          </SettingsSection>
        </AnimatedSection>
      </View>

      <ActionConfirmDialog
        visible={archiveDialogOpen}
        onDismiss={() => setArchiveDialogOpen(false)}
        onConfirm={handleArchiveGroup}
        titleKey="dialog.archive.title"
        messageKey="dialog.archive.confirm"
        messageReplacements={{ days: ARCHIVE_RETENTION_DAYS }}
        confirmLabelKey="btn.archive"
        cancelLabelKey="btn.cancel"
      />
    </AppScreen>
  );
}

const styles = StyleSheet.create({
  content: {
    width: '100%',
    gap: TOKENS.spacing.lg,
  },
  modeActions: {
    flexDirection: 'row',
    gap: TOKENS.spacing.xs,
  },
});
