package com.rshock.quanjing.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

val Context.settingsDataStore by preferencesDataStore("qj_settings")

object SettingsStore {

    private val KEY_MODE = stringPreferencesKey("mode")
    private val KEY_ZOOM = floatPreferencesKey("ocr_zoom")
    private val KEY_DELAY = intPreferencesKey("click_delay")
    private val KEY_DEBUG = booleanPreferencesKey("debug_mode")
    private val KEY_BALL_ALPHA = floatPreferencesKey("ball_alpha")
    private val KEY_BALL_SIZE = intPreferencesKey("ball_size")
    private val KEY_BALL_SHAPE = stringPreferencesKey("ball_shape")
    private val KEY_DELAY_START = intPreferencesKey("delay_start")
    private val KEY_DELAY_TAG = intPreferencesKey("delay_tag")

    const val DEFAULT_MODE = "ball"
    const val DEFAULT_ZOOM = 2.0f
    const val DEFAULT_DELAY = 400
    const val DEFAULT_DEBUG = false
    const val DEFAULT_BALL_ALPHA = 0.8f
    const val DEFAULT_BALL_SIZE = 56
    const val DEFAULT_BALL_SHAPE = "circle"
    const val DEFAULT_DELAY_TAG = 100
    const val DEFAULT_DELAY_START = 50

    private var appCtx: Context? = null

    var mode: String = DEFAULT_MODE
        set(v) { field = v; saveString("mode", v) }
    var ocrZoom: Float = DEFAULT_ZOOM
        set(v) { field = v; saveFloat("ocr_zoom", v) }
    var clickDelayMs: Int = DEFAULT_DELAY
        set(v) { field = v; saveInt("click_delay", v) }
    var debugMode: Boolean = DEFAULT_DEBUG
        set(v) { field = v; saveBool("debug_mode", v) }
    var ballAlpha: Float = DEFAULT_BALL_ALPHA
        set(v) { field = v; saveFloat("ball_alpha", v) }
    var ballSizeDp: Int = DEFAULT_BALL_SIZE
        set(v) { field = v; saveInt("ball_size", v) }
    var ballShape: String = DEFAULT_BALL_SHAPE
        set(v) { field = v; saveString("ball_shape", v) }
    var delayTag: Int = DEFAULT_DELAY_TAG
        set(v) { field = v; saveInt("delay_tag", v) }

    var delayStart: Int = DEFAULT_DELAY_START
        set(v) { field = v; saveInt("delay_start", v) }

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        runBlocking {
            val p = ctx.settingsDataStore.data.first()
            mode = p[KEY_MODE] ?: DEFAULT_MODE
            ocrZoom = p[KEY_ZOOM] ?: DEFAULT_ZOOM
            clickDelayMs = p[KEY_DELAY] ?: DEFAULT_DELAY
            debugMode = p[KEY_DEBUG] ?: DEFAULT_DEBUG
            ballAlpha = p[KEY_BALL_ALPHA] ?: DEFAULT_BALL_ALPHA
            ballSizeDp = p[KEY_BALL_SIZE] ?: DEFAULT_BALL_SIZE
            ballShape = p[KEY_BALL_SHAPE] ?: DEFAULT_BALL_SHAPE

            // ★ 读取新加的延迟
            delayTag = p[KEY_DELAY_TAG] ?: DEFAULT_DELAY_TAG
            delayStart = p[KEY_DELAY_START] ?: DEFAULT_DELAY_START
        }
    }

    private fun saveString(k: String, v: String) {
        val c = appCtx ?: return
        runBlocking { c.settingsDataStore.edit { it[stringPreferencesKey(k)] = v } }
    }
    private fun saveFloat(k: String, v: Float) {
        val c = appCtx ?: return
        runBlocking { c.settingsDataStore.edit { it[floatPreferencesKey(k)] = v } }
    }
    private fun saveInt(k: String, v: Int) {
        val c = appCtx ?: return
        runBlocking { c.settingsDataStore.edit { it[intPreferencesKey(k)] = v } }
    }
    private fun saveBool(k: String, v: Boolean) {
        val c = appCtx ?: return
        runBlocking { c.settingsDataStore.edit { it[booleanPreferencesKey(k)] = v } }
    }
}