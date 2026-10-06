package com.example.salim.core.storage

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.salim.core.model.ChatMessage
import com.example.salim.core.model.DeliveryStatus
import com.example.salim.core.model.MessageType

@Entity(
    tableName = "messages",
    indices = [
        Index("conversationId"),
        Index("timestamp")
    ]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val senderNickname: String,
    val recipientId: String,
    val content: String,
    val type: String = MessageType.TEXT.name,
    val mediaUri: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = DeliveryStatus.SENDING.name,
    val replyToId: String? = null,
    val reaction: String? = null,
    val isEdited: Boolean = false,
    val isOutgoing: Boolean = true
) {
    fun toChatMessage(): ChatMessage = ChatMessage(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        senderNickname = senderNickname,
        recipientId = recipientId,
        content = content,
        type = try { MessageType.valueOf(type) } catch (_: Exception) { MessageType.TEXT },
        mediaUri = mediaUri,
        timestamp = timestamp,
        status = try { DeliveryStatus.valueOf(status) } catch (_: Exception) { DeliveryStatus.SENDING },
        replyToId = replyToId,
        reaction = reaction,
        reactions = if (reaction != null) mapOf(reaction to 1) else emptyMap(),
        isEdited = isEdited,
        isOutgoing = isOutgoing
    )
}
