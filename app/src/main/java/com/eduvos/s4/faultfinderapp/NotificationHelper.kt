package com.eduvos.s4.faultfinderapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    private const val CHANNEL_ID = "fault_status_updates"
    private const val CHANNEL_NAME = "Fault Status Updates"

    // Tracks last known statuses across Activity recreations during the session
    private val knownStatuses = mutableMapOf<String, String>()
    private var isInitialized = false

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when fault reports are updated or logged nearby"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Evaluates incoming fault list against previously known statuses and user Settings
     * ("status_alerts", "notify_all_nearby", and "notify_max_distance_km").
     */
    fun checkAndNotifyChanges(
        context: Context,
        faults: List<Fault>,
        currentUid: String?,
        userLat: Double,
        userLon: Double
    ) {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val alertsEnabled = prefs.getBoolean("status_alerts", true)
        val notifyAllNearby = prefs.getBoolean("notify_all_nearby", false)
        val maxDistanceMeters = prefs.getInt("notify_max_distance_km", 5) * 1000f

        if (!isInitialized) {
            faults.forEach { knownStatuses[it.id] = it.status }
            isInitialized = true
            return
        }

        for (fault in faults) {
            val oldStatus = knownStatuses[fault.id]
            knownStatuses[fault.id] = fault.status

            // Determine if this fault is a brand-new report or had its status updated
            val isNewFault = (oldStatus == null)
            val isStatusChanged = (oldStatus != null && !oldStatus.equals(fault.status, ignoreCase = true))

            if (!alertsEnabled || (!isNewFault && !isStatusChanged)) continue

            val isMine = currentUid != null && fault.userId == currentUid
            val dist = FloatArray(1)
            android.location.Location.distanceBetween(
                userLat, userLon, fault.latitude, fault.longitude, dist
            )
            val isWithinRadius = dist[0] <= maxDistanceMeters

            val shouldNotify = if (notifyAllNearby) {
                isMine || isWithinRadius
            } else {
                isMine
            }

            if (shouldNotify) {
                val displayStatus = if (isNewFault) "New Nearby Report (${fault.status})" else fault.status
                sendStatusNotification(
                    context = context,
                    ref = fault.ref.ifBlank { fault.id.take(6) },
                    title = fault.title,
                    newStatus = displayStatus
                )
            }
        }
    }

    fun sendStatusNotification(
        context: Context,
        ref: String,
        title: String,
        newStatus: String
    ) {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("status_alerts", true)) return

        createNotificationChannel(context)

        val intent = Intent(context, MyReportsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            ref.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusEmoji = when (newStatus.trim().lowercase()) {
            "resolved", "fixed" -> "✅"
            "in progress" -> "⚙️"
            else -> "📢"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$statusEmoji Fault Alert: $newStatus")
            .setContentText("Report $ref ($title) is now '$newStatus'.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(ref.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission not granted on Android 13+
        }
    }
}