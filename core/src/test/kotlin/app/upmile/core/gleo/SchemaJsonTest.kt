package app.upmile.core.gleo

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SchemaJsonTest {
    private val root = Json.parseToJsonElement(
        javaClass.classLoader.getResource("schema.json")!!.readText()
    ).jsonObject

    @Test fun matchesOfficialGleoShape() {
        assertEquals("app.upmile", root["packageName"]!!.jsonPrimitive.content)
        val calls = root["actionCalls"]!!.jsonArray
        assertTrue(calls.size in 1..3)
        val hangul = Regex("[가-힣]")
        val name = Regex("^[A-Z][A-Z0-9_]*$")
        for (c in calls.map { it.jsonObject }) {
            assertEquals("function", c["type"]!!.jsonPrimitive.content)
            assertEquals("activity", c["component"]!!.jsonPrimitive.content)
            val f = c["function"]!!.jsonObject
            assertTrue(name.matches(f["name"]!!.jsonPrimitive.content))
            assertFalse(hangul.containsMatchIn(f["description"]!!.jsonPrimitive.content))
            f["parameters"]!!.jsonObject["properties"]!!.jsonObject.values.forEach {
                assertFalse(hangul.containsMatchIn(it.jsonObject["description"]!!.jsonPrimitive.content))
            }
        }
        assertFalse(hangul.containsMatchIn(root["appDescription"]!!.jsonPrimitive.content))
    }
}
