package com.suming.speedspy

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class GuidanceActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        setContentView(R.layout.activity_guidance)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.activity_guidance)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        //按钮：返回
        val buttonBack = findViewById<ImageButton>(R.id.buttonExit)
        buttonBack.setOnClickListener {
            finish()
        }
        //按钮：前往酷安主页
        val buttonGoCoolApk = findViewById<TextView>(R.id.buttonGoCoolApk)
        buttonGoCoolApk.setOnClickListener {
            val url = "http://www.coolapk.com/u/3105725"
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(intent)
        }

    }
}