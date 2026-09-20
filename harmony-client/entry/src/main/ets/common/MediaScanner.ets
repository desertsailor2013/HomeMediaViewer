import { photoAccessHelper } from '@kit.MediaLibraryKit';
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
