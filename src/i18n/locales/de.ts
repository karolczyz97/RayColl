export const de: Record<string, string> = {
  // Common
  'app.title': 'RayColl',
  'btn.cancel': 'Abbrechen',
  'btn.save': 'Speichern',
  'btn.add': 'Hinzufügen',
  'btn.delete': 'Löschen',
  'btn.back': 'Zurück',
  'btn.login': 'Mit Google anmelden',
  'btn.logout': 'Abmelden',
  'auth.local': 'Lokaler Modus',
  'auth.welcome': 'Willkommen, {name}',
  'auth.migration.title': 'Lokale Daten gefunden',
  'auth.migration.desc':
    'Du hast lokale Decks aus einer früheren Sitzung. Möchtest du sie auf dein Konto übertragen oder neu beginnen?',
  'auth.migration.migrate_btn': 'Auf Konto übertragen',
  'auth.migration.fresh_btn': 'Neu beginnen',
  'dialog.delete.title': 'Sind Sie sicher?',
  'dialog.delete.desc': 'Diese Aktion kann nicht rückgängig gemacht werden.',
  'dialog.delete.token': 'LÖSCHEN',

  // Dashboard
  'dashboard.no_groups': 'Keine Decks gefunden. Klicken Sie auf +, um eines hinzuzufügen.',
  'dashboard.create_title': 'Deck erstellen',
  'dashboard.name_label': 'Deckname',
  'dashboard.langs_label': 'Seitensprachen (z. B. en-US, pl-PL)',
  'dashboard.pages_label': 'Seitennamen (z. B. Wort, Übersetzung)',
  'dashboard.due_count': 'Fällig: {count}',
  'dashboard.mastery': 'Gemeistert: {percent}%',
  'dashboard.tooltip.study': 'Lernen',
  'dashboard.tooltip.browse': 'Karten durchsuchen',
  'dashboard.tooltip.settings': 'Deck-Einstellungen',
  'dashboard.cards_count': '{count} Karten',

  // Study
  'study.finished': 'Lernsitzung beendet! 🎉',
  'study.finished_desc': 'Alle für heute geplanten Karten wurden überprüft.',
  'study.no_due': 'Keine fälligen Karten zur Überprüfung.',
  'study.status.listening': 'Hören...',
  'study.status.speaking': 'Sprechen...',
  'study.match_percent': 'Übereinstimmung: {percent}%',
  'study.rating.1': 'Nochmal',
  'study.rating.2': 'Schwer',
  'study.rating.3': 'Gut',
  'study.rating.4': 'Einfach',
  'study.restart_session': 'Sitzung neu starten',
  'study.restart_failed': 'Nur Fehlgeschlagene wiederholen',
  'study.group_not_found': 'Deck nicht gefunden',
  'study.bravo': 'Bravo! 🎉',
  'study.back_to_panel': 'Zurück zum Dashboard',
  'study.recognized': 'Erkannt:',
  'study.tap_to_reveal': 'Tippen zum Aufdecken',

  // Browse
  'browse.search_placeholder': 'Karteikarten suchen...',
  'browse.add_card': 'Karte hinzufügen',
  'browse.edit_card': 'Karte bearbeiten',
  'browse.no_cards': 'Keine Karteikarten gefunden.',
  'browse.min_filled_pages': 'Fülle mindestens 2 Felder aus, um diese Karte zu speichern.',

  // Import
  'import.title': 'Karteikarten importieren',
  'import.csv_label': 'Fügen Sie CSV- / TSV-Text hier ein',
  'import.separator': 'Trennzeichen',
  'import.preview': 'Vorschau',
  'import.btn': 'Importieren Sie {count} Karten',
  'import.success': 'Erfolgreich {count} Karten importiert!',
  'import.name_placeholder':
    'Fügen Sie Daten ein — Trennzeichen und Seitenanzahl werden automatisch erkannt',
  'import.upload_btn': 'Datei hochladen (.csv, .txt, .json)',
  'import.sep.tab': 'Tabstopp',
  'import.sep.semicolon': 'Semikolon (;)',
  'import.sep.comma': 'Komma (,)',
  'import.sep.pipe': 'Senkrechter Strich (|)',
  'import.sep.custom': 'Benutzerdefiniert',
  'import.sep.current_custom': 'Aktuell',
  'import.sep.set_custom': 'Eigenes festlegen',
  'import.first_row_header': 'Erste Zeile ist eine Kopfzeile',
  'import.err.too_many_lines': 'Eingabe auf 500 Zeilen beschränkt.',
  'import.pages_count': 'Seiten:',
  'import.page_label': 'Seite {index}',
  'import.import_create_btn': 'Importieren & Deck erstellen',
  'import.preview_rows': 'Vorschau ({count} Zeilen)',

  // Stats
  'stats.title': 'Lernstatistiken',
  'stats.decks_title': 'Stapel',
  'stats.cards_title': 'Karten',
  'stats.heatmap_title': 'Aktivität in den letzten 140 Tagen',
  'stats.progress_title': 'Deck-Fortschritt',
  'stats.streak': 'Tage in Folge',
  'stats.active_days': 'Aktive Tage',
  'stats.due_cards': 'Fällig',
  'stats.deck_progress': 'Deck-Fortschritt',
  'stats.overall_progress': 'Gesamtfortschritt',
  'stats.cards_count': '{count} Karten',
  'stats.day.mon': 'Mon',
  'stats.day.wed': 'Mit',
  'stats.day.fri': 'Fre',
  'stats.day.sun': 'Son',

  // Settings
  'settings.title': 'Deck-Einstellungen: {name}',
  'settings.modes_title': 'Lernmodus',
  'settings.steps_title': 'Schrittfolge',
  'settings.delete_btn': 'Deck löschen',
  'settings.delete_desc':
    'Sind Sie sicher, dass Sie dieses Deck löschen möchten? Dadurch werden alle seine Karten dauerhaft entfernt.',
  'settings.rename_label': 'Deckname',
  'settings.pages_config': 'Seitenkonfiguration',
  'settings.reorder_columns': 'Spalten neu ordnen',
  'settings.active_mode': 'Aktiver Lernmodus',
  'settings.study_scope': 'Lernumfang',
  'settings.which_cards': 'Welche Karten lernen',
  'settings.mode_steps': 'Schritte des Modus "{name}"',
  'settings.add_step_btn': 'Schritt hinzufügen',
  'settings.reset_mode_btn': 'Auf Standard zurücksetzen',
  'settings.create_mode_btn': 'Neuen Modus erstellen',
  'settings.new_mode_name': 'Modusname',
  'settings.save_mode_btn': 'Modus speichern',
  'settings.dialog.add_step.title': 'Schritt hinzufügen',
  'settings.dialog.add_step.type': 'Typ',
  'settings.dialog.add_step.page_idx': 'Seitenindex (beginnt bei 0)',
  'settings.dialog.add_step.time': 'Zeit (ms)',
  'settings.dialog.add_step.threshold': 'Schwellenwert (%)',
  'settings.dialog.delete.confirm_text':
    'Um das Löschen des Decks "{name}" zu bestätigen, geben Sie unten {token} ein:',

  // App Settings
  'app_settings.title': 'App-Einstellungen',
  'app_settings.theme': 'Thema',
  'app_settings.theme.system': 'System',
  'app_settings.theme.light': 'Hell',
  'app_settings.theme.dark': 'Dunkel',
  'app_settings.tts_rate': 'TTS-Sprachgeschwindigkeit',
  'app_settings.export_import': 'Daten exportieren / importieren',
  'app_settings.export_btn': 'Zustand nach JSON exportieren',
  'app_settings.import_placeholder': 'Fügen Sie exportiertes JSON hier ein...',
  'app_settings.import_btn': 'Zustand importieren',
  'app_settings.reset_btn': 'App auf Standardwerte zurücksetzen',
  'app_settings.reset_confirm':
    'Sind Sie sicher, dass Sie die App zurücksetzen möchten? Alle benutzerdefinierten Decks und Statistiken werden gelöscht.',
  'app_settings.lang': 'Sprache',
  'app_settings.dynamic_colors.title': 'Dynamische Systemfarben',
  'app_settings.dynamic_colors.desc':
    'Akzent- und Systemfarben direkt vom Gerät extrahieren (Android 12+ / Material You).',
  'app_settings.dynamic_colors.enabled': 'Aktiviert',
  'app_settings.dynamic_colors.disabled': 'Deaktiviert',

  // Mode names
  'mode.classic.name': 'Klassisch',
  'mode.listen-speak.name': 'Audio',
  'mode.m1.name': 'Klassisch (Zeigen + Antworten)',
  'mode.m2.name': 'Hören + Sprechen',
  'mode.m3.name': 'Nur Zeigen',

  // Steps
  'step.show_page': 'Zeige Seite {index}',
  'step.speak_page': 'TTS Seite {index} (+{pause}ms)',
  'step.dynamic_pause': 'Dynamische Pause (Seite {index}, +{pause}ms)',
  'step.wait': 'Warte {ms}ms',
  'step.listen_and_branch': 'STT Seite {index} (Schwellenwert {threshold}%)',
  'step.reveal_on_tap': 'Nächste Seite per Tippen aufdecken',
  'step.rate': 'Wissen bewerten',

  // Step types (labels)
  'step.type.show_page': 'Seite zeigen',
  'step.type.speak_page': 'Sprechen (TTS)',
  'step.type.dynamic_pause': 'Dynamische Pause',
  'step.type.wait': 'Warten (ms)',
  'step.type.listen_and_branch': 'Sprache überprüfen (STT)',
  'step.type.reveal_on_tap': 'Per Tippen aufdecken',
  'step.type.rate': 'Wissen bewerten',

  // Study Filters
  'filter.new_review': 'Neue + Fällige zur Überprüfung',
  'filter.new': 'Neu',
  'filter.review': 'Wiederholung',
  'filter.all': 'Alle',

  // SRS Badge translation
  'srs.badge.new': 'Neu',
  'srs.badge.learning': 'Lernen',
  'srs.badge.review': 'Wiederholung',
  'srs.badge.mastered': 'Gemeistert',
  'srs.badge.now': 'Jetzt',

  // Additional Filters
  'filter.learning': 'Lernen',
  'filter.mastered': 'Gemeistert',

  // Tooltips in Browse
  'browse.tooltip.mastery': 'Meisterschaft: {percent}%',
  'browse.tooltip.repetitions': 'Wiederholungen: {count}',
  'browse.tooltip.review_in': 'Überprüfung in: {time}',
  'browse.delete_card': 'Karteikarte löschen',

  // Danger zone in App Settings
  'app_settings.danger_zone': 'Gefahrenzone',

  // Update notification
  'update.updated': 'App aktualisiert',
  'update.show_changes': 'Änderungen anzeigen',
  'update.ok': 'OK',

  // Language names
  'lang.pl-PL': 'Polnisch',
  'lang.en-US': 'Englisch',
  'lang.es-ES': 'Spanisch',
  'lang.de-DE': 'Deutsch',
  'lang.fr-FR': 'Französisch',
  'lang.it-IT': 'Italienisch',
  'lang.pt-PT': 'Portugiesisch',
  'lang.ru-RU': 'Russisch',
  'lang.ja-JP': 'Japanisch',
  'lang.zh-CN': 'Chinesisch',
};
