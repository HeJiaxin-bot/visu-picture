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
            :key="`like-${likeBumpKey}`"
            class="pill-btn like-btn"
            :class="{ liked: likeStore.isLiked(picture.id), bump: likeBumpKey > 0 }"
            @click="doLike"
          >
            <HeartFilled v-if="likeStore.isLiked(picture.id)" />
            <HeartOutlined v-else />
            <b>{{ picture.likeCount ?? 0 }}</b>
            <span v-if="showPlusOne" class="plus-one">+1</span>
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
          <!-- 移动端更多菜单：右键菜单在触屏上无法呼出，用 ⋯ 按钮承载同一份操作 -->
          <a-dropdown :trigger="['click']" placement="bottomRight">
            <button class="pill-btn icon-only mobile-menu-btn">
              <MoreOutlined />
            </button>
            <template #overlay>
              <a-menu @click="onMenuClick">
                <a-menu-item key="copy">
                  <CopyOutlined /> 复制图片链接
                </a-menu-item>
                <a-menu-item key="search">
                  <SearchOutlined /> 以图搜图
                </a-menu-item>
                <a-menu-item key="specs">
                  <InfoCircleOutlined /> 属性信息
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
              <a-menu-item key="search">
                <SearchOutlined /> 以图搜图
              </a-menu-item>
              <a-menu-item key="specs">
                <InfoCircleOutlined /> 属性信息
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

        <!-- 移动端互动行：图片下方，左点赞/收藏/分享，右下载（桌面隐藏） -->
        <div class="mobile-actions">
          <div class="ma-left">
            <button
              :key="`m-like-${likeBumpKey}`"
              class="ma-btn"
              :class="{ liked: likeStore.isLiked(picture.id), bump: likeBumpKey > 0 }"
              @click="doLike"
            >
              <HeartFilled v-if="likeStore.isLiked(picture.id)" />
              <HeartOutlined v-else />
              <b>{{ picture.likeCount ?? 0 }}</b>
              <span v-if="showPlusOne" class="plus-one">+1</span>
            </button>
            <button
              class="ma-btn"
              :class="{ favorited: likeStore.isFavorited(picture.id) }"
              @click="doFavorite"
            >
              <StarFilled v-if="likeStore.isFavorited(picture.id)" />
              <StarOutlined v-else />
              <b>{{ picture.favoriteCount ?? 0 }}</b>
            </button>
            <button class="ma-btn" @click="doShare">
              <ShareAltOutlined />
            </button>
            <!-- 浏览量（仅展示） -->
            <div class="ma-btn ma-views" title="浏览量">
              <EyeOutlined />
              <b>{{ picture.viewCount ?? 0 }}</b>
            </div>
          </div>
          <button class="ma-download" @click="download">
            <DownloadOutlined /> 下载
          </button>
        </div>

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
              <div class="spec-row">
                <dt>浏览量</dt>
                <dd>{{ picture.viewCount ?? 0 }}</dd>
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
    <!-- 属性信息：规格表收纳（移动端从 ⋯ 菜单打开） -->
    <a-modal
      v-model:open="specsVisible"
      title="属性信息"
      :footer="null"
      :width="340"
      centered
    >
      <dl class="spec-list specs-modal-list">
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
        <div class="spec-row">
          <dt>浏览量</dt>
          <dd>{{ picture.viewCount ?? 0 }}</dd>
        </div>
        <div class="spec-row" v-if="picture.picColor">
          <dt>主色调</dt>
          <dd class="color-value">
            {{ picture.picColor }}
            <i class="color-dot" :style="{ background: toHexColor(picture.picColor) }" />
          </dd>
        </div>
      </dl>
    </a-modal>
    <ShareModal ref="shareModalRef" :picture-id="picture.id" />
  </a-modal>
</template>

