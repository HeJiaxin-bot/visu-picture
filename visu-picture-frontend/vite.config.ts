import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import Components from 'unplugin-vue-components/vite'
import { AntDesignVueResolver } from 'unplugin-vue-components/resolvers'
import compression from 'vite-plugin-compression2'

// https://vite.dev/config/
export default defineConfig({
  // server: {
  //   proxy: {
  //     '/api': 'http://localhost:8123'
  //   }
  // },
  plugins: [
    vue(),
    vueDevTools(),
    // ant-design-vue 按需引入（组件 + 图标）。antd v4 使用 CSS-in-JS，
    // 运行时由 ConfigProvider 自动注入样式，故无需 importStyle
    Components({
      resolvers: [
        AntDesignVueResolver({
          importStyle: false,
          resolveIcons: true,
        }),
      ],
      dts: false,
    }),
    // 构建时预生成 Gzip 与 Brotli 压缩产物，部署方开启对应压缩即可大幅减小传输体积
    compression({
      algorithm: 'gzip',
      threshold: 1024,
      // 保留原文件，让服务器在支持 gzip 时返回 .gz，否则用原文件（更稳妥）
      deleteOriginFile: false,
    }),
    compression({
      algorithm: 'brotliCompress',
      threshold: 1024,
      deleteOriginFile: false,
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    // 代码分层分包，避免把所有第三方库打成一个巨型 bundle
    rollupOptions: {
      output: {
        manualChunks: {
          // 体积较大的可独立缓存的第三方库
          echarts: ['echarts', 'echarts-wordcloud', 'vue-echarts'],
          antd: ['ant-design-vue'],
        },
      },
    },
  },
})