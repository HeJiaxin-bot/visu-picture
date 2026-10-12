export default class PictureEditWebSocket {
  /**
   * 图片 id：保持字符串形态
   *
   * 雪花 id（如 2100942816594628609）超出 JS 安全整数范围，转 Number 会末位失真，
   * 导致 WebSocket 握手参数与服务端记录对不上。后端把 Long 序列化为 String
   * 正是为此，这里沿用同样约定。
   */
  private pictureId: string | number
  private socket: WebSocket | null
  private eventHandlers: any

  constructor(pictureId: string | number) {
    this.pictureId = pictureId // 当前编辑的图片 ID
    this.socket = null // WebSocket 实例
    this.eventHandlers = {} // 自定义事件处理器
  }

  /**
   * 初始化 WebSocket 连接
   */
  connect() {
    const DEV_BASE_URL = "ws://localhost:8123";
    // 线上走同源 ws 协议（http→ws / https→wss），由 Nginx 转发到后端
    const PROD_BASE_URL = `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}`;
    // APP 打包（Capacitor）时由 .env.app 注入 http(s) API 地址，http→ws / https→wss 推导
    const APP_BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined) || '';
    const base =
      APP_BASE_URL ||
      (import.meta.env.DEV ? DEV_BASE_URL : PROD_BASE_URL);
    const url = `${base}/api/ws/picture/edit?pictureId=${this.pictureId}`
    this.socket = new WebSocket(url)

    // 设置携带 cookie
    this.socket.binaryType = 'blob'

    // 监听连接成功事件
    this.socket.onopen = () => {
      console.log('WebSocket 连接已建立')
      this.triggerEvent('open')
    }

    // 监听消息事件
    this.socket.onmessage = (event) => {
      const message = JSON.parse(event.data)
      console.log('收到消息:', message)

      // 根据消息类型触发对应事件
      const type = message.type
      this.triggerEvent(type, message)
    }

    // 监听连接关闭事件
    this.socket.onclose = (event) => {
      console.log('WebSocket 连接已关闭:', event)
      this.triggerEvent('close', event)
    }

    // 监听错误事件
    this.socket.onerror = (error) => {
      console.error('WebSocket 发生错误:', error)
      this.triggerEvent('error', error)
    }
  }

  /**
   * 关闭 WebSocket 连接
   */
  disconnect() {
    if (this.socket) {
      this.socket.close()
      console.log('WebSocket 连接已手动关闭')
    }
  }

  /**
   * 发送消息到后端
   * @param {Object} message 消息对象
   */
  sendMessage(message: object) {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(message))
      console.log('消息已发送:', message)
    } else {
      console.error('WebSocket 未连接，无法发送消息:', message)
    }
  }

  /**
   * 添加自定义事件监听
   * @param {string} type 消息类型
   * @param {Function} handler 消息处理函数
   */
  on(type: string, handler: (data?: any) => void) {
    if (!this.eventHandlers[type]) {
      this.eventHandlers[type] = []
    }
    this.eventHandlers[type].push(handler)
  }

  /**
   * 触发事件
   * @param {string} type 消息类型
   * @param {Object} data 消息数据
   */
  triggerEvent(type: string, data?: any) {
    const handlers = this.eventHandlers[type]
    if (handlers) {
      handlers.forEach((handler: any) => handler(data))
    }
  }
}
