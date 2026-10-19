package lol.simeon.kocon.mapping

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import lol.simeon.kocon.Comment
import lol.simeon.kocon.tree.HoconValue
import lol.simeon.kocon.tree.HoconValue.HoconList
import lol.simeon.kocon.tree.HoconValue.HoconNull
import lol.simeon.kocon.tree.HoconValue.HoconObject
import lol.simeon.kocon.tree.HoconValue.HoconScalar

/**
 * Encodes one value into a [HoconValue] handed to [sink]. Structures get a child encoder that collects
 * their elements (every element, defaults and nulls included) and hands the finished node up on end.
 */
@OptIn(ExperimentalSerializationApi::class)
internal class TreeEncoder(
    private val structure: SerialDescriptor? = null,
    private val sink: (HoconValue) -> Unit,
) : AbstractEncoder() {
    override val serializersModule: SerializersModule = EmptySerializersModule()

    private val entries = LinkedHashMap<String, HoconValue>()
    private val comments = LinkedHashMap<String, String>()
    private val items = ArrayList<HoconValue>()
    private var index = -1
    private var mapKey = ""

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        this.index = index
        if (descriptor.kind == StructureKind.CLASS) {
            val text = descriptor.getElementAnnotations(index).commentText()
            if (text != null) comments[descriptor.getElementName(index)] = text
        }
        return true
    }

    override fun encodeValue(value: Any) = put(HoconScalar(value.toString(), quoted = false))

    override fun encodeString(value: String) = put(HoconScalar(value, quoted = true))

    override fun encodeChar(value: Char) = put(HoconScalar(value.toString(), quoted = true))

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) =
        put(HoconScalar(enumDescriptor.getElementName(index), quoted = true))

    override fun encodeNull() = put(HoconNull)

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder = TreeEncoder(descriptor, ::put)

    override fun endStructure(descriptor: SerialDescriptor) =
        sink(
            when (descriptor.kind) {
                StructureKind.LIST -> HoconList(items)
                else -> HoconObject(entries, comments, descriptor.annotations.commentText())
            },
        )

    private fun put(value: HoconValue) {
        when (structure?.kind) {
            null -> sink(value)
            StructureKind.LIST -> items += value
            // Map keys arrive as even elements; Map<String, V> keys are scalars.
            StructureKind.MAP if index % 2 == 0 -> mapKey = (value as HoconScalar).text
            StructureKind.MAP -> entries[mapKey] = value
            else -> entries[structure.getElementName(index)] = value
        }
    }

    private fun List<Annotation>.commentText(): String? = filterIsInstance<Comment>().firstOrNull()?.text
}
