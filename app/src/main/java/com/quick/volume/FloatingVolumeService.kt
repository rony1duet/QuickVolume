package com.quick.volume

import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.TileService
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlin.math.abs

/**
 * Modern, compact floating volume bubble with smooth ringer sync and notch notification.
 */
class FloatingVolumeService : Service() {

    companion object {
        @Volatile
        var isRunning = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var notchView: View? = null
    private lateinit var audioManager: AudioManager
    private lateinit var notificationManager: NotificationManager
    private var ringerIcon: ImageView? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private val hideNotchRunnable = Runnable { hideNotch() }

    private val channelId = "volumefix_bubble_channel"
    private val notificationId = 1001

    private val ringerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.RINGER_MODE_CHANGED_ACTION || 
                intent.action == NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED) {
                updateRingerIcon()
            }
        }
    }

    private val volumeObserver = object : ContentObserver(handler) {
        private var lastVolume = -1
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (current != lastVolume) {
                lastVolume = current
                showNotchNotification()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(notificationId, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(notificationId, buildNotification())
        }
        
        addBubble()
        initNotchView()

        val filter = IntentFilter().apply {
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        registerReceiver(ringerReceiver, filter)
        
        contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            volumeObserver
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        unregisterReceiver(ringerReceiver)
        contentResolver.unregisterContentObserver(volumeObserver)
        
        bubbleView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        notchView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        
        bubbleView = null
        notchView = null
        handler.removeCallbacks(hideNotchRunnable)
    }

    private fun buildNotification(): android.app.Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Floating volume bubble",
                NotificationManager.IMPORTANCE_MIN
            )
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("QuickVolume bubble active")
            .setContentText("Tap to open Dashboard")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun addBubble() {
        val themedContext = ContextThemeWrapper(this, R.style.Theme_QuickVolume)
        
        val card = MaterialCardView(themedContext).apply {
            cardElevation = 16f
            radius = 32.dpToPx().toFloat() // Pill shape
            setCardBackgroundColor(ContextCompat.getColor(themedContext, R.color.secondaryContainer))
            alpha = 0.95f
            strokeWidth = 0
        }

        val layout = LinearLayout(themedContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(4.dpToPx(), 6.dpToPx(), 4.dpToPx(), 6.dpToPx())
        }

        val volUp = ImageView(themedContext).apply {
            setImageResource(R.drawable.ic_volume_up)
            setPadding(10.dpToPx(), 8.dpToPx(), 10.dpToPx(), 8.dpToPx())
            layoutParams = LinearLayout.LayoutParams(48.dpToPx(), 40.dpToPx())
            setColorFilter(ContextCompat.getColor(themedContext, R.color.onSecondaryContainer))
        }

        ringerIcon = ImageView(themedContext).apply {
            setPadding(10.dpToPx(), 8.dpToPx(), 10.dpToPx(), 8.dpToPx())
            layoutParams = LinearLayout.LayoutParams(48.dpToPx(), 40.dpToPx())
            setColorFilter(ContextCompat.getColor(themedContext, R.color.onSecondaryContainer))
        }
        updateRingerIcon()

        val volDown = ImageView(themedContext).apply {
            setImageResource(R.drawable.ic_volume_down)
            setPadding(10.dpToPx(), 8.dpToPx(), 10.dpToPx(), 8.dpToPx())
            layoutParams = LinearLayout.LayoutParams(48.dpToPx(), 40.dpToPx())
            setColorFilter(ContextCompat.getColor(themedContext, R.color.onSecondaryContainer))
        }

        layout.addView(volUp)
        layout.addView(ringerIcon)
        layout.addView(volDown)
        card.addView(layout)

        val layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 20
        params.y = 500

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDrag = false
        var startTime = 0L

        card.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDrag = false
                        startTime = System.currentTimeMillis()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (abs(dx) > 10 || abs(dy) > 10) {
                            isDrag = true
                        }
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager.updateViewLayout(card, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val duration = System.currentTimeMillis() - startTime
                        if (!isDrag) {
                            if (duration > 500) {
                                // Long press: open app
                                val intent = Intent(this@FloatingVolumeService, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                startActivity(intent)
                            } else {
                                // Short press: 3 sections
                                val height = card.height
                                val y = event.y
                                if (y < height / 3) {
                                    // Top third: Vol Up
                                    audioManager.adjustStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        AudioManager.ADJUST_RAISE,
                                        AudioManager.FLAG_SHOW_UI
                                    )
                                } else if (y < (height * 2) / 3) {
                                    // Middle third: Ringer Cycle
                                    cycleRingerMode()
                                } else {
                                    // Bottom third: Vol Down
                                    audioManager.adjustStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        AudioManager.ADJUST_LOWER,
                                        AudioManager.FLAG_SHOW_UI
                                    )
                                }
                                v.performClick()
                            }
                        } else {
                            // Snap to edge
                            snapToEdge(params, card)
                        }
                        return true
                    }
                    else -> return false
                }
            }
        })

        card.setOnClickListener { } // Needed for accessibility with onTouch

        windowManager.addView(card, params)
        bubbleView = card
    }

    private fun cycleRingerMode() {
        val currentMode = audioManager.ringerMode
        val dndActive = notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        
        if (dndActive) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        } else {
            when (currentMode) {
                AudioManager.RINGER_MODE_NORMAL -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                }
                else -> {
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                }
            }
        }
        
        // Sync widget and app
        RingerModeWidgetProvider.updateAllWidgets(this)
        updateTiles()
    }

    private fun updateTiles() {
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

    private fun updateRingerIcon() {
        val icon = ringerIcon ?: return
        val dndActive = notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        
        if (dndActive) {
            icon.setImageResource(R.drawable.ic_dnd)
        } else {
            when (audioManager.ringerMode) {
                AudioManager.RINGER_MODE_VIBRATE -> icon.setImageResource(R.drawable.ic_vibrate)
                AudioManager.RINGER_MODE_NORMAL -> icon.setImageResource(R.drawable.ic_ring)
                else -> icon.setImageResource(R.drawable.ic_volume_mute)
            }
        }
    }

    private fun snapToEdge(params: WindowManager.LayoutParams, view: View) {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val centerX = params.x + view.width / 2
        
        val startX = params.x
        val endX = if (centerX < screenWidth / 2) 20 else screenWidth - view.width - 20
        
        val animator = ValueAnimator.ofInt(startX, endX)
        animator.addUpdateListener { animation ->
            params.x = animation.animatedValue as Int
            try {
                windowManager.updateViewLayout(view, params)
            } catch (_: Exception) {}
        }
        animator.duration = 250
        animator.interpolator = DecelerateInterpolator()
        animator.start()
    }

    private fun initNotchView() {
        val themedContext = ContextThemeWrapper(this, R.style.Theme_QuickVolume)
        // Correct way to inflate with layout params without attaching
        notchView = LayoutInflater.from(themedContext).inflate(R.layout.notch_notification, LinearLayout(themedContext), false)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = 8.dpToPx() // Safer position below most notches
        params.alpha = 0f // Start hidden
        
        windowManager.addView(notchView, params)
    }

    private fun showNotchNotification() {
        val view = notchView ?: return
        val params = view.layoutParams as WindowManager.LayoutParams
        
        val progress = view.findViewById<LinearProgressIndicator>(R.id.notchProgress)
        val percent = view.findViewById<TextView>(R.id.notchPercent)
        val icon = view.findViewById<ImageView>(R.id.notchIcon)
        
        // Use Music stream as default for the notch
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val percentage = (current.toFloat() / max * 100).toInt()
        
        progress.max = max
        progress.progress = current
        percent.text = getString(R.string.percentage_format, percentage)
        
        if (percentage == 0) {
            icon.setImageResource(R.drawable.ic_volume_mute)
        } else {
            icon.setImageResource(R.drawable.ic_media)
        }

        handler.removeCallbacks(hideNotchRunnable)
        
        if (params.alpha < 1f) {
            ValueAnimator.ofFloat(params.alpha, 1f).apply {
                duration = 300
                interpolator = DecelerateInterpolator()
                addUpdateListener { 
                    params.alpha = it.animatedValue as Float
                    windowManager.updateViewLayout(view, params)
                }
                start()
            }
        }
        
        handler.postDelayed(hideNotchRunnable, 2000)
    }

    private fun hideNotch() {
        val view = notchView ?: return
        val params = view.layoutParams as WindowManager.LayoutParams
        
        if (params.alpha > 0f) {
            ValueAnimator.ofFloat(params.alpha, 0f).apply {
                duration = 300
                interpolator = DecelerateInterpolator()
                addUpdateListener { 
                    params.alpha = it.animatedValue as Float
                    windowManager.updateViewLayout(view, params)
                }
                start()
            }
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
