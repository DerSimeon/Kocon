package lol.simeon.kocon.render

import lol.simeon.kocon.CommentStyle
import lol.simeon.kocon.tree.HoconValue.HoconList
import lol.simeon.kocon.tree.HoconValue.HoconNull
import lol.simeon.kocon.tree.HoconValue.HoconObject
import lol.simeon.kocon.tree.HoconValue.HoconScalar
import kotlin.test.Test
import kotlin.test.assertEquals

class RendererTest {
    private fun bare(text: String) = HoconScalar(text, quoted = false)

    private fun quoted(text: String) = HoconScalar(text, quoted = true)

    @Test
    fun `flat object renders key = value lines with trailing newline`() {
        val tree = HoconObject(mapOf("port" to bare("25565"), "name" to quoted("srv"), "motd" to HoconNull))
        assertEquals("port = 25565\nname = \"srv\"\nmotd = null\n", renderHocon(tree))
    }

    @Test
    fun `nested object renders with four-space indent and key brace`() {
        val pool = HoconObject(mapOf("max" to bare("8")))
        val tree = HoconObject(
            mapOf(
                "db" to HoconObject(mapOf("host" to quoted("localhost"), "pool" to pool)),
                "empty" to HoconObject(emptyMap()),
            ),
        )
        val expected = """
            |db {
            |    host = "localhost"
            |    pool {
            |        max = 8
            |    }
            |}
            |empty {}
            |
        """.trimMargin()
        assertEquals(expected, renderHocon(tree))
    }

    @Test
    fun `lists render empty as brackets and otherwise multi-line`() {
        val tree = HoconObject(
            mapOf(
                "none" to HoconList(emptyList()),
                "ports" to HoconList(listOf(bare("1"), bare("2"))),
                "users" to HoconList(listOf(HoconObject(mapOf("name" to quoted("a"))))),
            ),
        )
        val expected = """
            |none = []
            |ports = [
            |    1
            |    2
            |]
            |users = [
            |    {
            |        name = "a"
            |    }
            |]
            |
        """.trimMargin()
        assertEquals(expected, renderHocon(tree))
    }

    @Test
    fun `strings with special characters are escaped and quoted`() {
        val tree = HoconObject(mapOf("s" to quoted("say \"hi\" \\ a\nb # c // d ü\t\u0001")))
        assertEquals("s = \"say \\\"hi\\\" \\\\ a\\nb # c // d ü\\t\\u0001\"\n", renderHocon(tree))
    }

    @Test
    fun `unsafe keys are quoted and safe keys stay bare`() {
        val tree = HoconObject(
            mapOf("a b" to bare("1"), "a.b" to bare("2"), "" to bare("3"), "max-players" to bare("4")),
        )
        assertEquals("\"a b\" = 1\n\"a.b\" = 2\n\"\" = 3\nmax-players = 4\n", renderHocon(tree))
    }

    @Test
    fun `two-line comment renders as hashtag lines by default (AE6 at tree level)`() {
        val tree = HoconObject(mapOf("port" to bare("25565")), comments = mapOf("port" to "Server port\nDefault 25565"))
        assertEquals("# Server port\n# Default 25565\nport = 25565\n", renderHocon(tree))
    }

    @Test
    fun `two-line comment renders as double-slash lines (AE6 at tree level)`() {
        val tree = HoconObject(mapOf("port" to bare("25565")), comments = mapOf("port" to "Server port\nDefault 25565"))
        assertEquals("// Server port\n// Default 25565\nport = 25565\n", renderHocon(tree, CommentStyle.DoubleSlash))
    }

    @Test
    fun `root, key and class comments render above their targets`() {
        val db = HoconObject(mapOf("host" to quoted("h")), comment = "Database class")
        val tree = HoconObject(
            mapOf("db" to db, "dbs" to HoconList(listOf(db))),
            comments = mapOf("db" to "Main database"),
            comment = "Root config",
        )
        val expected = """
            |# Root config
            |# Main database
            |# Database class
            |db {
            |    host = "h"
            |}
            |dbs = [
            |    # Database class
            |    {
            |        host = "h"
            |    }
            |]
            |
        """.trimMargin()
        assertEquals(expected, renderHocon(tree))
    }
}
