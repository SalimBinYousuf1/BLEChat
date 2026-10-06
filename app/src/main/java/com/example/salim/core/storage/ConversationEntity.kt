package com.example.salim.core.storage

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.salim.core.model.Conversation

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val peerId: String?,
    val isGroup: Boolean = false,
    val isPublicChannel: Boolean = false,
    val lastMessage: String = "",
    val lastTimestamp: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false
) {
    fun toConversation(): Conversation = Conversation(
        id = id,
        title = title,
        peerId = peerId,
        isGroup = isGroup,
        isPublicChannel = isPublicChannel,
        lastMessage = lastMessage,
        lastTimestamp = lastTimestamp,
        unreadCount = unreadCount,
        isPinned = isPinned,
        isMuted = isMuted
    )
}
