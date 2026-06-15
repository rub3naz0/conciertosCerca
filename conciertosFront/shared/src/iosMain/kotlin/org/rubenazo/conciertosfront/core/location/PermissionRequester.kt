package org.rubenazo.conciertosfront.core.location

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberLocationPermissionState(): LocationPermissionState {
    val holder = remember { IosLocationPermissionHolder() }
    DisposableEffect(holder) {
        holder.start()
        onDispose { holder.stop() }
    }
    val status by holder.authorizationStatus
    return LocationPermissionState(
        status = status,
        launchPermissionRequest = holder::request,
    )
}

/**
 * Wraps a [CLLocationManager] and exposes the current authorization status
 * as Compose state, refreshed via the delegate whenever the user responds
 * to the system prompt.
 */
@OptIn(ExperimentalForeignApi::class)
private class IosLocationPermissionHolder {

    private val manager = CLLocationManager()

    // Initialize synchronously from the live OS status. CLLocationManager.authorizationStatus
    // is available immediately on construction, so the very first composition already reflects
    // the real authorization — without this, the holder reported NOT_DETERMINED until start()
    // ran (after composition), and checkPermissionState (in LaunchedEffect(Unit)) captured that
    // stale value, re-showing the rationale even when the user had granted persistently.
    val authorizationStatus = mutableStateOf(manager.authorizationStatus.toAuthorizationStatus())

    private val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            authorizationStatus.value = manager.authorizationStatus.toAuthorizationStatus()
        }
    }

    fun start() {
        manager.delegate = delegate
        authorizationStatus.value = manager.authorizationStatus.toAuthorizationStatus()
    }

    fun request() {
        manager.requestWhenInUseAuthorization()
    }

    fun stop() {
        manager.delegate = null
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun CLAuthorizationStatus.toAuthorizationStatus(): LocationAuthorizationStatus =
    when (this) {
        kCLAuthorizationStatusAuthorizedWhenInUse,
        kCLAuthorizationStatusAuthorizedAlways -> LocationAuthorizationStatus.GRANTED
        kCLAuthorizationStatusDenied,
        kCLAuthorizationStatusRestricted -> LocationAuthorizationStatus.DENIED
        else -> LocationAuthorizationStatus.NOT_DETERMINED
    }
