<template>
  <div id="app">
    <a-config-provider :locale="zhCN" :theme="themeConfig">
      <BasicLayout />
    </a-config-provider>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { theme as antdTheme } from 'ant-design-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN';
import { useThemeStore } from '@/stores/useThemeStore.ts'

const themeStore = useThemeStore()

// 全局主题：悦目暗黑默认 + 浅色四季背景渐变；主色固定悦目蓝（不随季节变化）
const themeConfig = computed(() => ({
  algorithm: themeStore.isDark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
  token: {
    colorPrimary: '#1677ff',
    colorInfo: '#1677ff',
    colorLink: '#1677ff',
    borderRadius: 8,
    borderRadiusLG: 12,
    colorBgLayout: 'transparent',
    colorTextHeading: themeStore.isDark ? '#f0f0f0' : '#212121',
    fontFamily:
      "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif",
  },
  components: {
    Menu: {
      itemBorderRadius: 10,
      itemMarginInline: 8,
      itemSelectedBg: 'rgba(22, 119, 255, 0.12)',
      itemHoverBg: 'rgba(22, 119, 255, 0.08)',
    },
    Card: {
      borderRadiusLG: 12,
    },
  },
}))
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
dayjs.locale('zh-cn');

import BasicLayout from '@/layouts/BasicLayout.vue'
// import { healthUsingGet } from '@/api/mainController.ts'
// import {useLoginUserStore} from "@/stores/useLoginUserStore.ts";

// 已经改为在权限校验文件中获取
// const loginUserStore = useLoginUserStore()
// loginUserStore.fetchLoginUser()

// healthUsingGet().then((res) => {
//   console.log(res)
// })
</script>

<style scoped></style>
