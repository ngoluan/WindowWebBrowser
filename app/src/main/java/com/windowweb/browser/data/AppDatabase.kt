package com.windowweb.browser.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WorkspaceEntity::class,
        WindowEntity::class,
        TabEntity::class,
        HistoryEntity::class,
        ConsoleLogEntity::class,
        NetworkLogEntity::class,
        SitePermissionEntity::class,
        DownloadEntity::class,
        BrowserSettingsEntity::class,
        RecentlyClosedTabEntity::class,
        WorkspaceCheckpointEntity::class,
        WebAppEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun windowDao(): WindowDao
    abstract fun tabDao(): TabDao
    abstract fun historyDao(): HistoryDao
    abstract fun consoleLogDao(): ConsoleLogDao
    abstract fun networkLogDao(): NetworkLogDao
    abstract fun sitePermissionDao(): SitePermissionDao
    abstract fun downloadDao(): DownloadDao
    abstract fun browserSettingsDao(): BrowserSettingsDao
    abstract fun recentlyClosedTabDao(): RecentlyClosedTabDao
    abstract fun workspaceCheckpointDao(): WorkspaceCheckpointDao
    abstract fun webAppDao(): WebAppDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "windowweb.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
