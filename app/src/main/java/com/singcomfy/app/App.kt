package com.singcomfy.app

import android.app.Application
import com.singcomfy.app.dsp.FxState

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        FxState.init(this)
        // Never leave the phone's media volume muted if we crashed during Live FX.
        com.singcomfy.app.live.LiveFxPrefs.recoverAudio(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { com.singcomfy.app.live.LiveFxPrefs.recoverAudio(this) }
            previous?.uncaughtException(t, e)
        }
    }
}
