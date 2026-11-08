package com.lowas.metronome

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the metronome and drone running when the app is in the
 * background. It stops itself as soon as neither is playing.
 */
class MetronomeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_ALL) {
            Metronome.stop()
            Drone.stop()
            if (job == null) stopSelf()
            return START_NOT_STICKY
        }

        // Every startForegroundService call must be answered with startForeground.
        ensureChannel()
        val notification = buildNotification(
            Metronome.playing.value, Drone.playing.value, Metronome.settings.value
        )
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        when (action) {
            ACTION_START_METRONOME -> Metronome.start()
            ACTION_START_DRONE -> Drone.start()
        }

        if (job == null) {
            job = scope.launch {
                combine(Metronome.playing, Metronome.settings, Drone.playing) { m, s, d ->
                    Triple(m, s, d)
                }.collect { (m, s, d) ->
                    if (!m && !d) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                        nm.notify(NOTIF_ID, buildNotification(m, d, s))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Metronome.stop()
        Drone.stop()
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    private fun buildNotification(met: Boolean, drone: Boolean, s: MSettings): Notification {
        val parts = ArrayList<String>()
        if (met) parts.add("${s.bpm} BPM · ${s.beats}/4")
        if (drone) parts.add("Drone ${NOTE_NAMES[s.droneNote]}${s.droneOctave}")
        val text = if (parts.isEmpty()) "Starting…" else parts.joinToString("  •  ")

        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, MetronomeService::class.java).setAction(ACTION_STOP_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Metronome")
            .setContentText(text)
            .setContentIntent(openPi)
            .addAction(0, "Stop", stopPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .build()
    }

    companion object {
        const val ACTION_START_METRONOME = "com.lowas.metronome.START_METRONOME"
        const val ACTION_START_DRONE = "com.lowas.metronome.START_DRONE"
        const val ACTION_STOP_ALL = "com.lowas.metronome.STOP_ALL"
        private const val CHANNEL = "playback"
        private const val NOTIF_ID = 1
    }
}
