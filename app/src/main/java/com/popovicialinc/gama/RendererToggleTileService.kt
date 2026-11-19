package com.popovicialinc.gama

import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Single Quick Settings tile that switches between the two renderers —
 * replaces the old separate Vulkan and OpenGL tiles.
 *
 * Tap → Vulkan if currently OpenGL, OpenGL if currently Vulkan.
 * The subtitle always shows the current renderer plus a hint of the next one.
 */
@RequiresApi(Build.VERSION_CODES.N)
class RendererToggleTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onClick() {
        super.onClick()

        val prefs = getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
        RendererState.reconcileRebootReset(prefs)
        val current = RendererState.getRenderer(prefs)
        val targetVulkan = current != RendererState.RENDERER_VULKAN
        val targetName = if (targetVulkan) RendererState.RENDERER_VULKAN else RendererState.RENDERER_OPENGL

        scope.launch {
            RendererOperationLock.run {
            if (!ShizukuHelper.isBackendReady() && !ShizukuHelper.refreshRootAvailability()) {
                setTile(Tile.STATE_INACTIVE, getString(R.string.tile_no_backend))
                return@run
            }

            setTile(Tile.STATE_ACTIVE, getString(R.string.tile_switching))

            try {
                val applied = if (targetVulkan) {
                    ShizukuHelper.runVulkanSuspend(
                        context = applicationContext,
                        aggressiveMode = prefs.getBoolean("aggressive_mode", false),
                        killLauncher = prefs.getBoolean("kill_launcher", false),
                        killKeyboard = prefs.getBoolean("kill_keyboard", false),
                        onStatusUpdate = {}
                    )
                } else {
                    ShizukuHelper.runOpenGLSuspend(
                        context = applicationContext,
                        aggressiveMode = prefs.getBoolean("aggressive_mode", false),
                        killLauncher = prefs.getBoolean("kill_launcher", false),
                        killKeyboard = prefs.getBoolean("kill_keyboard", false),
                        onStatusUpdate = {}
                    )
                }
                if (applied) {
                    // Persist renderer + both switch timestamps so BootReceiver
                    // re-applies this choice after reboot. commit() because the
                    // SystemUI soft-crash can kill this process mid-switch.
                    RendererState.recordSwitch(prefs, targetName)
                    setTile(Tile.STATE_ACTIVE, null)
                } else {
                    setTile(
                        Tile.STATE_INACTIVE,
                        getString(R.string.tile_failed)
                    )
                }
            } catch (_: Exception) {
                setTile(Tile.STATE_INACTIVE, getString(R.string.tile_failed))
            }
            }
        }
    }

    private fun setTile(state: Int, subtitle: String?) {
        val tile = qsTile ?: return
        tile.label = getString(R.string.tile_label)
        tile.state = state
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle ?: currentRendererSubtitle()
        }
        tile.updateTile()
    }

    private fun currentRendererSubtitle(): String {
        val prefs = getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
        RendererState.reconcileRebootReset(prefs)
        val renderer = RendererState.getRenderer(prefs)
        return if (renderer == RendererState.RENDERER_VULKAN) {
            getString(R.string.tile_state_vulkan)
        } else {
            getString(R.string.tile_state_opengl)
        }
    }

    private fun refreshTile() {
        setTile(Tile.STATE_ACTIVE, null)
    }
}
