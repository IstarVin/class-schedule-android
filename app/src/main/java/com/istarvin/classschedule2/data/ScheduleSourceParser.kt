package com.istarvin.classschedule2.data

import org.json.JSONArray
import org.json.JSONObject

/** Reads JSON or the literal schedule tables in Sue's page, without executing JavaScript. */
internal object ScheduleSourceParser {
    fun parse(source: String): String {
        val root = if (source.trimStart().startsWith("{")) {
            JSONObject(source)
        } else {
            val subjects = literal(source, "SUBJECTS", '{', '}')
            val events = literal(source, "DATA", '[', ']')
            JSONObject()
                .put("subjects", JSONObject(toJsonLiteral(subjects)))
                .put("events", JSONArray(toJsonLiteral(events)))
        }
        requireValidSchedule(root)
        return root.toString()
    }

    private fun literal(source: String, name: String, open: Char, close: Char): String {
        val declaration = Regex("\\bconst\\s+$name\\s*=\\s*").find(source)
            ?: throw IllegalArgumentException("Schedule page has no $name table")
        val start = declaration.range.last + 1
        require(source.getOrNull(start) == open) { "Schedule $name is not a literal table" }
        var depth = 0
        var quoted = false
        var escaped = false
        for (index in start until source.length) {
            val char = source[index]
            if (quoted) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') quoted = false
            } else {
                when (char) {
                    '"' -> quoted = true
                    open -> depth++
                    close -> if (--depth == 0) return source.substring(start, index + 1)
                }
            }
        }
        throw IllegalArgumentException("Schedule $name table is incomplete")
    }

    /** Only quotes bare keys and removes trailing commas; strings are copied verbatim. */
    private fun toJsonLiteral(literal: String): String = buildString {
        var index = 0
        while (index < literal.length) {
            val char = literal[index]
            when {
                char == '"' -> {
                    append(char)
                    index++
                    while (index < literal.length) {
                        val next = literal[index++]
                        append(next)
                        if (next == '\\' && index < literal.length) append(literal[index++])
                        else if (next == '"') break
                    }
                }
                char == ',' -> {
                    val next = literal.indexOfFirstNonWhitespace(index + 1)
                    if (literal.getOrNull(next) != '}' && literal.getOrNull(next) != ']') append(char)
                    index++
                }
                char.isLetter() || char == '_' || char == '$' -> {
                    val start = index++
                    while (index < literal.length &&
                        (literal[index].isLetterOrDigit() || literal[index] == '_' || literal[index] == '$')) index++
                    val token = literal.substring(start, index)
                    val next = literal.indexOfFirstNonWhitespace(index)
                    if (literal.getOrNull(next) == ':') append(JSONObject.quote(token)) else append(token)
                }
                else -> {
                    append(char)
                    index++
                }
            }
        }
    }

    private fun String.indexOfFirstNonWhitespace(start: Int): Int {
        var index = start
        while (index < length && this[index].isWhitespace()) index++
        return index
    }

    private fun requireValidSchedule(root: JSONObject) {
        val subjects = root.optJSONObject("subjects")
        val events = root.optJSONArray("events")
        require(subjects != null) { "Schedule has no subjects object" }
        require(events != null) { "Schedule has no events array" }
        for (index in 0 until events.length()) {
            val event = events.optJSONObject(index)
                ?: throw IllegalArgumentException("Schedule event $index is invalid")
            val start = event.optInt("start", -1)
            val end = event.optInt("end", -1)
            require(event.optInt("day") in 1..7 && start in 0..1439 && end in 1..1440 && end > start) {
                "Schedule event $index has invalid times"
            }
            require(event.optString("code").isNotBlank() && subjects.optJSONObject(event.optString("code")) != null) {
                "Schedule event $index has no subject"
            }
        }
    }
}
