package com.santiago43rus.rupoop.lan

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/** Кадр: 4 байта длины (kind + payload), 1 байт kind, payload. */
object LanWire {
    const val MAX_FRAME = 8 * 1024 * 1024

    const val K_PAIR_HELLO = 1
    const val K_PAIR_ACCEPT = 2
    const val K_PAIR_REJECT = 3
    const val K_PAIR_CONFIRM = 4
    const val K_PAIR_DONE = 5
    const val K_SYNC_HELLO = 10
    const val K_SYNC_NONCE = 11
    const val K_DATA = 12

    // типы сообщений внутри зашифрованного K_DATA (первый байт открытого текста)
    const val T_HASH_REQ = 1
    const val T_HASH_RESP = 2
    const val T_SYNC_REQ = 3
    const val T_SYNC_RESP = 4
    const val T_UNPAIR = 5
    const val T_OK = 6
    const val T_BYE = 7

    class Frame(val kind: Int, val payload: ByteArray)

    fun write(out: DataOutputStream, kind: Int, payload: ByteArray) {
        if (payload.size + 1 > MAX_FRAME) throw IOException("frame too large")
        out.writeInt(payload.size + 1)
        out.writeByte(kind)
        out.write(payload)
        out.flush()
    }

    fun read(inp: DataInputStream): Frame {
        val len = inp.readInt()
        if (len < 1 || len > MAX_FRAME) throw IOException("bad frame length")
        val kind = inp.readUnsignedByte()
        val buf = ByteArray(len - 1)
        inp.readFully(buf)
        return Frame(kind, buf)
    }
}

class ByteWriter {
    private val o = ByteArrayOutputStream()
    fun u8(v: Int) = apply { o.write(v) }
    fun u16(v: Int) = apply { o.write(v ushr 8); o.write(v) }
    fun raw(b: ByteArray) = apply { o.write(b) }
    fun bytes16(b: ByteArray) = apply { u16(b.size); raw(b) }
    fun string16(s: String) = bytes16(s.take(64).toByteArray(Charsets.UTF_8))
    fun toByteArray(): ByteArray = o.toByteArray()
}

class ByteReader(private val b: ByteArray) {
    var pos = 0
        private set

    private fun need(n: Int) {
        if (n < 0 || pos + n > b.size) throw IOException("short payload")
    }

    fun u8(): Int { need(1); return b[pos++].toInt() and 0xFF }
    fun u16(): Int {
        need(2)
        val v = ((b[pos].toInt() and 0xFF) shl 8) or (b[pos + 1].toInt() and 0xFF)
        pos += 2
        return v
    }
    fun fixed(n: Int): ByteArray { need(n); val r = b.copyOfRange(pos, pos + n); pos += n; return r }
    fun bytes16(): ByteArray = fixed(u16())
    fun string16(): String = String(bytes16(), Charsets.UTF_8)
}
