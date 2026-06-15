package org.rubenazo.conciertosfront.core.location

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import org.rubenazo.conciertosfront.core.map.LatLng
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.Foundation.NSError
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

/**
 * CoreLocation-backed [LocationPort]. Mirrors the Android provider: first tries
 * the cached `location`, then falls back to a one-shot `requestLocation()`.
 * Any failure (denied authorization, services off) resolves to null so the
 * ViewModel can apply the Madrid fallback (SCN-LOC-006).
 */
@OptIn(ExperimentalForeignApi::class)
class IosLocationProvider : LocationPort {

    // CLLocationManager keeps only a weak reference to its delegate, so we
    // retain both for the lifetime of an in-flight request.
    private var manager: CLLocationManager? = null
    private var delegate: CLLocationManagerDelegateProtocol? = null

    override suspend fun getLastLocation(): LatLng? =
        suspendCancellableCoroutine { cont ->
            // CLLocationManager must be created and used on a thread with an
            // active run loop — the main queue.
            dispatch_async(dispatch_get_main_queue()) {
                val mgr = CLLocationManager()
                manager = mgr

                val cached = mgr.location
                if (cached != null) {
                    cleanup()
                    cont.resume(cached.toLatLng())
                    return@dispatch_async
                }

                val oneShot = OneShotLocationDelegate { result ->
                    cleanup()
                    if (cont.isActive) cont.resume(result)
                }
                delegate = oneShot
                mgr.delegate = oneShot
                mgr.requestLocation()
            }

            cont.invokeOnCancellation { cleanup() }
        }

    private fun cleanup() {
        manager?.delegate = null
        manager = null
        delegate = null
    }
}

@OptIn(ExperimentalForeignApi::class)
private class OneShotLocationDelegate(
    private val onResult: (LatLng?) -> Unit,
) : NSObject(), CLLocationManagerDelegateProtocol {

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        val location = didUpdateLocations.lastOrNull() as? CLLocation
        onResult(location?.toLatLng())
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        onResult(null)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun CLLocation.toLatLng(): LatLng =
    coordinate.useContents { LatLng(latitude, longitude) }
