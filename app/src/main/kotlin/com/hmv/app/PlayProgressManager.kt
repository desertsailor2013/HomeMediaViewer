package com.hmv.app

import android.content.Context
import android.content.SharedPreferences

/**
 * 播放进度管理器。
 *
 * 使用 SharedPreferences 持久化每个媒体的播放位置（毫秒），
 * 支持保存、恢复、清除进度。
 */
object PlayProgressManager {

    private const val PREFS_NAME = "play_progress"
    private const val KEY_PREFIX = "pos_"
    private const val KEY_DURATION_PREFIX = "dur_"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 保存播放进度。
     *
     * @param context 上下文
     * @param mediaId 媒体唯一标识
     * @param position 当前播放位置（毫秒）
     * @param duration 媒体总时长（毫秒），用于判断是否接近结尾
     */
    fun save(context: Context, mediaId: String, position: Long, duration: Long) {
        if (mediaId.isEmpty() || duration <= 0) return
        // 如果已播放到 95% 以上，视为看完，不保存进度
        if (duration > 0 && position.toFloat() / duration > 0.95f) {
            clear(context, mediaId)
            return
        }
        prefs(context).edit()
            .putLong(KEY_PREFIX + mediaId, position)
            .putLong(KEY_DURATION_PREFIX + mediaId, duration)
            .apply()
    }

    /**
     * 获取保存的播放位置。
     *
     * @return 保存的位置（毫秒），无记录返回 0
     */
    fun restore(context: Context, mediaId: String): Long {
        if (mediaId.isEmpty()) return 0
        return prefs(context).getLong(KEY_PREFIX + mediaId, 0)
    }

    /**
     * 清除指定媒体的播放进度。
     */
    fun clear(context: Context, mediaId: String) {
        if (mediaId.isEmpty()) return
        prefs(context).edit()
            .remove(KEY_PREFIX + mediaId)
            .remove(KEY_DURATION_PREFIX + mediaId)
            .apply()
    }

    /**
     * 清除所有播放进度。
     */
    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
    }

    /**
     * 获取格式化的进度文本，如 "12:34 / 45:00"。
     */
    fun formatProgress(positionMs: Long, durationMs: Long): String {
        return "${formatTime(positionMs)} / ${formatTime(durationMs)}"
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}
