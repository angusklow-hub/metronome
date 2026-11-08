package com.lowas.metronome

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Metronome.init(this)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MetronomeApp(
                onToggleMetronome = {
                    if (Metronome.playing.value) Metronome.stop()
                    else startEngine(MetronomeService.ACTION_START_METRONOME)
                },
                onToggleDrone = {
                    if (Drone.playing.value) Drone.stop()
                    else startEngine(MetronomeService.ACTION_START_DRONE)
                },
            )
        }
    }

    private fun startEngine(action: String) {
        ContextCompat.startForegroundService(
            this, Intent(this, MetronomeService::class.java).setAction(action)
        )
    }

    override fun onStop() {
        super.onStop()
        // When "keep playing in background" is off, silence everything on leaving the app.
        if (!isChangingConfigurations && !Metronome.settings.value.background) {
            Metronome.stop()
            Drone.stop()
        }
    }
}
