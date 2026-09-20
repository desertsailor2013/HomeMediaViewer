/**
 * HTTP+Range 流媒体服务端
 * 基于鸿蒙原生 Socket API 实现
 */
import socket from '@ohos.net.socket';
import { MediaItem } from '../model/MediaModels';

const MIME_JSON = 'application/json; charset=utf-8';
const MIME_OCTET = 'application/octet-stream';
const STATUS_OK = '200 OK';
const STATUS_CREATED = '201 Created';
const STATUS_NO_CONTENT = '204 No Content';
const STATUS_PARTIAL = '206 Partial Content';
const STATUS_RANGE_416 = '416 Range Not Satisfiable';
const STATUS_NOT_FOUND = '404 Not Found';
const STATUS_BAD_REQUEST = '400 Bad Request';
const STATUS_UNAUTHORIZED = '401 Unauthorized';

interface HttpRequest {
  method: string;
  path: string;
  headers: Map<string, string>;
  body: string;
  queryParams: Map<string, string>;
}

interface HttpResponse {
  status: string;
  headers: Map<string, string>;
  body: ArrayBuffer | string;
}

type MediaProvider = () => MediaItem[];
type ThumbnailProvider = (id: string) => ArrayBuffer | null;
type FileStreamProvider = (path: string, offset: number, length: number) => ArrayBuffer | null;
type FileSizeProvider = (path: string) => number;

export class HttpServer {
  private serverSocket: socket.TCPSocketServer | null = null;
  private port: number = 0;
  private running: boolean = false;
  private mediaProvider: MediaProvider;
  private thumbnailProvider: ThumbnailProvider | null = null;
  private fileStreamProvider: FileStreamProvider | null = null;
  private fileSizeProvider: FileSizeProvider | null = null;
  private adminPassword: string = '';
  private deviceType: string = 'phone';
  private deviceName: string = 'HarmonyOS Device';

  constructor(port: number = 0) {
    this.port = port;
    this.mediaProvider = () => [];
  }

  /**
   * 设置媒体数据提供者
   */
  setMediaProvider(provider: MediaProvider): void {
    this.mediaProvider = provider;
  }

  /**
   * 设置缩略图提供者
   */
  setThumbnailProvider(provider: ThumbnailProvider): void {
    this.thumbnailProvider = provider;
  }

  /**
   * 设置文件流提供者
   */
  setFileStreamProvider(provider: FileStreamProvider): void {
    this.fileStreamProvider = provider;
  }

  /**
   * 设置文件大小提供者
   */
  setFileSizeProvider(provider: FileSizeProvider): void {
    this.fileSizeProvider = provider;
  }

  /**
   * 设置管理密码
   */
  setAdminPassword(password: string): void {
    this.adminPassword = password;
  }

  /**
   * 设置设备信息
   */
  setDeviceInfo(deviceType: string, deviceName: string): void {
    this.deviceType = deviceType;
    this.deviceName = deviceName;
  }

  /**
   * 获取监听端口
   */
  getPort(): number {
    return this.port;
  }

  /**
   * 启动服务器
   */
  async start(): Promise<boolean> {
    if (this.running) {
      return true;
    }

    try {
      this.serverSocket = new socket.TCPSocketServer();
      
      await this.serverSocket.listen({
        backlog: 128
      });

      const localAddress = await this.serverSocket.getState();
      if (localAddress.isConnected !== undefined) {
        this.port = this.port || 8080;
      }

      this.running = true;
      this.acceptConnections();
      
      console.info(`HTTP Server started on port ${this.port}`);
      return true;
    } catch (err) {
      console.error('Failed to start HTTP server:', JSON.stringify(err));
      return false;
    }
  }

  /**
   * 停止服务器
   */
  async stop(): Promise<void> {
    this.running = false;
    if (this.serverSocket) {
      try {
        await this.serverSocket.close();
      } catch (err) {
        console.error('Error closing server:', JSON.stringify(err));
      }
      this.serverSocket = null;
    }
  }

  /**
   * 接受连接
   */
  private async acceptConnections(): Promise<void> {
    if (!this.serverSocket || !this.running) {
      return;
    }

    try {
      const clientSocket = await this.serverSocket.accept();
      this.handleClient(clientSocket);
      this.acceptConnections();
    } catch (err) {
      if (this.running) {
        console.error('Error accepting connection:', JSON.stringify(err));
        setTimeout(() => this.acceptConnections(), 100);
      }
    }
  }

