package com.example.hsa.acitivty

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.hsa.R


class MainActivity : AppCompatActivity() {
    val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val openActivity = findViewById<Button>(R.id.openActivity)
        val openActivity1 = findViewById<Button>(R.id.openActivity1)

        openActivity.setOnClickListener {
            val intent = Intent()
            intent.setClass(applicationContext, TwoActivity::class.java)
            startActivity(intent)
        }
        openActivity1.setOnClickListener {
            val intent = Intent()
            intent.setClass(applicationContext, ThreeActivity::class.java)
            startActivity(intent)
        }
    }
}