package lol.simeon.kocon.io

import lol.simeon.kocon.KoconException
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.PosixFilePermissions.fromString

/**
 * Writes [text] as UTF-8 to a temp file beside [target] and moves it into place, so a crash never
 * leaves a truncated file. An existing symlink is followed; failures become [KoconException].
 */
internal fun writeAtomically(target: Path, text: String) {
    try {
        val exists = Files.exists(target)
        val real = if (exists) target.toRealPath() else target.toAbsolutePath()
        // POSIX rename would silently replace a read-only file.
        if (exists && !Files.isWritable(real)) {
            throw AccessDeniedException(real.toString(), null, "not writable")
        }
        val dir = Files.createDirectories(real.parent)
        val temp = Files.createTempFile(dir, real.fileName.toString(), ".tmp")
        try {
            // createTempFile is 0600 on POSIX; keep the target's mode, or the usual 0644 for a new file.
            if (Files.getFileStore(temp).supportsFileAttributeView("posix")) {
                val perms = if (exists) Files.getPosixFilePermissions(real) else fromString("rw-r--r--")
                Files.setPosixFilePermissions(temp, perms)
            }
            Files.writeString(temp, text, Charsets.UTF_8)
            try {
                Files.move(temp, real, ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, real, REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    } catch (e: IOException) {
        throw KoconException("Could not write config file $target: ${e.message}", e)
    }
}
