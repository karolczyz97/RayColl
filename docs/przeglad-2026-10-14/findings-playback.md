# Findings: playback/ + MainActivity, RayCollApplication, ui/study/StudyViewModel.kt, ui/main/MainViewModel.kt, ui/navigation/* (paths relative to app/src/main/java/com/raycoll/app/)

P1 BUG ui/study/StudyViewModel.kt:257-267 — onAppBackground sets pausedByAppBackground and pauses even if user already paused manually → onAppForeground resumes a manually paused session. (VERIFIED by orchestrator)
P2 BUG MainActivity.kt:29 — enableEdgeToEdge() default SystemBarStyle.auto follows system theme; in-app forced dark on light system → dark bar icons on dark bg. Also window background / no setDefaultNightMode (themes.xml).
P3 BUG playback/StudySessionHolder.kt:55 — startForegroundService after suspensions; app backgrounded meanwhile → ForegroundServiceStartNotAllowedException on API31+ (same in restartWith).
P4 playback/StudySessionService.kt:94-121 — onCreate always startForeground before knowing session exists; sticky restart after process death shows then stops; may hit FGS restriction.
P5 playback/SttRecognizer.kt:36-149 — listen() ~110-line lambda nested 5 deep; extract buildIntent, listener class, mapError.
P6 SttRecognizer.kt:41-148 — withTimeout + catch TimeoutCancellationException → withTimeoutOrNull.
P7 SttRecognizer.kt:29,69-85 — AtomicBoolean + Looper check + Handler.post for cleanup.
P8 SttRecognizer.kt:49-58 — recognizer==null branch dead; catch(Exception) swallows CancellationException w/o logging.
P9 SttRecognizer.kt:65,71 — EXTRA_MAX_RESULTS=3 but only firstOrNull read; `val run` shadows kotlin.run.
P10 SttRecognizer.kt:37-39,103-108 — missing mic permission returns ERROR same as engine failure; VM guesses via listeningFailed.
P11 StudySessionService.kt:123-151 — combine emits on every SessionState change; re-runs focus.onSessionPlaying, silence.start, holdCpuAwake; Triple destructuring.
P12 StudySessionService.kt:126-129 — engine.autoPausedInBackground collection redundant with combine collector (if confirmed delete SharedFlow).
P13 StudySessionService.kt:153 — playbackOf(playing, paused) always playing==!paused; `paused` really "idle"; notification text "background paused" for foreground gate case?
P14 StudySessionService.kt:171-194 — wake lock across 3 nullable fields + renewal loop; extract TimedWakeLock.
P15 StudySessionService.kt:218-228 — SDK_INT>=Q branch unnecessary with ServiceCompat.startForeground.
P16 StudySessionService.kt:94-103,230-238 — bootstrap notification + Media3 provider share id/channel undocumented.
P17 StudySessionService.kt:54 — injects concrete AudioFocusManager instead of interface.
P18 StudySessionService.kt:199-203 — controls.stop() + stopSelf() redundant.
P19 StudyViewModel.kt:121-179 — startSession does everything; casts current as? Active 3x; unchecked cast line 160.
P20 StudyViewModel.kt:136 — reviewWrites.state combined only as re-emission trigger; restarts FINISHED block (awaitIdle/getDeck) on each queue change.
P21 StudyViewModel.kt:122,124 — `return _uiState.update{}` idiom.
P22 StudyViewModel.kt:123,169 — getDueCards and filter on Main; getDeck loads whole deck on start/finish/restart.
P23 StudyViewModel.kt:133,278 — ProcessLifecycleOwner observer added inside async startSession; asymmetric.
P24 StudyViewModel.kt:52,96-99 — runsInBackground stored twice.
P25 StudyViewModel.kt:227-246 — restartSession/restartFailed double tap during awaitIdle starts two launches.
P26 RayCollApplication.kt:22-28 + StudyViewModel.kt:105-109 — two ProcessLifecycleOwner observers; single AppForeground StateFlow.
P27 ui/navigation/NavGraph.kt:75-133 — this@SharedTransitionLayout passed 11x; CompositionLocal.
P28 NavGraph.kt:75-113 — inline nav lambdas; ModeDetailRoute navigated from 3 screens; Dashboard 8 callbacks.
P29 NavGraph.kt:47,57,60 — 0.92f/0.9f magic; shrinkInPlace/shrinkAway differ by scale.
P30 NavGraph.kt:68,136-140 — up() vague name; NavHost background duplicated with Surface in MainActivity.
P31 ui/navigation/SharedTransition.kt:21-26 — deckBoundsTransform @Composable with keyless remember around constant.
P32 SharedTransition.kt:31-60 — duplicated scope lookup; stringly keys; check ExperimentalSharedTransitionApi still needed.
P33 MainActivity.kt:33 — `?: return@setContent`; splash condition no timeout.
P34 playback/AudioFocusManager.kt:36-61 — onChange callback of later request() calls silently ignored; platform type getSystemService.
P35 AudioFocusManager.kt:42-45 — comment about duck/delayed focus.
P36 playback/SessionAudioFocus.kt:24-26 — onSessionPlaying re-requests on every emission.
P37 playback/HapticAndSoundPlayer.kt:36-83 — loaded/pending sets with synchronized; failed load (status!=0) stays pending forever.
P38 HapticAndSoundPlayer.kt:59-74 — vibration ms magic; else hides GOOD.
P39 HapticAndSoundPlayer.kt:85-88 — VibrationEffect.createPredefined on API29+.
P40 playback/SilentPlayback.kt:32-42 — elvis/runCatching/also chain; Log.w drops throwable.
P41 SilentPlayback.kt:58-87 — AudioTrack leaked if write/setLoopPoints throws inside also{}; @MainThread.
P42 playback/SessionPlayer.kt:49-52 — MediaMetadata set twice; getState rebuilds each call.
P43 SessionPlayer.kt:99-107 — withCardNavigation(controls) takes controls the ctor already has.
P44 playback/TtsPlayer.kt:34-62 — speak() mixes guard/readiness/language/chunking/timeouts/reset.
P45 TtsPlayer.kt:18-22,83 — TtsResult.Error(reason: String); utterance id nanoTime.
P46 TtsPlayer.kt:44-45 — tts.language result ignored; no setAudioAttributes (USAGE_MEDIA/CONTENT_TYPE_SPEECH).
P47 playback/SessionDepsAdapter.kt:25-39 — mapping inside lambda; magic tag.
P48 playback/AudioTurn.kt:16 — `take` name.
P49 StudySessionHolder.kt:26-40 — controls var mutated; resume two paths; ActiveSession equality dedup undocumented.
P50 ui/main/MainViewModel.kt:23-24 — preferences flow throwing crashes; add catch.
