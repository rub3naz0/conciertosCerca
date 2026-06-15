package org.rubenazo.conciertosfront.core.location

import androidx.compose.runtime.Composable

enum class LocationAuthorizationStatus {
    GRANTED,
    NOT_DETERMINED,
    DENIED,
}

class LocationPermissionState(
    val status: LocationAuthorizationStatus,
    val launchPermissionRequest: () -> Unit,
)

@Composable
expect fun rememberLocationPermissionState(): LocationPermissionState
