<script setup lang="ts">
import { useRouter } from 'vue-router'
import { userLogoutUsingPost } from '@/api/userController.ts'
import { showToast, showConfirmDialog } from 'vant'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

defineOptions({ name: 'UserSettingsPage' })

const router = useRouter()
const loginUserStore = useLoginUserStore()

// 退出登录
const doLogout = async () => {
  try {
    await showConfirmDialog({ title: '退出登录', message: '确定要退出当前账号吗？' })
  } catch (e) {
    return
  }
  try {
    await userLogoutUsingPost()
  } catch (e) {
    // 即使请求失败也清理本地登录态
  }
  loginUserStore.setLoginUser({ userName: '未登录' })
  showToast({ type: 'success', message: '已退出登录' })
  router.replace('/user/login')
}

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.replace('/mine')
  }
}
</script>

<template>
  <div class="settings-page">
    <van-nav-bar title="设置" left-arrow @click-left="goBack" />

    <van-cell-group inset class="settings-group">
      <van-cell title="个人资料" icon="contact-o" is-link @click="router.push('/user/profile')" />
      <van-cell title="我的发布" icon="photo-o" is-link @click="router.push('/my/pictures')" />
    </van-cell-group>

    <div class="logout-area">
      <van-button block round type="danger" plain @click="doLogout">退出登录</van-button>
    </div>

    <div class="version-text">视界云图库 v1.0.0</div>
  </div>
</template>

<style scoped>
.settings-page {
  min-height: 100vh;
  background: var(--bg-page);
  padding-bottom: calc(32px + var(--safe-bottom));
}

.settings-page :deep(.van-nav-bar) {
  padding-top: var(--safe-top);
}

.settings-group {
  margin-top: 12px;
  border-radius: var(--radius-card);
  overflow: hidden;
}

.logout-area {
  margin-top: 32px;
  padding: 0 16px;
}

.version-text {
  margin-top: 40px;
  text-align: center;
  font-size: 12px;
  color: var(--text-3);
}
</style>
