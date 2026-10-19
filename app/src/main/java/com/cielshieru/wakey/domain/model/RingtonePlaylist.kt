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
            val shuffled = ringtoneUris.shuffled(Random(index.toLong()))
            shuffled[index % size]
        } else {
            ringtoneUris[index % size]
        }
    }
}
