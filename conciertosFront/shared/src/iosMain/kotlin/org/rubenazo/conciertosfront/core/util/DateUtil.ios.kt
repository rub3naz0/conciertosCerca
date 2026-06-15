package org.rubenazo.conciertosfront.core.util

import androidx.compose.material3.CalendarLocale
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.timeZoneWithName

actual fun todayIsoDate(): String {
    val formatter = NSDateFormatter()
    formatter.dateFormat = "yyyy-MM-dd"
    // UTC-consistent with epochMillisToIsoDate; near local midnight "today" follows UTC, not local time
    formatter.timeZone = NSTimeZone.timeZoneWithName("UTC")!!
    return formatter.stringFromDate(NSDate())
}

actual fun epochMillisToIsoDate(millis: Long): String {
    val secondsSinceReference = millis / 1000.0 - 978307200.0
    val date = NSDate(timeIntervalSinceReferenceDate = secondsSinceReference)
    val formatter = NSDateFormatter()
    formatter.dateFormat = "yyyy-MM-dd"
    formatter.timeZone = NSTimeZone.timeZoneWithName("UTC")!!
    return formatter.stringFromDate(date)
}

actual val SpanishLocale: CalendarLocale = NSLocale("es_ES")
