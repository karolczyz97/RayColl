package com.raycoll.app.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Enum ze stałym kluczem tekstowym zgodnym z zapisanymi danymi Room, Firestore i kopii JSON. */
interface KeyedEnum {
    val key: String
}

fun <E : KeyedEnum> keyedEnumOrNull(entries: List<E>, key: String?): E? = entries.firstOrNull { it.key == key }

/**
 * Serializuje enum przez [KeyedEnum.key]. Nieznana wartość (stary/ręcznie edytowany
 * backup) daje [fallback] zamiast błędu, aby starsze kopie nadal dało się wczytać.
 */
abstract class KeyedEnumSerializer<E>(serialName: String, private val entries: List<E>, private val fallback: E) :
    KSerializer<E> where E : Enum<E>, E : KeyedEnum {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: E) = encoder.encodeString(value.key)

    override fun deserialize(decoder: Decoder): E {
        val key = decoder.decodeString()
        return keyedEnumOrNull(entries, key) ?: fallback
    }
}
