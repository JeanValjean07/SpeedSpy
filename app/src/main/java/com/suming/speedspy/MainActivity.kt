package com.suming.speedspy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.widget.Button
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import data.source.LocalVideoSource
import kotlinx.coroutines.launch


class MainActivity: AppCompatActivity() {

    private val REQUEST_STORAGE_PERMISSION = 1001

    private lateinit var adapter: VideoPagingAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.activity_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        //按钮：刷新列表
        val button1 = findViewById<FloatingActionButton>(R.id.fab)
        button1.setOnClickListener {
            runOnUiThread {
                adapter.refresh()
            }
            val recyclerview1 = findViewById<RecyclerView>(R.id.recyclerview1)
            recyclerview1.smoothScrollToPosition(0)
            //Toast.makeText(this, "刷新", Toast.LENGTH_SHORT).show()
        }
        //按钮：指南
        val button2 = findViewById<Button>(R.id.buttonGuidance)
        button2.setOnClickListener {
            val intent = Intent(this, GuidanceActivity::class.java)
            startActivity(intent)
        }
        //按钮：设置
        val buttonSettings= findViewById<Button>(R.id.buttonSetting)
        buttonSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        preCheck()

        load()



        onBackPressedDispatcher.addCallback(this, object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val duration = intent.getIntExtra("duration", 0)
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    putExtra("duration", duration)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)

                Handler(Looper.getMainLooper()).postDelayed({
                    val pid = Process.myPid()
                    Process.killProcess(pid)
                }, 500)
            }
        })



    }//onCreate END


    private fun preCheck(){
        /*
        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // 申请权限
            ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_STORAGE_PERMISSION)
            Toast.makeText(this, "需要访问媒体来读取视频，授权后请手动刷新", Toast.LENGTH_SHORT).show()
        }*/
        val requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO // Android 13+ 使用视频专用权限
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE // Android 12及以下使用旧权限
        }

        if (ContextCompat.checkSelfPermission(this, requiredPermission) != PackageManager.PERMISSION_GRANTED) {
            // 申请对应版本的权限
            ActivityCompat.requestPermissions(
                this,
                arrayOf(requiredPermission),
                REQUEST_STORAGE_PERMISSION
            )
            Toast.makeText(this, "需要访问媒体来读取视频，授权后请手动刷新", Toast.LENGTH_SHORT).show()
        }
    }

    private fun load(){
        //创建数据源实例
        val videoSource = LocalVideoSource(contentResolver, this)
        //构造Pager
        val pager = Pager(
            config = PagingConfig(pageSize = 20), // 每页加载20个视频
            //pagingSourceFactory = { videoSource }
            pagingSourceFactory = { LocalVideoSource(contentResolver, this)
            }

        )

        //定位RecyclerView
        val recyclerview1 = findViewById<RecyclerView>(R.id.recyclerview1)
        //为选中的RecyclerView设置LayoutManager
        recyclerview1.layoutManager = LinearLayoutManager(this)
        //挂载Adapter
        adapter = VideoPagingAdapter()
        //val adapter = VideoAdapter(videos)
        recyclerview1.adapter = adapter


        lifecycleScope.launch {
            pager.flow.collect {
                adapter.submitData(it)
            }
        }

    }



}//class END
