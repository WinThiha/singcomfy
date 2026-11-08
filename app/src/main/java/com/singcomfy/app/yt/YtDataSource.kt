package com.singcomfy.app.yt

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.okhttp.OkHttpDataSource
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

/**
 * Data source for googlevideo "videoplayback" URLs. YouTube throttles or rejects big open-ended
 * requests, so this fetches the file in bounded chunks using the `range` URL parameter and
 * POST requests (the same way official clients do), stitching them into one continuous stream.
 * Any other URL is passed straight through.
 */
@UnstableApi
class YtDataSource(private val upstreamFactory: OkHttpDataSource.Factory) : BaseDataSource(true) {

    class Factory(private val upstream: OkHttpDataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = YtDataSource(upstream)
    }

    private var inner: DataSource? = null
    private var spec: DataSpec? = null
    private var passthrough = false
    private var position = 0L     // absolute position of next byte to read
    private var end = 0L          // absolute exclusive end of what we will serve
    private var chunkRemaining = 0L
    private var requestNumber = 0
    private var uri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val url = dataSpec.uri.toString()
        val clen = dataSpec.uri.getQueryParameter("clen")?.toLongOrNull()
        if (!dataSpec.uri.path.orEmpty().startsWith("/videoplayback") || clen == null) {
            passthrough = true
            val ds = upstreamFactory.createDataSource()
            inner = ds
            val len = ds.open(dataSpec)
            transferStarted(dataSpec)
            return len
        }
        passthrough = false
        position = dataSpec.position
        end = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.position + dataSpec.length else clen
        openChunk(url)
        transferStarted(dataSpec)
        return end - dataSpec.position
    }

    private fun openChunk(baseUrl: String) {
        inner?.close()
        val chunkEnd = minOf(end, position + CHUNK) - 1
        val u = "$baseUrl&range=$position-$chunkEnd&rn=${requestNumber++}"
        val ds = upstreamFactory.createDataSource()
        if (YoutubeParsingHelper.isVisionOsStreamingUrl(baseUrl)) {
            ds.setRequestProperty("User-Agent", YoutubeParsingHelper.getVisionOsUserAgent(null))
        }
        val chunkSpec = DataSpec.Builder()
            .setUri(u)
            .setHttpMethod(DataSpec.HTTP_METHOD_POST)
            .setHttpBody(byteArrayOf(0x78, 0))
            .build()
        ds.open(chunkSpec)
        inner = ds
        chunkRemaining = chunkEnd - position + 1
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val ds = inner ?: return C.RESULT_END_OF_INPUT
        if (passthrough) {
            val n = ds.read(buffer, offset, length)
            if (n > 0) bytesTransferred(n)
            return n
        }
        if (position >= end) return C.RESULT_END_OF_INPUT
        if (chunkRemaining <= 0) openChunk(spec!!.uri.toString())
        val toRead = minOf(length.toLong(), chunkRemaining).toInt()
        val n = inner!!.read(buffer, offset, toRead)
        if (n == C.RESULT_END_OF_INPUT) {
            // Server sent less than requested; continue with a fresh chunk from here.
            chunkRemaining = 0
            if (position >= end) return C.RESULT_END_OF_INPUT
            openChunk(spec!!.uri.toString())
            return read(buffer, offset, length)
        }
        position += n
        chunkRemaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        try {
            inner?.close()
        } finally {
            inner = null
            if (spec != null) transferEnded()
            spec = null
        }
    }

    companion object {
        private const val CHUNK = 4L * 1024 * 1024
    }
}
