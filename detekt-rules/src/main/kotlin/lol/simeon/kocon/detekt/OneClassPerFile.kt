package lol.simeon.kocon.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile

/**
 * Flags every top-level class, interface or object after the first one in a file.
 * Nested declarations (such as sealed subclasses inside their parent) are not top-level
 * and are therefore allowed.
 */
class OneClassPerFile(config: Config) : Rule(
    config,
    "A file declares at most one top-level class, interface or object.",
) {
    override fun visitKtFile(file: KtFile) {
        super.visitKtFile(file)
        val declarations = file.declarations.filterIsInstance<KtClassOrObject>()
        val first = declarations.firstOrNull() ?: return
        declarations.drop(1).forEach { extra ->
            report(
                Finding(
                    Entity.atName(extra),
                    "'${extra.name}' is a second top-level declaration next to '${first.name}'; " +
                        "move it to its own file or nest it inside '${first.name}'.",
                ),
            )
        }
    }
}
