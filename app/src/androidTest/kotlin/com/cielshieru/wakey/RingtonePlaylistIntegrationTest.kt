package com.cielshieru.wakey

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cielshieru.wakey.domain.AlarmComputer
import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.AlarmSchedule
import com.cielshieru.wakey.domain.model.EventKind
import com.cielshieru.wakey.domain.model.RingtonePlaylist
import com.cielshieru.wakey.domain.model.TimetableEvent
import com.cielshieru.wakey.domain.port.Clock
import com.cielshieru.wakey.domain.port.ZoneProvider
import com.cielshieru.wakey.fakes.FakeAlarmRepository
import com.cielshieru.wakey.ui.ringtone.RingtonePickerEvent
import com.cielshieru.wakey.ui.ringtone.RingtonePickerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RingtonePlaylistIntegrationTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeAlarmRepository: FakeAlarmRepository
    private lateinit var viewModel: RingtonePickerViewModel
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        fakeAlarmRepository = FakeAlarmRepository()
        viewModel = RingtonePickerViewModel(
            alarmRepository = fakeAlarmRepository,
            ringtoneNameResolver = StubRingtoneNameResolver(context)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `save playlist then compute schedule verifies ringtoneUri on alarms`() = runTest {
        val ringtones = listOf(
            "content://ringtone/gentle",
            "content://ringtone/loud",
            "content://ringtone/urgent"
        )

        ringtones.forEach { viewModel.addRingtone(it) }
        viewModel.toggleShuffle(false)
        viewModel.save()

        val savedConfig = fakeAlarmRepository.savedConfig
        assertNotNull(savedConfig)
        assertEquals(3, savedConfig!!.ringtonePlaylist.ringtoneUris.size)
        assertEquals(ringtones[0], savedConfig.ringtonePlaylist.ringtoneUris[0])
        assertEquals(ringtones[1], savedConfig.ringtonePlaylist.ringtoneUris[1])
        assertEquals(ringtones[2], savedConfig.ringtonePlaylist.ringtoneUris[2])
        assertFalse(savedConfig.ringtonePlaylist.shuffleEnabled)

        val clock = object : Clock {
            override fun now(): Instant = Instant.parse("2026-10-19T06:00:00Z")
        }
        val zoneProvider = object : ZoneProvider {
            override val zone: ZoneId = ZoneId.of("Asia/Singapore")
        }

        val events = listOf(
            TimetableEvent(
                title = "Lecture",
                dtstart = LocalDate.of(2026, 10, 19).atStartOfDay(zoneProvider.zone).toInstant(),
                kind = EventKind.CAMPUS,
                eventType = EventKind.CAMPUS
            )
        )

        val schedules = AlarmComputer.computeSchedule(
            events = events,
            config = savedConfig,
            clock = clock,
            zoneProvider = zoneProvider
        )

        assertEquals(1, schedules.size)
        val daySchedules = schedules[0]
        assertEquals(6, daySchedules.alarms.size)

        for ((index, alarm) in daySchedules.alarms.withIndex()) {
            val expectedRingtone = ringtones[index % ringtones.size]
            assertEquals(
                "Alarm index $index should have ringtone $expectedRingtone",
                expectedRingtone,
                alarm.ringtoneUri
            )
        }
    }

    @Test
    fun `shuffle enabled produces deterministic ringtone assignment`() = runTest {
        val ringtones = listOf(
            "content://ringtone/a",
            "content://ringtone/b",
            "content://ringtone/c"
        )

        ringtones.forEach { viewModel.addRingtone(it) }
        viewModel.toggleShuffle(true)
        viewModel.save()

        val savedConfig = fakeAlarmRepository.savedConfig!!
        assertTrue(savedConfig.ringtonePlaylist.shuffleEnabled)

        val clock = object : Clock {
            override fun now(): Instant = Instant.parse("2026-10-19T06:00:00Z")
        }
        val zoneProvider = object : ZoneProvider {
            override val zone: ZoneId = ZoneId.of("Asia/Singapore")
        }

        val events = listOf(
            TimetableEvent(
                title = "Tutorial",
                dtstart = LocalDate.of(2026, 10, 19).atStartOfDay(zoneProvider.zone).toInstant(),
                kind = EventKind.ONLINE,
                eventType = EventKind.ONLINE
            )
        )

        val schedules1 = AlarmComputer.computeSchedule(
            events = events,
            config = savedConfig,
            clock = clock,
            zoneProvider = zoneProvider
        )

        val schedules2 = AlarmComputer.computeSchedule(
            events = events,
            config = savedConfig,
            clock = clock,
            zoneProvider = zoneProvider
        )

        for (i in schedules1[0].alarms.indices) {
            assertEquals(
                "Shuffle assignment must be deterministic for index $i",
                schedules1[0].alarms[i].ringtoneUri,
                schedules2[0].alarms[i].ringtoneUri
            )
        }
    }

    @Test
    fun `empty playlist produces null ringtoneUri on all alarms`() = runTest {
        viewModel.save()

        val savedConfig = fakeAlarmRepository.savedConfig!!
        assertTrue(savedConfig.ringtonePlaylist.ringtoneUris.isEmpty())

        val clock = object : Clock {
            override fun now(): Instant = Instant.parse("2026-10-19T06:00:00Z")
        }
        val zoneProvider = object : ZoneProvider {
            override val zone: ZoneId = ZoneId.of("Asia/Singapore")
        }

        val events = listOf(
            TimetableEvent(
                title = "Lab",
                dtstart = LocalDate.of(2026, 10, 19).atStartOfDay(zoneProvider.zone).toInstant(),
                kind = EventKind.CAMPUS,
                eventType = EventKind.CAMPUS
            )
        )

        val schedules = AlarmComputer.computeSchedule(
            events = events,
            config = savedConfig,
            clock = clock,
            zoneProvider = zoneProvider
        )

        for (alarm in schedules[0].alarms) {
            assertNull("Empty playlist should produce null ringtoneUri", alarm.ringtoneUri)
        }
    }

    @Test
    fun `reorder ringtones then compute schedule verifies reordered ringtoneUri`() = runTest {
        val originalOrder = listOf(
            "content://ringtone/first",
            "content://ringtone/second",
            "content://ringtone/third"
        )

        originalOrder.forEach { viewModel.addRingtone(it) }
        viewModel.reorderRingtones(0, 2)
        viewModel.save()

        val savedConfig = fakeAlarmRepository.savedConfig!!
        assertEquals(3, savedConfig.ringtonePlaylist.ringtoneUris.size)
        assertEquals("content://ringtone/second", savedConfig.ringtonePlaylist.ringtoneUris[0])
        assertEquals("content://ringtone/third", savedConfig.ringtonePlaylist.ringtoneUris[1])
        assertEquals("content://ringtone/first", savedConfig.ringtonePlaylist.ringtoneUris[2])

        val clock = object : Clock {
            override fun now(): Instant = Instant.parse("2026-10-19T06:00:00Z")
        }
        val zoneProvider = object : ZoneProvider {
            override val zone: ZoneId = ZoneId.of("Asia/Singapore")
        }

        val events = listOf(
            TimetableEvent(
                title = "Seminar",
                dtstart = LocalDate.of(2026, 10, 19).atStartOfDay(zoneProvider.zone).toInstant(),
                kind = EventKind.ONLINE,
                eventType = EventKind.ONLINE
            )
        )

        val schedules = AlarmComputer.computeSchedule(
            events = events,
            config = savedConfig,
            clock = clock,
            zoneProvider = zoneProvider
        )

        assertEquals("content://ringtone/second", schedules[0].alarms[0].ringtoneUri)
        assertEquals("content://ringtone/third", schedules[0].alarms[1].ringtoneUri)
        assertEquals("content://ringtone/first", schedules[0].alarms[2].ringtoneUri)
    }

    @Test
    fun `save emits Saved event and config flows updated value`() = runTest {
        viewModel.addRingtone("content://ringtone/new")
        viewModel.save()

        val event = viewModel.event.first()
        assertNotNull(event)
        assertTrue(event is RingtonePickerEvent.Saved)

        val configFromFlow = fakeAlarmRepository.config.first()
        assertEquals(1, configFromFlow.ringtonePlaylist.ringtoneUris.size)
        assertEquals("content://ringtone/new", configFromFlow.ringtonePlaylist.ringtoneUris[0])
    }
}

class StubRingtoneNameResolver(private val ctx: Context?) : com.cielshieru.wakey.data.RingtoneNameResolver(ctx) {
    override fun resolveName(uriString: String): String {
        return uriString.substringAfterLast("/")
    }

    override fun resolveName(uri: android.net.Uri): String {
        return uri.lastPathSegment ?: "Unknown"
    }
}
