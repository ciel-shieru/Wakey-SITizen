package com.cielshieru.wakey.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.WorkManager
import com.cielshieru.wakey.android.AlarmService
import com.cielshieru.wakey.data.calendar.SystemCalendarSource
import com.cielshieru.wakey.data.network.TimetableClient
import com.cielshieru.wakey.data.repository.AlarmRepositoryImpl
import com.cielshieru.wakey.data.repository.TimetableRepositoryImpl
import com.cielshieru.wakey.domain.port.BaseUrlProvider
import com.cielshieru.wakey.domain.port.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.cielshieru.wakey.data.store.AlarmConfigStore.configFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

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
    fun provideBaseUrlProvider(
        datastore: DataStore<Preferences>
    ): BaseUrlProvider {
        val initialUrl = runBlocking {
            datastore.data.configFlow().first().baseUrl
        }
        return object : BaseUrlProvider {
            override val baseUrl: String = initialUrl
        }
    }

    @Provides
    @Singleton
    fun provideTimetableClient(
        json: Json,
        baseUrlProvider: BaseUrlProvider
    ): TimetableClient = TimetableClient(
        baseUrlProvider = baseUrlProvider,
        json = json
    )

    @Provides
    @Singleton
    fun provideAlarmService(@ApplicationContext context: Context): AlarmService =
        AlarmService(context)

    @Provides
    @Singleton
    fun provideCalendarEventSource(
        @ApplicationContext context: Context
    ): CalendarEventSource = SystemCalendarSource(context.contentResolver)

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore

    @Provides
    @Singleton
    fun provideTimetableRepository(
        client: TimetableClient,
        datastore: DataStore<Preferences>
    ): TimetableRepository = TimetableRepositoryImpl(client, datastore)

    @Provides
    @Singleton
    fun provideAlarmRepository(
        datastore: DataStore<Preferences>
    ): AlarmRepository = AlarmRepositoryImpl(datastore)

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
