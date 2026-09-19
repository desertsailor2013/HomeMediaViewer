import { router } from '@kit.ArkUI';
import { promptAction } from '@kit.ArkUI';
import { RemoteMediaClient, FileOperationResult } from '../common/RemoteMediaClient';
import { MediaItem, DiscoveredDevice } from '../model/MediaModels';

@Entry
@Component
struct FileManager {
  @State mediaItems: MediaItem[] = [];
  @State currentPath: string = '/';
  @State status: string = '加载中...';
  @State deviceAddress: string = '';
  @State showUploadDialog: boolean = false;
  @State showRenameDialog: boolean = false;
  @State showCreateFolderDialog: boolean = false;
  @State showDeleteConfirm: boolean = false;
  @State selectedItem: MediaItem | null = null;
  @State newName: string = '';
  @State folderName: string = '';

  private remoteClient: RemoteMediaClient = new RemoteMediaClient();

  aboutToAppear(): void {
    let params = router.getParams() as Record<string, Object>;
    if (params) {
      this.deviceAddress = params['deviceAddress'] as string;
    }
    this.loadFiles();
  }

  private async loadFiles(): Promise<void> {
    this.status = '加载中...';
    let items = await this.remoteClient.fetchMediaList(this.deviceAddress);
    this.mediaItems = items;
    this.status = items.length === 0 ? '空文件夹' : `${items.length} 个文件`;
  }

  private async uploadFile(): Promise<void> {
    // TODO: 使用 filePicker 选择文件
    promptAction.showToast({ message: '请选择文件' });
    this.showUploadDialog = false;
  }

  private async renameItem(): Promise<void> {
    if (!this.selectedItem || !this.newName) return;

    let result = await this.remoteClient.renameFile(
      this.deviceAddress,
      this.selectedItem.id,
      this.newName
    );

    if (result.success) {
      promptAction.showToast({ message: '重命名成功' });
      this.loadFiles();
    } else {
      promptAction.showToast({ message: `重命名失败: ${result.message}` });
    }

    this.showRenameDialog = false;
    this.selectedItem = null;
    this.newName = '';
  }

  private async deleteItem(): Promise<void> {
    if (!this.selectedItem) return;

    let result = await this.remoteClient.deleteFile(
      this.deviceAddress,
      this.selectedItem.id
    );

    if (result.success) {
      promptAction.showToast({ message: '删除成功' });
      this.loadFiles();
    } else {
      promptAction.showToast({ message: `删除失败: ${result.message}` });
    }

    this.showDeleteConfirm = false;
    this.selectedItem = null;
  }

  private async createFolder(): Promise<void> {
    if (!this.folderName) return;

    let result = await this.remoteClient.createFolder(
      this.deviceAddress,
      this.folderName,
      this.currentPath
    );

    if (result.success) {
      promptAction.showToast({ message: '文件夹创建成功' });
      this.loadFiles();
    } else {
      promptAction.showToast({ message: `创建失败: ${result.message}` });
    }

    this.showCreateFolderDialog = false;
    this.folderName = '';
  }

