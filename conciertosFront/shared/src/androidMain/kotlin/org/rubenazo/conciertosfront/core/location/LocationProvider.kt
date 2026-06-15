package org.rubenazo.conciertosfront.core.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import org.rubenazo.conciertosfront.core.map.LatLng
import kotlin.coroutines.resume

class AndroidLocationProvider(private val context: Context) : LocationPort {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    override suspend fun getLastLocation(): LatLng? = try {
        awaitLastLocation() ?: awaitCurrentLocation()
    } catch (_: Exception) {
        null
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitLastLocation(): LatLng? = suspendCancellableCoroutine { cont ->
        fusedClient.lastLocation
            .addOnSuccessListener { location ->
                cont.resume(location?.let { LatLng(it.latitude, it.longitude) })
            }
            .addOnFailureListener { cont.resume(null) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitCurrentLocation(): LatLng? = suspendCancellableCoroutine { cont ->
        val cts = CancellationTokenSource()
        fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
            .addOnSuccessListener { location ->
                cont.resume(location?.let { LatLng(it.latitude, it.longitude) })
            }
            .addOnFailureListener { cont.resume(null) }
        cont.invokeOnCancellation { cts.cancel() }
    }
}
