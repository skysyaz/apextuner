package com.apextuner.vpn.dns

import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Minimal IPv4 UDP/53 forwarder for DNS-only VPN mode.
 *
 * Only packets destined to the configured DNS servers (routed into the tun)
 * arrive here. We protect() a DatagramSocket, forward the DNS query upstream,
 * and write the response back onto the tun with swapped addresses.
 *
 * No root required — this is how DNS changers work on stock Android.
 */
class DnsTunForwarder(
    private val vpn: VpnService,
    private val tun: ParcelFileDescriptor,
    private val upstreamDns: List<InetAddress>
) {
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        worker = thread(name = "ApexDnsForwarder", isDaemon = true) {
            val input = FileInputStream(tun.fileDescriptor)
            val output = FileOutputStream(tun.fileDescriptor)
            val buf = ByteArray(32767)
            try {
                while (running.get()) {
                    val len = try {
                        input.read(buf)
                    } catch (_: Throwable) {
                        break
                    }
                    if (len <= 0) break
                    handlePacket(buf, len, output)
                }
            } finally {
                runCatching { input.close() }
                runCatching { output.close() }
            }
        }
    }

    fun stop() {
        running.set(false)
        worker?.interrupt()
        worker = null
    }

    private fun handlePacket(buf: ByteArray, len: Int, output: FileOutputStream) {
        if (len < 28) return // min IPv4 + UDP
        val version = (buf[0].toInt() ushr 4) and 0x0f
        if (version != 4) return // IPv6 not handled in this light forwarder
        val ihl = (buf[0].toInt() and 0x0f) * 4
        if (ihl < 20 || len < ihl + 8) return
        val protocol = buf[9].toInt() and 0xff
        if (protocol != 17) return // UDP only

        val destPort = ((buf[ihl + 2].toInt() and 0xff) shl 8) or (buf[ihl + 3].toInt() and 0xff)
        if (destPort != 53) return

        val srcIp = buf.copyOfRange(12, 16)
        val dstIp = buf.copyOfRange(16, 20)
        val srcPort = ((buf[ihl].toInt() and 0xff) shl 8) or (buf[ihl + 1].toInt() and 0xff)
        val udpLen = ((buf[ihl + 4].toInt() and 0xff) shl 8) or (buf[ihl + 5].toInt() and 0xff)
        val payloadOffset = ihl + 8
        val payloadLen = (udpLen - 8).coerceAtMost(len - payloadOffset).coerceAtLeast(0)
        if (payloadLen <= 0) return

        val query = buf.copyOfRange(payloadOffset, payloadOffset + payloadLen)
        val upstream = pickUpstream(dstIp) ?: return
        val response = queryUpstream(upstream, query) ?: return
        val reply = buildIpv4UdpReply(
            srcIp = dstIp, // original dest becomes source
            dstIp = srcIp,
            srcPort = 53,
            dstPort = srcPort,
            payload = response
        ) ?: return
        runCatching { output.write(reply) }
    }

    private fun pickUpstream(dstIp: ByteArray): InetAddress? {
        val matched = upstreamDns.firstOrNull { it.address.contentEquals(dstIp) }
        return matched ?: upstreamDns.firstOrNull()
    }

    private fun queryUpstream(server: InetAddress, query: ByteArray): ByteArray? {
        return try {
            DatagramSocket().use { socket ->
                vpn.protect(socket)
                socket.soTimeout = 3_000
                val packet = DatagramPacket(query, query.size, server, 53)
                socket.send(packet)
                val buf = ByteArray(4096)
                val resp = DatagramPacket(buf, buf.size)
                socket.receive(resp)
                buf.copyOf(resp.length)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun buildIpv4UdpReply(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray? {
        val ihl = 20
        val udpLen = 8 + payload.size
        val total = ihl + udpLen
        if (total > 32767) return null
        val out = ByteArray(total)

        // IPv4 header
        out[0] = 0x45.toByte()
        out[1] = 0
        out[2] = ((total ushr 8) and 0xff).toByte()
        out[3] = (total and 0xff).toByte()
        out[4] = 0; out[5] = 0 // id
        out[6] = 0x40.toByte(); out[7] = 0 // DF
        out[8] = 64 // TTL
        out[9] = 17 // UDP
        // checksum filled later
        System.arraycopy(srcIp, 0, out, 12, 4)
        System.arraycopy(dstIp, 0, out, 16, 4)
        val ipChecksum = checksum(out, 0, ihl)
        out[10] = ((ipChecksum ushr 8) and 0xff).toByte()
        out[11] = (ipChecksum and 0xff).toByte()

        // UDP header
        out[ihl] = ((srcPort ushr 8) and 0xff).toByte()
        out[ihl + 1] = (srcPort and 0xff).toByte()
        out[ihl + 2] = ((dstPort ushr 8) and 0xff).toByte()
        out[ihl + 3] = (dstPort and 0xff).toByte()
        out[ihl + 4] = ((udpLen ushr 8) and 0xff).toByte()
        out[ihl + 5] = (udpLen and 0xff).toByte()
        out[ihl + 6] = 0; out[ihl + 7] = 0 // checksum optional for IPv4
        System.arraycopy(payload, 0, out, ihl + 8, payload.size)

        // Optional UDP checksum with pseudo-header
        val udpChecksum = udpChecksum(srcIp, dstIp, out, ihl, udpLen)
        out[ihl + 6] = ((udpChecksum ushr 8) and 0xff).toByte()
        out[ihl + 7] = (udpChecksum and 0xff).toByte()
        return out
    }

    private fun checksum(buf: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        while (i < offset + length - 1) {
            sum += ((buf[i].toInt() and 0xff) shl 8) or (buf[i + 1].toInt() and 0xff)
            i += 2
        }
        if (length % 2 != 0) sum += (buf[offset + length - 1].toInt() and 0xff) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xffff) + (sum shr 16)
        return (sum.inv() and 0xffff).toInt()
    }

    private fun udpChecksum(
        srcIp: ByteArray, dstIp: ByteArray, packet: ByteArray, udpOffset: Int, udpLen: Int
    ): Int {
        val pseudo = ByteBuffer.allocate(12 + udpLen)
        pseudo.put(srcIp)
        pseudo.put(dstIp)
        pseudo.put(0)
        pseudo.put(17.toByte())
        pseudo.putShort(udpLen.toShort())
        pseudo.put(packet, udpOffset, udpLen)
        val arr = pseudo.array()
        // Zero existing checksum bytes in the copy for calculation — already 0.
        val c = checksum(arr, 0, arr.size)
        return if (c == 0) 0xffff else c
    }
}
