<script setup lang="ts">
import { onActivated, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

defineOptions({ name: 'MinePage' })

const router = useRouter()
const loginUserStore = useLoginUserStore()

const checked = ref(false)

const checkLogin = async () => {
  if (!loginUserStore.loginUser.id) {
    await loginUserStore.fetchLoginUser()
  }
  checked.value = true
}

onMounted(() => {
  checkLogin()
})

onActivated(() => {
  if (checked.value) {
    checkLogin()
  }
})

const goLogin = () => {
  router.push('/user/login?redirect=' + encodeURIComponent('/mine'))
}

const goRegister = () => {
  router.push('/user/register')
}

const goMyPictures = () => {
  router.push('/my/pictures')
}

const goProfile = () => {
  router.push('/user/profile')
}

const goSettings = () => {
  router.push('/user/settings')
}

const goMySpace = () => {
  router.replace('/space')
}
</script>

<template>
  <div class="mine-page">
    <!-- 用户卡片 -->
    <div class="user-card">
      <template v-if="loginUserStore.loginUser.id">
        <van-image
          round
          width="56"
          height="56"
          fit="cover"
          :src="loginUserStore.loginUser.userAvatar"
          @click="goProfile"
        >
          <template #error>
            <div class="avatar-fallback">{{ (loginUserStore.loginUser.userName ?? '用')[0] }}</div>
          </template>
        </van-image>
        <div class="user-info" @click="goProfile">
          <div class="user-name-row">
            <span class="user-name">{{ loginUserStore.loginUser.userName ?? '未命名用户' }}</span>
            <span
              v-if="(loginUserStore.loginUser as any).vipCode"
              class="vip-badge"
              >VIP</span
            >
          </div>
          <div class="user-account">账号：{{ loginUserStore.loginUser.userAccount ?? '-' }}</div>
        </div>
        <van-icon name="arrow" color="#94a3b8" />
      </template>
      <template v-else>
        <div class="unlogin-area">
          <div class="unlogin-text">登录后管理你的图片与空间</div>
          <van-button round type="primary" size="small" @click="goLogin">登录</van-button>
          <van-button round plain size="small" @click="goRegister">注册</van-button>
        </div>
      </template>
    </div>

    <!-- 功能菜单 -->
    <van-cell-group inset class="menu-group">
      <van-cell title="我的发布" icon="photo-o" is-link @click="goMyPictures" />
      <van-cell title="我的空间" icon="apps-o" is-link @click="goMySpace" />
      <van-cell title="个人资料" icon="contact-o" is-link @click="goProfile" />
      <van-cell title="设置" icon="setting-o" is-link @click="goSettings" />
    </van-cell-group>
  </div>
</template>

<style scoped>
.mine-page {
  min-height: 100vh;
  padding: calc(var(--safe-top) + 16px) 0 calc(70px + var(--safe-bottom) + 12px);
  box-sizing: border-box;
}

/* 用户卡片 */
.user-card {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 0 16px;
  padding: 20px 16px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  cursor: pointer;
}

.avatar-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--brand-weak);
  color: var(--brand);
  font-size: 22px;
  font-weight: 600;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-name {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-1);
}

.vip-badge {
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #f7b500, #fa8c16);
}

.user-account {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-2);
}

.unlogin-area {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.unlogin-text {
  flex: 1;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-1);
}

/* 菜单 */
.menu-group {
  margin-top: 16px;
  border-radius: var(--radius-card);
  overflow: hidden;
}
</style>
