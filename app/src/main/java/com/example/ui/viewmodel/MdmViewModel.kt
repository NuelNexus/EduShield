package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.app.usage.UsageStatsManager
import android.app.AppOpsManager
import android.os.Process
import java.util.Calendar
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DeviceApp(
    val appName: String,
    val packageName: String
)

class MdmViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MdmRepository

    val installedDeviceApps = MutableStateFlow<List<DeviceApp>>(emptyList())

    init {
        val dao = AppDatabase.getDatabase(application).mdmDao()
        repository = MdmRepository(dao)

        viewModelScope.launch {
            // Check for previous mock data and purge the database to transition completely to real logs
            val currentStudent = repository.getPolicy("STUDENT_NAME", "")
            if (currentStudent == "Alex Carter" || currentStudent.isEmpty()) {
                repository.clearLogs()
                // Force a rebuild with dynamic device names and fresh tracking assets
                dao.resetAllAppUsage()
            }
            repository.initializeMdmDefaultsIfNeeded()
            loadInstalledApps()
            syncActualUsageStats()
            startRealTimeTracking()
            
            // Periodically keep VPN active state in sync with actual service companion
            while (true) {
                checkVpnStatus()
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    // State flows from database
    val logs: StateFlow<List<ActivityLog>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blockedUrls: StateFlow<List<BlockedUrl>> = repository.blockedUrls
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appLimits: StateFlow<List<AppLimit>> = repository.appLimits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val policies: StateFlow<List<MdmPolicy>> = repository.allPolicies
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isSetupCompleted: StateFlow<Boolean> = policies.map { list ->
        list.any { it.key == "SETUP_COMPLETED" && it.value == "true" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val schoolName: StateFlow<String> = policies.map { list ->
        list.find { it.key == "SCHOOL_NAME" }?.value ?: "EduGuard School"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "EduGuard School")

    val globalLimitMinutes: StateFlow<Int> = policies.map { list ->
        list.find { it.key == "GLOBAL_LIMIT_MINUTES" }?.value?.toIntOrNull() ?: 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Virtual Interface VPN content blocking service integration
    val isVpnActive = MutableStateFlow(false)

    fun checkVpnStatus() {
        isVpnActive.value = com.example.service.EduGuardVpnService.isRunning
    }

    fun startVpnService() {
        val context = getApplication<Application>()
        val intent = Intent(context, com.example.service.EduGuardVpnService::class.java).apply {
            action = com.example.service.EduGuardVpnService.ACTION_START
        }
        context.startService(intent)
        isVpnActive.value = true
    }

    fun stopVpnService() {
        val context = getApplication<Application>()
        val intent = Intent(context, com.example.service.EduGuardVpnService::class.java).apply {
            action = com.example.service.EduGuardVpnService.ACTION_STOP
        }
        context.startService(intent)
        isVpnActive.value = false
    }

    // Admin authorization UI state
    private val _isAdminAuthenticated = MutableStateFlow(false)
    val isAdminAuthenticated: StateFlow<Boolean> = _isAdminAuthenticated.asStateFlow()

    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError.asStateFlow()

    // Activity Log filtering
    private val _logFilter = MutableStateFlow("ALL") // "ALL", "BLOCKED", "SYSTEM", "INFO"
    val logFilter: StateFlow<String> = _logFilter.asStateFlow()

    val filteredLogs: StateFlow<List<ActivityLog>> = combine(logs, logFilter) { logList, filter ->
        when (filter) {
            "ALL" -> logList
            "BLOCKED" -> logList.filter { it.activityType == "SITE_BLOCKED" || it.severity == "BLOCKED" }
            "SYSTEM" -> logList.filter { it.activityType == "SYSTEM_ALERT" || it.activityType == "ADMIN_SETTING" }
            "INFO" -> logList.filter { it.activityType == "APP_OPEN" || it.activityType == "SITE_VISITED" || it.severity == "INFO" }
            else -> logList
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Simulated Student Simulation Inputs
    private val _simulatedUrlInput = MutableStateFlow("")
    val simulatedUrlInput: StateFlow<String> = _simulatedUrlInput.asStateFlow()

    private val _simulatedStatusMessage = MutableStateFlow<Pair<String, Boolean>?>(null) // Class, isBlocked
    val simulatedStatusMessage: StateFlow<Pair<String, Boolean>?> = _simulatedStatusMessage.asStateFlow()

    // Administrative Actions
    fun updateLogFilter(filter: String) {
        _logFilter.value = filter
    }

    fun setSimulatedUrl(url: String) {
        _simulatedUrlInput.value = url
        _simulatedStatusMessage.value = null
    }

    fun verifyPasscode(inputPin: String) {
        viewModelScope.launch {
            val actualPin = repository.getPolicy("PIN", "1234")
            if (inputPin == actualPin) {
                _isAdminAuthenticated.value = true
                _pinError.value = null
                repository.addLog("ADMIN_PORTAL", "ADMIN_SETTING", "Administrative portal accessed successfully", "INFO")
            } else {
                _pinError.value = "Incorrect passcode. Security log recorded."
                repository.addLog("ADMIN_PORTAL", "SYSTEM_ALERT", "Unauthorized attempt to access Admin console with PIN: $inputPin", "WARNING")
            }
        }
    }

    fun changeAdminPasscode(newPin: String) {
        viewModelScope.launch {
            if (newPin.length == 4 && newPin.all { it.isDigit() }) {
                repository.setPolicy("PIN", newPin)
                repository.addLog("ADMIN_PORTAL", "ADMIN_SETTING", "Administrative passcode updated", "INFO")
            }
        }
    }

    fun logoutAdmin() {
        _isAdminAuthenticated.value = false
    }

    fun blockDomain(domain: String) {
        viewModelScope.launch {
            repository.blockUrl(domain)
        }
    }

    fun unblockDomain(domain: String) {
        viewModelScope.launch {
            repository.allowUrl(domain)
        }
    }

    fun addAppLimit(appName: String, packageName: String, minutes: Int) {
        viewModelScope.launch {
            repository.setAppLimit(appName, packageName, minutes)
        }
    }

    fun deleteAppLimit(appName: String, packageName: String) {
        viewModelScope.launch {
            repository.setAppLimit(appName, packageName, 0) // Disable
        }
    }

    fun addUsageMinute(appName: String) {
        viewModelScope.launch {
            val limits = appLimits.value
            val match = limits.find { it.appName == appName }
            if (match != null) {
                val newUsed = match.usedMinutes + 1
                repository.updateAppUsage(appName, newUsed)

                if (match.limitMinutes in 1..newUsed) {
                    repository.addLog(appName, "LIMIT_REACHED", "Screen time limit of ${match.limitMinutes}m exhausted for $appName", "BLOCKED")
                } else {
                    repository.addLog(appName, "APP_OPEN", "Accumulated screen use: ${newUsed}m / ${match.limitMinutes}m", "INFO")
                }
            }
        }
    }

    fun setPolicySetting(key: String, value: String) {
        viewModelScope.launch {
            repository.setPolicy(key, value)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    fun resetDailyTime() {
        viewModelScope.launch {
            repository.resetAllAppUsage()
        }
    }

    fun completeSetup(pin: String, schoolName: String, globalLimitMinutes: Int) {
        viewModelScope.launch {
            repository.setPolicy("SCHOOL_NAME", schoolName)
            if (pin.length == 4 && pin.all { it.isDigit() }) {
                repository.setPolicy("PIN", pin)
            }
            repository.setPolicy("GLOBAL_LIMIT_MINUTES", globalLimitMinutes.toString())
            
            // Set App Limits for existing apps if global limit > 0
            if (globalLimitMinutes > 0) {
                val apps = installedDeviceApps.value
                for (app in apps) {
                    repository.setAppLimit(app.appName, app.packageName, globalLimitMinutes)
                }
            }
            
            repository.setPolicy("SETUP_COMPLETED", "true")
            repository.addLog("SYSTEM", "ADMIN_SETTING", "Administrative profile manual onboard setup completed for School: $schoolName", "INFO")
        }
    }

    fun generateConfigQrText(): String {
        return try {
            val json = org.json.JSONObject()
            json.put("school_name", schoolName.value)
            
            val currentPin = policies.value.find { it.key == "PIN" }?.value ?: "1234"
            json.put("pin", currentPin)
            json.put("global_limit", globalLimitMinutes.value)
            
            val domainsJson = org.json.JSONArray()
            blockedUrls.value.forEach {
                domainsJson.put(it.domain)
            }
            json.put("blocked_domains", domainsJson)
            
            json.toString()
        } catch (e: Exception) {
            "{}"
        }
    }

    fun applySetupFromEncodedConfig(configText: String): Boolean {
        return try {
            val json = org.json.JSONObject(configText)
            val school = json.optString("school_name", "EduGuard School")
            val pin = json.optString("pin", "1234")
            val globalLimit = json.optInt("global_limit", 0)
            
            viewModelScope.launch {
                repository.setPolicy("SCHOOL_NAME", school)
                repository.setPolicy("PIN", pin)
                repository.setPolicy("GLOBAL_LIMIT_MINUTES", globalLimit.toString())
                
                // If there are blocked domains target them too
                val blockedArray = json.optJSONArray("blocked_domains")
                if (blockedArray != null) {
                    for (i in 0 until blockedArray.length()) {
                        val domain = blockedArray.optString(i)
                        if (domain.isNotEmpty()) {
                            repository.blockUrl(domain)
                        }
                    }
                }
                
                // Populate app limits for device application list
                if (globalLimit > 0) {
                    val apps = installedDeviceApps.value
                    for (app in apps) {
                        repository.setAppLimit(app.appName, app.packageName, globalLimit)
                    }
                }
                
                repository.setPolicy("SETUP_COMPLETED", "true")
                repository.addLog("SYSTEM", "ADMIN_SETTING", "Provisioned successfully from QR Code Configuration for School: $school", "INFO")
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun addLog(target: String, type: String, details: String, severity: String) {
        viewModelScope.launch {
            repository.addLog(target, type, details, severity)
        }
    }

    // Interactive Student Mode Simulation action
    fun testBrowserNavigation(url: String) {
        viewModelScope.launch {
            val clean = url.trim().lowercase()
            if (clean.isEmpty()) return@launch

            val isBlocked = repository.isUrlBlocked(clean)
            if (isBlocked) {
                repository.addLog(clean, "SITE_BLOCKED", "Access to web domain denied by administrator firewall policy", "BLOCKED")
                _simulatedStatusMessage.value = Pair("Access denied! This site has been blacklisted by your school administrator.", true)
            } else {
                repository.addLog(clean, "APP_OPEN", "DNS request allowed: $clean launched in secure school browser", "INFO")
                _simulatedStatusMessage.value = Pair("Successfully resolved secure server connection to: $clean", false)
            }
        }
    }

    fun hasUsagePermission(): Boolean {
        val context = getApplication<Application>()
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.noteOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = pm.queryIntentActivities(intent, 0)
                val list = resolveInfos.map { info ->
                    val name = info.loadLabel(pm).toString()
                    val pkg = info.activityInfo.packageName
                    DeviceApp(name, pkg)
                }.distinctBy { it.packageName }.sortedBy { it.appName }
                installedDeviceApps.value = list
            } catch (e: Exception) {
                // Ignore background errors
            }
        }
    }

    fun syncActualUsageStats() {
        viewModelScope.launch {
            if (!hasUsagePermission()) return@launch
            try {
                val context = getApplication<Application>()
                val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startTime = calendar.timeInMillis
                val endTime = System.currentTimeMillis()
                
                val stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startTime,
                    endTime
                ) ?: return@launch
                
                val currentLimits = appLimits.value
                val usageMap = stats.associate { it.packageName to it.totalTimeInForeground }

                for (limit in currentLimits) {
                    val foregroundTime = usageMap[limit.packageName] ?: 0L
                    if (foregroundTime > 0) {
                        val mins = (foregroundTime / 1000 / 60).toInt()
                        if (mins != limit.usedMinutes) {
                            repository.updateAppUsage(limit.appName, mins)
                            // Log real screen allowance crossing
                            if (limit.limitMinutes in 1..mins && limit.usedMinutes < limit.limitMinutes) {
                                repository.addLog(
                                    target = limit.appName,
                                    type = "LIMIT_REACHED",
                                    details = "Actual daily screen use for ${limit.appName} reached limit of ${limit.limitMinutes}m (Current Foreground is ${mins}m)",
                                    severity = "WARNING"
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore silent querying errors
            }
        }
    }

    private var lastCheckedEventTime = System.currentTimeMillis() - 30_000 // Last 30s as buffer on startup
    private var lastLoggedAppPackage = ""
    private var lastLoggedAppTime = 0L

    private fun startRealTimeTracking() {
        viewModelScope.launch {
            while (true) {
                if (hasUsagePermission()) {
                    syncActualUsageStats()
                    trackActivityEvents()
                }
                kotlinx.coroutines.delay(6000) // Poll every 6 seconds for highly responsive real tracking
            }
        }
    }

    private suspend fun trackActivityEvents() {
        try {
            val context = getApplication<Application>()
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val pm = context.packageManager

            val now = System.currentTimeMillis()
            val events = usageStatsManager.queryEvents(lastCheckedEventTime, now)
            val event = android.app.usage.UsageEvents.Event()

            var latestTimestamp = lastCheckedEventTime

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                    val pkg = event.packageName
                    val timestamp = event.timeStamp
                    if (pkg != context.packageName && timestamp > lastCheckedEventTime) {
                        if (timestamp > latestTimestamp) {
                            latestTimestamp = timestamp
                        }

                        // Avoid duplicate log spamming if same app is double tracked rapidly or within 5 seconds
                        if (pkg == lastLoggedAppPackage && (timestamp - lastLoggedAppTime) < 5000) {
                            continue
                        }

                        lastLoggedAppPackage = pkg
                        lastLoggedAppTime = timestamp

                        // Extract app friendly name
                        val appName = try {
                            val appInfo = pm.getApplicationInfo(pkg, 0)
                            pm.getApplicationLabel(appInfo).toString()
                        } catch (e: Exception) {
                            pkg
                        }

                        // Detect Block Status - Is package matching any blocked URLs or in custom limits?
                        val limits = appLimits.value
                        val limitMatch = limits.find { it.packageName == pkg }

                        if (limitMatch != null && limitMatch.isLimitExceeded) {
                            repository.addLog(
                                target = pkg,
                                type = "LIMIT_REACHED",
                                details = "Blocked attempt to open $appName: screen time limit reached (${limitMatch.limitMinutes}m limit, used ${limitMatch.usedMinutes}m)",
                                severity = "WARNING"
                            )
                        } else {
                            repository.addLog(
                                target = pkg,
                                type = "APP_OPEN",
                                details = "Opened app: $appName (Package: $pkg)",
                                severity = "INFO"
                            )
                        }
                    }
                }
            }
            lastCheckedEventTime = maxOf(lastCheckedEventTime, latestTimestamp + 1)
        } catch (e: Exception) {
            // Unhandled system events
        }
    }
}
