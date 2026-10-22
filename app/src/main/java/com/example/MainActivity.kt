package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.ForgeTab
import com.example.ui.KalamaForgeViewModel
import com.example.ui.components.KalamaSiliconDieHeroCard
import com.example.ui.components.KalamaTopTelemetryBar
import com.example.ui.components.KernelShellConsoleDrawer
import com.example.ui.screens.AvbSignerAndFlashScreen
import com.example.ui.screens.FailSafeRecoveryScreen
import com.example.ui.screens.OppFrequencyForgeScreen
import com.example.ui.screens.PartitionsBackupScreen
import com.example.ui.theme.KalamaCyan
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                KalamaForgeApp()
            }
        }
    }
}

private data class NavTabInfo(
    val tab: ForgeTab,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val NAV_TABS = listOf(
    NavTabInfo(
        tab = ForgeTab.PARTITIONS,
        label = "Partitions",
        selectedIcon = Icons.Filled.Memory,
        unselectedIcon = Icons.Outlined.Memory,
        testTag = "nav_tab_partitions"
    ),
    NavTabInfo(
        tab = ForgeTab.OPP_FORGE,
        label = "OPP Forge",
        selectedIcon = Icons.Filled.Speed,
        unselectedIcon = Icons.Outlined.Speed,
        testTag = "nav_tab_opp_forge"
    ),
    NavTabInfo(
        tab = ForgeTab.AVB_FLASH,
        label = "AVB & Flash",
        selectedIcon = Icons.Filled.FlashOn,
        unselectedIcon = Icons.Outlined.FlashOn,
        testTag = "nav_tab_avb_flash"
    ),
    NavTabInfo(
        tab = ForgeTab.FAIL_SAFE,
        label = "Fail-Safe",
        selectedIcon = Icons.Filled.HealthAndSafety,
        unselectedIcon = Icons.Outlined.HealthAndSafety,
        testTag = "nav_tab_failsafe"
    )
)

@Composable
fun KalamaForgeApp(
    viewModel: KalamaForgeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backups by viewModel.backups.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()

    // Mandatory BackHandler for custom state-switched navigation
    BackHandler(enabled = uiState.selectedTab != ForgeTab.PARTITIONS) {
        viewModel.selectTab(ForgeTab.PARTITIONS)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        val isExpandedScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = ObsidianBg,
            bottomBar = {
                if (!isExpandedScreen) {
                    NavigationBar(
                        containerColor = ObsidianSurface,
                        tonalElevation = 8.dp
                    ) {
                        NAV_TABS.forEach { item ->
                            val selected = uiState.selectedTab == item.tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(item.tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF002229),
                                    selectedTextColor = KalamaCyan,
                                    indicatorColor = KalamaCyan,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpandedScreen) {
                    NavigationRail(
                        containerColor = ObsidianSurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        NAV_TABS.forEach { item ->
                            val selected = uiState.selectedTab == item.tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(item.tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    KalamaTopTelemetryBar(
                        environment = uiState.environment,
                        statusMessage = uiState.statusBannerMessage,
                        isError = uiState.isStatusError,
                        onToggleSandboxMode = { viewModel.toggleSandboxMode() }
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 720.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            KalamaSiliconDieHeroCard(
                                oppPoints = uiState.oppPoints,
                                globalVoltageOffsetMv = uiState.globalVoltageOffsetMv,
                                selectedCluster = uiState.selectedCluster,
                                onSelectCluster = { cluster ->
                                    viewModel.selectCluster(cluster)
                                }
                            )
                        }

                        item {
                            KernelShellConsoleDrawer(lines = uiState.terminalOutputLines)
                        }

                        item {
                            when (uiState.selectedTab) {
                                ForgeTab.PARTITIONS -> {
                                    PartitionsBackupScreen(
                                        environment = uiState.environment,
                                        extractedKernel = uiState.extractedKernel,
                                        backups = backups,
                                        isBusy = uiState.isBusy,
                                        onBackupKernel = { label -> viewModel.createNewKernelBackup(label) },
                                        onExtractBackup = { backup -> viewModel.extractKernelFromBackup(backup) },
                                        onReflashBackup = { backup -> viewModel.reflashBackupAndVerify(backup) },
                                        onDeleteBackup = { backup -> viewModel.deleteSnapshotBackup(backup) },
                                        onNavigateToOppForge = { viewModel.selectTab(ForgeTab.OPP_FORGE) }
                                    )
                                }

                                ForgeTab.OPP_FORGE -> {
                                    OppFrequencyForgeScreen(
                                        selectedCluster = uiState.selectedCluster,
                                        oppPoints = uiState.oppPoints,
                                        globalVoltageOffsetMv = uiState.globalVoltageOffsetMv,
                                        activePresetName = uiState.activePresetName,
                                        onSelectCluster = { viewModel.selectCluster(it) },
                                        onToggleOppPoint = { viewModel.toggleOppFrequencyPoint(it) },
                                        onVoltageOffsetChange = { viewModel.setGlobalVoltageOffset(it) },
                                        onApplyPreset = { viewModel.applyOppPreset(it) },
                                        onAddCustomOppPoint = { cluster, mhz, mv ->
                                            viewModel.addCustomOppPoint(cluster, mhz, mv)
                                        },
                                        onProceedToSignAndFlash = {
                                            viewModel.compileAndSignPatchedKernel()
                                            viewModel.selectTab(ForgeTab.AVB_FLASH)
                                        }
                                    )
                                }

                                ForgeTab.AVB_FLASH -> {
                                    AvbSignerAndFlashScreen(
                                        environment = uiState.environment,
                                        selectedSigningMode = uiState.selectedSigningMode,
                                        compiledArtifact = uiState.compiledArtifact,
                                        lastVerificationReport = uiState.lastVerificationReport,
                                        simulateVerifyFault = uiState.simulateVerifyFaultNextFlash,
                                        auditLogs = auditLogs,
                                        isBusy = uiState.isBusy,
                                        onSelectSigningMode = { viewModel.setAvbSigningMode(it) },
                                        onCompileAndSign = { viewModel.compileAndSignPatchedKernel() },
                                        onToggleSimulateFault = { viewModel.setSimulateVerifyFault(it) },
                                        onFlashAndVerifyPatched = { viewModel.flashPatchedKernelAndVerify() },
                                        onReflashOriginalStock = { viewModel.reflashOriginalStockKernel() }
                                    )
                                }

                                ForgeTab.FAIL_SAFE -> {
                                    FailSafeRecoveryScreen(
                                        failSafeConfig = uiState.failSafeConfig,
                                        isBusy = uiState.isBusy,
                                        onUpdateConfig = { w, b, r, p, t, v ->
                                            viewModel.updateFailSafeSettings(w, b, r, p, t, v)
                                        },
                                        onArmWatchdogAndBuildZip = {
                                            viewModel.deployFailSafeWatchdogAndRecoveryZip()
                                        },
                                        onImmediateRestoreStock = {
                                            viewModel.reflashOriginalStockKernel()
                                        },
                                        onTriggerReboot = { mode ->
                                            viewModel.triggerHardwareRebootAction(mode)
                                        }
                                    )
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}
