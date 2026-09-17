<template>
  <div id="basicLayout">
    <a-layout style="min-height: 100vh">
      <a-layout-header class="header" :class="{ 'header-hidden': headerHidden }">
        <GlobalHeader />
      </a-layout-header>
      <a-layout-content class="content">
        <router-view />
      </a-layout-content>
      <!-- 底部备案号：链接指向工信部备案系统 -->
      <footer class="footer">
        <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">赣ICP备2026022464号-1</a>
      </footer>
    </a-layout>
    <!-- 右下角悬浮按钮组：主题切换 + 返回顶部 -->
    <div class="float-actions">
      <div
        class="float-btn"
        :title="themeStore.isDark ? '切换为浅色模式' : '切换为深色模式'"
        @click="themeStore.toggleTheme()"
      >
        <!-- 月亮：切到深色；太阳：切到浅色（icons-vue 无 moon/sun，使用内联 SVG） -->
        <svg v-if="!themeStore.isDark" viewBox="0 0 24 24" width="17" height="17" fill="currentColor">
          <path d="M21 12.79A9 9 0 1 1 11.21 3a7 7 0 0 0 9.79 9.79z" />
        </svg>
        <svg v-else viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
          <circle cx="12" cy="12" r="4" />
          <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41" />
        </svg>
      </div>
      <div v-if="showBackTop" class="float-btn" title="返回顶部" @click="backToTop">
        <ArrowUpOutlined />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ArrowUpOutlined } from '@ant-design/icons-vue'
import GlobalHeader from '@/components/GlobalHeader.vue'
import { useThemeStore } from '@/stores/useThemeStore.ts'

const themeStore = useThemeStore()

// ----- 下滑隐藏 / 上滑显示导航栏 -----
const headerHidden = ref(false)
let lastScrollY = 0
// 累计滚动超过该阈值才切换，避免轻微抖动误触发
const THRESHOLD = 12

// 返回顶部按钮：滚动超过 300px 出现
const showBackTop = ref(false)

const onScroll = () => {
  const y = window.scrollY
  showBackTop.value = y > 300
  const delta = y - lastScrollY
  // 页面顶部时始终显示
  if (y <= 0) {
    headerHidden.value = false
    lastScrollY = y
    return
  }
  if (delta > THRESHOLD) {
    // 下滑：隐藏
    headerHidden.value = true
    lastScrollY = y
  } else if (delta < -THRESHOLD) {
    // 上滑：显示
    headerHidden.value = false
    lastScrollY = y
  }
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
})

// 平滑返回顶部
const backToTop = () => {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
</script>

<style scoped>
#basicLayout .header {
  padding-inline: 20px;
  background: var(--bg-nav);
  color: unset;
  border-bottom: 1px solid var(--border-color);
  position: sticky;
  top: 0;
  z-index: 100;
  transition:
    transform 0.3s ease,
    box-shadow 0.3s ease;
}

/* 下滑时导航栏上移隐藏 */
#basicLayout .header-hidden {
  transform: translateY(-100%);
  box-shadow: none;
}

#basicLayout .content {
  padding: 24px;
  background: transparent;
  margin-bottom: 28px;
}

#basicLayout .footer {
  background: transparent;
  padding: 16px;
  text-align: center;
  font-size: 12px;
}

#basicLayout .footer a {
  color: var(--text-disabled);
  text-decoration: none;
}

#basicLayout .footer a:hover {
  color: var(--accent);
}

/* 右下角悬浮按钮组 */
.float-actions {
  position: fixed;
  right: 32px;
  bottom: 48px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  z-index: 200;
}

.float-btn {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 17px;
  color: var(--text-secondary);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  box-shadow: var(--card-shadow);
  cursor: pointer;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    background 0.2s ease;
}

.float-btn:hover {
  transform: translateY(-3px);
  box-shadow: var(--card-shadow-hover);
  color: var(--accent);
}
</style>
