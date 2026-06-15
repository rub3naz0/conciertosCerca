package org.rubenazo.conciertosfront

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform