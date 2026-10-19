package lol.simeon.kocon.mapping

import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import lol.simeon.kocon.Comment
import lol.simeon.kocon.KoconException
import lol.simeon.kocon.decodeFromTree
import lol.simeon.kocon.encodeToTree
import lol.simeon.kocon.parse.HoconParser
import lol.simeon.kocon.tree.HoconValue.HoconNull
import lol.simeon.kocon.tree.HoconValue.HoconObject
import lol.simeon.kocon.tree.HoconValue.HoconScalar
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MappingTest {
    enum class Difficulty { Easy, Hard }

    @Serializable
    data class Level3(val depth: Int = 3)

    @Serializable
    data class Level2(val inner: Level3 = Level3(), val label: String = "two")

    @Serializable
    data class Level1(val inner: Level2 = Level2())

    @Serializable
    data class Everything(
        val int: Int,
        val long: Long,
        val double: Double,
        val float: Float,
        val bool: Boolean,
        val char: Char,
        val string: String,
        val difficulty: Difficulty,
        val nothing: String?,
        val something: Int?,
        val ints: List<Int>,
        val tags: Set<String>,
        val byName: Map<String, Level3>,
        val nested: Level1,
    )

    @Serializable
    data class Defaults(val port: Int = 25565, val name: String = "srv", val tags: List<String> = listOf("a"))

    @Serializable
    data class Required(val token: String)

    @Serializable
    data class Server(val port: Int = 1)

    @Serializable
    data class Root(val server: Server = Server(), val port: Int = 2)

    @Serializable
    data class Text(val code: String)

    @Serializable
    data class Game(val difficulty: Difficulty)

    @Serializable
    @Comment("Server settings")
    data class Commented(@Comment("Server port\nDefault 25565") val port: Int = 25565)

    @Serializable
    data class Holder(val server: Commented = Commented(), val note: String? = null)

    private val everything = Everything(
        int = -7,
        long = Long.MAX_VALUE,
        double = 3.25,
        float = 1.5f,
        bool = true,
        char = 'x',
        string = "0123 \"quoted\"",
        difficulty = Difficulty.Hard,
        nothing = null,
        something = 42,
        ints = listOf(1, 2, 3),
        tags = setOf("x", "y"),
        byName = mapOf("first" to Level3(1), "second" to Level3(2)),
        nested = Level1(Level2(Level3(9), "deep")),
    )

    private inline fun <reified T> decode(text: String): T = decodeFromTree(serializer<T>(), HoconParser.parse(text))

    @Test
    fun `tree round trip gives back an equal value`() {
        assertEquals(everything, decodeFromTree(serializer<Everything>(), encodeToTree(serializer(), everything)))
    }

    @Test
    fun `encoder quotes strings and enums but not numbers or booleans`() {
        val tree = encodeToTree(serializer(), everything)
        assertEquals(HoconScalar("Hard", quoted = true), tree.entries["difficulty"])
        assertEquals(HoconScalar("x", quoted = true), tree.entries["char"])
        assertEquals(HoconScalar("9223372036854775807", quoted = false), tree.entries["long"])
        assertEquals(HoconScalar("true", quoted = false), tree.entries["bool"])
        assertEquals(HoconNull, tree.entries["nothing"])
    }

    @Test
    fun `encoder writes defaults and comments into the tree`() {
        val tree = encodeToTree(serializer(), Holder())
        assertEquals(listOf("server", "note"), tree.entries.keys.toList())
        val server = tree.entries["server"] as HoconObject
        assertEquals("Server settings", server.comment)
        assertEquals(mapOf("port" to "Server port\nDefault 25565"), server.comments)
    }

    @Test
    fun `empty object decodes to the default instance`() {
        assertEquals(Defaults(), decode<Defaults>("{}"))
    }

    @Test
    fun `missing required property names it`() {
        val e = assertFailsWith<KoconException> { decode<Required>("") }
        assertContains(e.message!!, "token")
    }

    @Test
    fun `unparsable number names the key path`() {
        assertContains(assertFailsWith<KoconException> { decode<Root>("port = \"abc\"") }.message!!, "port")
        val nested = assertFailsWith<KoconException> { decode<Root>("server { port = abc }") }
        assertContains(nested.message!!, "server.port")
    }

    @Test
    fun `scalar for an object property names the key path`() {
        assertContains(assertFailsWith<KoconException> { decode<Root>("server = 5") }.message!!, "server")
    }

    @Test
    fun `scalar for a list property names the key path`() {
        assertContains(assertFailsWith<KoconException> { decode<Defaults>("tags = 1") }.message!!, "tags")
    }

    @Test
    fun `null for a non-null property names the key path`() {
        assertContains(assertFailsWith<KoconException> { decode<Root>("port = null") }.message!!, "port")
        val nested = assertFailsWith<KoconException> { decode<Root>("server { port = null }") }
        assertContains(nested.message!!, "server.port")
    }

    @Test
    fun `unknown keys are ignored`() {
        assertEquals(Root(port = 5), decode<Root>("old = true\nport = 5"))
    }

    @Test
    fun `primitives are read from raw text`() {
        assertEquals(Server(25565), decode<Server>("port = \"25565\""))
        assertEquals(Text("0123"), decode<Text>("code = 0123"))
    }

    @Test
    fun `enums decode by name and reject unknown names with the path`() {
        assertEquals(Game(Difficulty.Hard), decode<Game>("difficulty = Hard"))
        val e = assertFailsWith<KoconException> { decode<Game>("difficulty = Impossible") }
        assertContains(e.message!!, "difficulty")
    }

    @Test
    fun `non-object root cannot be encoded`() {
        assertFailsWith<KoconException> { encodeToTree(serializer<List<Int>>(), listOf(1)) }
    }
}
