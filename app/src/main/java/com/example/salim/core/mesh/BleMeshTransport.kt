package com.example.salim.core.mesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.example.salim.core.crypto.IdentityKeys
import com.example.salim.core.model.BatteryMode
import com.example.salim.core.model.PacketFlags
import com.example.salim.core.model.PacketType
import com.example.salim.core.model.Peer
import com.example.salim.core.model.SalimPacket
import com.example.salim.core.model.SignalBucket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("MissingPermission")
class BleMeshTransport(
    private val context: Context,
    private val identity: IdentityKeys
) : Transport {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private var bleScanner: BluetoothLeScanner? = null
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null

    private val _incomingPackets = MutableSharedFlow<SalimPacket>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val incomingPackets: SharedFlow<SalimPacket> = _incomingPackets.asSharedFlow()

    private val _connectedPeers = MutableStateFlow<List<Peer>>(emptyList())
    override val connectedPeers: StateFlow<List<Peer>> = _connectedPeers.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    // Connected Central clients (when we act as GATT Server)
    private val centralDevices = ConcurrentHashMap<String, BluetoothDevice>()

    // Connected Peripheral links (when we act as Central Client)
    private val activeGatts = ConcurrentHashMap<String, BluetoothGatt>()

    // Discovered peer metadata: device address -> Peer
    private val peerMap = ConcurrentHashMap<String, Peer>()

    // Fragment reassembly cache: msgId hex -> map of index to ByteArray
    private val fragmentCache = ConcurrentHashMap<String, ConcurrentHashMap<Int, ByteArray>>()
    private val fragmentTotal = ConcurrentHashMap<String, Int>()

    private var currentBatteryMode = BatteryMode.BALANCED

    override fun start() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _isRunning.value = false
            return
        }

        try {
            startGattServer()
            startAdvertising()
            startScanning()
            _isRunning.value = true
        } catch (_: SecurityException) {
            _isRunning.value = false
        } catch (_: Exception) {
            _isRunning.value = false
        }
    }

    override fun stop() {
        try {
            stopScanning()
            stopAdvertising()
            stopGattServer()
            activeGatts.values.forEach {
                try { it.disconnect(); it.close() } catch (_: Exception) {}
            }
            activeGatts.clear()
            centralDevices.clear()
            peerMap.clear()
            _connectedPeers.value = emptyList()
            _isRunning.value = false
        } catch (_: Exception) {
        }
    }

    override fun setBatteryMode(mode: BatteryMode) {
        currentBatteryMode = mode
        if (_isRunning.value) {
            stopScanning()
            startScanning()
        }
    }

    private fun startGattServer() {
        val manager = bluetoothManager ?: return
        val gattCallback = object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    centralDevices[device.address] = device
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    centralDevices.remove(device.address)
                }
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                if (characteristic.uuid == BleConstants.PACKET_CHAR_UUID && value != null) {
                    handleRawIncomingBytes(value)
                }
                if (responseNeeded) {
                    try {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                    } catch (_: Exception) {}
                }
            }

            override fun onDescriptorWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                descriptor: BluetoothGattDescriptor,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                if (responseNeeded) {
                    try {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                    } catch (_: Exception) {}
                }
            }
        }

        try {
            val server = manager.openGattServer(context, gattCallback) ?: return
            val service = BluetoothGattService(BleConstants.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

            val packetChar = BluetoothGattCharacteristic(
                BleConstants.PACKET_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                        BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ
            )

            val cccd = BluetoothGattDescriptor(
                BleConstants.CLIENT_CONFIG_DESCRIPTOR_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
            packetChar.addDescriptor(cccd)
            service.addCharacteristic(packetChar)

            server.addService(service)
            gattServer = server
        } catch (_: Exception) {}
    }

    private fun stopGattServer() {
        try {
            gattServer?.close()
            gattServer = null
        } catch (_: Exception) {}
    }

    private fun startAdvertising() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isMultipleAdvertisementSupported) return

        bleAdvertiser = adapter.bluetoothLeAdvertiser ?: return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(
                when (currentBatteryMode) {
                    BatteryMode.PERFORMANCE -> AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY
                    BatteryMode.BALANCED -> AdvertiseSettings.ADVERTISE_MODE_BALANCED
                    BatteryMode.SAVER -> AdvertiseSettings.ADVERTISE_MODE_LOW_POWER
                }
            )
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        // Service data includes 8-byte peer ID
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
            .addServiceData(ParcelUuid(BleConstants.SERVICE_UUID), identity.peerId)
            .setIncludeDeviceName(false)
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {}
            override fun onStartFailure(errorCode: Int) {}
        }

        try {
            bleAdvertiser?.startAdvertising(settings, data, callback)
        } catch (_: Exception) {}
    }

    private fun stopAdvertising() {
        try {
            bleAdvertiser?.stopAdvertising(object : AdvertiseCallback() {})
            bleAdvertiser = null
        } catch (_: Exception) {}
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {}
    }

    private fun startScanning() {
        val adapter = bluetoothAdapter ?: return
        bleScanner = adapter.bluetoothLeScanner ?: return

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
            .build()

        val scanMode = when (currentBatteryMode) {
            BatteryMode.PERFORMANCE -> ScanSettings.SCAN_MODE_LOW_LATENCY
            BatteryMode.BALANCED -> ScanSettings.SCAN_MODE_BALANCED
            BatteryMode.SAVER -> ScanSettings.SCAN_MODE_LOW_POWER
        }

        val settings = ScanSettings.Builder()
            .setScanMode(scanMode)
            .setReportDelay(0)
            .build()

        try {
            bleScanner?.startScan(listOf(filter), settings, scanCallback)
        } catch (_: Exception) {}
    }

    private fun stopScanning() {
        try {
            bleScanner?.stopScan(scanCallback)
            bleScanner = null
        } catch (_: Exception) {}
    }

    private fun handleScanResult(result: ScanResult) {
        val device = result.device ?: return
        val serviceData = result.scanRecord?.getServiceData(ParcelUuid(BleConstants.SERVICE_UUID))

        val peerIdBytes = if (serviceData != null && serviceData.size >= 8) {
            serviceData.copyOf(8)
        } else {
            // fallback to hashing mac address if advertisement didn't carry full payload
            device.address.replace(":", "").take(16).toByteArray()
        }

        val peerIdHex = peerIdBytes.joinToString("") { "%02X".format(it) }
        // Skip self
        if (peerIdBytes.contentEquals(identity.peerId)) return

        val rssi = result.rssi
        val bucket = when {
            rssi >= -65 -> SignalBucket.CLOSE
            rssi >= -82 -> SignalBucket.NEAR
            else -> SignalBucket.FAR
        }

        val peer = Peer(
            id = peerIdHex,
            nickname = device.name ?: "Peer ${peerIdHex.take(4)}",
            publicKeyEd25519Hex = "",
            publicKeyX25519Hex = "",
            lastSeenTimestamp = System.currentTimeMillis(),
            signalBucket = bucket,
            rssi = rssi,
            hops = 1
        )

        peerMap[device.address] = peer
        _connectedPeers.value = peerMap.values.toList()

        // Connect GATT client if not connected and connection limit not reached
        if (!activeGatts.containsKey(device.address) && activeGatts.size < BleConstants.MAX_CONCURRENT_CONNECTIONS) {
            connectGattClient(device)
        }
    }

    private fun connectGattClient(device: BluetoothDevice) {
        try {
            val gattCallback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                        activeGatts[device.address] = gatt
                        try {
                            gatt.requestMtu(BleConstants.DEFAULT_MTU)
                        } catch (_: Exception) {
                            gatt.discoverServices()
                        }
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                        activeGatts.remove(device.address)
                        try { gatt.close() } catch (_: Exception) {}
                    }
                }

                override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                    try { gatt.discoverServices() } catch (_: Exception) {}
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        val service = gatt.getService(BleConstants.SERVICE_UUID)
                        val characteristic = service?.getCharacteristic(BleConstants.PACKET_CHAR_UUID)
                        if (characteristic != null) {
                            try {
                                gatt.setCharacteristicNotification(characteristic, true)
                                val desc = characteristic.getDescriptor(BleConstants.CLIENT_CONFIG_DESCRIPTOR_UUID)
                                if (desc != null) {
                                    desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    gatt.writeDescriptor(desc)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                    if (characteristic.uuid == BleConstants.PACKET_CHAR_UUID) {
                        val value = characteristic.value
                        if (value != null) handleRawIncomingBytes(value)
                    }
                }
            }

            val gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            activeGatts[device.address] = gatt
        } catch (_: Exception) {}
    }

    private fun handleRawIncomingBytes(bytes: ByteArray) {
        val packet = SalimPacket.decode(bytes) ?: return
        if (packet.type == PacketType.FRAGMENT) {
            handleIncomingFragment(packet)
        } else {
            scope.launch {
                _incomingPackets.emit(packet)
            }
        }
    }

    private fun handleIncomingFragment(fragmentPacket: SalimPacket) {
        if (fragmentPacket.payload.size < BleConstants.FRAGMENT_HEADER_SIZE) return
        val buffer = ByteBuffer.wrap(fragmentPacket.payload)
        val totalFragments = buffer.get().toInt() and 0xFF
        val fragmentIndex = buffer.get().toInt() and 0xFF
        val origMsgId = ByteArray(16)
        buffer.get(origMsgId)
        val chunk = ByteArray(buffer.remaining())
        buffer.get(chunk)

        val origMsgIdHex = origMsgId.joinToString("") { "%02X".format(it) }
        val chunks = fragmentCache.computeIfAbsent(origMsgIdHex) { ConcurrentHashMap() }
        chunks[fragmentIndex] = chunk
        fragmentTotal[origMsgIdHex] = totalFragments

        if (chunks.size == totalFragments) {
            // Reassemble full packet
            var totalSize = 0
            for (i in 0 until totalFragments) {
                totalSize += chunks[i]?.size ?: 0
            }
            val reassembled = ByteArray(totalSize)
            var offset = 0
            for (i in 0 until totalFragments) {
                val part = chunks[i] ?: return
                System.arraycopy(part, 0, reassembled, offset, part.size)
                offset += part.size
            }
            fragmentCache.remove(origMsgIdHex)
            fragmentTotal.remove(origMsgIdHex)

            val fullPacket = SalimPacket.decode(reassembled)
            if (fullPacket != null) {
                scope.launch { _incomingPackets.emit(fullPacket) }
            }
        }
    }

    override suspend fun send(packet: SalimPacket): Boolean {
        val raw = packet.encode()
        val mtu = 200 // Safe write size across all devices
        var success = false

        if (raw.size > mtu) {
            // Fragment large packet
            val totalFragments = (raw.size + mtu - 1) / mtu
            for (i in 0 until totalFragments) {
                val start = i * mtu
                val end = minOf(start + mtu, raw.size)
                val chunk = raw.copyOfRange(start, end)

                val fragBuffer = ByteBuffer.allocate(BleConstants.FRAGMENT_HEADER_SIZE + chunk.size)
                fragBuffer.put(totalFragments.toByte())
                fragBuffer.put(i.toByte())
                fragBuffer.put(packet.messageId)
                fragBuffer.put(chunk)

                val fragPacket = SalimPacket(
                    type = PacketType.FRAGMENT,
                    flags = PacketFlags.IS_FRAGMENT,
                    ttl = packet.ttl,
                    messageId = SalimPacket.randomMessageId(),
                    senderId = packet.senderId,
                    recipientId = packet.recipientId,
                    timestamp = packet.timestamp,
                    payload = fragBuffer.array()
                )
                success = sendBytesToConnectedLinks(fragPacket.encode()) || success
            }
        } else {
            success = sendBytesToConnectedLinks(raw)
        }

        return success
    }

    private fun sendBytesToConnectedLinks(bytes: ByteArray): Boolean {
        var sentAny = false

        // 1. Write to connected Peripheral GATTs
        for (gatt in activeGatts.values) {
            try {
                val service = gatt.getService(BleConstants.SERVICE_UUID)
                val characteristic = service?.getCharacteristic(BleConstants.PACKET_CHAR_UUID)
                if (characteristic != null) {
                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    characteristic.value = bytes
                    val ok = gatt.writeCharacteristic(characteristic)
                    if (ok) sentAny = true
                }
            } catch (_: Exception) {}
        }

        // 2. Notify connected Central GATT clients
        val server = gattServer
        if (server != null && centralDevices.isNotEmpty()) {
            val service = server.getService(BleConstants.SERVICE_UUID)
            val characteristic = service?.getCharacteristic(BleConstants.PACKET_CHAR_UUID)
            if (characteristic != null) {
                characteristic.value = bytes
                for (device in centralDevices.values) {
                    try {
                        server.notifyCharacteristicChanged(device, characteristic, false)
                        sentAny = true
                    } catch (_: Exception) {}
                }
            }
        }

        return sentAny
    }
}
