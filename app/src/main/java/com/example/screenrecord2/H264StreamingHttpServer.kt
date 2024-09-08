package com.example.screenrecord2

import fi.iki.elonen.NanoHTTPD
import java.util.concurrent.ConcurrentLinkedQueue
import android.util.Log
import java.io.InputStream

class H264StreamingHttpServer(
    port: Int,
    private val frameQueue: ConcurrentLinkedQueue<ByteArray>
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession?): Response {
        Log.i("H264StreamingHttpServer", "Received HTTP request from: ${session?.remoteIpAddress}, URI: ${session?.uri}")

        if (session?.uri == "/video_feed") {
            return newChunkedResponse(Response.Status.OK, "video/h264", object : InputStream() {
                private var currentFrame: ByteArray? = null
                private var currentPosition = 0

                override fun read(): Int {
                    if (currentFrame == null || currentPosition >= currentFrame!!.size) {
                        currentFrame = getNextFrame() ?: return -1
                        currentPosition = 0
                    }
                    return currentFrame!![currentPosition++].toInt() and 0xFF
                }

                private fun getNextFrame(): ByteArray? {
                    return frameQueue.poll()
                }
            })
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
    }
}
