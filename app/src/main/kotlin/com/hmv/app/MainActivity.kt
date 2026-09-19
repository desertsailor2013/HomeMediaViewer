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
import android.widget.Toast
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
    private var networkMonitor: NetworkMonitor? = null
    private var currentDevice: NsdHelper.DiscoveredDevice? = null
    private val remoteClient = RemoteMediaClient()
    private val statusView by lazy { findViewById<TextView>(R.id.status) }
    private val emptyHint by lazy { findViewById<TextView>(R.id.empty_hint) }
    private val searchInput by lazy { findViewById<EditText>(R.id.search_input) }
    private val filterAll by lazy { findViewById<TextView>(R.id.filter_all) }
    private val filterVideo by lazy { findViewById<TextView>(R.id.filter_video) }
    private val filterAudio by lazy { findViewById<TextView>(R.id.filter_audio) }
    private val playAllBtn by lazy { findViewById<TextView>(R.id.btn_play_all) }
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
        setupPlayAllButton()
        setupNetworkMonitor()
        requestMediaPermissions()
    }

    private fun setupNetworkMonitor() {
        networkMonitor = NetworkMonitor(this)
        networkMonitor?.startListening(object : NetworkMonitor.NetworkListener {
            override fun onNetworkAvailable() {
                // 网络恢复
            }

            override fun onNetworkLost() {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, R.string.network_lost, Toast.LENGTH_SHORT).show()
                }
            }
        })
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

    private fun setupPlayAllButton() {
        playAllBtn.setOnClickListener {
            val items = mediaAdapter.getCurrentItems()
            if (items.isEmpty()) return@setOnClickListener
            playAll(items, 0)
        }
    }

    private fun playAll(items: List<MediaItem>, startIndex: Int) {
        val urls = mutableListOf<String>()
        val titles = mutableListOf<String>()
        val ids = mutableListOf<String>()

        val device = currentDevice
        val port = service?.port

        for (item in items) {
            val url = if (device != null) {
                remoteClient.getMediaUrl(
                    RemoteMediaClient.RemoteDevice(device.name, device.host, device.port),
                    item.id
                )
            } else if (port != null) {
                "http://127.0.0.1:$port/media/${item.id}"
            } else {
                continue
            }
            urls.add(url)
            titles.add(item.title)
            ids.add(item.id)
        }

        if (urls.isNotEmpty()) {
            startActivity(PlayerActivity.createIntent(this, urls, titles, ids, startIndex))
        }
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
        playAllBtn.visibility = if (hasItems) android.view.View.VISIBLE else android.view.View.GONE
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
            val scanner = MediaScanner(this)
            val items = scanner.scan(MediaScanner.CollectionKind.VIDEO) +
                scanner.scan(MediaScanner.CollectionKind.AUDIO)
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

        statusView.text = getString(R.string.server_starting)
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
                    // 如果当前选中的设备离线，提示并切回本地媒体
                    if (currentDevice?.name == device.name) {
                        currentDevice = null
                        Toast.makeText(this@MainActivity, R.string.device_offline, Toast.LENGTH_SHORT).show()
                        startServerAsync()
                    }
                }
            }

            override fun onDiscoveryFailed(errorCode: Int) {
                runOnUiThread {
                    statusView.text = getString(R.string.discovery_failed, errorCode)
                }
            }
        })
    }

    private fun onMediaClicked(item: MediaItem) {
        val items = mediaAdapter.getCurrentItems()
        val index = items.indexOfFirst { it.id == item.id }
        if (index >= 0 && items.size > 1) {
            playAll(items, index)
        } else {
            // 单文件播放（兼容旧逻辑）
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
    }

    private fun onDeviceClicked(device: NsdHelper.DiscoveredDevice) {
        currentDevice = device
        statusView.text = getString(R.string.fetching_media, device.name)

        val remoteDevice = RemoteMediaClient.RemoteDevice(device.name, device.host, device.port)
        remoteClient.fetchMediaList(remoteDevice) { result ->
            runOnUiThread {
                result.onSuccess { items ->
                    mediaAdapter.submit(items)
                    updateEmptyHint()
                    statusView.text = getString(R.string.device_media_count, device.name, items.size)
                }.onFailure { e ->
                    statusView.text = getString(R.string.fetch_failed, e.message ?: "")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        networkMonitor?.stopListening()
        networkMonitor = null
        nsdHelper?.stopDiscovery()
        nsdHelper = null
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }
}
