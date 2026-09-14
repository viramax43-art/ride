package com.rideminiapp

import android.app.Application
import com.tencent.mmkv.MMKV
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration

@HiltAndroidApp
class RideApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(this)
        Configuration.getInstance().userAgentValue = packageName
    }
}
