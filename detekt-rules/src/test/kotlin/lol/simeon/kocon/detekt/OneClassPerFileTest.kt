package lol.simeon.kocon.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OneClassPerFileTest {
    private val rule = OneClassPerFile(Config.empty)

    @Test
    fun `single class passes`() {
        assertTrue(rule.lint("class Npc").isEmpty())
    }

    @Test
    fun `file without classes passes`() {
        assertTrue(rule.lint("fun walk() = Unit\nval speed = 1").isEmpty())
    }

    @Test
    fun `class with top-level functions and typealias passes`() {
        val code = """
            class Path
            typealias Nodes = List<Int>
            fun Path.length() = 0
        """.trimIndent()
        assertTrue(rule.lint(code).isEmpty())
    }

    @Test
    fun `sealed subclasses nested inside their parent pass`() {
        val code = """
            sealed interface Movement {
                data object Walk : Movement
                data class Fall(val height: Int) : Movement
            }
        """.trimIndent()
        assertTrue(rule.lint(code).isEmpty())
    }

    @Test
    fun `second top-level class is reported`() {
        val findings = rule.lint("class Npc\nclass Route")
        assertEquals(1, findings.size)
        assertTrue(findings.single().message.startsWith("'Route'"))
    }

    @Test
    fun `interfaces and objects count`() {
        val code = """
            interface WorldView
            object Registry
            enum class Mode { LOOP }
        """.trimIndent()
        assertEquals(2, rule.lint(code).size)
    }

    @Test
    fun `provider exposes the rule`() {
        val provider = KoconRuleSetProvider()
        val ruleSet = provider.instance()
        assertEquals("kocon", ruleSet.id.value)
        assertEquals(listOf("OneClassPerFile"), ruleSet.rules.keys.map { it.value })
        assertTrue(ruleSet.rules.values.single()(Config.empty) is OneClassPerFile)
    }
}
