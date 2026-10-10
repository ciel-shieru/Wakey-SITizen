package com.cielshieru.wakey.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.LocalDate

internal object InstantSerializer : KSerializer<Instant> {
    override val descriptor =
        kotlinx.serialization.descriptors.PrimitiveKind.LONG.toPrimitiveDescriptor("Instant")

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeLong(value.toEpochMilli())
    }

    override fun deserialize(decoder: Decoder): Instant {
        return Instant.ofEpochMilli(decoder.decodeLong())
    }
}

internal object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor =
        kotlinx.serialization.descriptors.PrimitiveKind.STRING.toPrimitiveDescriptor("LocalDate")

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): LocalDate {
        return LocalDate.parse(decoder.decodeString())
    }
}
