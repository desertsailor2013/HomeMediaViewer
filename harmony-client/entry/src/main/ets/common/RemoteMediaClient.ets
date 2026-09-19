import { http } from '@kit.NetworkKit';
import { MediaItem } from '../model/MediaModels';

const TAG = 'RemoteMediaClient';

/**
 * 远程媒体客户端 - 连接 core-server HTTP API
 */
export class RemoteMediaClient {
  private timeout: number = 5000;

  /**
   * 获取媒体列表
   */
  async fetchMediaList(deviceAddress: string): Promise<MediaItem[]> {
    let httpRequest = http.createHttp();
    try {
      let url = `${deviceAddress}/media`;
      let response = await httpRequest.request(url, {
        method: http.RequestMethod.GET,
        readTimeout: this.timeout,
        connectTimeout: this.timeout
      });

      if (response.responseCode === 200) {
        let result = response.result as http.HttpResponse;
        let data = JSON.parse(result.toString()) as Record<string, Object>;
        let items = data['items'] as Array<Record<string, Object>>;
        let mediaItems: MediaItem[] = [];
        for (let i = 0; i < items.length; i++) {
          mediaItems.push(MediaItem.fromJson(items[i]));
        }
        return mediaItems;
      }
      return [];
    } catch (err) {
      console.error(TAG, `fetchMediaList failed: ${err}`);
      return [];
    } finally {
      httpRequest.destroy();
    }
  }

  /**
   * 获取媒体播放 URL
   */
  getMediaUrl(deviceAddress: string, mediaId: string): string {
    return `${deviceAddress}/media/${mediaId}`;
  }

  /**
   * 获取缩略图 URL
   */
  getThumbnailUrl(deviceAddress: string, mediaId: string): string {
    return `${deviceAddress}/thumbnail/${mediaId}`;
  }

  /**
   * 检查设备是否在线
   */
  async checkDeviceOnline(deviceAddress: string): Promise<boolean> {
    let httpRequest = http.createHttp();
    try {
      let response = await httpRequest.request(deviceAddress, {
        method: http.RequestMethod.HEAD,
        readTimeout: 3000,
        connectTimeout: 3000
      });
      return response.responseCode === 200;
    } catch (err) {
      return false;
    } finally {
      httpRequest.destroy();
    }
  }
}
