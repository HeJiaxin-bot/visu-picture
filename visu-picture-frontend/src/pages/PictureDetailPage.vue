<template>
  <div id="pictureDetailPage">
    <a-row :gutter="[32, 24]">
      <!-- 左列：预览 + 评论（同一列，左右边界与预览严格对齐） -->
      <a-col :sm="24" :md="16" :xl="18">
        <div class="preview-wrap">
          <a-image
            :src="picture.url"
            class="preview-image"
            :preview="{ maskClassName: 'preview-mask' }"
          />
          <div v-if="picture.isAiGenerated === 1" class="ai-flag">
            <span class="ai-flag-dot" /> AI 生成内容
          </div>
        </div>

        <!-- 评论区：与预览同宽同边界，不再整行撑开 -->
        <div class="comment-section">
          <PictureCommentPanel :picture-id="picture.id" :author-id="picture.userId" />
        </div>
      </a-col>

      <!-- 图片信息区域：排版式，无卡片 -->
      <a-col :sm="24" :md="8" :xl="6">
        <div class="info-col">
          <!-- 作者 -->
          <div class="info-author" @click="goUser">
            <a-avatar :size="40" :src="picture.user?.userAvatar" class="author-avatar" />
            <div class="author-meta">
              <div class="author-name">{{ picture.user?.userName ?? '视界用户' }}</div>
              <div class="author-hint">查看主页</div>
            </div>
            <RightOutlined class="author-arrow" />
          </div>

          <!-- 名称 -->
          <h1 class="info-name">{{ picture.name ?? '未命名' }}</h1>

          <!-- 互动状态：点赞 / 收藏 -->
          <div class="interaction-row">
            <button
              class="interact-btn like"
              :class="{ active: likeStore.isLiked(picture.id) }"
              @click="doLike"
            >
              <HeartFilled v-if="likeStore.isLiked(picture.id)" />
              <HeartOutlined v-else />
              <span>{{ picture.likeCount ?? 0 }}</span>
            </button>
            <button
              class="interact-btn favorite"
              :class="{ active: likeStore.isFavorited(picture.id) }"
              @click="doFavorite"
            >
              <StarFilled v-if="likeStore.isFavorited(picture.id)" />
              <StarOutlined v-else />
              <span>{{ picture.favoriteCount ?? 0 }}</span>
            </button>
          </div>

          <!-- 简介 -->
          <p class="info-intro">{{ picture.introduction || '暂无简介' }}</p>

          <!-- 分类 / 标签 -->
          <div class="info-tags-row">
            <a-tag color="blue" class="cat-tag">{{ picture.category || '默认' }}</a-tag>
            <a-tag v-for="tag in picture.tags" :key="tag" class="tag-pill">
              {{ tag }}
            </a-tag>
          </div>

          <!-- 规格表 -->
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
              <dt>大小</dt>
              <dd>{{ formatSize(picture.picSize) }}</dd>
            </div>
            <div class="spec-row" v-if="picture.picColor">
              <dt>主色调</dt>
              <dd class="param-color">
                <span
                  v-if="picture.picColor"
                  class="color-dot"
                  :style="{ backgroundColor: toHexColor(picture.picColor) }"
                ></span>
                {{ picture.picColor }}
              </dd>
            </div>
            <div class="spec-row">
              <dt>发布</dt>
              <dd>{{ formatCreateTime(picture.createTime) }}</dd>
            </div>
          </dl>

          <!-- 操作区 -->
          <!-- 从消息通知进入（审核结果）：只保留「编辑」，引导修改后重新提交 -->
          <div v-if="fromNotify && canEdit" class="action-group">
            <a-button
              type="primary"
              size="large"
              block
              class="download-btn"
              @click="doEdit"
            >
              <EditOutlined />
              编辑图片
            </a-button>
            <p class="action-tip">修改保存后将重新提交审核</p>
          </div>
          <!-- 常规浏览：下载 / 分享 / 编辑 / 删除 -->
          <div v-else class="action-group">
            <a-button type="primary" size="large" block class="download-btn" @click="doDownload">
              <DownloadOutlined />
              免费下载
            </a-button>
            <div class="action-row">
              <a-button :icon="h(ShareAltOutlined)" @click="doShare">分享</a-button>
              <a-button v-if="canEdit" :icon="h(EditOutlined)" @click="doEdit">编辑</a-button>
              <a-button v-if="canDelete" :icon="h(DeleteOutlined)" danger @click="doDelete">
                删除
              </a-button>
            </div>
          </div>
        </div>
      </a-col>
    </a-row>

    <ShareModal ref="shareModalRef" :picture-id="picture.id" />
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { deletePictureUsingPost } from '@/api/pictureController.ts'
import { recordView } from '@/api/interaction.ts'
import { message } from 'ant-design-vue'
import {
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  HeartFilled,
  HeartOutlined,
  RightOutlined,
  ShareAltOutlined,
  StarFilled,
  StarOutlined,
} from '@ant-design/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { firstQueryValue, formatSize, toHexColor } from '@/utils'
import ShareModal from '@/components/ShareModal.vue'
import PictureCommentPanel from '@/components/PictureCommentPanel.vue'
import { usePictureDetail } from '@/composables/usePictureDetail.ts'
import { useLikeStore } from '@/stores/useLikeStore.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

