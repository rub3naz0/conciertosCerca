package org.rubenazo.conciertosfront.core.config

/**
 * Dev backend override for Android. Keep null for production; set temporarily to a
 * local backend (e.g. "http://10.0.2.2:8080" from the emulator) and never commit it.
 */
actual val platformDevBaseUrl: String? = null
