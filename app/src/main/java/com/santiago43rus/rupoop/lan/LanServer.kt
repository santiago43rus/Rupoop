package com.santiago43rus.rupoop.lan

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.GeneralSecurityException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * TCP-сервер синхронизации. Работает, пока приложение на экране.
 * Протокол: кадры (LanWire), сопряжение по QR и зашифрованный обмен (LanCrypto.Channel).
 */
class LanServer(
    private val identity: LanIdentity,
    private val peers: PeerStore,
    private val access: RegistryAccess,
    private val onPairRequest: PairDecision,
    private val onPaired: (LanPeer) -> Unit = {},
    private val log: (String) -> Unit = {}
) {
    @Volatile var offer: PairingOffer? = null
    @Volatile var port: Int = 0
        private set

    private var server: ServerSocket? = null
    private val active = AtomicInteger(0)
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "lan-server").apply { isDaemon = true } }

    /** Запускает сервер. Возвращает порт. Сначала пробует [preferredPort], при занятости берёт любой. */
    @Synchronized
    fun start(preferredPort: Int = DEFAULT_PORT): Int {
        if (server != null) return port
        val ss = try {
            ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(preferredPort)) }
        } catch (_: IOException) {
            ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(0)) }
        }
        server = ss
        port = ss.localPort
        pool.execute { acceptLoop(ss) }
        return port
    }

    @Synchronized
    fun stop() {
        try { server?.close() } catch (_: IOException) {}
        server = null
        port = 0
        offer = null
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (!ss.isClosed) {
            val s = try { ss.accept() } catch (_: IOException) { break }
            if (active.incrementAndGet() > MAX_SESSIONS) {
                active.decrementAndGet()
                try { s.close() } catch (_: IOException) {}
                continue
            }
            pool.execute {
                try { handle(s) }
                catch (e: Exception) { log("session: ${e.javaClass.simpleName}") }
                finally {
                    active.decrementAndGet()
                    try { s.close() } catch (_: IOException) {}
                }
            }
        }
    }

    private fun handle(s: Socket) {
        s.soTimeout = READ_TIMEOUT_MS
        s.tcpNoDelay = true
        val inp = DataInputStream(BufferedInputStream(s.getInputStream()))
        val out = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
        val first = LanWire.read(inp)
        when (first.kind) {
            LanWire.K_PAIR_HELLO -> pair(s, inp, out, first.payload)
            LanWire.K_SYNC_HELLO -> sync(s, inp, out, first.payload)
        }
    }

    private fun authKey(k: ByteArray) =
        LanCrypto.hkdf(k, "rupoop-pair-v1".toByteArray(), "auth".toByteArray())

    private fun pair(s: Socket, inp: DataInputStream, out: DataOutputStream, hello: ByteArray) {
        val o = offer ?: return
        synchronized(o) {
            if (o.busy || o.used || o.failures >= MAX_FAILURES || System.currentTimeMillis() > o.expiresAtMs) return
            o.busy = true
        }
        try {
            val r = ByteReader(hello)
            if (r.u8() != 1) return
            val pubC = r.bytes16()
            val nonceC = r.fixed(16)
            val idC = r.fixed(16)
            val portC = r.u16()
            val nameC = r.string16()
            val bodyLen = r.pos
            val tagC = r.fixed(32)

            val kAuth = authKey(o.key)
            val expect = LanCrypto.hmac(kAuth, "C".toByteArray(), hello.copyOfRange(0, bodyLen))
            if (!LanCrypto.constantTimeEquals(expect, tagC)) {
                synchronized(o) { o.failures++ }
                return
            }

            s.soTimeout = DECISION_TIMEOUT_MS
            val accepted = try { onPairRequest.onRequest(nameC, LanCrypto.hex(idC)) } catch (_: Exception) { false }
            if (!accepted) {
                o.used = true
                LanWire.write(out, LanWire.K_PAIR_REJECT, ByteArray(0))
                return
            }

            val eph = LanCrypto.newEphemeral()
            val nonceS = LanCrypto.randomBytes(16)
            val bodyS = ByteWriter().u8(1).bytes16(eph.publicKey).raw(nonceS).raw(identity.deviceId)
                .string16(identity.name).toByteArray()
            val tagS = LanCrypto.hmac(kAuth, "S".toByteArray(), hello, bodyS)
            val p2 = bodyS + tagS
            LanWire.write(out, LanWire.K_PAIR_ACCEPT, p2)

            val pk = LanCrypto.hkdf(eph.agree(pubC), nonceC + nonceS, "rupoop-lan-pk-v1".toByteArray())
            val transcript = LanCrypto.sha256(hello, p2)

            val f4 = LanWire.read(inp)
            if (f4.kind != LanWire.K_PAIR_CONFIRM) return
            val expect4 = LanCrypto.hmac(pk, "confirm".toByteArray(), transcript)
            if (!LanCrypto.constantTimeEquals(expect4, f4.payload)) return

            val host = (s.remoteSocketAddress as? InetSocketAddress)?.address?.hostAddress
            val peer = LanPeer(LanCrypto.hex(idC), nameC, pk, host = host, port = portC)
            peers.put(peer)
            LanWire.write(out, LanWire.K_PAIR_DONE, LanCrypto.hmac(pk, "done".toByteArray(), transcript))
            o.used = true
            onPaired(peer)
        } finally {
            synchronized(o) { o.busy = false }
        }
    }

    private fun sync(s: Socket, inp: DataInputStream, out: DataOutputStream, hello: ByteArray) {
        if (hello.size < 32) return
        val r = ByteReader(hello)
        val idC = LanCrypto.hex(r.fixed(16))
        val clientPort = if (hello.size >= 34) r.u16() else 0
        val nonceC = r.fixed(16)
        val peer = peers.get(idC) ?: return            // чужой: молча закрываем
        val nonceS = LanCrypto.randomBytes(16)
        LanWire.write(out, LanWire.K_SYNC_NONCE, nonceS)
        val ch = LanCrypto.Channel.server(peer.key, nonceC, nonceS)

        fun reply(type: Int, body: ByteArray = ByteArray(0)) {
            LanWire.write(out, LanWire.K_DATA, ch.seal(byteArrayOf(type.toByte()) + body))
        }

        val remoteHost = (s.remoteSocketAddress as? InetSocketAddress)?.address?.hostAddress
        if (remoteHost != null) {
            peers.get(idC)?.let { existing ->
                val updatedPort = if (clientPort > 0) clientPort else existing.port
                if (remoteHost != existing.host || updatedPort != existing.port) {
                    peers.put(existing.copy(host = remoteHost, port = updatedPort))
                }
            }
        }

        while (true) {
            val f = try { LanWire.read(inp) } catch (_: IOException) { return }
            if (f.kind != LanWire.K_DATA) return
            val plain = try { ch.open(f.payload) } catch (_: GeneralSecurityException) { return }
            if (plain.isEmpty()) return
            val body = plain.copyOfRange(1, plain.size)
            when (plain[0].toInt()) {
                LanWire.T_HASH_REQ -> reply(LanWire.T_HASH_RESP, access.hash().toByteArray())
                LanWire.T_SYNC_REQ -> {
                    val merged = access.mergeAndApply(String(body, Charsets.UTF_8))
                    reply(LanWire.T_SYNC_RESP, merged.toByteArray(Charsets.UTF_8))
                }
                LanWire.T_UNPAIR -> { peers.remove(idC); reply(LanWire.T_OK); return }
                LanWire.T_BYE -> return
                else -> return
            }
        }
    }

    companion object {
        const val DEFAULT_PORT = 47931
        private const val MAX_SESSIONS = 4
        private const val MAX_FAILURES = 5
        private const val READ_TIMEOUT_MS = 15_000
        private const val DECISION_TIMEOUT_MS = 90_000
    }
}