interface Props {
  id: string | number
}

const props = defineProps<Props>()
const { picture, canEdit, canDelete, fetchDetail, download } = usePictureDetail()
const likeStore = useLikeStore()
const loginUserStore = useLoginUserStore()

onMounted(async () => {
  await fetchDetail(props.id)
  // 浏览历史：仅登录用户记录；失败静默（旁路数据，不打断查看）
  // id 保持字符串原样（雪花 id 超出 JS 安全整数，转 Number 会改末位）
  if (loginUserStore.loginUser?.id) {
    const pictureId = firstQueryValue(props.id)
    if (pictureId) {
      try {
        await recordView(pictureId)
      } catch {
        // 忽略
      }
    }
  }
})

// 点赞 / 收藏
const doLike = () => {
  likeStore.toggle(picture.value)
}
const doFavorite = () => {
  likeStore.toggleFavorite(picture.value)
}

const router = useRouter()
const route = useRoute()

// 是否从消息通知进入：审核结果场景下仅保留「编辑」操作
const fromNotify = computed(() => route.query.from === 'notify')

// 跳作者主页
const goUser = () => {
  if (picture.value.user?.id) {
    router.push(`/user/${picture.value.user.id}`)
  }
}

// 发布时间：年-月-日
const formatCreateTime = (value?: string) => {
  if (!value) return '-'
  const d = new Date(value)
  if (isNaN(d.getTime())) return '-'
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
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

// 删除数据
const doDelete = async () => {
  const id = picture.value.id
  if (!id) {
    return
  }
  const res = await deletePictureUsingPost({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
    // 删除后跳转回公共图库（空间图片由空间页管理）
    router.push({ path: '/' })
  } else {
    message.error('删除失败')
  }
}

// 下载图片
const doDownload = () => {
  download()
}

// ----- 分享操作 ----
const shareModalRef = ref()
// 分享（由 ShareModal 负责创建/查询后端分享链接）
const doShare = () => {
  if (shareModalRef.value) {
    shareModalRef.value.openModal()
  }
}
</script>

<style scoped>
#pictureDetailPage {
  /* 页面留白：限宽居中 + 左右内边距，避免内容贴边 */
  max-width: 1440px;
  margin: 0 auto 16px;
  padding: 0 12px;
}

/* ===== 预览：无卡片，图片直接坐在页面底色上 ===== */
.preview-wrap {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 320px;
  padding: 24px;
  border-radius: 16px;
  background:
    radial-gradient(ellipse at 30% 20%, rgba(64, 169, 255, 0.05), transparent 60%),
    var(--bg-card);
  border: 1px solid var(--border-color);
}

/* AI 标识：右下角小字标注，不用胶囊 */
.ai-flag {
  position: absolute;
  left: 16px;
  bottom: 14px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
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

/* 图片自适应：宽高同时约束，避免宽图横向溢出 */
.preview-image {
  min-width: 0;
  max-width: 100%;
  line-height: 0;
}

.preview-image :deep(img) {
  display: block;
  width: auto;
  height: auto;
  max-width: 100%;
  max-height: min(62vh, 560px);
  object-fit: contain;
  border-radius: 12px;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.18);
}

/* 预览 hover 遮罩 */
.preview-wrap :deep(.ant-image-mask) {
  border-radius: 12px;
}

/* ===== 信息列：排版式，无卡片 ===== */
/*
  信息列：sticky 跟随滚动。
  左列含预览 + 评论，高度往往远超右列；若右列静态排列，
  滚动到评论区时右侧会留下大片空白。sticky 让它保持可见。
  top 值 = 导航栏高度 + 呼吸间距。
*/
.info-col {
  display: flex;
  flex-direction: column;
  position: sticky;
  top: 84px;
  align-self: flex-start;
}

/* 窄屏（单列堆叠）时取消 sticky，避免与上方内容重叠 */
@media (max-width: 992px) {
  .info-col {
    position: static;
    margin-top: 24px;
  }
}

/* 作者 */
.info-author {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--border-color);
  margin-bottom: 18px;
  cursor: pointer;
}

