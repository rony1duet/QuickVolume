package com.quick.volume

import android.app.Application
import com.google.android.material.color.DynamicColors

class QuickVolumeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Apply Dynamic Colors (Material You) globally to all activities
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
