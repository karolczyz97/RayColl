package com.raycoll.app.ui.appsettings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.raycoll.app.R
import com.raycoll.app.data.local.prefs.ThemePref
import com.raycoll.app.data.local.prefs.UserPreferences
import com.raycoll.app.domain.model.TtsRate
import com.raycoll.app.ui.components.DropdownChip
import com.raycoll.app.ui.components.SettingsAction
import com.raycoll.app.ui.components.SettingsBlock
import com.raycoll.app.ui.components.SettingsGroup
import com.raycoll.app.ui.components.SettingsSwitch
import com.raycoll.app.ui.components.SingleChoiceButtonGroup
import com.raycoll.app.ui.components.SyncStatus
import com.raycoll.app.ui.components.SyncStatusBanner
import java.util.Locale
import kotlin.math.roundToInt

/** Języki interfejsu ("" = jak w systemie) — te, dla których są tłumaczenia w res/values-*. */
internal val APP_LANGUAGES = listOf("", "pl", "en", "de", "es", "it")

private const val TTS_RATE_STEP = 0.1f

@Composable
fun AccountSection(state: AppSettingsUiState, onSignIn: () -> Unit, onSignOut: () -> Unit, onSyncNow: () -> Unit) {
    val account = state.account
    SettingsGroup(stringResource(R.string.app_settings_section_account)) {
        if (account != null) {
            item { shapes ->
                SettingsBlock(shapes) {
                    Text(stringResource(R.string.auth_signed_in_as, account.email.orEmpty()))
                    SyncStatusBanner(status = state.syncStatus, onRetry = onSyncNow)
                }
            }
            item { shapes ->
                SettingsAction(
                    shapes = shapes,
                    title = stringResource(R.string.auth_sync_now),
                    icon = painterResource(R.drawable.ic_sync),
                    enabled =
                        state.syncStatus != SyncStatus.SYNCING && !state.signingOut && state.backupOperation == null,
                    onClick = onSyncNow
                )
            }
            item { shapes ->
                SettingsAction(
                    shapes = shapes,
                    title = stringResource(R.string.auth_sign_out),
                    icon = painterResource(R.drawable.ic_logout),
                    description = if (state.syncStatus == SyncStatus.OFFLINE) {
                        stringResource(R.string.auth_sign_out_offline)
                    } else {
                        null
                    },
                    enabled = !state.signingOut && state.backupOperation == null &&
                        state.syncStatus != SyncStatus.OFFLINE,
                    busy = state.signingOut,
                    onClick = onSignOut
                )
            }
        } else {
            item { shapes ->
                SettingsBlock(shapes) {
                    Text(stringResource(R.string.auth_local_benefit))
                    Text(
                        stringResource(R.string.auth_local),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item { shapes ->
                SettingsAction(
                    shapes = shapes,
                    title = stringResource(R.string.auth_sign_in_google),
                    icon = painterResource(R.drawable.ic_login),
                    enabled = !state.signingIn && state.backupOperation == null,
                    busy = state.signingIn,
                    onClick = onSignIn
                )
            }
        }
    }
}

@Composable
fun AppearanceSection(preferences: UserPreferences, onTheme: (ThemePref) -> Unit, onSystemColors: (Boolean) -> Unit) {
    SettingsGroup(stringResource(R.string.app_settings_section_appearance)) {
        item { shapes ->
            SettingsSwitch(
                shapes = shapes,
                title = stringResource(R.string.app_settings_dynamic_colors_title),
                description = stringResource(R.string.app_settings_dynamic_colors_desc),
                checked = preferences.useSystemColors,
                onChange = onSystemColors
            )
        }
        item { shapes ->
            SettingsBlock(shapes, title = stringResource(R.string.app_settings_theme)) {
                SingleChoiceButtonGroup(
                    options = listOf(
                        ThemePref.SYSTEM to stringResource(R.string.app_settings_theme_system),
                        ThemePref.LIGHT to stringResource(R.string.app_settings_theme_light),
                        ThemePref.DARK to stringResource(R.string.app_settings_theme_dark)
                    ),
                    selected = preferences.theme,
                    onSelect = onTheme
                )
            }
        }
    }
}

@Composable
fun LanguageSpeechSection(
    preferences: UserPreferences,
    currentLanguage: String,
    onLanguage: (String) -> Unit,
    onTtsRate: (Float) -> Unit,
    onBackgroundPlayback: (Boolean) -> Unit
) {
    SettingsGroup(stringResource(R.string.app_settings_section_language_speech)) {
        item { shapes ->
            SettingsBlock(
                shapes,
                title = stringResource(R.string.app_settings_lang),
                trailingContent = { AppLanguagePicker(currentLanguage, onLanguage) }
            ) {}
        }
        item { shapes ->
            // klucz: suwak wraca do zapisanej wartości, gdy ta zmieni się poza nim
            key(preferences.ttsRate) {
                val rate = rememberSliderState(
                    preferences.ttsRate,
                    ((TtsRate.MAX - TtsRate.MIN) / TTS_RATE_STEP).roundToInt() - 1,
                    TtsRate.MIN..TtsRate.MAX
                )
                val rateName = stringResource(R.string.app_settings_tts_rate)
                val rateValue = stringResource(R.string.app_settings_tts_rate_value, rate.value)
                // wartość w linii tytułu, nie w kolumnie końcowej — ta zabrałaby suwakowi prawą część wiersza
                SettingsBlock(shapes) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(rateName, Modifier.weight(1f))
                        Text(
                            rateValue,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        state = rate,
                        onValueChange = { rate.value = it },
                        modifier = Modifier.fillMaxWidth().semantics {
                            contentDescription = rateName
                            stateDescription = rateValue
                        },
                        onValueChangeFinished = { onTtsRate(rate.value) }
                    )
                }
            }
        }
        item { shapes ->
            SettingsSwitch(
                shapes = shapes,
                title = stringResource(R.string.app_settings_background_playback),
                description = stringResource(R.string.app_settings_background_playback_desc),
                checked = preferences.backgroundPlayback,
                onChange = onBackgroundPlayback
            )
        }
    }
}

@Composable
private fun AppLanguagePicker(current: String, onSelect: (String) -> Unit) {
    DropdownChip(
        label = appLanguageLabel(current),
        options = APP_LANGUAGES,
        selected = current,
        optionLabel = { appLanguageLabel(it) },
        onSelect = onSelect
    )
}

/** Nazwa języka w nim samym ("Deutsch", "Polski") — tak użytkownik najłatwiej go rozpozna. */
@Composable
private fun appLanguageLabel(tag: String): String {
    if (tag.isEmpty()) return stringResource(R.string.lang_system)
    val locale = Locale.forLanguageTag(tag)
    return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
}

@Composable
fun StudyModesLink(onClick: () -> Unit) {
    SettingsGroup(title = null) {
        item { shapes ->
            SettingsAction(
                shapes = shapes,
                title = stringResource(R.string.app_settings_study_modes),
                description = stringResource(R.string.study_modes_manage),
                icon = painterResource(R.drawable.ic_tune),
                onClick = onClick
            )
        }
    }
}

/** Kopia zapasowa i — na końcu, na czerwono — wymazanie wszystkich danych. */
@Composable
fun BackupSection(onExport: () -> Unit, onImport: () -> Unit, onReset: () -> Unit, enabled: Boolean = true) {
    SettingsGroup(stringResource(R.string.app_settings_export_import)) {
        item { shapes ->
            SettingsAction(
                shapes = shapes,
                title = stringResource(R.string.app_settings_export_btn),
                icon = painterResource(R.drawable.ic_download),
                enabled = enabled,
                onClick = onExport
            )
        }
        item { shapes ->
            SettingsAction(
                shapes = shapes,
                title = stringResource(R.string.app_settings_import_file_btn),
                icon = painterResource(R.drawable.ic_upload),
                enabled = enabled,
                onClick = onImport
            )
        }
        item { shapes ->
            SettingsAction(
                shapes = shapes,
                title = stringResource(R.string.app_settings_factory_reset),
                icon = painterResource(R.drawable.ic_delete_forever),
                enabled = enabled,
                destructive = true,
                onClick = onReset
            )
        }
    }
}
