<template>
  <div class="picture-list">
    <!-- Justified 瀑布流：每行等宽铺满，图片保持原始宽高比 -->
    <div ref="listRef" class="justified-list">
      <a-spin v-if="loading && dataList.length === 0" class="list-spin" />
      <div
        v-for="(row, rowIndex) in rows"
        :key="rowIndex"
        class="justified-row"
      >
        <div
          v-for="cell in row.items"
          :key="cell.picture.id"
          class="justified-item"
          :style="{ width: itemWidth(cell, row.height) + 'px' }"
          @click="doClickPicture(cell.picture)"
        >
          <div class="img-wrapper" :style="{ height: row.height + 'px' }">
            <img
              :alt="cell.picture.name"
              :src="cell.picture.thumbnailUrl ?? cell.picture.url"
              loading="lazy"
              decoding="async"
            />
            <!-- AI 生成内容标识（常显，符合内容标识规范） -->
            <div v-if="cell.picture.isAiGenerated === 1" class="ai-badge">AI 生成</div>
            <!-- 悬浮信息层 -->
            <div class="item-overlay">
              <div class="overlay-top">
                <div class="pic-tags">
                  <a-tag color="green">{{ cell.picture.category ?? '默认' }}</a-tag>
                  <a-tag v-for="tag in cell.picture.tags?.slice(0, 2)" :key="tag">
                    {{ tag }}
                  </a-tag>
                </div>
              </div>
              <div v-if="showOp" class="overlay-actions" @click.stop>
                <ShareAltOutlined @click="(e) => doShare(cell.picture, e)" />
                <SearchOutlined @click="(e) => doSearch(cell.picture, e)" />
                <EditOutlined v-if="canEdit" @click="(e) => doEdit(cell.picture, e)" />
                <DeleteOutlined v-if="canDelete" @click="(e) => doDelete(cell.picture, e)" />
              </div>
            </div>
          </div>
          <!-- 图片下方信息条：名称 + 作者 + 点赞 -->
          <div class="card-footer">
            <div class="pic-name" :title="cell.picture.name">{{ cell.picture.name }}</div>
            <div class="card-meta">
              <div class="pic-author" title="查看作者主页" @click.stop="goUserPage(cell.picture)">
                <a-avatar :size="18" :src="cell.picture.user?.userAvatar">
                  <template #icon><UserOutlined /></template>
                </a-avatar>
                <span class="author-name">{{ cell.picture.user?.userName ?? '未知用户' }}</span>
              </div>
              <div
                class="pic-like"
                :class="{ liked: likeStore.isLiked(cell.picture.id) }"
                title="点赞"
                @click.stop="doLike(cell.picture)"
              >
                <HeartFilled v-if="likeStore.isLiked(cell.picture.id)" />
                <HeartOutlined v-else />
                {{ formatCount(cell.picture.likeCount ?? 0) }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <!-- 滚动加载哨兵：进入视口时触发 loadMore -->
    <div ref="sentinelRef" class="load-more-sentinel">
      <a-spin v-if="loading && dataList.length > 0" size="small" />
      <span v-else-if="finished" class="no-more-text">没有更多了</span>
    </div>
    <ShareModal ref="shareModalRef" :link="shareLink" />
    <!-- 图片详情弹窗 -->
    <PictureDetailModal ref="detailModalRef" @deleted="handleDetailDeleted" />
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import {
  DeleteOutlined,
  EditOutlined,
  HeartFilled,
  HeartOutlined,
  SearchOutlined,
  ShareAltOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { deletePictureUsingPost } from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import ShareModal from '@/components/ShareModal.vue'
import PictureDetailModal from '@/components/PictureDetailModal.vue'
import { useLikeStore } from '@/stores/useLikeStore.ts'
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'

interface Props {
  dataList?: API.PictureVO[]
  loading?: boolean
  showOp?: boolean
  canEdit?: boolean
  canDelete?: boolean
  /** 是否已加载全部数据 */
  finished?: boolean
  /** 卡片尺寸：default 自适应大卡片；compact 紧凑小卡片（空间管理页等场景） */
  size?: 'default' | 'compact'
  onReload?: () => void
}

const props = withDefaults(defineProps<Props>(), {
  dataList: () => [],
  loading: false,
  showOp: false,
  canEdit: false,
  canDelete: false,
  finished: false,
  size: 'default',
})

const emit = defineEmits<{
  (e: 'loadMore'): void
}>()

// ----- Justified 瀑布流布局 -----
const listRef = ref<HTMLDivElement>()
const containerWidth = ref(0)
const GUTTER = 12 // 行内/行间距
let resizeObserver: ResizeObserver | null = null

// 目标行高随容器宽度自适应：宽屏用更大的行高（每行图更少更大），窄屏降低行高；
// compact 模式整体缩小（约 7 折），用于空间管理页等需要小卡片的场景
const targetRowHeight = computed(() => {
  const scale = props.size === 'compact' ? 0.7 : 1
  const width = containerWidth.value
  if (width < 640) return Math.round(200 * scale) // 手机
  if (width < 1024) return Math.round(260 * scale) // 平板
  return Math.round(360 * scale) // 桌面大屏
})

// 宽高比兜底：数据缺失时按 3:2 处理
const getRatio = (picture: API.PictureVO) => {
  const w = picture.picWidth
  const h = picture.picHeight
  if (!w || !h || w <= 0 || h <= 0) return 1.5
  return w / h
}

// 贪心分行：以目标行高逐张放入，当整行宽度达到容器宽度时成行，
// 实际行高 = 容器宽 / 行内宽高比之和（行内等高、铺满无空隙）；最后一行不拉伸
interface RowItem {
  picture: API.PictureVO
  ratio: number
}
interface JustifiedRow {
  items: RowItem[]
  height: number
}

const rows = computed<JustifiedRow[]>(() => {
  const width = containerWidth.value
  if (width <= 0) return []
  const result: JustifiedRow[] = []
  let currentRow: RowItem[] = []
  let ratioSum = 0
  for (const picture of props.dataList) {
    const ratio = getRatio(picture)
    currentRow.push({ picture, ratio })
    ratioSum += ratio
    const gaps = GUTTER * (currentRow.length - 1)
    // 以目标行高渲染时的整行宽度
    const rowWidthAtTarget = ratioSum * (targetRowHeight.value - GUTTER) + gaps
    if (rowWidthAtTarget >= width) {
      result.push({
        items: currentRow,
        height: Math.round((width - gaps) / ratioSum),
      })
      currentRow = []
      ratioSum = 0
    }
  }
  // 最后一行不足：按目标行高展示，不拉伸铺满
  if (currentRow.length > 0) {
    result.push({ items: currentRow, height: targetRowHeight.value })
  }
  return result
})

// 单元格宽度：行高 × 宽高比（最后一行也按行高算，保持原始比例）
const itemWidth = (cell: RowItem, height: number) =>
  Math.round(cell.ratio * (height - GUTTER))

onMounted(() => {
  if (listRef.value) {
    resizeObserver = new ResizeObserver((entries) => {
      containerWidth.value = Math.floor(entries[0].contentRect.width)
    })
    resizeObserver.observe(listRef.value)
  }
})

onUnmounted(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
})

// ----- 滚动加载（IntersectionObserver） -----
const sentinelRef = ref<HTMLDivElement>()
let observer: IntersectionObserver | null = null

// loading 状态本身即防重（加载中不重复触发）；失败重试由下方 autoRetryCount 限制
const tryLoadMore = () => {
  if (props.loading || props.finished) return
  emit('loadMore')
}

// 判断哨兵当前是否在视口内（含预加载余量，与 IO rootMargin 保持一致）
const PRELOAD_MARGIN = 800
const isSentinelVisible = () => {
  const el = sentinelRef.value
  if (!el) return false
  const rect = el.getBoundingClientRect()
  return rect.top < window.innerHeight + PRELOAD_MARGIN
}

onMounted(() => {
  observer = new IntersectionObserver(
    (entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        tryLoadMore()
      }
    },
    { rootMargin: `${PRELOAD_MARGIN}px 0px` },
  )
  if (sentinelRef.value) {
    observer.observe(sentinelRef.value)
  }
})

