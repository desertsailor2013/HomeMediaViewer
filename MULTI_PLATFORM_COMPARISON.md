# Multi-Platform Feature Comparison

## 1. Core Features

| Feature | Android Phone | Android Pad | Web | HarmonyOS | iOS |
|---------|---------------|-------------|-----|-----------|-----|
| **Media Scanning** | ✅ MediaStore | ✅ MediaStore | ❌ N/A (client) | ✅ MediaKit | ⚠️ TODO |
| **HTTP Server** | ✅ MediaServerService | ✅ MediaServerService | ❌ N/A | ❌ N/A | ❌ N/A |
| **mDNS Discovery** | ✅ NsdHelper | ✅ NsdHelper | ❌ Manual only | ✅ NsdHelper | ✅ NWBrowser |
| **Manual Connect** | ✅ | ✅ | ✅ | ✅ | ✅ |

## 2. UI Features

| Feature | Android Phone | Android Pad | Web | HarmonyOS | iOS |
|---------|---------------|-------------|-----|-----------|-----|
| **Device List** | ✅ Horizontal | ✅ Horizontal | ✅ Sidebar | ✅ List | ✅ ScrollView |
| **Media List** | ✅ RecyclerView | ✅ RecyclerView | ✅ Table | ✅ List | ✅ List |
| **Search** | ✅ TextWatcher | ✅ TextWatcher | ✅ Input event | ✅ TextInput | ✅ TextField |
| **Type Filter** | ✅ 3 buttons | ✅ 3 buttons | ✅ Tabs | ✅ 3 Text | ✅ Button |
| **Group by Folder** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Play All** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Empty State** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Thumbnail** | ✅ Coil | ✅ Coil | ✅ <img> | ✅ Image | ✅ AsyncImage |
| **Dark Mode** | ✅ values-night | ✅ values-night | ✅ CSS | ✅ resources-dark | ✅ System |
| **Dual Pane** | ❌ | ✅ layout-sw600dp | ✅ Responsive | ❌ | ❌ iPad landscape |

## 3. Playback Features

| Feature | Android Phone | Android Pad | Web | HarmonyOS | iOS |
|---------|---------------|-------------|-----|-----------|-----|
| **Player Engine** | ✅ ExoPlayer | ✅ ExoPlayer | ✅ HTML5 Video | ✅ AVPlayer | ✅ AVPlayer |
| **Queue Playback** | ✅ setMediaItems | ✅ setMediaItems | ✅ Array | ✅ Manual next | ✅ Manual next |
| **Speed Control** | ✅ 0.5x-2.0x | ✅ 0.5x-2.0x | ✅ playbackRate | ✅ AVPlayer.rate | ✅ AVPlayer.rate |
| **Fullscreen** | ✅ Immersive | ✅ Immersive | ✅ Fullscreen API | ✅ Window API | ✅ Orientation |
| **Progress Save** | ✅ SharedPreferences | ✅ SharedPreferences | ✅ localStorage | ✅ Preferences | ✅ UserDefaults |
| **Cast** | ✅ POST /play | ✅ POST /play | ✅ fetch API | ✅ sendCastCommand | ✅ sendCastCommand |

## 4. Player Page

| Feature | Android Phone | Android Pad | Web | HarmonyOS | iOS |
|---------|---------------|-------------|-----|-----------|-----|
| **Separate Activity** | ✅ PlayerActivity | ✅ Embedded | ✅ player.html | ✅ Player.ets | ✅ PlayerView |
| **Queue Panel** | ✅ BottomSheet | ✅ Right pane | ✅ Slide panel | ✅ Inline list | ✅ Sheet |
| **Queue Remove** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Queue Clear** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Cast Device Selector** | ✅ BottomSheet | ✅ Dialog | ✅ Modal | ✅ Dialog | ✅ Sheet |

## 5. Favorites Features

| Feature | Android Phone | Android Pad | Web | HarmonyOS | iOS |
|---------|---------------|-------------|-----|-----------|-----|
| **Add/Remove** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Alias** | ✅ AlertDialog | ✅ AlertDialog | ✅ Prompt | ✅ Custom Dialog | ✅ Alert |
| **Auto-connect** | ✅ tryConnectFavorites | ✅ tryConnectFavorites | ❌ | ✅ | ✅ |
| **Address Update** | ✅ | ✅ | ❌ | ✅ | ✅ |

## 6. Network Features

| Feature | Android Phone | Android Pad | Web | HarmonyOS | iOS |
|---------|---------------|-------------|-----|-----------|-----|
| **Network Monitor** | ✅ ConnectivityManager | ✅ ConnectivityManager | ✅ online/offline | ✅ connection | ✅ NWPathMonitor |
| **Network Error UI** | ✅ Toast + error view | ✅ Toast + error view | ✅ Alert | ✅ Toast | ✅ Alert |
| **Offline Handling** | ✅ Pause + retry | ✅ Pause + retry | ✅ | ✅ | ✅ |

## 7. Server Features (Android only)

| Feature | Android Phone | Android Pad | Web | HarmonyOS | iOS |
|---------|---------------|-------------|-----|-----------|-----|
| **Start HTTP Server** | ✅ | ✅ | ❌ | ❌ | ❌ |
| **Serve Media Files** | ✅ | ✅ | ❌ | ❌ | ❌ |
| **Range Request** | ✅ | ✅ | ❌ | ❌ | ❌ |
| **Thumbnail Endpoint** | ✅ | ✅ | ❌ | ❌ | ❌ |
| **Cast Command Endpoint** | ✅ POST /play | ✅ POST /play | ❌ | ❌ | ❌ |
| **File Upload** | ✅ | ✅ | ❌ | ❌ | ❌ |
| **File Delete** | ✅ | ✅ | ❌ | ❌ | ❌ |
| **File Rename** | ✅ | ✅ | ❌ | ❌ | ❌ |
| **Folder Create** | ✅ | ✅ | ❌ | ❌ | ❌ |

## 8. Web-Specific Features

| Feature | Web |
|---------|-----|
| **13 Pages** | ✅ index, player, queue, favorites, history, filemanager, stats, network, shortcuts, i18n, settings, export, device |
| **4-Language i18n** | ✅ zh-CN, en, ja, ko (130+ keys) |
| **File Manager** | ✅ browse, upload, delete, rename, new folder |
| **Play History** | ✅ record, search, delete, clear, replay |
| **Statistics** | ✅ playback stats |
| **Export** | ✅ export settings |

## Summary Matrix

| Category | Android Phone | Android Pad | Web | HarmonyOS | iOS |
|----------|---------------|-------------|-----|-----------|-----|
| Core | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ |
| UI | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| Playback | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| Favorites | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| Network | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| Server | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | N/A | N/A | N/A |
| **Total** | **30/30** | **30/30** | **19/25** | **22/25** | **20/25** |

## Missing Features by Platform

### Android Phone/Pad
- None (reference implementation)

### Web Client
- ❌ mDNS discovery (manual IP only)
- ❌ Auto-connect favorites
- ❌ Address update for favorites
- ❌ HTTP server (client only)

### HarmonyOS
- ❌ HTTP server (client only)
- ❌ File upload/delete/rename (web has, harmony doesn't)
- ❌ Play history page

### iOS
- ❌ HTTP server (client only)
- ❌ Local media scanning (PHPhotoLibrary TODO)
- ❌ File upload/delete/rename
- ❌ Play history page
- ❌ Statistics page
