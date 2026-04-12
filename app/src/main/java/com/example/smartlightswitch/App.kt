package com.example.smartlightswitch

import android.app.Application
import com.thingclips.smart.home.sdk.ThingHomeSdk

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize the Tuya/ThingClips SDK with your credentials from iot.tuya.com
        ThingHomeSdk.init(this, BuildConfig.TUYA_APP_KEY, BuildConfig.TUYA_APP_SECRET)
        ThingHomeSdk.setDebugMode(BuildConfig.DEBUG)
    }
}
