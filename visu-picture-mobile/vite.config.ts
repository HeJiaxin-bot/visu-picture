import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { VantResolver } from 'unplugin-vue-components/resolvers'

// https://vite.dev/config/
export default defineConfig(({ mode }) => ({
  plugins: [
    vue(),
    // Vant 按需引入（组件 + 样式自动导入）
    Components({
      resolvers: [VantResolver()],
      dts: false,
    }),
    // APP 打包模式（--mode app）不生成 .gz/.br：会被同步进 APK 导致资源合并冲突（Duplicate resources）
    // （移动端构建本身不做预压缩，保持与 PC 端 app 模式一致的行为）
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8123',
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          vant: ['vant'],
        },
      },
    },
  },
}))
