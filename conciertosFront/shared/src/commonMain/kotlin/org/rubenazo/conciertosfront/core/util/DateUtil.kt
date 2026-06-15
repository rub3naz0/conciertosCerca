package org.rubenazo.conciertosfront.core.util

import androidx.compose.material3.CalendarLocale

expect fun todayIsoDate(): String

expect fun epochMillisToIsoDate(millis: Long): String

expect val SpanishLocale: CalendarLocale
