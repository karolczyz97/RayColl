package com.raycoll.app.di

import android.content.Context
import com.raycoll.app.playback.AudioFocusManager
import com.raycoll.app.playback.HapticAndSoundPlayer
import com.raycoll.app.playback.SttRecognizer
import com.raycoll.app.playback.TtsPlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// StudySessionHolder i SessionDepsAdapter mają @Singleton @Inject constructor —
// Hilt buduje je sam, bez providerów.
@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {
    @Provides @Singleton fun provideTtsPlayer(@ApplicationContext ctx: Context) = TtsPlayer(ctx)
    @Provides @Singleton fun provideSttRecognizer(@ApplicationContext ctx: Context) = SttRecognizer(ctx)
    @Provides @Singleton fun provideHapticAndSoundPlayer(@ApplicationContext ctx: Context) = HapticAndSoundPlayer(ctx)
    @Provides @Singleton fun provideAudioFocusManager(@ApplicationContext ctx: Context) = AudioFocusManager(ctx)
}
