package com.popovicialinc.gama

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private val binderListener = Shizuku.OnBinderReceivedListener {
        try {
            if (!Shizuku.isPreV11() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(0)
            }
        } catch (_: Exception) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        setContent { GamaCoreTheme { GamaCoreApp() } }
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderListener)
        super.onDestroy()
    }
}

@Composable
private fun GamaCoreTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

private enum class Screen { MAIN, OPTIONS, INTEGRATIONS, SETTINGS, DIAGNOSTICS, ABOUT }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun GamaCoreApp() {
    val context = LocalContext.current
    val controller = remember { RendererController(context.applicationContext) }
    val state by controller.state.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var screen by remember { mutableStateOf(Screen.MAIN) }
    BackHandler(screen != Screen.MAIN) { screen = Screen.MAIN }
    LaunchedEffect(Unit) { controller.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (screen == Screen.MAIN) "GAMA Core" else screen.title()) },
                navigationIcon = if (screen == Screen.MAIN) ({}) else ({ TextButton({ screen = Screen.MAIN }) { Text("Back") } })
            )
        }
    ) { padding ->
        when (screen) {
            Screen.MAIN -> MainScreen(padding, state,
                onVulkan = { scope.launch { controller.switchTo(RendererState.RENDERER_VULKAN) } },
                onOpenGl = { scope.launch { controller.switchTo(RendererState.RENDERER_OPENGL) } },
                onRefresh = { scope.launch { controller.refresh() } },
                onNavigate = { screen = it }
            )
            Screen.OPTIONS -> OptionsScreen(padding)
            Screen.INTEGRATIONS -> IntegrationsScreen(padding)
            Screen.SETTINGS -> SettingsScreen(padding)
            Screen.DIAGNOSTICS -> DiagnosticsScreen(padding, state)
            Screen.ABOUT -> AboutScreen(padding)
        }
    }
}

private fun Screen.title() = when (this) {
    Screen.MAIN -> "GAMA Core"
    Screen.OPTIONS -> "Renderer options"
    Screen.INTEGRATIONS -> "Integrations"
    Screen.SETTINGS -> "Settings"
    Screen.DIAGNOSTICS -> "Diagnostics"
    Screen.ABOUT -> "About"
}

@Composable
private fun MainScreen(
    padding: PaddingValues,
    state: RendererUiState,
    onVulkan: () -> Unit,
    onOpenGl: () -> Unit,
    onRefresh: () -> Unit,
    onNavigate: (Screen) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Current renderer", style = MaterialTheme.typography.labelLarge)
                    Text(state.actual, style = MaterialTheme.typography.headlineMedium)
                    if (state.actual != state.desired) Text("Desired after reboot: " + state.desired, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onVulkan, modifier = Modifier.weight(1f), enabled = !state.inProgress) { Text("Use Vulkan") }
                OutlinedButton(onOpenGl, modifier = Modifier.weight(1f), enabled = !state.inProgress) { Text("Use OpenGL") }
            }
        }
        item { StatusLine(if (state.inProgress) state.message ?: "Switching…" else state.message, state.detail) }
        item { HorizontalDivider() }
        item {
            ListItem(
                headlineContent = { Text("Privileged access") },
                supportingContent = { Text(state.backend) },
                trailingContent = { TextButton(onRefresh) { Text("Refresh") } }
            )
        }
        item { HorizontalDivider() }
        item { NavigationRow("Renderer options") { onNavigate(Screen.OPTIONS) } }
        item { NavigationRow("Integrations") { onNavigate(Screen.INTEGRATIONS) } }
        item { NavigationRow("Settings") { onNavigate(Screen.SETTINGS) } }
        item { NavigationRow("Diagnostics") { onNavigate(Screen.DIAGNOSTICS) } }
        item { NavigationRow("About") { onNavigate(Screen.ABOUT) } }
    }
}

@Composable
private fun StatusLine(message: String?, detail: String?) {
    if (message == null) return
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun NavigationRow(label: String, click: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Text("›", style = MaterialTheme.typography.headlineSmall) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = click)
    )
}

