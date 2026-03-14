package com.example.blockbadapps

/**
 * Extracts the SNI (Server Name Indication) from a TLS ClientHello.
 *
 * The SNI is sent in PLAINTEXT as part of the TLS handshake — no decryption needed.
 * This lets us block HTTPS connections by domain before they establish.
 */
object SniExtractor {

    /**
     * Parses a raw TCP payload looking for a TLS ClientHello with an SNI extension.
     * Returns the server name string, or null if not found / not a ClientHello.
     */
    fun extract(tcpPayload: ByteArray): String? {
        if (tcpPayload.size < 43) return null

        // TLS Record header[0] = Content Type: 0x16 = Handshake
        if (tcpPayload[0].toInt() and 0xFF != 0x16) return null

        // Handshake header[5] = Handshake Type: 0x01 = ClientHello
        if (tcpPayload[5].toInt() and 0xFF != 0x01) return null

        // ClientHello body starts at offset 9:
        //   TLS record header  = 5 bytes (type[1] + version[2] + length[2])
        //   Handshake header   = 4 bytes (type[1] + length[3])
        //   → body offset = 9
        var pos = 9

        // Skip Legacy Version (2) + Random (32)
        pos += 34
        if (pos >= tcpPayload.size) return null

        // Skip Session ID
        val sessionLen = tcpPayload[pos++].toInt() and 0xFF
        pos += sessionLen
        if (pos + 2 > tcpPayload.size) return null

        // Skip Cipher Suites
        val cipherLen = readU16(tcpPayload, pos)
        pos += 2 + cipherLen
        if (pos + 1 > tcpPayload.size) return null

        // Skip Compression Methods
        val compLen = tcpPayload[pos++].toInt() and 0xFF
        pos += compLen
        if (pos + 2 > tcpPayload.size) return null

        // Extensions block
        val extTotal = readU16(tcpPayload, pos)
        pos += 2
        val extEnd = minOf(pos + extTotal, tcpPayload.size)

        while (pos + 4 <= extEnd) {
            val extType = readU16(tcpPayload, pos)
            val extLen  = readU16(tcpPayload, pos + 2)
            pos += 4

            if (extType == 0x0000 /* server_name */) {
                // SNI wire format:
                //   server_name_list_length (2)
                //   name_type               (1) — 0x00 = host_name
                //   name_length             (2)
                //   name                    (nameLen bytes, ASCII)
                if (pos + 5 > tcpPayload.size) return null
                val nameLen   = readU16(tcpPayload, pos + 3)
                val nameStart = pos + 5
                if (nameStart + nameLen > tcpPayload.size) return null
                return String(tcpPayload, nameStart, nameLen, Charsets.US_ASCII)
            }
            pos += extLen
        }
        return null
    }

    private fun readU16(buf: ByteArray, off: Int): Int =
        ((buf[off].toInt() and 0xFF) shl 8) or (buf[off + 1].toInt() and 0xFF)
}