onUnmounted(() => {
  observer?.disconnect()
  observer = null
})

// 数据加载结束后，若哨兵仍在视口内（如新图片懒加载高度未撑开），自动续载下一页；
// 绕过节流（节流仅用于 IntersectionObserver 高频回调），但限制失败重试次数防死循环
let lastListLength = 0
let autoRetryCount = 0
watch(
  () => [props.dataList.length, props.loading],
  () => {
    nextTick(() => {
      if (props.loading || props.finished) return
      // 数据长度未变化视为本次加载失败，最多连续重试 3 次后停止
      if (props.dataList.length === lastListLength) {
        autoRetryCount++
        if (autoRetryCount >= 3) return
      } else {
        autoRetryCount = 0
      }
      lastListLength = props.dataList.length
      if (isSentinelVisible()) {
        emit('loadMore')
      }
    })
  },
)

const router = useRouter()
// 点赞状态管理
const likeStore = useLikeStore()

// 点赞 / 取消点赞
const doLike = (picture: API.PictureVO) => {
  likeStore.toggle(picture)
}

// 查看作者主页
const goUserPage = (picture: API.PictureVO) => {
  const userId = picture.user?.id ?? picture.userId
  if (userId != null) {
    router.push(`/user/${userId}`)
  }
}

// 点赞数格式化：超过 1 万显示为 x.x w
const formatCount = (count: number) => {
  if (count >= 10000) {
    return (count / 10000).toFixed(1).replace(/\.0$/, '') + 'w'
  }
  return String(count)
}

// 打开图片详情弹窗（不再跳转页面），传入同组 id 列表以支持左右键切换
const detailModalRef = ref()
const doClickPicture = (picture: API.PictureVO) => {
  detailModalRef.value?.openModal(
    picture.id,
    props.dataList.map((p) => p.id).filter((id) => id != null) as (string | number)[],
  )
}

