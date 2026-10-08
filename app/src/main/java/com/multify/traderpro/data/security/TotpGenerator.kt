package com.multify.traderpro.data.security

import java.nio.ByteBuffer
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** RFC 6238 TOTP generator used for Groww's TOTP-token authentication flow. */
object TotpGenerator {
    private const val STEP_SECONDS = 30L
    private const val DIGITS = 6

    fun generate(base32Secret: String, epochSeconds: Long = System.currentTimeMillis() / 1000L): String {
        val key = decodeBase32(base32Secret)
        require(key.isNotEmpty()) { "Groww TOTP secret is empty or invalid" }
        val counter = epochSeconds / STEP_SECONDS
        val data = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(data)
        val offset = hash.last().toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        val otp = binary % 1_000_000
        return String.format(Locale.US, "%0${DIGITS}d", otp)
    }

    private fun decodeBase32(value: String): ByteArray {
        val clean = value.uppercase(Locale.US).filter { !it.isWhitespace() && it != '-' && it != '=' }
        require(clean.isNotBlank()) { "Groww TOTP secret is empty" }
        var buffer = 0
        var bitsLeft = 0
        val out = ArrayList<Byte>(clean.length * 5 / 8)
        for (c in clean) {
            val v = when (c) {
                in 'A'..'Z' -> c.code - 'A'.code
                in '2'..'7' -> c.code - '2'.code + 26
                else -> throw IllegalArgumentException("Groww TOTP secret is not valid Base32")
            }
            buffer = (buffer shl 5) or v
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out.add(((buffer shr bitsLeft) and 0xff).toByte())
                buffer = buffer and ((1 shl bitsLeft) - 1)
            }
        }
        return out.toByteArray()
    }
}
