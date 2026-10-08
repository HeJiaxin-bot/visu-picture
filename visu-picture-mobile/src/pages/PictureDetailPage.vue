<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { deletePictureUsingPost, getPictureVoByIdUsingGet } from '@/api/pictureController.ts'
import { showToast, showConfirmDialog, showImagePreview } from 'vant'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { useLikeStore } from '@/stores/useLikeStore.ts'
import { SPACE_PERMISSION_ENUM } from '@/constants/space.ts'
import { PIC_REVIEW_STATUS_MAP } from '@/constants/picture.ts'

defineOptions({ name: 'PictureDetailPage' })

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const likeStore = useLikeStore()

const picture = ref<API.PictureVO>({})
const loading = ref(true)

// 权限判定（基于后端下发的 permissionList）
const canEdit = computed(() =>
  (picture.value.permissionList ?? []).includes(SPACE_PERMISSION_ENUM.PICTURE_EDIT),
)
const canDelete = computed(() =>
  (picture.value.permissionList ?? []).includes(SPACE_PERMISSION_ENUM.PICTURE_DELETE),
)

const fetchDetail = async () => {
  loading.value = true
  const id = route.params.id as string
  try {
    const res = await getPictureVoByIdUsingGet({ id } as any)
    if (res.data.code === 0 && res.data.data) {
      picture.value = res.data.data
      document.title = `${picture.value.name ?? '图片详情'} - 视界云图库`
    } else {
      showToast('获取图片详情失败，' + res.data.message)
    }
  } catch (e) {
    showToast('获取图片详情失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchDetail()
})

// ----- 操作 -----

// 全屏预览（双指缩放）
const doPreview = () => {
  if (picture.value.url) {
    showImagePreview({ images: [picture.value.url], closeable: true })
  }
}

// 查看作者主页
const goUserPage = () => {
  const userId = picture.value.user?.id ?? picture.value.userId
  if (userId != null) {
    router.push(`/user/${userId}`)
  }
}

// 点赞
const doLike = () => {
  likeStore.toggle(picture.value)
}

// 下载原图：优先 blob 下载（重命名），跨域受限时降级新窗口打开
const downloading = ref(false)
const doDownload = async () => {
  const url = picture.value.url
  if (!url) return
  if (downloading.value) return
  downloading.value = true
  try {
    const resp = await fetch(url)
    const blob = await resp.blob()
    const objectUrl = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = objectUrl
    a.download = picture.value.name ?? 'picture'
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(objectUrl)
  } catch (e) {
    // 跨域等场景降级：新窗口打开由用户长按保存
    window.open(url, '_blank')
  } finally {
    downloading.value = false
  }
}

// 编辑
const doEdit = () => {
  router.push({
    path: '/add_picture',
    query: { id: picture.value.id, spaceId: picture.value.spaceId },
  })
}

// 删除
const doDelete = async () => {
  try {
    await showConfirmDialog({
      title: '删除图片',
      message: '删除后不可恢复，确定删除吗？',
    })
  } catch (e) {
    return
  }
  const id = picture.value.id
  if (!id) return
  const res = await deletePictureUsingPost({ id })
  if (res.data.code === 0) {
    showToast({ type: 'success', message: '删除成功' })
    router.back()
  } else {
    showToast('删除失败，' + res.data.message)
  }
}

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.replace('/')
  }
}

