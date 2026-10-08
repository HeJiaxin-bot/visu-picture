<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listSpaceVoByPageUsingPost } from '@/api/spaceController.ts'
import { showToast } from 'vant'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

defineOptions({ name: 'PublishPage' })

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

// 已选目标空间（公共图库为空）
const spaceId = ref<string>()
const spaceName = ref('公共图库')

// 我的空间列表
const spaceList = ref<API.SpaceVO[]>([])
const showSpacePicker = ref(false)

const loadSpaces = async () => {
  try {
    const res = await listSpaceVoByPageUsingPost({ current: 1, pageSize: 50 })
    if (res.data.code === 0 && res.data.data) {
      spaceList.value = res.data.data.records ?? []
    }
  } catch (e) {
    // 静默失败：空间列表加载失败不影响发布入口
  }
}

onMounted(() => {
  // 未登录先跳登录
  if (!loginUserStore.loginUser.id) {
    showToast('请先登录')
    router.replace('/user/login?redirect=' + encodeURIComponent('/publish'))
    return
  }
  // 支持 /publish?spaceId=xxx 直达指定空间
  const querySpaceId = route.query.spaceId as string | undefined
  if (querySpaceId) {
    spaceId.value = querySpaceId
  }
  loadSpaces().then(() => {
    if (querySpaceId) {
      const space = spaceList.value.find((s) => String(s.id) === String(querySpaceId))
      if (space) {
        spaceName.value = space.spaceName ?? '空间'
      }
    }
  })
})

// 选择空间
const doPickSpace = (space?: API.SpaceVO) => {
  if (space) {
    spaceId.value = String(space.id)
    spaceName.value = space.spaceName ?? '空间'
  } else {
    spaceId.value = undefined
    spaceName.value = '公共图库'
  }
  showSpacePicker.value = false
}

// 三个发布入口
const goAddPicture = () => {
  router.push({ path: '/add_picture', query: spaceId.value ? { spaceId: spaceId.value } : {} })
}
const goAddUrl = () => {
  router.push({ path: '/add_picture/url', query: spaceId.value ? { spaceId: spaceId.value } : {} })
}
const goAddBatch = () => {
  router.push({
    path: '/add_picture/batch',
    query: spaceId.value ? { spaceId: spaceId.value } : {},
  })
}

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.replace('/')
  }
}
</script>

<template>
  <div class="publish-page">
    <van-nav-bar title="发布图片" left-arrow @click-left="goBack" />

    <!-- 目标空间 -->
    <div class="space-bar" @click="showSpacePicker = true">
      <span class="space-label">发布到</span>
      <span class="space-value">{{ spaceName }}</span>
      <van-icon name="arrow-down" color="#64748b" />
    </div>

    <!-- 发布入口 -->
    <div class="entry-list">
      <div class="entry-card" @click="goAddPicture">
        <div class="entry-icon">
          <van-icon name="photograph" />
        </div>
        <div class="entry-text">
          <div class="entry-title">上传图片</div>
          <div class="entry-desc">拍摄或从相册选择，支持 AI 智能配文</div>
        </div>
        <van-icon name="arrow" color="#94a3b8" />
      </div>

      <div class="entry-card" @click="goAddUrl">
        <div class="entry-icon">
          <van-icon name="link-o" />
        </div>
        <div class="entry-text">
          <div class="entry-title">URL 导入</div>
          <div class="entry-desc">粘贴图片链接，一键抓取到图库</div>
        </div>
        <van-icon name="arrow" color="#94a3b8" />
      </div>

      <div class="entry-card" @click="goAddBatch">
        <div class="entry-icon">
          <van-icon name="description-o" />
        </div>
        <div class="entry-text">
          <div class="entry-title">批量上传</div>
          <div class="entry-desc">一次选择多张图片，并发快速上传</div>
        </div>
        <van-icon name="arrow" color="#94a3b8" />
      </div>
    </div>

    <!-- 空间选择弹层 -->
    <van-popup v-model:show="showSpacePicker" position="bottom" round>
      <div class="picker-header">选择发布目标</div>
      <div class="picker-list">
        <div class="picker-item" :class="{ active: !spaceId }" @click="doPickSpace()">
          <van-icon name="public" />
          <span>公共图库</span>
          <van-icon v-if="!spaceId" name="success" color="#2563eb" />
        </div>
        <div
          v-for="space in spaceList"
          :key="space.id"
          class="picker-item"
          :class="{ active: String(space.id) === spaceId }"
          @click="doPickSpace(space)"
        >
          <van-icon name="apps-o" />
          <span class="ellipsis">{{ space.spaceName }}</span>
          <van-icon v-if="String(space.id) === spaceId" name="success" color="#2563eb" />
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.publish-page {
  min-height: 100vh;
  background: var(--bg-page);
  padding-bottom: calc(24px + var(--safe-bottom));
}

.publish-page :deep(.van-nav-bar) {
  padding-top: var(--safe-top);
}

/* 目标空间条 */
.space-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 12px 16px 0;
  padding: 12px 16px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  cursor: pointer;
}

.space-label {
  font-size: 14px;
  color: var(--text-2);
}

.space-value {
  flex: 1;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-1);
}

/* 入口列表 */
.entry-list {
  margin: 16px 16px 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.entry-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 16px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  cursor: pointer;
  transition: transform 0.15s ease;
}

.entry-card:active {
  transform: scale(0.98);
}

.entry-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: var(--brand-weak);
  color: var(--brand);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  flex-shrink: 0;
}

.entry-text {
  flex: 1;
  min-width: 0;
}

.entry-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-1);
}

.entry-desc {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-2);
}

/* 空间选择弹层 */
.picker-header {
  padding: 16px;
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-1);
}

.picker-list {
  padding: 0 16px calc(20px + var(--safe-bottom));
  max-height: 50vh;
  overflow-y: auto;
}

.picker-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 8px;
  font-size: 15px;
  color: var(--text-1);
  border-bottom: 1px solid var(--bg-page);
  cursor: pointer;
}

.picker-item span {
  flex: 1;
}
</style>
