package com.hmv.app

import android.content.Context
import android.content.SharedPreferences

/**
 * 播放速度管理器。
 *
 * 使用 SharedPreferences 持久化用户的播放速度偏好。
 */
object PlaybackSpeedManager {

    private const val PREFS_NAME = "playback_speed"
    private const val KEY_SPEED = "speed"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 保存播放速度偏好。
     *
     * @param context 上下文
     * @param speed 播放速度（如 1.0f, 1.5f, 2.0f）
     */
    fun save(context: Context, speed: Float) {
        prefs(context).edit()
            .putFloat(KEY_SPEED, speed)
            .apply()
    }

    /**
     * 获取保存的播放速度偏好。
     *
     * @return 保存的速度，默认 1.0f
     */
    fun restore(context: Context): Float {
        return prefs(context).getFloat(KEY_SPEED, 1.0f)
    }

    /**
     * 清除播放速度偏好。
     */
    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_SPEED).apply()
    }
}
