import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

const THEME_KEY = 'visu-theme'
const SEASON_KEY = 'visu-season-theme'
const SEASON_MIGRATED_KEY = 'visu-season-migrated-v2'

/** 季节主题：春樱 / 夏蓝 / 秋橙 / 冬青 */
export type SeasonTheme = 'spring' | 'summer' | 'autumn' | 'winter'
/** 用户偏好：auto = 跟随当前月份 */
export type SeasonPreference = SeasonTheme | 'auto'

// 按月份计算当前季节：3-5 春 / 6-8 夏 / 9-11 秋 / 12、1、2 冬
const seasonByMonth = (): SeasonTheme => {
  const month = new Date().getMonth() + 1
  if (month >= 3 && month <= 5) return 'spring'
  if (month >= 6 && month <= 8) return 'summer'
  if (month >= 9 && month <= 11) return 'autumn'
  return 'winter'
}

/**
 * 全局主题状态：
 * 1. 深浅模式：切换时在 html 根元素挂/摘 dark class，配合全局样式与 antd darkAlgorithm 生效
 * 2. 四季主题：挂 season-* class，浅色模式下页面背景/强调色随季节变化（WCAG AA 对比度选色）；
 *    深色模式保持悦目暗黑不变，仅 antd 主色随季节
 */
export const useThemeStore = defineStore('theme', () => {
  // 默认暗色（悦目风格）；仅当用户明确选择过浅色时才用浅色
  const isDark = ref<boolean>(localStorage.getItem(THEME_KEY) !== 'light')

  // 一次性迁移：旧版本默认 auto（跟随月份），现统一迁移为夏季默认；用户此后主动选择的值仍会保留
  if (!localStorage.getItem(SEASON_MIGRATED_KEY)) {
    localStorage.setItem(SEASON_KEY, 'summer')
    localStorage.setItem(SEASON_MIGRATED_KEY, '1')
  }

  // 季节偏好，默认夏季
  const season = ref<SeasonPreference>(
    (localStorage.getItem(SEASON_KEY) as SeasonPreference) || 'summer',
  )

  // 实际生效的季节
  const currentSeason = computed<SeasonTheme>(() =>
    season.value === 'auto' ? seasonByMonth() : season.value,
  )

  const applyTheme = () => {
    const root = document.documentElement
    root.classList.toggle('dark', isDark.value)
    // 季节 class：先清后挂，避免切换时残留
    root.classList.remove('season-spring', 'season-summer', 'season-autumn', 'season-winter')
    root.classList.add(`season-${currentSeason.value}`)
    localStorage.setItem(THEME_KEY, isDark.value ? 'dark' : 'light')
    localStorage.setItem(SEASON_KEY, season.value)
  }

  // 初始化时同步一次（处理刷新后恢复）
  applyTheme()

  // 切换季节偏好（设置页调用）：挂过渡类让颜色平滑渐变，结束后移除避免影响日常渲染性能
  const setSeason = (value: SeasonPreference) => {
    season.value = value
    document.documentElement.classList.add('theme-transitioning')
    applyTheme()
    window.setTimeout(() => {
      document.documentElement.classList.remove('theme-transitioning')
    }, 1000)
  }

  const toggleTheme = () => {
    isDark.value = !isDark.value
    // 切换期间挂上过渡类，让背景/文字/边框颜色平滑渐变，结束后移除避免影响日常渲染性能
    document.documentElement.classList.add('theme-transitioning')
    applyTheme()
    window.setTimeout(() => {
      document.documentElement.classList.remove('theme-transitioning')
    }, 1000)
  }

  return { isDark, season, currentSeason, setSeason, toggleTheme }
})
