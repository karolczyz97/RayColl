package com.raycoll.app.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Dispatchers.Main = [dispatcher] na czas testu. Na końcu anuluje zakresy śledzonych ViewModeli,
 * zanim przywróci Main — inaczej ich korutyny wznawiałyby się na Main w trakcie podmiany.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher) : TestWatcher() {
    private val viewModels = mutableListOf<ViewModel>()

    fun <T : ViewModel> track(viewModel: T): T = viewModel.also { viewModels += it }

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        try {
            val jobs = viewModels.map { it.viewModelScope.coroutineContext.job }
            jobs.forEach { it.cancel() }
            // anulowane korutyny kończą się na dispatcherze testu — przy Standard trzeba go przepompować
            dispatcher.scheduler.advanceUntilIdle()
            runBlocking { jobs.joinAll() }
        } finally {
            viewModels.clear()
            Dispatchers.resetMain()
        }
    }
}
