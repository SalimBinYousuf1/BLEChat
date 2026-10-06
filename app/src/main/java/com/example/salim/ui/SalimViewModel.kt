package com.example.salim.ui

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.salim.core.crypto.CryptoManager
import com.example.salim.core.crypto.IdentityKeys
import com.example.salim.core.media.MediaManager
import com.example.salim.core.mesh.BleMeshTransport
import com.example.salim.core.mesh.MeshEngine
import com.example.salim.core.model.BatteryMode
import com.example.salim.core.model.ChatMessage
import com.example.salim.core.model.Conversation
import com.example.salim.core.model.Peer
import com.example.salim.service.SalimApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

class SalimViewModel(application: Application) : AndroidViewModel(application) {

    private val salimApp = application as SalimApplication
    val repository = salimApp.repository

    private val _identity = MutableStateFlow<IdentityKeys?>(repository.getIdentity())
    val identity: StateFlow<IdentityKeys?> = _identity.asStateFlow()

    val isOnboardingComplete: StateFlow<Boolean> = repository.isOnboardingComplete
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val conversations: StateFlow<List<Conversation>> = repository.conversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activePeers: StateFlow<List<Peer>> = repository.activePeers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val batteryMode: StateFlow<BatteryMode> = repository.batteryMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BatteryMode.BALANCED)

    val readReceipts: StateFlow<Boolean> = repository.readReceiptsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val flagSecure: StateFlow<Boolean> = repository.flagSecureEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()

    val currentMessages: StateFlow<List<ChatMessage>> = _currentConversationId.flatMapLatest { convId ->
        if (convId != null) repository.getMessagesForConversation(convId) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _sosMessageSent = MutableStateFlow<Boolean?>(null)
    val sosMessageSent: StateFlow<Boolean?> = _sosMessageSent.asStateFlow()

    // Real voice recorder instance
    val voiceRecorder = MediaManager.VoiceRecorder(salimApp)
    val voicePlayer = MediaManager.VoicePlayer()

    init {
        viewModelScope.launch {
            repository.ensurePublicChannelExists()
        }
    }

    private fun getMeshEngine(): MeshEngine {
        val existing = salimApp.meshEngine
        if (existing != null) return existing

        val id = _identity.value ?: repository.getIdentity() ?: throw IllegalStateException("No identity")
        val transport = BleMeshTransport(salimApp, id)
        val engine = MeshEngine(id, transport, repository, salimApp)
        engine.start()
        return engine
    }

    fun completeOnboarding(nickname: String) {
        viewModelScope.launch {
            val newIdentity = repository.createIdentity(nickname)
            _identity.value = newIdentity
            repository.setOnboardingComplete(true)
            salimApp.initMeshIfIdentityExists()
        }
    }

    fun openConversation(convId: String) {
        _currentConversationId.value = convId
        viewModelScope.launch {
            repository.markConversationRead(convId)
        }
    }

    fun closeConversation() {
        _currentConversationId.value = null
        voicePlayer.stop()
    }

    fun openDirectChatWithPeer(peer: Peer) {
        viewModelScope.launch {
            val conv = repository.getOrCreateDirectConversation(peer)
            openConversation(conv.id)
        }
    }

    fun sendMessage(text: String, replyToId: String? = null) {
        val convId = _currentConversationId.value ?: return
        viewModelScope.launch {
            val engine = getMeshEngine()
            if (convId == Conversation.PUBLIC_CHANNEL_ID) {
                engine.sendPublicBroadcast(text)
            } else if (convId.startsWith("direct_")) {
                val peerId = convId.removePrefix("direct_")
                engine.sendTextMessage(convId, peerId, text, replyToId)
            } else {
                // Group chat broadcast
                engine.sendPublicBroadcast(text)
            }
        }
    }

    fun sendImage(imageUri: Uri, replyToId: String? = null) {
        val convId = _currentConversationId.value ?: return
        viewModelScope.launch {
            val compressed = MediaManager.compressImage(salimApp, imageUri)
            if (compressed.isEmpty()) return@launch
            val localPath = MediaManager.saveImageFile(salimApp, compressed)
            val engine = getMeshEngine()

            if (convId.startsWith("direct_")) {
                val peerId = convId.removePrefix("direct_")
                engine.sendImageMessage(convId, peerId, compressed, localPath, replyToId)
            } else {
                val peerId = "BROADCAST"
                engine.sendImageMessage(convId, peerId, compressed, localPath, replyToId)
            }
        }
    }

    fun sendVoiceNote(audioBytes: ByteArray, replyToId: String? = null) {
        val convId = _currentConversationId.value ?: return
        viewModelScope.launch {
            val localPath = MediaManager.saveAudioFile(salimApp, audioBytes)
            val engine = getMeshEngine()

            if (convId.startsWith("direct_")) {
                val peerId = convId.removePrefix("direct_")
                engine.sendVoiceMessage(convId, peerId, audioBytes, localPath, replyToId)
            } else {
                val peerId = "BROADCAST"
                engine.sendVoiceMessage(convId, peerId, audioBytes, localPath, replyToId)
            }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
        }
    }

    fun triggerEmergencySos() {
        viewModelScope.launch {
            val engine = getMeshEngine()
            engine.sendEmergencySos()
            _sosMessageSent.value = true
        }
    }

    fun clearSosState() {
        _sosMessageSent.value = null
    }

    fun togglePeerVerified(peerId: String, currentVerified: Boolean) {
        viewModelScope.launch {
            repository.setPeerVerified(peerId, !currentVerified)
        }
    }

    fun togglePeerBlocked(peerId: String, currentBlocked: Boolean) {
        viewModelScope.launch {
            repository.setPeerBlocked(peerId, !currentBlocked)
        }
    }

    fun setBatteryMode(mode: BatteryMode) {
        viewModelScope.launch {
            repository.setBatteryMode(mode)
        }
    }

    fun toggleReadReceipts(enabled: Boolean) {
        viewModelScope.launch {
            repository.setReadReceipts(enabled)
        }
    }

    fun toggleFlagSecure(enabled: Boolean) {
        viewModelScope.launch {
            repository.setFlagSecure(enabled)
        }
    }

    fun createGroup(name: String) {
        viewModelScope.launch {
            val conv = repository.createGroupConversation(name)
            openConversation(conv.id)
        }
    }

    fun deleteConversation(convId: String) {
        viewModelScope.launch {
            repository.deleteConversation(convId)
            if (_currentConversationId.value == convId) {
                closeConversation()
            }
        }
    }

    fun exportEncryptedBackup(passphrase: String): String {
        val bytes = kotlinx.coroutines.runBlocking { repository.exportEncryptedBackup(passphrase) }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun importEncryptedBackup(backupBase64: String, passphrase: String): Boolean {
        return try {
            val cipher = Base64.decode(backupBase64.trim(), Base64.DEFAULT)
            val salt = "SalimBackupSalt".toByteArray()
            val key = CryptoManager.deriveSessionKey(passphrase.toByteArray(), salt)
            val decrypted = CryptoManager.decryptChaCha20Poly1305(cipher, key) ?: return false
            val payload = String(decrypted, StandardCharsets.UTF_8)
            val parts = payload.split("|")
            if (parts.size >= 4 && parts[0] == "SALIM_BACKUP_V1") {
                val nickname = parts[1]
                completeOnboarding(nickname)
                true
            } else false
        } catch (_: Exception) {
            false
        }
    }

    fun panicWipe(onWipeComplete: () -> Unit) {
        viewModelScope.launch {
            salimApp.meshEngine?.stop()
            repository.panicWipe()
            _identity.value = null
            onWipeComplete()
        }
    }

    override fun onCleared() {
        super.onCleared()
        voicePlayer.stop()
    }
}
