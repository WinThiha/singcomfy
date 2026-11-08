package com.singfx.app.yt

import com.singfx.app.player.StreamItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale

data class VideoResult(
    val url: String,
    val title: String,
    val channel: String?,
    val durationSec: Long,
    val thumbnail: String?,
)

data class SearchPage(val items: List<VideoResult>, val next: Page?)

object YouTubeRepo {
    @Volatile private var initialized = false

    private fun ensureInit() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val loc = Locale.getDefault()
            NewPipe.init(
                NpDownloader(),
                Localization.fromLocale(loc),
                ContentCountry(loc.country.ifBlank { "US" })
            )
            initialized = true
        }
    }

    private val yt get() = ServiceList.YouTube

    private fun handler(query: String) =
        yt.searchQHFactory.fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")

    private fun map(items: List<*>): List<VideoResult> = items.filterIsInstance<StreamInfoItem>().map {
        VideoResult(
            url = it.url,
            title = it.name,
            channel = it.uploaderName,
            durationSec = it.duration,
            thumbnail = it.thumbnails.maxByOrNull { img -> img.width }?.url ?: it.thumbnails.firstOrNull()?.url,
        )
    }

    suspend fun search(query: String): SearchPage = withContext(Dispatchers.IO) {
        ensureInit()
        val info = SearchInfo.getInfo(yt, handler(query))
        SearchPage(map(info.relatedItems), info.nextPage)
    }

    suspend fun more(query: String, page: Page): SearchPage = withContext(Dispatchers.IO) {
        ensureInit()
        val res = SearchInfo.getMoreItems(yt, handler(query), page)
        SearchPage(map(res.items), res.nextPage)
    }

    fun isYouTubeUrl(url: String): Boolean = try {
        ensureInit(); yt.streamLHFactory.acceptUrl(url)
    } catch (e: Exception) { false }

    /** Resolve a watch URL into playable stream URLs. [audioOnly] skips video to save data. */
    suspend fun resolve(url: String, audioOnly: Boolean = false): StreamItem = withContext(Dispatchers.IO) {
        ensureInit()
        val info = StreamInfo.getInfo(yt, url)

        val audio = info.audioStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
            .ifEmpty { info.audioStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP } }
            .sortedWith(compareBy({ if (it.format == MediaFormat.M4A) 0 else 1 }, { -it.averageBitrate }))
            .firstOrNull()

        fun height(r: String) = r.substringBefore('p').toIntOrNull() ?: 0
        val video = if (audioOnly) null else info.videoOnlyStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && height(it.getResolution()) <= 720 }
            .sortedWith(compareBy({ if (it.format == MediaFormat.MPEG_4 && (it.codec ?: "").startsWith("avc")) 0 else if (it.format == MediaFormat.MPEG_4) 1 else 2 }, { -height(it.getResolution()) }))
            .firstOrNull()

        val muxed = info.videoStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .maxByOrNull { height(it.getResolution()) }

        StreamItem(
            title = info.name,
            artist = info.uploaderName,
            thumbnail = info.thumbnails.maxByOrNull { it.width }?.url,
            videoUrl = video?.content,
            audioUrl = audio?.content,
            muxedUrl = if (audioOnly) null else muxed?.content,
            hlsUrl = info.hlsUrl?.takeIf { it.isNotBlank() },
            dashUrl = info.dashMpdUrl?.takeIf { it.isNotBlank() },
            pageUrl = url,
        )
    }
}