  /**
   * 处理客户端连接
   */
  private async handleClient(clientSocket: socket.TCPSocketConnection): Promise<void> {
    try {
      const data = await clientSocket.recv({ timeout: 5000 });
      if (!data || data.byteLength === 0) {
        await clientSocket.close();
        return;
      }

      const request = this.parseRequest(data);
      if (!request) {
        await this.sendResponse(clientSocket, STATUS_BAD_REQUEST, 'text/plain', 'Bad Request');
        await clientSocket.close();
        return;
      }

      const response = this.handleRequest(request);
      await this.sendHttpResponse(clientSocket, response);
      await clientSocket.close();
    } catch (err) {
      console.error('Error handling client:', JSON.stringify(err));
      try {
        await clientSocket.close();
      } catch (e) {
        // ignore
      }
    }
  }

  /**
   * 解析 HTTP 请求
   */
  private parseRequest(data: ArrayBuffer): HttpRequest | null {
    try {
      const decoder = new util.TextDecoder();
      const text = decoder.decodeIntoString(new Uint8Array(data));
      const lines = text.split('\r\n');
      
      if (lines.length === 0) {
        return null;
      }

      const [method, pathWithQuery] = lines[0].split(' ');
      if (!method || !pathWithQuery) {
        return null;
      }

      const questionIndex = pathWithQuery.indexOf('?');
      let path = pathWithQuery;
      const queryParams = new Map<string, string>();

      if (questionIndex > 0) {
        path = pathWithQuery.substring(0, questionIndex);
        const queryString = pathWithQuery.substring(questionIndex + 1);
        const pairs = queryString.split('&');
        for (const pair of pairs) {
          const [key, value] = pair.split('=');
          if (key) {
            queryParams.set(decodeURIComponent(key), decodeURIComponent(value || ''));
          }
        }
      }

      const headers = new Map<string, string>();
      let bodyStart = -1;

      for (let i = 1; i < lines.length; i++) {
        if (lines[i] === '') {
          bodyStart = i + 1;
          break;
        }
        const colonIndex = lines[i].indexOf(':');
        if (colonIndex > 0) {
          const key = lines[i].substring(0, colonIndex).trim().toLowerCase();
          const value = lines[i].substring(colonIndex + 1).trim();
          headers.set(key, value);
        }
      }

      let body = '';
      if (bodyStart >= 0 && bodyStart < lines.length) {
        body = lines.slice(bodyStart).join('\r\n');
      }

      return { method, path, headers, body, queryParams };
    } catch (err) {
      console.error('Parse request error:', JSON.stringify(err));
      return null;
    }
  }

  /**
   * 处理 HTTP 请求
   */
  private handleRequest(request: HttpRequest): HttpResponse {
    const { method, path, headers, body, queryParams } = request;

    // 路由匹配
    if (method === 'GET' && path === '/media') {
      return this.handleListMedia();
    }
    if (method === 'GET' && path.match(/^\/media\/[^/]+$/)) {
      const id = path.split('/')[2];
      return this.handleStreamMedia(id, headers);
    }
    if (method === 'GET' && path.match(/^\/media\/[^/]+\/thumbnail$/)) {
      const id = path.split('/')[2];
      return this.handleThumbnail(id);
    }
    if (method === 'GET' && path === '/device/info') {
      return this.handleGetDeviceInfo();
    }
    if (method === 'POST' && path === '/device/info') {
      return this.handleSetDeviceInfo(body);
    }
    if (method === 'GET' && path === '/admin/password') {
      return this.handleGetAdminPassword();
    }
    if (method === 'POST' && path === '/admin/password') {
      return this.handleSetAdminPassword(body);
    }
    if (method === 'POST' && path === '/admin/verify') {
      return this.handleVerifyAdminPassword(body);
    }
    if (method === 'GET' && path === '/search') {
      const query = queryParams.get('q') || '';
      const type = queryParams.get('type') || 'all';
      return this.handleSearch(query, type);
    }
    if (method === 'POST' && path === '/play') {
      return this.handlePlayCommand(body);
    }
    if (method === 'GET' && path === '/scanpaths') {
      return this.handleGetScanPaths();
    }
    if (method === 'POST' && path === '/scanpaths') {
      return this.handleAddScanPath(body);
    }

    return { status: STATUS_NOT_FOUND, headers: new Map(), body: 'Not Found' };
  }

  /**
   * 获取媒体列表
   */
  private handleListMedia(): HttpResponse {
    const items = this.mediaProvider();
    const json = JSON.stringify(items.map(item => ({
      id: item.id,
      title: item.title,
      mimeType: item.mimeType,
      size: item.size,
      relativePath: item.relativePath,
      thumbnailUri: item.thumbnailUri,
      folderName: item.folderName
    })));
    
    const headers = new Map<string, string>();
    headers.set('Content-Type', MIME_JSON);
    return { status: STATUS_OK, headers, body: json };
  }

