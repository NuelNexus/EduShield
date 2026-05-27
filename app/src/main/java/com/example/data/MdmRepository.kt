package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID

class MdmRepository(private val mdmDao: MdmDao) {

    val allLogs: Flow<List<ActivityLog>> = mdmDao.getAllLogsFlow()
    val blockedUrls: Flow<List<BlockedUrl>> = mdmDao.getBlockedUrlsFlow()
    val appLimits: Flow<List<AppLimit>> = mdmDao.getAppLimitsFlow()
    val allPolicies: Flow<List<MdmPolicy>> = mdmDao.getAllPoliciesFlow()

    // Logs
    suspend fun addLog(target: String, type: String, details: String, severity: String) {
        mdmDao.insertLog(
            ActivityLog(
                target = target,
                activityType = type,
                details = details,
                severity = severity
            )
        )
    }

    suspend fun clearLogs() {
        mdmDao.clearLogs()
    }

    // Web blocklist
    suspend fun blockUrl(domain: String) {
        val cleanDomain = domain.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.")
        if (cleanDomain.isNotEmpty()) {
            mdmDao.insertBlockedUrl(BlockedUrl(cleanDomain))
            addLog(
                target = cleanDomain,
                type = "SITE_BLOCKED",
                details = "Domain added to school firewall: $cleanDomain",
                severity = "INFO"
            )
        }
    }

    suspend fun allowUrl(domain: String) {
        mdmDao.deleteBlockedUrl(BlockedUrl(domain))
        addLog(
            target = domain,
            type = "SITE_ALOWED",
            details = "Domain removed from school firewall: $domain",
            severity = "INFO"
        )
    }

    suspend fun isUrlBlocked(domain: String): Boolean {
        val clean = domain.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.")
        // Also support subdomains checking (e.g. check if tiktok.com is blocked when visiting sub.tiktok.com)
        val blockedList = blockedUrls.firstOrNull() ?: emptyList()
        return blockedList.any { blockedItem ->
            clean == blockedItem.domain || clean.endsWith("." + blockedItem.domain)
        }
    }

    // App limits
    suspend fun setAppLimit(appName: String, packageName: String, limitMinutes: Int) {
        mdmDao.insertAppLimit(
            AppLimit(appName = appName, packageName = packageName, limitMinutes = limitMinutes)
        )
        addLog(
            target = packageName,
            type = "ADMIN_SETTING",
            details = "Set screen-time limit for $appName to $limitMinutes mins",
            severity = "INFO"
        )
    }

    suspend fun updateAppUsage(appName: String, usedMinutes: Int) {
        mdmDao.updateAppUsage(appName, usedMinutes)
    }

    suspend fun resetAllAppUsage() {
        mdmDao.resetAllAppUsage()
        addLog(
            target = "SYSTEM",
            type = "ADMIN_SETTING",
            details = "Daily screen time counters reset by administrative daemon",
            severity = "INFO"
        )
    }

    // Policies
    suspend fun setPolicy(key: String, value: String) {
        mdmDao.insertPolicy(MdmPolicy(key, value))
        addLog(
            target = "SYSTEM",
            type = "ADMIN_SETTING",
            details = "Device policy updated: [$key] -> $value",
            severity = "WARNING"
        )
    }

    suspend fun getPolicy(key: String, defaultValue: String): String {
        return mdmDao.getPolicyValue(key) ?: defaultValue
    }

    // First open setup to initialize clean dynamic device defaults
    suspend fun initializeMdmDefaultsIfNeeded() {
        val existingPin = mdmDao.getPolicyValue("PIN")
        if (existingPin == null) {
            mdmDao.insertPolicy(MdmPolicy("PIN", "1234")) // Default administration PIN choice
            mdmDao.insertPolicy(MdmPolicy("CAMERA_DISABLED", "false"))
            mdmDao.insertPolicy(MdmPolicy("SCREEN_LOCKED", "false"))
            mdmDao.insertPolicy(MdmPolicy("WEB_FILTER_ACTIVE", "true"))
            
            // Extract the actual device name and class level representation for real-time tracking
            val manufacturer = android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            val model = android.os.Build.MODEL
            val deviceFriendlyName = "$manufacturer $model"
            mdmDao.insertPolicy(MdmPolicy("STUDENT_NAME", deviceFriendlyName))
            mdmDao.insertPolicy(MdmPolicy("GRADE_CLASS", "Device Owner Admin Mode"))

            // Start with fully clean operational database states for zero fake tracking elements
            val now = System.currentTimeMillis()
            mdmDao.insertLog(
                ActivityLog(
                    target = "SYSTEM",
                    activityType = "ADMIN_SETTING",
                    details = "Secure Administrative Portal initialized. Zero-trust device monitoring activated on $deviceFriendlyName.",
                    severity = "INFO",
                    timestamp = now
                )
            )
        }
    }
}
