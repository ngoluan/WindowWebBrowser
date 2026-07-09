package com.windowweb.browser.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {
    @Query("SELECT COUNT(*) FROM workspaces")
    suspend fun count(): Int

    @Query("SELECT * FROM workspaces WHERE active = 1 LIMIT 1")
    fun observeActiveWorkspace(): Flow<WorkspaceEntity?>

    @Query("SELECT * FROM workspaces WHERE active = 1 LIMIT 1")
    suspend fun getActiveWorkspace(): WorkspaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(workspace: WorkspaceEntity)
}

@Dao
interface WindowDao {
    @Query("SELECT * FROM windows WHERE workspaceId = :workspaceId ORDER BY zIndex ASC")
    fun observeWindowsForWorkspace(workspaceId: String): Flow<List<WindowEntity>>

    @Query("SELECT * FROM windows WHERE workspaceId = :workspaceId ORDER BY zIndex ASC")
    suspend fun getWindowsForWorkspace(workspaceId: String): List<WindowEntity>

    @Query("SELECT * FROM windows WHERE workspaceId = :workspaceId AND minimized = 0 ORDER BY zIndex DESC LIMIT 1")
    fun observeFocusedWindow(workspaceId: String): Flow<WindowEntity?>

    @Query("SELECT * FROM windows WHERE workspaceId = :workspaceId AND minimized = 0 ORDER BY zIndex DESC LIMIT 1")
    suspend fun getFocusedWindow(workspaceId: String): WindowEntity?

    @Query("SELECT COALESCE(MAX(zIndex), 0) FROM windows WHERE workspaceId = :workspaceId")
    suspend fun maxZIndex(workspaceId: String): Int

    @Query("SELECT * FROM windows WHERE id = :windowId LIMIT 1")
    suspend fun getById(windowId: String): WindowEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(window: WindowEntity)

    @Query("UPDATE windows SET activeTabId = :tabId, updatedAt = :updatedAt WHERE id = :windowId")
    suspend fun setActiveTab(windowId: String, tabId: String, updatedAt: Long)

    @Query("UPDATE windows SET zIndex = :zIndex, minimized = 0, updatedAt = :updatedAt WHERE id = :windowId")
    suspend fun focus(windowId: String, zIndex: Int, updatedAt: Long)

    @Query("UPDATE windows SET x = :x, y = :y, width = :width, height = :height, mode = :mode, minimized = :minimized, updatedAt = :updatedAt WHERE id = :windowId")
    suspend fun updateBoundsAndMode(windowId: String, x: Float, y: Float, width: Float, height: Float, mode: String, minimized: Boolean, updatedAt: Long)

    @Query("UPDATE windows SET chromeMode = :chromeMode, updatedAt = :updatedAt WHERE id = :windowId")
    suspend fun setChromeMode(windowId: String, chromeMode: String, updatedAt: Long)

    @Query("UPDATE windows SET desktopMode = :enabled, desktopViewportWidthDp = :viewportWidthDp, updatedAt = :updatedAt WHERE id = :windowId")
    suspend fun setDesktopMode(windowId: String, enabled: Boolean, viewportWidthDp: Int, updatedAt: Long)

    @Query("UPDATE windows SET pageZoomPercent = :pageZoomPercent, updatedAt = :updatedAt WHERE id = :windowId")
    suspend fun setPageZoom(windowId: String, pageZoomPercent: Int, updatedAt: Long)

    @Query("UPDATE windows SET textZoomPercent = :textZoomPercent, updatedAt = :updatedAt WHERE id = :windowId")
    suspend fun setTextZoom(windowId: String, textZoomPercent: Int, updatedAt: Long)

    @Query("UPDATE windows SET desktopViewportWidthDp = :viewportWidthDp, updatedAt = :updatedAt WHERE id = :windowId")
    suspend fun setDesktopViewportWidth(windowId: String, viewportWidthDp: Int, updatedAt: Long)

    @Query("DELETE FROM windows WHERE id = :windowId")
    suspend fun delete(windowId: String)
}

@Dao
interface TabDao {
    @Query("SELECT * FROM tabs WHERE id = :tabId LIMIT 1")
    fun observeById(tabId: String): Flow<TabEntity?>

    @Query("SELECT * FROM tabs WHERE id = :tabId LIMIT 1")
    suspend fun getById(tabId: String): TabEntity?

    @Query("SELECT * FROM tabs ORDER BY lastActiveAt DESC")
    fun observeAll(): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs ORDER BY lastActiveAt DESC")
    suspend fun getAllTabs(): List<TabEntity>

