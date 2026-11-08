package com.singfx.app

import android.app.Application
import com.singfx.app.dsp.FxState

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        FxState.init(this)
        // Never leave the phone's media volume muted if we crashed during Live FX.
        com.singfx.app.live.LiveFxPrefs.recoverAudio(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { com.singfx.app.live.LiveFxPrefs.recoverAudio(this) }
            previous?.uncaughtException(t, e)
        }
    }
}
