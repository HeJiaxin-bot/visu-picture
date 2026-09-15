<template>
  <a-modal
    v-model:open="visible"
    :width="'min(1600px, 98vw)'"
    :footer="null"
    :closable="false"
    centered
    destroy-on-close
    wrap-class-name="picture-detail-modal"
  >
    <a-spin :spinning="loading">
      <!-- 顶部栏：作者信息 + 操作按钮 -->
      <div class="top-bar">
        <div class="top-author" title="查看作者主页" @click="goUserPage">
          <a-avatar :size="44" :src="picture.user?.userAvatar">
            <template #icon><UserOutlined /></template>
          </a-avatar>
          <div class="author-info">
            <div class="author-name">{{ picture.user?.userName ?? '未知用户' }}</div>
            <div v-if="formatDate(picture.createTime)" class="author-tip">
              发布于 {{ formatDate(picture.createTime) }}
            </div>
          </div>
        </div>
        <div class="top-actions">
          <!-- 点赞（替代参考样式中的浏览数） -->
          <button
            class="pill-btn like-btn"
            :class="{ liked: likeStore.isLiked(picture.id) }"
            @click="doLike"
          >
            <HeartFilled v-if="likeStore.isLiked(picture.id)" />
            <HeartOutlined v-else />
            <b>{{ picture.likeCount ?? 0 }}</b>
          </button>
          <button class="pill-btn" @click="doSearch">
            <SearchOutlined /> 搜图
          </button>
          <button class="pill-btn" @click="download">
            <DownloadOutlined /> 下载
          </button>
          <button class="pill-btn" @click="doShare">
            <ShareAltOutlined /> 分享
          </button>
          <button v-if="canEdit" class="pill-btn" @click="doEdit">
            <EditOutlined /> 编辑
          </button>
          <button v-if="canDelete" class="pill-btn danger" @click="doDelete">
            <DeleteOutlined />
          </button>
          <button class="pill-btn icon-only" @click="visible = false">
            <CloseOutlined />
          </button>
        </div>
      </div>

      <!-- 主体：左大图 + 右信息栏 -->
      <div class="detail-body">
        <!-- 左：图片预览（右键弹出操作菜单） -->
        <a-dropdown
          :trigger="['contextmenu']"
          :visible="menuVisible"
          @visibleChange="(v: boolean) => (menuVisible = v)"
        >
          <div class="preview-area" @contextmenu.prevent>
            <a-image v-if="picture.url" :src="picture.url" class="preview-img" />
          </div>
          <template #overlay>
            <a-menu @click="onMenuClick">
              <a-menu-item key="copy">
                <CopyOutlined /> 复制图片链接
              </a-menu-item>
              <a-menu-item key="download">
                <DownloadOutlined /> 下载图片
              </a-menu-item>
              <a-menu-item key="share">
                <ShareAltOutlined /> 分享
              </a-menu-item>
              <a-menu-divider v-if="canEdit || canDelete" />
              <a-menu-item v-if="canEdit" key="edit">
                <EditOutlined /> 编辑图片
              </a-menu-item>
              <a-menu-item v-if="canDelete" key="delete" danger>
                <DeleteOutlined /> 删除图片
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>

        <!-- 右：图片信息栏 -->
        <div class="info-area">
          <h2 class="pic-title">{{ picture.name ?? '图片详情' }}</h2>
          <a-tag v-if="picture.isAiGenerated === 1" color="purple" class="ai-tag">AI 生成</a-tag>
          <!-- 简介 -->
          <p v-if="picture.introduction" class="intro-block">
            {{ picture.introduction }}
          </p>
          <!-- 分类与标签 -->
          <div v-if="picture.tags?.length || picture.category" class="tags-row">
            <a-tag color="blue">{{ picture.category ?? '默认' }}</a-tag>
            <a-tag v-for="tag in picture.tags" :key="tag">{{ tag }}</a-tag>
          </div>
          <!-- 元信息 -->
          <div class="meta-grid">
            <div class="meta-item">
              <span class="meta-label">格式</span>
              <span class="meta-value">{{ picture.picFormat ?? '-' }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">尺寸</span>
              <span class="meta-value">
                {{ picture.picWidth ?? '-' }} × {{ picture.picHeight ?? '-' }}
              </span>
            </div>
            <div class="meta-item">
              <span class="meta-label">宽高比</span>
              <span class="meta-value">{{ picture.picScale ?? '-' }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">大小</span>
              <span class="meta-value">{{ formatSize(picture.picSize) }}</span>
            </div>
            <div class="meta-item meta-item-wide">
              <span class="meta-label">主色调</span>
              <span class="meta-value color-value">
                {{ picture.picColor ?? '-' }}
                <i
                  v-if="picture.picColor"
                  class="color-dot"
                  :style="{ background: toHexColor(picture.picColor) }"
                />
              </span>
            </div>
          </div>
          <a-tag v-if="canSwitch" class="switch-tip">← → 切换图片</a-tag>
        </div>
      </div>
    </a-spin>
    <ShareModal ref="shareModalRef" :link="shareLink" />
  </a-modal>
</template>

<script setup lang="ts">
import { computed, createVNode, onMounted, onUnmounted, ref } from 'vue'
import { Modal, message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import {
  CloseOutlined,
  CopyOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  ExclamationCircleOutlined,
  HeartFilled,
  HeartOutlined,
  SearchOutlined,
  ShareAltOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { deletePictureUsingPost } from '@/api/pictureController.ts'
import { formatSize, toHexColor } from '@/utils'
import ShareModal from '@/components/ShareModal.vue'
import { usePictureDetail } from '@/composables/usePictureDetail.ts'
import { useLikeStore } from '@/stores/useLikeStore.ts'

const emit = defineEmits<{
  (e: 'deleted'): void
}>()

const likeStore = useLikeStore()

const {
  picture,
  loading,
  canEdit,
  canDelete,
  fetchDetail,
  download,
  shareLink,
  setShareLink,
} = usePictureDetail()

const visible = ref(false)

// ----- 键盘左右切换（需外部传入同组图片 id 列表） -----
const idList = ref<(string | number)[]>([])
const currentId = ref<string | number>()
const canSwitch = computed(() => idList.value.length > 1)
const currentIndex = computed(() =>
  idList.value.findIndex((id) => String(id) === String(currentId.value)),
)

const switchTo = (id: string | number) => {
  currentId.value = id
  fetchDetail(id)
}

const goPrev = () => {
  if (!canSwitch.value) return
  const len = idList.value.length
  switchTo(idList.value[(currentIndex.value - 1 + len) % len])
}
const goNext = () => {
  if (!canSwitch.value) return
  const len = idList.value.length
  switchTo(idList.value[(currentIndex.value + 1) % len])
}

// 打开弹窗：id 当前图片，idList 可选（同一列表的图片 id，用于左右切换）
const openModal = (id: string | number, list?: (string | number)[]) => {
  visible.value = true
  currentId.value = id
  idList.value =
    list && list.length ? list : [id]
  fetchDetail(id)
}

// 键盘导航
const onKeydown = (e: KeyboardEvent) => {
  if (!visible.value) return
  if (e.key === 'ArrowLeft') {
    goPrev()
  } else if (e.key === 'ArrowRight') {
    goNext()
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onUnmounted(() => window.removeEventListener('keydown', onKeydown))

defineExpose({ openModal })

// ----- 点赞 / 取消点赞 -----
const doLike = () => {
  likeStore.toggle(picture.value)
}

// ----- 查看作者主页 -----
const router = useRouter()
const goUserPage = () => {
  const userId = picture.value.user?.id ?? picture.value.userId
  if (userId != null) {
    visible.value = false
    router.push(`/user/${userId}`)
  }
}

// ----- 右键菜单 -----
const menuVisible = ref(false)
const onMenuClick = ({ key }: { key: string }) => {
  menuVisible.value = false
  switch (key) {
    case 'copy':
      doCopyLink()
      break
    case 'download':
      download()
      break
    case 'share':
      doShare()
      break
    case 'edit':
      doEdit()
      break
    case 'delete':
      doDelete()
      break
  }
}

// 复制图片链接
const doCopyLink = async () => {
  const url = picture.value.url
  if (!url) return
  try {
    await navigator.clipboard.writeText(url)
    message.success('图片链接已复制')
  } catch {
    message.error('复制失败')
  }
}

// 以图搜图（站内）
const doSearch = () => {
  window.open(`/search_picture?pictureId=${picture.value.id}`)
}

// 编辑
const doEdit = () => {
  router.push({
    path: '/add_picture',
    query: {
      id: picture.value.id,
      spaceId: picture.value.spaceId,
    },
  })
}

// 删除（二次确认，成功后关闭弹窗并通知父组件刷新）
const doDelete = () => {
  const id = picture.value.id
  if (!id) return
  Modal.confirm({
    title: '确认删除这张图片吗？',
    icon: createVNode(ExclamationCircleOutlined),
    content: picture.value.name ?? '删除后不可恢复',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      const res = await deletePictureUsingPost({ id })
      if (res.data.code === 0) {
        message.success('删除成功')
        visible.value = false
        emit('deleted')
      } else {
        message.error('删除失败，' + res.data.message)
      }
    },
  })
}

// ----- 分享 -----
const shareModalRef = ref()
const doShare = () => {
  setShareLink()
  if (shareModalRef.value) {
    shareModalRef.value.openModal()
  }
}

// 时间格式化：兼容 ISO（带 T）与空格分隔两种格式
const formatDate = (time?: string) => {
  if (!time) return ''
  let d = new Date(time)
  if (isNaN(d.getTime())) {
    d = new Date(time.replace(/-/g, '/').replace('T', ' '))
  }
  if (isNaN(d.getTime())) return ''
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}
</script>

<style scoped>
/* 顶部栏 */
.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 4px 4px 12px;
  border-bottom: 1px solid rgba(35, 44, 86, 0.08);
}

.top-author {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  border-radius: 12px;
  padding: 4px 8px;
  transition: background 0.2s;
}

.top-author:hover {
  background: rgba(61, 90, 245, 0.06);
}

.author-name {
  font-weight: 600;
  font-size: 15px;
  color: #171a2b;
}

.author-tip {
  font-size: 12px;
  color: rgba(35, 44, 86, 0.45);
}

/* 顶部操作按钮组：胶囊样式 */
.top-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.pill-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid rgba(35, 44, 86, 0.14);
  background: transparent;
  color: #232c56;
  font-size: 13px;
  cursor: pointer;
  transition:
    background 0.2s,
    color 0.2s,
    border-color 0.2s,
    transform 0.15s;
}

.pill-btn:hover {
  background: rgba(61, 90, 245, 0.06);
  border-color: rgba(61, 90, 245, 0.4);
  transform: translateY(-1px);
}

.pill-btn:active {
  transform: scale(0.96);
}

.pill-btn.icon-only {
  width: 36px;
  padding: 0;
  justify-content: center;
}

.pill-btn.danger:hover {
  color: #ff4d4f;
  border-color: #ff4d4f;
  background: rgba(255, 77, 79, 0.06);
}

.like-btn.liked {
  color: #ff4d6a;
  border-color: rgba(255, 77, 106, 0.5);
  background: rgba(255, 77, 106, 0.08);
}

/* 主体布局：左大图 + 右信息栏 */
.detail-body {
  display: flex;
  gap: 20px;
  margin-top: 12px;
}

/* 左侧预览区：纯白背景，固定高度，小图也放大撑满区域 */
.preview-area {
  flex: 1;
  min-width: 0;
  height: calc(88vh - 130px);
  min-height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  border-radius: 12px;
  background-color: #fff;
}

.preview-area :deep(.ant-image) {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-area :deep(img) {
  width: 100%;
  height: 100%;
  object-fit: contain;
  border-radius: 8px;
}

/* 右侧信息区 */
.info-area {
  width: 320px;
  flex-shrink: 0;
  max-height: 82vh;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.pic-title {
  font-size: 18px;
  font-weight: 700;
  color: #171a2b;
  margin: 0;
  line-height: 1.4;
}

.ai-tag {
  align-self: flex-start;
}

.intro-block {
  font-size: 13px;
  line-height: 1.7;
  color: rgba(35, 44, 86, 0.75);
  background: rgba(61, 90, 245, 0.05);
  border-radius: 10px;
  padding: 10px 12px;
  margin: 0;
}

.tags-row :deep(.ant-tag) {
  margin-inline-end: 0;
}

.meta-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
  background: rgba(61, 90, 245, 0.04);
  border-radius: 8px;
  padding: 8px 10px;
}

.meta-item-wide {
  grid-column: span 2;
}

.meta-label {
  font-size: 12px;
  color: rgba(35, 44, 86, 0.45);
}

.meta-value {
  font-size: 13px;
  font-weight: 500;
  color: #26283a;
  display: flex;
  align-items: center;
}

.color-dot {
  width: 14px;
  height: 14px;
  border-radius: 4px;
  margin-left: 6px;
  border: 1px solid rgba(35, 44, 86, 0.15);
}

.switch-tip {
  align-self: flex-start;
  font-size: 12px;
  color: rgba(61, 90, 245, 0.7);
  background: rgba(61, 90, 245, 0.06);
  border: 1px solid rgba(61, 90, 245, 0.16);
}

/* 弹窗整体 padding 微调 */
.picture-detail-modal :deep(.ant-modal-content) {
  padding: 16px 20px 20px;
}

/* 深色模式 */
html.dark .author-name,
html.dark .pic-title,
html.dark .meta-value {
  color: #e8eaf6;
}

html.dark .author-tip,
html.dark .meta-label {
  color: rgba(200, 208, 240, 0.5);
}

html.dark .intro-block {
  color: rgba(200, 208, 240, 0.75);
  background: rgba(120, 140, 220, 0.08);
}

html.dark .meta-item {
  background: rgba(120, 140, 220, 0.07);
}

html.dark .preview-area {
  background-color: #171d30;
}

html.dark .top-bar {
  border-bottom-color: rgba(255, 255, 255, 0.1);
}

html.dark .pill-btn {
  color: #e8eaf6;
  border-color: rgba(255, 255, 255, 0.18);
}

html.dark .pill-btn:hover {
  background: rgba(120, 140, 220, 0.12);
  border-color: rgba(120, 140, 220, 0.5);
}

html.dark .pill-btn.danger:hover {
  color: #ff4d4f;
  border-color: #ff4d4f;
}
</style>
