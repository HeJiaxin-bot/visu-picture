<template>
  <div id="addPicturePage">
    <!-- 页头 -->
    <header class="page-header">
      <h2 class="page-title">{{ isEditMode ? '修改图片' : '创建图片' }}</h2>
      <p class="page-sub">
        <template v-if="spaceId">
          保存至空间
          <router-link class="space-link" :to="`/space/${spaceId}`">
            {{ space?.spaceName || spaceId }}
          </router-link>
        </template>
        <template v-else>保存至公共图库，审核通过后展示在首页</template>
      </p>
      <p class="page-note">
        支持 JPG / PNG / WEBP，单张不超过 10 MB
        <span class="note-sep">·</span>
        <router-link v-if="!isEditMode" class="batch-entry" :to="batchUploadPath">
          批量上传多张图片
        </router-link>
      </p>
    </header>

    <div class="layout">
      <!-- 左栏：上传 / 预览 / 编辑 -->
      <section class="card stage-card">
        <div class="stage-head">
          <span class="stage-status" :class="`is-${stageStatus.key}`">
            <i class="stage-dot" />
            {{ stageStatus.text }}
          </span>
        </div>

        <div class="stage-frame">
          <PictureUpload
            ref="pictureUploadRef"
            defer-upload
            :picture="picture"
            :spaceId="spaceId"
            :onSuccess="onSuccess"
          />
        </div>

        <!-- 未上传时提供 URL 导入入口（用 v-show 保持组件挂载，否则组件被卸载后会丢失待抓取的 URL） -->
        <div v-show="!picture?.url" class="url-import">
          <div class="upload-divider"><span>或通过图片链接导入</span></div>
          <UrlPictureUpload
            ref="urlPictureUploadRef"
            defer-upload
            :picture="picture"
            :spaceId="spaceId"
            :onSuccess="onSuccess"
          />
        </div>

        <!-- 图片编辑 -->
        <div v-if="picture" class="edit-bar">
          <a-button class="tool-btn" :icon="h(EditOutlined)" @click="doEditPicture">
            编辑图片
          </a-button>
          <a-button
            class="tool-btn tool-btn-primary"
            type="primary"
            :icon="h(FullscreenOutlined)"
            @click="doImagePainting"
          >
            AI 扩图
          </a-button>
        </div>
        <ImageCropper
          ref="imageCropperRef"
          :imageUrl="picture?.url"
          :picture="picture"
          :spaceId="spaceId"
          :space="space"
          :onSuccess="onCropSuccess"
        />
        <ImageOutPainting
          ref="imageOutPaintingRef"
          :picture="picture"
          :spaceId="spaceId"
          :onSuccess="onImageOutPaintingSuccess"
        />
      </section>

      <!-- 右栏：图片信息 -->
      <aside class="card info-card">
        <div class="info-head">
          <div class="info-title">图片信息</div>
          <a-button
            v-if="picture"
            type="text"
            size="small"
            class="ai-btn"
            :icon="h(ThunderboltOutlined)"
            :loading="aiEditLoading"
            :disabled="aiEditLoading"
            @click="doAiEdit"
          >
            AI 配文
          </a-button>
        </div>
        <template v-if="picture">
          <!-- 图片客观信息：一行浅灰小字 -->
          <div class="meta-line">
            {{ picture.picFormat || stripFallback }} ·
            {{ picture.picWidth && picture.picHeight ? `${picture.picWidth}×${picture.picHeight}` : stripFallback }} ·
            {{ picture.picSize ? formatPicSize(picture.picSize) : stripFallback }}
          </div>

          <a-form
            name="pictureForm"
            layout="vertical"
            :model="pictureForm"
            @finish="handleSubmit"
            class="info-form"
          >
            <div class="plain-row">
              <a-input v-model:value="pictureForm.name" placeholder="给图片起个名字吧" />
            </div>

            <div class="plain-row">
              <a-textarea
                v-model:value="pictureForm.introduction"
                placeholder="补充一句介绍，让大家更了解这张图"
                :auto-size="{ minRows: 3, maxRows: 5 }"
              />
            </div>

            <PictureMetaPicker
              v-model:category="pictureForm.category"
              v-model:tags="pictureForm.tags"
            />

            <div class="submit-row">
              <a-button type="primary" html-type="submit" class="submit-btn" :disabled="aiEditLoading">
                {{ isEditMode ? '保存修改' : '创建' }}
              </a-button>
            </div>
          </a-form>
        </template>

        <!-- 未选择图片时的引导 -->
        <div v-else class="info-empty">
          <div class="empty-art" />
          <div class="empty-title">还没有选择图片</div>
          <div class="empty-sub">在左侧上传或导入一张图片后，即可填写名称、分类与标签</div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import PictureUpload from '@/components/PictureUpload.vue'
