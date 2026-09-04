package com.popovicialinc.gama

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Functional-settings-only backup format for GAMA Core. */
object BackupHelper {
    private val BOOLEAN_KEYS = listOf("aggressive_mode", "kill_launcher", "kill_keyboard", "notif_enabled", "restore_after_boot")
    private val STRING_KEYS = listOf(RendererState.PREF_DESIRED_RENDERER)
    private val STRING_SET_KEYS = emptyList<String>()

    internal fun registeredKeysForTesting(): Set<String> = (BOOLEAN_KEYS + STRING_KEYS + STRING_SET_KEYS).toSet()
    internal fun isRegisteredStringSetKey(key: String) = key in STRING_SET_KEYS
    internal fun isRegisteredStringKey(key: String) = key in STRING_KEYS
    internal fun isRegisteredFloatKey(key: String) = false
    internal fun isRegisteredLongKey(key: String) = false
    internal fun isRegisteredBooleanKey(key: String) = key in BOOLEAN_KEYS

    fun buildFileName(): String = "GAMA_Core_backup_" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".json"

    suspend fun export(prefs: SharedPreferences): String = withContext(Dispatchers.IO) {
        JSONObject().apply {
            put("gama_backup_version", 3)
            put("edition", "gama_core")
            put("exported_at", System.currentTimeMillis())
            BOOLEAN_KEYS.forEach { key -> if (prefs.contains(key)) put(key, prefs.getBoolean(key, false)) }
            STRING_KEYS.forEach { key -> if (prefs.contains(key)) put(key, prefs.getString(key, "") ?: "") }
            STRING_SET_KEYS.forEach { key -> if (prefs.contains(key)) put(key, JSONArray(prefs.getStringSet(key, emptySet())?.toList())) }
        }.toString(2)
    }

    /** Old visual keys are deliberately ignored; only supported functional values are restored. */
    suspend fun import(prefs: SharedPreferences, json: String): String = withContext(Dispatchers.IO) {
        val root = JSONObject(json)
        if (root.optInt("gama_backup_version", 0) < 1) throw IllegalArgumentException("This doesn't look like a GAMA backup file.")
        val editor = prefs.edit()
        var restored = 0
        var skipped = 0
        BOOLEAN_KEYS.forEach { key ->
            if (!root.has(key)) return@forEach
            val value = root.opt(key)
            if (value is Boolean || value is String && (value.equals("true", true) || value.equals("false", true))) {
                editor.putBoolean(key, root.optBoolean(key)); restored++
            } else skipped++
        }
        STRING_KEYS.forEach { key ->
            if (!root.has(key)) return@forEach
            (root.opt(key) as? String)?.let { editor.putString(key, it); restored++ } ?: run { skipped++ }
        }
        STRING_SET_KEYS.forEach { key ->
            if (!root.has(key)) return@forEach
            val values = root.optJSONArray(key)
            if (values == null) { skipped++; return@forEach }
            editor.putStringSet(key, buildSet { for (index in 0 until values.length()) values.optString(index, null)?.let(::add) })
            restored++
        }
        editor.apply()
        "Restored $restored functional settings" + if (skipped > 0) " ($skipped invalid entries skipped)." else "."
    }
}
