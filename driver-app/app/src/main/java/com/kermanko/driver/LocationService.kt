package com.kermanko.driver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.database.FirebaseDatabase

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var lineId: String = "unknown"

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lineId = intent?.getStringExtra("lineId") ?: "unknown"
        startForeground(1, buildNotification())
        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 10000L
        ).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location: Location = result.lastLocation ?: return
                sendLocationToFirebase(location)
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest, locationCallback, Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            // مجوز داده نشده - نباید عملا به اینجا برسیم چون تو اکتیویتی چک شده
        }
    }

    // ساختار داده دقیقاً هم‌راستا با سایت: drivers/{lineId}/lat,lng,lastUpdate
    private fun sendLocationToFirebase(location: Location) {
        val db = FirebaseDatabase.getInstance().reference
        db.child("drivers").child(lineId).updateChildren(
            mapOf(
                "lat" to location.latitude,
                "lng" to location.longitude,
                "lastUpdate" to System.currentTimeMillis()
            )
        )
        // آخرین فعالیت راننده رو هم برای پنل ادمین به‌روز می‌کنیم
        db.child("driver_profiles").child(lineId).child("lastActive")
            .setValue(System.currentTimeMillis())
    }

    private fun buildNotification(): android.app.Notification {
        val channelId = "location_tracking_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "ردیابی مسیر", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("کرمانکو فعاله")
            .setContentText("موقعیت مسیر در حال ارسال است")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
