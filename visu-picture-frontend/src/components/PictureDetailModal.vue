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
          <!-- 收藏 -->
          <button
            class="pill-btn favorite-btn"
            :class="{ favorited: likeStore.isFavorited(picture.id) }"
            @click="doFavorite"
          >
            <StarFilled v-if="likeStore.isFavorited(picture.id)" />
            <StarOutlined v-else />
            <b>{{ picture.favoriteCount ?? 0 }}</b>
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
          <button class="pill-btn" @click="doEdit" v-if="canEdit">
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

        <!-- 右：信息与评论（排版式布局，无卡片嵌套） -->
        <div class="info-area">
          <div class="info-scroll">
            <h2 class="pic-title">{{ picture.name ?? '图片详情' }}</h2>

            <div v-if="picture.isAiGenerated === 1" class="ai-flag">
              <span class="ai-flag-dot" /> AI 生成内容
            </div>

            <p v-if="picture.introduction" class="intro-block">
              {{ picture.introduction }}
            </p>

            <div v-if="picture.tags?.length || picture.category" class="tags-row">
              <a-tag v-if="picture.category" color="blue">{{ picture.category }}</a-tag>
              <a-tag v-for="tag in picture.tags" :key="tag">{{ tag }}</a-tag>
            </div>

            <!-- 规格表：label 左 value 右，去卡片化 -->
            <dl class="spec-list">
              <div class="spec-row">
                <dt>格式</dt>
                <dd>{{ picture.picFormat ?? '—' }}</dd>
              </div>
              <div class="spec-row">
                <dt>尺寸</dt>
                <dd>{{ picture.picWidth ?? '—' }} × {{ picture.picHeight ?? '—' }}</dd>
              </div>
              <div class="spec-row">
                <dt>宽高比</dt>
                <dd>{{ picture.picScale ?? '—' }}</dd>
              </div>
              <div class="spec-row">
                <dt>大小</dt>
                <dd>{{ formatSize(picture.picSize) }}</dd>
              </div>
              <div class="spec-row" v-if="picture.picColor">
                <dt>主色调</dt>
                <dd class="color-value">
                  {{ picture.picColor }}
                  <i class="color-dot" :style="{ background: toHexColor(picture.picColor) }" />
                </dd>
              </div>
            </dl>

            <a-tag v-if="canSwitch" class="switch-tip">← → 切换图片</a-tag>
          </div>

          <!-- 评论区：占满剩余高度，输入框固定底部 -->
          <div class="comment-zone">
            <PictureCommentPanel
              :picture-id="picture.id"
              :author-id="picture.userId"
            />
          </div>
        </div>
      </div>
    </a-spin>
    <ShareModal ref="shareModalRef" :picture-id="picture.id" />
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
  StarFilled,
  StarOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { deletePictureUsingPost } from '@/api/pictureController.ts'
import { recordView } from '@/api/interaction.ts'
import { firstQueryValue, formatSize, toHexColor } from '@/utils'
import ShareModal from '@/components/ShareModal.vue'
import PictureCommentPanel from '@/components/PictureCommentPanel.vue'
import { usePictureDetail } from '@/composables/usePictureDetail.ts'
import { useLikeStore } from '@/stores/useLikeStore.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

const emit = defineEmits<{
  (e: 'deleted'): void
}>()

const likeStore = useLikeStore()
const loginUserStore = useLoginUserStore()

const {
  picture,
  loading,
  canEdit,
  canDelete,
  fetchDetail,
  download,
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
  recordViewIfLoggedIn(id)
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
  recordViewIfLoggedIn(id)
}

/**
 * 记录浏览历史（仅登录用户；同一张图只保留一条记录）
 * 静默失败：浏览历史是旁路数据，不应打断查看
 *
 * id 保持字符串原样：雪花 id（如 2100942816594628609）超出 JS 安全整数，
 * 经 Number() 转换会末位失真并导致后端 404。
 */
