import { defineStore } from 'pinia'
import { ref } from 'vue'

const THEME_KEY = 'visu-theme'

/**
 * 全局主题（浅色/深色）状态
 * 切换时在 html 根元素上挂/摘 dark class，配合全局样式与 antd darkAlgorithm 生效
 */
export const useThemeStore = defineStore('theme', () => {
  // 默认暗色（悦目风格）；仅当用户明确选择过浅色时才用浅色
  const isDark = ref<boolean>(localStorage.getItem(THEME_KEY) !== 'light')

  const applyTheme = () => {
    document.documentElement.classList.toggle('dark', isDark.value)
    localStorage.setItem(THEME_KEY, isDark.value ? 'dark' : 'light')
  }

  // 初始化时同步一次（处理刷新后恢复）
  applyTheme()

  const toggleTheme = () => {
    isDark.value = !isDark.value
    // 切换期间挂上过渡类，让背景/文字/边框颜色平滑渐变，结束后移除避免影响日常渲染性能
    document.documentElement.classList.add('theme-transitioning')
    applyTheme()
    window.setTimeout(() => {
      document.documentElement.classList.remove('theme-transitioning')
    }, 1000)
  }

  return { isDark, toggleTheme }
})
