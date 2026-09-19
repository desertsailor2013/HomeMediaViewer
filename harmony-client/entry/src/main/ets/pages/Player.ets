import { router } from '@kit.ArkUI';
import { media } from '@kit.MediaKit';
import { window } from '@kit.ArkUI';
import { RemoteMediaClient } from '../common/RemoteMediaClient';
import { PlayProgressManager } from '../common/PlayProgressManager';
import { PlaybackSpeedManager } from '../common/PlaybackSpeedManager';
import { DiscoveredDevice, QueueItem } from '../model/MediaModels';

const SPEED_OPTIONS: number[] = [0.5, 0.75, 1.0, 1.25, 1.5, 2.0];

@Entry
@Component
struct Player {
  @State queue: QueueItem[] = [];
  @State queueIndex: number = 0;
  @State currentTitle: string = '';
  @State isPlaying: boolean = false;
  @State isFullscreen: boolean = false;
  @State currentSpeed: number = 1.0;
  @State speedIndex: number = 2;
  @State currentPosition: number = 0;
  @State duration: number = 0;
  @State showQueue: boolean = false;
  @State showSpeedMenu: boolean = false;

  private avPlayer: media.AVPlayer | null = null;
  private progressManager: PlayProgressManager = new PlayProgressManager(this.getUIContext().getHostContext());
  private speedManager: PlaybackSpeedManager = new PlaybackSpeedManager(this.getUIContext().getHostContext());
  private remoteClient: RemoteMediaClient = new RemoteMediaClient();
  private timer: number = -1;

  aboutToAppear(): void {
    let params = router.getParams() as Record<string, Object>;
    if (params) {
      let urls = params['urls'] as string[];
      let titles = params['titles'] as string[];
      let mediaIds = params['mediaIds'] as string[];
      let startIndex = params['startIndex'] as number;
      let deviceAddress = params['deviceAddress'] as string;

      if (urls) {
        for (let i = 0; i < urls.length; i++) {
          let item = new QueueItem(
            urls[i],
            titles ? titles[i] : '',
            mediaIds ? mediaIds[i] : ''
          );
          this.queue.push(item);
        }
        this.queueIndex = startIndex || 0;
      }
    }

    this.loadSpeed();
  }

  aboutToDisappear(): void {
    this.releasePlayer();
    if (this.timer >= 0) {
      clearInterval(this.timer);
    }
  }

  private async loadSpeed(): Promise<void> {
    this.currentSpeed = await this.speedManager.restore();
    this.speedIndex = SPEED_OPTIONS.indexOf(this.currentSpeed);
    if (this.speedIndex < 0) this.speedIndex = 2;
  }

  private async startPlayback(): Promise<void> {
    if (this.queueIndex < 0 || this.queueIndex >= this.queue.length) return;

    let item = this.queue[this.queueIndex];
    this.currentTitle = item.title;

    try {
      this.avPlayer = await media.createAVPlayer();
      let avPlayer = this.avPlayer as media.AVPlayer;

      avPlayer.on('stateChange', (state: string) => {
        if (state === 'initialized') {
          avPlayer.setSpeed(this.currentSpeed);
          avPlayer.play();
          this.isPlaying = true;
          this.startProgressTimer();
        } else if (state === 'completed') {
          this.playNext();
        }
      });

      avPlayer.on('timeUpdate', (time: number) => {
        this.currentPosition = time;
      });

      avPlayer.on('error', (err: BusinessError) => {
        console.error('Playback error:', err.message);
        this.isPlaying = false;
      });

      await avPlayer.url = item.url;
    } catch (err) {
      console.error('Failed to start playback:', err);
      this.isPlaying = false;
    }
  }

  private startProgressTimer(): void {
    if (this.timer >= 0) {
      clearInterval(this.timer);
    }
    this.timer = setInterval(() => {
      if (this.avPlayer) {
        this.avPlayer.getDuration().then((duration: number) => {
          this.duration = duration;
        });
      }
    }, 1000);
  }

