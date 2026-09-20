/**
 * 媒体服务
 * 管理 HTTP Server 的生命周期
 */
import { HttpServer } from './HttpServer';
import { MediaItem } from '../model/MediaModels';
import media from '@ohos.multimedia.media';
import { photoAccessHelper } from '@kit.MediaLibraryKit';

const TAG = 'MediaServerService';

export class MediaServerService {
  private httpServer: HttpServer | null = null;
  private port: number = 8080;
  private running: boolean = false;
  private mediaItems: MediaItem[] = [];

  constructor(port: number = 8080) {
    this.port = port;
    this.httpServer = new HttpServer(port);
  }

  /**
   * 获取 HTTP 服务器实例
   */
  getServer(): HttpServer | null {
    return this.httpServer;
  }

  /**
   * 获取监听端口
   */
  getPort(): number {
    return this.port;
  }

  /**
   * 是否运行中
   */
  isRunning(): boolean {
    return this.running;
  }

  /**
   * 设置媒体列表
   */
  setMediaItems(items: MediaItem[]): void {
    this.mediaItems = items;
    if (this.httpServer) {
      this.httpServer.setMediaProvider(() => this.mediaItems);
    }
  }

  /**
   * 启动服务
   */
  async start(): Promise<boolean> {
    if (this.running) {
      console.warn(`${TAG}: Service already running`);
      return true;
    }

    if (!this.httpServer) {
      console.error(`${TAG}: HTTP server not initialized`);
      return false;
    }

    try {
      // 设置媒体提供者
      this.httpServer.setMediaProvider(() => this.mediaItems);

      // 设置缩略图提供者
      this.httpServer.setThumbnailProvider((id: string) => this.getThumbnail(id));

      // 启动 HTTP 服务器
      const started = await this.httpServer.start();
      if (started) {
        this.running = true;
        this.port = this.httpServer.getPort();
        console.info(`${TAG}: Service started on port ${this.port}`);
        return true;
      }
      return false;
    } catch (err) {
      console.error(`${TAG}: Failed to start service:`, JSON.stringify(err));
      return false;
    }
  }

  /**
   * 停止服务
   */
  async stop(): Promise<void> {
    if (!this.running) {
      return;
    }

    try {
      if (this.httpServer) {
        await this.httpServer.stop();
      }
      this.running = false;
      console.info(`${TAG}: Service stopped`);
    } catch (err) {
      console.error(`${TAG}: Error stopping service:`, JSON.stringify(err));
    }
  }

  /**
   * 获取缩略图
   * 使用 photoAccessHelper 获取媒体缩略图
   */
  private getThumbnail(id: string): ArrayBuffer | null {
    try {
      // 从 ID 解析出 mediaId
      const mediaId = id.replace('local_', '');
      
      // 使用 photoAccessHelper 获取缩略图
      // 注意：这里需要异步操作，但当前接口是同步的
      // 实际实现中应该使用缓存机制
      console.info(`${TAG}: Thumbnail requested for: ${mediaId}`);
      
      // 返回 null 表示需要异步处理
      // 在实际实现中，这里应该使用 ThumbnailAbility
      return null;
    } catch (err) {
      console.error(`${TAG}: Get thumbnail error:`, JSON.stringify(err));
      return null;
    }
  }

  /**
   * 更新设备信息
   */
  setDeviceInfo(deviceType: string, deviceName: string): void {
    if (this.httpServer) {
      this.httpServer.setDeviceInfo(deviceType, deviceName);
    }
  }

  /**
   * 设置管理密码
   */
  setAdminPassword(password: string): void {
    if (this.httpServer) {
      this.httpServer.setAdminPassword(password);
    }
  }

  /**
   * 获取服务状态
   */
  getStatus(): object {
    return {
      running: this.running,
      port: this.port,
      mediaCount: this.mediaItems.length,
      deviceType: this.httpServer?.getPort() || 'unknown'
    };
  }
}

// 单例
export const mediaServerService = new MediaServerService();
