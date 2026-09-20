# Feature Comparison: Android vs HarmonyOS Phone Client

| Feature | Android | HarmonyOS | Status |
|---------|---------|-----------|--------|
| **Core** | | | |
| HTTP Server (serve media) | ✅ MediaServerService | ❌ N/A (client only) | — |
| Media scanning (local) | ✅ MediaStore | ✅ MediaKit PhotoAccessHelper | ✅ |
| mDNS device discovery | ✅ NsdHelper | ✅ NsdHelper @ohos.net.nsd | ✅ |
| Manual device connect | ✅ | ✅ | ✅ |
| **UI** | | | |
| Device list | ✅ Horizontal RecyclerView | ✅ List component | ✅ |
| Media list | ✅ RecyclerView + adapter | ✅ List + ForEach | ✅ |
| Search | ✅ TextWatcher | ✅ TextInput.onChange | ✅ |
| Type filter (All/Video/Audio) | ✅ 3 buttons | ✅ 3 Text buttons | ✅ |
| Group by folder toggle | ✅ | ❌ Missing | 🟡 |
| "Play All" button | ✅ | ✅ | ✅ |
| Empty state hint | ✅ | ❌ Missing | 🟡 |
| Thumbnail loading (Coil) | ✅ | ❌ Missing | 🔴 |
| Dark mode | ✅ values-night | ❌ Missing | 🔴 |
| **Playback** | | | |
| ExoPlayer / AVPlayer | ✅ ExoPlayer | ✅ AVPlayer | ✅ |
| Queue playback (setMediaItems) | ✅ setMediaItems | ✅ Manual playNext | ✅ |
| Playback speed (0.5x-2.0x) | ✅ | ✅ | ✅ |
| Fullscreen toggle | ✅ | ✅ setWindowLayoutFullScreen | ✅ |
| Play progress persistence | ✅ PlayProgressManager | ✅ PlayProgressManager | ✅ |
| **Player Page** | | | |
| Separate PlayerActivity | ✅ | ✅ Player.ets | ✅ |
| Queue panel (BottomSheet) | ✅ | ✅ Inline list | ✅ |
| Cast to other devices | ✅ | ❌ Missing | 🔴 |
| Network monitoring | ✅ NetworkMonitor | ❌ Missing | 🔴 |
| **Favorites** | | | |
| Favorite devices | ✅ | ✅ | ✅ |
| Alias dialog | ✅ | ❌ Missing | 🟡 |
| Auto-connect favorites | ✅ tryConnectFavorites | ✅ | ✅ |

## Summary

| Category | Total | Aligned | Missing |
|----------|-------|---------|---------|
| Core | 4 | 4 | 0 |
| UI | 10 | 7 | 3 |
| Player | 7 | 6 | 1 |
| Favorites | 3 | 2 | 1 |
| **Total** | **24** | **19** | **5** |

## Remaining Gaps to Close

1. 🔴 **Thumbnail loading** - Use ImageKit or_PICTURE_DATA to load thumbnails
2. 🔴 **Dark mode** - Add resources-dark
3. 🔴 **Cast to other devices** - Implement cast command via HTTP
4. 🟡 **Group by folder** - Add folder grouping UI
5. 🟡 **Empty state hint** - Add empty state text
