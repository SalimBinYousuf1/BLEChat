package com.example.salim.core.crypto

import com.example.salim.core.model.IntroductionVoucher
import com.example.salim.core.model.Peer
import java.nio.charset.StandardCharsets

/**
 * Peer-to-Peer Identity Introductions:
 * Allows Friend A to act as a cryptographically signed bridge, introducing
 * Friend B to Friend C so they can establish mutual trust out-of-band without servers.
 */
object IdentityIntroduction {

    fun createVoucher(
        introducerKeys: IdentityKeys,
        subjectPeer: Peer,
        targetPeer: Peer
    ): IntroductionVoucher {
        val timestamp = System.currentTimeMillis()
        val statement = "INTRODUCE|${introducerKeys.peerIdHex}|${subjectPeer.id}|${targetPeer.id}|$timestamp"
        val statementBytes = statement.toByteArray(StandardCharsets.UTF_8)
        val signature = CryptoManager.sign(statementBytes, introducerKeys.ed25519PrivateKey)
        val sigHex = signature.joinToString("") { "%02X".format(it) }

        return IntroductionVoucher(
            introducerNickname = introducerKeys.nickname,
            introducerPeerIdHex = introducerKeys.peerIdHex,
            subjectNickname = subjectPeer.nickname,
            subjectPeerIdHex = subjectPeer.id,
            targetPeerIdHex = targetPeer.id,
            signatureHex = sigHex,
            timestamp = timestamp
        )
    }

    fun verifyVoucher(
        voucher: IntroductionVoucher,
        introducerEd25519PublicKeyHex: String
    ): Boolean {
        return try {
            val statement = "INTRODUCE|${voucher.introducerPeerIdHex}|${voucher.subjectPeerIdHex}|${voucher.targetPeerIdHex}|${voucher.timestamp}"
            val statementBytes = statement.toByteArray(StandardCharsets.UTF_8)
            val sigBytes = voucher.signatureHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val pubBytes = introducerEd25519PublicKeyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            CryptoManager.verify(statementBytes, sigBytes, pubBytes)
        } catch (_: Exception) {
            false
        }
    }
}
