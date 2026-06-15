package org.rubenazo.conciertosfront.core.location

import android.content.Context
import android.content.SharedPreferences

/**
 * Android-only persistence of the user's EXPLICIT location denial.
 *
 * iOS does not need this — CLAuthorizationStatus already distinguishes notDetermined / granted /
 * denied. Android cannot tell "permanently denied" from "granted once ('Only this time'), then
 * auto-revoked" at cold start, so we record only the one signal that matters: did the user
 * explicitly decline the system dialog? A one-time grant returns granted=true to the launcher and
 * is therefore NEVER recorded as denied, so it re-prompts on the next launch — which is the
 * behavior the user asked for.
 */
interface PermissionPrefsPort {
    fun wasDenied(): Boolean
    fun markDenied()
    fun clearDenied()
}

private const val PREFS_NAME = "conciertos_prefs"
private const val KEY_LOCATION_DENIED = "location_permission_denied"

class AndroidPermissionPrefs(private val context: Context) : PermissionPrefsPort {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun wasDenied(): Boolean =
        prefs.getBoolean(KEY_LOCATION_DENIED, false)

    override fun markDenied() {
        prefs.edit().putBoolean(KEY_LOCATION_DENIED, true).apply()
    }

    override fun clearDenied() {
        prefs.edit().putBoolean(KEY_LOCATION_DENIED, false).apply()
    }
}
