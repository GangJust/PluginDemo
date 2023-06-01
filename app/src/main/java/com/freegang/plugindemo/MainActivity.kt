package com.freegang.plugindemo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.to_registered).apply {
            setOnClickListener {
                val intent = Intent(this@MainActivity, RegisteredActivity::class.java)
                startActivity(intent)
            }
        }

        findViewById<Button>(R.id.to_unregistered).apply {
            setOnClickListener {
                val intent = Intent(this@MainActivity, UnregisteredActivity::class.java)
                startActivity(intent)
            }
        }
    }
}