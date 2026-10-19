package lol.simeon.kocon.detekt

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

class KoconRuleSetProvider : RuleSetProvider {
    override val ruleSetId = RuleSetId("kocon")

    override fun instance() = RuleSet(ruleSetId, listOf(::OneClassPerFile))
}