// 元信息格式化
const formatSize = (size?: number) => {
  if (!size) return '-'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(2)} MB`
}
</script>

<template>
  <div class="detail-page">
    <!-- 顶部导航：返回 -->
    <van-nav-bar left-arrow @click-left="goBack" class="detail-nav" />

    <van-loading v-if="loading" class="detail-loading" size="24" vertical>加载中...</van-loading>

    <template v-else>
      <!-- 大图：点击全屏预览 -->
      <div class="img-stage" @click="doPreview">
        <img :src="picture.url ?? picture.thumbnailUrl" :alt="picture.name" />
      </div>

      <!-- 信息区 -->
      <div class="info-area">
        <h2 class="pic-title">{{ picture.name ?? '未命名图片' }}</h2>
        <div
          v-if="picture.reviewStatus != null && picture.reviewStatus !== 1"
          class="review-tip"
        >
          {{ PIC_REVIEW_STATUS_MAP[picture.reviewStatus] }}
        </div>
        <p v-if="picture.introduction" class="pic-intro">{{ picture.introduction }}</p>

        <!-- 分类 / 标签 -->
        <div class="pic-tags">
          <span v-if="picture.category" class="tag-chip category">{{ picture.category }}</span>
          <span v-for="tag in picture.tags" :key="tag" class="tag-chip">{{ tag }}</span>
        </div>

        <!-- 元信息 -->
        <div class="meta-grid">
          <div class="meta-item">
            <span class="meta-label">尺寸</span>
            <span>{{ picture.picWidth ?? '-' }} × {{ picture.picHeight ?? '-' }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">大小</span>
            <span>{{ formatSize(picture.picSize) }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">格式</span>
            <span>{{ picture.picFormat ?? '-' }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">上传时间</span>
            <span>{{ picture.createTime?.slice(0, 10) ?? '-' }}</span>
          </div>
        </div>

        <!-- 作者卡片 -->
        <div class="author-card" @click="goUserPage">
          <van-image
            round
            width="40"
            height="40"
            fit="cover"
            :src="picture.user?.userAvatar"
          />
          <div class="author-info">
            <div class="author-name">{{ picture.user?.userName ?? '未知用户' }}</div>
            <div class="author-desc ellipsis">{{ picture.user?.userProfile ?? '查看TA的主页' }}</div>
          </div>
          <van-icon name="arrow" color="#c9cdd4" />
        </div>
      </div>

      <!-- 底部操作栏 -->
      <div class="action-bar">
        <div class="action-item" @click="doLike">
          <van-icon
            :name="likeStore.isLiked(picture.id) ? 'good-job' : 'good-job-o'"
            :class="{ liked: likeStore.isLiked(picture.id) }"
          />
          <span>{{ picture.likeCount ?? 0 }}</span>
        </div>
        <van-button
          class="download-btn"
          type="primary"
          round
          size="small"
          :loading="downloading"
          @click="doDownload"
        >
          免费下载
        </van-button>
        <van-button v-if="canEdit" round size="small" @click="doEdit">编辑</van-button>
        <van-button v-if="canDelete" round size="small" type="danger" plain @click="doDelete">
          删除
        </van-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.detail-page {
  min-height: 100vh;
  background: var(--bg-page);
  padding-bottom: calc(72px + var(--safe-bottom));
}

.detail-nav {
  position: sticky;
  top: 0;
  padding-top: var(--safe-top);
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  z-index: 10;
}

.detail-loading {
  padding-top: 40vh;
  justify-content: center;
}

/* 大图区 */
.img-stage {
  background: #000;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 40vh;
  cursor: zoom-in;
}

.img-stage img {
  width: 100%;
  height: auto;
  max-height: 60vh;
  object-fit: contain;
}

/* 信息区 */
.info-area {
  padding: 16px;
}

.pic-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-1);
}

.review-tip {
  display: inline-block;
  margin-top: 8px;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 12px;
  color: #fff;
  background: var(--warning);
}

.pic-intro {
  margin: 10px 0 0;
  font-size: 14px;
  color: var(--text-2);
  line-height: 1.6;
}

.pic-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.tag-chip {
  padding: 3px 10px;
  border-radius: var(--radius-chip);
  font-size: 12px;
  color: var(--brand);
  background: var(--brand-weak);
}

.tag-chip.category {
  color: var(--success);
  background: #e8ffea;
}

/* 元信息 */
.meta-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 16px;
  margin-top: 16px;
  padding: 14px 16px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 13px;
  color: var(--text-1);
}

.meta-label {
  font-size: 11px;
  color: var(--text-3);
}

/* 作者卡片 */
.author-card {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  padding: 14px 16px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  cursor: pointer;
}

.author-info {
  flex: 1;
  min-width: 0;
}

.author-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-1);
}

.author-desc {
  font-size: 12px;
  color: var(--text-2);
  margin-top: 2px;
}

/* 底部操作栏 */
.action-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px calc(10px + var(--safe-bottom));
  background: var(--bg-card);
  box-shadow: 0 -1px 4px rgba(0, 0, 0, 0.06);
  z-index: 100;
}

.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  font-size: 11px;
  color: var(--text-2);
  min-width: 44px;
  cursor: pointer;
}

.action-item .van-icon {
  font-size: 22px;
}

.action-item .van-icon.liked {
  color: #ff4d6a;
}

.download-btn {
  flex: 1;
  margin-left: 8px;
}

/* :active 按压反馈 */
.action-item:active {
  transform: scale(0.9);
}
</style>
