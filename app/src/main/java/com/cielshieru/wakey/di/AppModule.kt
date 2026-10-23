package com.cielshieru.wakey.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.WorkManager
import com.cielshieru.wakey.android.AlarmService
import com.cielshieru.wakey.android.PlatformPermissionChecker
import com.cielshieru.wakey.data.RingtoneNameResolver
import com.cielshieru.wakey.data.calendar.SystemCalendarSource
import com.cielshieru.wakey.data.network.TimetableClient
import com.cielshieru.wakey.data.repository.AlarmRepositoryImpl
import com.cielshieru.wakey.data.repository.TimetableRepositoryImpl
import com.cielshieru.wakey.data.repository.WorkManagerSyncStatus
import com.cielshieru.wakey.data.store.AlarmConfigStore.configFlow
import com.cielshieru.wakey.data.store.DebugConfigStore.debugConfigFlow
import com.cielshieru.wakey.domain.DebugLogger
import com.cielshieru.wakey.domain.port.DebugConfigProvider
import com.cielshieru.wakey.domain.port.RemoteServerProvider
import com.cielshieru.wakey.domain.port.*
import com.cielshieru.wakey.usecase.SyncOperations
import com.cielshieru.wakey.usecase.SyncUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.cielshieru.wakey.domain.model.AlarmConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJavaClock(): java.time.Clock = java.time.Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun provideDomainClock(javaClock: java.time.Clock): com.cielshieru.wakey.domain.port.Clock = object : com.cielshieru.wakey.domain.port.Clock {
        override fun now(): java.time.Instant = javaClock.instant()
    }

    @Provides
    @Singleton
    fun provideZoneProvider(): ZoneProvider = object : ZoneProvider {
        override val zone: ZoneId = ZoneId.systemDefault()
    }

    @Provides
    @Singleton
    fun provideRemoteServerProvider(
        datastore: DataStore<Preferences>
    ): RemoteServerProvider = object : RemoteServerProvider {
        override val remoteServerUrl: Flow<String> = datastore.data.map { prefs ->
            prefs[stringPreferencesKey("base_url")] ?: prefs[stringPreferencesKey("remote_server_url")] ?: AlarmConfig.DEFAULT_REMOTE_SERVER_URL
        }
    }

    @Provides
    @Singleton
    fun provideTimetableClient(
        remoteServerProvider: RemoteServerProvider
    ): TimetableClient = TimetableClient(remoteServerProvider)

    @Provides
    @Singleton
    fun provideDebugConfigProvider(
        datastore: DataStore<Preferences>
    ): DebugConfigProvider = object : DebugConfigProvider {
        override val debugModeEnabled: Boolean
            get() = runBlocking {
                datastore.data.debugConfigFlow().first()
            }.debugModeEnabled
    }

    @Provides
    @Singleton
    fun provideAlarmService(
        @ApplicationContext context: Context,
        debugLogger: DebugLogger
    ): AlarmService = AlarmService(context, debugLogger)

    @Provides
    @Singleton
    fun provideRingtoneNameResolver(
        @ApplicationContext context: Context
    ): RingtoneNameResolver = RingtoneNameResolver(context)

    @Provides
    @Singleton
    fun provideCalendarEventSource(
        @ApplicationContext context: Context
    ): CalendarEventSource = SystemCalendarSource(context.contentResolver)

    @Provides
    @Singleton
    fun provideDebugLogger(
        @ApplicationContext context: Context,
        debugConfigProvider: DebugConfigProvider
    ): DebugLogger = DebugLogger(context, debugConfigProvider.debugModeEnabled)

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore

    @Provides
    @Singleton
    fun provideTimetableRepository(
        client: TimetableClient,
        datastore: DataStore<Preferences>,
        calendarEventSource: CalendarEventSource
    ): TimetableRepository = TimetableRepositoryImpl(
        client,
        datastore,
        datastore.data.configFlow(),
        calendarEventSource
    )

    @Provides
    @Singleton
    fun provideAlarmRepository(
        datastore: DataStore<Preferences>,
        alarmService: AlarmService
    ): AlarmRepository = AlarmRepositoryImpl(datastore, alarmService)

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideSyncStatus(workManager: WorkManager): SyncStatus =
        WorkManagerSyncStatus(workManager)

    @Provides
    @Singleton
    fun providePermissionChecker(pc: PlatformPermissionChecker): PermissionChecker = pc

    @Provides
    @Singleton
    fun provideSyncOperations(syncUseCase: SyncUseCase): SyncOperations = syncUseCase
}
