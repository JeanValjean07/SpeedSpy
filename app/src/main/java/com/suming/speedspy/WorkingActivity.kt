package com.suming.speedspy

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaCodec
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C.WAKE_MODE_NETWORK
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.suming.speedspy.WorkingActivity.DeviceCompatUtil.isCompatibleDevice
import com.suming.speedspy.data.model.ThumbItem
import data.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@UnstableApi
class WorkingActivity: AppCompatActivity()  {

    //时间戳信息显示位
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalDuration: TextView
    //视频信息预读
    private var absolutePath = ""       //视频绝对路径,多个成员要读取
    private var totalDuration: Long = 0   //视频总时长,用于计算
    private var fps: Float = 0f      //获取视频帧率：计算切帧间距
    //播放器状态标识
    private lateinit var player: ExoPlayer
    private var playerEnd = false     //上一次是否播放到末尾了
    private var wasPlaying = true     //上一次暂停时是否在播放
    //音量配置参数
    private var currentVolume = 0
    //缩略图绘制参数
    private var maxPicNumber = 20         //缩略图最大数量(写死)
    private var eachPicWidth = 0          //单张缩略图最大宽度(现场计算),高度45dp布局写死
    private var picNumber = 0             //缩略图数量(现场计算)
    private var eachPicDuration: Int = 0  //单张缩略图对应时长(现场计算)
    //点击和滑动状态标识
    private var onDown = false
    private var onScrolling = false
    private var dragging = false
    private var fling =false
    private var singleTap = false
    private var scrolling = false
    //设置
    private lateinit var sharedPref: SharedPreferences
    private var tapScrollEnabled = false
    private var linkScrollEnabled = false
    private var alwaysSeekEnabled = false
    //标记位
    private var marking1Value = 0L
    private var marking2Value = 0L
    private var flag1Value = 0L
    private var flag2Value = 0L
    //速度计算结果
    private var speedKmh = 0f
    //计算要用的参数
    private var areaLength = 25.0f   //计算用的区域长度,一节车厢一般25米(400af&bf都是)
    //Seek程序用到的参数
    private var isSeekReady = false  //seek进入锁
    //倍速程序用到的参数
    private var currentTime = 0L     //进度条对应时间,用于和当前播放器返回时间比较,防止反向
    private var lastPlaySpeed = 0f   //速度没变时,不发送给ExoPlayer
    //进度条随视频进度滚动程序用到的参数
    private var syncScrollTaskRunning = false
    //onScrolled回调中用来判断滚动方向
    private var thisScrollerPosition = 0
    private var lastScrollerPosition = 0
    private var scrollerPositionGap = 0
    //保存为副本程序用到的参数
    private var newFileSaved = false   //标记已保存,防止重复保存副本
    //旧机型标识
    private var isCompatibleDevice = false
    private var cannotOpen = false

    //以下几个可能跟上面的功能有重复
    private var lastScrollerPositionSeek = 0
    private var dontScrollThisTime = false
    private var dropThisOnDown = false

