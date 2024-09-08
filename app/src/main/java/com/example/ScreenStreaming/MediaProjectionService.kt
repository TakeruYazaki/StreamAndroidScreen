package com.example.ScreenStreaming

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import android.graphics.ImageFormat
import androidx.core.app.NotificationCompat
import java.nio.ByteBuffer
import android.os.Parcelable

class MediaProjectionService : Service() {

    companion object {
        const val CHANNEL_ID = "MediaProjectionServiceChannel"
        const val NOTIFICATION_ID = 1
    }

    private var mediaProjection: MediaProjection? = null
    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var imageReader: ImageReader
    private lateinit var screenServer: ScreenServer

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Streaming")
            .setContentText("Screen capture is running")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // 仮の通知アイコン
            .build()

        startForeground(NOTIFICATION_ID, notification)

        mediaProjection = intent?.getParcelableExtra<Parcelable>("media_projection") as? MediaProjection

        // ディスプレイメトリクスの取得
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)

        val screenDensity = metrics.densityDpi
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        // ImageReaderの作成 (キャプチャ画像のフォーマットはRGB_565を使用)
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, ImageFormat.RGB_565, 2)

        // VirtualDisplayの作成
        virtualDisplay = mediaProjection!!.createVirtualDisplay(
            "ScreenCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, null
        )

        // ScreenServerの初期化と開始
        screenServer = ScreenServer(8080)
        screenServer.start()

        // ImageReaderで画面キャプチャのデータを処理
        imageReader.setOnImageAvailableListener({
            val image = it.acquireLatestImage()
            if (image != null) {
                processImage(image)
                image.close()
            }
        }, null)

        return START_NOT_STICKY
    }

    private fun processImage(image: Image) {
        val width = image.width
        val height = image.height
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width

        // Bitmap作成 (Imageからピクセルデータを取得)
        val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)

        // サーバーにBitmapを送信 (ScreenServerに画像を更新)
        screenServer.updateBitmap(bitmap)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        virtualDisplay.release()
        mediaProjection?.stop()
        screenServer.stop() // サーバーの停止
    }

    private fun createNotificationChannel() {
        // APIレベルが26以上の場合、NotificationChannelを作成
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Media Projection Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

}
