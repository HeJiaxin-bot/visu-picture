import { computed, type ComputedRef } from 'vue'
import { useThemeStore } from '@/stores/useThemeStore.ts'

/** 分析页图表统一色板：蓝 / 青 / 紫 / 金 / 红 / 绿 / 青蓝 / 粉 */
export const CHART_PALETTE = [
  '#3b82f6',
  '#14b8a6',
  '#8b5cf6',
  '#f59e0b',
  '#f43f5e',
  '#22c55e',
  '#06b6d4',
  '#f472b6',
]

export interface ChartTheme {
  dark: boolean
  textColor: string
  axisLineColor: string
  splitLineColor: string
  cardBg: string
  tooltip: Record<string, unknown>
}

/**
 * 响应式图表主题：跟随全局深浅模式切换。
 * 在各图表 option 的 computed 中引用 chartTheme.value，即可让图表随主题自动重算。
 */
export function useChartTheme(): ComputedRef<ChartTheme> {
  const themeStore = useThemeStore()
  return computed<ChartTheme>(() => {
    const dark = themeStore.isDark
    return {
      dark,
      textColor: dark ? '#a6adbb' : '#6b7280',
      axisLineColor: dark ? 'rgba(255,255,255,0.14)' : 'rgba(15,35,80,0.12)',
      splitLineColor: dark ? 'rgba(255,255,255,0.06)' : 'rgba(15,35,80,0.06)',
      cardBg: dark ? '#2d2d2d' : '#ffffff',
      tooltip: {
        backgroundColor: dark ? '#26262a' : '#ffffff',
        borderWidth: 0,
        textStyle: { color: dark ? '#f0f0f0' : '#212121', fontSize: 12 },
        extraCssText:
          'box-shadow: 0 8px 28px rgba(15,35,80,0.16); border-radius: 10px; padding: 10px 14px;',
      },
    }
  })
}
