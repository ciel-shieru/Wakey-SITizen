package com.cielshieru.wakey.data.store

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.cielshieru.wakey.data.store.AlarmDismissalStore.clearDismissalForDay
import com.cielshieru.wakey.data.store.AlarmDismissalStore.clearDismissedAlarmDaysBefore
import com.cielshieru.wakey.data.store.AlarmDismissalStore.dismissAlarmsForDay
import com.cielshieru.wakey.data.store.AlarmDismissalStore.dismissedAlarmDaysFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class AlarmDismissalStoreTest {

    @Test
    fun `dismissed day remains persisted across reads`() = runTest {
        val file = File("/tmp/test_alarm_dismissal_${System.nanoTime()}.preferences_pb")
        val store = PreferenceDataStoreFactory.create(produceFile = { file })
        val day = LocalDate.of(2026, 10, 20)

        store.dismissAlarmsForDay(day)

        assertEquals(setOf(day), store.data.dismissedAlarmDaysFlow().first())
        file.delete()
    }

    @Test
    fun `one dismissed day can be cleared without affecting other days`() = runTest {
        val file = File("/tmp/test_alarm_dismissal_clear_" + java.util.UUID.randomUUID() + ".preferences_pb")
        val store = PreferenceDataStoreFactory.create(produceFile = { file })
        val today = LocalDate.of(2026, 10, 21)
        val tomorrow = today.plusDays(1)
        store.dismissAlarmsForDay(today)
        store.dismissAlarmsForDay(tomorrow)

        store.clearDismissalForDay(today)

        assertEquals(setOf(tomorrow), store.data.dismissedAlarmDaysFlow().first())
        file.delete()
    }

    @Test
    fun `old dismissed days are pruned without clearing today`() = runTest {
        val file = File("/tmp/test_alarm_dismissal_prune_${System.nanoTime()}.preferences_pb")
        val store = PreferenceDataStoreFactory.create(produceFile = { file })
        val yesterday = LocalDate.of(2026, 10, 19)
        val today = yesterday.plusDays(1)
        store.dismissAlarmsForDay(yesterday)
        store.dismissAlarmsForDay(today)

        store.clearDismissedAlarmDaysBefore(today)

        assertEquals(setOf(today), store.data.dismissedAlarmDaysFlow().first())
        assertTrue(store.data.dismissedAlarmDaysFlow().first().contains(today))
        assertFalse(store.data.dismissedAlarmDaysFlow().first().contains(yesterday))
        file.delete()
    }
}
