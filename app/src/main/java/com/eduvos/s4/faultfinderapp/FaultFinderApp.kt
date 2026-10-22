
package com.eduvos.s4.faultfinderapp

import android.app.Application
import com.cloudinary.android.MediaManager

/**
 * This is also used to link images to faults
 */
class FaultFinderApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val config = mapOf(
            "cloud_name" to CloudinaryConfig.CLOUD_NAME
        )

        MediaManager.init(this, config)
    }
}
