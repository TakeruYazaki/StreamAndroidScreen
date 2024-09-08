package com.example.screenrecord2

import android.app.Notification
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.screenstream.R

class ScreenRecordService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder? = null
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var isRecording = false // 録画が開始されているかのフラグ

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // フォアグラウンドサービスの開始
        startForeground(1, createNotification())

        // MediaProjectionManagerを取得
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // MediaProjectionの取得
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("data")
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data!!)

        mediaRecorder = MediaRecorder().apply {
            // MediaRecorderの設定
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(1280, 720)
            setVideoFrameRate(30)
            setVideoEncodingBitRate(5_000_000)
            setOutputFile("/storage/emulated/0/Download/screenrecord.mp4")
            prepare()
        }

        // 録画を開始
        try {
            mediaRecorder?.start()
            isRecording = true // 録画が開始されたことをフラグで管理
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ScreenRecordService", "Recording failed to start: ${e.message}")
            stopSelf() // 録画開始に失敗した場合、サービスを停止
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) {
            try {
                mediaRecorder?.stop() // 録画を停止
                isRecording = false // フラグをリセット
            } catch (e: RuntimeException) {
                e.printStackTrace()
                Log.e("ScreenRecordService", "Failed to stop recording: ${e.message}")
            } finally {
                mediaRecorder?.reset()
                mediaProjection?.stop()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotification(): Notification {
        val channelId = "screen_record_channel"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Android 8.0 (API 26) 以降では通知チャンネルが必要
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Screen Record", NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Screen Recording")
            .setContentText("Recording in progress")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // アイコンは適宜変更
            .build()
    }
}
