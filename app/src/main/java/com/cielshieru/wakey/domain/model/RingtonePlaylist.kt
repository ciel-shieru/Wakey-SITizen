package com.cielshieru.wakey.domain.model

import kotlinx.serialization.Serializable
import java.util.Random

@Serializable
data class RingtonePlaylist(
    val ringtoneUris: List<String> = emptyList(),
    val shuffleEnabled: Boolean = false
) {
    companion object {
        val DEFAULT = RingtonePlaylist()
        const val MAX_RINGTONES = 20
    }

    val isEmpty: Boolean get() = ringtoneUris.isEmpty()
    val size: Int get() = ringtoneUris.size

    fun ringtoneForRepeatIndex(index: Int): String? {
        if (ringtoneUris.isEmpty()) return null
        return if (shuffleEnabled) {
            val cycle = index / size
            val position = index % size
            shuffledCycle(cycle)[position]
        } else {
            ringtoneUris[index % size]
        }
    }

    private fun shuffledCycle(cycle: Int): List<String> {
        val shuffled = ringtoneUris.shuffled(Random(cycle.toLong())).toMutableList()
        if (size > 1 && cycle > 0 && shuffled.first() == shuffledCycle(cycle - 1).last()) {
            java.util.Collections.swap(shuffled, 0, 1)
        }
        return shuffled
    }
}