  /**
   * 流式传输媒体
   */
  private handleStreamMedia(id: string, headers: Map<string, string>): HttpResponse {
    const items = this.mediaProvider();
    const item = items.find(i => i.id === id);
    if (!item) {
      return { status: STATUS_NOT_FOUND, headers: new Map(), body: 'Not Found' };
    }

    if (!this.fileStreamProvider || !this.fileSizeProvider) {
      return { status: STATUS_NOT_FOUND, headers: new Map(), body: 'File provider not set' };
    }

    const fileSize = this.fileSizeProvider(item.relativePath);
    const rangeHeader = headers.get('range');

    if (rangeHeader) {
      const match = rangeHeader.match(/bytes=(\d+)-(\d*)/);
      if (match) {
        const start = parseInt(match[1]);
        const end = match[2] ? parseInt(match[2]) : fileSize - 1;
        const length = end - start + 1;

        if (start >= fileSize || start < 0) {
          const respHeaders = new Map<string, string>();
          respHeaders.set('Content-Range', `bytes */${fileSize}`);
          return { status: STATUS_RANGE_416, headers: respHeaders, body: '' };
        }

        const data = this.fileStreamProvider(item.relativePath, start, length);
        if (!data) {
          return { status: STATUS_NOT_FOUND, headers: new Map(), body: 'Read failed' };
        }

        const respHeaders = new Map<string, string>();
        respHeaders.set('Content-Range', `bytes ${start}-${end}/${fileSize}`);
        respHeaders.set('Content-Length', data.byteLength.toString());
        respHeaders.set('Content-Type', MIME_OCTET);
        respHeaders.set('Accept-Ranges', 'bytes');
        return { status: STATUS_PARTIAL, headers: respHeaders, body: data };
      }
    }

    // 整文件
    const data = this.fileStreamProvider(item.relativePath, 0, fileSize);
    if (!data) {
      return { status: STATUS_NOT_FOUND, headers: new Map(), body: 'Read failed' };
    }

    const respHeaders = new Map<string, string>();
    respHeaders.set('Content-Length', data.byteLength.toString());
    respHeaders.set('Content-Type', MIME_OCTET);
    respHeaders.set('Accept-Ranges', 'bytes');
    return { status: STATUS_OK, headers: respHeaders, body: data };
  }

  /**
   * 获取缩略图
   */
  private handleThumbnail(id: string): HttpResponse {
    if (!this.thumbnailProvider) {
      return { status: STATUS_NOT_FOUND, headers: new Map(), body: 'Thumbnail not supported' };
    }

    const data = this.thumbnailProvider(id);
    if (!data) {
      return { status: STATUS_NOT_FOUND, headers: new Map(), body: 'Thumbnail not found' };
    }

    const headers = new Map<string, string>();
    headers.set('Content-Type', 'image/jpeg');
    headers.set('Content-Length', data.byteLength.toString());
    return { status: STATUS_OK, headers, body: data };
  }

  /**
   * 获取设备信息
   */
  private handleGetDeviceInfo(): HttpResponse {
    const json = JSON.stringify({
      deviceType: this.deviceType,
      deviceName: this.deviceName
    });
    const headers = new Map<string, string>();
    headers.set('Content-Type', MIME_JSON);
    return { status: STATUS_OK, headers, body: json };
  }

  /**
   * 设置设备信息
   */
  private handleSetDeviceInfo(body: string): HttpResponse {
    try {
      const data = JSON.parse(body);
      if (data.deviceType) this.deviceType = data.deviceType;
      if (data.deviceName) this.deviceName = data.deviceName;
      return { status: STATUS_OK, headers: new Map(), body: JSON.stringify({ success: true }) };
    } catch (e) {
      return { status: STATUS_BAD_REQUEST, headers: new Map(), body: 'Invalid JSON' };
    }
  }

  /**
   * 获取管理密码状态
   */
  private handleGetAdminPassword(): HttpResponse {
    const json = JSON.stringify({ configured: this.adminPassword.length > 0 });
    const headers = new Map<string, string>();
    headers.set('Content-Type', MIME_JSON);
    return { status: STATUS_OK, headers, body: json };
  }

  /**
   * 设置管理密码
   */
  private handleSetAdminPassword(body: string): HttpResponse {
    try {
      const data = JSON.parse(body);
      if (data.password) {
        this.adminPassword = data.password;
        return { status: STATUS_OK, headers: new Map(), body: JSON.stringify({ success: true }) };
      }
      return { status: STATUS_BAD_REQUEST, headers: new Map(), body: 'Password required' };
    } catch (e) {
      return { status: STATUS_BAD_REQUEST, headers: new Map(), body: 'Invalid JSON' };
    }
  }

  /**
   * 验证管理密码
   */
  private handleVerifyAdminPassword(body: string): HttpResponse {
    try {
      const data = JSON.parse(body);
      const valid = this.adminPassword === data.password;
      const json = JSON.stringify({ valid });
      const headers = new Map<string, string>();
      headers.set('Content-Type', MIME_JSON);
      return { status: STATUS_OK, headers, body: json };
    } catch (e) {
      return { status: STATUS_BAD_REQUEST, headers: new Map(), body: 'Invalid JSON' };
    }
  }

