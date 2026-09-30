package com.flow2.comments

import io.ktor.http.Headers
import io.ktor.server.application.ApplicationCall
import java.net.InetAddress

internal fun visitorIp(call: ApplicationCall): String {
    return extractVisitorIp(call.request.headers, call.request.local.remoteAddress)
}

internal fun extractVisitorIp(headers: Headers, remoteAddress: String): String {
    // Production Nginx overwrites CF-Connecting-IP and X-Real-IP with its resolved client IP.
    val candidates = sequence {
        yield(headers["CF-Connecting-IP"])
        yield(headers["X-Real-IP"])

        headers.getAll("X-Forwarded-For").orEmpty().forEach { chain ->
            yieldAll(chain.split(','))
        }

        headers.getAll("Forwarded").orEmpty().forEach { chain ->
            for (hop in chain.split(',')) {
                val address = hop.split(';').firstOrNull {
                    it.substringBefore('=').trim().equals("for", ignoreCase = true)
                }?.substringAfter('=')
                yield(address)
            }
        }

        yield(remoteAddress)
    }

    return candidates.filterNotNull()
        .map(::normalizeVisitorIp)
        .firstOrNull { it != "unknown" }
        ?: "unknown"
}

internal fun normalizeVisitorIp(value: String): String {
    var address = value.trim().removeSurrounding("\"")

    // Forwarded may wrap IPv6 in brackets and append a port to either address family.
    if (address.startsWith('[')) {
        val match = Regex("^\\[([^]]+)](?::[0-9]+)?$").matchEntire(address) ?: return "unknown"
        address = match.groupValues[1]
    }
    else if (address.count { it == ':' } == 1 && '.' in address) {
        val match = Regex("^([0-9.]+):[0-9]+$").matchEntire(address) ?: return "unknown"
        address = match.groupValues[1]
    }

    if (':' in address) {
        // Scope IDs are local interface identifiers, not part of the IP itself.
        address = address.substringBefore('%')
        if (!address.matches(Regex("[0-9a-fA-F:.]+"))) return "unknown"

        return try {
            InetAddress.getByName(address).hostAddress
        }
        catch (_: Exception) {
            "unknown"
        }
    }

    val octets = address.split('.')
    val invalidOctet = octets.any { part ->
        part.isEmpty() || part.any { it !in '0'..'9' } || part.toIntOrNull() !in 0..255
    }
    if (octets.size != 4 || invalidOctet) return "unknown"

    return octets.joinToString(".") { it.toInt().toString() }
}
