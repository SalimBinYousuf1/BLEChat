package com.example.salim.core.mesh

import java.util.UUID

object BleConstants {
    // Salim 128-bit Service and Characteristic UUIDs
    val SERVICE_UUID: UUID = UUID.fromString("0000FA10-0000-1000-8000-00805F9B34FB")
    val PACKET_CHAR_UUID: UUID = UUID.fromString("0000FA11-0000-1000-8000-00805F9B34FB")
    val CONTROL_CHAR_UUID: UUID = UUID.fromString("0000FA12-0000-1000-8000-00805F9B34FB")
    val CLIENT_CONFIG_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    const val DEFAULT_MTU = 247
    const val MAX_CONCURRENT_CONNECTIONS = 6
    const val FRAGMENT_HEADER_SIZE = 18 // 1 byte total + 1 byte index + 16 bytes msgId
}
