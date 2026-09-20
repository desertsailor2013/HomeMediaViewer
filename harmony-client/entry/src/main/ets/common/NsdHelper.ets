import { nsdManager } from '@kit.NetworkKit';
import { DiscoveredDevice } from '../model/MediaModels';

const TAG = 'NsdHelper';
const SERVICE_TYPE = '_hmv._tcp.';
const SERVICE_NAME = 'HomeMediaViewer';

/**
 * 设备发现监听器
 */
export interface DeviceListener {
  onDeviceFound(device: DiscoveredDevice): void;
  onDeviceLost(device: DiscoveredDevice): void;
  onDiscoveryFailed(errorCode: number): void;
}

/**
 * mDNS 设备发现帮助器
 */
export class NsdHelper {
  private context: Context;
  private discoveryService: nsdManager.DiscoveryService | null = null;
  private listener: DeviceListener | null = null;
  private discoveredDevices: Map<string, DiscoveredDevice> = new Map();

  constructor(context: Context) {
    this.context = context;
  }

  /**
   * 开始设备发现
   */
  startDiscovery(listener: DeviceListener): void {
    this.listener = listener;

    let discoveryOptions: nsdManager.DiscoveryOptions = {
      serviceType: SERVICE_TYPE,
      discoverTimeout: 0
    };

    nsdManager.createDiscoveryService(this.context, (err, service) => {
      if (err) {
        console.error(TAG, `createDiscoveryService failed: ${err.message}`);
        listener.onDiscoveryFailed(-1);
        return;
      }

      this.discoveryService = service;

      service.on('discovering', (data: nsdManager.DiscoveryServiceStateChange) => {
        console.info(TAG, `discovering: ${JSON.stringify(data)}`);
      });

      service.on('found', (data: nsdManager.ServiceDiscovered) => {
        console.info(TAG, `found service: ${data.serviceName}`);
        this.resolveService(data.serviceName, data.serviceType);
      });

      service.on('lost', (data: nsdManager.ServiceLost) => {
        console.info(TAG, `lost service: ${data.serviceName}`);
        let device = this.discoveredDevices.get(data.serviceName);
        if (device) {
          this.discoveredDevices.delete(data.serviceName);
          if (this.listener) {
            this.listener.onDeviceLost(device);
          }
        }
      });

      service.startDiscovery(discoveryOptions, (err) => {
        if (err) {
          console.error(TAG, `startDiscovery failed: ${err.message}`);
          listener.onDiscoveryFailed(-1);
        }
      });
    });
  }

  /**
   * 解析服务获取地址
   */
  private resolveService(serviceName: string, serviceType: string): void {
    if (!this.discoveryService) return;

    let resolveOptions: nsdManager.ResolveOptions = {
      serviceName: serviceName,
      serviceType: serviceType
    };

    this.discoveryService.resolveService(resolveOptions, (err, serviceInfo) => {
      if (err) {
        console.error(TAG, `resolveService failed: ${err.message}`);
        return;
      }

      let address = serviceInfo.address;
      let port = serviceInfo.port;

      if (address && port) {
        let device = new DiscoveredDevice(serviceName, address, port);
        this.discoveredDevices.set(serviceName, device);

        if (this.listener) {
          this.listener.onDeviceFound(device);
        }
      }
    });
  }

  /**
   * 停止设备发现
   */
  stopDiscovery(): void {
    if (this.discoveryService) {
      this.discoveryService.stopDiscovery((err) => {
        if (err) {
          console.error(TAG, `stopDiscovery failed: ${err.message}`);
        }
      });
      this.discoveryService.destroy();
      this.discoveryService = null;
    }
    this.discoveredDevices.clear();
    this.listener = null;
  }
}