@Composable
private fun OptionsScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("gama_prefs", Context.MODE_PRIVATE) }
    var version by remember { mutableIntStateOf(0) }
    val aggressive = remember(version) { prefs.getBoolean("aggressive_mode", false) }
    val restartLauncher = remember(version) { prefs.getBoolean("kill_launcher", false) }
    val restartKeyboard = remember(version) { prefs.getBoolean("kill_keyboard", false) }
    SettingsList(padding) {
        TogglePreference("Aggressive switching", "Restart eligible apps after switching.", aggressive) { prefs.edit().putBoolean("aggressive_mode", it).apply(); version++ }
        TogglePreference("Restart launcher", "Apply the renderer to the launcher when safe.", restartLauncher) { prefs.edit().putBoolean("kill_launcher", it).apply(); version++ }
        TogglePreference("Restart keyboard", "Restart the active keyboard after switching.", restartKeyboard) { prefs.edit().putBoolean("kill_keyboard", it).apply(); version++ }
    }
}

@Composable
private fun IntegrationsScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val token = remember { TaskerAuth.getOrCreateToken(context) }
    SettingsList(padding) {
        ListItem(headlineContent = { Text("Quick Settings and widget") }, supportingContent = { Text("Add the renderer tile or GAMA Core widget from Android's picker.") })
        HorizontalDivider()
        ListItem(headlineContent = { Text("Tasker") }, supportingContent = { Text("Broadcast: " + TaskerReceiver.ACTION_SET_RENDERER + "\nExtras: renderer=vulkan|opengl, token=<token>") })
        Button({
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("GAMA Core Tasker token", token))
            Toast.makeText(context, "Token copied", Toast.LENGTH_SHORT).show()
        }) { Text("Copy Tasker token") }
    }
}

@Composable
private fun SettingsScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("gama_prefs", Context.MODE_PRIVATE) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var backupText by remember { mutableStateOf<String?>(null) }
    var restoreResult by remember { mutableStateOf<String?>(null) }
    val restoreAfterBoot = remember(version) { prefs.getBoolean("restore_after_boot", true) }
    val notificationsEnabled = remember(version) { prefs.getBoolean("notif_enabled", false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        backupText?.let { text -> uri?.let { context.contentResolver.openOutputStream(it)?.use { stream -> stream.write(text.toByteArray()) } } }
        backupText = null
    }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { selected -> scope.launch {
            restoreResult = runCatching {
                context.contentResolver.openInputStream(selected)?.bufferedReader()?.use { BackupHelper.import(prefs, it.readText()) } ?: "Could not read backup."
            }.getOrElse { it.message ?: "Restore failed." }
            version++
        } }
    }
    SettingsList(padding) {
        TogglePreference("Restore renderer after reboot", "Reapply the desired Vulkan renderer when access becomes available.", restoreAfterBoot) { prefs.edit().putBoolean("restore_after_boot", it).apply(); version++ }
        TogglePreference("Renderer notifications", "Show the boot restoration result.", notificationsEnabled) {
            prefs.edit().putBoolean("notif_enabled", it).apply()
            if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !ShizukuHelper.hasNotificationPermission(context)) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            version++
        }
        HorizontalDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton({ scope.launch { backupText = BackupHelper.export(prefs); createDocument.launch(BackupHelper.buildFileName()) } }) { Text("Backup") }
            OutlinedButton({ openDocument.launch(arrayOf("application/json")) }) { Text("Restore") }
        }
        restoreResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Text("Theme and language follow Android automatically.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DiagnosticsScreen(padding: PaddingValues, state: RendererUiState) {
    val context = LocalContext.current
    val report = remember(state) {
        "GAMA Core\nrenderer=" + state.actual + "\ndesired=" + state.desired + "\nbackend=" + state.backend + "\nlast operation=" + (state.message ?: "none") + "\n" + (state.detail ?: "")
    }
    SettingsList(padding) {
        Text(report, style = MaterialTheme.typography.bodyMedium)
        Button({
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("GAMA Core diagnostics", report))
            Toast.makeText(context, "Diagnostics copied", Toast.LENGTH_SHORT).show()
        }) { Text("Copy diagnostics") }
    }
}

@Composable
private fun AboutScreen(padding: PaddingValues) {
    SettingsList(padding) {
        Text("GAMA Core is a small renderer-management utility. It uses Root or authorized Shizuku access to change and verify Android's HWUI renderer property.")
    }
}

@Composable
private fun TogglePreference(title: String, summary: String, checked: Boolean, change: (Boolean) -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(summary) }, trailingContent = { Switch(checked = checked, onCheckedChange = change) })
}

@Composable
private fun SettingsList(padding: PaddingValues, content: @Composable () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) { item { content() } }
}