  /**
   * 搜索媒体
   */
  private handleSearch(query: string, type: string): HttpResponse {
    let items = this.mediaProvider();
    
    if (query) {
      items = items.filter(item => 
        item.title.toLowerCase().includes(query.toLowerCase())
      );
    }

    if (type !== 'all') {
      items = items.filter(item => item.mimeType.startsWith(type + '/'));
    }

    const json = JSON.stringify(items.map(item => ({
      id: item.id,
      title: item.title,
      mimeType: item.mimeType,
      size: item.size,
      relativePath: item.relativePath,
      thumbnailUri: item.thumbnailUri,
      folderName: item.folderName
    })));

    const headers = new Map<string, string>();
    headers.set('Content-Type', MIME_JSON);
    return { status: STATUS_OK, headers, body: json };
  }

  /**
   * 处理播放命令
   */
  private handlePlayCommand(body: string): HttpResponse {
    try {
      const data = JSON.parse(body);
      console.info(`Play command: mediaId=${data.mediaId}, position=${data.position}`);
      return { status: STATUS_OK, headers: new Map(), body: JSON.stringify({ success: true }) };
    } catch (e) {
      return { status: STATUS_BAD_REQUEST, headers: new Map(), body: 'Invalid JSON' };
    }
  }

  /**
   * 获取扫描路径
   */
  private handleGetScanPaths(): HttpResponse {
    const json = JSON.stringify({ paths: [] });
    const headers = new Map<string, string>();
    headers.set('Content-Type', MIME_JSON);
    return { status: STATUS_OK, headers, body: json };
  }

  /**
   * 添加扫描路径
   */
  private handleAddScanPath(body: string): HttpResponse {
    try {
      const data = JSON.parse(body);
      console.info(`Add scan path: ${data.path}`);
      return { status: STATUS_CREATED, headers: new Map(), body: JSON.stringify({ success: true }) };
    } catch (e) {
      return { status: STATUS_BAD_REQUEST, headers: new Map(), body: 'Invalid JSON' };
    }
  }

  /**
   * 发送 HTTP 响应
   */
  private async sendHttpResponse(clientSocket: socket.TCPSocketConnection, response: HttpResponse): Promise<void> {
    let bodyStr = '';
    let bodyBytes: ArrayBuffer | null = null;

    if (typeof response.body === 'string') {
      const encoder = new util.TextEncoder();
      bodyBytes = encoder.encodeIntoBuffer(response.body).buffer;
    } else {
      bodyBytes = response.body;
    }

    const contentLength = bodyBytes ? bodyBytes.byteLength : 0;

    let headerStr = `HTTP/1.1 ${response.status}\r\n`;
    headerStr += `Content-Length: ${contentLength}\r\n`;
    headerStr += `Connection: close\r\n`;
    
    for (const [key, value] of response.headers) {
      headerStr += `${key}: ${value}\r\n`;
    }
    headerStr += '\r\n';

    const encoder = new util.TextEncoder();
    const headerBytes = encoder.encodeIntoBuffer(headerStr).buffer;
    
    const totalLength = headerBytes.byteLength + contentLength;
    const combined = new ArrayBuffer(totalLength);
    const combinedView = new Uint8Array(combined);
    combinedView.set(new Uint8Array(headerBytes), 0);
    if (bodyBytes) {
      combinedView.set(new Uint8Array(bodyBytes), headerBytes.byteLength);
    }

    await clientSocket.send({ data: combined });
  }

  /**
   * 发送简单响应（兼容方法）
   */
  private async sendResponse(
    clientSocket: socket.TCPSocketConnection,
    status: string,
    contentType: string,
    body: string
  ): Promise<void> {
    const encoder = new util.TextEncoder();
    const bodyBytes = encoder.encodeIntoBuffer(body).buffer;
    
    let headerStr = `HTTP/1.1 ${status}\r\n`;
    headerStr += `Content-Type: ${contentType}\r\n`;
    headerStr += `Content-Length: ${bodyBytes.byteLength}\r\n`;
    headerStr += `Connection: close\r\n`;
    headerStr += '\r\n';

    const headerBytes = encoder.encodeIntoBuffer(headerStr).buffer;
    const totalLength = headerBytes.byteLength + bodyBytes.byteLength;
    const combined = new ArrayBuffer(totalLength);
    const combinedView = new Uint8Array(combined);
    combinedView.set(new Uint8Array(headerBytes), 0);
    combinedView.set(new Uint8Array(bodyBytes), headerBytes.byteLength);

    await clientSocket.send({ data: combined });
  }
}
