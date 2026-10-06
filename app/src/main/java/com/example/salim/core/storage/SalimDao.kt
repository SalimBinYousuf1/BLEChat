package com.example.salim.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerDao {
    @Query("SELECT * FROM peers ORDER BY lastSeenTimestamp DESC")
    fun getAllPeers(): Flow<List<PeerEntity>>

    @Query("SELECT * FROM peers WHERE isBlocked = 0 ORDER BY lastSeenTimestamp DESC")
    fun getActivePeers(): Flow<List<PeerEntity>>

    @Query("SELECT * FROM peers WHERE id = :peerId LIMIT 1")
    suspend fun getPeerById(peerId: String): PeerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPeer(peer: PeerEntity)

    @Query("UPDATE peers SET lastSeenTimestamp = :timestamp, rssi = :rssi, hops = :hops WHERE id = :peerId")
    suspend fun updatePeerPresence(peerId: String, timestamp: Long, rssi: Int, hops: Int)

    @Query("UPDATE peers SET isVerified = :isVerified WHERE id = :peerId")
    suspend fun setVerified(peerId: String, isVerified: Boolean)

    @Query("UPDATE peers SET isBlocked = :isBlocked WHERE id = :peerId")
    suspend fun setBlocked(peerId: String, isBlocked: Boolean)

    @Query("DELETE FROM peers WHERE id = :peerId")
    suspend fun deletePeer(peerId: String)

    @Query("DELETE FROM peers")
    suspend fun wipeAll()
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY isPinned DESC, lastTimestamp DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE peerId = :peerId LIMIT 1")
    suspend fun getConversationByPeerId(peerId: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Query("UPDATE conversations SET lastMessage = :lastMessage, lastTimestamp = :timestamp, unreadCount = unreadCount + :unreadDelta WHERE id = :id")
    suspend fun updateLastMessage(id: String, lastMessage: String, timestamp: Long, unreadDelta: Int = 0)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE id = :id")
    suspend fun markAsRead(id: String)

    @Query("UPDATE conversations SET isPinned = :isPinned WHERE id = :id")
    suspend fun setPinned(id: String, isPinned: Boolean)

    @Query("UPDATE conversations SET isMuted = :isMuted WHERE id = :id")
    suspend fun setMuted(id: String, isMuted: Boolean)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("DELETE FROM conversations")
    suspend fun wipeAll()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :convId ORDER BY timestamp ASC")
    fun getMessagesForConversation(convId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateMessageStatus(id: String, status: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :convId")
    suspend fun deleteConversationMessages(convId: String)

    @Query("DELETE FROM messages")
    suspend fun wipeAll()
}

@Dao
interface RelayCacheDao {
    @Query("SELECT * FROM relay_cache WHERE expiryTimestamp > :now ORDER BY timestamp ASC LIMIT 500")
    suspend fun getPendingRelayPackets(now: Long = System.currentTimeMillis()): List<RelayCacheEntity>

    @Query("SELECT * FROM relay_cache WHERE recipientIdHex = :recipientIdHex AND expiryTimestamp > :now")
    suspend fun getPacketsForRecipient(recipientIdHex: String, now: Long = System.currentTimeMillis()): List<RelayCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelayPacket(entry: RelayCacheEntity)

    @Query("DELETE FROM relay_cache WHERE messageIdHex = :messageIdHex")
    suspend fun deleteRelayPacket(messageIdHex: String)

    @Query("DELETE FROM relay_cache WHERE expiryTimestamp <= :now")
    suspend fun purgeExpired(now: Long = System.currentTimeMillis())

    @Query("DELETE FROM relay_cache")
    suspend fun wipeAll()
}

@Dao
interface SeenMessageDao {
    @Query("SELECT COUNT(*) FROM seen_messages WHERE messageIdHex = :messageIdHex")
    suspend fun hasSeenMessage(messageIdHex: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markSeen(entry: SeenMessageEntity)

    @Query("DELETE FROM seen_messages WHERE seenTimestamp < :threshold")
    suspend fun purgeOldSeen(threshold: Long)

    @Query("DELETE FROM seen_messages")
    suspend fun wipeAll()
}
