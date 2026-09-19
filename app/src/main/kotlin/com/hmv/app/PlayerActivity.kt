package com.hmv.app

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * 播放页面。
 *
 * 支持播放队列：
 * - 通过 [EXTRA_URLS] / [EXTRA_TITLES] / [EXTRA_MEDIA_IDS] 传入列表
 * - ExoPlayer 使用 [player.setMediaItems] 实现连续播放
 * - 通过 [EXTRA_START_INDEX] 指定起始播放位置
 *
 * 支持接收投屏指令（BroadcastReceiver）。
 */
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var loading: ProgressBar
    private lateinit var errorView: TextView
    private lateinit var fullscreenBtn: ImageButton
    private lateinit var queueInfo: TextView
    private lateinit var speedBtn: ImageButton
    private lateinit var speedLabel: TextView

    private var urls: List<String> = emptyList()
    private var titles: List<String> = emptyList()
    private var mediaIds: List<String> = emptyList()
    private var startIndex: Int = 0
    private var seekToOnReady: Long = 0L
    private var isFullscreen = false
    private var isRemotePlayback = false
    private var networkMonitor: NetworkMonitor? = null
    private var isNetworkLost = false

    private val speedOptions = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    private var currentSpeedIndex = 2 // 默认 1.0x

    private val castReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MediaServerService.ACTION_CAST_PLAY) {
                val mediaId = intent.getStringExtra(MediaServerService.EXTRA_CAST_MEDIA_ID) ?: return
                val title = intent.getStringExtra(MediaServerService.EXTRA_CAST_TITLE) ?: ""
                val position = intent.getLongExtra(MediaServerService.EXTRA_CAST_POSITION, 0L)
                handleCastCommand(mediaId, title, position)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.player_view)
        loading = findViewById(R.id.loading)
        errorView = findViewById(R.id.error_view)
        fullscreenBtn = findViewById(R.id.btn_fullscreen)
        queueInfo = findViewById(R.id.queue_info)
        speedBtn = findViewById(R.id.btn_speed)
        speedLabel = findViewById(R.id.speed_label)
        val queueBtn = findViewById<ImageButton>(R.id.btn_queue)

        speedBtn.setOnClickListener { cycleSpeed() }
        queueBtn.setOnClickListener { showQueuePanel() }

        // 解析传入的媒体列表
        urls = intent.getStringArrayListExtra(EXTRA_URLS) ?: run {
            // 兼容旧的单文件启动方式
            val singleUrl = intent.getStringExtra(EXTRA_URL) ?: run {
                finish()
                return
            }
            listOf(singleUrl)
        }
        titles = intent.getStringArrayListExtra(EXTRA_TITLES) ?: urls.map { "" }
        mediaIds = intent.getStringArrayListExtra(EXTRA_MEDIA_IDS) ?: urls.map { "" }
        startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)

        isRemotePlayback = urls.any { !it.startsWith("127.0.0.1") && !it.startsWith("localhost") }

        // 恢复上次播放进度
        val currentMediaId = mediaIds.getOrElse(startIndex) { "" }
        seekToOnReady = if (currentMediaId.isNotEmpty()) {
            PlayProgressManager.restore(this, currentMediaId)
        } else 0L

        loading.visibility = View.VISIBLE

        val player = ExoPlayer.Builder(this).build()
        playerView.player = player
        this.player = player

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        loading.visibility = View.VISIBLE
                        if (isNetworkLost) {
                            loading.visibility = View.GONE
                            errorView.text = getString(R.string.network_lost)
                            errorView.visibility = View.VISIBLE
                        }
                    }
                    Player.STATE_READY -> {
                        loading.visibility = View.GONE
                        errorView.visibility = View.GONE
                        updateQueueInfo()
                        if (seekToOnReady > 0) {
                            player.seekTo(seekToOnReady)
                            seekToOnReady = 0L
                        }
                    }
                    Player.STATE_ENDED -> {
                        loading.visibility = View.GONE
                        val pos = player.currentMediaItemIndex
                        val id = mediaIds.getOrElse(pos) { "" }
                        if (id.isNotEmpty()) {
                            PlayProgressManager.clear(this@PlayerActivity, id)
                        }
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: ExoMediaItem?, reason: Int) {
                // 切换到下一个媒体项时，清除上一个的进度
                updateQueueInfo()
            }

            override fun onPlayerError(error: PlaybackException) {
                loading.visibility = View.GONE
                val message = if (isNetworkLost) {
                    getString(R.string.network_lost)
                } else {
                    getString(R.string.play_error, error.errorCodeName)
                }
                errorView.text = message
                errorView.visibility = View.VISIBLE
            }
        })

        fullscreenBtn.setOnClickListener { toggleFullscreen() }

        // 构建播放队列
        val mediaItems = mutableListOf<ExoMediaItem>()
        for (i in urls.indices) {
            val item = ExoMediaItem.Builder()
                .setUri(urls[i])
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(titles.getOrElse(i) { "" })
                        .build()
                )
                .build()
            mediaItems.add(item)
        }

        player.setMediaItems(mediaItems)
        player.prepare()
        player.seekTo(startIndex, 0L)
        player.playWhenReady = true

        // 更新队列信息
        updateQueueInfo()

        // 初始进入全屏
        enterFullscreen()

        // 监听网络状态（仅远程播放时）
        if (isRemotePlayback) {
            startNetworkMonitor()
        }

        // 注册投屏指令接收器
        val filter = IntentFilter(MediaServerService.ACTION_CAST_PLAY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(castReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(castReceiver, filter)
        }
    }

    private fun updateQueueInfo() {
        if (urls.size <= 1) {
            queueInfo.visibility = View.GONE
            return
        }
        val pos = player?.currentMediaItemIndex?.plus(1) ?: 1
        queueInfo.text = getString(R.string.queue_info, pos, urls.size)
        queueInfo.visibility = View.VISIBLE
    }

    private fun handleCastCommand(mediaId: String, title: String, position: Long) {
        // 查找媒体 ID 在当前队列中的位置
        val index = mediaIds.indexOf(mediaId)
        if (index >= 0) {
            player?.seekTo(index, position)
            player?.playWhenReady = true
            Toast.makeText(this, getString(R.string.cast_playing, title), Toast.LENGTH_SHORT).show()
        } else {
            // 队列中没有该媒体，提示
            Toast.makeText(this, getString(R.string.cast_not_found, title), Toast.LENGTH_SHORT).show()
        }
    }

    private fun cycleSpeed() {
        currentSpeedIndex = (currentSpeedIndex + 1) % speedOptions.size
        val speed = speedOptions[currentSpeedIndex]
        player?.setPlaybackSpeed(speed)
        updateSpeedLabel()
    }

    private fun updateSpeedLabel() {
        speedLabel.text = "${speedOptions[currentSpeedIndex]}x"
    }

    private fun showQueuePanel() {
        val panel = layoutInflater.inflate(R.layout.sheet_queue, null)
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        dialog.setContentView(panel)

        val queueList = panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.queue_list)
        val emptyView = panel.findViewById<TextView>(R.id.queue_empty)

        val adapter = QueueAdapter(
            onItemClicked = { position ->
                player?.seekTo(position, 0)
                player?.playWhenReady = true
                dialog.dismiss()
            },
            onItemRemoved = { position ->
                removeQueueItem(position)
                if (urls.isEmpty()) {
                    dialog.dismiss()
                    finish()
                }
            }
        )

        queueList.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        queueList.adapter = adapter

        val queueItems = titles.mapIndexed { index, title ->
            QueueAdapter.QueueItem(
                title = title.ifEmpty { getString(R.string.unknown_title) },
                subtitle = mediaIds.getOrElse(index) { "" }
            )
        }
        val currentPos = player?.currentMediaItemIndex ?: 0
        adapter.submit(queueItems, currentPos)

        emptyView.visibility = if (queueItems.isEmpty()) View.VISIBLE else View.GONE
        queueList.visibility = if (queueItems.isEmpty()) View.GONE else View.VISIBLE

        dialog.show()
    }

    private fun removeQueueItem(position: Int) {
        if (position < 0 || position >= urls.size) return

        val wasPlaying = player?.isPlaying == true
        val currentPos = player?.currentMediaItemIndex ?: 0

        urls = urls.toMutableList().apply { removeAt(position) }
        titles = titles.toMutableList().apply { removeAt(position) }
        mediaIds = mediaIds.toMutableList().apply { removeAt(position) }

        // 重建播放器
        player?.release()
        val p = ExoPlayer.Builder(this).build()
        playerView.player = p
        this.player = p

        val mediaItems = urls.map { ExoMediaItem.fromUri(it) }
        p.setMediaItems(mediaItems)
        p.prepare()

        // 恢复播放位置
        val newPos = if (position < currentPos) currentPos - 1
        else if (position == currentPos && currentPos >= urls.size) urls.size - 1
        else currentPos

        if (newPos >= 0 && newPos < urls.size) {
            p.seekTo(newPos, 0L)
            p.playWhenReady = wasPlaying
        }

        updateQueueInfo()
        Toast.makeText(this, getString(R.string.queue_item_remove), Toast.LENGTH_SHORT).show()
    }

    private fun startNetworkMonitor() {
        networkMonitor = NetworkMonitor(this)
        networkMonitor?.startListening(object : NetworkMonitor.NetworkListener {
            override fun onNetworkAvailable() {
                runOnUiThread {
                    if (isNetworkLost) {
                        isNetworkLost = false
                        errorView.visibility = View.GONE
                        loading.visibility = View.VISIBLE
                        player?.let { p ->
                            p.prepare()
                            p.playWhenReady = true
                        }
                        Toast.makeText(this@PlayerActivity, R.string.network_restored, Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onNetworkLost() {
                runOnUiThread {
                    isNetworkLost = true
                    if (player?.isPlaying == true) {
                        player?.pause()
                    }
                    loading.visibility = View.GONE
                    errorView.text = getString(R.string.network_lost)
                    errorView.visibility = View.VISIBLE
                }
            }
        })
    }

    @SuppressLint("InlinedApi")
    private fun enterFullscreen() {
        isFullscreen = true
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        }
        fullscreenBtn.setImageResource(R.drawable.ic_fullscreen_exit)
    }

    @SuppressLint("InlinedApi")
    private fun exitFullscreen() {
        isFullscreen = false
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
        fullscreenBtn.setImageResource(R.drawable.ic_fullscreen_enter)
    }

    private fun toggleFullscreen() {
        if (isFullscreen) exitFullscreen() else enterFullscreen()
    }

    override fun onPause() {
        super.onPause()
        saveProgress()
        player?.pause()
    }

    override fun onStop() {
        super.onStop()
        saveProgress()
        networkMonitor?.stopListening()
        networkMonitor = null
        playerView.player = null
        player?.release()
        player = null
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(castReceiver)
        } catch (_: Exception) {}
    }

    private fun saveProgress() {
        val p = player ?: return
        val pos = p.currentMediaItemIndex
        val id = mediaIds.getOrElse(pos) { "" }
        if (id.isEmpty()) return
        val position = p.currentPosition
        val duration = p.duration
        if (duration > 0) {
            PlayProgressManager.save(this, id, position, duration)
        }
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_URLS = "extra_urls"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TITLES = "extra_titles"
        const val EXTRA_MEDIA_ID = "extra_media_id"
        const val EXTRA_MEDIA_IDS = "extra_media_ids"
        const val EXTRA_START_INDEX = "extra_start_index"

        /** 单文件启动（兼容旧版） */
        fun createIntent(context: Context, url: String, title: String, mediaId: String = ""): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_MEDIA_ID, mediaId)

        /** 播放队列启动 */
        fun createIntent(
            context: Context,
            urls: List<String>,
            titles: List<String>,
            mediaIds: List<String>,
            startIndex: Int
        ): Intent =
            Intent(context, PlayerActivity::class.java)
                .putStringArrayListExtra(EXTRA_URLS, ArrayList(urls))
                .putStringArrayListExtra(EXTRA_TITLES, ArrayList(titles))
                .putStringArrayListExtra(EXTRA_MEDIA_IDS, ArrayList(mediaIds))
                .putExtra(EXTRA_START_INDEX, startIndex)
    }
}
