package com.freegang.plugindemo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

//该活动未在 Manifest 中注册, 利用代理跳转
class UnregisteredActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_unregistered)
    }
}