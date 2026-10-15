package com.raycoll.app.di

import android.util.Log
import com.raycoll.app.core.Clock
import com.raycoll.app.core.WallClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Zakres korutyn żyjący tyle co proces — praca, która nie może zginąć z ekranem (sync, zapis ocen). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** Dispatcher do obliczeń CPU (import, kopie zapasowe, modele ekranów) — w testach podmieniany na testowy. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            // nieobsłużony wyjątek pracy w tle (bootstrap, sync) nie zabija procesu, a przy starcie powtarzałby się w pętli
            CoroutineExceptionHandler { _, e -> Log.e("AppScope", "Unhandled background failure", e) }
    )

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    fun provideClock(): Clock = WallClock
}
