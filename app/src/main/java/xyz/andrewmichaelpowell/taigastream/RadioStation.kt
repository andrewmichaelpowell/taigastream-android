//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream

import java.util.UUID

data class RadioStation(
    val id: UUID = UUID.randomUUID(),
    val url: String,
    val name: String,
    val faviconUrl: String,
) {
    companion object {
        val EMPTY = RadioStation(url = "", name = "", faviconUrl = "")
        const val SLOT_COUNT = 32
    }
}
