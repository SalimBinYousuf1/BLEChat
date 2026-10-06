package com.example.salim.core.storage

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.salim.core.model.Peer
import com.example.salim.core.model.SignalBucket

@Entity(tableName = "peers")
data class PeerEntity(
    @PrimaryKey val id: String, // 16-hex string
    val nickname: String,
    val publicKeyEd25519Hex: String,
    val publicKeyX25519Hex: String,
    val isVerified: Boolean = false,
    val isBlocked: Boolean = false,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val rssi: Int = -70,
    val hops: Int = 1
) {
    fun toPeer(): Peer {
        val bucket = when {
            rssi >= -65 -> SignalBucket.CLOSE
            rssi >= -82 -> SignalBucket.NEAR
            else -> SignalBucket.FAR
        }
        return Peer(
            id = id,
            nickname = nickname,
            publicKeyEd25519Hex = publicKeyEd25519Hex,
            publicKeyX25519Hex = publicKeyX25519Hex,
            isVerified = isVerified,
            isBlocked = isBlocked,
            lastSeenTimestamp = lastSeenTimestamp,
            signalBucket = bucket,
            rssi = rssi,
            hops = hops
        )
    }
}
