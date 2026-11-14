package com.santiago43rus.rupoop.lan

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Вся криптография LAN-синхронизации: только javax.crypto, без Keystore и сертификатов. */
object LanCrypto {
    private val rnd = SecureRandom()

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { rnd.nextBytes(it) }

    fun sha256(vararg parts: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        parts.forEach { md.update(it) }
        return md.digest()
    }

    fun hmac(key: ByteArray, vararg parts: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        parts.forEach { mac.update(it) }
        return mac.doFinal()
    }

    /** HKDF-SHA256 (RFC 5869). */
    fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int = 32): ByteArray {
        val prk = hmac(if (salt.isEmpty()) ByteArray(32) else salt, ikm)
        val out = ByteArrayOutputStream()
        var t = ByteArray(0)
        var i = 1
        while (out.size() < length) {
            t = hmac(prk, t, info, byteArrayOf(i.toByte()))
            out.write(t)
            i++
        }
        return out.toByteArray().copyOf(length)
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }

    fun unhex(s: String): ByteArray {
        require(s.length % 2 == 0) { "hex length" }
        return ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    /** Одноразовая пара ключей ECDH P-256. [publicKey] в формате X.509 DER (91 байт). */
    class Ephemeral internal constructor(val publicKey: ByteArray, private val priv: PrivateKey) {
        fun agree(peerPublicKey: ByteArray): ByteArray {
            val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(peerPublicKey))
            val ec = pub as? ECPublicKey ?: throw IOException("not EC key")
            if (ec.params.order.bitLength() != 256) throw IOException("wrong curve")
            val ka = KeyAgreement.getInstance("ECDH")
            ka.init(priv)
            ka.doPhase(pub, true)
            return ka.generateSecret()
        }
    }

    fun newEphemeral(): Ephemeral {
        val g = KeyPairGenerator.getInstance("EC")
        g.initialize(ECGenParameterSpec("secp256r1"))
        val kp = g.generateKeyPair()
        return Ephemeral(kp.public.encoded, kp.private)
    }

    /**
     * Шифрованный канал одной сессии. Ключи для двух направлений разные, IV = счётчик,
     * поэтому пара (ключ, IV) не повторяется, а повтор или перестановка кадров не расшифруются.
     */
    class Channel private constructor(sendKey: ByteArray, recvKey: ByteArray) {
        private val sk = SecretKeySpec(sendKey, "AES")
        private val rk = SecretKeySpec(recvKey, "AES")
        private var sendCtr = 0L
        private var recvCtr = 0L

        private fun iv(ctr: Long): ByteArray {
            val b = ByteArray(12)
            for (i in 0 until 8) b[11 - i] = (ctr ushr (8 * i)).toByte()
            return b
        }

        fun seal(plain: ByteArray): ByteArray {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, sk, GCMParameterSpec(128, iv(sendCtr++)))
            c.updateAAD(AAD)
            return c.doFinal(plain)
        }

        /** Бросает GeneralSecurityException, если ключ не тот или кадр подменён. */
        fun open(cipher: ByteArray): ByteArray {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, rk, GCMParameterSpec(128, iv(recvCtr)))
            c.updateAAD(AAD)
            val r = c.doFinal(cipher)
            recvCtr++
            return r
        }

        companion object {
            private val AAD = "rupoop-lan-data-v1".toByteArray()

            fun client(peerKey: ByteArray, nonceC: ByteArray, nonceS: ByteArray): Channel {
                val salt = nonceC + nonceS
                val c2s = hkdf(peerKey, salt, "rupoop-lan-c2s".toByteArray())
                val s2c = hkdf(peerKey, salt, "rupoop-lan-s2c".toByteArray())
                return Channel(c2s, s2c)
            }

            fun server(peerKey: ByteArray, nonceC: ByteArray, nonceS: ByteArray): Channel {
                val salt = nonceC + nonceS
                val c2s = hkdf(peerKey, salt, "rupoop-lan-c2s".toByteArray())
                val s2c = hkdf(peerKey, salt, "rupoop-lan-s2c".toByteArray())
                return Channel(s2c, c2s)
            }
        }
    }
}
