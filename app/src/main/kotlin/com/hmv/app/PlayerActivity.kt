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
 */
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var loading: ProgressBar
    private lateinit var errorView: TextView

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
        title = intent.getStringExtra(EXTRA_TITLE)

        loading.visibility = View.VISIBLE

        val player = ExoPlayer.Builder(this).build()
        playerView.player = player
        this.player = player

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> loading.visibility = View.VISIBLE
                    Player.STATE_READY, Player.STATE_ENDED -> loading.visibility = View.GONE
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
            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(intent.getStringExtra(EXTRA_TITLE) ?: "").build())
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onStop() {
        super.onStop()
        playerView.player = null
        player?.release()
        player = null
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"

        fun createIntent(context: Context, url: String, title: String): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)
    }
}