package lol.simeon.kocon

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KoconStringTest {
    @Serializable
    @Comment("Server settings")
    data class Server(@Comment("Server port\nDefault 25565") val port: Int = 25565)

    @Serializable
    data class Config(
        val server: Server = Server(),
        val motd: String? = null,
        val ports: Map<String, Int> = mapOf("a" to 1),
    )

    @Serializable
    data class D(val token: String)

    @Test
    fun `string round trip gives back an equal value`() {
        val config = Config(Server(80), "hi", mapOf("x" to 2, "y.z" to 3))
        assertEquals(config, Kocon.decodeFromString<Config>(Kocon.encodeToString(config)))
    }

    @Test
    fun `comments use the chosen style (AE6)`() {
        val slashes = Kocon.encodeToString(Config(), CommentStyle.DoubleSlash)
        assertContains(slashes, "// Server settings\nserver {\n")
        assertContains(slashes, "    // Server port\n    // Default 25565\n    port = 25565\n")
        val hashtags = Kocon.encodeToString(Config())
        assertContains(hashtags, "    # Server port\n    # Default 25565\n    port = 25565\n")
    }

    @Test
    fun `null property is written as null`() {
        assertContains(Kocon.encodeToString(Config()), "motd = null\n")
    }

    @Test
    fun `missing required property names it (AE5)`() {
        val e = assertFailsWith<KoconException> { Kocon.decodeFromString<D>("{}") }
        assertContains(e.message!!, "token")
    }
}
