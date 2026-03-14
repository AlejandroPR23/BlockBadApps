package com.example.blockbadapps

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class BlockingVpnService : VpnService() {

    // ─────────────────────────────────────────────────────────────────────────
    //  Constants
    // ─────────────────────────────────────────────────────────────────────────
    companion object {
        const val ACTION_START   = "com.example.blockbadapps.START_VPN"
        const val ACTION_STOP    = "com.example.blockbadapps.STOP_VPN"

        private const val NOTIF_CHANNEL  = "blockbadapps_vpn"
        private const val NOTIF_ID       = 1001

        // The TUN adapter address and our virtual DNS server live in 10.0.0.x
        private const val VPN_ADDR       = "10.0.0.2"
        private const val VIRTUAL_DNS    = "10.0.0.1"   // We answer DNS queries here
        private const val UPSTREAM_DNS   = "8.8.8.8"    // Forward allowed queries here

        @Volatile
        var isRunning = false
            private set
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  State
    // ─────────────────────────────────────────────────────────────────────────
    private var tun: ParcelFileDescriptor? = null
    private var packetThread: Thread?      = null

    // ─────────────────────────────────────────────────────────────────────────
    //  Lifecycle
    // ─────────────────────────────────────────────────────────────────────────
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return if (intent?.action == ACTION_STOP) {
            stopVpn()
            START_NOT_STICKY
        } else {
            startVpn()
            START_STICKY
        }
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  VPN setup / teardown
    // ─────────────────────────────────────────────────────────────────────────
    private fun startVpn() {
        if (isRunning) return

        val pfd = Builder()
            .setSession("BlockBadApps Shield")
            .addAddress(VPN_ADDR, 32)
            .addDnsServer(VIRTUAL_DNS)       // Tell Android: send all DNS to our virtual server
            .addRoute(VIRTUAL_DNS, 32)       // Only route traffic to 10.0.0.1 through the TUN
            .setMtu(1500)
            .setBlocking(true)               // FileInputStream.read() blocks until a packet arrives
            .addDisallowedApplication(packageName)  // Exclude ourselves to prevent loops
            .establish()

        if (pfd == null) { stopSelf(); return }

        tun       = pfd
        isRunning = true

        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())

        packetThread = Thread(::runPacketLoop, "vpn-packet-loop")
        packetThread!!.start()
    }

    private fun stopVpn() {
        if (!isRunning) return
        isRunning = false
        packetThread?.interrupt()
        packetThread = null
        tun?.close()
        tun = null
        stopForeground(true)
        stopSelf()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Main packet loop
    //
    //  Each read() returns one complete IPv4 packet from the TUN interface.
    //  We parse it, decide what to do, and optionally write a response back.
    // ─────────────────────────────────────────────────────────────────────────
    private fun runPacketLoop() {
        val fd  = tun?.fileDescriptor ?: return
        val inp = FileInputStream(fd)
        val out = FileOutputStream(fd)
        val buf = ByteArray(32767)

        while (isRunning && !Thread.currentThread().isInterrupted) {
            try {
                val len = inp.read(buf)
                if (len <= 0) continue

                val packet = buf.copyOf(len)
                routePacket(packet)?.let { out.write(it) }

            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (_: Exception) {
                if (!isRunning) break
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Packet routing (IPv4 only)
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Returns a response packet to write back into the TUN interface,
     * or null if the packet should be silently dropped / ignored.
     */
    private fun routePacket(pkt: ByteArray): ByteArray? {
        if (pkt.size < 20) return null

        val version  = (pkt[0].toInt() ushr 4) and 0xF
        if (version != 4) return null                    // Skip IPv6 for now

        val ipHLen   = (pkt[0].toInt() and 0xF) * 4
        val protocol = pkt[9].toInt() and 0xFF

        return when (protocol) {
            17 -> handleUdp(pkt, ipHLen)     // DNS lives here
            else -> null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  DNS interception (UDP port 53)
    // ─────────────────────────────────────────────────────────────────────────
    private fun handleUdp(pkt: ByteArray, ipHLen: Int): ByteArray? {
        if (pkt.size < ipHLen + 8) return null

        val dstPort = readU16(pkt, ipHLen + 2)
        if (dstPort != 53) return null

        val dnsPayload = pkt.copyOfRange(ipHLen + 8, pkt.size)
        val domain     = DnsPacketParser.extractDomain(dnsPayload)

        val dnsResponse = when {
            domain != null && isBlocked(domain) -> {
                // Domain is in the blocklist → reply with NXDOMAIN immediately
                DnsPacketParser.buildNxDomain(dnsPayload)
            }
            else -> {
                // Domain is allowed → forward to 8.8.8.8 and relay the real answer
                forwardDns(dnsPayload) ?: return null
            }
        }

        return wrapUdpResponse(pkt, ipHLen, dnsResponse)
    }

    /**
     * Sends the DNS query to upstream (8.8.8.8) using a socket that bypasses the VPN.
     * The call to protect() is what prevents an infinite routing loop.
     */
    private fun forwardDns(query: ByteArray): ByteArray? {
        return try {
            val sock = DatagramSocket()
            protect(sock)                            // ← Bypass our own VPN for this socket

            val server  = InetAddress.getByName(UPSTREAM_DNS)
            val request = DatagramPacket(query, query.size, server, 53)
            sock.soTimeout = 3000
            sock.send(request)

            val buf  = ByteArray(4096)
            val resp = DatagramPacket(buf, buf.size)
            sock.receive(resp)
            sock.close()

            buf.copyOf(resp.length)
        } catch (_: Exception) { null }
    }

    /**
     * Wraps a DNS payload in a UDP/IP response packet.
     * Source and destination addresses/ports are swapped from the original query.
     */
    private fun wrapUdpResponse(
        query: ByteArray,
        ipHLen: Int,
        dnsPayload: ByteArray
    ): ByteArray {
        val udpLen   = 8 + dnsPayload.size
        val totalLen = ipHLen + udpLen
        val out      = ByteArray(totalLen)

        // ── IP header ──────────────────────────────────────────────────────
        System.arraycopy(query, 0, out, 0, ipHLen)
        writeU16(out, 2, totalLen)                     // Update total length field

        System.arraycopy(query, 16, out, 12, 4)        // Original dst IP → new src IP
        System.arraycopy(query, 12, out, 16, 4)        // Original src IP → new dst IP

        writeU16(out, 10, 0)                           // Clear checksum before recalculating
        writeU16(out, 10, ipChecksum(out, ipHLen))

        // ── UDP header ─────────────────────────────────────────────────────
        val u = ipHLen
        out[u]     = query[u + 2]; out[u + 1] = query[u + 3]  // dst port → src port
        out[u + 2] = query[u];     out[u + 3] = query[u + 1]  // src port → dst port
        writeU16(out, u + 4, udpLen)
        writeU16(out, u + 6, 0)                        // UDP checksum = 0 (valid for IPv4)

        // ── DNS payload ────────────────────────────────────────────────────
        System.arraycopy(dnsPayload, 0, out, ipHLen + 8, dnsPayload.size)

        return out
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Blocklist check
    // ─────────────────────────────────────────────────────────────────────────
    private fun isBlocked(domain: String): Boolean {
        val prefs    = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val keywords = prefs.getStringSet(MainActivity.BLOCKED_SITES_KEY, emptySet()) ?: emptySet()
        // Match substring: "xvideos" blocks "xvideos.com", "www.xvideos.net", etc.
        return keywords.any { domain.contains(it, ignoreCase = true) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Byte helpers
    // ─────────────────────────────────────────────────────────────────────────
    private fun readU16(buf: ByteArray, off: Int): Int =
        ((buf[off].toInt() and 0xFF) shl 8) or (buf[off + 1].toInt() and 0xFF)

    private fun writeU16(buf: ByteArray, off: Int, value: Int) {
        buf[off]     = ((value ushr 8) and 0xFF).toByte()
        buf[off + 1] = (value and 0xFF).toByte()
    }

    private fun ipChecksum(header: ByteArray, len: Int): Int {
        var sum = 0
        for (i in 0 until len step 2) {
            sum += ((header[i].toInt() and 0xFF) shl 8) or (header[i + 1].toInt() and 0xFF)
        }
        while ((sum ushr 16) != 0) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv() and 0xFFFF
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Notification
    // ─────────────────────────────────────────────────────────────────────────
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                NOTIF_CHANNEL,
                "BlockBadApps VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Proteccion VPN activa" }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopVpn = PendingIntent.getService(
            this, 1,
            Intent(this, BlockingVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setContentTitle("BlockBadApps activo")
            .setContentText("Proteccion de contenido habilitada")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(openApp)
            .addAction(0, "Detener", stopVpn)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}