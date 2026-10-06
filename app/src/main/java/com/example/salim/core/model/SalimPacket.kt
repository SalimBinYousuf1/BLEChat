package com.example.salim.core.model

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Salim Packet Binary Specification (v1):
 * [0]       : version (1 byte, 0x01)
 * [1]       : type (1 byte: HELLO, MESSAGE, ACK, FRAGMENT, SOS, etc.)
 * [2]       : flags (1 byte: ENCRYPTED, SIGNED, NEEDS_ACK, IS_FRAGMENT, BROADCAST)
 * [3]       : ttl (1 byte, default 7, max 10)
 * [4..19]   : messageId (16 bytes UUID)
 * [20..27]  : senderId (8 bytes derived short ID)
 * [28..35]  : recipientId (8 bytes, or all 0xFF for broadcast)
 * [36..43]  : timestamp (8 bytes Long ms)
 * [44..45]  : payloadLength (2 bytes unsigned short)
 * [46..46+N): payload (N bytes)
 * [End-64..]: signature (64 bytes Ed25519 signature over header + payload) if SIGNED flag is set.
 */
data class SalimPacket(
    val version: Byte = PROTOCOL_VERSION,
    val type: PacketType,
    val flags: Byte,
    val ttl: Byte = DEFAULT_TTL,
    val messageId: ByteArray, // 16 bytes
    val senderId: ByteArray,  // 8 bytes
    val recipientId: ByteArray, // 8 bytes
    val timestamp: Long,
    val payload: ByteArray,
    val signature: ByteArray = ByteArray(SIGNATURE_LENGTH) // 64 bytes
) {
    init {
        require(messageId.size == MESSAGE_ID_LENGTH) { "messageId must be 16 bytes" }
        require(senderId.size == PEER_ID_LENGTH) { "senderId must be 8 bytes" }
        require(recipientId.size == PEER_ID_LENGTH) { "recipientId must be 8 bytes" }
        require(payload.size <= MAX_PAYLOAD_SIZE) { "payload exceeds max size $MAX_PAYLOAD_SIZE" }
    }

    val isSigned: Boolean get() = PacketFlags.hasFlag(flags, PacketFlags.SIGNED)
    val isEncrypted: Boolean get() = PacketFlags.hasFlag(flags, PacketFlags.ENCRYPTED)
    val needsAck: Boolean get() = PacketFlags.hasFlag(flags, PacketFlags.NEEDS_ACK)
    val isFragment: Boolean get() = PacketFlags.hasFlag(flags, PacketFlags.IS_FRAGMENT)
    val isBroadcast: Boolean get() = PacketFlags.hasFlag(flags, PacketFlags.BROADCAST)

    fun isForRecipient(myId: ByteArray): Boolean {
        return isBroadcast || recipientId.contentEquals(myId)
    }

    /**
     * Serializes header + payload into bytes (excluding signature)
     * for signature calculation or verification.
     */
    fun signableBytes(): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_LENGTH + payload.size).order(ByteOrder.BIG_ENDIAN)
        buffer.put(version)
        buffer.put(type.code)
        buffer.put(flags)
        buffer.put(ttl)
        buffer.put(messageId)
        buffer.put(senderId)
        buffer.put(recipientId)
        buffer.putLong(timestamp)
        buffer.putShort(payload.size.toShort())
        buffer.put(payload)
        return buffer.array()
    }

    /**
     * Encodes complete wire packet including signature if signed.
     */
    fun encode(): ByteArray {
        val signable = signableBytes()
        val totalLength = signable.size + if (isSigned) SIGNATURE_LENGTH else 0
        val buffer = ByteBuffer.allocate(totalLength).order(ByteOrder.BIG_ENDIAN)
        buffer.put(signable)
        if (isSigned) {
            val sig = if (signature.size == SIGNATURE_LENGTH) signature else ByteArray(SIGNATURE_LENGTH)
            buffer.put(sig)
        }
        return buffer.array()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SalimPacket
        if (version != other.version) return false
        if (type != other.type) return false
        if (flags != other.flags) return false
        if (ttl != other.ttl) return false
        if (!messageId.contentEquals(other.messageId)) return false
        if (!senderId.contentEquals(other.senderId)) return false
        if (!recipientId.contentEquals(other.recipientId)) return false
        if (timestamp != other.timestamp) return false
        if (!payload.contentEquals(other.payload)) return false
        if (!signature.contentEquals(other.signature)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = version.toInt()
        result = 31 * result + type.hashCode()
        result = 31 * result + flags.toInt()
        result = 31 * result + ttl.toInt()
        result = 31 * result + messageId.contentHashCode()
        result = 31 * result + senderId.contentHashCode()
        result = 31 * result + recipientId.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }

    companion object {
        const val PROTOCOL_VERSION: Byte = 0x01
        const val DEFAULT_TTL: Byte = 0x07
        const val MAX_TTL: Byte = 0x0A

        const val HEADER_LENGTH = 46
        const val MESSAGE_ID_LENGTH = 16
        const val PEER_ID_LENGTH = 8
        const val SIGNATURE_LENGTH = 64
        const val MAX_PAYLOAD_SIZE = 65535

        val BROADCAST_RECIPIENT = ByteArray(PEER_ID_LENGTH) { 0xFF.toByte() }

        fun randomMessageId(): ByteArray {
            val uuid = UUID.randomUUID()
            val bb = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
            bb.putLong(uuid.mostSignificantBits)
            bb.putLong(uuid.leastSignificantBits)
            return bb.array()
        }

        fun decode(bytes: ByteArray): SalimPacket? {
            if (bytes.size < HEADER_LENGTH) return null
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

            val version = buffer.get()
            if (version != PROTOCOL_VERSION) return null // Version mismatch

            val typeCode = buffer.get()
            val type = PacketType.fromCode(typeCode) ?: return null

            val flags = buffer.get()
            val ttl = buffer.get()

            val messageId = ByteArray(MESSAGE_ID_LENGTH)
            buffer.get(messageId)

            val senderId = ByteArray(PEER_ID_LENGTH)
            buffer.get(senderId)

            val recipientId = ByteArray(PEER_ID_LENGTH)
            buffer.get(recipientId)

            val timestamp = buffer.getLong()
            val payloadLength = buffer.short.toInt() and 0xFFFF

            val isSigned = PacketFlags.hasFlag(flags, PacketFlags.SIGNED)
            val expectedRemaining = payloadLength + if (isSigned) SIGNATURE_LENGTH else 0
            if (buffer.remaining() < expectedRemaining) return null

            val payload = ByteArray(payloadLength)
            buffer.get(payload)

            val signature = if (isSigned) {
                val sig = ByteArray(SIGNATURE_LENGTH)
                buffer.get(sig)
                sig
            } else {
                ByteArray(SIGNATURE_LENGTH)
            }

            return SalimPacket(
                version = version,
                type = type,
                flags = flags,
                ttl = ttl,
                messageId = messageId,
                senderId = senderId,
                recipientId = recipientId,
                timestamp = timestamp,
                payload = payload,
                signature = signature
            )
        }
    }
}