// 弹窗内删除成功后刷新列表
const handleDetailDeleted = () => {
  props.onReload?.()
}

// 搜索（跳转站内以图搜图结果页）
const doSearch = (picture: API.PictureVO, e: Event) => {
  // 阻止冒泡
  e.stopPropagation()
  // 打开新的页面
  window.open(`/search_picture?pictureId=${picture.id}`)
}

// 编辑
const doEdit = (picture: API.PictureVO, e: Event) => {
  // 阻止冒泡
  e.stopPropagation()
  // 跳转时一定要携带 spaceId
  router.push({
    path: '/add_picture',
    query: {
      id: picture.id,
      spaceId: picture.spaceId,
    },
  })
}

// 删除数据
const doDelete = async (picture: API.PictureVO, e: Event) => {
  // 阻止冒泡
  e.stopPropagation()
  const id = picture.id
  if (!id) {
    return
  }
  const res = await deletePictureUsingPost({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
    props.onReload?.()
  } else {
    message.error('删除失败')
  }
}

// ----- 分享操作 ----
const shareModalRef = ref()
// 分享链接
const shareLink = ref<string>('')
// 分享
const doShare = (picture: API.PictureVO, e: Event) => {
  // 阻止冒泡
  e.stopPropagation()
  shareLink.value = `${window.location.protocol}//${window.location.host}/picture/${picture.id}`
  if (shareModalRef.value) {
    shareModalRef.value.openModal()
  }
}
</script>

<style scoped>
.justified-list {
  min-height: 200px;
  width: 100%;
}

.justified-row {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}

.justified-row:last-child {
  margin-bottom: 0;
}

/* 悦目风格：图片独立圆角卡片 + 信息条无框；hover 图片微放大 + 阴影加深 */
.justified-item {
  position: relative;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  cursor: pointer;
  /* 无卡片容器：图片独立成圆角卡片，下方信息直接排在页面背景上，去边框感 */
}

/* 图片区：独立圆角 + 阴影，hover 时仅图片微放大加深阴影 */
.img-wrapper {
  position: relative;
  flex-shrink: 0;
  border-radius: var(--card-radius);
  overflow: hidden;
  background: var(--bg-card);
  box-shadow: var(--card-shadow);
  transition:
    transform 0.3s cubic-bezier(0.33, 0.8, 0.4, 1),
    box-shadow 0.3s ease;
}

.justified-item:hover .img-wrapper {
  transform: scale(1.03);
  /* 季节色调深阴影 + 1px 季节色微光环（内环不改变图片尺寸） */
  box-shadow:
    var(--card-shadow-hover),
    0 0 0 1px var(--glow-ring);
  z-index: 2;
}

.justified-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  background: var(--bg-card);
}

/* 卡片底部信息条：名称 + 作者 + 点赞 */
.card-footer {
  padding: 8px 10px 10px;
}

.pic-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary-light);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

html.dark .pic-name {
  color: var(--text-primary);
}

.card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 6px;
}

.pic-author {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  color: var(--text-secondary);
  transition: color 0.2s;
}

.pic-author:hover {
  color: var(--accent);
}

.pic-author .author-name {
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 96px;
}

.pic-author :deep(.ant-avatar) {
  flex-shrink: 0;
}

.pic-like {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  font-size: 12px;
  color: var(--text-secondary);
  transition:
    color 0.2s,
    transform 0.15s;
}

.pic-like:hover {
  color: #ff4d6a;
  transform: scale(1.1);
}

.pic-like.liked {
  color: #ff4d6a;
  font-weight: 600;
}

/* AI 生成内容标识：右上角常显 */
.ai-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 2;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  background: rgba(111, 66, 193, 0.75);
  backdrop-filter: blur(2px);
  pointer-events: none;
}

/* 悬浮信息层：默认隐藏，hover 渐显 */
.item-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 10px 12px;
  opacity: 0;
  transition: opacity 0.2s ease;
  background: linear-gradient(
    180deg,
    rgba(0, 0, 0, 0.45) 0%,
    rgba(0, 0, 0, 0) 40%,
    rgba(0, 0, 0, 0) 60%,
    rgba(0, 0, 0, 0.5) 100%
  );
}

.justified-item:hover .item-overlay {
  opacity: 1;
}

.overlay-top .pic-tags :deep(.ant-tag) {
  margin: 0 4px 0 0;
  font-size: 11px;
  line-height: 18px;
  padding: 0 6px;
  border: none;
}

.overlay-actions {
  display: flex;
  justify-content: flex-end;
  gap: 14px;
  color: #fff;
  font-size: 16px;
}

.overlay-actions :deep(span) {
  cursor: pointer;
  transition: transform 0.15s ease;
}

.overlay-actions :deep(span:hover) {
  transform: scale(1.2);
}

.list-spin {
  display: block;
  margin: 60px auto;
  width: 100%;
  text-align: center;
}

.load-more-sentinel {
  min-height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 8px 0;
}

.no-more-text {
  color: var(--text-secondary);
  font-size: 13px;
}
</style>
