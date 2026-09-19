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
| Group by folder toggle | ✅ | ✅ Expandable list | ✅ |
| "Play All" button | ✅ | ✅ | ✅ |
| Empty state hint | ✅ | ✅ | ✅ |
| Thumbnail loading (Coil) | ✅ | ✅ Image component | ✅ |
| Dark mode | ✅ values-night | ✅ resources-dark | ✅ |
| **Playback** | | | |
| ExoPlayer / AVPlayer | ✅ ExoPlayer | ✅ AVPlayer | ✅ |
| Queue playback (setMediaItems) | ✅ setMediaItems | ✅ Manual playNext | ✅ |
| Playback speed (0.5x-2.0x) | ✅ | ✅ | ✅ |
| Fullscreen toggle | ✅ | ✅ setWindowLayoutFullScreen | ✅ |
| Play progress persistence | ✅ PlayProgressManager | ✅ PlayProgressManager | ✅ |
| **Player Page** | | | |
| Separate PlayerActivity | ✅ | ✅ Player.ets | ✅ |
| Queue panel (BottomSheet) | ✅ | ✅ Inline list | ✅ |
| Cast to other devices | ✅ | ✅ sendCastCommand | ✅ |
| Network monitoring | ✅ NetworkMonitor | ✅ NetworkMonitor | ✅ |
| Network error handling | ✅ | ✅ | ✅ |
| **Favorites** | | | |
| Favorite devices | ✅ | ✅ | ✅ |
| Alias dialog | ✅ | ✅ | ✅ |
| Auto-connect favorites | ✅ tryConnectFavorites | ✅ | ✅ |

## Summary

| Category | Total | Aligned | Missing |
|----------|-------|---------|---------|
| Core | 4 | 4 | 0 |
| UI | 10 | 10 | 0 |
| Player | 8 | 8 | 0 |
| Favorites | 3 | 3 | 0 |
| **Total** | **25** | **25** | **0** |

## All Features Aligned ✅
