package org.rubenazo.conciertosfront.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
actual fun rememberLocationPermissionState(): LocationPermissionState {
    val context = LocalContext.current
    val prefs = remember { AndroidPermissionPrefs(context) }

    var status by remember { mutableStateOf(computeStatus(context, prefs)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (granted) {
            // Covers BOTH "While using the app" (persistent) and "Only this time" (one-time).
            // We do NOT persist anything on grant: a one-time grant is auto-revoked on the next
            // cold start, and because no denial was recorded it falls back to NOT_DETERMINED and
            // re-prompts — exactly the desired behavior.
            prefs.clearDenied()
            status = LocationAuthorizationStatus.GRANTED
        } else {
            // User explicitly declined the system dialog → remember it so we never nag again.
            prefs.markDenied()
            status = LocationAuthorizationStatus.DENIED
        }
    }

    return LocationPermissionState(
        status = status,
        launchPermissionRequest = {
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        },
    )
}

/**
 * Android tri-state mapping read live at cold start:
 *  - GRANTED         → fine or coarse location currently held (incl. an active one-time grant).
 *  - DENIED          → the user explicitly declined a previous system prompt (persisted flag).
 *  - NOT_DETERMINED  → never decided, OR a one-time grant was auto-revoked → re-prompt.
 *
 * The persisted flag records ONLY explicit denial, never "we asked once" — that distinction is
 * what lets "Only this time" re-prompt while "Don't allow" and persistent grants do not.
 */
private fun computeStatus(context: Context, prefs: PermissionPrefsPort): LocationAuthorizationStatus {
    val granted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    return when {
        granted -> LocationAuthorizationStatus.GRANTED
        prefs.wasDenied() -> LocationAuthorizationStatus.DENIED
        else -> LocationAuthorizationStatus.NOT_DETERMINED
    }
}
