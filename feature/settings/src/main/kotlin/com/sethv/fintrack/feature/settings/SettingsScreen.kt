package com.sethv.fintrack.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sethv.fintrack.core.ui.theme.FinTrackShape
import com.sethv.fintrack.core.ui.theme.FinTrackSpacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val pkgInfo = try { pm.getPackageInfo(context.packageName, 0) } catch (_: Exception) { null }
    val versionName = pkgInfo?.versionName ?: "1.1.0"
    val versionCode = pkgInfo?.let {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) it.longVersionCode.toString()
        else @Suppress("DEPRECATION") it.versionCode.toString()
    } ?: "2"

    var confirmDelete by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("SETTINGS", style = MaterialTheme.typography.titleSmall, letterSpacing = 1.4.sp, fontWeight = FontWeight.Black) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()).padding(FinTrackSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(FinTrackSpacing.Md),
        ) {
            SettingsSection(title = "DATA") {
                SettingsItem(
                    icon = Icons.Outlined.DeleteForever,
                    title = "Delete all data",
                    subtitle = "Erase transactions, cards & balance",
                    onClick = { confirmDelete = true },
                    tint = MaterialTheme.colorScheme.error,
                )
            }

            SettingsSection(title = "ABOUT") {
                SettingsItem(
                    icon = Icons.Outlined.Info,
                    title = "Version",
                    subtitle = "$versionName ($versionCode)",
                    onClick = {},
                )
                SettingsItem(
                    icon = Icons.Outlined.Code,
                    title = "GitHub",
                    subtitle = "AccessDenied1/FinTrack",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AccessDenied1/FinTrack"))
                        context.startActivity(intent)
                    },
                )
                SettingsItem(
                    icon = Icons.Outlined.Description,
                    title = "Open source licenses",
                    subtitle = "MIT — see LICENSE",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AccessDenied1/FinTrack/blob/main/LICENSE"))
                        context.startActivity(intent)
                    },
                )
            }

            SettingsSection(title = "PRIVACY") {
                SettingsItem(
                    icon = Icons.Outlined.Security,
                    title = "Privacy policy",
                    subtitle = "On-device only, no cloud",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AccessDenied1/FinTrack#privacy"))
                        context.startActivity(intent)
                    },
                )
                SettingsItem(
                    icon = Icons.Outlined.Shield,
                    title = "Permissions",
                    subtitle = "SMS + Notifications used for parsing only",
                    onClick = {},
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "FinTrack — Precision Ledger · Made with care in India",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            shape = FinTrackShape.Medium,
            title = { Text("Delete all data?", fontWeight = FontWeight.Bold) },
            text = { Text("Removes every transaction, pending item, credit card, statement and balance. Cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteAllData {
                            scope.launch { snackbarHostState.showSnackbar("All data deleted") }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = FinTrackShape.Pill,
                ) { Text("Delete everything") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelSmall, letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        OutlinedCard(shape = FinTrackShape.Medium) { Column { content() } }
    }
}

@Composable
private fun SettingsItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(FinTrackSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
