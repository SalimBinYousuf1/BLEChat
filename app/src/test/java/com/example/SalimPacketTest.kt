package com.example

import com.example.salim.core.model.PacketFlags
import com.example.salim.core.model.PacketType
import com.example.salim.core.model.SalimPacket
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class SalimPacketTest {

    @Test
    fun testEncodeDecodeRoundTrip() {
        val msgId = SalimPacket.randomMessageId()
        val senderId = ByteArray(8) { (it + 1).toByte() }
        val recipientId = ByteArray(8) { (it + 10).toByte() }
        val payload = "Hello Salim Mesh!".toByteArray(StandardCharsets.UTF_8)
        val flags = PacketFlags.setFlag(PacketFlags.SIGNED, PacketFlags.ENCRYPTED)

        val packet = SalimPacket(
            version = SalimPacket.PROTOCOL_VERSION,
            type = PacketType.MESSAGE,
            flags = flags,
            ttl = 5,
            messageId = msgId,
            senderId = senderId,
            recipientId = recipientId,
            timestamp = 1700000000000L,
            payload = payload,
            signature = ByteArray(64) { 0x42 }
        )

        val encoded = packet.encode()
        val decoded = SalimPacket.decode(encoded)

        assertNotNull(decoded)
        assertEquals(packet.version, decoded!!.version)
        assertEquals(packet.type, decoded.type)
        assertEquals(packet.flags, decoded.flags)
        assertEquals(packet.ttl, decoded.ttl)
        assertArrayEquals(packet.messageId, decoded.messageId)
        assertArrayEquals(packet.senderId, decoded.senderId)
        assertArrayEquals(packet.recipientId, decoded.recipientId)
        assertEquals(packet.timestamp, decoded.timestamp)
        assertArrayEquals(packet.payload, decoded.payload)
        assertArrayEquals(packet.signature, decoded.signature)
    }

    @Test
    fun testTruncatedPacketReturnsNull() {
        val raw = ByteArray(20) // Too short (< 46 bytes header)
        val decoded = SalimPacket.decode(raw)
        assertNull(decoded)
    }

    @Test
    fun testInvalidVersionReturnsNull() {
        val validPacket = SalimPacket(
            type = PacketType.HELLO,
            flags = 0,
            messageId = SalimPacket.randomMessageId(),
            senderId = ByteArray(8),
            recipientId = ByteArray(8),
            timestamp = System.currentTimeMillis(),
            payload = ByteArray(0)
        )
        val encoded = validPacket.encode()
        encoded[0] = 0x99.toByte() // Corrupt version byte
        val decoded = SalimPacket.decode(encoded)
        assertNull(decoded)
    }

    @Test
    fun testBroadcastFlagsAndRecipient() {
        val packet = SalimPacket(
            type = PacketType.ANNOUNCE,
            flags = PacketFlags.BROADCAST,
            messageId = SalimPacket.randomMessageId(),
            senderId = ByteArray(8) { 1 },
            recipientId = SalimPacket.BROADCAST_RECIPIENT,
            timestamp = System.currentTimeMillis(),
            payload = "Announce".toByteArray()
        )
        assertTrue(packet.isBroadcast)
        assertTrue(packet.isForRecipient(ByteArray(8) { 9 }))
    }
}
