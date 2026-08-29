//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.metadata

import okhttp3.HttpUrl

data class MetadataResult(
    val artist: String,
    val title: String,
    val artworkUrl: String? = null,
)

interface MetadataProvider {
    fun matches(streamUrl: HttpUrl): Boolean
    fun poll(streamUrl: HttpUrl, onResult: (MetadataResult) -> Unit)
    val pollInterval: Long?

    fun cancel() {}
}
