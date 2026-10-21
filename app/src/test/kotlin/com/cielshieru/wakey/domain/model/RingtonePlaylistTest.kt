package com.cielshieru.wakey.domain.model

import org.junit.Assert.*
import org.junit.Test

class RingtonePlaylistTest {

    @Test
    fun `empty playlist returns null for any index`() {
        val playlist = RingtonePlaylist(emptyList(), false)
        assertNull(playlist.ringtoneForRepeatIndex(0))
        assertNull(playlist.ringtoneForRepeatIndex(5))
        assertNull(playlist.ringtoneForRepeatIndex(100))
    }

    @Test
    fun `single ringtone returns same ringtone for all indices`() {
        val playlist = RingtonePlaylist(listOf("uri1"), false)
        assertEquals("uri1", playlist.ringtoneForRepeatIndex(0))
        assertEquals("uri1", playlist.ringtoneForRepeatIndex(1))
        assertEquals("uri1", playlist.ringtoneForRepeatIndex(19))
    }

    @Test
    fun `multiple ringtones cycle through playlist in linear mode`() {
        val uris = listOf("uri1", "uri2", "uri3")
        val playlist = RingtonePlaylist(uris, false)

        assertEquals("uri1", playlist.ringtoneForRepeatIndex(0))
        assertEquals("uri2", playlist.ringtoneForRepeatIndex(1))
        assertEquals("uri3", playlist.ringtoneForRepeatIndex(2))
        assertEquals("uri1", playlist.ringtoneForRepeatIndex(3))
        assertEquals("uri2", playlist.ringtoneForRepeatIndex(4))
        assertEquals("uri3", playlist.ringtoneForRepeatIndex(5))
    }

    @Test
    fun `linear mode wraps around correctly`() {
        val uris = listOf("a", "b")
        val playlist = RingtonePlaylist(uris, false)

        for (i in 0..100) {
            val expected = uris[i % 2]
            assertEquals(expected, playlist.ringtoneForRepeatIndex(i))
        }
    }

    @Test
    fun `shuffle mode produces deterministic results for same index`() {
        val uris = listOf("a", "b", "c", "d", "e")
        val playlist = RingtonePlaylist(uris, true)

        val results1 = (0..19).map { playlist.ringtoneForRepeatIndex(it) }
        val results2 = (0..19).map { playlist.ringtoneForRepeatIndex(it) }

        assertEquals(results1, results2)
    }

    @Test
    fun `shuffle mode produces different result for different indices`() {
        val uris = listOf("a", "b", "c", "d", "e")
        val playlist = RingtonePlaylist(uris, true)

        val index0 = playlist.ringtoneForRepeatIndex(0)
        val index1 = playlist.ringtoneForRepeatIndex(1)

        // With 5 ringtones and deterministic shuffle, indices 0 and 1 should typically differ
        // This test verifies the shuffle actually produces variety
        val allResults = (0..4).map { playlist.ringtoneForRepeatIndex(it) }
        val uniqueCount = allResults.toSet().size
        assertTrue("Shuffle should produce variety across indices", uniqueCount > 1)
    }

    @Test
    fun `shuffle mode is deterministic for each repeat index`() {
        val uris = listOf("x", "y", "z")
        val playlist = RingtonePlaylist(uris, true)

        // Same index should always produce same ringtone
        val firstCall = playlist.ringtoneForRepeatIndex(5)
        val secondCall = playlist.ringtoneForRepeatIndex(5)
        assertEquals(firstCall, secondCall)

        // Different indices may produce different ringtones
        val index3 = playlist.ringtoneForRepeatIndex(3)
        val index6 = playlist.ringtoneForRepeatIndex(6)
        // Just verify determinism - don't assert specific values since shuffle order depends on implementation
        assertEquals(index3, playlist.ringtoneForRepeatIndex(3))
        assertEquals(index6, playlist.ringtoneForRepeatIndex(6))
    }

    @Test
    fun `shuffle mode plays every ringtone once within each cycle`() {
        val uris = listOf("a", "b", "c")
        val playlist = RingtonePlaylist(uris, shuffleEnabled = true)

        repeat(5) { cycle ->
            val played = (cycle * uris.size until (cycle + 1) * uris.size)
                .mapNotNull(playlist::ringtoneForRepeatIndex)

            assertEquals("Cycle $cycle must include every ringtone", uris.toSet(), played.toSet())
            assertEquals("Cycle $cycle must not repeat a ringtone", uris.size, played.distinct().size)
        }
        val sequence = (0 until (uris.size * 5)).mapNotNull(playlist::ringtoneForRepeatIndex)
        assertTrue(
            "A new shuffle cycle should not immediately repeat the previous cycle's last ringtone",
            sequence.zipWithNext().all { (previous, next) -> previous != next }
        )
    }

    @Test
    fun `DEFAULT has empty playlist`() {
        assertEquals(0, RingtonePlaylist.DEFAULT.size)
        assertTrue(RingtonePlaylist.DEFAULT.isEmpty)
        assertNull(RingtonePlaylist.DEFAULT.ringtoneForRepeatIndex(0))
    }

    @Test
    fun `size returns correct count`() {
        val playlist = RingtonePlaylist(listOf("a", "b", "c"))
        assertEquals(3, playlist.size)
    }

    @Test
    fun `isEmpty returns false when ringtones present`() {
        val empty = RingtonePlaylist(emptyList())
        assertTrue(empty.isEmpty)

        val nonEmpty = RingtonePlaylist(listOf("a"))
        assertFalse(nonEmpty.isEmpty)
    }

    @Test
    fun `ringtoneForRepeatIndex with index larger than playlist size cycles correctly`() {
        val uris = listOf("first", "second")
        val playlist = RingtonePlaylist(uris, false)

        // Index 10 should wrap to position 0 (10 % 2 = 0)
        assertEquals("first", playlist.ringtoneForRepeatIndex(10))
        // Index 11 should wrap to position 1 (11 % 2 = 1)
        assertEquals("second", playlist.ringtoneForRepeatIndex(11))
        // Index 19 should wrap to position 1 (19 % 2 = 1)
        assertEquals("second", playlist.ringtoneForRepeatIndex(19))
    }

    @Test
    fun `shuffle mode produces same permutation for same index across computations`() {
        val uris = listOf("ring1", "ring2", "ring3", "ring4")
        val playlist = RingtonePlaylist(uris, true)

        // Simulate multiple "computations" (like AlarmComputer.computeSchedule being called multiple times)
        var baseline: List<String?>? = null
        repeat(10) {
            val results = (0 until 6).map { playlist.ringtoneForRepeatIndex(it) }
            if (baseline == null) {
                baseline = results
            } else {
                assertEquals(baseline, results)
            }
        }
    }
}
