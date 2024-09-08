package com.example.screenstreaming2

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.graphics.PixelFormat
import java.nio.ByteBuffer

class ScreenCaptureActivity : Activity() {

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var mediaProjection: MediaProjection
    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var imageReader: ImageReader
    private lateinit var screenServer: ScreenServer

    private val REQUEST_CODE_CAPTURE_PERM = 1000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // MediaProjectionManagerの初期化
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // 画面キャプチャの許可をリクエスト
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE_CAPTURE_PERM)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_CAPTURE_PERM && resultCode == RESULT_OK) {
            data?.let {
                // MediaProjectionの取得
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, it)

                // ディスプレイサイズの取得
                val metrics = DisplayMetrics()
                windowManager.defaultDisplay.getMetrics(metrics)
                val screenDensity = metrics.densityDpi
                val screenWidth = metrics.widthPixels
                val screenHeight = metrics.heightPixels

                // ImageReaderの作成
                imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)

                // VirtualDisplayの作成
                virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenCapture",
                    screenWidth, screenHeight, screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.surface, null, null
                )

                // ImageReaderのリスナー設定
                imageReader.setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        val bitmap = processImage(image)
                        image.close()

                        // サーバーに画像を配信
                        screenServer.updateBitmap(bitmap)
                    }
                }, null)

                // HTTPサーバーの起動
                screenServer = ScreenServer(8080)
                screenServer.start()
            }
        }
    }

    // ImageをBitmapに変換するメソッド
    private fun processImage(image: Image): Bitmap {
        val width = image.width
        val height = image.height
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    override fun onDestroy() {
        super.onDestroy()
        // VirtualDisplayとMediaProjectionの解放
        virtualDisplay.release()
        mediaProjection.stop()
        screenServer.stop()
    }
}
