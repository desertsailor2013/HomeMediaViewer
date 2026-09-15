package com.hmv.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hmv.server.HttpRangeServer
import com.hmv.server.MediaItem
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {

    private var server: HttpRangeServer? = null
    private val statusView by lazy { findViewById<TextView>(R.id.status) }
    private val emptyHint by lazy { findViewById<TextView>(R.id.empty_hint) }
    private val adapter = MediaAdapter { onMediaClicked(it) }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { granted -> granted }) startServer() else showPermissionDenied()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<RecyclerView>(R.id.media_list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        requestMediaPermissions()
    }

    private fun requestMediaPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            val needed = mutableListOf<String>()
            if (!hasPermission(Manifest.permission.READ_MEDIA_VIDEO)) needed += Manifest.permission.READ_MEDIA_VIDEO
            if (!hasPermission(Manifest.permission.READ_MEDIA_AUDIO)) needed += Manifest.permission.READ_MEDIA_AUDIO
            if (needed.isEmpty()) startServer() else permissionLauncher.launch(needed.toTypedArray())
        } else {
            if (!hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
            } else {
                startServer()
            }
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun showPermissionDenied() {
        statusView.text = getString(R.string.permission_denied)
        adapter.submit(emptyList())
        emptyHint.visibility = android.view.View.VISIBLE
    }

    private fun startServer() {
        statusView.text = getString(R.string.scanning)
        val items = MediaScanner(this).scan(MediaScanner.CollectionKind.VIDEO) +
            MediaScanner(this).scan(MediaScanner.CollectionKind.AUDIO)
        adapter.submit(items)
        emptyHint.visibility = if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE

        val repo = ContentMediaRepository(this, items)
        val s = HttpRangeServer(repo)
        s.start()
        server = s
        statusView.text = getString(R.string.server_started, s.port) + "\n" +
            getString(R.string.local_ips) + "：\n" +
            localIps().joinToString("\n") { "  http://$it:${s.port}/media" } +
            "\n（点击下方条目本地播放验证）"
    }

    private fun onMediaClicked(item: MediaItem) {
        val port = server?.port ?: return
        val url = "http://127.0.0.1:$port/media/${item.id}"
        startActivity(PlayerActivity.createIntent(this, url, item.title))
    }

    private fun localIps(): List<String> {
        val result = mutableListOf<String>()
        try {
            NetworkInterface.getNetworkInterfaces().toList().forEach { ni ->
                if (ni.isUp && !ni.isLoopback) {
                    ni.inetAddresses.toList().filter { it is Inet4Address && !it.isLoopbackAddress }
                        .forEach { result += it.hostAddress }
                }
            }
        } catch (_: Exception) {}
        return result
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.close()
        server = null
    }
}