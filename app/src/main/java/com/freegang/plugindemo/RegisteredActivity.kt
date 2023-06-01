package com.freegang.plugindemo

import android.app.Activity
import android.os.Bundle

//该活动已经在 Manifest 中注册, 可以正常跳转
class RegisteredActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registered)
    }
}