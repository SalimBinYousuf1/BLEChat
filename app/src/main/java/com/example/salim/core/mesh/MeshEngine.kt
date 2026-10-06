package com.example.salim.core.mesh

import android.content.Context
import android.util.Base64
import com.example.salim.core.crypto.CryptoManager
import com.example.salim.core.crypto.IdentityKeys
import com.example.salim.core.media.MediaManager
import com.example.salim.core.model.ChatMessage
import com.example.salim.core.model.Conversation
import com.example.salim.core.model.DeliveryStatus
import com.example.salim.core.model.MessageType
import com.example.salim.core.model.PacketFlags
import com.example.salim.core.model.PacketType
import com.example.salim.core.model.Peer
import com.example.salim.core.model.SalimPacket
import com.example.salim.core.storage.SalimRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import kotlin.random.Random

class MeshEngine(
    val identity: IdentityKeys,
    private val transport: Transport,
    private val repository: SalimRepository,
    private val context: Context? = null
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // High-priority SOS alert events for UI notification
    private val _sosAlerts = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 8)
    val sosAlerts: SharedFlow<ChatMessage> = _sosAlerts.asSharedFlow()

    fun start() {
        transport.start()
        observeIncomingPackets()
        observeConnectedPeers()
        broadcastAnnounce()
    }

    fun stop() {
        transport.stop()
    }

    private fun observeIncomingPackets() {
        scope.launch {
            transport.incomingPackets.collect { packet ->
                handleIncomingPacket(packet)
            }
        }
    }

    private fun observeConnectedPeers() {
        scope.launch {
            transport.connectedPeers.collectLatest { peers ->
                for (peer in peers) {
                    repository.upsertPeer(peer)
                    // Check if we have pending store-and-forward packets for this peer
                    val pending = repository.getRelayPacketsForRecipient(peer.id)
                    for (packetBytes in pending) {
                        val packet = SalimPacket.decode(packetBytes)
                        if (packet != null) {
                            transport.send(packet)
                        }
                    }
                }
            }
        }
    }

    private suspend fun handleIncomingPacket(packet: SalimPacket) {
        val msgIdHex = packet.messageId.joinToString("") { "%02X".format(it) }

        // 1. Deduplication: Drop if already processed
        if (repository.hasSeenMessage(msgIdHex)) return
        repository.markMessageSeen(msgIdHex)

        val isForMe = packet.isForRecipient(identity.peerId)

        // 2. Handle Packet Types
        when (packet.type) {
            PacketType.ANNOUNCE -> {
                handleAnnouncePacket(packet)
            }
            PacketType.ACK -> {
                handleAckPacket(packet)
            }
            PacketType.MESSAGE, PacketType.SOS -> {
                handleMessagePacket(packet, isForMe)
            }
            else -> {
                // Other control types
            }
        }

        // 3. Multi-hop Relay: if packet is broadcast or not for me, decrement TTL and forward
        if (packet.isBroadcast || !isForMe) {
            val nextTtl = (packet.ttl - 1).toByte()
            if (nextTtl > 0) {
                val relayPacket = packet.copy(ttl = nextTtl)
                scope.launch {
                    // Smart suppression jitter (50 to 180 ms) to prevent storm
                    delay(Random.nextLong(50, 180))
                    transport.send(relayPacket)
                }

                // If recipient is offline, store in relay cache
                if (!packet.isBroadcast) {
                    val recipientHex = packet.recipientId.joinToString("") { "%02X".format(it) }
                    repository.cacheRelayPacket(msgIdHex, recipientHex, packet.encode())
                }
            }
        }
    }

    private suspend fun handleAnnouncePacket(packet: SalimPacket) {
        val senderHex = packet.senderId.joinToString("") { "%02X".format(it) }
        try {
            val buffer = ByteBuffer.wrap(packet.payload)
            val nickLen = buffer.short.toInt() and 0xFFFF
            val nickBytes = ByteArray(nickLen).also { buffer.get(it) }
            val nickname = String(nickBytes, StandardCharsets.UTF_8)
            val edPub = ByteArray(32).also { buffer.get(it) }
            val xPub = ByteArray(32).also { buffer.get(it) }

            val peer = Peer(
                id = senderHex,
                nickname = nickname,
                publicKeyEd25519Hex = edPub.joinToString("") { "%02x".format(it) },
                publicKeyX25519Hex = xPub.joinToString("") { "%02x".format(it) },
                lastSeenTimestamp = System.currentTimeMillis()
            )
            repository.upsertPeer(peer)
        } catch (_: Exception) {}
    }

    private suspend fun handleAckPacket(packet: SalimPacket) {
        val targetMsgIdHex = packet.payload.joinToString("") { "%02X".format(it) }
        repository.updateMessageStatus(targetMsgIdHex, DeliveryStatus.DELIVERED)
        repository.removeRelayPacket(targetMsgIdHex)
    }

    private suspend fun handleMessagePacket(packet: SalimPacket, isForMe: Boolean) {
        if (!isForMe && !packet.isBroadcast) return

        val senderHex = packet.senderId.joinToString("") { "%02X".format(it) }
        val msgIdHex = packet.messageId.joinToString("") { "%02X".format(it) }

        var rawContent = ""
        var msgType = if (packet.type == PacketType.SOS) MessageType.SOS else MessageType.TEXT

        if (packet.isEncrypted) {
            // Decrypt 1:1 message
            val peer = repository.getPeer(senderHex)
            if (peer != null && peer.publicKeyX25519Hex.isNotEmpty()) {
                val peerXPub = peer.publicKeyX25519Hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val sharedSecret = CryptoManager.calculateX25519SharedSecret(identity.x25519PrivateKey, peerXPub)
                val sessionKey = CryptoManager.deriveSessionKey(sharedSecret)
                val decrypted = CryptoManager.decryptChaCha20Poly1305(packet.payload, sessionKey)
                if (decrypted != null) {
                    rawContent = String(decrypted, StandardCharsets.UTF_8)
                } else {
                    rawContent = "[Encrypted message - decryption error]"
                }
            } else {
                rawContent = "[Encrypted message from unknown peer]"
            }
        } else {
            // Plaintext public channel or SOS broadcast
            rawContent = String(packet.payload, StandardCharsets.UTF_8)
        }

        // Parse optional replyTo prefix: [REPLY:<replyToId>]:<actual>
        var replyToId: String? = null
        var processedText = rawContent
        if (processedText.startsWith("[REPLY:") && processedText.contains("]:")) {
            val endIdx = processedText.indexOf("]:")
            replyToId = processedText.substring(7, endIdx)
            processedText = processedText.substring(endIdx + 2)
        }

        // Handle in-place message edit packet
        if (processedText.startsWith("[EDIT:") && processedText.contains("]:")) {
            val endIdx = processedText.indexOf("]:")
            val targetId = processedText.substring(6, endIdx)
            val newContent = processedText.substring(endIdx + 2)
            repository.editMessage(targetId, newContent)
            return
        }

        // Handle emoji reaction packet
        if (processedText.startsWith("[REACTION:") && processedText.contains("]:")) {
            val endIdx = processedText.indexOf("]:")
            val targetId = processedText.substring(10, endIdx)
            val emoji = processedText.substring(endIdx + 2)
            repository.reactToMessage(targetId, emoji)
            return
        }

        var mediaUri: String? = null

        // Parse Media Image or Media Audio
        if (processedText.startsWith("[MEDIA:IMAGE:]") && context != null) {
            msgType = MessageType.IMAGE
            val base64Data = processedText.removePrefix("[MEDIA:IMAGE:]")
            try {
                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                mediaUri = MediaManager.saveImageFile(context, bytes, msgIdHex)
                processedText = "📷 Photo"
            } catch (_: Exception) {}
        } else if (processedText.startsWith("[MEDIA:AUDIO:]") && context != null) {
            msgType = MessageType.AUDIO
            val base64Data = processedText.removePrefix("[MEDIA:AUDIO:]")
            try {
                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                mediaUri = MediaManager.saveAudioFile(context, bytes, msgIdHex)
                processedText = "🎤 Voice note"
            } catch (_: Exception) {}
        }

        val peer = repository.getPeer(senderHex)
        val senderNick = peer?.nickname ?: "Peer ${senderHex.take(4)}"

        val conversationId = if (packet.isBroadcast) {
            Conversation.PUBLIC_CHANNEL_ID
        } else {
            "direct_$senderHex"
        }

        val chatMessage = ChatMessage(
            id = msgIdHex,
            conversationId = conversationId,
            senderId = senderHex,
            senderNickname = senderNick,
            recipientId = if (packet.isBroadcast) "BROADCAST" else identity.peerIdHex,
            content = processedText,
            type = msgType,
            mediaUri = mediaUri,
            replyToId = replyToId,
            timestamp = packet.timestamp,
            status = DeliveryStatus.DELIVERED,
            isOutgoing = false
        )

        repository.saveMessage(chatMessage)

        if (msgType == MessageType.SOS) {
            _sosAlerts.emit(chatMessage)
        }

        // Send ACK back if requested and direct message
        if (packet.needsAck && !packet.isBroadcast) {
            sendAck(packet.senderId, packet.messageId)
        }
    }

    private fun sendAck(recipientId: ByteArray, originalMsgId: ByteArray) {
        val ackPacket = SalimPacket(
            type = PacketType.ACK,
            flags = 0,
            ttl = SalimPacket.DEFAULT_TTL,
            messageId = SalimPacket.randomMessageId(),
            senderId = identity.peerId,
            recipientId = recipientId,
            timestamp = System.currentTimeMillis(),
            payload = originalMsgId
        )
        scope.launch {
            transport.send(ackPacket)
        }
    }

    fun broadcastAnnounce() {
        val nickBytes = identity.nickname.toByteArray(StandardCharsets.UTF_8)
        val payload = ByteBuffer.allocate(2 + nickBytes.size + 32 + 32)
            .putShort(nickBytes.size.toShort())
            .put(nickBytes)
            .put(identity.ed25519PublicKey)
            .put(identity.x25519PublicKey)
            .array()

        val packet = SalimPacket(
            type = PacketType.ANNOUNCE,
            flags = PacketFlags.BROADCAST,
            ttl = 4,
            messageId = SalimPacket.randomMessageId(),
            senderId = identity.peerId,
            recipientId = SalimPacket.BROADCAST_RECIPIENT,
            timestamp = System.currentTimeMillis(),
            payload = payload
        )

        scope.launch {
            transport.send(packet)
        }
    }

    suspend fun sendTextMessage(
        conversationId: String,
        recipientPeerId: String,
        text: String,
        replyToId: String? = null
    ): ChatMessage {
        val payloadText = if (replyToId != null) "[REPLY:$replyToId]:$text" else text
        return sendPayload(
            conversationId = conversationId,
            recipientPeerId = recipientPeerId,
            wireText = payloadText,
            displayText = text,
            type = MessageType.TEXT,
            mediaUri = null,
            replyToId = replyToId
        )
    }

    suspend fun sendImageMessage(
        conversationId: String,
        recipientPeerId: String,
        imageBytes: ByteArray,
        savedPath: String,
        replyToId: String? = null
    ): ChatMessage {
        val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        val wireText = if (replyToId != null) "[REPLY:$replyToId]:[MEDIA:IMAGE:]$base64" else "[MEDIA:IMAGE:]$base64"
        return sendPayload(
            conversationId = conversationId,
            recipientPeerId = recipientPeerId,
            wireText = wireText,
            displayText = "📷 Photo",
            type = MessageType.IMAGE,
            mediaUri = savedPath,
            replyToId = replyToId
        )
    }

    suspend fun sendVoiceMessage(
        conversationId: String,
        recipientPeerId: String,
        audioBytes: ByteArray,
        savedPath: String,
        replyToId: String? = null
    ): ChatMessage {
        val base64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
        val wireText = if (replyToId != null) "[REPLY:$replyToId]:[MEDIA:AUDIO:]$base64" else "[MEDIA:AUDIO:]$base64"
        return sendPayload(
            conversationId = conversationId,
            recipientPeerId = recipientPeerId,
            wireText = wireText,
            displayText = "🎤 Voice note",
            type = MessageType.AUDIO,
            mediaUri = savedPath,
            replyToId = replyToId
        )
    }

    suspend fun sendEditMessage(
        conversationId: String,
        recipientPeerId: String,
        originalMsgId: String,
        newContent: String
    ) {
        repository.editMessage(originalMsgId, newContent)
        sendPayload(
            conversationId = conversationId,
            recipientPeerId = recipientPeerId,
            wireText = "[EDIT:$originalMsgId]:$newContent",
            displayText = newContent,
            type = MessageType.TEXT,
            mediaUri = null,
            replyToId = null
        )
    }

    suspend fun sendReactionMessage(
        conversationId: String,
        recipientPeerId: String,
        targetMsgId: String,
        emoji: String
    ) {
        repository.reactToMessage(targetMsgId, emoji)
        sendPayload(
            conversationId = conversationId,
            recipientPeerId = recipientPeerId,
            wireText = "[REACTION:$targetMsgId]:$emoji",
            displayText = emoji,
            type = MessageType.SYSTEM,
            mediaUri = null,
            replyToId = targetMsgId
        )
    }

    private suspend fun sendPayload(
        conversationId: String,
        recipientPeerId: String,
        wireText: String,
        displayText: String,
        type: MessageType,
        mediaUri: String?,
        replyToId: String?
    ): ChatMessage {
        val msgIdBytes = SalimPacket.randomMessageId()
        val msgIdHex = msgIdBytes.joinToString("") { "%02X".format(it) }

        val isBroadcast = recipientPeerId == "BROADCAST" || recipientPeerId.length != 16
        val recipientBytes = if (isBroadcast) {
            SalimPacket.BROADCAST_RECIPIENT
        } else {
            try {
                recipientPeerId.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            } catch (_: Exception) {
                SalimPacket.BROADCAST_RECIPIENT
            }
        }
        val peer = if (!isBroadcast) repository.getPeer(recipientPeerId) else null

        val (payloadBytes, flags) = if (isBroadcast) {
            Pair(wireText.toByteArray(StandardCharsets.UTF_8), PacketFlags.BROADCAST)
        } else if (peer != null && peer.publicKeyX25519Hex.isNotEmpty()) {
            val peerXPub = try {
                peer.publicKeyX25519Hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            } catch (_: Exception) { ByteArray(0) }

            if (peerXPub.size == 32) {
                val sharedSecret = CryptoManager.calculateX25519SharedSecret(identity.x25519PrivateKey, peerXPub)
                val sessionKey = CryptoManager.deriveSessionKey(sharedSecret)
                val enc = CryptoManager.encryptChaCha20Poly1305(wireText.toByteArray(StandardCharsets.UTF_8), sessionKey)
                Pair(enc, (PacketFlags.ENCRYPTED.toInt() or PacketFlags.NEEDS_ACK.toInt()).toByte())
            } else {
                Pair(wireText.toByteArray(StandardCharsets.UTF_8), PacketFlags.NEEDS_ACK)
            }
        } else {
            Pair(wireText.toByteArray(StandardCharsets.UTF_8), PacketFlags.NEEDS_ACK)
        }

        val packet = SalimPacket(
            type = PacketType.MESSAGE,
            flags = flags,
            ttl = SalimPacket.DEFAULT_TTL,
            messageId = msgIdBytes,
            senderId = identity.peerId,
            recipientId = recipientBytes,
            timestamp = System.currentTimeMillis(),
            payload = payloadBytes
        )

        val chatMessage = ChatMessage(
            id = msgIdHex,
            conversationId = conversationId,
            senderId = identity.peerIdHex,
            senderNickname = identity.nickname,
            recipientId = recipientPeerId,
            content = displayText,
            type = type,
            mediaUri = mediaUri,
            replyToId = replyToId,
            timestamp = packet.timestamp,
            status = DeliveryStatus.SENT_TO_MESH,
            isOutgoing = true
        )

        repository.saveMessage(chatMessage)
        repository.markMessageSeen(msgIdHex)

        val sent = transport.send(packet)
        if (!sent) {
            repository.cacheRelayPacket(msgIdHex, recipientPeerId, packet.encode())
        }

        return chatMessage
    }

    suspend fun sendPublicBroadcast(text: String): ChatMessage {
        val msgIdBytes = SalimPacket.randomMessageId()
        val msgIdHex = msgIdBytes.joinToString("") { "%02X".format(it) }

        val packet = SalimPacket(
            type = PacketType.MESSAGE,
            flags = PacketFlags.BROADCAST,
            ttl = SalimPacket.DEFAULT_TTL,
            messageId = msgIdBytes,
            senderId = identity.peerId,
            recipientId = SalimPacket.BROADCAST_RECIPIENT,
            timestamp = System.currentTimeMillis(),
            payload = text.toByteArray(StandardCharsets.UTF_8)
        )

        val chatMessage = ChatMessage(
            id = msgIdHex,
            conversationId = Conversation.PUBLIC_CHANNEL_ID,
            senderId = identity.peerIdHex,
            senderNickname = identity.nickname,
            recipientId = "BROADCAST",
            content = text,
            type = MessageType.TEXT,
            timestamp = packet.timestamp,
            status = DeliveryStatus.SENT_TO_MESH,
            isOutgoing = true
        )

        repository.saveMessage(chatMessage)
        repository.markMessageSeen(msgIdHex)
        transport.send(packet)

        return chatMessage
    }

    suspend fun sendEmergencySos(): ChatMessage {
        val msgIdBytes = SalimPacket.randomMessageId()
        val msgIdHex = msgIdBytes.joinToString("") { "%02X".format(it) }
        val sosText = "EMERGENCY SOS ALERT from ${identity.nickname} [${identity.formattedShortId}]"

        val packet = SalimPacket(
            type = PacketType.SOS,
            flags = PacketFlags.BROADCAST,
            ttl = SalimPacket.MAX_TTL,
            messageId = msgIdBytes,
            senderId = identity.peerId,
            recipientId = SalimPacket.BROADCAST_RECIPIENT,
            timestamp = System.currentTimeMillis(),
            payload = sosText.toByteArray(StandardCharsets.UTF_8)
        )

        val chatMessage = ChatMessage(
            id = msgIdHex,
            conversationId = Conversation.PUBLIC_CHANNEL_ID,
            senderId = identity.peerIdHex,
            senderNickname = identity.nickname,
            recipientId = "BROADCAST",
            content = sosText,
            type = MessageType.SOS,
            timestamp = packet.timestamp,
            status = DeliveryStatus.SENT_TO_MESH,
            isOutgoing = true
        )

        repository.saveMessage(chatMessage)
        repository.markMessageSeen(msgIdHex)
        transport.send(packet)

        return chatMessage
    }
}
