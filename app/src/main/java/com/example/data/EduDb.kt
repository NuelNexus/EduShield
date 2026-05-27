package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// 1. Entities
@Entity(tableName = "activity_logs")
data class ActivityLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val target: String, // e.g., "com.instagram.android" or "tiktok.com"
    val activityType: String, // e.g., "APP_OPEN", "SITE_BLOCKED", "LIMIT_REACHED", "ADMIN_SETTING"
    val details: String,
    val severity: String // e.g., "INFO", "WARNING", "BLOCKED"
)

@Entity(tableName = "blocked_urls")
data class BlockedUrl(
    @PrimaryKey val domain: String, // e.g., "tiktok.com", "facebook.com"
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_limits")
data class AppLimit(
    @PrimaryKey val appName: String, // Friendly name, e.g., "TikTok", "Instagram", "Roblox"
    val packageName: String,        // Package name or pattern matching
    val limitMinutes: Int,          // 0 means unmanaged, >0 limit in minutes
    val usedMinutes: Int = 0,
    val lastReset: Long = System.currentTimeMillis()
) {
    val isLimitExceeded: Boolean
        get() = limitMinutes in 1..usedMinutes
}

@Entity(tableName = "mdm_policies")
data class MdmPolicy(
    @PrimaryKey val key: String, // e.g., "PIN", "CAMERA_DISABLED", "SCREEN_LOCKED", "MDM_ENABLED"
    val value: String
)

// 2. Data Access Object
@Dao
interface MdmDao {
    // Activity Logs
    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC LIMIT 200")
    fun getAllLogsFlow(): Flow<List<ActivityLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ActivityLog)

    @Query("DELETE FROM activity_logs")
    suspend fun clearLogs()

    // Blocked URLs
    @Query("SELECT * FROM blocked_urls ORDER BY addedAt DESC")
    fun getBlockedUrlsFlow(): Flow<List<BlockedUrl>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedUrl(url: BlockedUrl)

    @Delete
    suspend fun deleteBlockedUrl(url: BlockedUrl)

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_urls WHERE domain = :domain LIMIT 1)")
    suspend fun isUrlBlocked(domain: String): Boolean

    // App Limits
    @Query("SELECT * FROM app_limits ORDER BY appName ASC")
    fun getAppLimitsFlow(): Flow<List<AppLimit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppLimit(limit: AppLimit)

    @Query("UPDATE app_limits SET usedMinutes = :used WHERE appName = :name")
    suspend fun updateAppUsage(name: String, used: Int)

    @Query("UPDATE app_limits SET usedMinutes = 0")
    suspend fun resetAllAppUsage()

    // Policies & Settings
    @Query("SELECT value FROM mdm_policies WHERE `key` = :key LIMIT 1")
    suspend fun getPolicyValue(key: String): String?

    @Query("SELECT * FROM mdm_policies")
    fun getAllPoliciesFlow(): Flow<List<MdmPolicy>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPolicy(policy: MdmPolicy)
}

// 3. App Database
@Database(
    entities = [ActivityLog::class, BlockedUrl::class, AppLimit::class, MdmPolicy::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mdmDao(): MdmDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "eduguard_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
