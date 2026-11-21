package com.dps.devicescope

import android.app.Application
import com.dps.devicescope.core.DeviceContainer

class DeviceScopeApp : Application() {
    lateinit var container: DeviceContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DeviceContainer(this)
    }
}
