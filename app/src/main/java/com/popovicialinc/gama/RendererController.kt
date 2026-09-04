package com.popovicialinc.gama

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RendererUiState(val actual: String = "Unknown", val desired: String = RendererState.RENDERER_OPENGL, val backend: String = "Checking…", val inProgress: Boolean = false, val message: String? = null, val detail: String? = null)

/** Sole coordinator for renderer detection, switching, persistence, and read-back. */
class RendererController(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(RendererUiState(desired = RendererState.getDesiredRenderer(prefs)))
    val state: StateFlow<RendererUiState> = mutableState.asStateFlow()

    suspend fun refresh() {
        RendererState.reconcileRebootReset(prefs)
        val root = ShizukuHelper.refreshRootAvailability()
        val shizuku = ShizukuHelper.checkBinder() && ShizukuHelper.checkPermission()
        val backend = when { root -> "Root available"; shizuku -> "Shizuku available"; ShizukuHelper.checkBinder() -> "Shizuku permission required"; else -> "No privileged backend available" }
        val actual = if (root || shizuku) ShizukuHelper.getCurrentRenderer() else RendererState.getRenderer(prefs)
        if (actual in setOf(RendererState.RENDERER_VULKAN, RendererState.RENDERER_OPENGL)) RendererState.recordObservedRenderer(prefs, actual)
        mutableState.value = mutableState.value.copy(actual = actual, desired = RendererState.getDesiredRenderer(prefs), backend = backend, inProgress = false)
    }

    suspend fun switchTo(target: String) {
        if (target !in setOf(RendererState.RENDERER_VULKAN, RendererState.RENDERER_OPENGL)) return
        mutableState.value = mutableState.value.copy(inProgress = true, message = "Switching to $target…", detail = null)
        RendererOperationLock.run { switchToLocked(target) }
    }

    private suspend fun switchToLocked(target: String) {
        val root = ShizukuHelper.refreshRootAvailability()
        val shizuku = ShizukuHelper.checkBinder() && ShizukuHelper.checkPermission()
        if (!root && !shizuku) {
            mutableState.value = mutableState.value.copy(inProgress = false, message = "Could not switch renderer", detail = "Root and authorized Shizuku access are unavailable.")
            return
        }
        val options = RendererOptions.from(prefs)
        val changed = if (target == RendererState.RENDERER_VULKAN) {
            ShizukuHelper.runVulkanSuspend(
                context = context, aggressiveMode = options.aggressive,
                killLauncher = options.restartLauncher, killKeyboard = options.restartKeyboard, onStatusUpdate = {}
            )
        } else {
            ShizukuHelper.runOpenGLSuspend(
                context = context, aggressiveMode = options.aggressive,
                killLauncher = options.restartLauncher, killKeyboard = options.restartKeyboard, onStatusUpdate = {}
            )
        }
        val actual = ShizukuHelper.getCurrentRenderer()
        if (changed && actual == target) {
            RendererState.recordSwitch(prefs, target)
            ShizukuHelper.refreshRendererViewSync(context)
            mutableState.value = RendererUiState(actual, target, if (root) "Root available" else "Shizuku available", false, "$target enabled")
        } else {
            mutableState.value = mutableState.value.copy(inProgress = false, actual = actual, message = "Could not enable $target", detail = "Requested: $target\nDetected: $actual")
        }
    }
}

data class RendererOptions(val aggressive: Boolean, val restartLauncher: Boolean, val restartKeyboard: Boolean) {
    companion object { fun from(prefs: SharedPreferences) = RendererOptions(prefs.getBoolean("aggressive_mode", false), prefs.getBoolean("kill_launcher", false), prefs.getBoolean("kill_keyboard", false)) }
}
