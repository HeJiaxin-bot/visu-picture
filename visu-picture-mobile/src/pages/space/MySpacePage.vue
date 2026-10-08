<script setup lang="ts">
import { onMounted, onActivated, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listSpaceVoByPageUsingPost } from '@/api/spaceController.ts'
import { showToast } from 'vant'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { SPACE_TYPE_MAP } from '@/constants/space.ts'

defineOptions({ name: 'MySpacePage' })

const router = useRouter()
const loginUserStore = useLoginUserStore()

const spaceList = ref<API.SpaceVO[]>([])
const loading = ref(true)
const checked = ref(false) // 是否已检查登录态

const loadSpaces = async () => {
  loading.value = true
  try {
    const res = await listSpaceVoByPageUsingPost({ current: 1, pageSize: 50 })
    if (res.data.code === 0 && res.data.data) {
      spaceList.value = res.data.data.records ?? []
    } else {
      showToast('获取空间列表失败，' + res.data.message)
    }
  } catch (e) {
    showToast('获取空间列表失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const checkAndLoad = async () => {
  // 登录态尚未拉到时等一拍
  if (!loginUserStore.loginUser.id) {
    await loginUserStore.fetchLoginUser()
  }
  checked.value = true
  if (loginUserStore.loginUser.id) {
    loadSpaces()
  } else {
    loading.value = false
  }
}

onMounted(() => {
  checkAndLoad()
})

// Tab 页 keep-alive：重新进入时刷新登录态与列表
onActivated(() => {
  if (checked.value) {
    checkAndLoad()
  }
})

// 容量百分比
const percent = (space: API.SpaceVO) => {
  const total = Number(space.totalSize ?? 0)
  const max = Number(space.maxSize ?? 0)
  if (max <= 0) return 0
  return Math.min(Math.round((total / max) * 100), 100)
}

// 容量文本
const sizeText = (size?: string | number) => {
  const n = Number(size ?? 0)
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} MB`
  return `${(n / 1024 / 1024 / 1024).toFixed(2)} GB`
}

const goDetail = (space: API.SpaceVO) => {
  router.push(`/space/${space.id}`)
}

const goAddSpace = () => {
  router.push('/space/add')
}

const goLogin = () => {
  router.push('/user/login?redirect=' + encodeURIComponent('/space'))
}

const goPublish = () => {
  router.push('/publish')
}
</script>

<template>
  <div class="space-page">
    <!-- 未登录 -->
    <van-empty v-if="checked && !loginUserStore.loginUser.id" description="登录后查看你的空间">
      <van-button round type="primary" @click="goLogin">去登录</van-button>
    </van-empty>

    <template v-else>
      <!-- 加载中 -->
      <div v-if="loading" class="loading-area">
        <van-loading size="24" vertical>加载中...</van-loading>
      </div>

      <template v-else>
        <div class="page-header">
          <span class="page-title">我的空间</span>
          <van-icon name="plus" class="add-icon" @click="goAddSpace" />
        </div>

        <!-- 空间卡片列表 -->
        <div v-if="spaceList.length > 0" class="space-list">
          <div v-for="space in spaceList" :key="space.id" class="space-card" @click="goDetail(space)">
            <div class="space-top">
              <div class="space-name ellipsis">
                {{ space.spaceName }}
              </div>
              <span class="space-type">{{
                SPACE_TYPE_MAP[space.spaceType ?? 0] ?? '私有空间'
              }}</span>
            </div>
            <div class="space-usage">
              <van-progress :percentage="percent(space)" stroke-width="6" :show-pivot="false" />
              <div class="usage-text">
                <span>{{ space.totalCount ?? 0 }} 张图片</span>
                <span>{{ sizeText(space.totalSize) }} / {{ sizeText(space.maxSize) }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 空状态 -->
        <van-empty v-else description="还没有空间，创建一个开始管理图片吧">
          <van-button round type="primary" @click="goAddSpace">创建空间</van-button>
        </van-empty>

        <!-- 底部提示 -->
        <div v-if="spaceList.length > 0" class="tips">
          <van-button plain round size="small" @click="goPublish">去发布图片</van-button>
        </div>
      </template>
    </template>
  </div>
</template>

<style scoped>
.space-page {
  min-height: 100vh;
  padding: calc(var(--safe-top) + 16px) 16px calc(70px + var(--safe-bottom) + 12px);
  box-sizing: border-box;
}

.loading-area {
  padding-top: 30vh;
  display: flex;
  justify-content: center;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.page-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-1);
}

.add-icon {
  font-size: 20px;
  color: var(--text-1);
  padding: 6px;
  cursor: pointer;
}

.space-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.space-card {
  padding: 16px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  cursor: pointer;
  transition: transform 0.15s ease;
}

.space-card:active {
  transform: scale(0.98);
}

.space-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.space-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-1);
  flex: 1;
}

.space-type {
  flex-shrink: 0;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  color: var(--brand);
  background: var(--brand-weak);
}

.space-usage {
  margin-top: 12px;
}

.usage-text {
  display: flex;
  justify-content: space-between;
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-2);
}

.tips {
  margin-top: 24px;
  text-align: center;
}
</style>
