package com.cielshieru.wakey.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.cielshieru.wakey.data.store.AlarmConfigStore.configFlow
import com.cielshieru.wakey.data.store.AlarmConfigStore.explicitNumericConfigFieldsFlow
import com.cielshieru.wakey.domain.model.NumericConfigField
import com.cielshieru.wakey.data.store.AlarmConfigStore.saveConfig
import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.RingtonePlaylist
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AlarmConfigStoreRingtoneTest {

    @Test
    fun `save and load ringtone playlist round-trip`() = runTest {
        val testFile = File("/tmp/test_ringtone_playlist_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { testFile })

        val uris = listOf("content://ringtone1", "content://ringtone2", "content://ringtone3")
        val config = AlarmConfig(
            ringtonePlaylist = RingtonePlaylist(uris, shuffleEnabled = true)
        )

        dataStore.saveConfig(config)

        val loaded = dataStore.data.configFlow().first()

        assertEquals(3, loaded.ringtonePlaylist.size)
        assertEquals(uris, loaded.ringtonePlaylist.ringtoneUris)
        assertTrue(loaded.ringtonePlaylist.shuffleEnabled)

        testFile.delete()
    }


    @Test
    fun `explicitly saved default numeric value remains configured`() = runTest {
        val testFile = File("/tmp/test_explicit_default_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { testFile })

        dataStore.saveConfig(
            AlarmConfig(),
            explicitlyConfiguredNumericFields = setOf(NumericConfigField.ONLINE_OFFSET)
        )

        val loaded = dataStore.data.configFlow().first()
        val explicitFields = dataStore.data.explicitNumericConfigFieldsFlow().first()

        assertEquals(AlarmConfig.DEFAULT_ONLINE_OFFSET, loaded.onlineOffsetMinutes)
        assertEquals(setOf(NumericConfigField.ONLINE_OFFSET), explicitFields)
        testFile.delete()
    }

    @Test
    fun `blank numeric settings are not persisted as explicit choices`() = runTest {
        val testFile = File("/tmp/test_unconfigured_numeric_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { testFile })

        dataStore.saveConfig(
            AlarmConfig(),
            explicitlyConfiguredNumericFields = emptySet()
        )

        val loaded = dataStore.data.configFlow().first()
        val explicitFields = dataStore.data.explicitNumericConfigFieldsFlow().first()

        assertEquals(AlarmConfig.DEFAULT_ONLINE_OFFSET, loaded.onlineOffsetMinutes)
        assertTrue(explicitFields.isEmpty())
        testFile.delete()
    }

    @Test
    fun `saving ringtone changes preserves implicit numeric defaults`() = runTest {
        val testFile = File("/tmp/test_ringtone_preserves_defaults_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { testFile })

        dataStore.saveConfig(AlarmConfig(), explicitlyConfiguredNumericFields = emptySet())
        val original = dataStore.data.configFlow().first()
        dataStore.saveConfig(original.copy(ringtonePlaylist = RingtonePlaylist(listOf("content://ringtone"))))

        assertTrue(dataStore.data.explicitNumericConfigFieldsFlow().first().isEmpty())
        testFile.delete()
    }

    @Test
    fun `legacy numeric defaults remain implicit while non-default values remain configured`() = runTest {
        val testFile = File("/tmp/test_legacy_numeric_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { testFile })

        dataStore.edit { prefs ->
            // Prior versions wrote every setting value whenever settings were saved.
            prefs[intPreferencesKey("online_offset_minutes")] = AlarmConfig.DEFAULT_ONLINE_OFFSET
            prefs[intPreferencesKey("campus_offset_minutes")] = 120
        }

        val explicitFields = dataStore.data.explicitNumericConfigFieldsFlow().first()

        assertEquals(setOf(NumericConfigField.CAMPUS_OFFSET), explicitFields)
        testFile.delete()
    }

    @Test
    fun `save empty playlist and load returns empty`() = runTest {
        val testFile = File("/tmp/test_ringtone_empty_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { testFile })

        val config = AlarmConfig(
            ringtonePlaylist = RingtonePlaylist(emptyList(), shuffleEnabled = false)
        )

        dataStore.saveConfig(config)

        val loaded = dataStore.data.configFlow().first()

        assertTrue(loaded.ringtonePlaylist.isEmpty)
        assertFalse(loaded.ringtonePlaylist.shuffleEnabled)

        testFile.delete()
    }

    @Test
    fun `load without ringtone_playlist key returns DEFAULT`() = runTest {
        val testFile = File("/tmp/test_ringtone_default_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { testFile })

        val config = AlarmConfig()

        dataStore.saveConfig(config)

        val loaded = dataStore.data.configFlow().first()

        assertEquals(RingtonePlaylist.DEFAULT.ringtoneUris, loaded.ringtonePlaylist.ringtoneUris)
        assertEquals(RingtonePlaylist.DEFAULT.shuffleEnabled, loaded.ringtonePlaylist.shuffleEnabled)

        testFile.delete()
    }

    @Test
    fun `save with shuffle disabled and load preserves state`() = runTest {
        val testFile = File("/tmp/test_ringtone_noshuffle_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { testFile })

        val uris = listOf("content://a", "content://b")
        val config = AlarmConfig(
            ringtonePlaylist = RingtonePlaylist(uris, shuffleEnabled = false)
        )

        dataStore.saveConfig(config)

        val loaded = dataStore.data.configFlow().first()

        assertEquals(2, loaded.ringtonePlaylist.size)
        assertFalse(loaded.ringtonePlaylist.shuffleEnabled)

        testFile.delete()
    }

    @Test
    fun `save with single ringtone and load preserves it`() = runTest {
        val testFile = File("/tmp/test_ringtone_single_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { testFile })

        val config = AlarmConfig(
            ringtonePlaylist = RingtonePlaylist(listOf("content://only_one"))
        )

        dataStore.saveConfig(config)

        val loaded = dataStore.data.configFlow().first()

        assertEquals(1, loaded.ringtonePlaylist.size)
        assertEquals("content://only_one", loaded.ringtonePlaylist.ringtoneUris[0])

        testFile.delete()
    }

    @Test
    fun `save with max ringtones and load preserves all`() = runTest {
        val testFile = File("/tmp/test_ringtone_max_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { testFile })

        val uris = (1..20).map { "content://ringtone$it" }
        val config = AlarmConfig(
            ringtonePlaylist = RingtonePlaylist(uris, shuffleEnabled = true)
        )

        dataStore.saveConfig(config)

        val loaded = dataStore.data.configFlow().first()

        assertEquals(20, loaded.ringtonePlaylist.size)
        assertEquals(uris, loaded.ringtonePlaylist.ringtoneUris)
        assertTrue(loaded.ringtonePlaylist.shuffleEnabled)

        testFile.delete()
    }

    @Test
    fun `other config fields are preserved when saving ringtone playlist`() = runTest {
        val testFile = File("/tmp/test_ringtone_otherfields_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { testFile })

        val uris = listOf("content://ringtone1")
        val config = AlarmConfig(
            onlineOffsetMinutes = 120,
            maxRepeats = 10,
            ringtonePlaylist = RingtonePlaylist(uris)
        )

        dataStore.saveConfig(config)

        val loaded = dataStore.data.configFlow().first()

        assertEquals(120, loaded.onlineOffsetMinutes)
        assertEquals(10, loaded.maxRepeats)
        assertEquals(1, loaded.ringtonePlaylist.size)
        assertEquals("content://ringtone1", loaded.ringtonePlaylist.ringtoneUris[0])

        testFile.delete()
    }

    @Test
    fun `malformed ringtone_playlist value returns DEFAULT`() = runTest {
        val testFile = File("/tmp/test_ringtone_malformed_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { testFile })

        dataStore.edit { prefs ->
            prefs[androidx.datastore.preferences.core.stringPreferencesKey("ringtone_playlist")] = "not valid json {{{"
        }

        val loaded = dataStore.data.configFlow().first()

        assertEquals(RingtonePlaylist.DEFAULT.ringtoneUris, loaded.ringtonePlaylist.ringtoneUris)

        testFile.delete()
    }
}
