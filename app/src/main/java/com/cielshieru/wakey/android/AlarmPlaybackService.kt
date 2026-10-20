package com.cielshieru.wakey.android

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import android.util.Log

/**
 * Owns the audible alarm in a media-playback foreground service. Player looping
 * and the ongoing notification ensure a short source file or notification swipe
 * cannot silently stop a scheduled alarm.
 */
class AlarmPlaybackService : Service() {

    private var player: MediaPlayer? = null
    private var alarmIndex: Int? = null
    private var alarmDay: String? = null
    private var requestedRingtoneUri: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PLAY -> {
                alarmIndex = intent.getIntExtra(AlarmService.EXTRA_ALARM_INDEX, -1).takeIf { it >= 0 }
                alarmDay = intent.getStringExtra(AlarmService.EXTRA_ALARM_DAY)
                requestedRingtoneUri = intent.getStringExtra(AlarmService.EXTRA_RINGTONE_URI)

                val notification = NotificationService.buildAlarmNotification(
                    this,
                    alarmIndex,
                    alarmDay,
                    requestedRingtoneUri
                )
                startForeground(
                    NotificationService.ALARM_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
                startRingtone(requestedRingtoneUri)
                return START_NOT_STICKY
            }
            else -> {
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
    }

    private fun startRingtone(ringtoneUri: String?) {
        releasePlayer()
        val requested = ringtoneUri?.let(Uri::parse)
        val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val target = requested ?: defaultUri
        if (target == null) {
            Log.e(TAG, "No configured or default alarm ringtone is available")
            return
        }
        preparePlayer(target, defaultUri, target != defaultUri)
    }

    private fun preparePlayer(target: Uri, defaultUri: Uri?, allowFallback: Boolean) {
        val nextPlayer = MediaPlayer()
        player = nextPlayer
        try {
            nextPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            nextPlayer.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
            nextPlayer.isLooping = true
            nextPlayer.setOnPreparedListener { prepared ->
                if (player === prepared) prepared.start()
            }
            nextPlayer.setOnErrorListener { failedPlayer, what, extra ->
                Log.e(TAG, "Alarm ringtone playback error: $what, $extra")
                if (player === failedPlayer) {
                    releasePlayer()
                    if (allowFallback && defaultUri != null && defaultUri != target) {
                        preparePlayer(defaultUri, defaultUri, allowFallback = false)
                    }
                }
                true
            }
            nextPlayer.setDataSource(this, target)
            nextPlayer.prepareAsync()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to prepare alarm ringtone", e)
            if (player === nextPlayer) releasePlayer()
            if (allowFallback && defaultUri != null && defaultUri != target) {
                preparePlayer(defaultUri, defaultUri, allowFallback = false)
            }
        }
    }

    private fun releasePlayer() {
        val current = player
        player = null
        if (current != null) {
            runCatching { current.setOnPreparedListener(null) }
            runCatching { current.setOnErrorListener(null) }
            runCatching { current.stop() }
            runCatching { current.release() }
        }
    }

    override fun onDestroy() {
        releasePlayer()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY = "com.cielshieru.wakey.action.PLAY_ALARM"
        const val ACTION_STOP = "com.cielshieru.wakey.action.STOP_ALARM"

        private const val TAG = "AlarmPlaybackService"

        fun start(
            context: Context,
            alarmIndex: Int? = null,
            alarmDay: String? = null,
            ringtoneUri: String? = null
        ) {
            val intent = Intent(context, AlarmPlaybackService::class.java).apply {
                action = ACTION_PLAY
                alarmIndex?.let { putExtra(AlarmService.EXTRA_ALARM_INDEX, it) }
                alarmDay?.let { putExtra(AlarmService.EXTRA_ALARM_DAY, it) }
                ringtoneUri?.let { putExtra(AlarmService.EXTRA_RINGTONE_URI, it) }
            }
            context.startForegroundService(intent)
        }
    }
}
