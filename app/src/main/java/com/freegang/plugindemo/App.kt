package com.freegang.plugindemo

import android.app.Application
import com.freegang.fplugin.PluginBridge

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        PluginBridge.registerProxyActivity1(this)
        //PluginBridge.registerProxyActivity2(this)
    }
}