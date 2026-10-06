package com.example.salim.core.mesh

import com.example.salim.core.crypto.IdentityKeys
import com.example.salim.core.model.BatteryMode
import com.example.salim.core.model.Peer
import com.example.salim.core.model.SalimPacket
import com.example.salim.core.model.SignalBucket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * MeshSimulator: In-memory virtual mesh network that connects multiple
 * simulated nodes for automated testing, unit tests, and multi-hop verification.
 */
class MeshSimulator(
    val identity: IdentityKeys,
    private val packetLossRate: Float = 0.0f,
    private val latencyMs: Long = 20L
) : Transport {

    companion object {
        // Global virtual ether connecting all simulated node instances
        val virtualEther = ConcurrentHashMap<String, MeshSimulator>()

        fun resetSimulation() {
            virtualEther.clear()
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _incomingPackets = MutableSharedFlow<SalimPacket>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val incomingPackets: SharedFlow<SalimPacket> = _incomingPackets.asSharedFlow()

    private val _connectedPeers = MutableStateFlow<List<Peer>>(emptyList())
    override val connectedPeers: StateFlow<List<Peer>> = _connectedPeers.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    override fun start() {
        virtualEther[identity.peerIdHex] = this
        _isRunning.value = true
        updateSimulatedPeers()
    }

    override fun stop() {
        virtualEther.remove(identity.peerIdHex)
        _isRunning.value = false
        _connectedPeers.value = emptyList()
    }

    override fun setBatteryMode(mode: BatteryMode) {}

    fun updateSimulatedPeers() {
        val peers = virtualEther.values
            .filter { it.identity.peerIdHex != identity.peerIdHex }
            .map { other ->
                Peer(
                    id = other.identity.peerIdHex,
                    nickname = other.identity.nickname,
                    publicKeyEd25519Hex = other.identity.ed25519PubHex,
                    publicKeyX25519Hex = other.identity.x25519PubHex,
                    isVerified = false,
                    lastSeenTimestamp = System.currentTimeMillis(),
                    signalBucket = SignalBucket.CLOSE,
                    rssi = -55,
                    hops = 1
                )
            }
        _connectedPeers.value = peers
    }

    override suspend fun send(packet: SalimPacket): Boolean {
        if (!_isRunning.value) return false
        val myId = identity.peerIdHex

        // Broadcast to other simulated nodes in the virtual ether
        for ((peerHex, node) in virtualEther) {
            if (peerHex == myId) continue

            // Simulate packet loss
            if (packetLossRate > 0.0f && Math.random() < packetLossRate) continue

            node.scope.launch {
                if (latencyMs > 0) delay(latencyMs)
                node._incomingPackets.emit(packet)
            }
        }
        return true
    }
}
