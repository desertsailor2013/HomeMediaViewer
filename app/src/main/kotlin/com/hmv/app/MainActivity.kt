package com.hmv.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
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
    private lateinit var favoritesManager: DeviceFavoritesManager
    private val statusView by lazy { findViewById<TextView>(R.id.status) }
    private val emptyHint by lazy { findViewById<TextView>(R.id.empty_hint) }
    private val searchInput by lazy { findViewById<EditText>(R.id.search_input) }
    private val filterAll by lazy { findViewById<TextView>(R.id.filter_all) }
    private val filterVideo by lazy { findViewById<TextView>(R.id.filter_video) }
    private val filterAudio by lazy { findViewById<TextView>(R.id.filter_audio) }
    private val playAllBtn by lazy { findViewById<TextView>(R.id.btn_play_all) }
    private val groupToggleBtn by lazy { findViewById<TextView>(R.id.btn_group_toggle) }
    private val mediaAdapter = MediaAdapter { onMediaClicked(it) }
    private val deviceAdapter = DeviceAdapter(
        onDeviceClicked = { onDeviceClicked(it) },
        onFavoriteClicked = { onFavoriteClicked(it) }
    )

    // 平板双栏模式相关
    private var isDualPane = false
    private var exoPlayer: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var playerContainer: View? = null
    private var rightPaneHint: TextView? = null
    private var currentPlayingUrl: String? = null

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

        favoritesManager = DeviceFavoritesManager(this)
        deviceAdapter.setFavoritesManager(favoritesManager)

        // 检测双栏模式
        isDualPane = findViewById<View>(R.id.right_pane) != null
        if (isDualPane) {
            playerView = findViewById(R.id.player_view)
            playerContainer = findViewById(R.id.player_container)
            rightPaneHint = findViewById(R.id.right_pane_hint)
        }

        val mediaList = findViewById<RecyclerView>(R.id.media_list)
        mediaList.layoutManager = LinearLayoutManager(this)
        mediaList.adapter = mediaAdapter

        findViewById<RecyclerView>(R.id.device_list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = this@MainActivity.deviceAdapter
        }

        setupSearchAndFilter()
        setupPlayAllButton()
        setupGroupToggle()
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
            val devices = deviceAdapter.getDevices()
            startActivity(PlayerActivity.createIntent(this, urls, titles, ids, startIndex, devices))
        }
    }

    private fun setupGroupToggle() {
        updateGroupToggleUI()
        groupToggleBtn.setOnClickListener {
            mediaAdapter.setGroupByFolder(!mediaAdapter.isGroupByFolder())
            updateGroupToggleUI()
            updateEmptyHint()
        }
    }

    private fun updateGroupToggleUI() {
        val selectedColor = ContextCompat.getColor(this, R.color.filter_selected)
        val unselectedColor = ContextCompat.getColor(this, R.color.filter_unselected)
        if (mediaAdapter.isGroupByFolder()) {
            groupToggleBtn.text = getString(R.string.flat_view)
            groupToggleBtn.setTextColor(selectedColor)
        } else {
            groupToggleBtn.text = getString(R.string.group_by_folder)
            groupToggleBtn.setTextColor(unselectedColor)
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
        // 尝试直连收藏设备（跳过 mDNS 等待）
        tryConnectFavorites()

        nsdHelper = NsdHelper(this)
        nsdHelper?.startDiscovery(object : NsdHelper.DeviceListener {
            override fun onDeviceFound(device: NsdHelper.DiscoveredDevice) {
                runOnUiThread {
                    deviceAdapter.addDevice(device)
                    // 更新收藏设备的 IP:Port
                    if (favoritesManager.isFavorite(device.name)) {
                        favoritesManager.updateDeviceAddress(device.name, device.host, device.port)
                    }
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

    private fun tryConnectFavorites() {
        val favorites = favoritesManager.getFavorites()
        if (favorites.isEmpty()) return

        Thread {
            for (fav in favorites) {
                try {
                    // 尝试直连收藏设备
                    val url = java.net.URL("http://${fav.host}:${fav.port}/media")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    conn.requestMethod = "HEAD"

                    if (conn.responseCode == 200) {
                        // 连接成功，添加到设备列表
                        val device = NsdHelper.DiscoveredDevice(
                            name = fav.name,
                            host = fav.host,
                            port = fav.port,
                            serviceInfo = android.net.nsd.NsdServiceInfo()
                        )
                        runOnUiThread {
                            deviceAdapter.addDevice(device)
                        }
                    }
                    conn.disconnect()
                } catch (_: Exception) {
                    // 连接失败，等待 mDNS 发现
                }
            }
        }.start()
    }

    private fun onMediaClicked(item: MediaItem) {
        val device = currentDevice
        val port = service?.port

        val url = if (device != null) {
            remoteClient.getMediaUrl(
                RemoteMediaClient.RemoteDevice(device.name, device.host, device.port),
                item.id
            )
        } else if (port != null) {
            "http://127.0.0.1:$port/media/${item.id}"
        } else {
            return
        }

        if (isDualPane) {
            // 平板双栏模式：在右侧嵌入播放器播放
            playInEmbeddedPlayer(url, item.title)
        } else {
            // 手机模式：启动独立播放器 Activity
            val items = mediaAdapter.getCurrentItems()
            val index = items.indexOfFirst { it.id == item.id }
            if (index >= 0 && items.size > 1) {
                playAll(items, index)
            } else {
                startActivity(PlayerActivity.createIntent(this, url, item.title, item.id))
            }
        }
    }

    /**
     * 平板双栏模式：在右侧嵌入播放器中播放
     */
    private fun playInEmbeddedPlayer(url: String, title: String) {
        // 显示播放器容器，隐藏提示
        rightPaneHint?.visibility = View.GONE
        playerContainer?.visibility = View.VISIBLE

        // 更新标题
        findViewById<TextView>(R.id.player_title)?.text = title

        // 初始化 ExoPlayer
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(this).build()
            playerView?.player = exoPlayer
        }

        // 设置媒体并播放
        val mediaItem = ExoMediaItem.fromUri(Uri.parse(url))
        exoPlayer?.apply {
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }

        currentPlayingUrl = url
    }

    /**
     * 平板模式：停止嵌入式播放器
     */
    private fun stopEmbeddedPlayer() {
        exoPlayer?.release()
        exoPlayer = null
        playerContainer?.visibility = View.GONE
        rightPaneHint?.visibility = View.VISIBLE
        currentPlayingUrl = null
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

    private fun onFavoriteClicked(device: NsdHelper.DiscoveredDevice) {
        if (favoritesManager.isFavorite(device.name)) {
            // 已收藏 → 弹出编辑别名对话框
            showAliasDialog(device)
        } else {
            // 未收藏 → 直接收藏
            favoritesManager.toggleFavorite(device)
            deviceAdapter.setFavoritesManager(favoritesManager)
            Toast.makeText(this, R.string.device_favorited, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAliasDialog(device: NsdHelper.DiscoveredDevice) {
        val currentAlias = favoritesManager.getAlias(device.name)
        val editText = android.widget.EditText(this).apply {
            hint = getString(R.string.device_alias_hint)
            setText(currentAlias)
            setPadding(48, 32, 48, 32)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.set_alias))
            .setView(editText)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val alias = editText.text.toString().trim()
                favoritesManager.setAlias(device.name, alias)
                deviceAdapter.setFavoritesManager(favoritesManager)
                Toast.makeText(this, R.string.alias_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.remove_favorite) { _, _ ->
                favoritesManager.removeFavorite(device.name)
                deviceAdapter.setFavoritesManager(favoritesManager)
                Toast.makeText(this, R.string.favorite_removed, Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopEmbeddedPlayer()
        networkMonitor?.stopListening()
        networkMonitor = null
        nsdHelper?.stopDiscovery()
        nsdHelper = null
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.playWhenReady = false
    }

    override fun onResume() {
        super.onResume()
        exoPlayer?.playWhenReady = true
    }
}
