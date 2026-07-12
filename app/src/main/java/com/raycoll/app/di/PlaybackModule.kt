package com.raycoll.app.di

import android.content.Context
import com.raycoll.app.playback.AudioFocusManager
import com.raycoll.app.playback.HapticAndSoundPlayer
import com.raycoll.app.playback.SttRecognizer
import com.raycoll.app.playback.SttTranscriptAccumulator
import com.raycoll.app.playback.SttSessionTimers
import com.raycoll.app.playback.StudySessionHolder
import com.raycoll.app.playback.TtsPlayer
import com.raycoll.app.playback.SessionDepsAdapter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.raycoll.app.di.ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {
    @Provides @Singleton fun provideTtsPlayer(@ApplicationContext ctx: Context) = TtsPlayer(ctx)
    @Provides @Singleton fun provideSttRecognizer(@ApplicationContext ctx: Context) = SttRecognizer(ctx)
    @Provides @Singleton fun provideHapticAndSoundPlayer(@ApplicationContext ctx: Context) = HapticAndSoundPlayer(ctx)
    @Provides @Singleton fun provideAudioFocusManager(@ApplicationContext ctx: Context) = AudioFocusManager(ctx)
    @Provides @Singleton fun provideStudySessionHolder() = StudySessionHolder()
    @Provides @Singleton fun provideSessionDepsAdapter(
        tts: TtsPlayer,
        stt: SttRecognizer,
        haptic: HapticAndSoundPlayer,
        deckRepository: com.raycoll.app.data.repository.DeckRepository,
        @ApplicationScope appScope: kotlinx.coroutines.CoroutineScope,
    ) = SessionDepsAdapter(tts, stt, haptic, deckRepository, appScope)
}
