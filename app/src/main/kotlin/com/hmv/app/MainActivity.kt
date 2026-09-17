package com.hmv.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hmv.server.MediaItem

class MainActivity : AppCompatActivity() {

    private var service: MediaServerService? = null
    private var bound = false
    private var nsdHelper: NsdHelper? = null
    private val statusView by lazy { findViewById<TextView>(R.id.status) }
    private val emptyHint by lazy { findViewById<TextView>(R.id.empty_hint) }
    private val mediaAdapter = MediaAdapter { onMediaClicked(it) }
    private val deviceAdapter = DeviceAdapter { onDeviceClicked(it) }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as MediaServerService.LocalBinder
            service = localBinder.getService()
            bound = true
            startDeviceDiscovery()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { granted -> granted }) startServerAsync() else showPermissionDenied()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<RecyclerView>(R.id.media_list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.mediaAdapter
        }

        findViewById<RecyclerView>(R.id.device_list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = this@MainActivity.deviceAdapter
        }

        requestMediaPermissions()
    }

    private fun requestMediaPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            val needed = mutableListOf<String>()
            if (!hasPermission(Manifest.permission.READ_MEDIA_VIDEO)) needed += Manifest.permission.READ_MEDIA_VIDEO
            if (!hasPermission(Manifest.permission.READ_MEDIA_AUDIO)) needed += Manifest.permission.READ_MEDIA_AUDIO
            if (needed.isEmpty()) startServerAsync() else permissionLauncher.launch(needed.toTypedArray())
        } else {
            if (!hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
            } else {
                startServerAsync()
            }
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun showPermissionDenied() {
        statusView.text = getString(R.string.permission_denied)
        mediaAdapter.submit(emptyList())
        emptyHint.visibility = android.view.View.VISIBLE
    }

    private fun startServerAsync() {
        statusView.text = getString(R.string.scanning)
        Thread {
            val items = MediaScanner(this).scan(MediaScanner.CollectionKind.VIDEO) +
                MediaScanner(this).scan(MediaScanner.CollectionKind.AUDIO)
            runOnUiThread {
                mediaAdapter.submit(items)
                emptyHint.visibility = if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                startService(items)
            }
        }.start()
    }

    private fun startService(items: List<MediaItem>) {
        val serviceIntent = Intent(this, MediaServerService::class.java).apply {
            action = MediaServerService.ACTION_START
            putExtra(MediaServerService.EXTRA_ITEMS, ArrayList(items))
        }
        startForegroundService(serviceIntent)
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)

        statusView.text = "服务启动中..."
    }

    private fun startDeviceDiscovery() {
        nsdHelper = NsdHelper(this)
        nsdHelper?.startDiscovery(object : NsdHelper.DeviceListener {
            override fun onDeviceFound(device: NsdHelper.DiscoveredDevice) {
                runOnUiThread {
                    deviceAdapter.addDevice(device)
                }
            }

            override fun onDeviceLost(device: NsdHelper.DiscoveredDevice) {
                runOnUiThread {
                    deviceAdapter.removeDevice(device)
                }
            }

            override fun onDiscoveryFailed(errorCode: Int) {
                runOnUiThread {
                    statusView.text = "设备发现失败: $errorCode"
                }
            }
        })
    }

    private fun onMediaClicked(item: MediaItem) {
        val port = service?.port ?: return
        val url = "http://127.0.0.1:$port/media/${item.id}"
        startActivity(PlayerActivity.createIntent(this, url, item.title))
    }

    private fun onDeviceClicked(device: NsdHelper.DiscoveredDevice) {
        val url = "${device.mediaUrl}"
        startActivity(PlayerActivity.createIntent(this, url, device.name))
    }

    override fun onDestroy() {
        super.onDestroy()
        nsdHelper?.stopDiscovery()
        nsdHelper = null
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }
}
