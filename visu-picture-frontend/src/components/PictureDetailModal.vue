<template>
  <a-modal
    v-model:open="visible"
    :width="1040"
    :footer="null"
    centered
    destroy-on-close
    wrap-class-name="picture-detail-modal"
  >
    <template #title>
      <div class="modal-title">
        <PictureOutlined />
        <span class="title-text">{{ picture.name ?? '图片详情' }}</span>
      </div>
    </template>
    <a-spin :spinning="loading">
      <div class="detail-body">
        <!-- 左：图片预览（点击可全屏查看） -->
        <div class="preview-area">
          <a-image v-if="picture.url" :src="picture.url" class="preview-img" />
        </div>
        <!-- 右：图片信息 -->
        <div class="info-area">
          <!-- 作者 -->
          <div class="author-row">
            <a-avatar :size="36" :src="picture.user?.userAvatar">
              <template #icon><UserOutlined /></template>
            </a-avatar>
            <div class="author-info">
              <div class="author-name">{{ picture.user?.userName ?? '未知用户' }}</div>
              <div v-if="formatDate(picture.createTime)" class="author-tip">
                上传于 {{ formatDate(picture.createTime) }}
              </div>
            </div>
          </div>
          <!-- 简介 -->
          <div v-if="picture.introduction" class="intro-block">
            {{ picture.introduction }}
          </div>
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
          <!-- 操作按钮 -->
          <div class="action-area">
            <a-button type="primary" @click="doDownload">
              <template #icon><DownloadOutlined /></template>
              免费下载
            </a-button>
            <a-button type="primary" ghost @click="doShare">
              <template #icon><ShareAltOutlined /></template>
              分享
            </a-button>
            <a-button v-if="canEdit" @click="doEdit">
              <template #icon><EditOutlined /></template>
              编辑
            </a-button>
            <a-button v-if="canDelete" danger @click="doDelete">
              <template #icon><DeleteOutlined /></template>
              删除
            </a-button>
          </div>
        </div>
      </div>
    </a-spin>
    <ShareModal ref="shareModalRef" :link="shareLink" />
  </a-modal>
</template>

<script setup lang="ts">
import { computed, createVNode, ref } from 'vue'
import { Modal, message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import {
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  ExclamationCircleOutlined,
  PictureOutlined,
  ShareAltOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { deletePictureUsingPost, getPictureVoByIdUsingGet } from '@/api/pictureController.ts'
import { downloadImage, formatSize, toHexColor } from '@/utils'
import ShareModal from '@/components/ShareModal.vue'
import { SPACE_PERMISSION_ENUM } from '@/constants/space.ts'

const emit = defineEmits<{
  (e: 'deleted'): void
}>()

const visible = ref(false)
const loading = ref(false)
const picture = ref<API.PictureVO>({})

// 通用权限检查函数
function createPermissionChecker(permission: string) {
  return computed(() => {
    return (picture.value.permissionList ?? []).includes(permission)
  })
}

// 定义权限检查
const canEdit = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_EDIT)
const canDelete = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_DELETE)

// 获取图片详情
const fetchPictureDetail = async (id: string | number) => {
  try {
    const res = await getPictureVoByIdUsingGet({ id })
    if (res.data.code === 0 && res.data.data) {
      picture.value = res.data.data
    } else {
      message.error('获取图片详情失败，' + res.data.message)
      visible.value = false
    }
  } catch (e: any) {
    message.error('获取图片详情失败：' + e.message)
    visible.value = false
  } finally {
    loading.value = false
  }
}

// 打开弹窗（传入图片 id，弹窗内自行拉取完整详情与权限）
const openModal = (id?: string | number) => {
  if (!id) return
  visible.value = true
  loading.value = true
  picture.value = {}
  fetchPictureDetail(id)
}

defineExpose({ openModal })

const router = useRouter()

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

// 删除数据（弹窗内二次确认，删除成功后通知父组件刷新）
const doDelete = () => {
  const id = picture.value.id
  if (!id) {
    return
  }
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

// 下载图片
const doDownload = () => {
  downloadImage(picture.value.url)
}

// ----- 分享操作 ----
const shareModalRef = ref()
// 分享链接
const shareLink = ref<string>()
// 分享
const doShare = () => {
  shareLink.value = `${window.location.protocol}//${window.location.host}/picture/${picture.value.id}`
  if (shareModalRef.value) {
    shareModalRef.value.openModal()
  }
}

// 时间格式化：兼容 ISO（带 T）与空格分隔两种格式，转为本地时间
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
.modal-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
}

.modal-title :deep(.anticon) {
  color: #3d5af5;
}

.detail-body {
  display: flex;
  gap: 20px;
}

/* 左侧预览区：棋盘格底衬，突出透明图 */
.preview-area {
  flex: 1;
  min-width: 0;
  min-height: 420px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  border-radius: 12px;
  background-color: #f3f5fa;
  background-image:
    linear-gradient(45deg, rgba(35, 44, 86, 0.05) 25%, transparent 25%, transparent 75%, rgba(35, 44, 86, 0.05) 75%),
    linear-gradient(45deg, rgba(35, 44, 86, 0.05) 25%, transparent 25%, transparent 75%, rgba(35, 44, 86, 0.05) 75%);
  background-size: 20px 20px;
  background-position:
    0 0,
    10px 10px;
}

.preview-area :deep(.ant-image) {
  max-width: 100%;
  display: flex;
  justify-content: center;
}

.preview-area :deep(img) {
  max-width: 100%;
  max-height: 62vh;
  object-fit: contain;
  border-radius: 8px;
  box-shadow: 0 8px 28px rgba(24, 39, 92, 0.16);
}

/* 右侧信息区 */
.info-area {
  width: 300px;
  flex-shrink: 0;
  max-height: 66vh;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.author-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.author-name {
  font-weight: 600;
  color: #171a2b;
}

.author-tip {
  font-size: 12px;
  color: rgba(35, 44, 86, 0.45);
}

.intro-block {
  font-size: 13px;
  line-height: 1.7;
  color: rgba(35, 44, 86, 0.75);
  background: rgba(61, 90, 245, 0.05);
  border-radius: 10px;
  padding: 10px 12px;
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

.action-area {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding-top: 4px;
  border-top: 1px dashed rgba(35, 44, 86, 0.12);
}

/* 深色模式 */
html.dark .author-name,
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
  background-image:
    linear-gradient(45deg, rgba(255, 255, 255, 0.04) 25%, transparent 25%, transparent 75%, rgba(255, 255, 255, 0.04) 75%),
    linear-gradient(45deg, rgba(255, 255, 255, 0.04) 25%, transparent 25%, transparent 75%, rgba(255, 255, 255, 0.04) 75%);
}

html.dark .action-area {
  border-top-color: rgba(255, 255, 255, 0.1);
}
</style>
