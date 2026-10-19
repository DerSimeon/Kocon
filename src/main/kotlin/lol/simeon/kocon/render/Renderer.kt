package lol.simeon.kocon.render

import lol.simeon.kocon.CommentStyle
import lol.simeon.kocon.tree.HoconValue
import lol.simeon.kocon.tree.HoconValue.HoconList
import lol.simeon.kocon.tree.HoconValue.HoconNull
import lol.simeon.kocon.tree.HoconValue.HoconObject
import lol.simeon.kocon.tree.HoconValue.HoconScalar

private const val INDENT = "    "
private val BARE_KEY = Regex("[A-Za-z0-9_-]+")

/** Renders [root] as HOCON text: 4-space indent, `\n` line endings, trailing newline. */
internal fun renderHocon(root: HoconObject, style: CommentStyle = CommentStyle.Hashtag): String =
    buildString {
        root.comment?.let { appendComment(it, style, "") }
        appendEntries(root, style, "")
    }

private fun StringBuilder.appendEntries(obj: HoconObject, style: CommentStyle, indent: String) {
    for ((key, value) in obj.entries) {
        obj.comments[key]?.let { appendComment(it, style, indent) }
        (value as? HoconObject)?.comment?.let { appendComment(it, style, indent) }
        append(indent).append(if (BARE_KEY.matches(key)) key else quote(key))
        append(if (value is HoconObject) " " else " = ")
        appendValue(value, style, indent)
        append('\n')
    }
}

private fun StringBuilder.appendValue(value: HoconValue, style: CommentStyle, indent: String) {
    when (value) {
        is HoconObject if value.entries.isEmpty() -> append("{}")
        is HoconObject -> {
            append("{\n")
            appendEntries(value, style, indent + INDENT)
            append(indent).append('}')
        }
        is HoconList if value.items.isEmpty() -> append("[]")
        is HoconList -> {
            append("[\n")
            for (item in value.items) {
                (item as? HoconObject)?.comment?.let { appendComment(it, style, indent + INDENT) }
                append(indent + INDENT)
                appendValue(item, style, indent + INDENT)
                append('\n')
            }
            append(indent).append(']')
        }
        is HoconScalar -> append(if (value.quoted) quote(value.text) else value.text)
        HoconNull -> append("null")
    }
}

private fun StringBuilder.appendComment(text: String, style: CommentStyle, indent: String) {
    for (line in text.split('\n')) append(indent).append(style.prefix).append(line).append('\n')
}

/** JSON-style quoting. */
private fun quote(text: String): String =
    buildString {
        append('"')
        for (c in text) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }
