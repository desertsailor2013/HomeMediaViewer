import Foundation
import Network

/// 设备发现监听器
protocol DeviceDiscoveryDelegate: AnyObject {
    func deviceFound(_ device: DiscoveredDevice)
    func deviceLost(_ device: DiscoveredDevice)
    func discoveryFailed(error: Error)
}

/// mDNS 设备发现帮助器
class NsdHelper: ObservableObject {
    private var browser: NWBrowser?
    private var discoveredDevices: [String: DiscoveredDevice] = [:]
    weak var delegate: DeviceDiscoveryDelegate?
    
    private let serviceType = "_hmv._tcp"
    
    /// 开始设备发现
    func startDiscovery() {
        let params = NWParameters()
        params.includePeerToPeer = true
        
        let browser = NWBrowser(for: .bonjour(type: serviceType, domain: nil), using: params)
        self.browser = browser
        
        browser.stateUpdateHandler = { [weak self] state in
            switch state {
            case .ready:
                print("Browser ready")
            case .failed(let error):
                DispatchQueue.main.async {
                    self?.delegate?.discoveryFailed(error: error)
                }
            default:
                break
            }
        }
        
        browser.browseResultsChangedHandler = { [weak self] results, changes in
            for result in results {
                let endpoint = result.endpoint
                if case let .service(name, _, _, _) = endpoint {
                    let device = DiscoveredDevice(
                        name: name,
                        host: "",
                        port: 0
                    )
                    
                    // 解析地址
                    self?.resolveEndpoint(endpoint, for: device)
                }
            }
            
            for change in changes {
                if case .removed(let result) = change {
                    if case let .service(name, _, _, _) = result.endpoint {
                        DispatchQueue.main.async {
                            self?.discoveredDevices.removeValue(forKey: name)
                            self?.delegate?.deviceLost(
                                DiscoveredDevice(name: name, host: "", port: 0)
                            )
                        }
                    }
                }
            }
        }
        
        browser.start(queue: .global())
    }
    
    /// 解析端点获取地址
    private func resolveEndpoint(_ endpoint: NWEndpoint, for device: DiscoveredDevice) {
        let connection = NWConnection(to: endpoint, using: .tcp)
        
        connection.stateUpdateHandler = { [weak self] state in
            switch state {
            case .ready:
                if let path = connection.currentPath,
                   let endpoint = path.remoteEndpoint {
                    switch endpoint {
                    case .hostPort(let host, let port):
                        let hostString = "\(host)"
                        let portInt = Int("\(port)") ?? 0
                    
                        let resolvedDevice = DiscoveredDevice(
                            name: device.name,
                            host: hostString,
                            port: portInt
                        )
                        
                        DispatchQueue.main.async {
                            self?.discoveredDevices[device.name] = resolvedDevice
                            self?.delegate?.deviceFound(resolvedDevice)
                        }
                    default:
                        break
                    }
                }
                connection.cancel()
            case .failed:
                connection.cancel()
            default:
                break
            }
        }
        
        connection.start(queue: .global())
    }
    
    /// 停止设备发现
    func stopDiscovery() {
        browser?.cancel()
        browser = nil
        discoveredDevices.removeAll()
    }
    
    /// 注册 mDNS 服务（使本设备可被发现）
    func registerService(port: Int) {
        let listener: NWListener
        do {
            let params = NWParameters()
            params.includePeerToPeer = true
            listener = try NWListener(using: params, on: NWEndpoint.Port(rawValue: UInt16(port))!)
        } catch {
            print("Failed to create listener: \(error)")
            return
        }
        
        listener.service = NWListener.Service(
            name: "HomeMediaViewer",
            type: serviceType,
            txtRecord: NWTXTRecord(["deviceType": "phone", "version": "1.0"])
        )
        
        listener.stateUpdateHandler = { state in
            switch state {
            case .ready:
                print("Service registered on port \(port)")
            case .failed(let error):
                print("Service registration failed: \(error)")
            default:
                break
            }
        }
        
        listener.newConnectionHandler = { connection in
            connection.cancel()
        }
        
        listener.start(queue: .global())
    }
}
