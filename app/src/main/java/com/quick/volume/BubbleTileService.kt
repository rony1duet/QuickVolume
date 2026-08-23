package com.quick.volume

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

/**
 * Quick Settings Tile to toggle the Floating Volume Bubble.
 */
class BubbleTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        
        // Immediate feedback: toggle state locally first
        val currentlyRunning = FloatingVolumeService.isRunning
        val newState = !currentlyRunning
        
        updateTileState(newState)

        val intent = Intent(this, FloatingVolumeService::class.java)
        if (currentlyRunning) {
            stopService(intent)
        } else {
            ContextCompat.startForegroundService(this, intent)
        }
    }

    private fun updateTile() {
        updateTileState(FloatingVolumeService.isRunning)
    }

    private fun updateTileState(running: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.floating_bubble_title)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_bubble)
        tile.updateTile()
    }
}