import { computed, h, onMounted, onUnmounted, reactive, ref, watchEffect } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  aiEditPictureUsingPost,
  deletePictureUsingPost,
  editPictureUsingPost,
  getPictureVoByIdUsingGet,
} from '@/api/pictureController.ts'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import UrlPictureUpload from '@/components/UrlPictureUpload.vue'
import ImageCropper from '@/components/ImageCropper.vue'
import PictureMetaPicker from '@/components/PictureMetaPicker.vue'
import { EditOutlined, FullscreenOutlined, ThunderboltOutlined } from '@ant-design/icons-vue'
import ImageOutPainting from '@/components/ImageOutPainting.vue'
import { getSpaceVoByIdUsingGet } from '@/api/spaceController.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

const router = useRouter()
const route = useRoute()

const picture = ref<API.PictureVO>()
const pictureForm = reactive<API.PictureEditRequest>({})
// 上传组件引用（延迟上传模式下由本页在提交前触发真正上传）
const pictureUploadRef = ref()
const urlPictureUploadRef = ref()
// 进入页面时已存在的图片 id（编辑模式），用于区分「新上传的图片」与「原有图片」
const initialPictureId = ref<string | number>()
// 本次会话中新上传、尚未提交的图片 id：离开页面时需要清理，避免留下孤儿图片
const uploadedPictureId = ref<string | number>()

// 是否编辑模式（带图片 id 进入）
const isEditMode = computed(() => !!route.query?.id)

// 左栏画布状态：未选择 / 已选待提交（延迟上传） / 已上传
const stageStatus = computed(() => {
  if (!picture.value?.url) {
    return { key: 'idle', text: '等待选择图片' }
  }
  if (!picture.value.id) {
    return { key: 'pending', text: '待提交上传' }
  }
  return { key: 'ready', text: '已上传' }
})

// 参数条占位文案：本地预览尚未上传时提示「待上传」
const stripFallback = computed(() => (stageStatus.value.key === 'pending' ? '待上传' : '—'))

/** 图片体积格式化 */
const formatPicSize = (size: number) => {
  return size >= 1024 * 1024
    ? `${(size / 1024 / 1024).toFixed(2)} MB`
    : `${(size / 1024).toFixed(1)} KB`
}
// 空间 id
const spaceId = computed(() => {
  return route.query?.spaceId
})

// 批量上传入口（带上当前空间，保持上传目标一致）
const batchUploadPath = computed(() =>
  spaceId.value ? `/add_picture/batch-upload?spaceId=${spaceId.value}` : '/add_picture/batch-upload',
)

/**
 * 图片上传成功
 * @param newPicture
 */
const onSuccess = (newPicture: API.PictureVO) => {
  if (newPicture.id) {
    // 编辑模式下原地替换文件时返回的是原有图片 id，不应清理
    if (String(newPicture.id) !== String(initialPictureId.value)) {
      // 之前还有未提交的新图片时先清理掉
      const lastUploadedId = uploadedPictureId.value
      if (lastUploadedId && String(lastUploadedId) !== String(newPicture.id)) {
        deletePictureUsingPost({ id: lastUploadedId as number }).catch(() => {})
      }
      uploadedPictureId.value = newPicture.id
    }
  }
  picture.value = newPicture
  // 仅在名称为空时用文件名兜底，避免覆盖用户填写或 AI 生成的内容
  if (!pictureForm.name) {
    pictureForm.name = newPicture.name
  }
}

