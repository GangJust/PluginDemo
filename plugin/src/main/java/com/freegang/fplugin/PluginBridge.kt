package com.freegang.fplugin

import android.app.Application
import com.freegang.fplugin.activity.PluginActivityBridge


object PluginBridge {

    /**
     * see at {@link PluginActivityBridge#registerProxyActivity1(Application)}
     */
    fun registerProxyActivity1(application: Application) {
        PluginActivityBridge.registerProxyActivity1(application)
    }

    /**
     * see at {@link PluginActivityBridge#registerProxyActivity2(Application)}
     */
    fun registerProxyActivity2(application: Application) {
        PluginActivityBridge.registerProxyActivity2(application)
    }
}