package com.example.ScreenStreaming

import android.graphics.Bitmap
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayOutputStream

class ScreenServer(port: Int) : NanoHTTPD(port) {

    private var latestBitmap: Bitmap? = null

    // HTTPリクエストに応じて画像を配信する
    override fun serve(session: IHTTPSession?): Response {
        return latestBitmap?.let { bitmap ->
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val byteArray = stream.toByteArray()
            // image/pngとしてBitmapを配信
            newFixedLengthResponse(Response.Status.OK, "image/png", byteArray.inputStream(), byteArray.size.toLong())
        } ?: newFixedLengthResponse("No image available")
    }

    // 画像を更新するメソッド
    fun updateBitmap(bitmap: Bitmap) {
        this.latestBitmap = bitmap
    }
}
