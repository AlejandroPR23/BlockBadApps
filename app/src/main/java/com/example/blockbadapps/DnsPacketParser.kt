package com.example.blockbadapps

/**
 * Parses raw DNS payloads (without IP/UDP headers) and builds NXDOMAIN responses.
 */
object DnsPacketParser {

    /**
     * Extracts the queried domain from a DNS payload.
     * Returns null if the packet is malformed.
     */
    fun extractDomain(dns: ByteArray): String? {
        if (dns.size < 13) return null
        return try {
            parseName(dns, 12).first.lowercase()
        } catch (_: Exception) { null }
    }

    /**
     * Builds a NXDOMAIN response reusing the original query's header and question.
     * Only the flags and record counts change — everything else stays identical.
     */
    fun buildNxDomain(query: ByteArray): ByteArray {
        val r = query.copyOf()
        // Flags: QR=1 (response) | AA=1 (authoritative) | RD=1 (copy) | RCODE=3 (NXDOMAIN)
        // Byte[2]: 0x81 = 1000_0001   Byte[3]: 0x83 = 1000_0011
        r[2] = 0x81.toByte()
        r[3] = 0x83.toByte()
        // Zero out answer / authority / additional counts
        r[6] = 0;  r[7]  = 0   // ANCOUNT
        r[8] = 0;  r[9]  = 0   // NSCOUNT
        r[10] = 0; r[11] = 0   // ARCOUNT
        return r
    }

    // ─── Internal ────────────────────────────────────────────────────────────

    /**
     * Parses a DNS-encoded name at [offset].
     * Handles pointer compression (0xC0 prefix).
     * Returns the decoded domain and the offset immediately after the name.
     */
    private fun parseName(dns: ByteArray, offset: Int): Pair<String, Int> {
        val labels  = mutableListOf<String>()
        var pos     = offset
        var jumped  = false
        var endPos  = -1

        while (pos < dns.size) {
            val len = dns[pos].toInt() and 0xFF
            when {
                len == 0 -> { pos++; break }

                (len and 0xC0) == 0xC0 -> {          // Pointer compression
                    if (!jumped) endPos = pos + 2
                    jumped = true
                    pos = ((len and 0x3F) shl 8) or (dns[pos + 1].toInt() and 0xFF)
                }

                else -> {                              // Regular label
                    pos++
                    if (pos + len > dns.size) break
                    labels.add(String(dns, pos, len, Charsets.US_ASCII))
                    pos += len
                }
            }
        }

        return Pair(labels.joinToString("."), if (jumped) endPos else pos)
    }
}