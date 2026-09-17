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
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
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
    private var currentDevice: NsdHelper.DiscoveredDevice? = null
    private val remoteClient = RemoteMediaClient()
    private val statusView by lazy { findViewById<TextView>(R.id.status) }
    private val emptyHint by lazy { TextView(this).apply { /* placeholder, set in onCreate */ } }
    private val searchInput by lazy { findViewById<EditText>(R.id.search_input) }
    private val filterAll by lazy { findViewById<TextView>(R.id.filter_all) }
    private val filterVideo by lazy { findViewById<TextView>(R.id.filter_video) }
    private val filterAudio by lazy { findViewById<TextView>(R.id.filter_audio) }
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

        val mediaList = findViewById<RecyclerView>(R.id.media_list)
        mediaList.layoutManager = LinearLayoutManager(this)
        mediaList.adapter = mediaAdapter

        findViewById<RecyclerView>(R.id.device_list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = this@MainActivity.deviceAdapter
        }

        setupSearchAndFilter()
        requestMediaPermissions()
    }

    private fun setupSearchAndFilter() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                mediaAdapter.setSearchQuery(s?.toString() ?: "")
                updateEmptyHint()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        filterAll.setOnClickListener { setFilter(MediaAdapter.TypeFilter.ALL) }
        filterVideo.setOnClickListener { setFilter(MediaAdapter.TypeFilter.VIDEO) }
        filterAudio.setOnClickListener { setFilter(MediaAdapter.TypeFilter.AUDIO) }
    }

    private fun setFilter(filter: MediaAdapter.TypeFilter) {
        mediaAdapter.setTypeFilter(filter)
        updateFilterUI(filter)
        updateEmptyHint()
    }

    private fun updateFilterUI(filter: MediaAdapter.TypeFilter) {
        val selectedColor = ContextCompat.getColor(this, R.color.filter_selected)
        val unselectedColor = ContextCompat.getColor(this, R.color.filter_unselected)

        filterAll.setTextColor(if (filter == MediaAdapter.TypeFilter.ALL) selectedColor else unselectedColor)
        filterAll.textSize = if (filter == MediaAdapter.TypeFilter.ALL) 15f else 13f
        filterVideo.setTextColor(if (filter == MediaAdapter.TypeFilter.VIDEO) selectedColor else unselectedColor)
        filterVideo.textSize = if (filter == MediaAdapter.TypeFilter.VIDEO) 15f else 13f
        filterAudio.setTextColor(if (filter == MediaAdapter.TypeFilter.AUDIO) selectedColor else unselectedColor)
        filterAudio.textSize = if (filter == MediaAdapter.TypeFilter.AUDIO) 15f else 13f
    }

    private fun updateEmptyHint() {
        val hasItems = mediaAdapter.itemCount > 0
        val hintText = if (searchInput.text.isNotEmpty() || mediaAdapter.itemCount == 0) {
            getString(R.string.no_match)
        } else {
            getString(R.string.no_media)
        }
        emptyHint.text = hintText
        emptyHint.visibility = if (hasItems) android.view.View.GONE else android.view.View.VISIBLE
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
        updateEmptyHint()
    }

    private fun startServerAsync() {
        statusView.text = getString(R.string.scanning)
        Thread {
            val items = MediaScanner(this).scan(MediaScanner.CollectionKind.VIDEO) +
                MediaScanner(this).scan(MediaScanner.CollectionKind.AUDIO)
            runOnUiThread {
                mediaAdapter.submit(items)
                updateEmptyHint()
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
        val device = currentDevice
        if (device != null) {
            val url = remoteClient.getMediaUrl(
                RemoteMediaClient.RemoteDevice(device.name, device.host, device.port),
                item.id
            )
            startActivity(PlayerActivity.createIntent(this, url, item.title, item.id))
        } else {
            val port = service?.port ?: return
            val url = "http://127.0.0.1:$port/media/${item.id}"
            startActivity(PlayerActivity.createIntent(this, url, item.title, item.id))
        }
    }

    private fun onDeviceClicked(device: NsdHelper.DiscoveredDevice) {
        currentDevice = device
        statusView.text = "正在获取 ${device.name} 的媒体列表..."

        val remoteDevice = RemoteMediaClient.RemoteDevice(device.name, device.host, device.port)
        remoteClient.fetchMediaList(remoteDevice) { result ->
            runOnUiThread {
                result.onSuccess { items ->
                    mediaAdapter.submit(items)
                    updateEmptyHint()
                    statusView.text = "${device.name} - ${items.size} 个媒体文件"
                }.onFailure { e ->
                    statusView.text = "获取失败: ${e.message}"
                }
            }
        }
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