  private formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
    return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
  }

  build() {
    Column() {
      // 标题栏
      Row() {
        Text('<')
          .fontSize(20)
          .fontColor('#FFFFFF')
          .onClick(() => router.back())

        Text('文件管理')
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
          .fontColor('#FFFFFF')
          .layoutWeight(1)
          .margin({ left: 12 })

        Text('+')
          .fontSize(24)
          .fontColor('#FFFFFF')
          .onClick(() => { this.showCreateFolderDialog = true; })
      }
      .width('100%')
      .height(56)
      .padding({ left: 16, right: 16 })
      .backgroundColor('#1E88E5')

      // 路径
      Text(this.currentPath)
        .fontSize(12)
        .fontColor('#757575')
        .width('100%')
        .padding({ left: 16, top: 8 })

      // 状态
      Text(this.status)
        .fontSize(14)
        .fontColor('#212121')
        .width('100%')
        .padding({ left: 16, top: 8, bottom: 8 })

      // 文件列表
      List() {
        ForEach(this.mediaItems, (item: MediaItem) => {
          ListItem() {
            Row() {
              Column() {
                Text(item.mimeType.includes('video') ? '🎬' : item.mimeType.includes('audio') ? '🎵' : '📄')
                  .fontSize(24)
              }
              .width(48)
              .height(48)
              .backgroundColor('#F0F0F0')
              .justifyContent(FlexAlign.Center)
              .borderRadius(8)

              Column() {
                Text(item.title)
                  .fontSize(16)
                  .fontColor('#212121')
                  .maxLines(1)
                  .textOverflow({ overflow: TextOverflow.Ellipsis })
                Text(this.formatSize(item.size))
                  .fontSize(12)
                  .fontColor('#757575')
                  .margin({ top: 4 })
              }
              .alignItems(HorizontalAlign.Start)
              .layoutWeight(1)
              .margin({ left: 12 })

              Column() {
                Text('✏️')
                  .fontSize(16)
                  .onClick(() => {
                    this.selectedItem = item;
                    this.newName = item.title;
                    this.showRenameDialog = true;
                  })
                Text('🗑️')
                  .fontSize(16)
                  .margin({ top: 8 })
                  .onClick(() => {
                    this.selectedItem = item;
                    this.showDeleteConfirm = true;
                  })
              }
            }
            .width('100%')
            .height(72)
            .padding({ left: 16, right: 16 })
          }
        })
      }
      .width('100%')
      .layoutWeight(1)

      // 底部按钮
      Row() {
        Text('上传文件')
          .fontSize(14)
          .fontColor('#1E88E5')
          .onClick(() => { this.showUploadDialog = true; })
      }
      .width('100%')
      .height(48)
      .justifyContent(FlexAlign.Center)
      .backgroundColor('#F5F5F5')
    }
    .width('100%')
    .height('100%')

    // 重命名对话框
    if (this.showRenameDialog) {
      Column() {
        Column() {
          Text('重命名')
            .fontSize(18)
            .fontWeight(FontWeight.Bold)
            .margin({ bottom: 16 })

          TextInput({ text: this.newName, placeholder: '输入新名称' })
            .width('100%')
            .height(48)
            .onChange((value: string) => { this.newName = value; })

          Row() {
            Text('取消')
              .fontSize(16)
              .fontColor('#757575')
              .onClick(() => { this.showRenameDialog = false; })
            Text('确定')
              .fontSize(16)
              .fontColor('#1E88E5')
              .margin({ left: 24 })
              .onClick(() => this.renameItem())
          }
          .width('100%')
          .justifyContent(FlexAlign.End)
          .margin({ top: 16 })
        }
        .width(300)
        .padding(24)
        .backgroundColor('#FFFFFF')
        .borderRadius(12)
      }
      .width('100%')
      .height('100%')
      .backgroundColor('#80000000')
      .justifyContent(FlexAlign.Center)
    }

    // 删除确认对话框
    if (this.showDeleteConfirm) {
      Column() {
        Column() {
          Text('确认删除')
            .fontSize(18)
            .fontWeight(FontWeight.Bold)
            .margin({ bottom: 16 })

          Text(`确定要删除 "${this.selectedItem?.title}" 吗？`)
            .fontSize(14)
            .fontColor('#757575')

          Row() {
            Text('取消')
              .fontSize(16)
              .fontColor('#757575')
              .onClick(() => { this.showDeleteConfirm = false; })
            Text('删除')
              .fontSize(16)
              .fontColor('#FF5252')
              .margin({ left: 24 })
              .onClick(() => this.deleteItem())
          }
          .width('100%')
          .justifyContent(FlexAlign.End)
          .margin({ top: 16 })
        }
        .width(300)
        .padding(24)
        .backgroundColor('#FFFFFF')
        .borderRadius(12)
      }
      .width('100%')
      .height('100%')
      .backgroundColor('#80000000')
      .justifyContent(FlexAlign.Center)
    }

    // 新建文件夹对话框
    if (this.showCreateFolderDialog) {
      Column() {
        Column() {
          Text('新建文件夹')
            .fontSize(18)
            .fontWeight(FontWeight.Bold)
            .margin({ bottom: 16 })

          TextInput({ text: this.folderName, placeholder: '文件夹名称' })
            .width('100%')
            .height(48)
            .onChange((value: string) => { this.folderName = value; })

          Row() {
            Text('取消')
              .fontSize(16)
              .fontColor('#757575')
              .onClick(() => { this.showCreateFolderDialog = false; })
            Text('创建')
              .fontSize(16)
              .fontColor('#1E88E5')
              .margin({ left: 24 })
              .onClick(() => this.createFolder())
          }
          .width('100%')
          .justifyContent(FlexAlign.End)
          .margin({ top: 16 })
        }
        .width(300)
        .padding(24)
        .backgroundColor('#FFFFFF')
        .borderRadius(12)
      }
      .width('100%')
      .height('100%')
      .backgroundColor('#80000000')
      .justifyContent(FlexAlign.Center)
    }
  }
}
