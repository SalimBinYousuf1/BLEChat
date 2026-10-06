package com.example.salim.core.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PeerEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        RelayCacheEntity::class,
        SeenMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SalimDatabase : RoomDatabase() {
    abstract fun peerDao(): PeerDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun relayCacheDao(): RelayCacheDao
    abstract fun seenMessageDao(): SeenMessageDao

    companion object {
        private const val DB_NAME = "salim_mesh_database.db"

        @Volatile
        private var INSTANCE: SalimDatabase? = null

        fun getInstance(context: Context): SalimDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SalimDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }

        /**
         * Clears all database tables and resets instance for Panic Wipe.
         */
        suspend fun wipeDatabase(context: Context) {
            val db = getInstance(context)
            db.clearAllTables()
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
            context.deleteDatabase(DB_NAME)
        }
    }
}