/**
 * 确保图片已上传：延迟上传模式下，点提交或使用 AI 功能前才真正上传
 */
const ensureUploaded = async (): Promise<boolean> => {
  // 本地文件优先，其次是通过链接导入的图片
  if (pictureUploadRef.value?.hasPendingFile?.()) {
    const uploaded = await pictureUploadRef.value.upload()
    return !!uploaded?.id
  }
  if (urlPictureUploadRef.value?.hasPendingUrl?.()) {
    const uploaded = await urlPictureUploadRef.value.upload()
    return !!uploaded?.id
  }
  if (!picture.value?.id) {
    message.warning('请先选择图片，或粘贴图片链接后点击抓取')
    return false
  }
  return true
}

/**
 * 清理本次会话中上传但未提交的图片（避免 COS 与数据库残留孤儿数据）
 * @param keepalive 页面即将卸载/关闭时使用，保证请求能发出
 */
const cleanupUploadedPicture = (keepalive = false) => {
  const id = uploadedPictureId.value
  if (!id) {
    return
  }
  uploadedPictureId.value = undefined
  if (keepalive) {
    // 刷新 / 关闭页面时用 keepalive 请求兜底
    const baseURL = import.meta.env.DEV ? 'http://localhost:8123' : ''
    fetch(`${baseURL}/api/picture/delete`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id }),
      credentials: 'include',
      keepalive: true,
    }).catch(() => {})
    return
  }
  deletePictureUsingPost({ id: id as number }).catch(() => {})
}

// 离开页面时清理未提交的图片
onBeforeRouteLeave(() => {
  cleanupUploadedPicture()
})

onUnmounted(() => {
  cleanupUploadedPicture()
})

const onBeforeUnload = () => {
  cleanupUploadedPicture(true)
}

onMounted(() => {
  window.addEventListener('beforeunload', onBeforeUnload)
})

onUnmounted(() => {
  window.removeEventListener('beforeunload', onBeforeUnload)
})

/** 提交表单 */
const handleSubmit = async () => {
  // AI 配文进行中不允许提交，避免拿到旧的配文内容或并发冲突
  if (aiEditLoading.value) {
    return
  }
  // 延迟上传：点击创建/保存时才真正上传图片
  if (!(await ensureUploaded())) {
    return
  }
  const pictureId = picture.value?.id
  if (!pictureId) {
    return
  }
  // 表单未使用 a-form-item，@finish 回传的 values 为空，直接取 pictureForm 的当前值
  const res = await editPictureUsingPost({
    id: pictureId,
    spaceId: spaceId.value,
    name: pictureForm.name,
    introduction: pictureForm.introduction,
    category: pictureForm.category,
    tags: pictureForm.tags,
  })
  // 操作成功
  if (res.data.code === 0 && res.data.data) {
    // 已提交成功，无需再清理
    uploadedPictureId.value = undefined
    const isAdmin = useLoginUserStore().loginUser.userRole === 'admin'
    if (isAdmin) {
      // 管理员上传直接过审
      message.success('创建成功')
    } else {
      // 普通用户上传（公共图库 / 空间）都进入待审核状态
      Modal.success({
        title: '上传完成',
        content: spaceId.value
          ? '待管理员审核通过后在空间展示'
          : '待管理员审核通过后在首页展示',
        centered: true,
        okText: '知道了',
      })
    }
    if (spaceId.value) {
      // 上传到空间（私人/团队空间）：返回对应空间详情页
      router.push(`/space/${spaceId.value}`)
      return
    }
    // 跳转到公共图库首页
    router.push('/')
  } else {
    message.error('创建失败，' + res.data.message)
  }
}

