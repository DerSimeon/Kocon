package lol.simeon.kocon.mapping

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import lol.simeon.kocon.KoconException
import lol.simeon.kocon.tree.HoconValue
import lol.simeon.kocon.tree.HoconValue.HoconList
import lol.simeon.kocon.tree.HoconValue.HoconNull
import lol.simeon.kocon.tree.HoconValue.HoconObject
import lol.simeon.kocon.tree.HoconValue.HoconScalar

/**
 * Decodes [node] at dotted [path]. As a structure decoder it walks the node's children: absent keys
 * are skipped so kotlinx applies defaults, unknown keys are never visited.
 * Primitives are parsed from the raw scalar text, so quoting does not matter.
 */
@OptIn(ExperimentalSerializationApi::class)
@Suppress("TooManyFunctions")
internal class TreeDecoder(
    private val node: HoconValue,
    private val path: String = "",
) : AbstractDecoder() {
    override val serializersModule: SerializersModule = EmptySerializersModule()

    private var current = node
    private var currentPath = path
    private var cursor = 0

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        val (child, childPath) = when (descriptor.kind) {
            StructureKind.LIST -> (node as HoconList).items.getOrNull(cursor)?.let { it to "$path[$cursor]" }
            StructureKind.MAP -> mapElement()
            else -> classElement(descriptor)
        } ?: return CompositeDecoder.DECODE_DONE
        current = child
        currentPath = childPath
        return cursor++
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        val fits = if (descriptor.kind == StructureKind.LIST) current is HoconList else current is HoconObject
        if (!fits) fail(if (descriptor.kind == StructureKind.LIST) "expected a list" else "expected an object")
        return TreeDecoder(current, currentPath)
    }

    override fun <T> decodeSerializableValue(deserializer: DeserializationStrategy<T>): T =
        try {
            super.decodeSerializableValue(deserializer)
        } catch (e: MissingFieldException) {
            throw KoconException("Missing required key ${e.missingFields.joinToString { join(it, currentPath) }}", e)
        } catch (e: SerializationException) {
            throw KoconException("Invalid value for ${currentPath.ifEmpty { "root" }}: ${e.message}", e)
        }

    override fun decodeNotNullMark(): Boolean = current != HoconNull

    override fun decodeString(): String = text()

    override fun decodeBoolean(): Boolean = parse(String::toBooleanStrictOrNull)

    override fun decodeByte(): Byte = parse(String::toByteOrNull)

    override fun decodeShort(): Short = parse(String::toShortOrNull)

    override fun decodeInt(): Int = parse(String::toIntOrNull)

    override fun decodeLong(): Long = parse(String::toLongOrNull)

    override fun decodeFloat(): Float = parse(String::toFloatOrNull)

    override fun decodeDouble(): Double = parse(String::toDoubleOrNull)

    override fun decodeChar(): Char = parse(String::singleOrNull)

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int =
        parse { enumDescriptor.getElementIndex(it).takeIf { i -> i != CompositeDecoder.UNKNOWN_NAME } }

    /** Map elements alternate key, value; the key is decoded from a quoted scalar. */
    private fun mapElement(): Pair<HoconValue, String>? {
        val (key, value) = (node as HoconObject).entries.entries.elementAtOrNull(cursor / 2) ?: return null
        return (if (cursor % 2 == 0) HoconScalar(key, quoted = true) else value) to join(key)
    }

    /** Next present property from [cursor]; absent ones are skipped so their defaults apply. */
    private fun classElement(descriptor: SerialDescriptor): Pair<HoconValue, String>? {
        val entries = (node as HoconObject).entries
        while (cursor < descriptor.elementsCount) {
            val name = descriptor.getElementName(cursor)
            entries[name]?.let { return it to join(name) }
            cursor++
        }
        return null
    }

    private fun join(key: String, parent: String = path) = if (parent.isEmpty()) key else "$parent.$key"

    private fun text(): String = (current as? HoconScalar)?.text ?: fail("expected a value")

    private fun <T : Any> parse(convert: (String) -> T?): T {
        val text = text()
        return convert(text) ?: fail("'$text' does not fit the expected type")
    }

    private fun fail(problem: String): Nothing =
        throw KoconException("Invalid value for ${currentPath.ifEmpty { "root" }}: $problem")
}
