package com.hmv.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
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
 * 支持播放进度保存与续播：
 * - [EXTRA_MEDIA_ID] 用于标识媒体，持久化播放位置
 * - onPause/onStop 时自动保存进度
 * - onCreate 时自动恢复上次播放位置
 */
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var loading: ProgressBar
    private lateinit var errorView: TextView
    private var mediaId: String = ""
    private var seekToOnReady: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.player_view)
        loading = findViewById(R.id.loading)
        errorView = findViewById(R.id.error_view)

        val url = intent.getStringExtra(EXTRA_URL) ?: run {
            finish()
            return
        }
        mediaId = intent.getStringExtra(EXTRA_MEDIA_ID) ?: ""
        title = intent.getStringExtra(EXTRA_TITLE)

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
                    Player.STATE_BUFFERING -> loading.visibility = View.VISIBLE
                    Player.STATE_READY -> {
                        loading.visibility = View.GONE
                        // 准备就绪后跳转到保存的位置
                        if (seekToOnReady > 0) {
                            player.seekTo(seekToOnReady)
                            seekToOnReady = 0L
                        }
                    }
                    Player.STATE_ENDED -> {
                        loading.visibility = View.GONE
                        // 播放结束，清除进度
                        if (mediaId.isNotEmpty()) {
                            PlayProgressManager.clear(this@PlayerActivity, mediaId)
                        }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                loading.visibility = View.GONE
                errorView.text = getString(R.string.play_error, error.errorCodeName)
                errorView.visibility = View.VISIBLE
            }
        })

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
    }

    override fun onPause() {
        super.onPause()
        saveProgress()
        player?.pause()
    }

    override fun onStop() {
        super.onStop()
        saveProgress()
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
