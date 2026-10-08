<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

// 点击中央发布按钮
const goPublish = () => {
  router.push('/publish')
}
</script>

<template>
  <div class="tab-layout">
    <router-view v-slot="{ Component }">
      <component :is="Component" />
    </router-view>

    <!-- 底部导航：中央占位留给凸起发布按钮 -->
    <van-tabbar route fixed placeholder safe-area-inset-bottom class="tabbar">
      <van-tabbar-item replace to="/" icon="wap-home-o">首页</van-tabbar-item>
      <van-tabbar-item replace to="/search" icon="search">搜索</van-tabbar-item>
      <van-tabbar-item class="tabbar-placeholder" icon="plus" @click="goPublish"
        >发布</van-tabbar-item
      >
      <van-tabbar-item replace to="/space" icon="apps-o">空间</van-tabbar-item>
      <van-tabbar-item replace to="/mine" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>

    <!-- 中央凸起发布按钮 -->
    <div class="fab-publish" @click="goPublish">
      <van-icon name="plus" />
    </div>
  </div>
</template>

<style scoped>
.tab-layout {
  min-height: 100vh;
}

/* 中央凸起发布按钮：圆角主色，悬浮于 TabBar 之上 */
.fab-publish {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: calc(16px + var(--safe-bottom));
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: linear-gradient(135deg, #40a9ff 0%, #1687e0 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  box-shadow:
    0 4px 12px rgba(64, 169, 255, 0.45),
    0 0 0 4px var(--bg-page);
  z-index: 1001;
  cursor: pointer;
  transition: transform 0.15s ease;
}

.fab-publish:active {
  transform: translateX(-50%) scale(0.92);
}

/* 占位 Tab 不显示默认样式 */
.tabbar-placeholder :deep(.van-tabbar-item__icon),
.tabbar-placeholder :deep(.van-tabbar-item__text) {
  opacity: 0;
}
</style>
