import { ref, onMounted, onUnmounted } from 'vue'

/**
 * 移动端检测：窄屏（≤768px）走移动端外壳（底部 Tabbar / 转场动画），
 * 桌面端保持现有布局不变。matchMedia 响应式，无需手动监听 resize。
 */
const isMobile = ref(false)
let mediaQuery: MediaQueryList | null = null
let bound = false

const update = () => {
  isMobile.value = mediaQuery?.matches ?? false
}

export function useIsMobile() {
  onMounted(() => {
    if (!mediaQuery) {
      mediaQuery = window.matchMedia('(max-width: 768px)')
      mediaQuery.addEventListener('change', update)
      bound = true
    }
    update()
  })
  // 立即求值一次（SSR 无关，纯浏览器项目）
  if (!bound) {
    mediaQuery = window.matchMedia('(max-width: 768px)')
    mediaQuery.addEventListener('change', update)
    bound = true
    update()
  }
  onUnmounted(() => {
    // 全局共享的 matcher 不随组件卸载移除，避免其他使用者失联
  })
  return { isMobile }
}
