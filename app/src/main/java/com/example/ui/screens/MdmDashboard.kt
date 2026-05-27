package com.example.ui.screens

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ActivityLog
import com.example.data.AppLimit
import com.example.data.BlockedUrl
import com.example.receiver.EduGuardDeviceAdminReceiver
import com.example.ui.viewmodel.MdmViewModel
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import android.widget.Toast
import com.example.util.QrCodeUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MdmDashboard(
    viewModel: MdmViewModel,
    modifier: Modifier = Modifier,
    onRequestActivateVpn: () -> Unit = {},
    onRequestScanConfigQr: () -> Unit = {}
) {
    val context = LocalContext.current
    val isAdminAuth by viewModel.isAdminAuthenticated.collectAsStateWithLifecycle()
    val pinError by viewModel.pinError.collectAsStateWithLifecycle()
    val isSetupCompleted by viewModel.isSetupCompleted.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf("ADMIN_PORTAL") } // "STUDENT_PORTAL", "ADMIN_PORTAL"

    if (!isSetupCompleted) {
        EduGuardSetupOnboardingWizard(
            viewModel = viewModel,
            onRequestScanConfigQr = onRequestScanConfigQr
        )
    } else if (!isAdminAuth) {
        // Gated App Launcher Lock for admins
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.systemBars),
            contentAlignment = Alignment.Center
        ) {
            AdminPasscodeAuthView(
                errorText = pinError,
                onVerify = { pin -> viewModel.verifyPasscode(pin) }
            )
        }
    } else {
        // Material 3 Edge-to-Edge Custom Scaffold with Slate-Midnight aesthetic
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AdminPanelSettings,
                                        contentDescription = "Shield Logo",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            val currentSchoolName by viewModel.schoolName.collectAsStateWithLifecycle()
                            Column {
                                Text(
                                    currentSchoolName,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "School Device Management Console",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    },
                    actions = {
                        Button(
                            onClick = { viewModel.logoutAdmin() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "Exit Admin", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Lock Admin", style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    NavigationBarItem(
                        selected = activeTab == "ADMIN_PORTAL",
                        onClick = { activeTab = "ADMIN_PORTAL" },
                        icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin Area") },
                        label = { Text("Management") }
                    )
                    NavigationBarItem(
                        selected = activeTab == "STUDENT_PORTAL",
                        onClick = { activeTab = "STUDENT_PORTAL" },
                        icon = { Icon(Icons.Default.School, contentDescription = "Student Workspace") },
                        label = { Text("School Mode") }
                    )
                }
            },
            modifier = modifier
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (activeTab) {
                    "STUDENT_PORTAL" -> {
                        StudentWorkspaceView(viewModel = viewModel)
                    }
                    "ADMIN_PORTAL" -> {
                        AdminControlPanelView(viewModel = viewModel, onRequestActivateVpn = onRequestActivateVpn)
                    }
                }
            }
        }
    }
}

// ======================== STUDENT COMPOSABLES ========================

