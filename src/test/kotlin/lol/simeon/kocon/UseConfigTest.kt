package lol.simeon.kocon

import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UseConfigTest {
    @Serializable
    data class Port(val port: Int = 25565)

    @Serializable
    data class Server(@Comment("Server port") val port: Int = 25565, val motd: String = "hi")

    @Serializable
    data class Nested(val server: Server = Server(), val note: String? = null)

    @Serializable
    data class Required(val token: String)

    @TempDir
    lateinit var dir: Path

    private val file get() = dir.resolve("config.conf")

    @Test
    fun `AE1 missing file is created from defaults with parent dirs`() {
        val target = dir.resolve("a/b/config.conf")
        val config = useConfig<Port> { path = target }
        assertEquals(25565, config.value.port)
        assertContains(target.readText(), "port = 25565")
    }

    @Test
    fun `AE2 missing key is filled in and unknown key dropped`() {
        file.writeText("port = 1\nold = true\n")
        val config = useConfig<Server> { path = file }
        assertEquals(Server(1, "hi"), config.value)
        val text = file.readText()
        assertContains(text, "port = 1")
        assertContains(text, "motd = \"hi\"")
        assertFalse("old" in text)
    }

    @Test
    fun `missing nested key triggers a rewrite`() {
        file.writeText("server { port = 2 }\nnote = null\n")
        val config = useConfig<Nested> { path = file }
        assertEquals(Nested(Server(2, "hi")), config.value)
        assertContains(file.readText(), "motd = \"hi\"")
    }

    @Test
    fun `AE3 complete hand-written file keeps its bytes`() {
        file.writeText("// mine\nport = 7 # hand comment\nmotd = custom\n")
        val before = file.readBytes()
        assertEquals(Server(7, "custom"), useConfig<Server> { path = file }.value)
        assertContentEquals(before, file.readBytes())
    }

    @Test
    fun `AE3 generated file with nulls keeps its bytes on second load`() {
        useConfig<Nested> { path = file }
        val before = file.readBytes()
        useConfig<Nested> { path = file }
        assertContentEquals(before, file.readBytes())
    }

    @Test
    fun `AE4 missing file without withDefault throws and creates nothing`() {
        assertFailsWith<KoconException> { useConfig<Port> { path = file; withDefault = false } }
        assertFalse(file.exists())
    }

    @Test
    fun `without withDefault a missing key uses its default and the file is unchanged`() {
        file.writeText("port = 3\n")
        val config = useConfig<Server> { path = file; withDefault = false }
        assertEquals(Server(3, "hi"), config.value)
        assertEquals("port = 3\n", file.readText())
    }

    @Test
    fun `AE5 missing required property throws naming it and creates nothing`() {
        val e = assertFailsWith<KoconException> { useConfig<Required> { path = file } }
        assertContains(e.message.orEmpty(), "token")
        assertFalse(file.exists())
    }

    @Test
    fun `AE7 syntax error names the line and leaves the bytes unchanged`() {
        file.writeText("port = {")
        val e = assertFailsWith<KoconException> { useConfig<Server> { path = file } }
        assertContains(e.message.orEmpty(), "line 1")
        assertEquals("port = {", file.readText())
    }

    @Test
    fun `type mismatch throws and leaves the bytes unchanged`() {
        file.writeText("port = abc\n")
        assertFailsWith<KoconException> { useConfig<Server> { path = file } }
        assertEquals("port = abc\n", file.readText())
    }

    @Test
    fun `read-only target needing a merge throws with the IO cause`() {
        file.writeText("port = 1\n")
        assumeFalse(!file.toFile().setReadOnly() || Files.isWritable(file), "cannot make the file read-only here")
        try {
            val e = assertFailsWith<KoconException> { useConfig<Server> { path = file } }
            assertIs<IOException>(e.cause)
            assertEquals("port = 1\n", file.readText())
        } finally {
            file.toFile().setWritable(true)
        }
    }

    @Test
    fun `reload picks up an external edit`() {
        val config = useConfig<Server> { path = file }
        file.writeText("port = 9\nmotd = x\n")
        config.reload()
        assertEquals(Server(9, "x"), config.value)
    }

    @Test
    fun `reload of an invalid file throws and keeps the previous value and bytes`() {
        file.writeText("port = 8\nmotd = ok\n")
        val config = useConfig<Server> { path = file }
        file.writeText("port = {")
        val before = file.readBytes()
        assertFailsWith<KoconException> { config.reload() }
        assertEquals(Server(8, "ok"), config.value)
        assertContentEquals(before, file.readBytes())
    }

    @Test
    fun `invalid UTF-8 throws KoconException and leaves the bytes unchanged`() {
        val bytes = byteArrayOf(0xC3.toByte(), 0x28)
        file.writeBytes(bytes)
        assertFailsWith<KoconException> { useConfig<Server> { path = file; withDefault = true } }
        assertContentEquals(bytes, file.readBytes())
    }

    @Test
    fun `save writes the value and a fresh load reads it back`() {
        val config = useConfig<Server> { path = file }
        config.value = config.value.copy(motd = "saved")
        config.save()
        assertEquals("saved", useConfig<Server> { path = file }.value.motd)
    }

    @Test
    fun `works as a property delegate`() {
        file.writeText("port = 4\n")
        val config: Port by useConfig { path = file }
        assertEquals(Port(4), config)
    }

    @Test
    fun `defaults are Hashtag comments and withDefault`() {
        val options = KoconOptions()
        assertEquals(CommentStyle.Hashtag, options.commentStyle)
        assertTrue(options.withDefault)
        useConfig<Server> { path = file }
        assertContains(file.readText(), "# Server port")
    }

    @Test
    fun `DoubleSlash comment style is used for written files`() {
        useConfig<Server> { path = file; commentStyle = CommentStyle.DoubleSlash }
        assertContains(file.readText(), "// Server port")
    }

    @Test
    fun `missing path throws`() {
        assertIs<KoconException>(runCatching { useConfig<Port> { } }.exceptionOrNull())
    }
}
