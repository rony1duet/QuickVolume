package com.quick.volume

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.TileService
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

class MainActivity : AppCompatActivity() {

    private lateinit var audioManager: AudioManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var dndCard: MaterialCardView
    private lateinit var switchBubble: MaterialSwitch
    private lateinit var toggleRingerMode: MaterialButtonToggleGroup
    
    // Cached views for performance
    private val streamViews = mutableMapOf<Int, StreamViewCache>()

    private data class StreamViewCache(
        val slider: Slider,
        val icon: ImageView,
        val originalIconRes: Int
    )

    private val volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            refreshSliders()
            updateRingerToggleSelection()
        }
    }

    private val ringerModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.RINGER_MODE_CHANGED_ACTION || 
                intent.action == NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED) {
                updateRingerToggleSelection()
                updateDndCardVisibility()
                // Only request ringer tile update
                TileService.requestListeningState(this@MainActivity, ComponentName(this@MainActivity, QuickVolumeTileService::class.java))
            }
        }
    }

    private fun updateAllTiles() {
        val services = listOf(
            QuickVolumeTileService::class.java,
            BubbleTileService::class.java,
            DashboardTileService::class.java,
            VolumePanelTileService::class.java
        )
        services.forEach { 
            TileService.requestListeningState(this, ComponentName(this, it))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        dndCard = findViewById(R.id.dndCard)
        switchBubble = findViewById(R.id.switchBubble)
        toggleRingerMode = findViewById(R.id.toggleRingerMode)

        setupSlider(R.id.cardMedia, AudioManager.STREAM_MUSIC, R.string.title_media, R.drawable.ic_media)
        setupSlider(R.id.cardCall, AudioManager.STREAM_VOICE_CALL, R.string.title_call, R.drawable.ic_call)
        setupSlider(R.id.cardRing, AudioManager.STREAM_RING, R.string.title_ring, R.drawable.ic_ring)
        setupSlider(R.id.cardNotification, AudioManager.STREAM_NOTIFICATION, R.string.title_notification, R.drawable.ic_notification)
        setupSlider(R.id.cardAlarm, AudioManager.STREAM_ALARM, R.string.title_alarm, R.drawable.ic_alarm)

        toggleRingerMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnNormal -> setRingerMode(AudioManager.RINGER_MODE_NORMAL)
                    R.id.btnVibrate -> setRingerMode(AudioManager.RINGER_MODE_VIBRATE)
                    R.id.btnSilent -> setRingerMode(AudioManager.RINGER_MODE_SILENT)
                }
            }
        }

        findViewById<android.widget.Button>(R.id.btnGrantDnd).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }

        switchBubble.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (Settings.canDrawOverlays(this)) {
                    startBubbleService()
                    TileService.requestListeningState(this, ComponentName(this, BubbleTileService::class.java))
                } else {
                    switchBubble.isChecked = false
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        "package:$packageName".toUri(),
                    )
                    startActivity(intent)
                }
            } else {
                stopService(Intent(this, FloatingVolumeService::class.java))
                TileService.requestListeningState(this, ComponentName(this, BubbleTileService::class.java))
            }
        }
        
        updateRingerToggleSelection()
    }

    override fun onResume() {
        super.onResume()
        refreshSliders()
        updateDndCardVisibility()
        updateRingerToggleSelection()
        
        contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            volumeObserver
        )

        val filter = IntentFilter().apply {
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        registerReceiver(ringerModeReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        contentResolver.unregisterContentObserver(volumeObserver)
        unregisterReceiver(ringerModeReceiver)
    }

    private fun setupSlider(containerId: Int, stream: Int, labelRes: Int, iconRes: Int) {
        val container = findViewById<View>(containerId)
        val slider = container.findViewById<Slider>(R.id.streamSlider)
        val label = container.findViewById<TextView>(R.id.streamLabel)
        val icon = container.findViewById<ImageView>(R.id.streamIcon)

        label.setText(labelRes)
        icon.setImageResource(iconRes)
        
        streamViews[stream] = StreamViewCache(slider, icon, iconRes)

        val max = audioManager.getStreamMaxVolume(stream).toFloat()
        slider.valueFrom = 0f
        slider.valueTo = if (max > 0) max else 1f
        slider.stepSize = 1f
        
        val current = audioManager.getStreamVolume(stream).toFloat()
        slider.value = if (current <= slider.valueTo) current else slider.valueTo
        updateIconForValue(icon, iconRes, current)
        
        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                try {
                    audioManager.setStreamVolume(stream, value.toInt(), 0)
                    updateIconForValue(icon, iconRes, value)
                } catch (_: SecurityException) {
                }
            }
        }
    }

    private fun refreshSliders() {
        streamViews.forEach { (stream, cache) ->
            val current = audioManager.getStreamVolume(stream).toFloat()
            if (current <= cache.slider.valueTo) {
                cache.slider.value = current
            }
            updateIconForValue(cache.icon, cache.originalIconRes, current)
        }
    }

    private fun updateIconForValue(icon: ImageView, originalIconRes: Int, value: Float) {
        if (value == 0f) {
            icon.setImageResource(R.drawable.ic_volume_mute)
            icon.alpha = 0.6f
        } else {
            icon.setImageResource(originalIconRes)
            icon.alpha = 1.0f
        }
    }

    private fun updateRingerToggleSelection() {
        val dndActive = notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        if (dndActive) {
            toggleRingerMode.check(R.id.btnSilent)
        } else {
            val btnId = when (audioManager.ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> R.id.btnNormal
                AudioManager.RINGER_MODE_VIBRATE -> R.id.btnVibrate
                else -> R.id.btnSilent
            }
            toggleRingerMode.check(btnId)
        }
    }

    private fun setRingerMode(mode: Int) {
        if (!notificationManager.isNotificationPolicyAccessGranted &&
            (mode != AudioManager.RINGER_MODE_VIBRATE && mode != AudioManager.RINGER_MODE_NORMAL)
        ) {
            if (mode == AudioManager.RINGER_MODE_SILENT) {
                updateDndCardVisibility()
                return
            }
        }
        try {
            audioManager.ringerMode = mode
            RingerModeWidgetProvider.updateAllWidgets(this)
            updateAllTiles()
        } catch (_: Exception) {
            updateDndCardVisibility()
        }
    }

    private fun updateDndCardVisibility() {
        val needsAccess = !notificationManager.isNotificationPolicyAccessGranted
        dndCard.visibility = if (needsAccess) View.VISIBLE else View.GONE
    }

    private fun startBubbleService() {
        val intent = Intent(this, FloatingVolumeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
