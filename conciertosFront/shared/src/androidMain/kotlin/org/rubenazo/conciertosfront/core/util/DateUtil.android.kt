package org.rubenazo.conciertosfront.core.util

import androidx.compose.material3.CalendarLocale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

// UTC-consistent with epochMillisToIsoDate; near local midnight "today" follows UTC, not local time
actual fun todayIsoDate(): String = LocalDate.now(ZoneOffset.UTC).toString()

actual fun epochMillisToIsoDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()

actual val SpanishLocale: CalendarLocale = Locale("es", "ES")
