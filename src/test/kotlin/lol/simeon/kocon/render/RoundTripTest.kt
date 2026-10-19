package lol.simeon.kocon.render

import lol.simeon.kocon.CommentStyle
import lol.simeon.kocon.parse.HoconParser
import lol.simeon.kocon.tree.HoconValue.HoconList
import lol.simeon.kocon.tree.HoconValue.HoconNull
import lol.simeon.kocon.tree.HoconValue.HoconObject
import lol.simeon.kocon.tree.HoconValue.HoconScalar
import kotlin.test.Test
import kotlin.test.assertEquals

class RoundTripTest {
    private val tree = HoconObject(
        mapOf(
            "name" to HoconScalar("Hello \"world\" # // \n\t\u0001 ünï", quoted = true),
            "port" to HoconScalar("25565", quoted = false),
            "big" to HoconScalar("9223372036854775807", quoted = false),
            "ratio" to HoconScalar("-3.5e2", quoted = false),
            "flag" to HoconScalar("true", quoted = false),
            "quotedFlag" to HoconScalar("true", quoted = true),
            "nothing" to HoconNull,
            "a.b" to HoconScalar("dotted key", quoted = true),
            "" to HoconScalar("empty key", quoted = true),
            "empty" to HoconObject(emptyMap()),
            "none" to HoconList(emptyList()),
            "server" to HoconObject(
                mapOf(
                    "hosts" to HoconList(listOf(HoconScalar("a", quoted = true), HoconNull)),
                    "routes" to HoconList(listOf(HoconObject(mapOf("path" to HoconScalar("/", quoted = true))))),
                ),
            ),
        ),
    )

    @Test
    fun `parsing rendered output gives back the same tree`() {
        assertEquals(tree, HoconParser.parse(renderHocon(tree)))
    }

    @Test
    fun `comments in either style do not change the parsed tree`() {
        val commented = tree.copy(comments = mapOf("port" to "Port\nsecond line"), comment = "Root")
        CommentStyle.entries.forEach { style ->
            assertEquals(tree, HoconParser.parse(renderHocon(commented, style)))
        }
    }
}