    @Query("SELECT * FROM tabs WHERE windowId = :windowId ORDER BY lastActiveAt DESC")
    fun observeTabsForWindow(windowId: String): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs WHERE windowId = :windowId ORDER BY lastActiveAt DESC")
    suspend fun getTabsForWindow(windowId: String): List<TabEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tab: TabEntity)

    @Query("UPDATE tabs SET windowId = :windowId, lastActiveAt = :updatedAt, updatedAt = :updatedAt WHERE id = :tabId")
    suspend fun moveToWindow(tabId: String, windowId: String, updatedAt: Long)

    @Query("UPDATE tabs SET url = :url, title = :title, loading = :loading, updatedAt = :updatedAt, lastActiveAt = :updatedAt WHERE id = :tabId")
    suspend fun updateNavigation(tabId: String, url: String, title: String?, loading: Boolean, updatedAt: Long)

    @Query("UPDATE tabs SET loading = :loading, updatedAt = :updatedAt WHERE id = :tabId")
    suspend fun updateLoading(tabId: String, loading: Boolean, updatedAt: Long)

    @Query("UPDATE tabs SET lastActiveAt = :updatedAt WHERE id = :tabId")
    suspend fun markActive(tabId: String, updatedAt: Long)

    @Query("UPDATE tabs SET discarded = :discarded, suspended = :suspended, updatedAt = :updatedAt WHERE id = :tabId")
    suspend fun updateLifecycle(tabId: String, discarded: Boolean, suspended: Boolean, updatedAt: Long)

    @Query("DELETE FROM tabs WHERE id = :tabId")
    suspend fun delete(tabId: String)

    @Query("DELETE FROM tabs WHERE windowId = :windowId")
    suspend fun deleteForWindow(windowId: String)
}

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HistoryEntity)

    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<HistoryEntity>>
}

@Dao
interface ConsoleLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ConsoleLogEntity)

    @Query("SELECT * FROM console_logs WHERE tabId = :tabId ORDER BY timestamp DESC LIMIT :limit")
    fun observeForTab(tabId: String, limit: Int = 200): Flow<List<ConsoleLogEntity>>

    @Query("DELETE FROM console_logs WHERE tabId = :tabId")
    suspend fun clearForTab(tabId: String)
}

@Dao
interface NetworkLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: NetworkLogEntity)

    @Query("SELECT * FROM network_logs WHERE tabId = :tabId ORDER BY timestamp DESC LIMIT :limit")
    fun observeForTab(tabId: String, limit: Int = 200): Flow<List<NetworkLogEntity>>

    @Query("DELETE FROM network_logs WHERE tabId = :tabId")
    suspend fun clearForTab(tabId: String)
}

@Dao
interface SitePermissionDao {
    @Query("SELECT * FROM site_permissions ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SitePermissionEntity>>

    @Query("SELECT * FROM site_permissions WHERE origin = :origin AND resource = :resource LIMIT 1")
    suspend fun get(origin: String, resource: String): SitePermissionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SitePermissionEntity)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DownloadEntity)
}

@Dao
interface BrowserSettingsDao {
    @Query("SELECT * FROM browser_settings WHERE id = 'default' LIMIT 1")
    fun observe(): Flow<BrowserSettingsEntity?>

    @Query("SELECT * FROM browser_settings WHERE id = 'default' LIMIT 1")
    suspend fun get(): BrowserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: BrowserSettingsEntity)
}

@Dao
interface RecentlyClosedTabDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: RecentlyClosedTabEntity)

    @Query("SELECT * FROM recently_closed_tabs ORDER BY closedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<RecentlyClosedTabEntity>>
}

@Dao
interface WorkspaceCheckpointDao {
    @Query("SELECT * FROM workspace_checkpoints WHERE id = 'latest' LIMIT 1")
    fun observeLatest(): Flow<WorkspaceCheckpointEntity?>

    @Query("SELECT * FROM workspace_checkpoints WHERE id = 'latest' LIMIT 1")
    suspend fun latest(): WorkspaceCheckpointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WorkspaceCheckpointEntity)
}

@Dao
interface WebAppDao {
    @Query("SELECT * FROM web_apps ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<WebAppEntity>>

    @Query("SELECT * FROM web_apps WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WebAppEntity?

    @Query("SELECT * FROM web_apps WHERE origin = :origin LIMIT 1")
    suspend fun getByOrigin(origin: String): WebAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WebAppEntity)

    @Query("DELETE FROM web_apps WHERE id = :id")
    suspend fun delete(id: String)
}
