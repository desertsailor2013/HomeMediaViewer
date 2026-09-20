import { http } from '@kit.NetworkKit';
import { MediaItem } from '../model/MediaModels';

const TAG = 'RemoteMediaClient';

/**
 * 文件操作结果
 */
export class FileOperationResult {
  success: boolean = false;
  message: string = '';
  id: string = '';

  constructor(success: boolean = false, message: string = '', id: string = '') {
    this.success = success;
    this.message = message;
    this.id = id;
  }
}

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

  /**
   * 发送投屏命令到目标设备
   */
  async sendCastCommand(
    deviceAddress: string,
    mediaId: string,
    title: string,
    position: number
  ): Promise<boolean> {
    let httpRequest = http.createHttp();
    try {
      let url = `${deviceAddress}/play`;
      let body = JSON.stringify({
        mediaId: mediaId,
        title: title,
        position: position
      });

      let response = await httpRequest.request(url, {
        method: http.RequestMethod.POST,
        header: { 'Content-Type': 'application/json' },
        extraData: body,
        readTimeout: this.timeout,
        connectTimeout: this.timeout
      });

      return response.responseCode === 200;
    } catch (err) {
      console.error(TAG, `sendCastCommand failed: ${err}`);
      return false;
    } finally {
      httpRequest.destroy();
    }
  }

  /**
   * 上传文件
   */
  async uploadFile(
    deviceAddress: string,
    filePath: string,
    fileName: string,
    path: string = '/'
  ): Promise<FileOperationResult> {
    let httpRequest = http.createHttp();
    try {
      let url = `${deviceAddress}/upload`;
      let formData = `--boundary\r\nContent-Disposition: form-data; name="file"; filename="${fileName}"\r\nContent-Type: application/octet-stream\r\n\r\nFILE_DATA\r\n--boundary\r\nContent-Disposition: form-data; name="path"\r\n\r\n${path}\r\n--boundary--\r\n`;

      let response = await httpRequest.request(url, {
        method: http.RequestMethod.POST,
        header: { 'Content-Type': 'multipart/form-data; boundary=boundary' },
        extraData: formData,
        readTimeout: 30000,
        connectTimeout: 30000
      });

      if (response.responseCode === 200) {
        let result = response.result as http.HttpResponse;
        let data = JSON.parse(result.toString()) as Record<string, Object>;
        return new FileOperationResult(true, data['message'] as string, data['id'] as string);
      }
      return new FileOperationResult(false, `HTTP ${response.responseCode}`);
    } catch (err) {
      console.error(TAG, `uploadFile failed: ${err}`);
      return new FileOperationResult(false, `${err}`);
    } finally {
      httpRequest.destroy();
    }
  }

  /**
   * 删除文件
   */
  async deleteFile(deviceAddress: string, mediaId: string): Promise<FileOperationResult> {
    let httpRequest = http.createHttp();
    try {
      let url = `${deviceAddress}/media/${mediaId}`;
      let response = await httpRequest.request(url, {
        method: http.RequestMethod.DELETE,
        readTimeout: this.timeout,
        connectTimeout: this.timeout
      });

      if (response.responseCode === 200) {
        let result = response.result as http.HttpResponse;
        let data = JSON.parse(result.toString()) as Record<string, Object>;
        return new FileOperationResult(true, data['message'] as string);
      }
      return new FileOperationResult(false, `HTTP ${response.responseCode}`);
    } catch (err) {
      console.error(TAG, `deleteFile failed: ${err}`);
      return new FileOperationResult(false, `${err}`);
    } finally {
      httpRequest.destroy();
    }
  }

  /**
   * 重命名文件
   */
  async renameFile(
    deviceAddress: string,
    mediaId: string,
    newName: string
  ): Promise<FileOperationResult> {
    let httpRequest = http.createHttp();
    try {
      let url = `${deviceAddress}/media/${mediaId}/rename`;
      let body = JSON.stringify({ name: newName });

      let response = await httpRequest.request(url, {
        method: http.RequestMethod.POST,
        header: { 'Content-Type': 'application/json' },
        extraData: body,
        readTimeout: this.timeout,
        connectTimeout: this.timeout
      });

      if (response.responseCode === 200) {
        let result = response.result as http.HttpResponse;
        let data = JSON.parse(result.toString()) as Record<string, Object>;
        return new FileOperationResult(true, data['message'] as string);
      }
      return new FileOperationResult(false, `HTTP ${response.responseCode}`);
    } catch (err) {
      console.error(TAG, `renameFile failed: ${err}`);
      return new FileOperationResult(false, `${err}`);
    } finally {
      httpRequest.destroy();
    }
  }

  /**
   * 新建文件夹
   */
  async createFolder(
    deviceAddress: string,
    name: string,
    parentPath: string = '/'
  ): Promise<FileOperationResult> {
    let httpRequest = http.createHttp();
    try {
      let url = `${deviceAddress}/folder`;
      let body = JSON.stringify({ name: name, path: parentPath });

      let response = await httpRequest.request(url, {
        method: http.RequestMethod.POST,
        header: { 'Content-Type': 'application/json' },
        extraData: body,
        readTimeout: this.timeout,
        connectTimeout: this.timeout
      });

      if (response.responseCode === 200) {
        let result = response.result as http.HttpResponse;
        let data = JSON.parse(result.toString()) as Record<string, Object>;
        return new FileOperationResult(true, data['message'] as string, data['id'] as string);
      }
      return new FileOperationResult(false, `HTTP ${response.responseCode}`);
    } catch (err) {
      console.error(TAG, `createFolder failed: ${err}`);
      return new FileOperationResult(false, `${err}`);
    } finally {
      httpRequest.destroy();
    }
  }
}
