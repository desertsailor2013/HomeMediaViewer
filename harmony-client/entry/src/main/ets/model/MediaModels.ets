/**
 * 媒体项目数据模型
 */
export class MediaItem {
  id: string = '';
  title: string = '';
  mimeType: string = '';
  size: number = 0;
  relativePath: string = '';
  thumbnailUri: string = '';
  folderName: string = '';

  static fromJson(json: Record<string, Object>): MediaItem {
    let item = new MediaItem();
    item.id = json['id'] as string;
    item.title = json['title'] as string;
    item.mimeType = json['mimeType'] as string;
    item.size = json['size'] as number;
    item.relativePath = json['relativePath'] as string;
    item.thumbnailUri = json['thumbnailUri'] as string;
    item.folderName = json['folderName'] as string;
    return item;
  }
}

/**
 * 发现的设备
 */
export class DiscoveredDevice {
  name: string = '';
  host: string = '';
  port: number = 0;

  constructor(name: string = '', host: string = '', port: number = 0) {
    this.name = name;
    this.host = host;
    this.port = port;
  }

  get address(): string {
    return `http://${this.host}:${this.port}`;
  }
}

/**
 * 播放队列项
 */
export class QueueItem {
  url: string = '';
  title: string = '';
  id: string = '';

  constructor(url: string = '', title: string = '', id: string = '') {
    this.url = url;
    this.title = title;
    this.id = id;
  }
}