// 获取老数据
const getOldPicture = async () => {
  // 获取到 id
  const id = route.query?.id
  if (id) {
    const res = await getPictureVoByIdUsingGet({
      id,
    })
    if (res.data.code === 0 && res.data.data) {
      const data = res.data.data
      picture.value = data
      initialPictureId.value = data.id
      pictureForm.name = data.name
      pictureForm.introduction = data.introduction
      pictureForm.category = data.category
      pictureForm.tags = data.tags
    }
  }
}

onMounted(() => {
  getOldPicture()
})

// ----- 图片编辑器引用 ------
const imageCropperRef = ref()

// 编辑图片
const doEditPicture = async () => {
  // 延迟上传：先上传再编辑（图片编辑器依赖已上传的图片）
  if (!(await ensureUploaded())) {
    return
  }
  imageCropperRef.value?.openModal()
}

// 编辑成功事件
const onCropSuccess = (newPicture: API.PictureVO) => {
  picture.value = newPicture
}

// ----- AI 扩图引用 -----
const imageOutPaintingRef = ref()

// 打开 AI 扩图弹窗
const doImagePainting = async () => {
  // 延迟上传：先上传再扩图（扩图依赖已上传的图片）
  if (!(await ensureUploaded())) {
    return
  }
  imageOutPaintingRef.value?.openModal()
}

// AI 扩图保存事件
const onImageOutPaintingSuccess = (newPicture: API.PictureVO) => {
  picture.value = newPicture
}

// ----- AI 配文 -----
const aiEditLoading = ref(false)

/**
 * AI 智能配文：自动生成简介、分类、标签并回填表单
 */
const doAiEdit = async () => {
  // 防重复点击：AI 调用耗时较长，避免并发触发多次请求
  if (aiEditLoading.value) {
    return
  }
  aiEditLoading.value = true
  try {
    // 延迟上传：AI 配文依赖已上传的图片，先完成上传
    if (!(await ensureUploaded())) {
      return
    }
    const pictureId = picture.value?.id
    if (!pictureId) {
      message.warning('请先上传图片')
      return
    }
    // AI 调用耗时较长，延长超时时间
    const res = await aiEditPictureUsingPost({ pictureId }, { timeout: 60000 })
    if (res.data.code === 0 && res.data.data) {
      const { name, introduction, category, tags } = res.data.data
      if (name) {
        pictureForm.name = name
      }
      if (introduction) {
        pictureForm.introduction = introduction
      }
      if (category) {
        pictureForm.category = category
      }
      if (tags?.length) {
        pictureForm.tags = tags
      }
      message.success('AI 配文完成，请确认后保存')
    } else {
      message.error('AI 配文失败，' + res.data.message)
    }
  } finally {
    aiEditLoading.value = false
  }
}

// 获取空间信息
const space = ref<API.SpaceVO>()

// 获取空间信息
const fetchSpace = async () => {
  // 获取数据
  if (spaceId.value) {
    const res = await getSpaceVoByIdUsingGet({
      id: spaceId.value,
    })
    if (res.data.code === 0 && res.data.data) {
      space.value = res.data.data
    }
  }
}

watchEffect(() => {
  fetchSpace()
})
</script>

<style scoped>
#addPicturePage {
  max-width: 1180px;
  margin: 0 auto;
  padding-bottom: 32px;
}

/* ---------- 页头 ---------- */
.page-header {
  padding-bottom: 18px;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--border-color);
  animation: rise 0.28s ease-out both;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  line-height: 1.3;
  color: var(--text-primary-light);
}

.page-sub {
  margin: 8px 0 0;
  font-size: 13.5px;
  color: var(--text-secondary);
}

.page-note {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--text-disabled);
}

.note-sep {
  margin: 0 6px;
}

.batch-entry {
  color: var(--accent);
}

.batch-entry:hover {
  text-decoration: underline;
}

.space-link {
  font-weight: 500;
  color: var(--accent);
}

