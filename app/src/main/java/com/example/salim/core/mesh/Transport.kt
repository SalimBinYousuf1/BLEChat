package com.example.salim.core.mesh

import com.example.salim.core.model.BatteryMode
import com.example.salim.core.model.Peer
import com.example.salim.core.model.SalimPacket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface Transport {
    val incomingPackets: Flow<SalimPacket>
    val connectedPeers: StateFlow<List<Peer>>
    val isRunning: StateFlow<Boolean>

    fun start()
    fun stop()
    suspend fun send(packet: SalimPacket): Boolean
    fun setBatteryMode(mode: BatteryMode)
}
