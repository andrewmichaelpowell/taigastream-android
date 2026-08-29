//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.metadata

import okhttp3.HttpUrl
import xyz.andrewmichaelpowell.taigastream.metadata.providers.AbcRadioProvider
import xyz.andrewmichaelpowell.taigastream.metadata.providers.AudioAddictProvider
import xyz.andrewmichaelpowell.taigastream.metadata.providers.BbcRadioProvider
import xyz.andrewmichaelpowell.taigastream.metadata.providers.CeskyRozhlasProvider
import xyz.andrewmichaelpowell.taigastream.metadata.providers.DeutschlandfunkProvider
import xyz.andrewmichaelpowell.taigastream.metadata.providers.IcecastProvider
import xyz.andrewmichaelpowell.taigastream.metadata.providers.NrkProvider
import xyz.andrewmichaelpowell.taigastream.metadata.providers.RadioFranceProvider
import xyz.andrewmichaelpowell.taigastream.metadata.providers.RadioSwissProvider
import xyz.andrewmichaelpowell.taigastream.metadata.providers.RteRadioProvider
import xyz.andrewmichaelpowell.taigastream.metadata.providers.RtlRadioProvider
import xyz.andrewmichaelpowell.taigastream.metadata.providers.SomaFmProvider
import xyz.andrewmichaelpowell.taigastream.metadata.providers.StarFmProvider
import xyz.andrewmichaelpowell.taigastream.metadata.providers.SverigesRadioProvider
import xyz.andrewmichaelpowell.taigastream.metadata.providers.VirginRadioFranceProvider
import xyz.andrewmichaelpowell.taigastream.metadata.providers.VirginRadioItalyProvider
import xyz.andrewmichaelpowell.taigastream.metadata.providers.VirginRadioOmanProvider
import xyz.andrewmichaelpowell.taigastream.metadata.providers.VirginRadioRomaniaProvider
import xyz.andrewmichaelpowell.taigastream.metadata.providers.VrtRadioProvider
import xyz.andrewmichaelpowell.taigastream.metadata.providers.ZenoFmProvider

object MetadataProviders {
    val all: List<MetadataProvider> = listOf(
        AudioAddictProvider(),
        StarFmProvider(),
        RtlRadioProvider(),
        SomaFmProvider(),
        BbcRadioProvider(),
        NrkProvider(),
        RadioFranceProvider(),
        AbcRadioProvider(),
        RteRadioProvider(),
        RadioSwissProvider(),
        VirginRadioFranceProvider(),
        VirginRadioRomaniaProvider(),
        VirginRadioOmanProvider(),
        VirginRadioItalyProvider(),
        ZenoFmProvider(),
        VrtRadioProvider(),
        DeutschlandfunkProvider(),
        SverigesRadioProvider(),
        CeskyRozhlasProvider(),
        IcecastProvider(),
    )

    fun find(streamUrl: HttpUrl): MetadataProvider? = all.firstOrNull { it.matches(streamUrl) }
}
