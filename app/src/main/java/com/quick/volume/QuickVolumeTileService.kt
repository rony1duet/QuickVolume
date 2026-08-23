package com.quick.volume

import android.app.NotificationManager
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings Tile to cycle through Ringer Modes (Normal -> Vibrate -> DND).
 */
class QuickVolumeTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        
        val currentMode = audioManager.ringerMode
        val currentDnd = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        
        // Optimistic UI update for speed
        if (currentDnd) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            updateTileOptimistic(AudioManager.RINGER_MODE_NORMAL, false)
        } else {
            when (currentMode) {
                AudioManager.RINGER_MODE_NORMAL -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    updateTileOptimistic(AudioManager.RINGER_MODE_VIBRATE, false)
                }
                else -> {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                    updateTileOptimistic(currentMode, true)
                }
            }
        }
        
        // Sync everything else in background
        RingerModeWidgetProvider.updateAllWidgets(this)
    }

    private fun updateTileOptimistic(ringerMode: Int, dndActive: Boolean) {
        val tile = qsTile ?: return
        if (dndActive) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = getString(R.string.mode_dnd)
            tile.icon = Icon.createWithResource(this, R.drawable.ic_dnd)
        } else {
            when (ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> {
                    tile.state = Tile.STATE_INACTIVE
                    tile.label = getString(R.string.mode_normal)
                    tile.icon = Icon.createWithResource(this, R.drawable.ic_ring)
                }
                AudioManager.RINGER_MODE_VIBRATE -> {
                    tile.state = Tile.STATE_ACTIVE
                    tile.label = getString(R.string.mode_vibrate)
                    tile.icon = Icon.createWithResource(this, R.drawable.ic_vibrate)
                }
                else -> {
                    tile.state = Tile.STATE_INACTIVE
                    tile.label = getString(R.string.mode_dnd)
                    tile.icon = Icon.createWithResource(this, R.drawable.ic_dnd)
                }
            }
        }
        tile.updateTile()
    }

    private fun updateTile() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        updateTileOptimistic(audioManager.ringerMode, nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL)
    }
}
