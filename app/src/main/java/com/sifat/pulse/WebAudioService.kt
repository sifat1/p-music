package com.sifat.pulse

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Foreground service that keeps the process (and the browser page's own player) alive
 * while a web page is playing, like a desktop browser with the window minimized.
 */
class WebAudioService : Service() {
    private var playing = true
    private var title = "Browser audio"
    private val pause = "document.querySelectorAll('video,audio').forEach(function(m){m.pause()})"

    override fun onBind(i: Intent?): IBinder? = null
    override fun onCreate() { super.onCreate(); WebHolder.serviceRunning = true }
    override fun onDestroy() { WebHolder.serviceRunning = false; super.onDestroy() }
    override fun onTaskRemoved(rootIntent: Intent?) { if (!playing) stopSelf() }

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        when (i?.action) {
            "stop" -> { WebHolder.exec(pause); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY }
            "toggle" -> { WebHolder.exec("document.querySelectorAll('video,audio').forEach(function(m){m.paused?m.play():m.pause()})"); return START_NOT_STICKY }
            else -> { playing = i?.getBooleanExtra("playing", true) ?: true; title = i?.getStringExtra("title") ?: title }
        }
        val n = notification()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(1, n)
        return START_NOT_STICKY
    }

    private fun pi(action: String) = PendingIntent.getService(this, action.hashCode(),
        Intent(this, WebAudioService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun notification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("web_audio", "Browser audio", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "web_audio")
            .setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(title).setContentText("Playing from browser")
            .setContentIntent(open).setOngoing(playing).setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (playing) "Pause" else "Play", pi("toggle"))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pi("stop"))
            .setStyle(Notification.MediaStyle().setShowActionsInCompactView(0, 1)).build()
    }
}
