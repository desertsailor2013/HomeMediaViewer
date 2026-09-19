import { preferences } from '@kit.ArkData';
import { DiscoveredDevice } from '../model/MediaModels';

const TAG = 'FavoritesManager';
const PREFS_NAME = 'favorites';
const KEY_FAVORITES = 'favorites_list';

/**
 * 收藏设备项
 */
export class FavoriteDevice {
  name: string = '';
  host: string = '';
  port: number = 0;
  alias: string = '';

  constructor(name: string = '', host: string = '', port: number = 0, alias: string = '') {
    this.name = name;
    this.host = host;
    this.port = port;
    this.alias = alias;
  }

  get displayName(): string {
    return this.alias || this.name;
  }

  get address(): string {
    return `http://${this.host}:${this.port}`;
  }
}

/**
 * 设备收藏管理器
 */
export class FavoritesManager {
  private context: Context;
  private favorites: FavoriteDevice[] = [];

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
        let device = new FavoriteDevice(
          item['name'] as string,
          item['host'] as string,
          item['port'] as number,
          item['alias'] as string
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
          'port': device.port,
          'alias': device.alias
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
  async addFavorite(device: DiscoveredDevice, alias: string = ''): Promise<void> {
    if (!this.isFavorite(device.name)) {
      let fav = new FavoriteDevice(device.name, device.host, device.port, alias);
      this.favorites.push(fav);
      await this.saveFavorites();
    } else {
      await this.updateDeviceAddress(device.name, device.host, device.port);
    }
  }

  /**
   * 移除收藏
   */
  async removeFavorite(deviceName: string): Promise<void> {
    this.favorites = this.favorites.filter((d: FavoriteDevice) => d.name !== deviceName);
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
    return this.favorites.some((d: FavoriteDevice) => d.name === deviceName);
  }

  /**
   * 设置设备别名
   */
  async setAlias(deviceName: string, alias: string): Promise<void> {
    let device = this.favorites.find((d: FavoriteDevice) => d.name === deviceName);
    if (device) {
      device.alias = alias;
      await this.saveFavorites();
    }
  }

  /**
   * 获取设备别名
   */
  getAlias(deviceName: string): string {
    let device = this.favorites.find((d: FavoriteDevice) => d.name === deviceName);
    return device ? device.alias : '';
  }

  /**
   * 更新设备地址
   */
  async updateDeviceAddress(deviceName: string, host: string, port: number): Promise<void> {
    let device = this.favorites.find((d: FavoriteDevice) => d.name === deviceName);
    if (device) {
      device.host = host;
      device.port = port;
      await this.saveFavorites();
    }
  }

  /**
   * 获取所有收藏
   */
  getFavorites(): FavoriteDevice[] {
    return this.favorites;
  }
}
