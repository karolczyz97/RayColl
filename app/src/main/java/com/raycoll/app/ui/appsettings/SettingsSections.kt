package com.raycoll.app.ui.appsettings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.raycoll.app.R
import com.raycoll.app.data.firestore.SyncStatus
import com.raycoll.app.data.local.prefs.ThemePref
import com.raycoll.app.data.local.prefs.UserPreferences
import com.raycoll.app.domain.model.TtsRate
import com.raycoll.app.ui.components.ChoiceChip
import com.raycoll.app.ui.components.DropdownChip
import com.raycoll.app.ui.components.RayCollCard
import com.raycoll.app.ui.components.SectionCard
import com.raycoll.app.ui.components.SyncStatusBanner
import com.raycoll.app.ui.theme.Spacing
import java.util.Locale
import kotlin.math.roundToInt

/** Języki interfejsu ("" = jak w systemie) — te, dla których są tłumaczenia w res/values-*. */
internal val APP_LANGUAGES = listOf("", "pl", "en", "de", "es", "it")

private const val TTS_RATE_STEP = 0.1f

@Composable
fun AccountSection(state: AppSettingsUiState, onSignIn: () -> Unit, onSignOut: () -> Unit, onSyncNow: () -> Unit) {
    SectionCard(title = stringResource(R.string.app_settings_section_account)) {
        val account = state.account
        if (account != null) {
            Text(
                stringResource(R.string.auth_signed_in_as, account.email.orEmpty()),
                style = MaterialTheme.typography.bodyMedium
            )
            SyncStatusBanner(status = state.syncStatus, onRetry = onSyncNow)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.s)
            ) {
                Button(
                    onClick = onSyncNow,
                    enabled =
                        state.syncStatus != SyncStatus.SYNCING && !state.signingOut && state.backupOperation == null,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(stringResource(R.string.auth_sync_now))
                }
                OutlinedButton(
                    onClick = onSignOut,
                    enabled = !state.signingOut && state.backupOperation == null,
                    shape = MaterialTheme.shapes.small
                ) {
                    if (state.signingOut) {
                        CircularProgressIndicator(modifier = Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
                    }
                    Text(stringResource(R.string.auth_sign_out))
                }
            }
        } else {
            Text(
                stringResource(R.string.auth_local),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(stringResource(R.string.auth_local_benefit), style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = onSignIn,
                enabled = !state.signingIn && state.backupOperation == null,
                shape = MaterialTheme.shapes.small
            ) {
                if (state.signingIn) {
                    CircularProgressIndicator(modifier = Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.auth_sign_in_google))
                }
            }
        }
    }
}

@Composable
fun AppearanceSection(preferences: UserPreferences, onTheme: (ThemePref) -> Unit, onSystemColors: (Boolean) -> Unit) {
    SectionCard(title = stringResource(R.string.app_settings_section_appearance)) {
        SwitchRow(
            title = stringResource(R.string.app_settings_dynamic_colors_title),
            description = stringResource(R.string.app_settings_dynamic_colors_desc),
            checked = preferences.useSystemColors,
            onChange = onSystemColors
        )
        Text(stringResource(R.string.app_settings_theme), style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            listOf(
                ThemePref.SYSTEM to R.string.app_settings_theme_system,
                ThemePref.LIGHT to R.string.app_settings_theme_light,
                ThemePref.DARK to R.string.app_settings_theme_dark
            ).forEach { (theme, label) ->
                ChoiceChip(
                    selected = preferences.theme == theme,
                    onClick = { onTheme(theme) },
                    label = { Text(stringResource(label)) }
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
    SectionCard(title = stringResource(R.string.app_settings_section_language_speech)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.app_settings_lang),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            AppLanguagePicker(currentLanguage, onLanguage)
        }
        var rate by remember(preferences.ttsRate) { mutableFloatStateOf(preferences.ttsRate) }
        Row {
            Text(
                stringResource(R.string.app_settings_tts_rate),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(R.string.app_settings_tts_rate_value, rate),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        val rateName = stringResource(R.string.app_settings_tts_rate)
        val rateValue = stringResource(R.string.app_settings_tts_rate_value, rate)
        Slider(
            modifier = Modifier.semantics {
                contentDescription = rateName
                stateDescription = rateValue
            },
            value = rate,
            onValueChange = { rate = it },
            onValueChangeFinished = { onTtsRate(rate) },
            valueRange = TtsRate.MIN..TtsRate.MAX,
            steps = ((TtsRate.MAX - TtsRate.MIN) / TTS_RATE_STEP).roundToInt() - 1
        )
        SwitchRow(
            title = stringResource(R.string.app_settings_background_playback),
            description = stringResource(R.string.app_settings_background_playback_desc),
            checked = preferences.backgroundPlayback,
            onChange = onBackgroundPlayback
        )
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
    RayCollCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Tune, contentDescription = null) }
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_settings_study_modes), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.study_modes_manage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun BackupSection(onExport: () -> Unit, onImport: () -> Unit, enabled: Boolean = true) {
    SectionCard(title = stringResource(R.string.app_settings_export_import)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s)
        ) {
            Button(onClick = onExport, enabled = enabled, shape = MaterialTheme.shapes.small) {
                Text(stringResource(R.string.app_settings_export_btn))
            }
            OutlinedButton(onClick = onImport, enabled = enabled, shape = MaterialTheme.shapes.small) {
                Text(stringResource(R.string.app_settings_import_file_btn))
            }
        }
    }
}

/** Wymazanie wszystkich danych — osobno od bezpiecznych akcji kopii zapasowej. */
@Composable
fun FactoryResetSection(onReset: () -> Unit, enabled: Boolean = true) {
    SectionCard {
        Button(
            onClick = onReset,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.app_settings_factory_reset)) }
    }
}

@Composable
internal fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
