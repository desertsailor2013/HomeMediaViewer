import { preferences } from '@kit.ArkData';

const TAG = 'PlayProgressManager';
const PREFS_NAME = 'play_progress';

/**
 * 播放进度管理器 - 持久化播放位置
 */
export class PlayProgressManager {
  private context: Context;

  constructor(context: Context) {
    this.context = context;
  }

  /**
   * 保存播放进度
   */
  async save(mediaId: string, position: number, duration: number): Promise<void> {
    try {
      let store = await preferences.getPreferences(this.context, PREFS_NAME);
      let key = `progress_${mediaId}`;
      let data = JSON.stringify({ position, duration, timestamp: Date.now() });
      await store.put(key, data);
      await store.flush();
    } catch (err) {
      console.error(TAG, `save failed: ${err}`);
    }
  }

  /**
   * 恢复播放进度
   */
  async restore(mediaId: string): Promise<number> {
    try {
      let store = await preferences.getPreferences(this.context, PREFS_NAME);
      let key = `progress_${mediaId}`;
      let data = await store.get(key, '');
      if (data) {
        let parsed = JSON.parse(data as string) as Record<string, Object>;
        return parsed['position'] as number;
      }
    } catch (err) {
      console.error(TAG, `restore failed: ${err}`);
    }
    return 0;
  }

  /**
   * 清除播放进度
   */
  async clear(mediaId: string): Promise<void> {
    try {
      let store = await preferences.getPreferences(this.context, PREFS_NAME);
      let key = `progress_${mediaId}`;
      await store.delete(key);
      await store.flush();
    } catch (err) {
      console.error(TAG, `clear failed: ${err}`);
    }
  }
}