  private playNext(): void {
    if (this.queueIndex < this.queue.length - 1) {
      this.saveProgress();
      this.queueIndex++;
      this.releasePlayer();
      this.startPlayback();
    } else {
      this.isPlaying = false;
    }
  }

  private playPrevious(): void {
    if (this.queueIndex > 0) {
      this.saveProgress();
      this.queueIndex--;
      this.releasePlayer();
      this.startPlayback();
    }
  }

  private togglePlayPause(): void {
    if (!this.avPlayer) return;

    if (this.isPlaying) {
      this.avPlayer.pause();
      this.isPlaying = false;
    } else {
      this.avPlayer.play();
      this.isPlaying = true;
    }
  }

  private async changeSpeed(): Promise<void> {
    this.speedIndex = (this.speedIndex + 1) % SPEED_OPTIONS.length;
    this.currentSpeed = SPEED_OPTIONS[this.speedIndex];
    await this.speedManager.save(this.currentSpeed);

    if (this.avPlayer) {
      this.avPlayer.setSpeed(this.currentSpeed);
    }
  }

  private async seekTo(position: number): Promise<void> {
    if (this.avPlayer) {
      await this.avPlayer.seek(position);
    }
  }

  private toggleFullscreen(): void {
    let context = this.getUIContext().getHostContext();
    window.getLastWindow(context).then((win: window.Window) => {
      if (this.isFullscreen) {
        win.setWindowLayoutFullScreen(false);
        this.isFullscreen = false;
      } else {
        win.setWindowLayoutFullScreen(true);
        this.isFullscreen = true;
      }
    });
  }

  private async saveProgress(): Promise<void> {
    if (this.queueIndex >= 0 && this.queueIndex < this.queue.length) {
      let item = this.queue[this.queueIndex];
      if (item.id) {
        await this.progressManager.save(item.id, this.currentPosition, this.duration);
      }
    }
  }

  private removeQueueItem(index: number): void {
    if (index < 0 || index >= this.queue.length) return;

    let wasPlaying = this.isPlaying;
    this.queue.splice(index, 1);

    if (this.queue.length === 0) {
      this.releasePlayer();
      router.back();
      return;
    }

    if (index < this.queueIndex) {
      this.queueIndex--;
    } else if (index === this.queueIndex && this.queueIndex >= this.queue.length) {
      this.queueIndex = this.queue.length - 1;
    }

    this.releasePlayer();
    if (wasPlaying) {
      this.startPlayback();
    }
  }

  private releasePlayer(): void {
    if (this.avPlayer) {
      this.avPlayer.release();
      this.avPlayer = null;
    }
    this.isPlaying = false;
    if (this.timer >= 0) {
      clearInterval(this.timer);
      this.timer = -1;
    }
  }

