package com.flow2.comments

import io.ktor.http.Headers
import kotlin.test.Test
import kotlin.test.assertEquals

class VisitorIpTest {
    @Test
    fun `headers have explicit precedence with socket address as fallback`() {
        val sources = listOf(
            "CF-Connecting-IP" to "192.0.2.1",
            "X-Real-IP" to "192.0.2.2",
            "X-Forwarded-For" to "192.0.2.3, 10.0.0.1",
            "Forwarded" to "for=192.0.2.4;proto=https",
        )
        for (start in sources.indices) {
            val headers = Headers.build {
                sources.drop(start).forEach { (name, value) -> append(name, value) }
            }
            assertEquals("192.0.2.${start + 1}", extractVisitorIp(headers, "127.0.0.1"))
        }
        assertEquals("127.0.0.1", extractVisitorIp(Headers.Empty, "127.0.0.1"))
        assertEquals(normalizeVisitorIp("::1"), extractVisitorIp(Headers.Empty, "::1"))
    }

    @Test
    fun `invalid headers do not mask valid fallback addresses`() {
        val headers = Headers.build {
            append("CF-Connecting-IP", "unknown")
            append("X-Real-IP", " ")
            append("X-Forwarded-For", "bad.example, 999.1.2.3, 192.0.2.5, 10.0.0.1")
        }
        assertEquals("192.0.2.5", extractVisitorIp(headers, "127.0.0.1"))
        assertEquals("127.0.0.1", extractVisitorIp(Headers.build {
            append("Forwarded", "for=_hidden;proto=https, for=unknown")
        }, "127.0.0.1"))
    }

    @Test
    fun `forwarded addresses support IPv6 quotes ports and multiple hops`() {
        val headers = Headers.build {
            append("Forwarded", "for=unknown, For=\"[2001:db8::1]:443\";proto=https, for=10.0.0.1")
        }
        assertEquals(normalizeVisitorIp("2001:db8::1"), extractVisitorIp(headers, "127.0.0.1"))
        assertEquals("192.0.2.1", normalizeVisitorIp(" \"192.0.2.1:5678\" "))
        assertEquals("192.0.2.1", normalizeVisitorIp("[::ffff:192.0.2.1]:5678"))
        assertEquals(normalizeVisitorIp("fe80::1"), normalizeVisitorIp("fe80::1%lo0"))
        assertEquals("unknown", normalizeVisitorIp("localhost"))
        assertEquals("unknown", normalizeVisitorIp("[2001:db8::1]garbage"))
    }
}
