package com.example.salim.core.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "relay_cache")
data class RelayCacheEntity(
    @PrimaryKey val messageIdHex: String,
    val packetBytes: ByteArray,
    val recipientIdHex: String,
    val timestamp: Long = System.currentTimeMillis(),
    val expiryTimestamp: Long = System.currentTimeMillis() + 24 * 60 * 60 * 1000 // 24 hours
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RelayCacheEntity
        return messageIdHex == other.messageIdHex && packetBytes.contentEquals(other.packetBytes)
    }

    override fun hashCode(): Int {
        var result = messageIdHex.hashCode()
        result = 31 * result + packetBytes.contentHashCode()
        return result
    }
}

@Entity(tableName = "seen_messages")
data class SeenMessageEntity(
    @PrimaryKey val messageIdHex: String,
    val seenTimestamp: Long = System.currentTimeMillis()
)
