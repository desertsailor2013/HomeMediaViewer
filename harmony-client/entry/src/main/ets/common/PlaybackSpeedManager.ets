import { preferences } from '@kit.ArkData';

const TAG = 'PlaybackSpeedManager';
const PREFS_NAME = 'playback_speed';
const KEY_SPEED = 'current_speed';

/**
 * 播放速度管理器 - 持久化播放速度
 */
export class PlaybackSpeedManager {
  private context: Context;

  constructor(context: Context) {
    this.context = context;
  }

  /**
   * 保存播放速度
   */
  async save(speed: number): Promise<void> {
    try {
      let store = await preferences.getPreferences(this.context, PREFS_NAME);
      await store.put(KEY_SPEED, speed);
      await store.flush();
    } catch (err) {
      console.error(TAG, `save failed: ${err}`);
    }
  }

  /**
   * 恢复播放速度
   */
  async restore(): Promise<number> {
    try {
      let store = await preferences.getPreferences(this.context, PREFS_NAME);
      let speed = await store.get(KEY_SPEED, 1.0);
      return speed as number;
    } catch (err) {
      console.error(TAG, `restore failed: ${err}`);
    }
    return 1.0;
  }
}