<script setup lang="ts">
import { computed, createVNode, onMounted, onUnmounted, ref, watch } from 'vue'
import { Modal, message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import {
  CloseOutlined,
  CopyOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  ExclamationCircleOutlined,
  EyeOutlined,
  HeartFilled,
  HeartOutlined,
  InfoCircleOutlined,
  MoreOutlined,
  SearchOutlined,
  ShareAltOutlined,
  StarFilled,
  StarOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { deletePictureUsingPost, incrementPictureViewUsingPost } from '@/api/pictureController.ts'
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

// 属性信息弹窗（移动端规格表收纳在 ⋯ 菜单里）
const specsVisible = ref(false)

// 主弹窗关闭时联动收起属性弹窗，避免下次打开时残留；
// 同时处理滚动位置：antd 锁 body 滚动在部分布局下会把页面滚动重置到顶部，
// 打开前记住位置，关闭并解除滚动锁后恢复，保证关闭后仍停留在原浏览位置
let savedScrollY = 0
watch(visible, async (v) => {
  if (v) {
    specsVisible.value = false
    savedScrollY = window.scrollY
    return
  }
  // 等待 antd 解除 body 滚动锁（关闭动画结束后），最多等 3s
  const body = document.body
  for (let i = 0; i < 30; i++) {
    await new Promise((r) => setTimeout(r, 100))
    if (getComputedStyle(body).overflow !== 'hidden') break
  }
  if (savedScrollY > 0) window.scrollTo(0, savedScrollY)
})

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
  reportView(id)
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
  reportView(id)
}

/**
 * 记录浏览：浏览量旁路上报（匿名也可统计）+ 浏览历史（仅登录用户，同一张图只保留一条记录）
 * 静默失败：都是旁路数据，不应打断查看
 *
 * id 保持字符串原样：雪花 id（如 2100942816594628609）超出 JS 安全整数，
 * 经 Number() 转换会末位失真并导致后端 404。
 */
const reportView = async (id: string | number) => {
  const pictureId = firstQueryValue(id)
  if (!pictureId) return
  try {
    await incrementPictureViewUsingPost(pictureId)
    // 乐观 +1：本次打开也计入展示（后端同一次调用已真实累加）。
    // viewCount 经 Long→String 序列化下发，必须先转 Number，避免字符串拼接
    picture.value.viewCount = Number(picture.value.viewCount ?? 0) + 1
  } catch {
    // 静默
  }
  if (!loginUserStore.loginUser?.id) return
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
// 点赞缩放弹跳动画 + 数字 +1 漂浮效果
const likeBumpKey = ref(0)
const showPlusOne = ref(false)
const doLike = () => {
  const wasLiked = likeStore.isLiked(picture.value.id)
  likeStore.toggle(picture.value)
  if (!wasLiked) {
    // 换 key 强制重放弹跳动画
    likeBumpKey.value++
    showPlusOne.value = true
    window.setTimeout(() => {
      showPlusOne.value = false
    }, 800)
  }
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
    case 'search':
      doSearch()
      break
    case 'specs':
      specsVisible.value = true
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
  position: relative;
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

/* 点赞弹跳动画 + 数字 +1 漂浮 */
.pill-btn.bump {
  animation: like-bump 0.45s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes like-bump {
  0% {
    transform: scale(1);
  }
  40% {
    transform: scale(1.35);
  }
  70% {
    transform: scale(0.92);
  }
  100% {
    transform: scale(1);
  }
}

.plus-one {
  position: absolute;
  top: -10px;
  right: 8px;
  font-size: 13px;
  font-weight: 700;
  color: #ff4d6d;
  animation: plus-one-rise 0.8s ease-out forwards;
  pointer-events: none;
}

@keyframes plus-one-rise {
  0% {
    transform: translateY(0);
    opacity: 1;
  }
  100% {
    transform: translateY(-20px);
    opacity: 0;
  }
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

/*
  移动端适配（小红书式详情页）：
  双栏（左图右信息）在窄屏下会把图片区挤到 0 宽，改为上下堆叠 ——
  吸顶作者栏 + 全幅出血大图 + 图下互动行 + 大标题排版，整页自然滚动。
*/
/* 移动端专属元素：桌面默认隐藏 */
.mobile-menu-btn,
.mobile-actions {
  display: none;
}

@media (max-width: 768px) {
  /* 弹窗内容零内边距：图片全幅出血，各区块自管留白 */
  .picture-detail-modal :deep(.ant-modal-content) {
    padding: 0 0 10px;
  }

  /* 吸顶栏：作者在左，关闭/更多在右 */
  .top-bar {
    position: sticky;
    top: 0;
    z-index: 12;
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    padding: 10px 12px;
    background: var(--bg-card);
    border-bottom: 1px solid var(--border-color);
  }

  .top-author {
    padding: 0;
    min-width: 0;
  }

  .author-name {
    font-size: 15px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .author-tip {
    margin-top: 1px;
  }

  /* 顶栏只保留关闭与更多菜单，其余胶囊按钮让位给图下互动行 */
  .top-actions {
    gap: 2px;
    flex-shrink: 0;
  }

  .top-actions .pill-btn {
    display: none;
  }

  .top-actions .pill-btn.icon-only,
  .top-actions .pill-btn.mobile-menu-btn {
    display: inline-flex;
  }

  .top-actions .pill-btn.icon-only {
    order: -1;
    border: none;
    background: transparent;
    width: 34px;
    height: 34px;
    font-size: 16px;
  }

  .mobile-menu-btn {
    width: 34px;
    height: 34px;
    border: none;
    background: transparent;
    font-size: 17px;
    color: var(--text-primary-light);
  }

  /* 主体上下堆叠 */
  .detail-body {
    flex-direction: column;
    height: auto;
    min-height: 0;
    margin-top: 0;
    border-top: none;
  }

  /* 全幅出血大图：暗色信箱底，无圆角 */
  .preview-area {
    height: 46vh;
    min-height: 260px;
    padding: 0;
    border-right: none;
    background: #0d0d12;
  }

  .preview-area :deep(img) {
    border-radius: 0;
  }

  /* 图下互动行：左点赞/收藏/分享，右主下载按钮 */
  .mobile-actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 16px 2px;
  }

  .ma-left {
    display: flex;
    align-items: center;
    gap: 20px;
  }

  .ma-btn {
    position: relative;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    border: none;
    background: transparent;
    color: var(--text-primary-light);
    font-size: 16px;
    padding: 4px 0;
    cursor: pointer;
    transition: transform 0.15s;
  }

  .ma-btn b {
    font-weight: 600;
    font-size: 14px;
  }

  .ma-btn:active {
    transform: scale(0.92);
  }

  .ma-btn.liked {
    color: #ff4d6a;
  }

  .ma-btn.favorited {
    color: #faad14;
  }

  /* 浏览量：只展示不可点击，弱化配色 */
  .ma-views {
    color: var(--text-secondary);
    cursor: default;
  }

  .ma-btn.bump {
    animation: like-bump 0.45s cubic-bezier(0.34, 1.56, 0.64, 1);
  }

  .ma-download {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    height: 34px;
    padding: 0 18px;
    border-radius: 999px;
    border: none;
    background: linear-gradient(135deg, #40a9ff, #1677ff);
    color: #fff;
    font-size: 13px;
    font-weight: 600;
    cursor: pointer;
    transition: transform 0.15s;
  }

  .ma-download:active {
    transform: scale(0.95);
  }

  /* 信息区：大标题 + 通栏排版 */
  .info-area {
    width: 100%;
    height: auto;
    padding: 10px 16px 0;
    gap: 12px;
  }

  .pic-title {
    font-size: 20px;
    line-height: 1.35;
  }

  /* 简介改通栏段落（引用线在窄屏上显窄） */
  .intro-block {
    padding-left: 0;
    border-left: none;
  }

  /* 元信息不再限高，跟随页面滚动 */
  .info-scroll {
    max-height: none;
    overflow: visible;
  }

  /* 评论区限高内部滚动，避免撑出超长空白 */
  .comment-zone {
    flex: none;
    max-height: 48vh;
  }

  /* 键盘切换提示在触屏上无意义 */
  .switch-tip {
    display: none;
  }

  /* 规格表收纳进 ⋯ 菜单的属性信息弹窗，信息区更聚焦内容 */
  .info-area .spec-list {
    display: none;
  }
}

/* 属性信息弹窗里的规格表：留白比内联版宽松 */
.specs-modal-list {
  padding: 4px 2px;
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
