package com.singcomfy.app.live

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.media.AudioManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Does nothing with screen content. Its only purpose is the "accessibility volume" flag, which
 * gives SingComfy its own independent audio stream. Live FX plays the processed sound on that stream
 * while the original media stream is muted, so you only hear the pitch-shifted / EQ'd version.
 * While Live FX is on it also turns the volume buttons into karaoke volume buttons.
 */
class VolumeA11yService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
    }

    /**
     * While Live FX runs, the original media stream is muted, so the normal volume buttons would
     * fight with it. Instead, the buttons adjust SingComfy's karaoke output directly. Only the two
     * volume keys are ever looked at, and only while Live FX is on; everything else passes through.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        if (!LiveFx.interceptsVolumeKeys()) return false
        val am = getSystemService(AudioManager::class.java)
        if (am.mode != AudioManager.MODE_NORMAL) return false // calls / ringing: leave alone
        if (event.action == KeyEvent.ACTION_DOWN) LiveFx.nudgeVolume(if (code == KeyEvent.KEYCODE_VOLUME_UP) 1 else -1)
        return true
    }

    companion object {
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(context, VolumeA11yService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
