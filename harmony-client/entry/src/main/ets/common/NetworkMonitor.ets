import { connection } from '@kit.NetworkKit';

const TAG = 'NetworkMonitor';

/**
 * 网络状态监听器
 */
export interface NetworkListener {
  onNetworkAvailable(): void;
  onNetworkLost(): void;
}

/**
 * 网络状态监控器
 */
export class NetworkMonitor {
  private context: Context;
  private listener: NetworkListener | null = null;
  private isListening: boolean = false;

  constructor(context: Context) {
    this.context = context;
  }

  /**
   * 开始监听网络状态
   */
  startListening(listener: NetworkListener): void {
    this.listener = listener;

    // 检查当前网络状态
    let netConnection = connection.getActiveNet();
    if (!netConnection) {
      listener.onNetworkLost();
    }

    // 监听网络变化
    try {
      connection.on('netAvailable', (data: connection.NetAvailableCallbackData) => {
        if (this.listener) {
          this.listener.onNetworkAvailable();
        }
      });

      connection.on('netUnavailable', () => {
        if (this.listener) {
          this.listener.onNetworkLost();
        }
      });

      this.isListening = true;
    } catch (err) {
      console.error(TAG, `startListening failed: ${err}`);
    }
  }

  /**
   * 停止监听网络状态
   */
  stopListening(): void {
    if (this.isListening) {
      try {
        connection.off('netAvailable');
        connection.off('netUnavailable');
      } catch (err) {
        console.error(TAG, `stopListening failed: ${err}`);
      }
      this.isListening = false;
    }
    this.listener = null;
  }

  /**
   * 检查当前是否已连接网络
   */
  isCurrentlyConnected(): boolean {
    let netConnection = connection.getActiveNet();
    return netConnection !== null;
  }
}
