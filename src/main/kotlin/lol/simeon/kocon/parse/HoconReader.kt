package lol.simeon.kocon.parse

import lol.simeon.kocon.KoconException

/** Character cursor over HOCON text that tracks the 1-based line and reads tokens for [HoconParser]. */
internal class HoconReader(private val src: String) {
    private var pos = 0

    var line = 1
        private set

    val atEnd get() = pos >= src.length

    private val atComment get() = peek() == '#' || src.startsWith("//", pos)

    fun peek(): Char = if (atEnd) END else src[pos]

    fun next(): Char = peek().also {
        if (it == '\n') line++
        pos++
    }

    fun fail(message: String): Nothing = throw KoconException("$message on line $line")

    /** Skips spaces and a trailing comment, stopping at the newline. */
    fun skipSpaces() {
        while (true) {
            when {
                atComment -> pos = src.indexOf('\n', pos).takeIf { it >= 0 } ?: src.length
                peek() != '\n' && peek().isWhitespace() -> pos++
                else -> return
            }
        }
    }

    /** Skips whitespace, newlines and comments, plus entry-separating commas when [commas] is set. */
    fun skipBlank(commas: Boolean = false) {
        while (true) {
            skipSpaces()
            val c = peek()
            if (c != '\n' && !(commas && c == ',')) return
            next()
        }
    }

    fun readKeyWord(): String = readWhile { !it.isWhitespace() && it != '.' && it !in FORBIDDEN }

    /** Reads an unquoted value up to the end of the line; words separated by spaces join into one string. */
    fun readUnquoted(): String = readWhile { it != '\n' && it !in FORBIDDEN }.trimEnd()

    /** Reads a `"quoted"` or `"""triple-quoted"""` string starting at the cursor. */
    fun readQuoted(): String {
        if (src.startsWith(TRIPLE, pos)) return readTripleQuoted()
        val startLine = line
        next()
        val out = StringBuilder()
        while (true) {
            if (atEnd || peek() == '\n') throw KoconException("Unterminated string starting on line $startLine")
            when (val c = next()) {
                '"' -> return out.toString()
                '\\' -> out.append(readEscape())
                else -> out.append(c)
            }
        }
    }

    private fun readTripleQuoted(): String {
        val startLine = line
        pos += TRIPLE.length
        var end = src.indexOf(TRIPLE, pos)
        if (end < 0) throw KoconException("Unterminated triple-quoted string starting on line $startLine")
        // Extra quotes before the closing """ belong to the string.
        while (src.startsWith(TRIPLE, end + 1)) end++
        val text = src.substring(pos, end)
        line += text.count { it == '\n' }
        pos = end + TRIPLE.length
        return text
    }

    private fun readEscape(): Char = when (val c = next()) {
        '"', '\\', '/' -> c
        'b' -> '\b'
        'f' -> '\u000C'
        'n' -> '\n'
        'r' -> '\r'
        't' -> '\t'
        'u' -> {
            val hex = src.substring(pos, minOf(pos + HEX_DIGITS, src.length))
            if (hex.length < HEX_DIGITS || !hex.all { it in HEX }) fail("Invalid \\u escape")
            pos += HEX_DIGITS
            hex.toInt(HEX_RADIX).toChar()
        }
        else -> fail("Invalid escape '\\$c'")
    }

    /** Reads while [accept] holds, never past a comment start. */
    private inline fun readWhile(accept: (Char) -> Boolean): String {
        val start = pos
        while (!atEnd && !atComment && accept(peek())) pos++
        return src.substring(start, pos)
    }

    private companion object {
        const val END = '\u0000'
        const val TRIPLE = "\"\"\""
        const val HEX = "0123456789abcdefABCDEF"
        const val HEX_DIGITS = 4
        const val HEX_RADIX = 16

        /** Characters that end an unquoted key or value. */
        const val FORBIDDEN = "\"{}[]:=,"
    }
}
