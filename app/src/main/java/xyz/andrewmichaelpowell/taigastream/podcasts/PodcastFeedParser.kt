//  Taiga Stream (Android)
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.podcasts

import android.util.Xml
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import xyz.andrewmichaelpowell.taigastream.metadata.NetworkClient
import kotlin.coroutines.resume

/** Loads a podcast's RSS feed: its title, artwork and episodes. */
object PodcastFeedParser {
    suspend fun fetch(feedUrl: String): PodcastFeed? {
        val body: String = suspendCancellableCoroutine { continuation ->
            val request = runCatching { NetworkClient.get(feedUrl) }.getOrNull()
            if (request == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            NetworkClient.fetchBody(
                request,
                onFailure = { if (continuation.isActive) continuation.resume(null) },
            ) { if (continuation.isActive) continuation.resume(it) }
        } ?: return null
        return withContext(Dispatchers.Default) { runCatching { parse(body) }.getOrNull() }
    }

    fun parse(xml: String): PodcastFeed {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        var channelTitle = ""
        var channelArtwork = ""
        val episodes = mutableListOf<PodcastEpisode>()

        var inItem = false
        var inChannelImage = false
        var title = ""
        var audioUrl = ""
        var guid = ""
        var pubDate: Long? = null
        var duration: Double? = null
        var itemArtwork: String? = null
        val text = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    text.setLength(0)
                    when (parser.name) {
                        "item" -> {
                            inItem = true
                            title = ""; audioUrl = ""; guid = ""
                            pubDate = null; duration = null; itemArtwork = null
                        }
                        "image" -> if (!inItem) inChannelImage = true
                        "enclosure" -> if (inItem) {
                            parser.getAttributeValue(null, "url")?.let { audioUrl = it.trim() }
                        }
                        "itunes:image" -> {
                            val href = parser.getAttributeValue(null, "href")?.trim().orEmpty()
                            if (inItem) {
                                if (href.isNotEmpty()) itemArtwork = href
                            } else if (href.isNotEmpty()) {
                                channelArtwork = href
                            }
                        }
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> text.append(parser.text)
                XmlPullParser.END_TAG -> {
                    val value = text.toString().trim()
                    when (parser.name) {
                        "title" -> when {
                            inItem -> title = value
                            !inChannelImage && channelTitle.isEmpty() -> channelTitle = value
                        }
                        "guid" -> if (inItem) guid = value
                        "pubDate" -> if (inItem) pubDate = parseDate(value)
                        "itunes:duration" -> if (inItem) duration = parseDuration(value)
                        "url" -> if (inChannelImage && channelArtwork.isEmpty()) channelArtwork = value
                        "image" -> inChannelImage = false
                        "item" -> {
                            inItem = false
                            if (audioUrl.isNotEmpty()) {
                                episodes += PodcastEpisode(
                                    id = guid.ifEmpty { audioUrl },
                                    title = title,
                                    audioUrl = audioUrl,
                                    publishedAt = pubDate,
                                    duration = duration,
                                    artworkUrl = itemArtwork,
                                )
                            }
                        }
                    }
                    text.setLength(0)
                }
            }
            event = parser.next()
        }
        return PodcastFeed(
            title = channelTitle,
            artworkUrl = channelArtwork,
            episodes = episodes.sortedByDescending { it.publishedAt ?: Long.MIN_VALUE },
        )
    }

    private val dateFormats = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "EEE, d MMM yyyy HH:mm:ss Z",
        "dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm Z",
    )

    private fun parseDate(value: String): Long? {
        for (pattern in dateFormats) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply { isLenient = true }.parse(value)
            }.getOrNull()
            if (parsed != null) return parsed.time
        }
        return null
    }

    private fun parseDuration(value: String): Double? {
        val parts = value.split(":").map { it.trim().toDoubleOrNull() ?: return null }
        val seconds = parts.fold(0.0) { total, part -> total * 60 + part }
        return seconds.takeIf { it > 0 }
    }
}
