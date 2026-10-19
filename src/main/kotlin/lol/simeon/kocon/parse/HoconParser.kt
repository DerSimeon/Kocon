package lol.simeon.kocon.parse

import lol.simeon.kocon.KoconException
import lol.simeon.kocon.tree.HoconValue
import lol.simeon.kocon.tree.HoconValue.HoconList
import lol.simeon.kocon.tree.HoconValue.HoconNull
import lol.simeon.kocon.tree.HoconValue.HoconObject
import lol.simeon.kocon.tree.HoconValue.HoconScalar

/**
 * Recursive-descent parser for the HOCON subset Kocon reads: objects, lists, quoted, triple-quoted and
 * unquoted strings, `=` / `:` / omitted separator before `{`, dotted keys and `#` / `//` comments.
 * Duplicate keys merge objects recursively and are last-wins otherwise.
 */
internal class HoconParser private constructor(text: String) {
    private val reader = HoconReader(text.removePrefix("﻿"))

    private fun parseRoot(): HoconObject {
        reader.skipBlank()
        val root = if (reader.peek() == '{') parseObject() else parseFields(close = null, openLine = 1)
        reader.skipBlank()
        if (!reader.atEnd) reader.fail("Unexpected '${reader.peek()}' after the root object")
        return root
    }

    private fun parseObject(): HoconObject {
        val openLine = reader.line
        reader.next()
        return parseFields(close = '}', openLine = openLine)
    }

    /** Parses fields up to [close] (consumed) or, for an unbraced root, up to the end of input. */
    private fun parseFields(close: Char?, openLine: Int): HoconObject {
        val entries = LinkedHashMap<String, HoconValue>()
        reader.skipBlank(commas = true)
        while (!reader.atEnd && reader.peek() != close) {
            val path = parseKey()
            reader.skipSpaces()
            val value = when (reader.peek()) {
                '{' -> parseObject()
                '=', ':' -> {
                    reader.next()
                    reader.skipSpaces()
                    parseValue()
                }
                else -> reader.fail("Expected '=', ':' or '{' after key '${path.joinToString(".")}'")
            }
            val nested = path.dropLast(1).foldRight(HoconObject(mapOf(path.last() to value))) { key, inner ->
                HoconObject(mapOf(key to inner))
            }
            nested.entries.forEach { (key, v) -> entries[key] = merge(entries[key], v) }
            expectSeparator(close)
            reader.skipBlank(commas = true)
        }
        if (close != null) {
            if (reader.atEnd) throw KoconException("Unclosed '{' opened on line $openLine")
            reader.next()
        }
        return HoconObject(entries)
    }

    private fun parseKey(): List<String> {
        val path = mutableListOf<String>()
        while (true) {
            path += if (reader.peek() == '"') {
                reader.readQuoted()
            } else {
                reader.readKeyWord().ifEmpty { reader.fail("Expected a key") }
            }
            if (reader.peek() != '.') return path
            reader.next()
        }
    }

    private fun parseValue(): HoconValue = when (reader.peek()) {
        '{' -> parseObject()
        '[' -> parseList()
        '"' -> HoconScalar(reader.readQuoted(), quoted = true)
        else -> {
            val text = reader.readUnquoted()
            if (text.isEmpty()) reader.fail("Expected a value")
            if (text == "null") HoconNull else HoconScalar(text, quoted = false)
        }
    }

    private fun parseList(): HoconList {
        val openLine = reader.line
        reader.next()
        val items = mutableListOf<HoconValue>()
        reader.skipBlank(commas = true)
        while (!reader.atEnd && reader.peek() != ']') {
            items += parseValue()
            expectSeparator(']')
            reader.skipBlank(commas = true)
        }
        if (reader.atEnd) throw KoconException("Unclosed '[' opened on line $openLine")
        reader.next()
        return HoconList(items)
    }

    /** After a field or list item, only a newline, a comma, [close] or the end of input may follow. */
    private fun expectSeparator(close: Char?) {
        reader.skipSpaces()
        if (!reader.atEnd && reader.peek() !in listOf('\n', ',', close)) reader.fail("Unexpected '${reader.peek()}'")
    }

    companion object {
        fun parse(text: String): HoconObject = HoconParser(text).parseRoot()

        private fun merge(old: HoconValue?, new: HoconValue): HoconValue {
            if (old !is HoconObject || new !is HoconObject) return new
            val merged = LinkedHashMap(old.entries)
            new.entries.forEach { (key, v) -> merged[key] = merge(merged[key], v) }
            return HoconObject(merged)
        }
    }
}
