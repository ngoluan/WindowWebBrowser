package com.windowweb.browser.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 5,
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
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE windows ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE windows ADD COLUMN opacity REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE windows ADD COLUMN layoutLocked INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "windowweb.db"
                )
                    .addMigrations(MIGRATION_4_5)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
