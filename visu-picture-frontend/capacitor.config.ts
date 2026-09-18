import type { CapacitorConfig } from '@capacitor/cli'

// Capacitor APP 打包配置（仅影响 Android/iOS 工程，不影响网页部署）
const config: CapacitorConfig = {
  appId: 'com.visupicture.app',
  appName: '视界云图库',
  webDir: 'dist',
}

export default config
