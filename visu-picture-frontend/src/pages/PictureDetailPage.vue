<template>
  <div id="pictureDetailPage">
    <a-row :gutter="[24, 24]">
      <!-- 图片预览 -->
      <a-col :sm="24" :md="16" :xl="18">
        <div class="preview-card">
          <div class="preview-head">
            <span class="preview-title">图片预览</span>
            <a-tag v-if="picture.isAiGenerated === 1" color="purple" class="ai-tag">
              AI 生成
            </a-tag>
          </div>
          <div class="preview-body">
            <a-image
              :src="picture.url"
              class="preview-image"
              :preview="{
                maskClassName: 'preview-mask',
              }"
            />
          </div>
        </div>
      </a-col>

      <!-- 图片信息区域 -->
      <a-col :sm="24" :md="8" :xl="6">
        <div class="info-card">
          <!-- 作者 -->
          <div class="info-author" @click="goUser">
            <a-avatar :size="40" :src="picture.user?.userAvatar" class="author-avatar" />
            <div class="author-meta">
              <div class="author-name">{{ picture.user?.userName ?? '视界用户' }}</div>
              <div class="author-hint">点击查看主页</div>
            </div>
            <RightOutlined class="author-arrow" />
          </div>

          <!-- 名称 -->
          <div class="info-name">{{ picture.name ?? '未命名' }}</div>

          <!-- 简介 -->
          <p class="info-intro">{{ picture.introduction || '暂无简介' }}</p>

          <!-- 分类 / 标签 -->
          <div class="info-tags-row">
            <a-tag color="blue" class="cat-tag">{{ picture.category || '默认' }}</a-tag>
            <a-tag v-for="tag in picture.tags" :key="tag" class="tag-pill">
              {{ tag }}
            </a-tag>
          </div>

          <a-divider class="info-divider" />

          <!-- 参数网格 -->
          <div class="param-grid">
            <div class="param-item">
              <div class="param-label">格式</div>
              <div class="param-value">{{ picture.picFormat ?? '-' }}</div>
            </div>
            <div class="param-item">
              <div class="param-label">尺寸</div>
              <div class="param-value">{{ picture.picWidth ?? '-' }} × {{ picture.picHeight ?? '-' }}</div>
            </div>
            <div class="param-item">
              <div class="param-label">宽高比</div>
              <div class="param-value">{{ picture.picScale ?? '-' }}</div>
            </div>
            <div class="param-item">
              <div class="param-label">大小</div>
              <div class="param-value">{{ formatSize(picture.picSize) }}</div>
            </div>
            <div class="param-item">
              <div class="param-label">主色调</div>
              <div class="param-value param-color">
                <span
                  v-if="picture.picColor"
                  class="color-dot"
                  :style="{ backgroundColor: toHexColor(picture.picColor) }"
                ></span>
                {{ picture.picColor ?? '-' }}
              </div>
            </div>
            <div class="param-item">
              <div class="param-label">发布时间</div>
              <div class="param-value">{{ formatCreateTime(picture.createTime) }}</div>
            </div>
          </div>

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
    <ShareModal ref="shareModalRef" :link="shareLink" />
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { deletePictureUsingPost } from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import {
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  RightOutlined,
  ShareAltOutlined,
} from '@ant-design/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { formatSize, toHexColor } from '@/utils'
import ShareModal from '@/components/ShareModal.vue'
import { usePictureDetail } from '@/composables/usePictureDetail.ts'

interface Props {
  id: string | number
}

const props = defineProps<Props>()
const { picture, canEdit, canDelete, fetchDetail, download, shareLink, setShareLink } =
  usePictureDetail()

onMounted(() => {
  fetchDetail(props.id)
})

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
// 分享
const doShare = () => {
  setShareLink()
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

/* ===== 预览卡 ===== */
.preview-card {
  background: var(--bg-card);
  border-radius: 16px;
  border: 1px solid var(--border-color);
  box-shadow: var(--card-shadow);
  overflow: hidden;
}

.preview-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 20px;
  border-bottom: 1px solid var(--border-color);
}

.preview-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary-light);
}

.ai-tag {
  margin-inline-end: 0;
}

.preview-body {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 280px;
  padding: 32px;
  background:
    radial-gradient(ellipse at 30% 20%, rgba(64, 169, 255, 0.06), transparent 60%),
    radial-gradient(ellipse at 70% 80%, rgba(22, 119, 255, 0.05), transparent 60%),
    var(--bg-body);
}

/* 图片自适应：宽高同时约束，避免宽图横向溢出被卡片裁切 */
.preview-image {
  /* flex 子项默认 min-width:auto 会阻止收缩，置 0 让超宽图能被压缩 */
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
.preview-body :deep(.ant-image-mask) {
  border-radius: 12px;
}

/* ===== 信息卡 ===== */
.info-card {
  background: var(--bg-card);
  border-radius: 16px;
  border: 1px solid var(--border-color);
  box-shadow: var(--card-shadow);
  padding: 20px;
}

/* 作者 */
.info-author {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  margin: -6px -8px 14px;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.2s ease;
}

.info-author:hover {
  background: rgba(64, 169, 255, 0.07);
}

.info-author:hover .author-arrow {
  transform: translateX(3px);
  color: var(--accent);
}

.author-avatar {
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
}

.author-meta {
  flex: 1;
  min-width: 0;
}

.author-name {
  font-size: 15px;
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

/* 名称与简介 */
.info-name {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary-light);
  line-height: 26px;
}

.info-intro {
  margin: 8px 0 14px;
  font-size: 13px;
  line-height: 20px;
  color: var(--text-secondary);
}

/* 分类与标签 */
.info-tags-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.cat-tag {
  margin-inline-end: 0;
}

.tag-pill {
  margin-inline-end: 0;
}

.info-divider {
  margin: 16px 0;
  border-top-color: var(--border-color);
}

/* 参数网格 */
.param-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 12px;
}

.param-label {
  font-size: 12px;
  color: var(--text-disabled);
  margin-bottom: 3px;
}

.param-value {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-primary-light);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.param-color {
  display: flex;
  align-items: center;
  gap: 7px;
}

.color-dot {
  width: 14px;
  height: 14px;
  border-radius: 4px;
  border: 1px solid var(--border-color);
  flex-shrink: 0;
}

/* 操作区 */
.action-group {
  margin-top: 20px;
}

.download-btn {
  border: none;
  background: linear-gradient(135deg, #40a9ff, #1677ff);
  box-shadow: 0 6px 18px rgba(22, 119, 255, 0.35);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.download-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 26px rgba(22, 119, 255, 0.45);
}

.download-btn:active {
  transform: translateY(0);
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

/* ===== 深色模式 ===== */
html.dark .preview-card,
html.dark .info-card {
  box-shadow: var(--card-shadow);
}

html.dark .preview-body {
  background:
    radial-gradient(ellipse at 30% 20%, rgba(64, 169, 255, 0.09), transparent 60%),
    radial-gradient(ellipse at 70% 80%, rgba(22, 119, 255, 0.07), transparent 60%),
    #1d1d1f;
}

html.dark .info-author:hover {
  background: rgba(64, 169, 255, 0.12);
}

html.dark .info-name,
html.dark .author-name,
html.dark .param-value {
  color: var(--text-primary);
}

html.dark .preview-title {
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

