package com.hmv.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * 播放页面。
 *
 * 接收 [EXTRA_URL]（http://ip:端口/media/{id}）交给 ExoPlayer 播放，
 * 拖动进度条时播放器会自动发送 HTTP Range 请求，验证服务端的 Range 能力。
 *
 * 支持：
 * - 播放进度保存与续播
 * - 横竖屏切换 + 沉浸式全屏
 * - 网络断开检测与提示
 */
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var loading: ProgressBar
    private lateinit var errorView: TextView
    private lateinit var fullscreenBtn: ImageButton
    private var mediaId: String = ""
    private var seekToOnReady: Long = 0L
    private var isFullscreen = false
    private var isRemotePlayback = false
    private var networkMonitor: NetworkMonitor? = null
    private var isNetworkLost = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.player_view)
        loading = findViewById(R.id.loading)
        errorView = findViewById(R.id.error_view)
        fullscreenBtn = findViewById(R.id.btn_fullscreen)

        val url = intent.getStringExtra(EXTRA_URL) ?: run {
            finish()
            return
        }
        mediaId = intent.getStringExtra(EXTRA_MEDIA_ID) ?: ""
        title = intent.getStringExtra(EXTRA_TITLE)
        isRemotePlayback = !url.startsWith("http://127.0.0.1") && !url.startsWith("http://localhost")

        // 恢复上次播放进度
        seekToOnReady = if (mediaId.isNotEmpty()) {
            PlayProgressManager.restore(this, mediaId)
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
                        if (seekToOnReady > 0) {
                            player.seekTo(seekToOnReady)
                            seekToOnReady = 0L
                        }
                    }
                    Player.STATE_ENDED -> {
                        loading.visibility = View.GONE
                        if (mediaId.isNotEmpty()) {
                            PlayProgressManager.clear(this@PlayerActivity, mediaId)
                        }
                    }
                }
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

        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(intent.getStringExtra(EXTRA_TITLE) ?: "")
                    .build()
            )
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true

        // 初始进入全屏
        enterFullscreen()

        // 监听网络状态（仅远程播放时）
        if (isRemotePlayback) {
            startNetworkMonitor()
        }
    }

    private fun startNetworkMonitor() {
        networkMonitor = NetworkMonitor(this)
        networkMonitor?.startListening(object : NetworkMonitor.NetworkListener {
            override fun onNetworkAvailable() {
                runOnUiThread {
                    if (isNetworkLost) {
                        isNetworkLost = false
                        // 网络恢复，尝试重新加载
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

    private fun saveProgress() {
        if (mediaId.isEmpty()) return
        val p = player ?: return
        val position = p.currentPosition
        val duration = p.duration
        if (duration > 0) {
            PlayProgressManager.save(this, mediaId, position, duration)
        }
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MEDIA_ID = "extra_media_id"

        fun createIntent(context: Context, url: String, title: String, mediaId: String = ""): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_MEDIA_ID, mediaId)
    }
}
