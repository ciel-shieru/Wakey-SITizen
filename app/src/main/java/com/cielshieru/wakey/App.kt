package com.cielshieru.wakey

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.cielshieru.wakey.android.NotificationService
import com.cielshieru.wakey.usecase.SyncUseCase
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var syncUseCase: SyncUseCase

    private val _workManagerConfiguration: Configuration by lazy {
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        // ADR-0008: create the notification channels up front (idempotent).
        NotificationService.initChannels(this)
        syncUseCase.syncImmediately()
        syncUseCase.schedulePeriodicSync()
    }

    override val workManagerConfiguration: Configuration
        get() = _workManagerConfiguration
}
