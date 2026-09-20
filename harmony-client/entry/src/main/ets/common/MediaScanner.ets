import { photoAccessHelper } from '@kit.MediaLibraryKit';
import { abilityAccessCtrl, common } from '@kit.AbilityKit';
import { MediaItem } from '../model/MediaModels';

const TAG = 'MediaScanner';

/**
 * 本地媒体扫描器 - 使用 MediaKit PhotoAccessHelper
 */
export class MediaScanner {
  private context: Context;

  constructor(context: Context) {
    this.context = context;
  }

  /**
   * 请求媒体权限
   */
  async requestAuthorization(): Promise<boolean> {
    try {
      let atManager = abilityAccessCtrl.createAtManager();
      let context = this.context as common.UIAbilityContext;
      let result = await atManager.requestPermissionsFromUser(context, [
        'ohos.permission.READ_MEDIA',
        'ohos.permission.READ_IMAGEVIDEO'
      ]);
      return result.authResults.every((r: number) => r === 0);
    } catch (err) {
      console.error(TAG, `requestAuthorization failed: ${err}`);
      return false;
    }
  }

  /**
   * 扫描视频文件
   */
  async scanVideos(): Promise<MediaItem[]> {
    return this.scanByType(photoAccessHelper.PhotoType.VIDEO);
  }

  /**
   * 扫描音频文件
   */
  async scanAudios(): Promise<MediaItem[]> {
    return this.scanByType(photoAccessHelper.PhotoType.AUDIO);
  }

  /**
   * 扫描所有媒体
   */
  async scanAll(): Promise<MediaItem[]> {
    let videos = await this.scanVideos();
    let audios = await this.scanAudios();
    return videos.concat(audios);
  }

  /**
   * 获取缩略图 URI
   */
  getThumbnailUri(mediaId: string): string {
    return `file://media/Photo/${mediaId}/thumbnail`;
  }

  /**
   * 按类型扫描
   */
  private async scanByType(photoType: photoAccessHelper.PhotoType): Promise<MediaItem[]> {
    let items: MediaItem[] = [];
    try {
      let helper = photoAccessHelper.getPhotoAccessHelper(this.context);

      let predicates = new photoAccessHelper.DataAbilityPredicates();
      predicates.equalTo(photoAccessHelper.PhotoColumns.PHOTO_TYPE, photoType);

      let fetchOptions: photoAccessHelper.FetchOptions = {
        predicates: predicates
      };

      let result = await helper.fetch(photoAccessHelper.PhotoColumns.DATA, fetchOptions);

      if (result && result.rowCount > 0) {
        result.goToFirstRow();
        for (let i = 0; i < result.rowCount; i++) {
          let id = result.getLong(result.getColumnIndex(photoAccessHelper.PhotoColumns.ID));
          let title = result.getString(result.getColumnIndex(photoAccessHelper.PhotoColumns.TITLE));
          let relativePath = result.getString(result.getColumnIndex(photoAccessHelper.PhotoColumns.RELATIVE_PATH));
          let mimeType = result.getString(result.getColumnIndex(photoAccessHelper.PhotoColumns.MIME_TYPE));
          let size = result.getLong(result.getColumnIndex(photoAccessHelper.PhotoColumns.SIZE));

          let item = new MediaItem();
          item.id = id.toString();
          item.title = title;
          item.relativePath = relativePath;
          item.mimeType = mimeType;
          item.size = size;
          item.thumbnailUri = this.getThumbnailUri(id.toString());
          item.folderName = relativePath.split('/').slice(-2, -1).join('/') || '根目录';

          items.push(item);
          result.goToNextRow();
        }
      }

      if (result) {
        result.close();
      }
    } catch (err) {
      console.error(TAG, `scanByType failed: ${err}`);
    }
    return items;
  }
}
