package lol.simeon.kocon.tree

/**
 * Parsed or to-be-rendered HOCON value. Scalars keep their raw text so the mapping layer can
 * convert to the exact target type (no early Long/Double precision loss, `0123` stays text).
 */
internal sealed interface HoconValue {
    /**
     * An object; entry order is preserved. [comments] holds per-key comment text rendered above
     * the key, [comment] is the object's own (class-level) comment.
     */
    data class HoconObject(
        val entries: Map<String, HoconValue>,
        val comments: Map<String, String> = emptyMap(),
        val comment: String? = null,
    ) : HoconValue

    data class HoconList(val items: List<HoconValue>) : HoconValue

    /** A string, number or boolean as written; [quoted] tells a quoted `"true"` from a bare `true`. */
    data class HoconScalar(val text: String, val quoted: Boolean) : HoconValue

    data object HoconNull : HoconValue
}
