package lol.simeon.kocon

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/** Loads the HOCON file set up in [block], creating or merging it per [KoconOptions.withDefault]. */
inline fun <reified T> useConfig(block: KoconOptions.() -> Unit): KoconConfig<T> =
    useConfig(serializer<T>(), KoconOptions().apply(block))

/** Like the reified [useConfig], with an explicit [serializer]. */
fun <T> useConfig(serializer: KSerializer<T>, options: KoconOptions): KoconConfig<T> = KoconConfig(serializer, options)
