package app.upmile.core.gleo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class GleoIntentsTest {
    @Test fun routes() {
        assertEquals("routine", GleoIntents.routeFor("start_routine", emptyMap()))
        assertEquals("record", GleoIntents.routeFor("show_my_record", emptyMap()))
        assertEquals("preview/commute", GleoIntents.routeFor("preview_today_route", mapOf("destination" to "회사")))
        assertEquals("preview/cafe", GleoIntents.routeFor("preview_today_route", mapOf("destination" to "처음 가는 카페")))
        assertEquals("preview/inlaws", GleoIntents.routeFor("preview_today_route", mapOf("destination" to "처가")))
        assertEquals("preview/inlaws", GleoIntents.routeFor("preview_today_route", mapOf("destination" to "inlaws")))
        assertEquals("preview", GleoIntents.routeFor("preview_today_route", mapOf("destination" to "달나라")))
        assertEquals("preview", GleoIntents.routeFor("preview_today_route", emptyMap()))
        assertNull(GleoIntents.routeFor("nope", emptyMap()))
        assertEquals("routine", GleoIntents.routeFor("START_ROUTINE", emptyMap()))
        assertEquals("record", GleoIntents.routeFor("SHOW_MY_RECORD", emptyMap()))
        assertEquals("preview/cafe", GleoIntents.routeFor("PREVIEW_TODAY_ROUTE", mapOf("destination" to " cafe ")))
        assertEquals("preview/commute", GleoIntents.routeFor("PREVIEW_TODAY_ROUTE", mapOf("destination" to "commute")))
        assertNull(GleoIntents.routeFor("UNKNOWN_THING", emptyMap()))
    }

    @Test fun params() {
        assertEquals(mapOf("destination" to "cafe", "n" to "2"), GleoIntents.params("""{"destination":"cafe","n":2}"""))
        assertEquals(mapOf("destination" to "cafe"), GleoIntents.params(""""{\"destination\":\"cafe\"}""""))
        assertEquals(emptyMap<String, String>(), GleoIntents.params(null))
        assertEquals(emptyMap<String, String>(), GleoIntents.params("  "))
        assertEquals(emptyMap<String, String>(), GleoIntents.params("{oops"))
        assertEquals(emptyMap<String, String>(), GleoIntents.params("[1,2]"))
    }

    private fun reply(a: String, d: String? = null, parked: Boolean = true) =
        GleoIntents.reply(a, if (d == null) emptyMap() else mapOf("destination" to d), parked)

    @Test fun replies() {
        reply("START_ROUTINE")!!.let {
            assertEquals(GleoReply("START_ROUTINE", "success", "Started the pre-departure routine.", "routine"), it)
        }
        assertEquals(GleoReply("SHOW_MY_RECORD", "success", "Opened your driving record.", "record"), reply("SHOW_MY_RECORD"))
        assertEquals("Opened the route preview to work.", reply("PREVIEW_TODAY_ROUTE", "commute")!!.message)
        assertEquals("Opened the route preview to the new cafe.", reply("PREVIEW_TODAY_ROUTE", "cafe")!!.message)
        assertEquals("Opened the route preview to the in-laws' home.", reply("PREVIEW_TODAY_ROUTE", "처가")!!.message)
        assertEquals("preview/inlaws", reply("PREVIEW_TODAY_ROUTE", "inlaws")!!.route)
        val asking = GleoReply(
            "PREVIEW_TODAY_ROUTE", "asking",
            "Which destination should I preview: work, the new cafe, or the in-laws' home?", "preview",
        )
        assertEquals(asking, reply("PREVIEW_TODAY_ROUTE"))
        assertEquals(asking, reply("PREVIEW_TODAY_ROUTE", "달나라"))
        val fail = GleoReply("PREVIEW_TODAY_ROUTE", "fail", "Upmile can be used only while the car is parked.", null)
        assertEquals(fail, reply("PREVIEW_TODAY_ROUTE", null, parked = false))
        assertEquals("fail", reply("START_ROUTINE", parked = false)!!.status)
        assertNull(reply("android.intent.action.MAIN"))
        assertEquals("START_ROUTINE", reply(" start_routine ")!!.functionName)
    }

    @Test fun jsonRoundTrip() {
        val o = Json.parseToJsonElement(GleoReply("START_ROUTINE", "success", "안녕 \"quoted\"", "routine").json()).jsonObject
        assertEquals(setOf("functionName", "status", "message"), o.keys)
        assertEquals("START_ROUTINE", o["functionName"]!!.jsonPrimitive.content)
        assertEquals("success", o["status"]!!.jsonPrimitive.content)
        assertEquals("안녕 \"quoted\"", o["message"]!!.jsonPrimitive.content)
    }
}
