package org.rubenazo.conciertosfront

/**
 * Minimal platform abstraction for Kotlin Multiplatform.
 *
 * [getPlatform] is declared `expect` here in commonMain and implemented (`actual`) once per target
 * (androidMain, iosMain). This is the core KMP pattern: shared code depends on the abstraction,
 * each platform supplies its concrete value at compile time.
 */
interface Platform {
    val name: String
}

expect fun getPlatform(): Platform