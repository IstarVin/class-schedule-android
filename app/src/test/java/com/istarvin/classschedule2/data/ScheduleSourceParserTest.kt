package com.istarvin.classschedule2.data

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleSourceParserTest {
    @Test
    fun `live Sue page tables match the bundled offline schedule`() {
        val actual = JSONObject(ScheduleSourceParser.parse(resource("sue-schedule-source.html")))
        val bundled = JSONObject(resource(ScheduleProfile.SUE.assetFileName))

        assertJsonEquals(bundled, actual)
        assertEquals(8, actual.getJSONObject("subjects").length())
        assertEquals(15, actual.getJSONArray("events").length())
    }

    @Test
    fun `both bundled schedules are valid cache sources`() {
        ScheduleProfile.entries.forEach { profile ->
            val parsed = JSONObject(ScheduleSourceParser.parse(resource(profile.assetFileName)))
            assertTrue(parsed.getJSONArray("events").length() > 0)
        }
    }

    @Test
    fun `literal punctuation and escaped quotes in names survive extraction`() {
        val page = """
            <script>
            const SUBJECTS = { TEST: { name: "A },] \"quote\" and { key: value", }, };
            const DATA = [{ day: 1, start: 450, end: 600, code: "TEST", room: "TBA", },];
            </script>
        """.trimIndent()
        val root = JSONObject(ScheduleSourceParser.parse(page))

        assertEquals("A },] \"quote\" and { key: value", root.getJSONObject("subjects").getJSONObject("TEST").getString("name"))
        assertEquals(1, root.getJSONArray("events").length())
    }

    @Test
    fun `empty schedules remain valid`() {
        val root = JSONObject(ScheduleSourceParser.parse("""{"subjects":{},"events":[]}"""))
        assertEquals(0, root.getJSONArray("events").length())
    }

    @Test
    fun `invalid downloads are rejected before cache replacement`() {
        val invalidSources = listOf(
            "<html>Service unavailable</html>",
            "const SUBJECTS = {}; const DATA = [",
            "const SUBJECTS = fetchSubjects(); const DATA = [];",
            """{"subjects":{},"events":[{"day":1,"start":450,"end":600,"code":"UNKNOWN"}]}""",
            """{"subjects":{"TEST":{}},"events":[{"day":8,"start":450,"end":600,"code":"TEST"}]}""",
            """{"subjects":{"TEST":{}},"events":[{"day":1,"start":600,"end":450,"code":"TEST"}]}""",
            """{"subjects":{"TEST":{}},"events":[null]}""",
        )
        invalidSources.forEach { source ->
            assertTrue("Should reject $source", runCatching { ScheduleSourceParser.parse(source) }.isFailure)
        }
    }

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)).bufferedReader().use { it.readText() }

    private fun assertJsonEquals(expected: Any, actual: Any) {
        when (expected) {
            is JSONObject -> {
                assertTrue(actual is JSONObject)
                actual as JSONObject
                val keys = expected.keys().asSequence().toSet()
                assertEquals(keys, actual.keys().asSequence().toSet())
                keys.forEach { key -> assertJsonEquals(expected.get(key), actual.get(key)) }
            }
            is JSONArray -> {
                assertTrue(actual is JSONArray)
                actual as JSONArray
                assertEquals(expected.length(), actual.length())
                for (index in 0 until expected.length()) assertJsonEquals(expected.get(index), actual.get(index))
            }
            else -> assertEquals(expected, actual)
        }
    }
}
