package com.raycoll.app.di

import com.raycoll.app.domain.session.SessionDeps
import com.raycoll.app.playback.SessionDepsAdapter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionModule {
    /** Silnik sesji widzi mowę i zapis ocen przez interfejs — w testach podmieniany na atrapę. */
    @Binds
    abstract fun bindSessionDeps(adapter: SessionDepsAdapter): SessionDeps
}
