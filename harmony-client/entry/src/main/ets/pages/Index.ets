import { router } from '@kit.ArkUI';
import { RemoteMediaClient } from '../common/RemoteMediaClient';
import { FavoritesManager } from '../common/FavoritesManager';
import { MediaScanner } from '../common/MediaScanner';
import { NsdHelper, DeviceListener } from '../common/NsdHelper';
import { MediaItem, DiscoveredDevice } from '../model/MediaModels';

@Entry
@Component
struct Index {
  @State mediaItems: MediaItem[] = [];
  @State devices: DiscoveredDevice[] = [];
  @State status: string = '正在扫描媒体...';
  @State searchQuery: string = '';
  @State currentDevice: DiscoveredDevice | null = null;
  @State isGroupByFolder: boolean = false;
  @State typeFilter: string = 'all';
  @State expandedFolders: Set<string> = new Set();

  private remoteClient: RemoteMediaClient = new RemoteMediaClient();
  private favoritesManager: FavoritesManager = new FavoritesManager(this.getUIContext().getHostContext());
  private scanner: MediaScanner = new MediaScanner(this.getUIContext().getHostContext());
  private nsdHelper: NsdHelper = new NsdHelper(this.getUIContext().getHostContext());

  aboutToAppear(): void {
    this.favoritesManager.loadFavorites();
    this.startServerAsync();
    this.startDeviceDiscovery();
  }

  aboutToDisappear(): void {
    this.nsdHelper.stopDiscovery();
  }

  private async startServerAsync(): Promise<void> {
    this.status = '正在扫描媒体...';
    let items = await this.scanner.scanAll();
    this.mediaItems = items;
    this.status = `本机媒体 - ${items.length} 个文件`;
  }

  private startDeviceDiscovery(): void {
    this.tryConnectFavorites();

    this.nsdHelper.startDiscovery({
      onDeviceFound: (device: DiscoveredDevice) => {
        let exists = this.devices.some((d: DiscoveredDevice) => d.name === device.name);
        if (!exists) {
          this.devices.push(device);
        }
      },
      onDeviceLost: (device: DiscoveredDevice) => {
        let index = this.devices.findIndex((d: DiscoveredDevice) => d.name === device.name);
        if (index >= 0) {
          this.devices.splice(index, 1);
        }
        if (this.currentDevice && this.currentDevice.name === device.name) {
          this.currentDevice = null;
          this.startServerAsync();
        }
      },
      onDiscoveryFailed: (errorCode: number) => {
        this.status = `设备发现失败: ${errorCode}`;
      }
    } as DeviceListener);
  }

  private tryConnectFavorites(): void {
    let favorites = this.favoritesManager.getFavorites();
    if (favorites.length === 0) return;

    for (let fav of favorites) {
      this.remoteClient.checkDeviceOnline(fav.address).then((online: boolean) => {
        if (online) {
          let exists = this.devices.some((d: DiscoveredDevice) => d.name === fav.name);
          if (!exists) {
            this.devices.push(fav);
          }
        }
      });
    }
  }

  private async connectDevice(device: DiscoveredDevice): Promise<void> {
    this.currentDevice = device;
    this.status = `正在获取 ${device.name} 的媒体列表...`;
    let items = await this.remoteClient.fetchMediaList(device.address);
    this.mediaItems = items;
    this.status = `${device.name} - ${items.length} 个媒体文件`;
  }

  private getFilteredItems(): MediaItem[] {
    let items = this.mediaItems;

    if (this.searchQuery) {
      let query = this.searchQuery.toLowerCase();
      items = items.filter((item: MediaItem) =>
        item.title.toLowerCase().includes(query) ||
        item.folderName.toLowerCase().includes(query)
      );
    }

    if (this.typeFilter === 'video') {
      items = items.filter((item: MediaItem) => item.mimeType.includes('video'));
    } else if (this.typeFilter === 'audio') {
      items = items.filter((item: MediaItem) => item.mimeType.includes('audio'));
    }

    return items;
  }

  private getGroupedItems(): Map<string, MediaItem[]> {
    let filtered = this.getFilteredItems();
    let grouped = new Map<string, MediaItem[]>();

    for (let item of filtered) {
      let folder = item.folderName || '未分类';
      let items = grouped.get(folder);
      if (!items) {
        items = [];
        grouped.set(folder, items);
      }
      items.push(item);
    }

    return grouped;
  }

  private playMedia(item: MediaItem): void {
    let items = this.getFilteredItems();
    let index = items.findIndex((i: MediaItem) => i.id === item.id);
    if (index < 0) index = 0;

    let urls: string[] = [];
    let titles: string[] = [];
    let mediaIds: string[] = [];

    for (let i of items) {
      let url: string;
      if (this.currentDevice) {
        url = this.remoteClient.getMediaUrl(this.currentDevice.address, i.id);
      } else {
        url = `file://${i.relativePath}`;
      }
      urls.push(url);
      titles.push(i.title);
      mediaIds.push(i.id);
    }

    router.pushUrl({
      url: 'pages/Player',
      params: {
        urls: urls,
        titles: titles,
        mediaIds: mediaIds,
        startIndex: index,
        deviceAddress: this.currentDevice ? this.currentDevice.address : ''
      }
    });
  }

