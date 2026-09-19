package com.osgateway.gateway.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * Récupère une position GPS/réseau utilisable (last-known + refresh court).
 * getLastKnownLocation seul est souvent null tant qu’aucune app n’a localisé l’appareil.
 */
object LocationHelper {
    private const val TAG = "LocationHelper"
    private val cache = AtomicReference<Pair<Double, Double>?>(null)

    fun cached(): Pair<Double, Double>? = cache.get()

    fun hasPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    fun isLocationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(LocationManager::class.java) ?: return false
        return runCatching {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun lastKnown(context: Context): Pair<Double, Double>? {
        if (!hasPermission(context)) return cache.get()
        val lm = context.getSystemService(LocationManager::class.java) ?: return cache.get()
        val best = listOfNotNull(
            runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull(),
            runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull(),
            runCatching { lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER) }.getOrNull(),
            runCatching {
                val criteria = Criteria().apply { accuracy = Criteria.ACCURACY_COARSE }
                val provider = lm.getBestProvider(criteria, true)
                provider?.let { lm.getLastKnownLocation(it) }
            }.getOrNull(),
        ).maxByOrNull { it.time }
        val pair = best?.toPair()
        if (pair != null) cache.set(pair)
        return pair ?: cache.get()
    }

    /**
     * Essaie last-known, sinon demande une position courante (timeout ~8s).
     */
    @SuppressLint("MissingPermission")
    suspend fun resolve(context: Context): Pair<Double, Double>? {
        if (!hasPermission(context)) {
            Log.w(TAG, "Permission localisation absente")
            return null
        }
        if (!isLocationEnabled(context)) {
            Log.w(TAG, "Localisation désactivée dans les paramètres système")
            return lastKnown(context)
        }

        lastKnown(context)?.let { return it }

        val fresh = withTimeoutOrNull(8_000L) {
            fusedCurrent(context) ?: managerSingleUpdate(context)
        }
        if (fresh != null) {
            cache.set(fresh)
            return fresh
        }
        return cache.get()
    }

    @SuppressLint("MissingPermission")
    private suspend fun fusedCurrent(context: Context): Pair<Double, Double>? {
        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val last = runCatching { client.lastLocation.await() }.getOrNull()
            if (last != null && last.accuracy <= 500f) {
                return last.toPair()
            }
            val current = runCatching {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
            }.getOrNull()
            current?.toPair()
        } catch (e: Exception) {
            Log.w(TAG, "Fused location indisponible: ${e.message}")
            null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun managerSingleUpdate(context: Context): Pair<Double, Double>? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val provider = when {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return null
        }
        return suspendCancellableCoroutine { cont ->
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: Location) {
                    runCatching { lm.removeUpdates(this) }
                    if (cont.isActive) cont.resume(location.toPair())
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
            }
            cont.invokeOnCancellation {
                runCatching { lm.removeUpdates(listener) }
            }
            try {
                lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            } catch (e: Exception) {
                Log.w(TAG, "requestLocationUpdates failed: ${e.message}")
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    /** Variante Play Services (si fused ok) — unused helper kept for clarity. */
    @SuppressLint("MissingPermission")
    @Suppress("unused")
    private fun requestFusedCallback(context: Context, onResult: (Location?) -> Unit) {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 2_000L)
            .setMaxUpdates(1)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                client.removeLocationUpdates(this)
                onResult(result.lastLocation)
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    private fun Location.toPair(): Pair<Double, Double> = latitude to longitude
}
