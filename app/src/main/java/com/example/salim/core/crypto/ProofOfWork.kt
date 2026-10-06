package com.example.salim.core.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Proof-of-Work (Hashcash) Anti-Spam Engine:
 * Forces senders to solve a lightweight cryptographic puzzle before broadcasting over the mesh.
 * Receivers instantly verify the zero-bit prefix, shielding the zero-internet mesh from flooding attacks.
 */
object ProofOfWork {

    const val DEFAULT_DIFFICULTY_BITS = 12 // Fast on mobile (~10-50ms), yet prevents high-rate spam

    /**
     * Mines a 4-byte nonce such that SHA-256(challenge + nonce) has at least [difficultyBits] leading 0 bits.
     */
    fun mine(challengeBytes: ByteArray, difficultyBits: Int = DEFAULT_DIFFICULTY_BITS): Int {
        var nonce = 0
        val digest = SHA256Digest()
        val hash = ByteArray(digest.digestSize)
        val nonceBytes = ByteArray(4)

        while (nonce < Int.MAX_VALUE) {
            val bb = ByteBuffer.wrap(nonceBytes).order(ByteOrder.BIG_ENDIAN)
            bb.putInt(nonce)

            digest.reset()
            digest.update(challengeBytes, 0, challengeBytes.size)
            digest.update(nonceBytes, 0, nonceBytes.size)
            digest.doFinal(hash, 0)

            if (hasLeadingZeroBits(hash, difficultyBits)) {
                return nonce
            }
            nonce++
        }
        return 0
    }

    /**
     * Verifies that SHA-256(challenge + nonce) has at least [difficultyBits] leading 0 bits.
     */
    fun verify(challengeBytes: ByteArray, nonce: Int, difficultyBits: Int = DEFAULT_DIFFICULTY_BITS): Boolean {
        val digest = SHA256Digest()
        val hash = ByteArray(digest.digestSize)
        val nonceBytes = ByteArray(4)
        val bb = ByteBuffer.wrap(nonceBytes).order(ByteOrder.BIG_ENDIAN)
        bb.putInt(nonce)

        digest.update(challengeBytes, 0, challengeBytes.size)
        digest.update(nonceBytes, 0, nonceBytes.size)
        digest.doFinal(hash, 0)

        return hasLeadingZeroBits(hash, difficultyBits)
    }

    private fun hasLeadingZeroBits(hash: ByteArray, targetBits: Int): Boolean {
        var bitsChecked = 0
        for (byte in hash) {
            val b = byte.toInt() and 0xFF
            for (i in 7 downTo 0) {
                if (bitsChecked >= targetBits) return true
                val bit = (b shr i) and 1
                if (bit != 0) return false
                bitsChecked++
            }
        }
        return bitsChecked >= targetBits
    }
}
