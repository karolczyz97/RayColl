package com.raycoll.app.di

import com.raycoll.app.core.Clock
import com.raycoll.app.core.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
}

@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides
    @Singleton
    fun provideClock(): Clock = SystemClock()
}

@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {
    @Provides
    @Singleton
    fun provideDefaultDispatcher() = kotlinx.coroutines.Dispatchers.Default

    @Provides
    @Singleton
    fun provideIoDispatcher() = kotlinx.coroutines.Dispatchers.IO

    @Provides
    @Singleton
    fun provideMainDispatcher() = kotlinx.coroutines.Dispatchers.Main
}
