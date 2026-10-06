package com.example.salim.core.mesh

import com.example.salim.core.model.DataMuleStats
import com.example.salim.core.model.Peer
import com.example.salim.core.model.SalimPacket
import com.example.salim.core.storage.SalimRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Sneakernet "Data Mules" (Delayed Store-and-Forward):
 * Devices passing by hold encrypted packets, carry them physically across kilometers as users
 * move through the city, and opportunistically offload them to recipients once in Bluetooth range.
 */
class DataMuleManager(
    private val repository: SalimRepository,
    private val transport: Transport
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _stats = MutableStateFlow(
        DataMuleStats(
            packetsCarried = 0,
            packetsDelivered = 0,
            physicalKilometersTraveled = 3.4f,
            isMulingActive = true
        )
    )
    val stats: StateFlow<DataMuleStats> = _stats.asStateFlow()

    // In-memory registry of bundles carried: packetIdHex -> SalimPacket
    private val carriedBundles = ConcurrentHashMap<String, SalimPacket>()

    fun toggleMuling(enabled: Boolean) {
        _stats.value = _stats.value.copy(isMulingActive = enabled)
    }

    /**
     * Accepts a packet to carry as a physical data mule.
     */
    fun enlistPacketAsMule(packet: SalimPacket) {
        if (!_stats.value.isMulingActive) return
        val msgIdHex = packet.messageId.joinToString("") { "%02X".format(it) }
        val recipientHex = packet.recipientId.joinToString("") { "%02X".format(it) }

        carriedBundles[msgIdHex] = packet
        scope.launch {
            repository.cacheRelayPacket(
                messageIdHex = msgIdHex,
                recipientIdHex = recipientHex,
                packetBytes = packet.encode(),
                ttlHours = 168 // 7-day extended retention for physical mules
            )
            _stats.value = _stats.value.copy(packetsCarried = carriedBundles.size)
        }
    }

    /**
     * Called whenever active peers update. Checks if any carried bundle is destined for a newly seen peer.
     */
    fun onPeersDiscovered(peers: List<Peer>) {
        if (!_stats.value.isMulingActive) return

        scope.launch {
            for (peer in peers) {
                val pending = repository.getRelayPacketsForRecipient(peer.id)
                for (packetBytes in pending) {
                    val packet = SalimPacket.decode(packetBytes)
                    if (packet != null) {
                        val sent = transport.send(packet)
                        if (sent) {
                            val msgIdHex = packet.messageId.joinToString("") { "%02X".format(it) }
                            carriedBundles.remove(msgIdHex)
                            _stats.value = _stats.value.copy(
                                packetsCarried = (_stats.value.packetsCarried - 1).coerceAtLeast(0),
                                packetsDelivered = _stats.value.packetsDelivered + 1
                            )
                        }
                    }
                }
            }
        }
    }
}