.info-author:hover .author-arrow {
  transform: translateX(3px);
  color: var(--accent);
}

.author-avatar {
  flex-shrink: 0;
}

.author-meta {
  flex: 1;
  min-width: 0;
}

.author-name {
  font-size: 14.5px;
  font-weight: 600;
  color: var(--text-primary-light);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.author-hint {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-disabled);
}

.author-arrow {
  font-size: 12px;
  color: var(--text-disabled);
  transition: all 0.2s ease;
}

/* 标题：整列最大的字，建立层级 */
.info-name {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary-light);
  line-height: 1.35;
  letter-spacing: -0.01em;
  margin: 0 0 14px;
}

.info-intro {
  margin: 0 0 16px;
  font-size: 13.5px;
  line-height: 1.75;
  color: var(--text-secondary);
  padding-left: 12px;
  border-left: 2px solid var(--accent);
}

/* 分类与标签 */
.info-tags-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 18px;
}

.info-tags-row :deep(.ant-tag) {
  margin-inline-end: 0;
  border: none;
  background: rgba(0, 0, 0, 0.04);
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 22px;
  padding: 0 10px;
  border-radius: 6px;
}

html.dark .info-tags-row :deep(.ant-tag) {
  background: rgba(255, 255, 255, 0.07);
}

/* 规格表：两栏 label/value，细分隔线 */
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
  margin-right: 8px;
  border: 1px solid var(--border-color);
  flex-shrink: 0;
}

/* 操作区 */
.action-group {
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px solid var(--border-color);
}

/* 主按钮：实色 accent，不用渐变不用投影 */
.download-btn {
  border: none;
  background: var(--link);
  font-weight: 600;
}

.download-btn:hover {
  background: var(--accent);
}

.action-row {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.action-row .ant-btn {
  flex: 1;
}

/* 通知来源下的操作提示 */
.action-tip {
  margin: 10px 0 0;
  text-align: center;
  font-size: 12px;
  color: var(--text-disabled);
}

/* 互动按钮组：点赞 / 收藏 */
.interaction-row {
  display: flex;
  gap: 10px;
  margin: 12px 0 14px;
}

.interact-btn {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 38px;
  border-radius: 10px;
  border: 1px solid var(--border-color);
  background: transparent;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.interact-btn.like:hover {
  color: #ff4d6a;
  border-color: rgba(255, 77, 106, 0.5);
  background: rgba(255, 77, 106, 0.06);
}

.interact-btn.like.active {
  color: #ff4d6a;
  border-color: rgba(255, 77, 106, 0.55);
  background: rgba(255, 77, 106, 0.1);
}

.interact-btn.favorite:hover {
  color: #faad14;
  border-color: rgba(250, 173, 20, 0.5);
  background: rgba(250, 173, 20, 0.06);
}

.interact-btn.favorite.active {
  color: #faad14;
  border-color: rgba(250, 173, 20, 0.55);
  background: rgba(250, 173, 20, 0.1);
}

/* 评论区卡片 */
/*
  评论区：与预览同处左列，左右边界严格对齐（不再整行撑开）。
  与预览之间留 24px，视觉上分为上下两个区块。
  内部不再限制宽度 —— 列宽本身已是合理阅读宽度。
*/
.comment-section {
  margin-top: 24px;
  padding: 20px 24px 18px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 16px;
}

/* ===== 深色模式 ===== */
html.dark .preview-wrap {
  background:
    radial-gradient(ellipse at 30% 20%, rgba(64, 169, 255, 0.09), transparent 60%),
    var(--bg-card);
}

html.dark .info-tags-row :deep(.ant-tag) {
  background: rgba(255, 255, 255, 0.07);
}

html.dark .info-name,
html.dark .author-name {
  color: var(--text-primary);
}
</style>

<!-- 全屏预览层：渲染在 body 下，scoped 不生效，需全局块 -->
<style>
/* 预览遮罩：加深底色，让图片在放大查看时更聚焦 */
.preview-mask {
  background: rgba(0, 0, 0, 0.72) !important;
  backdrop-filter: blur(4px);
}

/* 放大后的图片限制在视口内，避免超出屏幕 */
.preview-mask .ant-image-preview-img {
  max-width: 92vw;
  max-height: 90vh;
  border-radius: 8px;
}
</style>

