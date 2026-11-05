package app.upmile.core.gleo

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
}
