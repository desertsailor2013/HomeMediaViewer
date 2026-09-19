import { preferences } from '@kit.ArkData';
import { DiscoveredDevice } from '../model/MediaModels';

const TAG = 'FavoritesManager';
const PREFS_NAME = 'favorites';
const KEY_FAVORITES = 'favorites_list';

/**
 * 设备收藏管理器
 */
export class FavoritesManager {
  private context: Context;
  private favorites: DiscoveredDevice[] = [];

  constructor(context: Context) {
    this.context = context;
  }

  /**
   * 加载收藏列表
   */
  async loadFavorites(): Promise<void> {
    try {
      let store = await preferences.getPreferences(this.context, PREFS_NAME);
      let data = await store.get(KEY_FAVORITES, '[]');
      let json = data as string;
      let items = JSON.parse(json) as Array<Record<string, Object>>;
      this.favorites = [];
      for (let i = 0; i < items.length; i++) {
        let item = items[i];
        let device = new DiscoveredDevice(
          item['name'] as string,
          item['host'] as string,
          item['port'] as number
        );
        this.favorites.push(device);
      }
    } catch (err) {
      console.error(TAG, `loadFavorites failed: ${err}`);
      this.favorites = [];
    }
  }

  /**
   * 保存收藏列表
   */
  private async saveFavorites(): Promise<void> {
    try {
      let store = await preferences.getPreferences(this.context, PREFS_NAME);
      let items: Array<Record<string, Object>> = [];
      for (let i = 0; i < this.favorites.length; i++) {
        let device = this.favorites[i];
        items.push({
          'name': device.name,
          'host': device.host,
          'port': device.port
        });
      }
      await store.put(KEY_FAVORITES, JSON.stringify(items));
      await store.flush();
    } catch (err) {
      console.error(TAG, `saveFavorites failed: ${err}`);
    }
  }

  /**
   * 添加收藏
   */
  async addFavorite(device: DiscoveredDevice): Promise<void> {
    if (!this.isFavorite(device.name)) {
      this.favorites.push(device);
      await this.saveFavorites();
    }
  }

  /**
   * 移除收藏
   */
  async removeFavorite(deviceName: string): Promise<void> {
    this.favorites = this.favorites.filter((d: DiscoveredDevice) => d.name !== deviceName);
    await this.saveFavorites();
  }

  /**
   * 切换收藏状态
   */
  async toggleFavorite(device: DiscoveredDevice): Promise<boolean> {
    if (this.isFavorite(device.name)) {
      await this.removeFavorite(device.name);
      return false;
    } else {
      await this.addFavorite(device);
      return true;
    }
  }

  /**
   * 检查是否已收藏
   */
  isFavorite(deviceName: string): boolean {
    return this.favorites.some((d: DiscoveredDevice) => d.name === deviceName);
  }

  /**
   * 获取所有收藏
   */
  getFavorites(): DiscoveredDevice[] {
    return this.favorites;
  }
}
