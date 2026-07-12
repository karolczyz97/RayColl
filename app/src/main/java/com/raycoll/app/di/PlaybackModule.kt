package com.raycoll.app.di

import com.raycoll.app.domain.session.SessionDeps
import com.raycoll.app.playback.SessionDepsAdapter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {
    @Provides @Singleton
    fun provideSessionDeps(adapter: SessionDepsAdapter): SessionDeps = adapter
}
