package com.example.salim.core.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.salim.core.crypto.CryptoManager
import com.example.salim.core.crypto.IdentityKeys
import com.example.salim.core.model.BatteryMode
import com.example.salim.core.model.ChatMessage
import com.example.salim.core.model.Conversation
import com.example.salim.core.model.DeliveryStatus
import com.example.salim.core.model.MessageType
import com.example.salim.core.model.Peer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "salim_settings")

class SalimRepository(
    private val context: Context,
    private val db: SalimDatabase
) {
    private val peerDao = db.peerDao()
    private val conversationDao = db.conversationDao()
    private val messageDao = db.messageDao()
    private val relayCacheDao = db.relayCacheDao()
    private val seenMessageDao = db.seenMessageDao()

    companion object {
        private val KEY_BATTERY_MODE = stringPreferencesKey("battery_mode")
        private val KEY_READ_RECEIPTS = booleanPreferencesKey("read_receipts")
        private val KEY_FLAG_SECURE = booleanPreferencesKey("flag_secure")
        private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    // Identity
    fun getIdentity(): IdentityKeys? = CryptoManager.loadIdentity(context)

    fun createIdentity(nickname: String): IdentityKeys {
        val identity = CryptoManager.createIdentity(nickname)
        CryptoManager.saveIdentity(context, identity)
        return identity
    }

    // Preferences
    val batteryMode: Flow<BatteryMode> = context.dataStore.data.map { prefs ->
        val modeStr = prefs[KEY_BATTERY_MODE] ?: BatteryMode.BALANCED.name
        try { BatteryMode.valueOf(modeStr) } catch (_: Exception) { BatteryMode.BALANCED }
    }

    suspend fun setBatteryMode(mode: BatteryMode) {
        context.dataStore.edit { it[KEY_BATTERY_MODE] = mode.name }
    }

    val readReceiptsEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_READ_RECEIPTS] ?: true
    }

    suspend fun setReadReceipts(enabled: Boolean) {
        context.dataStore.edit { it[KEY_READ_RECEIPTS] = enabled }
    }

    val flagSecureEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_FLAG_SECURE] ?: false
    }

    suspend fun setFlagSecure(enabled: Boolean) {
        context.dataStore.edit { it[KEY_FLAG_SECURE] = enabled }
    }

    val isOnboardingComplete: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_ONBOARDING_DONE] ?: false
    }

    suspend fun setOnboardingComplete(completed: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_DONE] = completed }
    }

    // Peers
    val activePeers: Flow<List<Peer>> = peerDao.getActivePeers().map { list ->
        list.map { it.toPeer() }
    }

    suspend fun getPeer(peerId: String): Peer? = peerDao.getPeerById(peerId)?.toPeer()

    suspend fun upsertPeer(peer: Peer) {
        peerDao.upsertPeer(
            PeerEntity(
                id = peer.id,
                nickname = peer.nickname,
                publicKeyEd25519Hex = peer.publicKeyEd25519Hex,
                publicKeyX25519Hex = peer.publicKeyX25519Hex,
                isVerified = peer.isVerified,
                isBlocked = peer.isBlocked,
                lastSeenTimestamp = peer.lastSeenTimestamp,
                rssi = peer.rssi,
                hops = peer.hops
            )
        )
    }

    suspend fun setPeerVerified(peerId: String, verified: Boolean) {
        peerDao.setVerified(peerId, verified)
    }

    suspend fun setPeerBlocked(peerId: String, blocked: Boolean) {
        peerDao.setBlocked(peerId, blocked)
    }

    // Conversations
    val conversations: Flow<List<Conversation>> = conversationDao.getAllConversations().map { list ->
        list.map { it.toConversation() }
    }

    suspend fun ensurePublicChannelExists(): Conversation {
        val existing = conversationDao.getConversationById(Conversation.PUBLIC_CHANNEL_ID)
        if (existing != null) return existing.toConversation()

        val publicConv = ConversationEntity(
            id = Conversation.PUBLIC_CHANNEL_ID,
            title = "Public Nearby Channel",
            peerId = null,
            isGroup = false,
            isPublicChannel = true,
            lastMessage = "Welcome to the local mesh public broadcast",
            lastTimestamp = System.currentTimeMillis(),
            unreadCount = 0,
            isPinned = true
        )
        conversationDao.upsertConversation(publicConv)
        return publicConv.toConversation()
    }

    suspend fun getOrCreateDirectConversation(peer: Peer): Conversation {
        val convId = "direct_${peer.id}"
        val existing = conversationDao.getConversationById(convId)
        if (existing != null) return existing.toConversation()

        val entity = ConversationEntity(
            id = convId,
            title = peer.nickname,
            peerId = peer.id,
            isGroup = false,
            isPublicChannel = false,
            lastMessage = "Direct encrypted channel created",
            lastTimestamp = System.currentTimeMillis()
        )
        conversationDao.upsertConversation(entity)
        return entity.toConversation()
    }

    suspend fun createGroupConversation(name: String): Conversation {
        val groupId = "group_${UUID.randomUUID().toString().take(8)}"
        val entity = ConversationEntity(
            id = groupId,
            title = name,
            peerId = null,
            isGroup = true,
            isPublicChannel = false,
            lastMessage = "Group created",
            lastTimestamp = System.currentTimeMillis()
        )
        conversationDao.upsertConversation(entity)
        return entity.toConversation()
    }

    suspend fun markConversationRead(convId: String) {
        conversationDao.markAsRead(convId)
    }

    suspend fun deleteConversation(convId: String) {
        conversationDao.deleteConversation(convId)
        messageDao.deleteConversationMessages(convId)
    }

    // Messages
    fun getMessagesForConversation(convId: String): Flow<List<ChatMessage>> {
        return messageDao.getMessagesForConversation(convId).map { list ->
            list.map { it.toChatMessage() }
        }
    }

    suspend fun saveMessage(message: ChatMessage) {
        messageDao.insertMessage(
            MessageEntity(
                id = message.id,
                conversationId = message.conversationId,
                senderId = message.senderId,
                senderNickname = message.senderNickname,
                recipientId = message.recipientId,
                content = message.content,
                type = message.type.name,
                mediaUri = message.mediaUri,
                timestamp = message.timestamp,
                status = message.status.name,
                replyToId = message.replyToId,
                isOutgoing = message.isOutgoing
            )
        )
        // Update conversation last message preview
        val preview = when (message.type) {
            MessageType.TEXT -> message.content
            MessageType.SOS -> "🚨 EMERGENCY SOS ALERT"
            MessageType.IMAGE -> "📷 Photo"
            MessageType.AUDIO -> "🎤 Voice note"
            MessageType.SYSTEM -> message.content
        }
        val unreadDelta = if (!message.isOutgoing) 1 else 0
        conversationDao.updateLastMessage(message.conversationId, preview, message.timestamp, unreadDelta)
    }

    suspend fun updateMessageStatus(messageId: String, status: DeliveryStatus) {
        messageDao.updateMessageStatus(messageId, status.name)
    }

    suspend fun deleteMessage(messageId: String) {
        messageDao.deleteMessage(messageId)
    }

    // Relay Cache & Dedupe
    suspend fun hasSeenMessage(messageIdHex: String): Boolean {
        return seenMessageDao.hasSeenMessage(messageIdHex) > 0
    }

    suspend fun markMessageSeen(messageIdHex: String) {
        seenMessageDao.markSeen(SeenMessageEntity(messageIdHex, System.currentTimeMillis()))
    }

    suspend fun cacheRelayPacket(messageIdHex: String, recipientIdHex: String, packetBytes: ByteArray) {
        relayCacheDao.insertRelayPacket(
            RelayCacheEntity(
                messageIdHex = messageIdHex,
                packetBytes = packetBytes,
                recipientIdHex = recipientIdHex
            )
        )
    }

    suspend fun getRelayPacketsForRecipient(recipientIdHex: String): List<ByteArray> {
        return relayCacheDao.getPacketsForRecipient(recipientIdHex).map { it.packetBytes }
    }

    suspend fun removeRelayPacket(messageIdHex: String) {
        relayCacheDao.deleteRelayPacket(messageIdHex)
    }

    // Panic Wipe
    suspend fun panicWipe() {
        CryptoManager.wipeAllKeys(context)
        SalimDatabase.wipeDatabase(context)
        context.dataStore.edit { it.clear() }
    }

    // Encrypted Backup
    suspend fun exportEncryptedBackup(passphrase: String): ByteArray {
        val identity = getIdentity() ?: throw IllegalStateException("No identity")
        val payload = "SALIM_BACKUP_V1|${identity.nickname}|${identity.peerIdHex}|${identity.ed25519PubHex}"
        val salt = "SalimBackupSalt".toByteArray()
        val key = CryptoManager.deriveSessionKey(passphrase.toByteArray(), salt)
        return CryptoManager.encryptChaCha20Poly1305(payload.toByteArray(Charsets.UTF_8), key)
    }
}
