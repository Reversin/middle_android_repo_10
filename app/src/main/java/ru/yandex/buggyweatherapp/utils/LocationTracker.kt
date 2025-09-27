package ru.yandex.buggyweatherapp.utils

import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.CopyOnWriteArrayList

class LocationTracker private constructor(
    private val context: Context
) {
    
    companion object {
        @Volatile
        private var instance: LocationTracker? = null
        
        fun getInstance(context: Context): LocationTracker {
            return instance ?: synchronized(this) {
                instance ?: LocationTracker(context).also { instance = it }
            }
        }
    }

    @Volatile private var isTracking = false
    private var currentProvider: String? = null


    private val appCtx = context.applicationContext

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    
    
    private val listeners = CopyOnWriteArrayList<(ru.yandex.buggyweatherapp.model.Location) -> Unit>()
    
    
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            
            val newLocation = ru.yandex.buggyweatherapp.model.Location(
                latitude = location.latitude,
                longitude = location.longitude
            )
            
            
            notifyListeners(newLocation)
        }
        
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        
        override fun onProviderEnabled(provider: String) {}
        
        override fun onProviderDisabled(provider: String) {}
    }


    fun startTracking() {
        if (isTracking) return // уже запущено
        if (!hasLocationPermission()) {
            Log.w("LocationTracker", "No location permission")
            return
        }
        val provider = pickProvider() ?: run {
            Log.w("LocationTracker", "No provider enabled")
            return
        }
        try {
            locationManager.requestLocationUpdates(
                provider,
                5_000L,
                10f,
                locationListener
            )
            currentProvider = provider
            isTracking = true
        } catch (se: SecurityException) {
            Log.e("LocationTracker", "Permission denied", se)
        } catch (e: Exception) {
            Log.e("LocationTracker", "Error starting tracking", e)
        }
    }

    fun stopTracking() {
        if (!isTracking) return
        try {
            locationManager.removeUpdates(locationListener)
        } catch (e: Exception) {
            Log.w("LocationTracker", "stopTracking error", e)
        } finally {
            isTracking = false
            currentProvider = null
        }
    }

    fun addListener(listener: (ru.yandex.buggyweatherapp.model.Location) -> Unit) {
        listeners.add(listener)
    }
    
    private fun notifyListeners(location: ru.yandex.buggyweatherapp.model.Location) {
        
        Handler(Looper.getMainLooper()).post {
            for (listener in listeners) {
                listener(location)
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(appCtx, android.Manifest.permission.ACCESS_FINE_LOCATION) == PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(appCtx, android.Manifest.permission.ACCESS_FINE_LOCATION) == PERMISSION_GRANTED
        return fine || coarse
    }

    private fun pickProvider(): String? = when {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
    }
}