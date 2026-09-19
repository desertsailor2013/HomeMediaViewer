import { router } from '@kit.ArkUI';
import { media } from '@kit.MediaKit';
import { RemoteMediaClient } from '../common/RemoteMediaClient';
import { FavoritesManager } from '../common/FavoritesManager';
import { MediaItem, DiscoveredDevice, QueueItem } from '../model/MediaModels';

@Entry
@Component
struct Index {
  @State mediaItems: MediaItem[] = [];
  @State devices: DiscoveredDevice[] = [];
  @State status: string = '正在扫描媒体...';
  @State searchQuery: string = '';
  @State currentDevice: DiscoveredDevice | null = null;
  @State isPlaying: boolean = false;
  @State currentTitle: string = '';
  @State queue: QueueItem[] = [];
  @State queueIndex: number = -1;

  private remoteClient: RemoteMediaClient = new RemoteMediaClient();
  private favoritesManager: FavoritesManager = new FavoritesManager(this.getUIContext().getHostContext());
  private avPlayer: media.AVPlayer | null = null;

  aboutToAppear(): void {
    this.loadLocalMedia();
    this.favoritesManager.loadFavorites();
  }

  aboutToDisappear(): void {
    this.releasePlayer();
  }

  /**
   * 加载本地媒体
   */
  private loadLocalMedia(): void {
    this.status = '正在扫描媒体...';
    // 使用 MediaKit 扫描本地媒体
    // TODO: 实现 MediaKit 扫描
    this.status = '本地媒体加载完成';
  }

  /**
   * 连接远程设备
   */
  private async connectDevice(device: DiscoveredDevice): Promise<void> {
    this.currentDevice = device;
    this.status = `正在获取 ${device.name} 的媒体列表...`;
    let items = await this.remoteClient.fetchMediaList(device.address);
    this.mediaItems = items;
    this.status = `${device.name} - ${items.length} 个媒体文件`;
  }

  /**
   * 播放媒体
   */
  private async playMedia(item: MediaItem): Promise<void> {
    let url: string;
    if (this.currentDevice) {
      url = this.remoteClient.getMediaUrl(this.currentDevice.address, item.id);
    } else {
      // TODO: 本地播放
      url = '';
    }

    if (url) {
      this.currentTitle = item.title;
      this.isPlaying = true;
      await this.startPlayback(url);
    }
  }

  /**
   * 开始播放
   */
  private async startPlayback(url: string): Promise<void> {
    try {
      this.avPlayer = await media.createAVPlayer();
      let avPlayer = this.avPlayer as media.AVPlayer;

      avPlayer.on('stateChange', (state: string) => {
        if (state === 'initialized') {
          avPlayer.play();
        } else if (state === 'completed') {
          this.playNext();
        }
      });

      avPlayer.on('error', (err: BusinessError) => {
        console.error('Playback error:', err.message);
        this.isPlaying = false;
      });

      await avPlayer.url = url;
    } catch (err) {
      console.error('Failed to start playback:', err);
      this.isPlaying = false;
    }
  }

  /**
   * 播放下一个
   */
  private playNext(): void {
    if (this.queue.length > 0 && this.queueIndex < this.queue.length - 1) {
      this.queueIndex++;
      let item = this.queue[this.queueIndex];
      this.currentTitle = item.title;
      this.startPlayback(item.url);
    } else {
      this.isPlaying = false;
    }
  }

  /**
   * 停止播放
   */
  private stopPlayback(): void {
    if (this.avPlayer) {
      this.avPlayer.stop();
      this.avPlayer.release();
      this.avPlayer = null;
    }
    this.isPlaying = false;
  }

  /**
   * 释放播放器
   */
  private releasePlayer(): void {
    if (this.avPlayer) {
      this.avPlayer.release();
      this.avPlayer = null;
    }
  }

  /**
   * 检查设备是否在线
   */
  private async checkDeviceOnline(device: DiscoveredDevice): Promise<boolean> {
    return await this.remoteClient.checkDeviceOnline(device.address);
  }

  build() {
    Column() {
      // 标题栏
      Row() {
        Text('HomeMediaViewer')
          .fontSize(20)
          .fontWeight(FontWeight.Bold)
          .fontColor('#FFFFFF')
      }
      .width('100%')
      .height(56)
      .backgroundColor('#1E88E5')
      .justifyContent(FlexAlign.Center)

      // 状态栏
      Text(this.status)
        .fontSize(13)
        .fontColor('#757575')
        .margin({ left: 16, top: 8 })

      // 设备列表
      if (this.devices.length > 0) {
        Text('发现的设备')
          .fontSize(16)
          .fontWeight(FontWeight.Bold)
          .margin({ left: 16, top: 16, bottom: 8 })

        List() {
          ForEach(this.devices, (device: DiscoveredDevice) => {
            ListItem() {
              Row() {
                Column() {
                  Text(device.name)
                    .fontSize(16)
                    .fontColor('#212121')
                  Text(`${device.host}:${device.port}`)
                    .fontSize(12)
                    .fontColor('#757575')
                }
                .alignItems(HorizontalAlign.Start)
                .layoutWeight(1)

                if (this.favoritesManager.isFavorite(device.name)) {
                  Text('★')
                    .fontSize(20)
                    .fontColor('#FFC107')
                }
              }
              .width('100%')
              .height(60)
              .padding({ left: 16, right: 16 })
              .onClick(() => this.connectDevice(device))
            }
          })
        }
        .width('100%')
        .height(120)
      }

      // 本机媒体标题
      Row() {
        Text(this.currentDevice ? this.currentDevice.name : '本机媒体')
          .fontSize(16)
          .fontWeight(FontWeight.Bold)
      }
      .width('100%')
      .padding({ left: 16, top: 16, bottom: 8 })

      // 搜索框
      TextInput({ placeholder: '搜索媒体文件...' })
        .width('90%')
        .height(40)
        .margin({ bottom: 8 })
        .onChange((value: string) => {
          this.searchQuery = value;
        })

      // 媒体列表
      List() {
        ForEach(this.mediaItems, (item: MediaItem) => {
          ListItem() {
            Row() {
              // 缩略图占位
              Column() {
                Text(item.mimeType.includes('video') ? '🎬' : '🎵')
                  .fontSize(24)
              }
              .width(60)
              .height(60)
              .backgroundColor('#F0F0F0')
              .justifyContent(FlexAlign.Center)
              .borderRadius(8)

              // 媒体信息
              Column() {
                Text(item.title)
                  .fontSize(16)
                  .fontColor('#212121')
                  .maxLines(1)
                  .textOverflow({ overflow: TextOverflow.Ellipsis })
                Text(item.folderName)
                  .fontSize(12)
                  .fontColor('#757575')
                  .margin({ top: 4 })
              }
              .alignItems(HorizontalAlign.Start)
              .layoutWeight(1)
              .margin({ left: 12 })
            }
            .width('100%')
            .height(72)
            .padding({ left: 16, right: 16 })
            .onClick(() => this.playMedia(item))
          }
        })
      }
      .width('100%')
      .layoutWeight(1)

      // 播放控制栏
      if (this.isPlaying) {
        Row() {
          Text(this.currentTitle)
            .fontSize(14)
            .fontColor('#212121')
            .layoutWeight(1)
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })

          Text('⏹')
            .fontSize(24)
            .onClick(() => this.stopPlayback())
        }
        .width('100%')
        .height(56)
        .padding({ left: 16, right: 16 })
        .backgroundColor('#F5F5F5')
      }
    }
    .width('100%')
    .height('100%')
  }
}
