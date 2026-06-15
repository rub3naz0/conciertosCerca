package org.rubenazo.conciertosfront.core.util

fun interface DateProvider {
    fun today(): String
}

class DefaultDateProvider : DateProvider {
    override fun today(): String = todayIsoDate()
}
