package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [KernelBackupEntity::class, FlashAuditLogEntity::class],
    version = 2,
    exportSchema = false
)
abstract class KalamaDatabase : RoomDatabase() {
    abstract fun kernelDao(): KernelDao

    companion object {
        @Volatile
        private var INSTANCE: KalamaDatabase? = null

        fun getInstance(context: Context): KalamaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KalamaDatabase::class.java,
                    "kalama_forge_kernel.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
