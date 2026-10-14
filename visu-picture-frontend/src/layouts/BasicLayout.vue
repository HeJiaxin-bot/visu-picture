<template>
  <div id="basicLayout">
    <!-- 四季氛围粒子层（纯装饰，跟随季节切换：花瓣/光斑/落叶/雪花） -->
    <SeasonEffects />

    <!-- ==================== 移动端布局：紧凑顶栏 + 内容转场 + 底部 Tabbar ==================== -->
    <div v-if="isMobile" class="mobile-shell">
      <header class="mobile-header">
        <router-link to="/" class="m-logo" aria-label="回到首页">visu</router-link>
        <span class="m-brand">视界云图库</span>
        <div class="m-actions">
          <button
            class="m-icon-btn"
            :title="themeStore.isDark ? '切换为浅色模式' : '切换为深色模式'"
            @click="themeStore.toggleTheme()"
          >
            <svg v-if="!themeStore.isDark" viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
              <path d="M21 12.79A9 9 0 1 1 11.21 3a7 7 0 0 0 9.79 9.79z" />
            </svg>
            <svg v-else viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <circle cx="12" cy="12" r="4" />
              <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41" />
            </svg>
          </button>
          <router-link to="/user/center" class="m-avatar-link" aria-label="个人中心">
            <a-avatar :size="28" :src="loginUser.userAvatar">
              {{ loginUser.userName?.[0] ?? '客' }}
            </a-avatar>
          </router-link>
        </div>
      </header>

      <main class="mobile-content">
        <router-view v-slot="{ Component, route }">
          <transition :name="transitionName">
            <keep-alive include="HomePage">
              <component :is="Component" :key="route.path" />
            </keep-alive>
          </transition>
        </router-view>
      </main>

      <!-- 底部 Tabbar：iPhone 安全区适配 -->
      <van-tabbar route class="mobile-tabbar" safe-area-inset-bottom>
        <van-tabbar-item replace to="/" icon="home-o">首页</van-tabbar-item>
        <van-tabbar-item replace to="/add_picture" icon="plus">发布</van-tabbar-item>
        <van-tabbar-item replace to="/user/notifications" icon="chat-o" :badge="unreadBadge > 0 ? unreadBadge : ''">
          消息
        </van-tabbar-item>
        <van-tabbar-item replace to="/user/center" icon="user-o">我的</van-tabbar-item>
      </van-tabbar>
    </div>

    <!-- ==================== 桌面布局（保持现状） ==================== -->
    <a-layout v-else style="min-height: 100vh; position: relative; z-index: 1">
      <a-layout-header class="header" :class="{ 'header-hidden': headerHidden }">
        <GlobalHeader />
      </a-layout-header>
      <a-layout-content class="content">
        <router-view />
      </a-layout-content>
      <!-- 底部：技术栈入口 + 备案号 -->
      <footer class="footer">
        <router-link class="footer-link" to="/tech-stack">技术栈全景图</router-link>
        <span class="footer-sep">·</span>
        <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">赣ICP备2026022464号-1</a>
      </footer>
    </a-layout>

    <!-- 右下角悬浮按钮组：主题切换 + 返回顶部（仅桌面） -->
    <div v-if="!isMobile" class="float-actions">
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
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { ArrowUpOutlined } from '@ant-design/icons-vue'
import { Tabbar as VanTabbar, TabbarItem as VanTabbarItem } from 'vant'
import { useRoute } from 'vue-router'
import GlobalHeader from '@/components/GlobalHeader.vue'
import SeasonEffects from '@/components/SeasonEffects.vue'
import { useThemeStore } from '@/stores/useThemeStore.ts'
import { useIsMobile } from '@/composables/useIsMobile.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { countMyUnread } from '@/api/notification.ts'

const themeStore = useThemeStore()
const route = useRoute()
const loginUserStore = useLoginUserStore()
const { isMobile } = useIsMobile()

const loginUser = computed(() => loginUserStore.loginUser)

// ----- 移动端：未读消息角标（进入消息页或发布后刷新） -----
const unreadBadge = ref(0)
const refreshBadge = async () => {
  try {
    const res = await countMyUnread()
    if (res.code === 0 && res.data != null) {
      unreadBadge.value = res.data
    }
  } catch {
    // 静默：角标失败不影响使用
  }
}
watch(
  () => loginUser.value.id,
  (id) => {
    if (id) refreshBadge()
  },
  { immediate: true },
)
watch(
  () => route.path,
  (path) => {
    // 从消息页离开时刷新角标（可能已全部已读/删除）
    if (isMobile.value && path !== '/user/notifications') {
      refreshBadge()
    }
  },
)

// ----- 移动端转场：进入详情页从底部滑入，返回时滑出（仅移动端） -----
const transitionName = computed(() => {
  if (!isMobile.value) return ''
  if (route.path.startsWith('/picture/')) return 'slide-up'
  if (route.meta?.fromDetail) return 'slide-down'
  return ''
})
watch(
  () => route.path,
  (_newPath, oldPath) => {
    // 记录来源标记：从详情页返回时给反向动画
    if (oldPath?.startsWith('/picture/')) {
      route.meta.fromDetail = true
    } else {
      delete route.meta.fromDetail
    }
  },
)

// ----- 下滑隐藏 / 上滑显示导航栏（桌面） -----
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

#basicLayout .footer a,
#basicLayout .footer .footer-link {
  color: var(--text-disabled);
  text-decoration: none;
}

#basicLayout .footer a:hover,
#basicLayout .footer .footer-link:hover {
  color: var(--accent);
}

#basicLayout .footer .footer-sep {
  margin: 0 8px;
  color: var(--text-disabled);
  opacity: 0.6;
}

/* 右下角悬浮按钮组（桌面） */
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

/* ===== 移动端外壳 ===== */
.mobile-shell {
  min-height: 100vh;
  position: relative;
  z-index: 1;
}

.mobile-header {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  padding-top: calc(10px + var(--safe-top));
  background: var(--bg-nav);
  border-bottom: 1px solid var(--border-color);
}

.m-logo {
  font-size: 22px;
  font-weight: 800;
  color: var(--accent);
  text-decoration: none;
  letter-spacing: 0.5px;
  font-style: italic;
}

.m-brand {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary-light);
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

html.dark .m-brand {
  color: var(--text-primary);
}

.m-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.m-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
}

.m-avatar-link {
  display: inline-flex;
}

.mobile-content {
  padding: 12px 12px calc(58px + var(--safe-bottom));
  min-height: calc(100vh - 54px);
}

/* 底部 Tabbar：令牌化适配明暗主题；强制固定（防外部规则干扰定位） */
.mobile-tabbar {
  --van-tabbar-item-active-color: #40a9ff;
  --van-tabbar-item-text-color: var(--text-secondary);
  --van-tabbar-background: var(--bg-card);
  --van-tabbar-item-text-active-color: #40a9ff;
  position: fixed !important;
  bottom: 0;
  left: 0;
}

/* ===== 移动端路由转场：详情页底部滑入 / 滑出 ===== */
.slide-up-enter-active,
.slide-up-leave-active {
  transition:
    transform 0.32s cubic-bezier(0.22, 0.61, 0.36, 1),
    opacity 0.32s ease;
}

.slide-up-enter-from {
  transform: translateY(100%);
  opacity: 0.6;
}

.slide-up-leave-to {
  transform: translateY(100%);
  opacity: 0.6;
}
</style>
