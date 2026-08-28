//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.metadata

import okhttp3.HttpUrl

/**
 * Result of a metadata poll. [artworkUrl], when present, is resolved and applied centrally by
 * [xyz.andrewmichaelpowell.taigastream.metadata.ArtworkFetcher] rather than by each provider
 * downloading/decoding the image itself — the iOS providers each repeat that download-decode-
 * dispatch dance inline (e.g. Taiga Stream Widget/WidgetView.swift:270-289); centralizing it here
 * removes that duplication without changing behavior.
 */
data class MetadataResult(
    val artist: String,
    val title: String,
    val artworkUrl: String? = null,
)

/**
 * Ports the `MetadataProvider` protocol from the iOS app
 * (Taiga Stream Widget/WidgetView.swift:11-18). Each provider recognizes a family of stream URLs
 * and knows how to fetch that network's "now playing" artist/title.
 *
 * [pollInterval] is in seconds; `null` means the provider drives its own polling (a live
 * WebSocket/SSE connection, or a self-rescheduling one-shot poll) rather than being polled on a
 * fixed timer by the caller.
 */
interface MetadataProvider {
    fun matches(streamUrl: HttpUrl): Boolean
    fun poll(streamUrl: HttpUrl, onResult: (MetadataResult) -> Unit)
    val pollInterval: Long?

    /**
     * Stops any live connection or self-rescheduling timer this provider started (WebSocket, SSE,
     * `delayToRefresh`-driven re-poll). Called by the playback service when switching streams or
     * stopping playback. The iOS providers instead check `StreamInfo.shared.currentStreamUrl ==
     * streamUrl` inside each async callback to decide whether to keep going
     * (Taiga Stream Widget/WidgetView.swift:178, 744) — an explicit `cancel()` the service drives
     * is the same idea made explicit instead of implicit. No-op for one-shot/timer-polled providers.
     */
    fun cancel() {}
}
