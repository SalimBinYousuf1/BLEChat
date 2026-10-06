package com.example.salim.core.model

enum class SignalBucket {
    CLOSE, // RSSI >= -65 dBm
    NEAR,  // -65 dBm > RSSI >= -82 dBm
    FAR    // RSSI < -82 dBm
}

enum class DeliveryStatus {
    SENDING,
    SENT_TO_MESH,
    DELIVERED,
    READ,
    FAILED
}

enum class MessageType {
    TEXT,
    IMAGE,
    AUDIO,
    SOS,
    SYSTEM
}

enum class BatteryMode {
    BALANCED,    // Scan window 500ms, interval 2000ms
    PERFORMANCE, // Continuous scan/adv, max connections
    SAVER        // Scan window 250ms, interval 5000ms
}

data class Peer(
    val id: String,               // Hex 8 bytes or 16 chars
    val nickname: String,
    val publicKeyEd25519Hex: String,
    val publicKeyX25519Hex: String,
    val isVerified: Boolean = false,
    val isBlocked: Boolean = false,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val signalBucket: SignalBucket = SignalBucket.NEAR,
    val rssi: Int = -75,
    val hops: Int = 1,
    val batteryPercent: Int = 85,
    val isMuleCapable: Boolean = true
) {
    val shortId: String get() = if (id.length >= 8) id.substring(0, 8).uppercase() else id.uppercase()
}

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderNickname: String,
    val recipientId: String,
    val content: String,
    val type: MessageType = MessageType.TEXT,
    val mediaUri: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val status: DeliveryStatus = DeliveryStatus.SENDING,
    val replyToId: String? = null,
    val reaction: String? = null,
    val reactions: Map<String, Int> = emptyMap(), // emoji -> count
    val isEdited: Boolean = false,
    val isOutgoing: Boolean = true,
    val powDifficulty: Int = 12
)

data class Conversation(
    val id: String,
    val title: String,
    val peerId: String?, // Null if group or public channel
    val isGroup: Boolean = false,
    val isPublicChannel: Boolean = false,
    val lastMessage: String = "",
    val lastTimestamp: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false
) {
    companion object {
        const val PUBLIC_CHANNEL_ID = "channel_public_nearby"
    }
}

/**
 * Dead-Drop Geofenced Droplet:
 * Secret note bound to physical GPS coordinates and BLE beacons,
 * stored & relayed by passing mesh nodes, unveiling only when in vicinity.
 */
data class GeofenceDroplet(
    val id: String,
    val title: String,
    val encryptedNote: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 100f,
    val authorNickname: String,
    val authorIdHex: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isUnlocked: Boolean = false,
    val unlockedContent: String? = null
)

/**
 * Sneakernet Data Mule Metrics & Status
 */
data class DataMuleStats(
    val packetsCarried: Int = 0,
    val packetsDelivered: Int = 0,
    val physicalKilometersTraveled: Float = 0f,
    val isMulingActive: Boolean = true
)

/**
 * Peer-to-Peer Identity Introduction Voucher:
 * Peer A cryptographically signs an endorsement introducing Peer B to Peer C.
 */
data class IntroductionVoucher(
    val introducerNickname: String,
    val introducerPeerIdHex: String,
    val subjectNickname: String,
    val subjectPeerIdHex: String,
    val targetPeerIdHex: String,
    val signatureHex: String,
    val timestamp: Long = System.currentTimeMillis()
)