  private formatTime(ms: number): string {
    let seconds = Math.floor(ms / 1000);
    let minutes = Math.floor(seconds / 60);
    let hours = Math.floor(minutes / 60);
    minutes = minutes % 60;
    seconds = seconds % 60;
    if (hours > 0) {
      return `${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
    }
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  }

  build() {
    Column() {
      // 标题栏
      Row() {
        Text('<')
          .fontSize(20)
          .fontColor('#FFFFFF')
          .onClick(() => router.back())

        Text(this.currentTitle)
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
          .fontColor('#FFFFFF')
          .layoutWeight(1)
          .margin({ left: 12 })
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
      }
      .width('100%')
      .height(56)
      .padding({ left: 16, right: 16 })
      .backgroundColor('#1a1a1a')

      // 播放器区域
      Column() {
        if (!this.isPlaying && this.queue.length === 0) {
          Text('无媒体')
            .fontSize(16)
            .fontColor('#666666')
        }
      }
      .width('100%')
      .layoutWeight(1)
      .backgroundColor('#000000')
      .justifyContent(FlexAlign.Center)

      // 进度条
      Column() {
        Slider({
          value: this.duration > 0 ? this.currentPosition : 0,
          min: 0,
          max: this.duration > 0 ? this.duration : 1,
          step: 1000
        })
          .width('100%')
          .blockColor('#1E88E5')
          .trackColor('#333333')
          .selectedColor('#1E88E5')
          .onChange((value: number) => {
            this.seekTo(value);
          })

        Row() {
          Text(this.formatTime(this.currentPosition))
            .fontSize(12)
            .fontColor('#999999')

          Text(this.formatTime(this.duration))
            .fontSize(12)
            .fontColor('#999999')
        }
        .width('100%')
        .justifyContent(FlexAlign.SpaceBetween)
      }
      .width('100%')
      .padding({ left: 16, right: 16, top: 8 })

      // 控制栏
      Row() {
        // 速度按钮
        Text(`${this.currentSpeed}x`)
          .fontSize(14)
          .fontColor('#1E88E5')
          .padding({ left: 12, right: 12 })
          .height(36)
          .backgroundColor('#1a1a1a')
          .borderRadius(18)
          .onClick(() => this.changeSpeed())

        // 上一个
        Text('⏮')
          .fontSize(28)
          .fontColor('#FFFFFF')
          .onClick(() => this.playPrevious())

        // 播放/暂停
        Text(this.isPlaying ? '⏸' : '▶')
          .fontSize(40)
          .fontColor('#FFFFFF')
          .margin({ left: 24, right: 24 })
          .onClick(() => this.togglePlayPause())

        // 下一个
        Text('⏭')
          .fontSize(28)
          .fontColor('#FFFFFF')
          .onClick(() => this.playNext())

        // 全屏
        Text(this.isFullscreen ? '⛶' : '⛶')
          .fontSize(20)
          .fontColor('#1E88E5')
          .onClick(() => this.toggleFullscreen())
      }
      .width('100%')
      .height(64)
      .justifyContent(FlexAlign.SpaceEvenly)
      .backgroundColor('#1a1a1a')

      // 队列面板
      if (this.showQueue) {
        Column() {
          Text('播放队列')
            .fontSize(16)
            .fontWeight(FontWeight.Bold)
            .fontColor('#FFFFFF')
            .padding({ left: 16, top: 12, bottom: 8 })

          List() {
            ForEach(this.queue, (item: QueueItem, index: number) => {
              ListItem() {
                Row() {
                  Text(index === this.queueIndex ? '▶' : '')
                    .fontSize(14)
                    .fontColor('#1E88E5')
                    .width(24)

                  Text(item.title || '未知标题')
                    .fontSize(14)
                    .fontColor(index === this.queueIndex ? '#1E88E5' : '#FFFFFF')
                    .layoutWeight(1)
                    .maxLines(1)
                    .textOverflow({ overflow: TextOverflow.Ellipsis })

                  Text('✕')
                    .fontSize(14)
                    .fontColor('#666666')
                    .onClick(() => this.removeQueueItem(index))
                }
                .width('100%')
                .height(48)
                .padding({ left: 16, right: 16 })
                .onClick(() => {
                  this.saveProgress();
                  this.queueIndex = index;
                  this.releasePlayer();
                  this.startPlayback();
                })
              }
            })
          }
          .width('100%')
          .layoutWeight(1)

          Row() {
            Text('清空队列')
              .fontSize(14)
              .fontColor('#FF5252')
              .onClick(() => {
                this.releasePlayer();
                router.back();
              })
          }
          .width('100%')
          .height(48)
          .justifyContent(FlexAlign.Center)
        }
        .width('100%')
        .height(300)
        .backgroundColor('#1a1a1a')
      }

      // 底部按钮
      Row() {
        Text('队列')
          .fontSize(14)
          .fontColor('#1E88E5')
          .onClick(() => {
            this.showQueue = !this.showQueue;
          })
      }
      .width('100%')
      .height(48)
      .justifyContent(FlexAlign.Center)
      .backgroundColor('#1a1a1a')
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#000000')
  }
}
