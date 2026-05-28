export const es: Record<string, string> = {
  // Common
  'app.title': 'RayColl',
  'btn.cancel': 'Cancelar',
  'btn.save': 'Guardar',
  'btn.add': 'Añadir',
  'btn.delete': 'Eliminar',
  'btn.back': 'Atrás',
  'btn.login': 'Iniciar sesión con Google',
  'btn.logout': 'Cerrar sesión',
  'auth.local': 'Modo local',
  'auth.welcome': 'Bienvenido, {name}',
  'dialog.delete.title': '¿Estás seguro?',
  'dialog.delete.desc': 'Esta acción no se puede deshacer.',

  // Dashboard
  'dashboard.no_groups': 'No se encontraron barajas. Haz clic en + para añadir una.',
  'dashboard.create_title': 'Crear baraja',
  'dashboard.name_label': 'Nombre de la baraja',
  'dashboard.langs_label': 'Idiomas de las páginas (ej. en-US, pl-PL)',
  'dashboard.pages_label': 'Nombres de las páginas (ej. Palabra, Traducción)',
  'dashboard.due_count': 'Pendiente: {count}',
  'dashboard.mastery': 'Dominado: {percent}%',
  'dashboard.tooltip.study': 'Estudiar',
  'dashboard.tooltip.browse': 'Ver tarjetas',
  'dashboard.tooltip.settings': 'Ajustes de baraja',
  'dashboard.cards_count': '{count} tarjetas',

  // Study
  'study.finished': '¡Sesión de repaso terminada! 🎉',
  'study.finished_desc': 'Todas las tarjetas programadas para hoy han sido repasadas.',
  'study.no_due': 'No hay tarjetas pendientes de repaso.',
  'study.status.listening': 'Escuchando...',
  'study.status.speaking': 'Hablando...',
  'study.match_percent': 'Coincidencia: {percent}%',
  'study.rating.1': 'Otra vez',
  'study.rating.2': 'Difícil',
  'study.rating.3': 'Bien',
  'study.rating.4': 'Fácil',
  'study.restart_session': 'Reiniciar sesión',
  'study.restart_failed': 'Repetir solo falladas',
  'study.group_not_found': 'Baraja no encontrada',
  'study.bravo': '¡Bravo! 🎉',
  'study.back_to_panel': 'Volver al panel',
  'study.recognized': 'Reconocido:',
  'study.tap_to_reveal': 'Toca para revelar',

  // Browse
  'browse.search_placeholder': 'Buscar tarjetas...',
  'browse.add_card': 'Añadir tarjeta',
  'browse.edit_card': 'Editar tarjeta',
  'browse.no_cards': 'No se encontraron tarjetas.',
  'browse.min_filled_pages': 'Rellena al menos 2 campos para guardar esta tarjeta.',

  // Import
  'import.title': 'Importar tarjetas',
  'import.csv_label': 'Pegue el texto CSV / TSV aquí',
  'import.separator': 'Separador',
  'import.preview': 'Vista previa',
  'import.btn': 'Importar {count} tarjetas',
  'import.success': '¡Se importaron con éxito {count} tarjetas!',
  'import.name_placeholder':
    'Pegue datos — el separador y número de páginas se detectarán automáticamente',
  'import.upload_btn': 'Subir archivo (.csv, .txt, .json)',
  'import.sep.tab': 'Tabulador',
  'import.sep.semicolon': 'Punto y coma (;)',
  'import.sep.comma': 'Coma (,)',
  'import.sep.pipe': 'Barra (|)',
  'import.sep.custom': 'Personalizado',
  'import.err.too_many_lines': 'Entrada truncada a 500 líneas.',
  'import.pages_count': 'Número de páginas:',
  'import.page_label': 'Página {index}',
  'import.import_create_btn': 'Importar y crear baraja',
  'import.preview_rows': 'Vista previa ({count} filas)',

  // Stats
  'stats.title': 'Estadísticas de estudio',
  'stats.decks_title': 'Mazos',
  'stats.cards_title': 'Tarjetas',
  'stats.heatmap_title': 'Actividad en los últimos 140 días',
  'stats.progress_title': 'Progreso de la baraja',
  'stats.streak': 'Racha de días',
  'stats.active_days': 'Días activos',
  'stats.due_cards': 'Pendientes',
  'stats.deck_progress': 'Progreso de barajas',
  'stats.overall_progress': 'Progreso general',
  'stats.cards_count': '{count} tarjetas',
  'stats.day.mon': 'Lun',
  'stats.day.wed': 'Mié',
  'stats.day.fri': 'Vie',
  'stats.day.sun': 'Dom',

  // Settings
  'settings.title': 'Ajustes de baraja: {name}',
  'settings.modes_title': 'Modo de estudio',
  'settings.steps_title': 'Secuencia de pasos',
  'settings.delete_btn': 'Eliminar baraja',
  'settings.delete_desc':
    '¿Estás seguro de que quieres eliminar esta baraja? Esto eliminará todas sus tarjetas de forma permanente.',
  'settings.rename_label': 'Nombre de la baraja',
  'settings.pages_config': 'Configuración de páginas',
  'settings.active_mode': 'Modo de estudio activo',
  'settings.study_scope': 'Alcance del estudio',
  'settings.which_cards': 'Qué tarjetas estudiar',
  'settings.mode_steps': 'Pasos del Modo "{name}"',
  'settings.add_step_btn': 'Añadir paso',
  'settings.create_mode_btn': 'Crear nuevo modo',
  'settings.new_mode_name': 'Nombre del modo',
  'settings.save_mode_btn': 'Guardar modo',
  'settings.dialog.add_step.title': 'Añadir paso',
  'settings.dialog.add_step.type': 'Tipo',
  'settings.dialog.add_step.page_idx': 'Índice de página (empieza por 0)',
  'settings.dialog.add_step.time': 'Tiempo (ms)',
  'settings.dialog.add_step.threshold': 'Umbral (%)',
  'settings.dialog.delete.confirm_text':
    'Para confirmar la eliminación de la baraja "{name}", escriba DELETE abajo:',

  // App Settings
  'app_settings.title': 'Ajustes de la aplicación',
  'app_settings.theme': 'Tema',
  'app_settings.theme.system': 'Sistema',
  'app_settings.theme.light': 'Claro',
  'app_settings.theme.dark': 'Oscuro',
  'app_settings.tts_rate': 'Velocidad de voz de TTS',
  'app_settings.export_import': 'Exportar / Importar datos',
  'app_settings.export_btn': 'Exportar estado a JSON',
  'app_settings.import_placeholder': 'Pegue el JSON exportado aquí...',
  'app_settings.import_btn': 'Importar estado',
  'app_settings.reset_btn': 'Restablecer aplicación',
  'app_settings.reset_confirm':
    '¿Estás seguro de que deseas restablecer la aplicación? Se borrarán todas las barajas personalizadas y las estadísticas.',
  'app_settings.lang': 'Idioma',
  'app_settings.dynamic_colors.title': 'Colores dinámicos del sistema',
  'app_settings.dynamic_colors.desc':
    'Extraer colores de acento y del sistema directamente del dispositivo (Android 12+ / Material You).',
  'app_settings.dynamic_colors.enabled': 'Activado',
  'app_settings.dynamic_colors.disabled': 'Desactivado',

  // Mode names
  'mode.classic.name': 'Clásico',
  'mode.listen-speak.name': 'Audio',
  'mode.m1.name': 'Clásico (mostrar + responder)',
  'mode.m2.name': 'Escuchar + hablar',
  'mode.m3.name': 'Solo mostrar',

  // Steps
  'step.show_page': 'Mostrar página {index}',
  'step.speak_page': 'TTS página {index} (+{pause}ms)',
  'step.dynamic_pause': 'Pausa dinámica (pág. {index}, +{pause}ms)',
  'step.wait': 'Esperar {ms}ms',
  'step.listen_and_branch': 'STT página {index} (umbral {threshold}%)',
  'step.rate_knowledge': 'Evaluar conocimiento',

  // Step types (labels)
  'step.type.show_page': 'Mostrar página',
  'step.type.speak_page': 'Hablar (TTS)',
  'step.type.dynamic_pause': 'Pausa dinámica',
  'step.type.wait': 'Esperar (ms)',
  'step.type.listen_and_branch': 'Verificar voz (STT)',
  'step.type.rate_knowledge': 'Evaluar conocimiento (mostrar botones de calificación)',

  // Study Filters
  'filter.new_review': 'Nuevas + pendientes de repaso',
  'filter.new': 'Nueva',
  'filter.review': 'Repaso',
  'filter.all': 'Todas',

  // SRS Badge translation
  'srs.badge.new': 'Nueva',
  'srs.badge.learning': 'Aprendiendo',
  'srs.badge.review': 'Repaso',
  'srs.badge.mastered': 'Dominada',
  'srs.badge.now': 'Ahora',

  // Additional Filters
  'filter.learning': 'Aprendiendo',
  'filter.mastered': 'Dominada',

  // Tooltips in Browse
  'browse.tooltip.mastery': 'Dominio: {percent}%',
  'browse.tooltip.repetitions': 'Repeticiones: {count}',
  'browse.tooltip.review_in': 'Repaso en: {time}',
  'browse.delete_card': 'Eliminar tarjeta',

  // Danger zone in App Settings
  'app_settings.danger_zone': 'Zona de peligro',

  // Language names
  'lang.pl-PL': 'Polaco',
  'lang.en-US': 'Inglés',
  'lang.es-ES': 'Español',
  'lang.de-DE': 'Alemán',
  'lang.fr-FR': 'Francés',
  'lang.it-IT': 'Italiano',
  'lang.pt-PT': 'Portugués',
  'lang.ru-RU': 'Ruso',
  'lang.ja-JP': 'Japonés',
  'lang.zh-CN': 'Chino',
};
