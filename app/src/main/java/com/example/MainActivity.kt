package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.KalamaForgeViewModel
import com.example.ui.SimpleTab
import com.example.ui.screens.SimpleBackupExportFlashScreen
import com.example.ui.screens.SimpleFrequenciesScreen
import com.example.ui.theme.KalamaCrimson
import com.example.ui.theme.KalamaCyan
import com.example.ui.theme.KalamaEmerald
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.example.ui.theme.KalamaAmber

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

private data class SimpleNavTabInfo(
    val tab: SimpleTab,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val SIMPLE_TABS = listOf(
    SimpleNavTabInfo(
        tab = SimpleTab.FREQUENCIES,
        label = "1. Frequencies & Voltages",
        selectedIcon = Icons.Filled.Speed,
        unselectedIcon = Icons.Outlined.Speed,
        testTag = "nav_tab_frequencies"
    ),
    SimpleNavTabInfo(
        tab = SimpleTab.BACKUP_EXPORT_FLASH,
        label = "2. Backup, Export & Flash",
        selectedIcon = Icons.Filled.FlashOn,
        unselectedIcon = Icons.Outlined.FlashOn,
        testTag = "nav_tab_export_flash"
    )
)

@Composable
fun KalamaForgeApp(
    viewModel: KalamaForgeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backups by viewModel.backups.collectAsStateWithLifecycle()

    BackHandler(enabled = uiState.selectedTab != SimpleTab.FREQUENCIES) {
        viewModel.selectTab(SimpleTab.FREQUENCIES)
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
                        SIMPLE_TABS.forEach { item ->
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
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
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
                        SIMPLE_TABS.forEach { item ->
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
                    SimpleTopHeader(
                        deviceName = uiState.environment?.detectedDeviceCode ?: "Samsung Snapdragon 8 Gen 2",
                        isCheckingRoot = uiState.isCheckingRoot,
                        hasRootAccess = uiState.environment?.hasRootAccess == true,
                        statusMessage = uiState.statusMessage,
                        isError = uiState.isError,
                        onRetryRootCheck = { viewModel.retryStartupRootCheck() },
                        onResetAllStock = { viewModel.resetAllClustersToStock() }
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 700.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            StartupRootCheckCard(
                                isCheckingRoot = uiState.isCheckingRoot,
                                hasRootAccess = uiState.environment?.hasRootAccess == true,
                                magiskbootPath = uiState.environment?.magiskbootPath,
                                onRetryRootCheck = { viewModel.retryStartupRootCheck() }
                            )
                        }

                        item {
                            when (uiState.selectedTab) {
                                SimpleTab.FREQUENCIES -> {
                                    SimpleFrequenciesScreen(
                                        selectedCluster = uiState.selectedCluster,
                                        oppPoints = uiState.oppPoints,
                                        onSelectCluster = { viewModel.selectCluster(it) },
                                        onAdjustVoltage = { id, delta -> viewModel.adjustPointVoltage(id, delta) },
                                        onAdjustFrequency = { id, delta -> viewModel.adjustPointFrequency(id, delta) },
                                        onShiftClusterVoltages = { cluster, delta ->
                                            viewModel.shiftClusterVoltages(cluster, delta)
                                        },
                                        onResetPointToStock = { id -> viewModel.resetPointToStock(id) },
                                        onResetClusterToStock = { cluster -> viewModel.resetClusterToStock(cluster) },
                                        onAddCustomStep = { cluster, mhz, mv ->
                                            viewModel.addCustomFrequencyStep(cluster, mhz, mv)
                                        },
                                        onRemoveCustomStep = { id -> viewModel.removeCustomStep(id) },
                                        onToggleStepEnabled = { id -> viewModel.toggleStepEnabled(id) },
                                        onGoToExportAndFlash = {
                                            viewModel.selectTab(SimpleTab.BACKUP_EXPORT_FLASH)
                                        }
                                    )
                                }

                                SimpleTab.BACKUP_EXPORT_FLASH -> {
                                    SimpleBackupExportFlashScreen(
                                        oppPoints = uiState.oppPoints,
                                        backups = backups,
                                        lastExportedPackage = uiState.lastExportedPackage,
                                        lastVerificationReport = uiState.lastVerificationReport,
                                        isBusy = uiState.isBusy,
                                        onExportModified = { format -> viewModel.exportModifiedKernel(format) },
                                        onFlashModifiedDirectly = { viewModel.flashModifiedKernelDirectly() },
                                        onBackupOriginalNow = { viewModel.backupOriginalStockKernelNow() },
                                        onExportOriginal = { format -> viewModel.exportOriginalStockKernel(format) },
                                        onFlashOriginalDirectly = { viewModel.flashOriginalStockKernelDirectly() }
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

@Composable
private fun StartupRootCheckCard(
    isCheckingRoot: Boolean,
    hasRootAccess: Boolean,
    magiskbootPath: String?,
    onRetryRootCheck: () -> Unit
) {
    val containerColor = when {
        hasRootAccess -> Color(0xFF07261A)
        isCheckingRoot -> Color(0xFF2B1F06)
        else -> Color(0xFF330C12)
    }
    val borderColor = when {
        hasRootAccess -> KalamaEmerald
        isCheckingRoot -> KalamaAmber
        else -> KalamaCrimson
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("root_advisory_banner"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.5.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (hasRootAccess) Icons.Filled.CheckCircle else Icons.Filled.Security,
                    contentDescription = "Startup Root Check Status",
                    tint = borderColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        hasRootAccess -> "STARTUP ROOT CHECK PASSED (uid=0)"
                        isCheckingRoot -> "CHECKING ROOT ACCESS AT STARTUP • ROOT REQUIRED"
                        else -> "ROOT ACCESS REQUIRED TO PROCEED"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = borderColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = when {
                    hasRootAccess -> {
                        val mbInfo = magiskbootPath ?: "built-in FDT binary patcher"
                        "Superuser (su) access verified and workspace permissions repaired. Ready to back up, unpack ($mbInfo), and flash SM8550 kernel partitions."
                    }
                    else -> {
                        "Root (su) is required to proceed with reading, backing up, unpacking (magiskboot), and flashing your Samsung Snapdragon 8 Gen 2 kernel partitions (boot, vendor_boot, and vbmeta). Please grant Superuser access in Magisk, KernelSU, or APatch."
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary
            )
            if (!hasRootAccess) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onRetryRootCheck,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCheckingRoot) KalamaAmber else KalamaCrimson,
                        contentColor = if (isCheckingRoot) Color(0xFF261900) else Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("retry_root_check_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = "Verify Root Access",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Verify / Grant Root Access (su) to Proceed",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SimpleTopHeader(
    deviceName: String,
    isCheckingRoot: Boolean,
    hasRootAccess: Boolean,
    statusMessage: String,
    isError: Boolean,
    onRetryRootCheck: () -> Unit,
    onResetAllStock: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF10243E)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeveloperBoard,
                        contentDescription = "Snapdragon 8 Gen 2",
                        tint = KalamaCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "KalamaForge • Snapdragon 8 Gen 2",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = deviceName,
                            style = MaterialTheme.typography.labelSmall,
                            color = KalamaCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when {
                                isCheckingRoot -> Color(0xFF332400)
                                hasRootAccess -> Color(0xFF06331E)
                                else -> Color(0xFF3B0A11)
                            },
                            border = BorderStroke(
                                1.dp,
                                when {
                                    isCheckingRoot -> KalamaAmber
                                    hasRootAccess -> KalamaEmerald
                                    else -> KalamaCrimson
                                }
                            ),
                            modifier = Modifier
                                .clickable { onRetryRootCheck() }
                                .testTag("root_status_badge")
                        ) {
                            Text(
                                text = when {
                                    isCheckingRoot -> "CHECKING ROOT..."
                                    hasRootAccess -> "ROOT GRANTED"
                                    else -> "ROOT REQUIRED"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    isCheckingRoot -> KalamaAmber
                                    hasRootAccess -> KalamaEmerald
                                    else -> KalamaCrimson
                                },
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedButton(
                onClick = onResetAllStock,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                border = BorderStroke(1.dp, ObsidianCardBorder),
                modifier = Modifier.testTag("reset_all_stock_btn")
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Reset All to Stock",
                    tint = TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "All Stock",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isError) Color(0xFF360B11) else ObsidianSurfaceVariant,
            border = BorderStroke(1.dp, if (isError) KalamaCrimson else ObsidianCardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("status_banner_surface")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isError) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                    contentDescription = "Status",
                    tint = if (isError) KalamaCrimson else KalamaEmerald,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
