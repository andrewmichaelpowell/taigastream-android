//  Taiga Stream (Android)
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.podcasts

import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong
import xyz.andrewmichaelpowell.taigastream.R

/** Episode durations, time left and dates, in the listener's language. */
object PodcastFormat {
    /** "1 hr, 5 min" style, at least a minute. */
    fun duration(seconds: Double): String {
        val totalMinutes = (maxOf(seconds, 60.0) / 60).roundToLong()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val format = MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.SHORT)
        val measures = buildList {
            if (hours > 0) add(Measure(hours, MeasureUnit.HOUR))
            if (minutes > 0 || hours == 0L) add(Measure(minutes, MeasureUnit.MINUTE))
        }
        return format.formatMeasures(*measures.toTypedArray())
    }

    fun remaining(context: Context, position: Double, duration: Double?): String? {
        if (duration == null || duration <= 0 || position <= 0) return null
        val left = duration - position
        if (left <= 0) return null
        return context.getString(R.string.time_left, duration(left))
    }

    fun date(millis: Long?): String? =
        millis?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) }
}
