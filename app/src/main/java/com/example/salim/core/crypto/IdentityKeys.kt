package com.example.salim.core.crypto

data class IdentityKeys(
    val peerId: ByteArray,             // 8 bytes short ID
    val nickname: String,
    val ed25519PublicKey: ByteArray,   // 32 bytes
    val ed25519PrivateKey: ByteArray,  // 32 or 64 bytes
    val x25519PublicKey: ByteArray,    // 32 bytes
    val x25519PrivateKey: ByteArray    // 32 bytes
) {
    val peerIdHex: String get() = peerId.joinToString("") { "%02X".format(it) }
    val ed25519PubHex: String get() = ed25519PublicKey.joinToString("") { "%02x".format(it) }
    val x25519PubHex: String get() = x25519PublicKey.joinToString("") { "%02x".format(it) }

    val formattedShortId: String
        get() = if (peerIdHex.length >= 8) {
            "${peerIdHex.substring(0, 4)}-${peerIdHex.substring(4, 8)}"
        } else peerIdHex

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as IdentityKeys
        return peerId.contentEquals(other.peerId) &&
                nickname == other.nickname &&
                ed25519PublicKey.contentEquals(other.ed25519PublicKey)
    }

    override fun hashCode(): Int {
        var result = peerId.contentHashCode()
        result = 31 * result + nickname.hashCode()
        result = 31 * result + ed25519PublicKey.contentHashCode()
        return result
    }
}
