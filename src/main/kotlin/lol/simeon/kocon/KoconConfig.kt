package lol.simeon.kocon

import kotlinx.serialization.KSerializer
import lol.simeon.kocon.io.writeAtomically
import lol.simeon.kocon.parse.HoconParser
import lol.simeon.kocon.render.renderHocon
import lol.simeon.kocon.tree.HoconValue.HoconObject
import java.io.IOException
import java.nio.file.Files
import kotlin.reflect.KProperty

/** A loaded config file. Also a read-only property delegate yielding [value]. */
class KoconConfig<T> internal constructor(private val serializer: KSerializer<T>, options: KoconOptions) {
    private val path = options.requirePath()
    private val commentStyle = options.commentStyle
    private val withDefault = options.withDefault

    /** The current config; assigning it does not write the file, call [save] for that. */
    @Volatile
    var value: T = load()

    /** Re-reads the file, applying the same create and merge rules as the first load. */
    fun reload() {
        value = load()
    }

    /** Writes [value] to the file. */
    fun save() = write(value)

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    private fun load(): T {
        if (Files.notExists(path)) {
            if (!withDefault) throw KoconException("Config file $path does not exist")
            return decodeFromTree(serializer, HoconObject(emptyMap())).also(::write)
        }
        val text = try {
            Files.readString(path, Charsets.UTF_8)
        } catch (e: IOException) {
            throw KoconException("Could not read config file $path: ${e.message}", e)
        }
        val tree = HoconParser.parse(text)
        val loaded = decodeFromTree(serializer, tree)
        if (withDefault && hasMissingKeys(encodeToTree(serializer, loaded), tree)) write(loaded)
        return loaded
    }

    private fun write(value: T) = writeAtomically(path, renderHocon(encodeToTree(serializer, value), commentStyle))
}

/** True when a key path of [expected] is absent from [actual]; only object keys are compared, list items are values. */
private fun hasMissingKeys(expected: HoconObject, actual: HoconObject): Boolean =
    expected.entries.any { (key, value) ->
        val present = actual.entries[key]
        present == null || value is HoconObject && present is HoconObject && hasMissingKeys(value, present)
    }
