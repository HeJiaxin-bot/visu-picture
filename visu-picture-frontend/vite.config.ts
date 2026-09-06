import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import Components from 'unplugin-vue-components/vite'
import { AntDesignVueResolver } from 'unplugin-vue-components/resolvers'

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