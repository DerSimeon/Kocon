package lol.simeon.kocon

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.serializer
import lol.simeon.kocon.mapping.TreeDecoder
import lol.simeon.kocon.mapping.TreeEncoder
import lol.simeon.kocon.parse.HoconParser
import lol.simeon.kocon.render.renderHocon
import lol.simeon.kocon.tree.HoconValue
import lol.simeon.kocon.tree.HoconValue.HoconObject

/** HOCON text encoding and decoding without any file. */
object Kocon {
    /** Encodes [value] as HOCON text; `@Comment` text is prefixed per [commentStyle]. */
    fun <T> encodeToString(
        serializer: SerializationStrategy<T>,
        value: T,
        commentStyle: CommentStyle = CommentStyle.Hashtag,
    ): String = renderHocon(encodeToTree(serializer, value), commentStyle)

    /** Decodes HOCON [text]; absent keys take the class defaults and unknown keys are ignored. */
    fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, text: String): T =
        decodeFromTree(deserializer, HoconParser.parse(text))

    inline fun <reified T> encodeToString(value: T, commentStyle: CommentStyle = CommentStyle.Hashtag): String =
        encodeToString(serializer<T>(), value, commentStyle)

    inline fun <reified T> decodeFromString(text: String): T = decodeFromString(serializer<T>(), text)
}

/** Encodes [value] to a tree holding every property, defaults and nulls included. The root must be a class. */
internal fun <T> encodeToTree(serializer: SerializationStrategy<T>, value: T): HoconObject {
    var root: HoconValue? = null
    TreeEncoder { root = it }.encodeSerializableValue(serializer, value)
    return root as? HoconObject ?: throw KoconException("Only classes can be encoded as a HOCON document")
}

internal fun <T> decodeFromTree(deserializer: DeserializationStrategy<T>, root: HoconObject): T =
    TreeDecoder(root).decodeSerializableValue(deserializer)
