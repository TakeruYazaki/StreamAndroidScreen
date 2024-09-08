package com.example.screenstream

import fi.iki.elonen.NanoHTTPD
import java.io.InputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import android.util.Log

class MjpegStreamingHttpServer(
    port: Int,
    private val frameQueue: ConcurrentLinkedQueue<ByteArray>  // 単一のキューを共有
) : NanoHTTPD(port) {

    // ExecutorService を使用してスレッドプールを作成
    private val executor = Executors.newCachedThreadPool()

    override fun serve(session: IHTTPSession?): Response {
        val clientIp = session?.remoteIpAddress ?: "unknown"

        Log.i("MjpegStreamingHttpServer", "Received HTTP request from: $clientIp, URI: ${session?.uri}")

        if (session?.uri == "/video_feed") {
            // 各クライアントに独立したストリームを作成し、非同期で処理
            return newChunkedResponse(
                Response.Status.OK,
                "multipart/x-mixed-replace; boundary=frame",
                MjpegInputStream(frameQueue)  // クライアントごとのインスタンス
            )
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
    }

    // 必要に応じてサーバの停止時にスレッドプールをシャットダウン
    fun stopServer() {
        executor.shutdownNow()
        stop()
    }

    // フレームをストリームとしてクライアントに送るInputStreamクラス
    private class MjpegInputStream(private val frameQueue: ConcurrentLinkedQueue<ByteArray>) : InputStream() {
        private var currentFrame: ByteArray? = null
        private var currentPosition = 0

        override fun read(): Int {
            if (currentFrame == null || currentPosition >= currentFrame!!.size) {
                // 新しいフレームが来るまで待機
                currentFrame = getNextFrame() ?: return -1
                currentPosition = 0
            }
            return currentFrame!![currentPosition++].toInt() and 0xFF
        }

        private fun getNextFrame(): ByteArray? {
            // キューにフレームが存在するまで待機
            while (frameQueue.isEmpty()) {
                try {
                    Thread.sleep(33)  // 30fpsに合わせて33ms待機
                } catch (e: InterruptedException) {
                    Log.e("MjpegStreamingHttpServer", "Interrupted while waiting for the next frame", e)
                    return null
                }
            }

            // 新しいフレームを取得
            val frame = frameQueue.poll() ?: return null  // 古いフレームを削除して最新のフレームを取得
            val boundary = "--frame\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray()
            val endBoundary = "\r\n".toByteArray()

            // フレームデータにboundaryとフレームのJPEGデータを連結
            val fullFrame = ByteArray(boundary.size + frame.size + endBoundary.size)
            System.arraycopy(boundary, 0, fullFrame, 0, boundary.size)
            System.arraycopy(frame, 0, fullFrame, boundary.size, frame.size)
            System.arraycopy(endBoundary, 0, fullFrame, boundary.size + frame.size, endBoundary.size)

            return fullFrame
        }
    }
}