  private playAll(): void {
    let items = this.getFilteredItems();
    if (items.length === 0) return;
    this.playMedia(items[0]);
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

      // 媒体标题
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

      // 筛选按钮
      Row() {
        Text('全部')
          .fontSize(this.typeFilter === 'all' ? 15 : 13)
          .fontColor(this.typeFilter === 'all' ? '#1E88E5' : '#888888')
          .onClick(() => { this.typeFilter = 'all'; })

        Text('视频')
          .fontSize(this.typeFilter === 'video' ? 15 : 13)
          .fontColor(this.typeFilter === 'video' ? '#1E88E5' : '#888888')
          .margin({ left: 16 })
          .onClick(() => { this.typeFilter = 'video'; })

        Text('音频')
          .fontSize(this.typeFilter === 'audio' ? 15 : 13)
          .fontColor(this.typeFilter === 'audio' ? '#1E88E5' : '#888888')
          .margin({ left: 16 })
          .onClick(() => { this.typeFilter = 'audio'; })

        Text(this.isGroupByFolder ? '平铺' : '按文件夹')
          .fontSize(12)
          .fontColor(this.isGroupByFolder ? '#1E88E5' : '#888888')
          .layoutWeight(1)
          .textAlign(TextAlign.End)
          .onClick(() => { this.isGroupByFolder = !this.isGroupByFolder; })
      }
      .width('100%')
      .height(32)
      .padding({ left: 16, right: 16 })
      .margin({ bottom: 8 })

      // 全部播放按钮
      if (this.getFilteredItems().length > 0) {
        Text('全部播放')
          .width('90%')
          .height(48)
          .backgroundColor('#1E88E5')
          .fontColor('#FFFFFF')
          .fontSize(15)
          .fontWeight(FontWeight.Bold)
          .textAlign(TextAlign.Center)
          .borderRadius(8)
          .margin({ bottom: 8 })
          .onClick(() => this.playAll())
      }

      // 媒体列表
      List() {
        if (this.isGroupByFolder) {
          ForEach(Array.from(this.getGroupedItems().entries()), (entry: [string, MediaItem[]]) => {
            ListItem() {
              Row() {
                Text(`📁 ${entry[0]}`)
                  .fontSize(14)
                  .fontWeight(FontWeight.Bold)
                  .fontColor('#212121')
                  .layoutWeight(1)
                Text(`${entry[1].length} 个文件`)
                  .fontSize(12)
                  .fontColor('#757575')
                Text(this.expandedFolders.has(entry[0]) ? '▼' : '▶')
                  .fontSize(12)
                  .fontColor('#757575')
                  .margin({ left: 8 })
              }
              .width('100%')
              .height(48)
              .padding({ left: 16, right: 16 })
              .onClick(() => {
                if (this.expandedFolders.has(entry[0])) {
                  this.expandedFolders.delete(entry[0]);
                } else {
                  this.expandedFolders.add(entry[0]);
                }
              })
            }

            if (this.expandedFolders.has(entry[0])) {
              ForEach(entry[1], (item: MediaItem) => {
                ListItem() {
                  Row() {
                    if (item.thumbnailUri) {
                      Image(item.thumbnailUri)
                        .width(60)
                        .height(60)
                        .borderRadius(8)
                        .objectFit(ImageFit.Cover)
                    } else {
                      Column() {
                        Text(item.mimeType.includes('video') ? '🎬' : '🎵')
                          .fontSize(24)
                      }
                      .width(60)
                      .height(60)
                      .backgroundColor('#F0F0F0')
                      .justifyContent(FlexAlign.Center)
                      .borderRadius(8)
                    }

                    Column() {
                      Text(item.title)
                        .fontSize(16)
                        .fontColor('#212121')
                        .maxLines(1)
                        .textOverflow({ overflow: TextOverflow.Ellipsis })
                    }
                    .alignItems(HorizontalAlign.Start)
                    .layoutWeight(1)
                    .margin({ left: 12 })
                  }
                  .width('100%')
                  .height(72)
                  .padding({ left: 40, right: 16 })
                  .onClick(() => this.playMedia(item))
                }
              })
            }
          })
        } else {
          ForEach(this.getFilteredItems(), (item: MediaItem) => {
            ListItem() {
              Row() {
                if (item.thumbnailUri) {
                  Image(item.thumbnailUri)
                    .width(60)
                    .height(60)
                    .borderRadius(8)
                    .objectFit(ImageFit.Cover)
                } else {
                  Column() {
                    Text(item.mimeType.includes('video') ? '🎬' : '🎵')
                      .fontSize(24)
                  }
                  .width(60)
                  .height(60)
                  .backgroundColor('#F0F0F0')
                  .justifyContent(FlexAlign.Center)
                  .borderRadius(8)
                }

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
      }
      .width('100%')
      .layoutWeight(1)

      // 空状态提示
      if (this.getFilteredItems().length === 0) {
        Text('没有媒体文件')
          .fontSize(14)
          .fontColor('#757575')
          .layoutWeight(1)
          .textAlign(TextAlign.Center)
      }
    }
    .width('100%')
    .height('100%')
  }
}
