package com.quick.volume

import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class RingerModeWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_SET_NORMAL = "com.quick.volume.ACTION_SET_NORMAL"
        const val ACTION_SET_VIBRATE = "com.quick.volume.ACTION_SET_VIBRATE"
        const val ACTION_TOGGLE_DND = "com.quick.volume.ACTION_TOGGLE_DND"
        const val ACTION_VOLUME_UP = "com.quick.volume.ACTION_VOLUME_UP"
        const val ACTION_VOLUME_DOWN = "com.quick.volume.ACTION_VOLUME_DOWN"

        private val executor = Executors.newSingleThreadExecutor()

        /**
         * Triggers an update for all active QuickVolume widgets.
         */
        fun updateAllWidgets(context: Context) {
            val appContext = context.applicationContext
            executor.execute {
                val appWidgetManager = AppWidgetManager.getInstance(appContext)
                val componentName = ComponentName(appContext, RingerModeWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                if (appWidgetIds.isEmpty()) return@execute

                val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
                val ringerMode = audioManager.ringerMode
                val dndActive = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
                
                for (id in appWidgetIds) {
                    val views = RemoteViews(appContext.packageName, R.layout.widget_ringer_mode)
                    updateViews(appContext, views, ringerMode, dndActive)
                    appWidgetManager.updateAppWidget(id, views)
                }
            }
        }

        private fun updateViews(
            context: Context,
            views: RemoteViews,
            ringerMode: Int,
            dndActive: Boolean
        ) {
            // Update backgrounds
            views.setInt(R.id.widgetBtnNormal, "setBackgroundResource", 
                if (!dndActive && ringerMode == AudioManager.RINGER_MODE_NORMAL) R.drawable.widget_button_active_bg else R.drawable.widget_button_bg)
            views.setInt(R.id.widgetBtnVibrate, "setBackgroundResource", 
                if (!dndActive && ringerMode == AudioManager.RINGER_MODE_VIBRATE) R.drawable.widget_button_active_bg else R.drawable.widget_button_bg)
            views.setInt(R.id.widgetBtnDnd, "setBackgroundResource", 
                if (dndActive) R.drawable.widget_button_active_bg else R.drawable.widget_button_bg)
            
            // Update icon tints
            val colorOnSurface = ContextCompat.getColor(context, R.color.onSurface)
            val colorOnPrimary = ContextCompat.getColor(context, R.color.onPrimary)
            
            views.setInt(R.id.widgetBtnNormal, "setColorFilter", if (!dndActive && ringerMode == AudioManager.RINGER_MODE_NORMAL) colorOnPrimary else colorOnSurface)
            views.setInt(R.id.widgetBtnVibrate, "setColorFilter", if (!dndActive && ringerMode == AudioManager.RINGER_MODE_VIBRATE) colorOnPrimary else colorOnSurface)
            views.setInt(R.id.widgetBtnDnd, "setColorFilter", if (dndActive) colorOnPrimary else colorOnSurface)

            // Update volume icon tints
            views.setInt(R.id.widgetBtnVolUp, "setColorFilter", colorOnSurface)
            views.setInt(R.id.widgetBtnVolDown, "setColorFilter", colorOnSurface)

            views.setOnClickPendingIntent(R.id.widgetBtnNormal, actionPendingIntent(context, ACTION_SET_NORMAL))
            views.setOnClickPendingIntent(R.id.widgetBtnVibrate, actionPendingIntent(context, ACTION_SET_VIBRATE))
            views.setOnClickPendingIntent(R.id.widgetBtnDnd, actionPendingIntent(context, ACTION_TOGGLE_DND))
            views.setOnClickPendingIntent(R.id.widgetBtnVolUp, actionPendingIntent(context, ACTION_VOLUME_UP))
            views.setOnClickPendingIntent(R.id.widgetBtnVolDown, actionPendingIntent(context, ACTION_VOLUME_DOWN))
        }

        private fun actionPendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, RingerModeWidgetProvider::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(context, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val ringerMode = audioManager.ringerMode
        val dndActive = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_ringer_mode)
            updateViews(context, views, ringerMode, dndActive)
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, RingerModeWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        if (intent.action == AudioManager.RINGER_MODE_CHANGED_ACTION || 
            intent.action == NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED) {
            
            val ringerMode = audioManager.ringerMode
            val dndActive = notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
            
            for (id in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_ringer_mode)
                updateViews(context, views, ringerMode, dndActive)
                appWidgetManager.updateAppWidget(id, views)
            }
            return
        }

        val needsDndAccess = !notificationManager.isNotificationPolicyAccessGranted

        when (intent.action) {
            ACTION_SET_NORMAL -> {
                if (needsDndAccess) {
                    notifyNeedsPermission(context)
                } else {
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    updateAllWidgets(context)
                }
            }
            ACTION_SET_VIBRATE -> {
                if (needsDndAccess) {
                    notifyNeedsPermission(context)
                } else {
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    updateAllWidgets(context)
                }
            }
            ACTION_TOGGLE_DND -> {
                if (needsDndAccess) {
                    notifyNeedsPermission(context)
                } else {
                    val isDnd = notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
                    notificationManager.setInterruptionFilter(if (isDnd) 
                        NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_NONE)
                    updateAllWidgets(context)
                }
            }
            ACTION_VOLUME_UP -> {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE,
                    AudioManager.FLAG_SHOW_UI
                )
            }
            ACTION_VOLUME_DOWN -> {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI
                )
            }
        }
    }

    private fun notifyNeedsPermission(context: Context) {
        Toast.makeText(
            context,
            "Open QuickVolume once to grant Do Not Disturb access",
            Toast.LENGTH_LONG
        ).show()
    }
}