const recordViewIfLoggedIn = async (id: string | number) => {
  if (!loginUserStore.loginUser?.id) return
  const pictureId = firstQueryValue(id)
  if (!pictureId) return
  try {
    await recordView(pictureId)
  } catch {
    // 忽略
  }
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

// ----- 收藏 / 取消收藏 -----
const doFavorite = () => {
  likeStore.toggleFavorite(picture.value)
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
  background: rgba(64, 169, 255, 0.06);
}

.author-name {
  font-weight: 600;
  font-size: 15px;
  color: var(--text-primary-light);
}

.author-tip {
  font-size: 12px;
  color: var(--text-disabled);
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
  border: 1px solid var(--border-color);
  background: transparent;
  color: var(--text-primary-light);
  font-size: 13px;
  cursor: pointer;
  transition:
    background 0.2s,
    color 0.2s,
    border-color 0.2s,
    transform 0.15s;
}

.pill-btn:hover {
  background: rgba(64, 169, 255, 0.06);
  border-color: rgba(64, 169, 255, 0.4);
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

/* 收藏按钮选中态 */
.favorite-btn.favorited {
  color: #faad14;
  border-color: rgba(250, 173, 20, 0.55);
  background: rgba(250, 173, 20, 0.1);
}

/* 主体布局：左大图 + 右信息栏 */
.detail-body {
  display: flex;
  /* 两栏等高：右栏据此撑满，评论输入框才能贴到弹窗底部 */
  height: calc(88vh - 130px);
  min-height: 420px;
  margin-top: 12px;
  /* 左右两栏之间的分隔：用一条实线明确切分（参考图的做法），
     仅靠间距在白底上区分度不够 */
  border-top: 1px solid var(--border-color);
}

/* 左侧预览区：纯白背景，底部对齐，右侧以竖线与信息栏分隔 */
.preview-area {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px 24px 16px 4px;
  background-color: var(--bg-card);
  /* 竖线分隔：与右栏形成明确边界。
     用比通用 border-color 更实的颜色 —— 白底白卡上 0.08 的透明度几乎不可见 */
  border-right: 1px solid rgba(0, 0, 0, 0.12);
}

html.dark .preview-area {
  border-right-color: rgba(255, 255, 255, 0.14);
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

/*
  右侧信息区：两段式布局。
  - .info-scroll 承载图片元信息，内容超出时自身滚动
  - .comment-zone 占满剩余高度，其内部再分「列表滚动 + 输入框固定」
  这样无论元信息多长、评论多少，输入框始终贴在弹窗底部。
*/
.info-area {
  width: 320px;
  flex-shrink: 0;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
  /* 与左侧图片区留出呼吸空间（竖线在 preview-area 的 border-right 上） */
  padding-left: 20px;
  padding-top: 16px;
  padding-bottom: 4px;
}

/* 元信息区：可滚动，但不占据评论区的空间 */
.info-scroll {
  flex-shrink: 0;
  /* 上限约为右栏一半，超出则自身滚动，避免把评论区挤没 */
  max-height: 46%;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-right: 4px;
}

.info-scroll::-webkit-scrollbar {
  width: 6px;
}

.info-scroll::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.12);
  border-radius: 3px;
}

.info-scroll::-webkit-scrollbar-track {
  background: transparent;
}

/* 评论区：占满剩余高度；面板自身是 flex 列，输入框贴底 */
.comment-zone {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

/* 面板在弹窗内不再自限高度，改为撑满 comment-zone（由父级决定高度） */
.comment-zone :deep(.cmt) {
  flex: 1;
  min-height: 0;
  max-height: none;
}

.pic-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary-light);
  margin: 0;
  line-height: 1.3;
  letter-spacing: -0.01em;
}

/* AI 生成标识：小圆点 + 文字，不用 tag 胶囊 */
.ai-flag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  align-self: flex-start;
  font-size: 12px;
  font-weight: 600;
  color: #7c3aed;
}

.ai-flag-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #7c3aed;
}

html.dark .ai-flag {
  color: #b794f6;
}

html.dark .ai-flag-dot {
  background: #b794f6;
}

.intro-block {
  font-size: 13.5px;
  line-height: 1.75;
  color: var(--text-secondary);
  margin: 0;
  padding-left: 12px;
  /* 排版式引用：左侧一根细线，不用底色块 */
  border-left: 2px solid var(--accent);
}

.tags-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tags-row :deep(.ant-tag) {
  margin-inline-end: 0;
  border: none;
  background: rgba(0, 0, 0, 0.04);
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 22px;
  padding: 0 10px;
  border-radius: 6px;
}

html.dark .tags-row :deep(.ant-tag) {
  background: rgba(255, 255, 255, 0.07);
}

/* 规格表：两栏 label/value，细分隔线，无卡片 */
.spec-list {
  margin: 0;
  display: flex;
  flex-direction: column;
}

.spec-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 0;
  border-bottom: 1px solid var(--border-color);
}

.spec-row:last-child {
  border-bottom: none;
}

.spec-row dt {
  font-size: 12.5px;
  color: var(--text-disabled);
  flex-shrink: 0;
}

.spec-row dd {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary-light);
  font-variant-numeric: tabular-nums;
  display: flex;
  align-items: center;
}

.color-dot {
  width: 13px;
  height: 13px;
  border-radius: 4px;
  margin-left: 8px;
  border: 1px solid var(--border-color);
  flex-shrink: 0;
}

/* 顶部栏：与内容区之间用一条贯穿的横线分隔（排版式，不用阴影） */
.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 4px 4px 14px;
  border-bottom: 1px solid var(--border-color);
}
.switch-tip {
  align-self: flex-start;
  font-size: 12px;
  color: rgba(64, 169, 255, 0.7);
  background: rgba(64, 169, 255, 0.06);
  border: 1px solid rgba(64, 169, 255, 0.16);
}

/* 弹窗整体 padding 微调 */
.picture-detail-modal :deep(.ant-modal-content) {
  padding: 20px 24px 24px;
}

/* 深色模式 */
html.dark .intro-block {
  color: rgba(179, 179, 179, 0.8);
}

html.dark .top-bar {
  border-bottom-color: rgba(255, 255, 255, 0.1);
}

html.dark .pill-btn {
  color: var(--text-primary);
  border-color: rgba(255, 255, 255, 0.18);
}

html.dark .pill-btn:hover {
  background: rgba(64, 169, 255, 0.12);
  border-color: rgba(64, 169, 255, 0.5);
}

html.dark .pill-btn.danger:hover {
  color: #ff4d4f;
  border-color: #ff4d4f;
}
</style>
