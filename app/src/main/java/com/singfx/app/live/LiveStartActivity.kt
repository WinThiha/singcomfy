package com.singfx.app.live

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.media3.common.util.UnstableApi

/** Invisible activity that collects the permissions needed for Live FX and starts the service. */
@UnstableApi
class LiveStartActivity : Activity() {
    companion object {
        /** Optional package to open once Live FX has started (e.g. YouTube). */
        const val EXTRA_LAUNCH = "launch"
    }

    private val reqPerms = 1
    private val reqCapture = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val need = buildList {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (need.isEmpty()) askCapture() else requestPermissions(need.toTypedArray(), reqPerms)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "SingFX needs the audio-recording permission to process other apps' sound", Toast.LENGTH_LONG).show()
            finish(); return
        }
        askCapture()
    }

    private fun askCapture() {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val intent = if (Build.VERSION.SDK_INT >= 34) mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        else mpm.createScreenCaptureIntent()
        @Suppress("DEPRECATION")
        startActivityForResult(intent, reqCapture)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqCapture) {
            if (resultCode == RESULT_OK && data != null) {
                LiveFx.status.value = "Starting…"
                startForegroundService(
                    Intent(this, LiveFxService::class.java)
                        .setAction(LiveFxService.ACTION_START)
                        .putExtra(LiveFxService.EXTRA_CODE, resultCode)
                        .putExtra(LiveFxService.EXTRA_DATA, data)
                )
                intent.getStringExtra(EXTRA_LAUNCH)?.let { pkg ->
                    packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            } else {
                Toast.makeText(this, "Live FX cancelled", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }
}
