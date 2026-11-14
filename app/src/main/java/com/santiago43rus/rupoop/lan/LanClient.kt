package com.santiago43rus.rupoop.lan

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.GeneralSecurityException

sealed class SyncOutcome {
    /** Данные уже совпадали. */
    object Same : SyncOutcome()
    /** Обмен выполнен, реестры слиты. */
    object Merged : SyncOutcome()
    /** Партнёр не знает нас: нужно сопряжение заново. */
    object Forgotten : SyncOutcome()
    /** Ключ не подошёл: партнёр переустановлен или подменён. */
    object KeyMismatch : SyncOutcome()
    class Unreachable(val reason: String) : SyncOutcome()
}

sealed class PairOutcome {
    class Paired(val peer: LanPeer) : PairOutcome()
    /** Пользователь нажал «Нет» на экране ТВ. */
    object Rejected : PairOutcome()
    /** QR устарел, использован или заблокирован: сервер закрыл соединение без ответа. */
    object Expired : PairOutcome()
    class Failed(val reason: String) : PairOutcome()
}

class LanClient(
    private val identity: LanIdentity,
    private val peers: PeerStore,
    private val access: RegistryAccess,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    private fun open(host: String, port: Int, readTimeoutMs: Int): Socket {
        val s = Socket()
        try {
            s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        } catch (e: IOException) {
            try { s.close() } catch (_: IOException) {}
            throw e
        }
        s.soTimeout = readTimeoutMs
        s.tcpNoDelay = true
        return s
    }

    /** Блокирующая синхронизация с партнёром. Вызывать не из главного потока. */
    fun syncOnce(peer: LanPeer, host: String, port: Int, myPort: Int = 0): SyncOutcome {
        try {
            open(host, port, READ_TIMEOUT_MS).use { s ->
                val inp = DataInputStream(BufferedInputStream(s.getInputStream()))
                val out = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
                val nonceC = LanCrypto.randomBytes(16)
                val helloPayload = ByteWriter()
                    .raw(identity.deviceId)
                    .u16(myPort)
                    .raw(nonceC)
                    .toByteArray()
                LanWire.write(out, LanWire.K_SYNC_HELLO, helloPayload)
                val f = try { LanWire.read(inp) } catch (_: EOFException) { return SyncOutcome.Forgotten }
                if (f.kind != LanWire.K_SYNC_NONCE || f.payload.size != 16) return SyncOutcome.Unreachable("protocol")
                val ch = LanCrypto.Channel.client(peer.key, nonceC, f.payload)

                fun request(type: Int, body: ByteArray = ByteArray(0)): ByteArray {
                    LanWire.write(out, LanWire.K_DATA, ch.seal(byteArrayOf(type.toByte()) + body))
                    val r = LanWire.read(inp)
                    if (r.kind != LanWire.K_DATA) throw IOException("protocol")
                    val plain = ch.open(r.payload)
                    if (plain.isEmpty()) throw IOException("protocol")
                    return plain.copyOfRange(1, plain.size)
                }

                try {
                    val remoteHash = try {
                        String(request(LanWire.T_HASH_REQ), Charsets.UTF_8)
                    } catch (_: EOFException) {
                        return SyncOutcome.KeyMismatch      // сервер закрыл соединение: наш ключ ему не подошёл
                    }
                    if (remoteHash == access.hash()) {
                        peers.get(peer.deviceId)?.let { peers.put(it.copy(host = host, port = port, lastSyncAt = now(), lastHash = remoteHash)) }
                        LanWire.write(out, LanWire.K_DATA, ch.seal(byteArrayOf(LanWire.T_BYE.toByte())))
                        return SyncOutcome.Same
                    }
                    val mergedJson = String(request(LanWire.T_SYNC_REQ, access.snapshot().toByteArray(Charsets.UTF_8)), Charsets.UTF_8)
                    access.mergeAndApply(mergedJson)
                    peers.get(peer.deviceId)?.let { peers.put(it.copy(host = host, port = port, lastSyncAt = now(), lastHash = access.hash())) }
                    LanWire.write(out, LanWire.K_DATA, ch.seal(byteArrayOf(LanWire.T_BYE.toByte())))
                    return SyncOutcome.Merged
                } catch (_: GeneralSecurityException) {
                    return SyncOutcome.KeyMismatch
                }
            }
        } catch (e: IOException) {
            return SyncOutcome.Unreachable(e.javaClass.simpleName)
        }
    }

    /** Попросить партнёра забыть нас и удалить его у себя. Ошибки сети не мешают локальному удалению. */
    fun unpair(peer: LanPeer, host: String?, port: Int) {
        try {
            if (host != null && port > 0) {
                open(host, port, READ_TIMEOUT_MS).use { s ->
                    val inp = DataInputStream(BufferedInputStream(s.getInputStream()))
                    val out = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
                    val nonceC = LanCrypto.randomBytes(16)
                    val helloPayload = ByteWriter()
                        .raw(identity.deviceId)
                        .u16(0)
                        .raw(nonceC)
                        .toByteArray()
                    LanWire.write(out, LanWire.K_SYNC_HELLO, helloPayload)
                    val f = LanWire.read(inp)
                    val ch = LanCrypto.Channel.client(peer.key, nonceC, f.payload)
                    LanWire.write(out, LanWire.K_DATA, ch.seal(byteArrayOf(LanWire.T_UNPAIR.toByte())))
                    LanWire.read(inp)
                }
            }
        } catch (_: Exception) {
        } finally {
            peers.remove(peer.deviceId)
        }
    }

    /** Сопряжение по данным из QR. [myPort] — порт нашего сервера (0, если он не запущен). */
    fun pair(qr: PairingQr.Data, myPort: Int): PairOutcome {
        try {
            open(qr.host, qr.port, PAIR_READ_TIMEOUT_MS).use { s ->
                val inp = DataInputStream(BufferedInputStream(s.getInputStream()))
                val out = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
                val kAuth = LanCrypto.hkdf(qr.key, "rupoop-pair-v1".toByteArray(), "auth".toByteArray())
                val eph = LanCrypto.newEphemeral()
                val nonceC = LanCrypto.randomBytes(16)
                val body = ByteWriter().u8(1).bytes16(eph.publicKey).raw(nonceC).raw(identity.deviceId)
                    .u16(myPort).string16(identity.name).toByteArray()
                val hello = body + LanCrypto.hmac(kAuth, "C".toByteArray(), body)
                LanWire.write(out, LanWire.K_PAIR_HELLO, hello)

                val f = try { LanWire.read(inp) } catch (_: EOFException) { return PairOutcome.Expired }
                if (f.kind == LanWire.K_PAIR_REJECT) return PairOutcome.Rejected
                if (f.kind != LanWire.K_PAIR_ACCEPT) return PairOutcome.Failed("protocol")

                val p2 = f.payload
                val r = ByteReader(p2)
                if (r.u8() != 1) return PairOutcome.Failed("version")
                val pubS = r.bytes16()
                val nonceS = r.fixed(16)
                val idS = r.fixed(16)
                val nameS = r.string16()
                val bodyLen = r.pos
                val tagS = r.fixed(32)
                val expect = LanCrypto.hmac(kAuth, "S".toByteArray(), hello, p2.copyOfRange(0, bodyLen))
                if (!LanCrypto.constantTimeEquals(expect, tagS)) return PairOutcome.Failed("auth")
                if (LanCrypto.hex(idS) != qr.deviceId) return PairOutcome.Failed("id")

                val pk = LanCrypto.hkdf(eph.agree(pubS), nonceC + nonceS, "rupoop-lan-pk-v1".toByteArray())
                val transcript = LanCrypto.sha256(hello, p2)
                LanWire.write(out, LanWire.K_PAIR_CONFIRM, LanCrypto.hmac(pk, "confirm".toByteArray(), transcript))

                val d = LanWire.read(inp)
                if (d.kind != LanWire.K_PAIR_DONE ||
                    !LanCrypto.constantTimeEquals(d.payload, LanCrypto.hmac(pk, "done".toByteArray(), transcript))
                ) return PairOutcome.Failed("confirm")

                val peer = LanPeer(qr.deviceId, nameS, pk, host = qr.host, port = qr.port, lastSyncAt = 0L)
                peers.put(peer)
                return PairOutcome.Paired(peer)
            }
        } catch (e: IOException) {
            return PairOutcome.Failed(e.javaClass.simpleName)
        } catch (e: GeneralSecurityException) {
            return PairOutcome.Failed("crypto")
        }
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 3_000
        const val READ_TIMEOUT_MS = 15_000
        const val PAIR_READ_TIMEOUT_MS = 100_000
    }
}
