package com.hafiyanjye.app.realtime

import com.hafiyanjye.app.data.realtime.transport.SseMessage
import com.hafiyanjye.app.data.realtime.transport.SseParser
import com.hafiyanjye.app.data.realtime.transport.toRealtimeEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SseParserTest {
    private fun parseAll(vararg chunks: String): List<SseMessage> {
        val parser = SseParser()
        return chunks.flatMap { parser.feed(it) }
    }

    @Test
    fun `an engine event is one message`() {
        val messages = parseAll("event: ping.new\ndata: {\"type\":\"ping.new\",\"pingId\":7}\n\n")
        assertEquals(listOf(SseMessage("ping.new", """{"type":"ping.new","pingId":7}""", null)), messages)
    }

    @Test
    fun `retry and heartbeat comments produce nothing`() {
        assertTrue(parseAll("retry: 3000\n\n", ": keep-alive\n\n", ":\n\n").isEmpty())
    }

    @Test
    fun `a chunk boundary anywhere gives the same messages`() {
        val wire = "retry: 3000\n\n: keep-alive\n\n" +
            "event: chat.message\nid: 12\ndata: {\"a\":\"é\"}\n\n" +
            "event: notification\ndata: {\"n\":1}\n\n"
        val whole = parseAll(wire)
        assertEquals(2, whole.size)
        for (cut in 1 until wire.length) {
            assertEquals("cut at $cut", whole, parseAll(wire.substring(0, cut), wire.substring(cut)))
        }
        assertEquals(whole, parseAll(*wire.map { it.toString() }.toTypedArray()))
    }

    @Test
    fun `CRLF and bare CR end lines, also when split between CR and LF`() {
        val expected = listOf(SseMessage("trip.updated", "{}", null), SseMessage("notification", "{}", null))
        assertEquals(
            expected,
            parseAll("event: trip.updated\r\ndata: {}\r\n\r\nevent: notification\r\ndata: {}\r\n\r\n"),
        )
        assertEquals(
            expected,
            parseAll("event: trip.updated\r", "\ndata: {}\r", "\n\r", "\nevent: notification\rdata: {}\r\r"),
        )
    }

    @Test
    fun `data lines are joined with a newline`() {
        val messages = parseAll("data: {\ndata:  \"a\": 1\ndata:}\n\n")
        assertEquals(listOf(SseMessage(null, "{\n \"a\": 1\n}", null)), messages)
    }

    @Test
    fun `only one leading space is dropped and a field without a colon has an empty value`() {
        assertEquals("  x", parseAll("data:   x\n\n").single().data)
        assertEquals("", parseAll("data\n\n").single().data)
        assertEquals("a:b", parseAll("data:a:b\n\n").single().data)
    }

    @Test
    fun `the event name resets after each message and the id stays`() {
        val messages = parseAll("event: ping.new\nid: 5\ndata: 1\n\ndata: 2\n\n")
        assertEquals(listOf(SseMessage("ping.new", "1", "5"), SseMessage(null, "2", "5")), messages)
    }

    @Test
    fun `an event with no data is not dispatched, nor is an unfinished one`() {
        assertTrue(parseAll("event: ping.new\n\n").isEmpty())
        assertTrue(parseAll("event: ping.new\ndata: {}\n").isEmpty())
    }

    @Test
    fun `a leading byte order mark is ignored`() {
        assertEquals("x", parseAll("﻿data: x\n\n").single().data)
    }

    @Test
    fun `a message becomes a live event with the stream's type`() {
        val message = SseMessage(
            "session.updated",
            """{"type":"other","sessionId":3,"jobId":9,"eid":"e-1"}""",
            null,
        )
        val event = requireNotNull(message.toRealtimeEvent())
        assertEquals("session.updated", event.type)
        assertEquals("session.updated", event.string("type"))
        assertEquals(3, event.int("sessionId"))
        assertEquals("e-1", event.string("eid"))
    }

    @Test
    fun `unnamed messages and malformed payloads are ignored`() {
        assertNull(SseMessage(null, "{}", null).toRealtimeEvent())
        assertNull(SseMessage("ping.new", "{oops", null).toRealtimeEvent())
        assertNull(SseMessage("ping.new", "[1,2]", null).toRealtimeEvent())
        assertNull(SseMessage("ping.new", "\"text\"", null).toRealtimeEvent())
    }
}
