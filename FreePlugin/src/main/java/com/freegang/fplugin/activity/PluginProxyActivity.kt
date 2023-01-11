package com.freegang.fplugin.activity

import android.app.Activity
import android.os.Bundle

class PluginProxyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        throw RuntimeException("此活动不应该被执行显示, 它只作为插件的桥梁!")
    }
}