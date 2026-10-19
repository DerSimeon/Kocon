package lol.simeon.kocon.io

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AtomicWriterTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `replaces the target and leaves no temp files`() {
        val target = dir.resolve("c.conf")
        target.writeText("old")
        writeAtomically(target, "new ü\n")
        assertEquals("new ü\n", target.readText())
        assertEquals(listOf(target), dir.listDirectoryEntries())
    }

    @Test
    fun `writing through a symlink updates the target and keeps the link`() {
        val real = dir.resolve("real.conf")
        real.writeText("old")
        val link = dir.resolve("link.conf")
        val created = runCatching { Files.createSymbolicLink(link, real) }.isSuccess
        assumeTrue(created, "symlinks not available on this platform")
        writeAtomically(link, "new")
        assertTrue(Files.isSymbolicLink(link))
        assertEquals("new", real.readText())
        assertEquals(2, dir.listDirectoryEntries().size)
    }

    @Test
    fun `keeps the target's POSIX permissions and gives new files rw-r--r--`() {
        assumeTrue(Files.getFileStore(dir).supportsFileAttributeView("posix"), "no POSIX permissions here")
        val target = dir.resolve("c.conf")
        target.writeText("old")
        Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-r-----"))
        writeAtomically(target, "new")
        assertEquals("rw-r-----", PosixFilePermissions.toString(Files.getPosixFilePermissions(target)))
        val created = dir.resolve("new.conf")
        writeAtomically(created, "x")
        assertEquals("rw-r--r--", PosixFilePermissions.toString(Files.getPosixFilePermissions(created)))
    }
}
