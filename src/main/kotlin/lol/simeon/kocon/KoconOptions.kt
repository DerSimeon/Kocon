package lol.simeon.kocon

import java.nio.file.Path

/** Options for [useConfig]; only [path] is required. */
class KoconOptions {
    /** The config file. */
    lateinit var path: Path

    /** Prefix for `@Comment` lines in written files. */
    var commentStyle: CommentStyle = CommentStyle.Hashtag

    /** Create a missing file and fill in missing keys from the class defaults, writing the result back. */
    var withDefault: Boolean = true

    internal fun requirePath(): Path =
        if (::path.isInitialized) path else throw KoconException("useConfig requires a path")
}
