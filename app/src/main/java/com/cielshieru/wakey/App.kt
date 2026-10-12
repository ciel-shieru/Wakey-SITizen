package com.cielshieru.wakey

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.widget.Toast
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExperimentalConfigurationApi
import androidx.work.ExistingPeriodicWorkPolicy
import com.cielshieru.wakey.android.NotificationService
import com.cielshieru.wakey.domain.DebugLogger
import com.cielshieru.wakey.usecase.SyncUseCase
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var syncUseCase: SyncUseCase

    @Inject
    lateinit var debugLogger: DebugLogger

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    @OptIn(ExperimentalConfigurationApi::class)
    private val _workManagerConfiguration: Configuration by lazy {
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMarkingJobsAsImportantWhileForeground(false)
            .build()
    }

    private var isForeground = false

    private val foregroundListener = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityResumed(activity: Activity) {
            synchronized(this@App) {
                if (!isForeground) {
                    isForeground = true
                    try {
                        debugLogger.logFullConfigFromDataStore(dataStore)
                    } catch (e: Exception) {
                    }
                }
            }
        }
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(foregroundListener)
        NotificationService.initChannels(this)
        syncUseCase.syncImmediately()
        syncUseCase.schedulePeriodicSync(ExistingPeriodicWorkPolicy.REPLACE)
        Toast.makeText(this, "Sync triggered: App launch (immediate)", Toast.LENGTH_SHORT).show()
    }

    override val workManagerConfiguration: Configuration
        get() = _workManagerConfiguration
}
