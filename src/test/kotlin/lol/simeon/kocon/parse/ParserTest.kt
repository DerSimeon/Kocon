package lol.simeon.kocon.parse

import lol.simeon.kocon.KoconException
import lol.simeon.kocon.tree.HoconValue
import lol.simeon.kocon.tree.HoconValue.HoconList
import lol.simeon.kocon.tree.HoconValue.HoconNull
import lol.simeon.kocon.tree.HoconValue.HoconObject
import lol.simeon.kocon.tree.HoconValue.HoconScalar
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ParserTest {
    private fun obj(vararg entries: Pair<String, HoconValue>) = HoconObject(linkedMapOf(*entries))

    private fun bare(text: String) = HoconScalar(text, quoted = false)

    private fun quoted(text: String) = HoconScalar(text, quoted = true)

    private fun assertSyntaxError(text: String, line: Int) {
        val e = assertFailsWith<KoconException> { HoconParser.parse(text) }
        assertContains(e.message.orEmpty(), "line $line")
    }

    @Test
    fun `equals, colon and omitted separator before brace`() {
        assertEquals(obj("a" to bare("1")), HoconParser.parse("a = 1"))
        assertEquals(obj("a" to bare("1")), HoconParser.parse("a: 1"))
        assertEquals(obj("a" to obj("b" to bare("1"))), HoconParser.parse("a { b = 1 }"))
    }

    @Test
    fun `missing separator before a non-object value fails on line 1`() {
        assertSyntaxError("a 1", line = 1)
    }

    @Test
    fun `braced root equals unbraced root`() {
        assertEquals(HoconParser.parse("a = 1\nb = x"), HoconParser.parse("{\n a = 1\n b = x\n}"))
    }

    @Test
    fun `dotted keys expand and merge with objects`() {
        assertEquals(obj("a" to obj("b" to obj("c" to bare("1")))), HoconParser.parse("a.b.c = 1"))
        assertEquals(
            obj("a" to obj("b" to bare("1"), "c" to bare("2"))),
            HoconParser.parse("a.b = 1\na { c = 2 }"),
        )
    }

    @Test
    fun `duplicate scalar keys are last-wins and duplicate objects merge`() {
        assertEquals(obj("a" to bare("2")), HoconParser.parse("a = 1\na = 2"))
        assertEquals(
            obj("a" to obj("x" to bare("1"), "y" to obj("p" to bare("3"), "q" to bare("4")))),
            HoconParser.parse("a { x = 1, y { p = 3 } }\na { y { q = 4 } }"),
        )
    }

    @Test
    fun `quoted, triple-quoted and unquoted strings`() {
        assertEquals(obj("s" to quoted("quoted \"esc\" \n")), HoconParser.parse("""s = "quoted \"esc\" \n""""))
        assertEquals(
            obj("s" to quoted("triple \"raw\" text\n\\n")),
            HoconParser.parse("s = \"\"\"triple \"raw\" text\n\\n\"\"\""),
        )
        assertEquals(obj("s" to bare("unquoted-word")), HoconParser.parse("s = unquoted-word"))
        assertEquals(obj("s" to quoted("\t\\/\u00e9")), HoconParser.parse("""s = "\t\\\/\u00e9""""))
    }

    @Test
    fun `quoted keys keep dots`() {
        assertEquals(obj("a.b" to bare("1")), HoconParser.parse("\"a.b\" = 1"))
    }

    @Test
    fun `unquoted words on one line join into one string`() {
        assertEquals(obj("motd" to bare("Hello world")), HoconParser.parse("motd = Hello world  "))
    }

    @Test
    fun `literals keep their raw text and bare null is null`() {
        val text = "t = true\nf = false\nn = null\ni = -12\nd = 3.5e2\nl = 9223372036854775807\nq = \"null\""
        assertEquals(
            obj(
                "t" to bare("true"),
                "f" to bare("false"),
                "n" to HoconNull,
                "i" to bare("-12"),
                "d" to bare("3.5e2"),
                "l" to bare("9223372036854775807"),
                "q" to quoted("null"),
            ),
            HoconParser.parse(text),
        )
    }

    @Test
    fun `lists`() {
        assertEquals(obj("l" to HoconList(emptyList())), HoconParser.parse("l = []"))
        assertEquals(obj("l" to HoconList(listOf(bare("1"), bare("2")))), HoconParser.parse("l = [1, 2]"))
        assertEquals(
            obj("l" to HoconList(listOf(bare("1"), quoted("two")))),
            HoconParser.parse("l = [\n  1,\n  \"two\",\n]"),
        )
        assertEquals(
            obj("l" to HoconList(listOf(obj("a" to bare("1")), obj("a" to bare("2"))))),
            HoconParser.parse("l = [\n  { a = 1 }\n  { a = 2 }\n]"),
        )
    }

    @Test
    fun `comments are skipped but kept inside quoted strings`() {
        val text = "# header\n// another\na = 1 # trailing\nb = \"x // y\" // trailing\nc = [1, // c\n 2]"
        assertEquals(
            obj("a" to bare("1"), "b" to quoted("x // y"), "c" to HoconList(listOf(bare("1"), bare("2")))),
            HoconParser.parse(text),
        )
    }

    @Test
    fun `bom and crlf parse like clean input`() {
        val clean = HoconParser.parse("a = 1\nb {\n c = x\n}\n")
        assertEquals(clean, HoconParser.parse("\uFEFFa = 1\nb {\n c = x\n}\n"))
        assertEquals(clean, HoconParser.parse("a = 1\r\nb {\r\n c = x\r\n}\r\n"))
    }

    @Test
    fun `empty input gives an empty object`() {
        assertEquals(obj(), HoconParser.parse(""))
        assertEquals(obj(), HoconParser.parse("  \n# only a comment\n"))
    }

    @Test
    fun `syntax errors name the 1-based line`() {
        assertSyntaxError("a = 1\nb = \"open", line = 2)
        assertSyntaxError("a = 1\nb = \"\"\"open", line = 2)
        assertSyntaxError("a = 1\nb {\n c = 1\n", line = 2)
        assertSyntaxError("a = 1\n\nb = [1, 2\n", line = 3)
        assertSyntaxError("a = 1\nb =\nc = 2", line = 2)
        assertSyntaxError("a = 1 = 2", line = 1)
        assertSyntaxError("{ a = 1 } b", line = 1)
        assertSyntaxError("= 1", line = 1)
        assertSyntaxError("a = \"\\q\"", line = 1)
    }
}