@Composable
fun StudentWorkspaceView(viewModel: MdmViewModel) {
    val simulatedUrl by viewModel.simulatedUrlInput.collectAsStateWithLifecycle()
    val simulatedResult by viewModel.simulatedStatusMessage.collectAsStateWithLifecycle()
    val appLimitsList by viewModel.appLimits.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val dpm = remember { context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    val adminComponent = remember { ComponentName(context, EduGuardDeviceAdminReceiver::class.java) }
    val isDeviceAdminActive = remember { dpm.isAdminActive(adminComponent) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Enrolled Banner Status
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isDeviceAdminActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = if (isDeviceAdminActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isDeviceAdminActive) Icons.Default.Shield else Icons.Default.PrivacyTip,
                            contentDescription = "Active Status",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    val brandingSchoolName by viewModel.schoolName.collectAsStateWithLifecycle()
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isDeviceAdminActive) "$brandingSchoolName Rules Active" else "Education Supervision Waiting",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (isDeviceAdminActive) "This device is registered to and monitored under $brandingSchoolName policies." else "Branding policies cannot be fully enforced without local device admin setup.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Student Web Workspace Simulator
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.BrowserUpdated, contentDescription = "Browser Link", tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Interactive Learning Browser",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Simulate accessing web sites (e.g. 'wikipedia.org', 'tiktok.com') to test live DNS blocks and administrative activity tracking.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = simulatedUrl,
                        onValueChange = { viewModel.setSimulatedUrl(it) },
                        placeholder = { Text("Enter a domain... e.g., tiktok.com") },
                        leadingIcon = { Icon(Icons.Default.Language, contentDescription = "Web Domain Icon") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_browser_input"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.testBrowserNavigation(simulatedUrl) },
                        modifier = Modifier
                            .align(Alignment.End)
                            .testTag("student_visit_button"),
                        enabled = simulatedUrl.isNotBlank()
                    ) {
                        Icon(Icons.Default.Launch, contentDescription = "Go", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Connect Server")
                    }

                    // Simulated Domain Resolver feedback with animations
                    simulatedResult?.let { (message, isBlocked) ->
                        Spacer(Modifier.height(16.dp))
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + expandVertically()
                        ) {
                            Surface(
                                color = if (isBlocked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isBlocked) Icons.Default.Block else Icons.Default.CheckCircle,
                                        contentDescription = "Domain filter outcome",
                                        tint = if (isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isBlocked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Managed Application Screen Time Limits
        item {
            Text(
                text = "Screen Time App Allowances",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        items(appLimitsList.filter { it.limitMinutes > 0 }) { appLimit ->
            AppLimitSimulatorCard(
                appLimit = appLimit,
                onAddUsageMinute = { viewModel.addUsageMinute(appLimit.appName) }
            )
        }

        if (appLimitsList.none { it.limitMinutes > 0 }) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = "No active screen limits assigned. Access to all registered application pools is unrestricted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AppLimitSimulatorCard(
    appLimit: AppLimit,
    onAddUsageMinute: () -> Unit
) {
    val progress = if (appLimit.limitMinutes > 0) {
        (appLimit.usedMinutes.toFloat() / appLimit.limitMinutes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val isExceeded = appLimit.isLimitExceeded

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isExceeded) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = appLimit.appName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = appLimit.packageName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    )
                }

                Surface(
                    color = if (isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${appLimit.usedMinutes}m / ${appLimit.limitMinutes}m",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isExceeded) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = if (isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isExceeded) "⚠️ EXCEEDED - Access Blocked" else "Within education group rules",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp
                    )
                )

                IconButton(
                    onClick = onAddUsageMinute,
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add simulator usage",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ======================== ADMIN AUTHORIZATION PIN DIALOG ========================

@Composable
fun AdminPasscodeAuthView(
    errorText: String?,
    onVerify: (String) -> Unit
) {
    var pinValue by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = "Shield Guard Lock",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "School Admin Authentication",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Please enter the administrative console security PIN to unlock student usage reporting, internet blocks, and screen time schedulers.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = pinValue,
            onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) pinValue = it },
            label = { Text("4-Digit Admin PIN") },
            placeholder = { Text("••••") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier
                .width(180.dp)
                .testTag("admin_pin_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        Spacer(Modifier.height(10.dp))
        Text(
            "Hint: Default preset is 1234",
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
        )

        errorText?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                onVerify(pinValue)
                pinValue = ""
            },
            enabled = pinValue.length == 4,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("admin_unlock_button")
        ) {
            Icon(Icons.Default.VerifiedUser, contentDescription = "Authorise")
            Spacer(Modifier.width(8.dp))
            Text("Verify Credentials")
        }
    }
}

// ======================== ADMIN CONTROL PANEL TABS ========================

@Composable
fun AdminControlPanelView(
    viewModel: MdmViewModel,
    onRequestActivateVpn: () -> Unit = {}
) {
    var adminSubTab by remember { mutableStateOf("LOGS") } // "LOGS", "FIREWALL", "LIMITS", "SYSTEM"
    val logsList by viewModel.logs.collectAsStateWithLifecycle()

    // Calculate dynamic alerts and active counts
    val policyAlertsCount = logsList.count { it.severity == "BLOCKED" || it.severity == "WARNING" }
    val alertsStr = if (policyAlertsCount < 10) "0$policyAlertsCount" else "$policyAlertsCount"

    Column(modifier = Modifier.fillMaxSize()) {
        // Quick Stats Cards (Directly matching Professional Polish HTML section layout)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Stat Card 1: Active Devices
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(28.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = "Active Devices Count",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "24/30",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    )
                    Text(
                        text = "Active Devices",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            // Stat Card 2: Policy Alerts
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(28.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ReportProblem,
                        contentDescription = "Policy Alerts Count",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = alertsStr,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    )
                    Text(
                        text = "Policy Alerts",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }

        ScrollableTabRow(
            selectedTabIndex = when (adminSubTab) {
                "LOGS" -> 0
                "FIREWALL" -> 1
                "LIMITS" -> 2
                "SYSTEM" -> 3
                else -> 0
            },
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        ) {
            Tab(selected = adminSubTab == "LOGS", onClick = { adminSubTab = "LOGS" }) {
                Box(Modifier.padding(vertical = 14.dp, horizontal = 4.dp)) { Text("Activity Log") }
            }
            Tab(selected = adminSubTab == "FIREWALL", onClick = { adminSubTab = "FIREWALL" }) {
                Box(Modifier.padding(vertical = 14.dp, horizontal = 4.dp)) { Text("Web Filter") }
            }
            Tab(selected = adminSubTab == "LIMITS", onClick = { adminSubTab = "LIMITS" }) {
                Box(Modifier.padding(vertical = 14.dp, horizontal = 4.dp)) { Text("Screen Limits") }
            }
            Tab(selected = adminSubTab == "SYSTEM", onClick = { adminSubTab = "SYSTEM" }) {
                Box(Modifier.padding(vertical = 14.dp, horizontal = 4.dp)) { Text("Device Rules") }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (adminSubTab) {
                "LOGS" -> AdminLogsTabView(viewModel = viewModel)
                "FIREWALL" -> AdminFirewallTabView(viewModel = viewModel, onRequestActivateVpn = onRequestActivateVpn)
                "LIMITS" -> AdminLimitsTabView(viewModel = viewModel)
                "SYSTEM" -> AdminSystemTabView(viewModel = viewModel)
            }
        }
    }
}

// ======================== SUB TOBS IMPLEMENTATIONS ========================

@Composable
fun AdminLogsTabView(viewModel: MdmViewModel) {
    val logsList by viewModel.filteredLogs.collectAsStateWithLifecycle()
    val activeFilter by viewModel.logFilter.collectAsStateWithLifecycle()
    val dateFormatter = remember { SimpleDateFormat("hh:mm:ss a", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        if (viewModel.hasUsagePermission()) {
            viewModel.syncActualUsageStats()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Filter Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("ALL", "BLOCKED", "SYSTEM", "INFO").forEach { category ->
                val isSelected = activeFilter == category
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .clickable { viewModel.updateLogFilter(category) }
                        .weight(1f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Logged System Activity (${logsList.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            TextButton(
                onClick = { viewModel.clearHistory() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = "Trash logs", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Clear Live Status")
            }
        }

        Spacer(Modifier.height(8.dp))

        if (logsList.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.List, contentDescription = "Empty", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No administrative logs registered.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logsList) { log ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = when (log.severity) {
                                "BLOCKED" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                                "WARNING" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
                                else -> MaterialTheme.colorScheme.surface
                            }
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        imageVector = when (log.activityType) {
                                            "SITE_BLOCKED" -> Icons.Default.Block
                                            "SYSTEM_ALERT" -> Icons.Default.Warning
                                            "LIMIT_REACHED" -> Icons.Default.HourglassEmpty
                                            "ADMIN_SETTING" -> Icons.Default.Settings
                                            else -> Icons.Default.Visibility
                                        },
                                        contentDescription = "Event category indicator",
                                        tint = when (log.severity) {
                                            "BLOCKED" -> MaterialTheme.colorScheme.error
                                            "WARNING" -> MaterialTheme.colorScheme.tertiary
                                            else -> MaterialTheme.colorScheme.primary
                                        },
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = log.target,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Text(
                                    text = dateFormatter.format(Date(log.timestamp)),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        fontSize = 11.sp
                                    )
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = log.details,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminFirewallTabView(
    viewModel: MdmViewModel,
    onRequestActivateVpn: () -> Unit = {}
) {
    val blockedList by viewModel.blockedUrls.collectAsStateWithLifecycle()
    val isVpnActive by viewModel.isVpnActive.collectAsStateWithLifecycle()
    var urlInput by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "School Content Filtering Registry",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            "Input domains (e.g. reddit.com) to restrict access across student mobile workspaces instantly.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(14.dp))

        // Live VPN Connection Status Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isVpnActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
            ),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, if (isVpnActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = if (isVpnActive) Icons.Default.Language else Icons.Default.Lock,
                            contentDescription = "VPN Lock",
                            tint = if (isVpnActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = if (isVpnActive) "Real-Time VPN Firewall: RUNNING" else "Real-Time VPN Firewall: OFFLINE",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isVpnActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                            )
                        )
                    }
                    
                    Button(
                        onClick = {
                            if (isVpnActive) {
                                viewModel.stopVpnService()
                            } else {
                                onRequestActivateVpn()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isVpnActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            contentColor = if (isVpnActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (isVpnActive) "Deactivate" else "Activate VPN")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (isVpnActive) {
                        "Your school firewall filter is running at system level. Devices lookups are intercepted to restrict access of blocked sites and trace device-wide DNS log history."
                    } else {
                        "The virtual DNS loopback block engine is offline. Wake up the VPN service to start active website blocking and record foreground browsing activities."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isVpnActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                placeholder = { Text("domain.com") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("firewall_add_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            Button(
                onClick = {
                    if (urlInput.isNotBlank()) {
                        viewModel.blockDomain(urlInput)
                        urlInput = ""
                    }
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .height(56.dp)
                    .testTag("firewall_add_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add block")
                Spacer(Modifier.width(4.dp))
                Text("Block")
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "Blocked Domains list (${blockedList.size})",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        )

        Spacer(Modifier.height(8.dp))

        if (blockedList.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No blacklisted sites configured.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(blockedList) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Bookmark, contentDescription = "Active Filter", tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    item.domain,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }

                            IconButton(onClick = { viewModel.unblockDomain(item.domain) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove block Rule", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminLimitsTabView(viewModel: MdmViewModel) {
    val context = LocalContext.current
    val limitsList by viewModel.appLimits.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedDeviceApps.collectAsStateWithLifecycle()

    var activeAppFormName by remember { mutableStateOf("") }
    var activeAppFormPackage by remember { mutableStateOf("") }
    var activeAppMinutes by remember { mutableStateOf("15") }
    var isAppDropdownExpanded by remember { mutableStateOf(false) }

    val hasUsagePermission = remember { viewModel.hasUsagePermission() }

    LaunchedEffect(Unit) {
        viewModel.loadInstalledApps()
        if (hasUsagePermission) {
            viewModel.syncActualUsageStats()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Screen Allowance Policy Manager",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )

        Spacer(Modifier.height(8.dp))

        // Live stats permission and sync status
        if (!hasUsagePermission) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "⚠️ Real Device Tracking Permission Required",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "EduGuard needs 'Usage Stats Access' permission in Android Settings to fetch actual daily usage minutes for the tracked applications.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Fallback
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Grant Usage Access Permission")
                    }
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "✓ Live Screen Time Connected",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        )
                        Text(
                            "Querying real-time foreground application states.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Button(
                        onClick = { viewModel.syncActualUsageStats() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync Now", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Sync")
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Lock Screen Allowance for Package", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(10.dp))

                // Select Actual App Selector Dropdown
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    OutlinedButton(
                        onClick = {
                            viewModel.loadInstalledApps()
                            isAppDropdownExpanded = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Apps, contentDescription = "Apps List", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (activeAppFormName.isEmpty()) "Select Actual App from Device" else "Targeting: $activeAppFormName")
                    }

                    DropdownMenu(
                        expanded = isAppDropdownExpanded,
                        onDismissRequest = { isAppDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        if (installedApps.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No launcher apps found on device.") },
                                onClick = { isAppDropdownExpanded = false }
                            )
                        } else {
                            installedApps.forEach { app ->
                                DropdownMenuItem(
                                    text = { Text("${app.appName} (${app.packageName})") },
                                    onClick = {
                                        activeAppFormName = app.appName
                                        activeAppFormPackage = app.packageName
                                        isAppDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = activeAppFormName,
                        onValueChange = { activeAppFormName = it },
                        placeholder = { Text("App Name (e.g. Roblox)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )
                    OutlinedTextField(
                        value = activeAppFormPackage,
                        onValueChange = { activeAppFormPackage = it },
                        placeholder = { Text("Package (e.g. com.roblox)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = activeAppMinutes,
                        onValueChange = { activeAppMinutes = it },
                        placeholder = { Text("Limit Mins") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(100.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )

                    Button(
                        onClick = {
                            val mins = activeAppMinutes.toIntOrNull() ?: 0
                            if (activeAppFormName.isNotBlank() && activeAppFormPackage.isNotBlank()) {
                                viewModel.addAppLimit(activeAppFormName, activeAppFormPackage, mins)
                                activeAppFormName = ""
                                activeAppFormPackage = ""
                                activeAppMinutes = "15"
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Limit")
                        Spacer(Modifier.width(4.dp))
                        Text("Establish Limit")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Reset All Screen times option
        Button(
            onClick = { viewModel.resetDailyTime() },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Reset usage counters")
            Spacer(Modifier.width(6.dp))
            Text("Reset Student Daily Usage App Counters")
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(limitsList) { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(item.appName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Text(
                                if (item.limitMinutes > 0) {
                                    "Used today: ${item.usedMinutes}m / Limit: ${item.limitMinutes}m"
                                } else {
                                    "Used today: ${item.usedMinutes}m [No limit set]"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (item.isLimitExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(onClick = { viewModel.deleteAppLimit(item.appName, item.packageName) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete app usage limit rule", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminSystemTabView(viewModel: MdmViewModel) {
    val context = LocalContext.current
    val dpm = remember { context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    val adminComponent = remember { ComponentName(context, EduGuardDeviceAdminReceiver::class.java) }
    var isDeviceAdminActive by remember { mutableStateOf(dpm.isAdminActive(adminComponent)) }

    var cameraDisabled by remember { mutableStateOf(false) }
    var rawPasscodeText by remember { mutableStateOf("") }
    var isPasscodeChangeTriggered by remember { mutableStateOf(false) }

    val activeSchoolName by viewModel.schoolName.collectAsStateWithLifecycle()
    var rawSchoolText by remember(activeSchoolName) { mutableStateOf(activeSchoolName) }
    var isSchoolChangeTriggered by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Device Policy Manager Control",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )

        // Device Admin Activation Action Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    "Device Admin Authorization Block",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "School rules are only enforced on this hardware when EduGuard is active as Device Admin. Click below to toggle hardware administrator lock.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = {
                        if (!isDeviceAdminActive) {
                            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Allow school supervisors to lock apps, check live navigation logs, and prevent student distraction.")
                            }
                            context.startActivity(intent)
                        } else {
                            try {
                                dpm.removeActiveAdmin(adminComponent)
                                isDeviceAdminActive = false
                            } catch (e: Exception) {
                                // Silent fallback
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDeviceAdminActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (isDeviceAdminActive) Icons.Default.RemoveModerator else Icons.Default.AddModerator,
                        contentDescription = "Toggle hardware mdm admin"
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isDeviceAdminActive) "Remove Active School Admin" else "Activate School Admin Link")
                }
            }
        }

        // Action Command list
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Enforce Immediate Commands",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )

                // 1. Force Lock
                Button(
                    onClick = {
                        if (isDeviceAdminActive) {
                            try {
                                dpm.lockNow()
                                viewModel.addLog("SYSTEM", "ADMIN_SETTING", "Command issued: Instant tablet lock initiated by admin", "WARNING")
                            } catch (e: Exception) {
                                // Fallback simulation logged
                                viewModel.addLog("SYSTEM_SIMULATOR", "ADMIN_SETTING", "Simulated table lockdown engaged (Actual requires Device Admin setting)", "INFO")
                            }
                        } else {
                            viewModel.addLog("SYSTEM_SIMULATOR", "ADMIN_SETTING", "Simulated table lockdown engaged (Actual requires Device Admin setting)", "INFO")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = "Lock now")
                    Spacer(Modifier.width(6.dp))
                    Text("Trigger Instant Lock Screen Lock")
                }

                // 2. Hardware camera block
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Hardware Camera Block", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Text("Disable lens during class hours", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Switch(
                            checked = cameraDisabled,
                            onCheckedChange = { blockCheck ->
                                cameraDisabled = blockCheck
                                if (isDeviceAdminActive) {
                                    try {
                                        dpm.setCameraDisabled(adminComponent, blockCheck)
                                    } catch (e: Exception) {
                                        // Silent context log
                                    }
                                }
                                viewModel.addLog("CAMERA_HARDWARE", "ADMIN_SETTING", "Enforce camera policy: disable = $blockCheck", "WARNING")
                            }
                        )
                    }
                }
            }
        }

        // School Branding Profile editor card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("School Branding Profile Settings", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = rawSchoolText,
                        onValueChange = { rawSchoolText = it },
                        placeholder = { Text("School Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    Button(
                        onClick = {
                            if (rawSchoolText.isNotBlank()) {
                                viewModel.setPolicySetting("SCHOOL_NAME", rawSchoolText.trim())
                                isSchoolChangeTriggered = true
                            }
                        },
                        enabled = rawSchoolText.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }

                if (isSchoolChangeTriggered) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "School name has been updated successfully!",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        // Administrative Access passcode changer
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Update Management Portal Passcode", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = rawPasscodeText,
                        onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) rawPasscodeText = it },
                        placeholder = { Text("4-Digit PIN") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    Button(
                        onClick = {
                            if (rawPasscodeText.length == 4) {
                                viewModel.changeAdminPasscode(rawPasscodeText)
                                rawPasscodeText = ""
                                isPasscodeChangeTriggered = true
                            }
                        },
                        enabled = rawPasscodeText.length == 4
                    ) {
                        Text("Save")
                    }
                }

                if (isPasscodeChangeTriggered) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Passcode changed successfully! Write it down safely.",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        // Deploy QR / Multi-Sync Card
        val configText = remember { viewModel.generateConfigQrText() }
        var showQrCode by remember { mutableStateOf(false) }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = "Config QR Logo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Enroll New Device (Multi-Sync QR)",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "You can instantly clone this tablet configuration (School Name, Passcode, App Limits, and Blacklisted Domains) to newly added teacher/student devices. Open EduGuard first launch setup on target devices and scan this QR code.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = { showQrCode = !showQrCode },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (showQrCode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = ""
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (showQrCode) "Hide Deployment QR Code" else "Generate Deployment QR Code")
                }

                if (showQrCode) {
                    Spacer(Modifier.height(16.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color.Gray),
                            modifier = Modifier
                                .size(200.dp)
                                .padding(12.dp)
                        ) {
                            QrCodeDisplay(
                                text = configText,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        // Share text option
                        Button(
                            onClick = {
                                try {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("EduGuard Config", configText)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Config copied to clipboard!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    // Silent
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onPrimaryContainer),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy Config Text String")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QrCodeDisplay(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { QrCodeUtil.generateQrMatrix(text) }

    if (matrix != null) {
        val rows = matrix.size
        val cols = matrix[0].size

        Canvas(modifier = modifier) {
            val cellWidth = size.width / cols
            val cellHeight = size.height / rows

            for (y in 0 until rows) {
                for (x in 0 until cols) {
                    if (matrix[y][x]) {
                        drawRect(
                            color = Color.Black,
                            topLeft = androidx.compose.ui.geometry.Offset(x * cellWidth, y * cellHeight),
                            size = androidx.compose.ui.geometry.Size(cellWidth + 1f, cellHeight + 1f)
                        )
                    } else {
                        drawRect(
                            color = Color.White,
                            topLeft = androidx.compose.ui.geometry.Offset(x * cellWidth, y * cellHeight),
                            size = androidx.compose.ui.geometry.Size(cellWidth + 1f, cellHeight + 1f)
                        )
                    }
                }
            }
        }
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Encoding failed. Config is invalid.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EduGuardSetupOnboardingWizard(
    viewModel: MdmViewModel,
    onRequestScanConfigQr: () -> Unit
) {
    var schoolInput by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }
    var globalLimitInput by remember { mutableStateOf("60") }
    var pastedConfigInput by remember { mutableStateOf("") }
    var pasteError by remember { mutableStateOf<String?>(null) }
    var isManualSetupMode by remember { mutableStateOf(true) }

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Identity Header
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = "Shield Logo",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Welcome to EduGuard MDM",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            ),
            textAlign = TextAlign.Center
        )

        Text(
            text = "School Tablet Provisioning Console",
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            ),
            modifier = Modifier.padding(top = 4.dp),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        // Mode switch chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(bottom = 20.dp)
        ) {
            FilterChip(
                selected = isManualSetupMode,
                onClick = { isManualSetupMode = true },
                label = { Text("Manual Custom Onboarding") },
                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = "", modifier = Modifier.size(16.dp)) }
            )
            FilterChip(
                selected = !isManualSetupMode,
                onClick = { isManualSetupMode = false },
                label = { Text("Sync from QR Code") },
                leadingIcon = { Icon(Icons.Default.QrCodeScanner, contentDescription = "", modifier = Modifier.size(16.dp)) }
            )
        }

        if (isManualSetupMode) {
            // Manual customized setup fields
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Customize Local Device Settings",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    OutlinedTextField(
                        value = schoolInput,
                        onValueChange = { schoolInput = it },
                        label = { Text("School / Provider Name") },
                        placeholder = { Text("e.g. Greenwood Academy") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.School, contentDescription = "") }
                    )

                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) pinInput = it },
                        label = { Text("Admin Passcode PIN") },
                        placeholder = { Text("4 Digit Code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "") }
                    )

                    OutlinedTextField(
                        value = globalLimitInput,
                        onValueChange = { if (it.all { char -> char.isDigit() }) globalLimitInput = it },
                        label = { Text("Global Screen Time Limit (Minutes)") },
                        placeholder = { Text("0 to disable, or limit in mins") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.HourglassTop, contentDescription = "") },
                        supportingText = { Text("Applied automatically to all target apps as default screen limit.") }
                    )

                    // presets limit chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("30", "60", "120").forEach { preset ->
                            SuggestionChip(
                                onClick = { globalLimitInput = preset },
                                label = { Text("$preset min Limit") }
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Button(
                        onClick = {
                            if (schoolInput.trim().isEmpty()) {
                                Toast.makeText(context, "Please enter a valid School Name.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (pinInput.length != 4) {
                                Toast.makeText(context, "PIN Passcode must be exactly 4 digits.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val limit = globalLimitInput.toIntOrNull() ?: 0
                            viewModel.completeSetup(pinInput, schoolInput.trim(), limit)
                            Toast.makeText(context, "Provisioning completed!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Create Configuration profile")
                    }
                }
            }
        } else {
            // QR Configuration Sync screen
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Automated Synchronization",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Point this camera to scan the setup QR code displayed on the master console, or paste the configuration text directly below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { onRequestScanConfigQr() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Camera Scan")
                        Spacer(Modifier.width(8.dp))
                        Text("Launch QR Code Scanner")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text("OR ENTER CONFIG DETAILS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }

                    OutlinedTextField(
                        value = pastedConfigInput,
                        onValueChange = {
                            pastedConfigInput = it
                            pasteError = null
                        },
                        label = { Text("Paste configuration string") },
                        placeholder = { Text("Paste JSON code starting with {") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        supportingText = {
                            if (pasteError != null) {
                                Text(pasteError!!, color = MaterialTheme.colorScheme.error)
                            } else {
                                Text("Useful for emulators or devices without a functional camera.")
                            }
                        }
                    )

                    Button(
                        onClick = {
                            if (pastedConfigInput.trim().isEmpty()) {
                                pasteError = "Configuration code cannot be empty."
                                return@Button
                            }
                            val applied = viewModel.applySetupFromEncodedConfig(pastedConfigInput.trim())
                            if (applied) {
                                Toast.makeText(context, "System provisioned successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                pasteError = "Invalid profile configuration code. Verify original copy."
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Apply Config Code")
                    }
                }
            }
        }
    }
}
