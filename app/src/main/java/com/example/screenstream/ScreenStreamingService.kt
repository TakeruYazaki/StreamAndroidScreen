package com.example.screenstream

import android.app.Service
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import android.graphics.PixelFormat
import android.media.Image

class ScreenStreamingService : Service() {

    private lateinit var mediaProjection: MediaProjection
    private lateinit var imageReader: ImageReader
    private lateinit var httpServer: MjpegStreamingHttpServer
    private val frameQueue: ConcurrentLinkedQueue<ByteArray> = ConcurrentLinkedQueue()
    private val handler = Handler()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (intent == null) {
            Log.e("ScreenStreamingService", "Intent is null")
            stopSelf()  // サービスを停止するか適切な処理を行う
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("data")

        if (data == null) {
            Log.e("ScreenStreamingService", "Data is null")
            stopSelf()  // サービスを停止するか適切な処理を行う
            return START_NOT_STICKY
        }

        // Android 8.0 以降で通知チャネルを作成
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "mediaProjectionChannel",
                "Screen Streaming Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        startForegroundServiceWithNotification()

        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data!!)

        // MediaProjection の状態変化を監視するためのコールバックを登録
        mediaProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                Log.i("ScreenStreamingService", "MediaProjection stopped.")
                stopScreenCapture()
            }
        }, null)

        // 画面キャプチャのためのセットアップ
        setupScreenCapture()

        // MJPEGストリーミングサーバをセットアップ
        setupHttpServer()

        return START_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        val notificationId = 1
        val notification = Notification.Builder(this, "mediaProjectionChannel")
            .setContentTitle("Screen Streaming")
            .setContentText("Streaming screen...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(notificationId, notification)
    }

    private fun setupScreenCapture() {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)

        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val density = displayMetrics.densityDpi

        // ImageReaderを使ってキャプチャ
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
        val surface = imageReader.surface

        // MediaProjectionを使って画面キャプチャを開始
        mediaProjection.createVirtualDisplay(
            "ScreenStreaming",
            width,
            height,
            density,
            0,
            surface,
            null,
            null
        )

        // 30fps（約33ミリ秒ごとに1フレーム）のタイマーを使用してフレームを取得
        handler.postDelayed(object : Runnable {
            override fun run() {
                var image: Image? = null
                try {
                    image = imageReader.acquireLatestImage()  // フレームを取得
                    if (image != null) {
                        // 非同期処理の代わりに、直列的に処理を行い、完了後に必ずクローズ
                        val jpegData = convertImageToJpeg(image)
                        if (jpegData != null) {
                            Log.i("ScreenStreamingService", "Captured frame of size: ${jpegData.size}")

                            // フレームキューに追加
                            if (frameQueue.size > 10) {
                                frameQueue.poll() // 古いフレームを削除
                            }
                            frameQueue.add(jpegData)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ScreenStreamingService", "Error acquiring image", e)
                } finally {
                    // 必ずImageをクローズしてリソースを解放
                    image?.close()
                }

                // 33ミリ秒後に次のフレームを取得
                handler.postDelayed(this, 10)
            }
        }, 10)



    }


    private fun stopScreenCapture() {
        if (this::mediaProjection.isInitialized) {
            mediaProjection.stop()
        }
        if (this::imageReader.isInitialized) {
            imageReader.close()
        }
    }

    private fun convertImageToJpeg(image: Image): ByteArray? {
        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            // Bitmap全体を作成
            val fullBitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            fullBitmap.copyPixelsFromBuffer(buffer)

            // 横幅は画面全体、縦方向の開始位置と高さは指定
            val cropX = 0  // 切り取りを始めるx座標
            val cropY = 0  // 切り取りを始めるY座標（例: 550px）
            val cropWidth = image.width  // 切り取りたい領域の横幅　image.widthなら画面全体
            val cropHeight = image.height  // 切り取りたい領域の高さ（例: 70px）image.heightなら画面全体

            // 特定の領域を切り出す
            val croppedBitmap = Bitmap.createBitmap(
                fullBitmap,  // 元のBitmap
                cropX,       // 切り出し開始位置X
                cropY,       // 切り出し開始位置Y
                cropWidth,   // 切り出す幅
                cropHeight   // 切り出す高さ
            )

            // 切り出したBitmapをJPEGにエンコード
            val outputStream = ByteArrayOutputStream()
            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            return outputStream.toByteArray()
        } catch (e: Exception) {
            Log.e("ScreenStreamingService", "Error converting image to JPEG", e)
            return null
        }
    }

    private fun setupHttpServer() {
        try {
            httpServer = MjpegStreamingHttpServer(8888, frameQueue)  // 単一のフレームキューを渡す
            httpServer.start()
            Log.i("ScreenStreamingService", "HTTP server started on port 8888")
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("ScreenStreamingService", "Failed to start HTTP server: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScreenCapture()
        if (this::httpServer.isInitialized) {
            httpServer.stopServer()  // サーバの完全停止
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
