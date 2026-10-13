package com.example.drinksync.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.drinksync.HydrationSnapshot
import com.example.drinksync.HydrationStore
import com.example.drinksync.R

private const val TAG = "DrinkSyncApp"

object HydrationNotifier {
    const val CHANNEL_ID = "hydration_reminder"
    private const val NOTIFICATION_ID = 2
    val MILESTONES = listOf(25, 50, 100)

    /** Milestones reached today that have not been announced yet, in ascending order. */
    fun pendingMilestones(state: HydrationSnapshot): List<Int> {
        if (!state.notifications) return emptyList()
        val goal = state.dailyGoal
        if (goal <= 0) return emptyList()
        val progressPercent = (state.currentIntake.toFloat() / goal.toFloat() * 100).toInt()
        return MILESTONES.filter { progressPercent >= it && it !in state.notificationTriggers }
    }

    fun message(milestone: Int): String? = when (milestone) {
        25 -> "Great job! You're 25% of the way to your goal."
        50 -> "You're halfway there! Keep hydrating!"
        100 -> "Congratulations! You've reached your hydration goal for today! \uD83C\uDF89"
        else -> null
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Hydration Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Channel for water intake reminders and progress"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Post any newly reached milestones and record them so each fires once per day. */
    fun notifyMilestones(context: Context, store: HydrationStore) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        for (milestone in pendingMilestones(store.snapshot)) {
            val text = message(milestone) ?: continue
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Hydration Update!")
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException sending notification: ${e.message}", e)
            }
            store.markNotificationTrigger(milestone)
        }
    }
}