    //旧机型兼容判断
    object DeviceCompatUtil {
        /*
        private val SOC_MAP = mapOf(
            "kirin710" to 700,
            "kirin970" to 970,
            "kirin980" to 980,
            "kirin990" to 990,
            "kirin9000" to 1000,

            "msm8998"  to 835,
            "sdm845"   to 845,
        )
        */
        fun isCompatibleDevice(): Boolean {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                val hw = Build.HARDWARE.lowercase()
                //val soc = SOC_MAP.entries.find { hw.contains(it.key) }?.value ?: return false
                return when {
                    hw.contains("kirin") -> return true
                    hw.contains("sdm") -> return true       //暂不完善soc细分判断
                    else -> false
                }
            }else{
                return false
            }
        }
    }


    @OptIn(UnstableApi::class)
    @SuppressLint("CutPasteId", "SetTextI18n", "InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        setContentView(R.layout.activity_working)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        //设置刷新率
        /*
        window.attributes.preferredRefreshRate = 60.0f
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        */
        //音量
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        //设置项读取,检查和预置
        sharedPref = getSharedPreferences("SpeedSpyPrefs", MODE_PRIVATE)
        val prefs = getSharedPreferences("SpeedSpyPrefs", MODE_PRIVATE)
        if (!prefs.contains("tapScrolling")){
            prefs.edit { putBoolean("tapScrolling", false) }
            tapScrollEnabled = prefs.getBoolean("tapScrolling", false)
        }else{
            tapScrollEnabled = prefs.getBoolean("tapScrolling", false)
        }
        if (!prefs.contains("linkScrolling")){
            prefs.edit { putBoolean("linkScrolling", true) }
            linkScrollEnabled = prefs.getBoolean("linkScrolling", false)
        } else{
            linkScrollEnabled = prefs.getBoolean("linkScrolling", false) }
        if (!prefs.contains("alwaysSeek")){
            prefs.edit { putBoolean("alwaysSeek", false) }
            alwaysSeekEnabled = prefs.getBoolean("alwaysSeek", false)
        } else{
            alwaysSeekEnabled = prefs.getBoolean("alwaysSeek", false) }
        preCheck()


        //反序列化item + 支持用分享打开和用其他应用打开，暂不支持批量打开
        val videoItem: VideoItem? = when (intent?.action) {
            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return finish()
                VideoItem(0, uri, "" , 0)
            }
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return finish()
                VideoItem(0, uri, "", 0)
            }
            else -> { IntentCompat.getParcelableExtra(intent, "video", VideoItem::class.java)}
        }
        if (videoItem == null) {
            Toast.makeText(this, "无法打开这条视频", Toast.LENGTH_SHORT).show()
            cannotOpen = true
            finish()
            return
        } //防空

        //播放器初始化
        val playerView = findViewById<PlayerView>(R.id.playerView)
        player = ExoPlayer.Builder(this)
            .setSeekParameters(SeekParameters.EXACT)
            .setWakeMode(WAKE_MODE_NETWORK)
            .build()
            .apply { setMediaItem(MediaItem.fromUri(videoItem.uri)) }
        playerView.player = player


        //动态控件初始化：时间戳 + 总时长 + 遮罩淡出
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalDuration = findViewById(R.id.tvTotalDuration)
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    isSeekReady = true
                    buttonRefresh()
                    //显示总时长
                    val totalDuration = player.duration
                    val totalDurationSec = (totalDuration / 1000)
                    this@WorkingActivity.totalDuration = totalDurationSec
                    tvTotalDuration.text = formatTime(totalDuration)
                    //开始播放遮罩淡出
                    val cover = findViewById<View>(R.id.cover)
                    cover.animate()
                        .alpha(0f)
                        .setDuration(100)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .withEndAction { cover.visibility = View.GONE }
                        .start()
                }
                when (state) {
                    Player.STATE_READY -> {
                        if (!alwaysSeekEnabled){
                           return
                        }
                        if (wasPlaying) {
                            playVideo()
                        }
                    }
                    Player.STATE_ENDED -> {
                        stopVideoTimeSync()
                        stopScrollerSync()
                        playerEnd = true
                        pauseVideo()
                        notice("视频结束",1000)
                    }
                    Player.STATE_IDLE -> {
                        stopVideoTimeSync()
                        stopScrollerSync()
                    }
                    Player.STATE_BUFFERING -> {  }
                }
            }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                return
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                buttonRefresh()
            }
            override fun onTracksChanged(tracks: Tracks) {
                for (trackGroup in tracks.groups) {
                    val format = trackGroup.getTrackFormat(0)
                    fps = format.frameRate
                    break
                }
            }
        })

        //测试器 codec
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializationDurationMs: Long) {
                Log.e("MediaCodec", "当前视频解码器 = $decoderName")
            }
            override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
                Log.e("MediaCodec", "AnalyticsListener 捕获错误：${error.errorCodeName}")
            }
        })
        try {
            val mime = "video/avc"
            val decoderInfos = MediaCodecUtil.getDecoderInfos(mime, false, false)
            Log.e("MediaCodec", "查询当前系统支持的解码器：已完成，数量=${decoderInfos.size}")
            for (info in decoderInfos) {
                Log.e("MediaCodec", "name=${info.name}, hardware=${info.hardwareAccelerated}")
            }
        } catch (t: Throwable) {
            Log.e("MediaCodec", "查询解码器失败", t)
        }

        //时间戳文本：开始(00:00.000),当前时间,总时间(均可点击)
        val seekStart = findViewById<TextView>(R.id.seekStart)
        seekStart.setOnClickListener {
            if (player.isPlaying) {
                player.pause()
                player.seekTo(0)
                player.setPlaybackSpeed(1.0f)
                player.prepare()
                player.play()
                startVideoTimeSync()
                stopVideoSmartScroll()
                if (linkScrollEnabled){ startScrollerSync() }
                notice("重播",1000)
            }else{
                player.pause()
                player.seekTo(0)
                buttonRefresh()
                startVideoTimeSync()
                stopVideoSmartScroll()
                if (linkScrollEnabled){ startScrollerSync() }
            }

        }
        val seekStop = findViewById<TextView>(R.id.tvCurrentTime)
        seekStop.setOnClickListener {
            if (player.isPlaying) {
                player.pause()
                notice("暂停播放",1000)
            } else {
                if (player.currentPosition >= player.duration){
                    player.seekTo(0)
                    player.play()
                    if (linkScrollEnabled){ startScrollerSync() }
                    notice("视频已结束,开始重播",1000)
                }
                else{
                    player.play()
                    notice("继续播放",1000)
                }
            }
            buttonRefresh()
        }
        val seekDuration = findViewById<TextView>(R.id.tvTotalDuration)
        seekDuration.setOnClickListener {
            notice("视频总长：${formatTime3(player.duration)}",3000)
        }

        //控件：缩略图滚动条初始化
        val rvThumbnails = findViewById<RecyclerView>(R.id.rvThumbnails)
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val sidePadding = screenWidth / 2
        rvThumbnails.setPadding(sidePadding, 0, sidePadding-1, 0) //右边需要减一，否则滑动区域会超出
        var videoUri = videoItem.uri
        absolutePath = getAbsoluteFilePath(this@WorkingActivity, videoUri).toString()
        rvThumbnails.layoutManager = LinearLayoutManager(this@WorkingActivity, LinearLayoutManager.HORIZONTAL, false)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(this@WorkingActivity, videoUri)
        } catch (_: Exception) {
            val data = Intent().apply {
                putExtra("key", "needRefresh")
            }
            setResult(RESULT_OK, data)
            finish()
            return
        }
        rvThumbnails.itemAnimator = null
        rvThumbnails.layoutParams.width = 0
        val videoDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toInt()
        if (videoDuration != null) {
            if (videoDuration/1000 > maxPicNumber){  //用47dp计算,做成长方形一点的
                eachPicWidth = (47 * displayMetrics.density).toInt()
                eachPicDuration = (videoDuration.div(100)*100) / maxPicNumber
                picNumber = maxPicNumber
            }else{
                eachPicWidth = (47 * displayMetrics.density).toInt()
                picNumber = videoDuration/1000+1
                eachPicDuration = (videoDuration.div(100)*100)/picNumber
            }
        } else{
            notice("视频长度获取失败,无法绘制控制界面",5000)
            finish()
        }
        retriever.release()


        //RecyclerView-事件监听器 -onSingleTap -onDown
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            @SuppressLint("SetTextI18n")
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                singleTap = true
                if (!tapScrollEnabled) {
                    if (linkScrollEnabled){
                        notice("未开启单击跳转,如需跳转请先开启,或关闭链接滚动", 1000)
                        if (wasPlaying) {
                            wasPlaying = false
                            player.play()
                        }
                        return false
                    }
                }
                //根据百分比计算具体跳转时间点
                val totalContentWidth = rvThumbnails.computeHorizontalScrollRange()
                val scrolled = rvThumbnails.computeHorizontalScrollOffset()
                val leftPadding = rvThumbnails.paddingLeft
                val xInContent = e.x + scrolled - leftPadding
                if (totalContentWidth <= 0) return false
                val percent = xInContent / totalContentWidth
                val seekToMs = (percent * player.duration).toLong().coerceIn(0, player.duration)
                if (seekToMs <= 0) {
                    return false
                }
                if (seekToMs >= player.duration){
                    return false
                }
                //发送跳转命令
                player.seekTo(seekToMs)
                notice("跳转至${formatTime2(seekToMs)}",1000)
                lifecycleScope.launch {
                    startScrollerSync()
                    delay(20)
                    if (!linkScrollEnabled){
                        stopScrollerSync()
                    }
                }

                if (wasPlaying){
                    wasPlaying = false
                    player.play()
                }
                return true
            }
            override fun onDown(e: MotionEvent): Boolean {
                if (!linkScrollEnabled) return false

                //播放状态记录
                wasPlaying = false
                if (player.isPlaying){
                    player.pause()
                    wasPlaying = true
                }
                stopVideoSeek()
                if (!linkScrollEnabled) return false
                if (!tapScrollEnabled) return false
                stopScrollerSync()
                //状态参量置位
                dropThisOnDown = true


                buttonRefresh()
                return false
            }
        })
        //RecyclerView-事件监听器 (中间层)
        rvThumbnails.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean =
                gestureDetector.onTouchEvent(e)
            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                gestureDetector.onTouchEvent(e)
            }
            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })
        //RecyclerView-事件监听器 -onScrollStateChanged -onScrolled
        rvThumbnails.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING){
                    dragging = true
                    scrolling = true
                    return
                }
                if (newState == RecyclerView.SCROLL_STATE_SETTLING){
                    dragging = false
                    onScrolling = true
                    return
                }
                if (newState == RecyclerView.SCROLL_STATE_IDLE){
                    dragging = false
                    scrolling = false
                    return
                }
            }
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (linkScrollEnabled){
                    val percent = recyclerView.computeHorizontalScrollOffset().toFloat() / rvThumbnails.computeHorizontalScrollRange()
                    val seekToMs = (percent * player.duration).toLong()
                    currentTime = seekToMs
                    tvCurrentTime.text = formatTime(seekToMs)
                }
                if (!scrolling && !dragging) { //此状态说明进度条是在随视频滚动,用户没有操作
                    return
                }
                if (!linkScrollEnabled) { //此状态说明进度条是在随视频滚动,用户没有操作
                    return
                }
                //检查滑动方向：由于必须触发滑动才会计算滑动Gap,故滑动Gap不会为0
                thisScrollerPosition = recyclerView.computeHorizontalScrollOffset()
                scrollerPositionGap = thisScrollerPosition - lastScrollerPosition
                lastScrollerPosition = thisScrollerPosition
                if (dropThisOnDown){
                    dropThisOnDown = false
                    return
                }
                stopScrollerSync()  //进度条变成上级控制层,关闭所有将进度条作为下级被控层的函数
                if (scrollerPositionGap > 0 && scrollerPositionGap < 100){
                    if(alwaysSeekEnabled){
                        pauseVideo()  //seek时必须暂停视频
                        stopVideoSmartScroll()
                        startVideoSeek()
                    }else{
                        stopVideoSeek()
                        startVideoSmartScroll()
                    }
                }
                else if(scrollerPositionGap < 0 && scrollerPositionGap > -100){
                    stopVideoTimeSync()
                    pauseVideo()
                    val recyclerView = findViewById<RecyclerView>(R.id.rvThumbnails)
                    val totalWidth2 = recyclerView.computeHorizontalScrollRange()
                    val offset2 = recyclerView.computeHorizontalScrollOffset()
                    val percent2 = offset2.toFloat() / totalWidth2
                    val seekToMs2 = (percent2 * player.duration).toLong()
                    if (seekToMs2 > player.currentPosition){
                        return
                    }
                    stopVideoSmartScroll()
                    startVideoSeek()
                }
            }
        })


        //固定控件初始化：退出按钮
        val buttonExit=findViewById<View>(R.id.buttonExit)
        buttonExit.setOnClickListener {
            player.playWhenReady = false
            finish()
        }
        //提示卡点击时关闭
        val noticeCard = findViewById<CardView>(R.id.noticeCard)
        noticeCard.setOnClickListener {
            noticeCard.visibility = View.GONE
        }
        //按钮：在系统播放器打开
        val buttonOpenInSys = findViewById<Button>(R.id.buttonOpenInSys)
        buttonOpenInSys.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(videoUri.toString().toUri(), "video/*")
            startActivity(intent)
        }
        //按钮：暂停视频
        val buttonPause = findViewById<FrameLayout>(R.id.buttonPause)
        buttonPause.setOnClickListener {
            if (player.isPlaying) {
                pauseVideo()
                stopScrollerSync()
                notice("暂停",1000)
                buttonRefresh()
            } else {
                if (playerEnd){
                    notice("视频已结束,为您重播",1000)
                    player.seekTo(0)
                    playerEnd = false
                }
                else{
                    onDown = false
                    fling = false
                    notice("继续播放",1000)
                }
                lifecycleScope.launch {
                    player.volume = currentVolume.toFloat()
                    player.setPlaybackSpeed(1.0f)
                    delay(20)
                    playVideo()
                }
                if (linkScrollEnabled){ startScrollerSync() }
                buttonRefresh()
            }
        }
        //按钮：单击跳转
        val buttonTap = findViewById<FrameLayout>(R.id.buttonActualTap)
        val buttonTapMaterial = findViewById<MaterialButton>(R.id.buttonMaterialTap)
        if (tapScrollEnabled){
            buttonTapMaterial.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ButtonBg))
        }else{
            buttonTapMaterial.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ButtonBgClosed))
        }
        buttonTap.setOnClickListener {
            if (!prefs.getBoolean("tapScrolling",false)){
                tapScrollEnabled = true
                sharedPref.edit { putBoolean("tapScrolling", true) }
                buttonTapMaterial.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ButtonBg))
                notice("已开启单击跳转",1000)
            }
            else{
                tapScrollEnabled = false
                sharedPref.edit { putBoolean("tapScrolling", false) }
                buttonTapMaterial.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ButtonBgClosed))
                notice("已关闭单击跳转",1000)
            }
        }
        //按钮：链接滚动条与视频进度
        val buttonLink = findViewById<FrameLayout>(R.id.buttonActualLink)
        val buttonLinkMaterial = findViewById<MaterialButton>(R.id.buttonMaterialLink)
        if (linkScrollEnabled){
            buttonLinkMaterial.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ButtonBg))
        }else{
            buttonLinkMaterial.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ButtonBgClosed))
        }
        buttonLink.setOnClickListener {
            if (!prefs.getBoolean("linkScrolling",false)){ //启用链接滚动条与视频进度
                linkScrollEnabled = true
                sharedPref.edit { putBoolean("linkScrolling", true) }
                notice("已将进度条与视频进度同步",1000)
                startScrollerSync()
                buttonLinkMaterial.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ButtonBg))
                stopVideoSeek()
            }
            else{  //关闭链接滚动条与视频进度
                linkScrollEnabled = false
                sharedPref.edit { putBoolean("linkScrolling", false) }
                stopScrollerSync()
                buttonLinkMaterial.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ButtonBgClosed))
                notice("已关闭链接滚动条与视频进度",2500)
            }
        }
        //按钮：AlwaysSeek
        val buttonAlwaysSeek = findViewById<FrameLayout>(R.id.buttonActualAlwaysSeek)
        val buttonAlwaysMaterial = findViewById<MaterialButton>(R.id.buttonMaterialAlwaysSeek)
        if (alwaysSeekEnabled){
            buttonAlwaysMaterial.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ButtonBg))
        }else{
            buttonAlwaysMaterial.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ButtonBgClosed))
        }
        buttonAlwaysSeek.setOnClickListener {
            if (!prefs.getBoolean("alwaysSeek",false)){
                alwaysSeekEnabled = true
                sharedPref.edit { putBoolean("alwaysSeek", true) }
                buttonAlwaysMaterial.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ButtonBg))
                notice("已开启AlwaysSeek",1000)
            }
            else{
                alwaysSeekEnabled = false
                sharedPref.edit { putBoolean("alwaysSeek", false) }
                buttonAlwaysMaterial.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ButtonBgClosed))
                notice("已关闭AlwaysSeek,正向拖动进度条时将启用倍速算法",3000)
            }
        }
        //按钮：上一帧
        val buttonPrevFrame = findViewById<FrameLayout>(R.id.buttonActualPrevFrame)
        buttonPrevFrame.setOnClickListener {
            if (!linkScrollEnabled) {
                notice("开启链接滚动后才能使用此按钮",1000)
                return@setOnClickListener
            }
            stopVideoSmartScroll()
            if (!isSeekReady) return@setOnClickListener
            isSeekReady = false
            player.pause()
            startVideoTimeSync()
            startScrollerSync()
            notice("上一帧",1000)
            player.seekTo(player.currentPosition - ((1000 / fps).toLong()+10))
        }
        //按钮：下一帧
        val buttonNextFrame = findViewById<FrameLayout>(R.id.buttonActualNextFrame)
        buttonNextFrame.setOnClickListener {
            if (!linkScrollEnabled) {
                notice("开启链接滚动后才能使用此按钮",1000)
                return@setOnClickListener
            }
            stopVideoSmartScroll()
            if (!isSeekReady) return@setOnClickListener
            isSeekReady = false
            player.pause()
            startVideoTimeSync()
            startScrollerSync()
            notice("下一帧",1000)
            player.seekTo(player.currentPosition + ((1000 / fps).toLong()+10))
        }
        //按钮：把视频绑定到进度条位置
        val buttonWithScroller = findViewById<FrameLayout>(R.id.buttonActualWithScroller)
        buttonWithScroller.setOnClickListener {
            if (player.isPlaying){
                notice("视频暂停时才能使用此按钮",1000)
                return@setOnClickListener
            }
            notice("已将视频绑定到进度条位置",1000)
            val recyclerView = findViewById<RecyclerView>(R.id.rvThumbnails)
            recyclerView.stopScroll()
            val totalWidth = recyclerView.computeHorizontalScrollRange()
            val offset     = recyclerView.computeHorizontalScrollOffset()
            val percent    = offset.toFloat() / totalWidth
            val seekToMs   = (percent * player.duration).toLong()
            player.seekTo(seekToMs)
            isSeekReady = false
        }
        //按钮：把进度条绑定到视频位置
        val buttonWithVideo = findViewById<FrameLayout>(R.id.buttonActualWithVideo)
        buttonWithVideo.setOnClickListener {
            if (player.isPlaying){
                notice("视频暂停时才能使用此按钮",1000)
                return@setOnClickListener
            }
            notice("已将进度条绑定到视频当前位置",1000)
            val recyclerView = findViewById<RecyclerView>(R.id.rvThumbnails)
            recyclerView.stopScroll()
            lifecycleScope.launch{
                startScrollerSync()
                delay(10)
                stopScrollerSync()
            }
        }
        //按钮：Flag1
        var buttonFlag1Set = false
        var buttonFlag2Set = false
        val buttonFlag1 = findViewById<MaterialButton>(R.id.buttonMaterialFlag1)
        buttonFlag1.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ButtonBgClosed))
        buttonFlag1.setOnClickListener {
            player.pause()
            if (!buttonFlag1Set){
                AlertDialog.Builder(this)
                    .setTitle("确认操作")
                    .setMessage("是否要标记当前位置为1号定位标？\n(${formatTime3(player.currentPosition)})")
                    .setPositiveButton("确认标记") { _, _ ->
                        flag1Value = player.currentPosition
                        saveFrameToInternal("${videoUri.hashCode()}_flag1")
                        buttonFlag1Set = true
                        buttonFlag1.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ButtonBg))
                        if (buttonFlag2Set && buttonFlag1Set){
                            val buttonMaterialLaunchAccuracy = findViewById<MaterialButton>(R.id.buttonMaterialLaunchAccuracy)
                            buttonMaterialLaunchAccuracy.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ButtonBg))
                            notice("您现在可以开始精调了",1000)
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("确认操作")
                    .setMessage("是否要移除1号定位标？")
                    .setPositiveButton("确认移除") { _, _ ->
                        notice("1号定位标已被移除",1000)
                        buttonFlag1Set = false
                        buttonFlag1.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ButtonBgClosed))
                        if (!buttonFlag2Set || !buttonFlag1Set){
                            val buttonMaterialLaunchAccuracy = findViewById<MaterialButton>(R.id.buttonMaterialLaunchAccuracy)
                            buttonMaterialLaunchAccuracy.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ButtonBgClosed))
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
        //按钮：Flag2
        val buttonFlag2 = findViewById<MaterialButton>(R.id.buttonMaterialFlag2)
        buttonFlag2.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ButtonBgClosed))
        buttonFlag2.setOnClickListener {
            player.pause()
            if (!buttonFlag2Set){
                AlertDialog.Builder(this)
                    .setTitle("确认操作")
                    .setMessage("是否要标记当前位置为2号定位标？\n(${formatTime3(player.currentPosition)})")
                    .setPositiveButton("确认标记") { _, _ ->
                        flag2Value = player.currentPosition
                        buttonFlag2Set = true
                        buttonFlag2.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ButtonBg))
                        saveFrameToInternal("${videoUri.hashCode()}_flag2")
                        if (buttonFlag2Set && buttonFlag1Set){
                            val buttonMaterialLaunchAccuracy = findViewById<MaterialButton>(R.id.buttonMaterialLaunchAccuracy)
                            buttonMaterialLaunchAccuracy.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ButtonBg))
                            notice("您现在可以开始精调了",1000)
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("确认操作")
                    .setMessage("是否要移除2号定位标？")
                    .setPositiveButton("确认移除") { _, _ ->
                        notice("2号定位标已被移除",1000)
                        buttonFlag2Set = false
                        buttonFlag2.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ButtonBgClosed))
                        if (!buttonFlag2Set || !buttonFlag1Set){
                            val buttonMaterialLaunchAccuracy = findViewById<MaterialButton>(R.id.buttonMaterialLaunchAccuracy)
                            buttonMaterialLaunchAccuracy.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ButtonBgClosed))
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
        //按钮：Marking1清除
        val buttonMarking1 = findViewById<Button>(R.id.marking1Clear)
        buttonMarking1.setOnClickListener {
            val marking1 = findViewById<TextView>(R.id.marking1)
            marking1Value = 0L
            marking1.text = ""
            notice("清除标记点1",1000)
        }
        //按钮：Marking2清除
        val buttonMarking2 = findViewById<Button>(R.id.marking2Clear)
        buttonMarking2.setOnClickListener {
            val marking2 = findViewById<TextView>(R.id.marking2)
            marking2Value = 0L
            marking2.text = ""
            notice("清除标记点2",1000)
        }
        //按钮：计算速度
        val buttonCalculate = findViewById<Button>(R.id.calculate)
        buttonCalculate.setOnClickListener {
            if (marking1Value == 0L || marking2Value == 0L){
                notice("请先打完标记点",2000)
                return@setOnClickListener
            }
            var markingGapMs = 0L
            if (marking2Value < marking1Value){
                markingGapMs = marking1Value - marking2Value
            }else if (marking2Value > marking1Value){
                markingGapMs = marking2Value - marking1Value
            }
            speedKmh = "%.1f".format((areaLength / (markingGapMs.toFloat()/1000)) * 3.6).toFloat()
            val result = findViewById<TextView>(R.id.result)
            result.text = "$speedKmh KM/H"
            if (speedKmh > 340){
                notice("计算结果：${speedKmh} KM/H, 达速运行！",5000)
            } else {
                notice("计算结果：${speedKmh} KM/H",5000)
            }
        }
        //按钮：保存新文件名的副本
        val buttonToFileName = findViewById<MaterialButton>(R.id.toFileName)
        buttonToFileName.setOnClickListener {
            if (speedKmh == 0f){
                notice("请先计算速度",1000)
                return@setOnClickListener
            }
            if (newFileSaved){
                notice("已保存副本,请前往文件管理查看",1000)
                return@setOnClickListener
            }
            val fileName = getFileName(videoUri)
            if (fileName != null){
                saveCopyWithAddon(this,videoUri,"${speedKmh}KMH")
            }





        }
        //按钮：进入精调
        val buttonLaunchAccuracy = findViewById<FrameLayout>(R.id.buttonActualLaunchAccuracy)
        val buttonMaterialLaunchAccuracy = findViewById<MaterialButton>(R.id.buttonMaterialLaunchAccuracy)
        buttonMaterialLaunchAccuracy.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ButtonBgClosed))
        buttonLaunchAccuracy.setOnClickListener {
            if (!buttonFlag1Set || !buttonFlag2Set){
                notice("请先设置完2个定位标",1000)
            } else if (marking2Value != 0L && marking1Value != 0L){
                notice("请先清除已有标记点",1000)
            } else {
                player.pause()
                val dialog = AccuracyActivity.newInstance(videoUri,flag1Value,flag2Value)
                dialog.show(supportFragmentManager, "ScreenshotDialog")
            }
        }
        //按钮：手动填写
        val manualSet = findViewById<MaterialButton>(R.id.manualSet)
        manualSet.setOnClickListener {
            player.pause()
            val dialog = Dialog(this)
            val dialogView = LayoutInflater.from(this).inflate(R.layout.activity_working_inputvalue, null)
            dialog.setContentView(dialogView)
            val title: TextView = dialogView.findViewById(R.id.dialog_title)
            title.text = "填写计算区域长度"
            val titleDescription:TextView = dialogView.findViewById(R.id.dialog_description)
            titleDescription.text = ""
            val input: EditText = dialogView.findViewById(R.id.dialog_input)
            input.hint = "单位:米丨如果不填写,默认使用25.0米"
            val button: Button = dialogView.findViewById(R.id.dialog_button)
            val imm = this.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            button.setOnClickListener {
                val userInput = input.text.toString()
                setValue(userInput)
                dialog.dismiss()
            }
            dialog.show()
            CoroutineScope(Dispatchers.Main).launch {
                delay(50)
                input.requestFocus()
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        //按钮：选择车型
        val chooseType = findViewById<MaterialButton>(R.id.chooseType)
        chooseType.setOnClickListener {
            player.pause()
            val dialog = EmuTypeChooseActivity.newInstance()
            dialog.show(supportFragmentManager, "ScreenshotDialog")
        }


        //Dialog返回值接收器与判断逻辑
        supportFragmentManager.setFragmentResultListener("requestKey", this) { _, bundle ->
            val Marking1 = findViewById<TextView>(R.id.marking1)
            val Marking2 = findViewById<TextView>(R.id.marking2)
            val receiveValue = bundle.getLong("value")
            buttonFlag1Set = false
            buttonFlag2Set = false
            val buttonFlag1 = findViewById<MaterialButton>(R.id.buttonMaterialFlag1)
            buttonFlag1.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ButtonBgClosed))
            val buttonFlag2 = findViewById<MaterialButton>(R.id.buttonMaterialFlag2)
            buttonFlag2.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ButtonBgClosed))
            val buttonMaterialLaunchAccuracy = findViewById<MaterialButton>(R.id.buttonMaterialLaunchAccuracy)
            buttonMaterialLaunchAccuracy.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ButtonBgClosed))
            if (marking1Value == 0L){
                marking1Value = receiveValue
                Marking1.text = formatTime3(receiveValue)
            } else if (marking2Value == 0L){
                marking2Value = receiveValue
                Marking2.text = formatTime3(receiveValue)
            }
        }

        //Dialog返回值接收器与判断逻辑2
        supportFragmentManager.setFragmentResultListener("requestKey2", this) { _, bundle ->
            val receiveValue = bundle.getFloat("value")
            areaLength = receiveValue
            val emuType = findViewById<TextView>(R.id.emuType)
            emuType?.text = "${areaLength}米(手动填写)"
        }

        //发起adapter联动
        lifecycleScope.launch(Dispatchers.IO) {
            videoUri = videoItem.uri
            val thumbs = MutableList(picNumber) { sec ->
                val file = File(
                    filesDir,
                    "thumbs/${File(videoUri.path!!).nameWithoutExtension}/$sec.jpg"
                )
                ThumbItem(videoUri.toString(), file, picNumber.toLong())
            }
            delay(800)
            withContext(Dispatchers.Main) {
                rvThumbnails.adapter = WorkingActivityAdapter(this@WorkingActivity,
                    absolutePath, thumbs,eachPicWidth,picNumber,eachPicDuration)
            }

            if(linkScrollEnabled){ startScrollerSync() }
            delay(200)
            startVideoTimeSync()

        }

        //系统手势监听：返回键重写
        onBackPressedDispatcher.addCallback(this, object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                MediaCodec.createDecoderByType("video/avc").release()
                player.release()
                finish()
            }
        })
    }//onCreate END


    //runnable-1 - 根据视频时间更新进度条位置: 主控：视频时间  被控：进度条位置
    private val syncScrollTaskHandler = Handler(Looper.getMainLooper())
    private val syncScrollTask = object : Runnable {
        override fun run() {
            //基于实时读取视频当前时间的图片滚动，模拟连续刷新
            if (playerEnd){
                playerEnd = false
                notice("视频已播放至结尾,返回当前光标位置",1000)
                val recyclerView = findViewById<RecyclerView>(R.id.rvThumbnails)
                val totalScrollerLength = recyclerView.computeHorizontalScrollRange()
                val currentScrollerPositionSeek = recyclerView.computeHorizontalScrollOffset()
                val percent = currentScrollerPositionSeek.toFloat() / totalScrollerLength
                val seekToMs = (percent * player.duration).toLong()
                player.seekTo(seekToMs)
            }
            val gap = 16L

            val scrollParam1 = (player.currentPosition / eachPicDuration).toInt()
            val scrollParam2 = ((player.currentPosition - scrollParam1*eachPicDuration)*eachPicWidth/eachPicDuration).toInt()

            val recyclerView = findViewById<RecyclerView>(R.id.rvThumbnails)
            val lm = recyclerView.layoutManager as LinearLayoutManager
            lm.scrollToPositionWithOffset(scrollParam1, -scrollParam2)
            syncScrollTaskHandler.postDelayed(this, gap)
        }
    }
    private fun startScrollerSync() {
        if (dontScrollThisTime){
            dontScrollThisTime = false
            return
        }
        syncScrollTaskRunning = true
        syncScrollTaskHandler.post(syncScrollTask)
    }
    private fun stopScrollerSync() {
        syncScrollTaskRunning = false
        syncScrollTaskHandler.removeCallbacks(syncScrollTask)
    }
    //runnable-2 - 根据视频时间更新时间戳: 主控：视频时间  被控：时间戳
    private val videoTimeSyncHandler = Handler(Looper.getMainLooper())
    private var videoTimeSync = object : Runnable{
        override fun run() {
            val currentPosition = player.currentPosition
            tvCurrentTime.text = formatTime(currentPosition)
            videoTimeSyncHandler.post(this)
        }
    }
    private fun startVideoTimeSync() {
        videoTimeSyncHandler.post(videoTimeSync)
    }
    private fun stopVideoTimeSync() {
        videoTimeSyncHandler.removeCallbacks(videoTimeSync)
    }
    //runnable-3 - 视频智能倍速滚动
    private var videoSmartScrollRunning = false
    private val videoSmartScrollHandler = Handler(Looper.getMainLooper())
    private var videoSmartScroll = object : Runnable{
        override fun run() {
            val recyclerView = findViewById<RecyclerView>(R.id.rvThumbnails)
            var delayGap = if (dragging){ 30L } else{ 30L }
            val videoPosition = (player.currentPosition)
            val scrollerPosition =  player.duration * (recyclerView.computeHorizontalScrollOffset().toFloat()/recyclerView.computeHorizontalScrollRange())
            if (scrollerPosition < videoPosition +100) {
                player.pause()
            }else{
                val positionGap = scrollerPosition - videoPosition
                if (positionGap > 5000){
                    player.seekTo(scrollerPosition.toLong())
                }
                var speed5 = (((positionGap / 100).toInt()) /10.0).toFloat()
                if (speed5 > lastPlaySpeed){
                    speed5 = speed5 + 0.1f
                }else if(speed5 < lastPlaySpeed){
                    speed5 = speed5 - 0.1f
                }

                if (speed5 > 0f){
                    player.setPlaybackSpeed(speed5)
                }else{
                    player.play()
                }

                videoSmartScrollHandler.postDelayed(this,delayGap)
            }
        }
    }
    private fun startVideoSmartScroll() {
        stopScrollerSync()
        stopVideoTimeSync()
        player.volume = 0f
        player.play()
        if (singleTap){
            singleTap = false
            return
        }
        videoSmartScrollHandler.post(videoSmartScroll)
    }
    private fun stopVideoSmartScroll() {
        videoSmartScrollRunning = false
        videoSmartScrollHandler.removeCallbacks(videoSmartScroll)
    }
    //runnable-4 - 视频Seek滚动
    private val videoSeekHandler = Handler(Looper.getMainLooper())
    private var videoSeek = object : Runnable{
        override fun run() {
            val recyclerView = findViewById<RecyclerView>(R.id.rvThumbnails)
            val currentScrollerPositionSeek = recyclerView.computeHorizontalScrollOffset()
            if (currentScrollerPositionSeek != lastScrollerPositionSeek){
                lastScrollerPositionSeek = currentScrollerPositionSeek
                if (currentTime != 0L && currentTime < (player.duration/1000*1000) - 200) {
                    if (isSeekReady) {
                        isSeekReady = false
                        seekJob()
                    }
                } else if (currentTime < 100){
                    player.seekTo(0)
                } else if (currentTime > (player.duration/1000*1000) - 200) {
                    lifecycleScope.launch {
                        player.pause()
                        player.seekTo(player.duration - 500)
                        delay(200)
                        player.seekTo(player.duration)
                    }
                }
                videoSeekHandler.post(this)
            }else{
                return
            }
        }
    }
    private fun startVideoSeek() {
        if (playerEnd) playerEnd = false
        videoSeekHandler.post(videoSeek)
    }
    private fun stopVideoSeek() {
        videoSeekHandler.removeCallbacks(videoSeek)
    }


    //job-1 -seek
    private var seekJob: Job? = null
    private fun seekJob() {
        seekJob?.cancel()
        seekJob = lifecycleScope.launch {
            val recyclerView = findViewById<RecyclerView>(R.id.rvThumbnails)
            val totalWidth = recyclerView.computeHorizontalScrollRange()
            val offset     = recyclerView.computeHorizontalScrollOffset()
            val percent    = offset.toFloat() / totalWidth
            val seekToMs   = (percent * player.duration).toLong()
            player.seekTo(seekToMs)
            isSeekReady = false
            //isSeekReady需在播放器状态监听器中更改
        }
    }
    //job-2 -showNotice
    private var showNoticeJob: Job? = null
    private fun showNoticeJob(text: String, duration: Long) {
        showNoticeJob?.cancel()
        showNoticeJob = lifecycleScope.launch {
            val notice = findViewById<TextView>(R.id.notice)
            val noticeCard = findViewById<CardView>(R.id.noticeCard)
            noticeCard.visibility = View.VISIBLE
            notice.text = text
            delay(duration)
            noticeCard.visibility = View.GONE
        }
    }
    private fun notice(text: String, duration: Long) {
        showNoticeJob(text, duration)
    }

    //其他生命周期
    override fun onEnterAnimationComplete() {
        super.onEnterAnimationComplete()
        player.prepare()
        if (wasPlaying){
            player.playWhenReady = true
            wasPlaying = false
        }else{
            wasPlaying = false
        }

    }

    override fun onPause() {
        super.onPause()
        if (player.isPlaying){ wasPlaying = true }
        player.stop()
        stopVideoSeek()
        stopVideoSmartScroll()
    }

    override fun onStop() {
        super.onStop()
        stopVideoSeek()
        stopVideoSmartScroll()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVideoSeek()
        stopVideoSmartScroll()
        stopVideoTimeSync()
        stopScrollerSync()
        if (!cannotOpen){
            player.release()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
                currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                false
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    @SuppressLint("ChromeOsOnConfigurationChanged")
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        notice("APP将不会响应深色模式切换或小窗等状态变化",10000)
    }


    //Functions
    //功能：设置值
    @SuppressLint("SetTextI18n")
    private fun setValue(content:String){
        if (content.isEmpty()){
            notice("您似乎什么都没有填......",2000)
            return
        }
        val number = content.toFloat()
        if (number == 1145f){
            notice("设置失败：车的长度不能是恶臭数字",2500)
            return
        }
        else if (number > 440){
            notice("设置失败：Bro的车似乎有点过长了",2500)
            return
        }else if (number == 0f){
            notice("设置失败：Bro的车没有长度",2500)
            return
        }else{
            areaLength = number
            notice("已将计算区域长度设为${areaLength}米",2000)
            val emuType = findViewById<TextView>(R.id.emuType)
            emuType.text = "${areaLength}米(手动填写)"
        }
    }

    private fun getFileName(uri: Uri): String? {
        return if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME).let { index ->
                        cursor.getString(index)
                    }

                } else null
            }
        } else {
            uri.path?.substringAfterLast('/')
        }
    }
    private fun appendNumberToFileName(name: String, addon: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) {
            val base = name.substring(0, dot)
            val ext = name.substring(dot)
            "${base}_${addon}$ext"
        } else {
            "${name}_${addon}"
        }
    }
    private fun saveCopyWithAddon(context: Context, uri: Uri, addon: String) {
        val fileName = getFileName(uri) ?: return
        val newName = appendNumberToFileName(fileName, addon)

        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, newName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES+"/补齐速度后的视频")
        }

        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val newUri = resolver.insert(collection, contentValues) ?: return

        resolver.openInputStream(uri)?.use { input ->
            resolver.openOutputStream(newUri)?.use { output ->
                input.copyTo(output)
            }
        }
        notice("已保存为副本,路径:Pictures/补齐速度后的视频",2000)
        newFileSaved = true


    }

    private fun pauseVideo(){
        stopVideoTimeSync()
        player.pause()
    }
    private fun playVideo(){
        if (!scrolling){
            player.play()
            if (linkScrollEnabled){ startScrollerSync() }
            lifecycleScope.launch {
                delay(1000)
                startVideoTimeSync()
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun saveFrameToInternal(name: String): File {
        val playerView = findViewById<PlayerView>(R.id.playerView)
        val bmp = (playerView.videoSurfaceView as TextureView).bitmap
            ?: throw IllegalStateException("截图失败")

        val file = File(filesDir, "$name.jpg")
        FileOutputStream(file).use { fos ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos)
        }
        return file
    }

    private fun getAbsoluteFilePath(context: Context, uri: Uri): String? {
        var absolutePath: String? = null
        // 检查URI
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            val projection = arrayOf(MediaStore.Video.Media.DATA)
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val columnIndex = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                    absolutePath = it.getString(columnIndex) //绝对路径
                }
            }
        } else if (uri.scheme == ContentResolver.SCHEME_FILE) {
            absolutePath = uri.path //文件URI直接获取路径
        }
        // 验证路径是否存在
        if (absolutePath != null && File(absolutePath).exists()) {
            return absolutePath
        }
        return null
    }

    private fun buttonRefresh(){
        val PauseImage = findViewById<ImageView>(R.id.PauseImage)
        val ContinueImage = findViewById<ImageView>(R.id.ContinueImage)
        if (player.isPlaying){
            PauseImage.visibility = View.VISIBLE
            ContinueImage.visibility = View.GONE
        }
        else{
            PauseImage.visibility = View.GONE
            ContinueImage.visibility = View.VISIBLE
        }
    }

    private fun preCheck(){
        isCompatibleDevice = isCompatibleDevice()
    }

    //格式化时间显示
    @SuppressLint("DefaultLocale")
    private fun formatTime(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        //val hours = totalSeconds / 3600      //此类视频基本不可能超过一小时
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val millis = milliseconds % 1000 // 提取毫秒部分
        return String.format("%02d:%02d.%03d",  minutes, seconds, millis)
    }
    private fun formatTime2(raw: Long): String {
        val cent  = raw % 1000
        val totalSec = raw / 1000
        val min  = totalSec / 60
        val sec  = totalSec % 60
        return "%02d:%02d.%03d".format(min, sec, cent)
    }
    private fun formatTime3(raw: Long): String {
        val cent  = raw % 1000
        val totalSec = raw / 1000
        val min  = totalSec / 60
        val sec  = totalSec % 60
        return "%01d分%02d秒%03d毫秒".format(min, sec, cent)
    }


}//class END