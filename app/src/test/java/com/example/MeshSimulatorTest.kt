package com.example

import com.example.salim.core.crypto.CryptoManager
import com.example.salim.core.mesh.MeshSimulator
import com.example.salim.core.model.PacketType
import com.example.salim.core.model.SalimPacket
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.nio.charset.StandardCharsets

class MeshSimulatorTest {

    @Before
    fun setup() {
        MeshSimulator.resetSimulation()
    }

    @After
    fun tearDown() {
        MeshSimulator.resetSimulation()
    }

    @Test
    fun testVirtualMeshPacketTransmission() = runBlocking {
        val aliceId = CryptoManager.createIdentity("Alice")
        val bobId = CryptoManager.createIdentity("Bob")

        val aliceNode = MeshSimulator(aliceId, latencyMs = 5)
        val bobNode = MeshSimulator(bobId, latencyMs = 5)

        aliceNode.start()
        bobNode.start()

        val packet = SalimPacket(
            type = PacketType.MESSAGE,
            flags = 0,
            ttl = 3,
            messageId = SalimPacket.randomMessageId(),
            senderId = aliceId.peerId,
            recipientId = bobId.peerId,
            timestamp = System.currentTimeMillis(),
            payload = "Hello Bob from Alice".toByteArray(StandardCharsets.UTF_8)
        )

        aliceNode.send(packet)

        val received = withTimeout(2000) {
            bobNode.incomingPackets.first()
        }

        assertNotNull(received)
        assertEquals(packet.messageId.toList(), received.messageId.toList())
        assertEquals("Hello Bob from Alice", String(received.payload, StandardCharsets.UTF_8))

        aliceNode.stop()
        bobNode.stop()
    }
}
