package com.sagar.voice_shield.service

/**
 * ITU-T G.711 mu-law audio codec implementation.
 * Compresses 16-bit linear PCM audio to 8-bit logarithmic samples (50% bandwidth reduction),
 * enabling crisp HD voice transmission over constrained cellular networks and WebSockets.
 */
object G711Codec {
    private const val BIAS = 0x84 // 132
    private const val CLIP = 32635

    /**
     * Converts a 16-bit linear PCM sample to an 8-bit G.711 mu-law byte.
     */
    fun linearToUlaw(sample: Short): Byte {
        var s = sample.toInt()
        val sign = if (s < 0) {
            s = -s
            0x7F
        } else {
            0xFF
        }
        if (s > CLIP) s = CLIP
        s += BIAS

        var exponent = 7
        var expMask = 0x4000
        while ((s and expMask) == 0 && exponent > 0) {
            exponent--
            expMask = expMask shr 1
        }
        val mantissa = (s shr (exponent + 3)) and 0x0F
        val ulaw = (sign xor (exponent shl 4 or mantissa)).toByte()
        return ulaw
    }

    /**
     * Converts an 8-bit G.711 mu-law byte back to a 16-bit linear PCM sample.
     */
    fun ulawToLinear(ulaw: Byte): Short {
        val u = ulaw.toInt().inv() and 0xFF
        val sign = u and 0x80
        val exponent = (u shr 4) and 0x07
        val mantissa = u and 0x0F
        var sample = ((mantissa shl 3) + BIAS) shl exponent
        sample -= BIAS
        return (if (sign != 0) -sample else sample).toShort()
    }

    /**
     * Bulk encode 16-bit linear PCM short array to 8-bit mu-law byte array.
     */
    fun encode(pcmSamples: ShortArray, length: Int = pcmSamples.size): ByteArray {
        val out = ByteArray(length)
        for (i in 0 until length) {
            out[i] = linearToUlaw(pcmSamples[i])
        }
        return out
    }

    /**
     * Bulk decode 8-bit mu-law byte array to 16-bit linear PCM byte array (little-endian).
     */
    fun decodeToPcmBytes(ulawBytes: ByteArray): ByteArray {
        val pcm = ByteArray(ulawBytes.size * 2)
        for (i in ulawBytes.indices) {
            val sample = ulawToLinear(ulawBytes[i])
            pcm[i * 2] = (sample.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        return pcm
    }
}