/* ---------- 版式 ---------- */
.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 400px;
  gap: 20px;
  align-items: stretch;
}

.card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 14px;
}

/* ---------- 左栏：画布 ---------- */
.stage-card {
  min-width: 0;
  padding: 18px 20px 20px;
  animation: rise 0.28s ease-out both;
}

.stage-head {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.stage-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-disabled);
}

.stage-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.stage-status.is-pending {
  color: #d48806;
}

.stage-status.is-ready {
  color: #389e0d;
}

.stage-frame {
  padding: 12px;
  border-radius: 12px;
  background: var(--bg-body);
}

.upload-divider {
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 18px 0 14px;
  color: var(--text-disabled);
  font-size: 12px;
  white-space: nowrap;
}

.upload-divider::before,
.upload-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--border-color);
}

/* ---------- 右栏：图片信息 ---------- */
.info-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 0 20px 20px;
  animation: rise 0.28s ease-out both;
}

.info-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 0 6px;
}

.info-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary-light);
}

.ai-btn {
  padding-right: 0;
  color: var(--accent);
}

/* 图片客观信息：一行浅灰小字 */
.meta-line {
  padding-bottom: 14px;
  font-size: 12px;
  letter-spacing: 0.2px;
  color: var(--text-disabled);
}

/* 字段行：左侧彩色图标 + 名称，右侧无边框输入 */
.info-form {
  display: flex;
  flex-direction: column;
  flex: 1;
}

/* 名称 / 简介：不显示标签，直接用占位文字提示 */
.plain-row {
  padding: 14px 0;
  border-bottom: 1px solid var(--border-color);
}

/* 输入控件：无边框，仅靠占位文字与留白区分 */
.info-form :deep(.ant-input) {
  width: 100%;
  padding: 0 !important;
  border: none !important;
  background: transparent !important;
  box-shadow: none !important;
  font-size: 14px;
  color: var(--text-primary-light);
}

.info-form :deep(.ant-input::placeholder) {
  color: var(--text-disabled);
}

.info-form :deep(textarea.ant-input) {
  padding: 2px 0 !important;
  line-height: 1.7;
  resize: none;
}

/* 未选图时的引导状态：在剩余空间里垂直居中，与左栏底部齐平 */
.info-empty {
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: center;
  padding: 24px 6px;
  text-align: center;
}

.empty-art {
  width: 68px;
  height: 68px;
  margin: 0 auto 18px;
  border: 1px dashed var(--border-color);
  border-radius: 16px;
  background: var(--bg-body);
}

.empty-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary-light);
}

.empty-sub {
  margin-top: 7px;
  font-size: 12.5px;
  line-height: 1.65;
  color: var(--text-secondary);
}

/* 编辑工具条：左对齐、等宽按钮，只留一条浅分割线 */
.edit-bar {
  display: flex;
  gap: 10px;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--border-color);
}

.edit-bar :deep(.ant-btn) {
  flex: 1;
  height: 38px;
  border-radius: 9px;
  font-weight: 400;
}

/* 提交区：按钮落在卡片底部，与左栏底边齐平 */
.submit-row {
  margin-top: auto;
  padding-top: 18px;
}

.submit-btn {
  width: 100%;
  height: 46px;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 500;
  box-shadow: none;
}

/* 入场动画 */
@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .page-header,
  .stage-card,
  .info-card {
    animation: none;
  }
}

/* 深色模式适配 */
html.dark .card {
  background: rgba(255, 255, 255, 0.05);
  border-color: rgba(255, 255, 255, 0.09);
}

html.dark .info-form :deep(.ant-input) {
  border: none !important;
  background: transparent !important;
}

html.dark .empty-art {
  border-color: rgba(255, 255, 255, 0.12);
  background: rgba(255, 255, 255, 0.04);
}

html.dark .stage-status.is-pending {
  color: #ffc53d;
}

html.dark .stage-status.is-ready {
  color: #95de64;
}

/* 小屏单栏 */
@media (max-width: 1024px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
