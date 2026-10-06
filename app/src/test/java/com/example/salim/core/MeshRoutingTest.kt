package com.example.salim.core

import com.example.salim.core.crypto.CryptoManager
import com.example.salim.core.mesh.MeshSimulator
import com.example.salim.core.model.PacketFlags
import com.example.salim.core.model.PacketType
import com.example.salim.core.model.SalimPacket
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.nio.charset.StandardCharsets

class MeshRoutingTest {

    @Before
    fun setup() {
        MeshSimulator.resetSimulation()
    }

    @After
    fun teardown() {
        MeshSimulator.resetSimulation()
    }

    @Test
    fun multi_node_packet_broadcast_over_mesh() = runBlocking {
        val alice = CryptoManager.createIdentity("Alice")
        val bob = CryptoManager.createIdentity("Bob")

        val simAlice = MeshSimulator(alice)
        val simBob = MeshSimulator(bob)

        simAlice.start()
        simBob.start()

        val payload = "Hello from Alice over virtual ether!".toByteArray(StandardCharsets.UTF_8)
        val packet = SalimPacket(
            type = PacketType.MESSAGE,
            flags = PacketFlags.BROADCAST,
            ttl = 3,
            messageId = SalimPacket.randomMessageId(),
            senderId = alice.peerId,
            recipientId = SalimPacket.BROADCAST_RECIPIENT,
            timestamp = System.currentTimeMillis(),
            payload = payload
        )

        simAlice.send(packet)

        val received = withTimeoutOrNull(2000) {
            simBob.incomingPackets.first()
        }

        assertNotNull("Bob should receive the packet from Alice", received)
        assertEquals(PacketType.MESSAGE, received!!.type)
        assertArrayEquals(alice.peerId, received.senderId)
        assertArrayEquals(payload, received.payload)

        simAlice.stop()
        simBob.stop()
    }
}
