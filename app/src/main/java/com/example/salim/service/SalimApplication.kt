package com.example.salim.service

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.R
import com.example.salim.core.mesh.BleMeshTransport
import com.example.salim.core.mesh.MeshEngine
import com.example.salim.core.storage.SalimDatabase
import com.example.salim.core.storage.SalimRepository

class SalimApplication : Application() {

    lateinit var database: SalimDatabase
        private set

    lateinit var repository: SalimRepository
        private set

    var meshEngine: MeshEngine? = null
        private set

    override fun onCreate() {
        super.onCreate()
        database = SalimDatabase.getInstance(this)
        repository = SalimRepository(this, database)
        createNotificationChannels()
        initMeshIfIdentityExists()
    }

    fun initMeshIfIdentityExists() {
        val identity = repository.getIdentity() ?: return
        if (meshEngine == null) {
            val transport = BleMeshTransport(this, identity)
            val engine = MeshEngine(identity, transport, repository, this)
            meshEngine = engine
            engine.start()
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                getString(R.string.notif_channel_mesh),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ongoing mesh connection status"
                setShowBadge(false)
            }

            val messageChannel = NotificationChannel(
                CHANNEL_MESSAGES_ID,
                getString(R.string.notif_channel_messages),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for incoming private and group messages"
                enableVibration(true)
            }

            val sosChannel = NotificationChannel(
                CHANNEL_SOS_ID,
                getString(R.string.notif_channel_sos),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority alerts for emergency SOS mesh broadcasts"
                enableVibration(true)
            }

            manager.createNotificationChannels(listOf(serviceChannel, messageChannel, sosChannel))
        }
    }

    companion object {
        const val CHANNEL_SERVICE_ID = "salim_mesh_service_channel"
        const val CHANNEL_MESSAGES_ID = "salim_messages_channel"
        const val CHANNEL_SOS_ID = "salim_sos_channel"
    }
}
