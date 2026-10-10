package com.cielshieru.wakey

import android.app.Application
import android.widget.Toast
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExperimentalConfigurationApi
import androidx.work.ExistingPeriodicWorkPolicy
import com.cielshieru.wakey.android.NotificationService
import com.cielshieru.wakey.usecase.SyncUseCase
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var syncUseCase: SyncUseCase

    @OptIn(ExperimentalConfigurationApi::class)
    private val _workManagerConfiguration: Configuration by lazy {
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMarkingJobsAsImportantWhileForeground(false)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        NotificationService.initChannels(this)
        syncUseCase.syncImmediately()
        syncUseCase.schedulePeriodicSync(ExistingPeriodicWorkPolicy.REPLACE)
        Toast.makeText(this, "Sync triggered: App launch (immediate)", Toast.LENGTH_SHORT).show()
    }

    override val workManagerConfiguration: Configuration
        get() = _workManagerConfiguration
}
