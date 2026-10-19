package lol.simeon.kocon.tree

import lol.simeon.kocon.tree.HoconValue.HoconList
import lol.simeon.kocon.tree.HoconValue.HoconNull
import lol.simeon.kocon.tree.HoconValue.HoconObject
import lol.simeon.kocon.tree.HoconValue.HoconScalar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class HoconValueTest {
    @Test
    fun `quoted scalar differs from bare scalar with same text`() {
        assertNotEquals<HoconValue>(HoconScalar("true", quoted = true), HoconScalar("true", quoted = false))
    }

    @Test
    fun `objects compare by entries, comments and own comment`() {
        val entries = mapOf("a" to HoconList(listOf(HoconNull)))
        assertEquals(HoconObject(entries), HoconObject(entries))
        assertNotEquals(HoconObject(entries), HoconObject(entries, comments = mapOf("a" to "note")))
        assertNotEquals(HoconObject(entries), HoconObject(entries, comment = "class note"))
    }
}
