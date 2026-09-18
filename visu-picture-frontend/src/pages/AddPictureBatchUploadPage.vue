<template>
  <div id="addPictureBatchUploadPage">
    <!-- 页头 -->
    <header class="page-header">
      <h2 class="page-title">批量上传</h2>
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
        单次最多 {{ MAX_BATCH_COUNT }} 张，支持 JPG / PNG / WEBP，单张不超过 10 MB
      </p>
    </header>

    <div class="layout">
      <!-- 左栏：选择图片 + 待上传列表 -->
      <section class="card stage-card">
        <a-upload
          class="batch-drop"
          :multiple="true"
          :show-upload-list="false"
          :before-upload="addFiles"
          :disabled="uploading"
        >
          <div class="drop-inner">
            <CloudUploadOutlined class="drop-icon" />
            <div class="drop-text">点击或拖拽批量选择图片</div>
            <div class="drop-sub">
              已选 {{ fileList.length }} / {{ MAX_BATCH_COUNT }} 张
            </div>
          </div>
        </a-upload>

        <!-- 多行 URL 导入 -->
        <div class="url-block">
          <a-textarea
            v-model:value="urlText"
            class="url-textarea"
            placeholder="也可以粘贴多个图片链接，每行一个，然后点「导入链接」"
            :auto-size="{ minRows: 2, maxRows: 4 }"
            :disabled="uploading"
          />
          <div class="url-actions">
            <span class="url-hint">支持 http/https 公开图片地址</span>
            <a-button size="small" :disabled="uploading" @click="importUrls">导入链接</a-button>
          </div>
        </div>

        <!-- 待上传列表 -->
        <div v-if="fileList.length" class="list-head">
          <span class="list-title">待上传</span>
          <a-button type="text" size="small" :disabled="uploading" @click="clearAll">清空</a-button>
        </div>
        <div v-if="fileList.length" class="file-list">
          <div v-for="item in fileList" :key="item.uid" class="file-row">
            <img class="file-thumb" :src="item.previewUrl" alt="" />
            <div class="file-main">
              <input
                v-model="item.name"
                class="file-name"
                placeholder="给图片起个名字"
                :disabled="uploading || item.status === 'done'"
              />
              <div class="file-sub">
                <span class="file-state" :class="`is-${item.status}`">{{ stateText(item) }}</span>
                <span v-if="item.error" class="file-error">{{ item.error }}</span>
              </div>
            </div>
            <a-button
              v-if="item.status === 'failed'"
              type="text"
              size="small"
              :disabled="uploading"
              @click="retryOne(item)"
            >
              重试
            </a-button>
            <CloseOutlined
              v-if="item.status !== 'done'"
              class="file-remove"
              @click="removeOne(item)"
            />
            <CheckCircleFilled v-else class="file-done" />
          </div>
        </div>
        <div v-else class="list-empty">还没有选择图片，从上方批量选择本地文件或导入图片链接</div>
      </section>

      <!-- 右栏：统一信息 -->
      <aside class="card info-card">
        <div class="info-head">
          <div class="info-title">统一信息</div>
        </div>
        <div class="info-form">
          <div class="plain-row">
            <a-textarea
              v-model:value="meta.introduction"
              placeholder="简介（选填，应用到本批全部图片）"
              :auto-size="{ minRows: 3, maxRows: 5 }"
              :disabled="uploading"
            />
          </div>

          <PictureMetaPicker v-model:category="meta.category" v-model:tags="meta.tags" />

          <div class="submit-row">
            <a-button
              type="primary"
              class="submit-btn"
              :loading="uploading"
              :disabled="!fileList.length || uploading"
              @click="handleSubmit"
            >
              {{ uploadButtonText }}
            </a-button>
          </div>
        </div>
        <div class="info-note">
          分类、标签、简介会应用到本次上传的全部图片；名称逐张取自文件名，可在左侧单独修改。
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { onBeforeRouteLeave, useRoute } from 'vue-router'
import {
  CheckCircleFilled,
  CloseOutlined,
  CloudUploadOutlined,
} from '@ant-design/icons-vue'
import {
  editPictureUsingPost,
  uploadPictureByUrlUsingPost,
  uploadPictureUsingPost,
} from '@/api/pictureController.ts'
import { getSpaceVoByIdUsingGet } from '@/api/spaceController.ts'
import PictureMetaPicker from '@/components/PictureMetaPicker.vue'

const route = useRoute()

// 单次批量上限与同时上传的并发数
const MAX_BATCH_COUNT = 20
const CONCURRENCY = 3

type ItemStatus = 'ready' | 'uploading' | 'done' | 'failed'

interface BatchItem {
  uid: number
  /** 来源：本地文件 / 图片链接 */
  kind: 'file' | 'url'
  file?: File
  fileUrl?: string
  /** 列表缩略图地址（本地为 objectURL） */
  previewUrl: string
  name: string
  status: ItemStatus
  error?: string
}

const fileList = ref<BatchItem[]>([])
const urlText = ref('')
const uploading = ref(false)
let uidSeed = 0

// 空间 id（雪花 id，保持字符串传递，避免精度丢失）
const spaceId = computed(() => route.query?.spaceId as string | undefined)
const space = ref<API.SpaceVO>()

// 统一信息：应用到本批全部图片
const meta = reactive<{ category?: string; tags?: string[]; introduction?: string }>({})

/** 空间剩余可上传张数 */
const remainCount = computed(() =>
  Math.max((space.value?.maxCount ?? 0) - (space.value?.totalCount ?? 0), 0),
)

const doneCount = computed(() => fileList.value.filter((item) => item.status === 'done').length)

const uploadButtonText = computed(() =>
  uploading.value ? `上传中 ${doneCount.value}/${fileList.value.length}` : '全部创建',
)

/** 上传前的格式与体积校验（与单张上传保持一致） */
const validateFile = (file: File) => {
  const isSupported = ['image/jpeg', 'image/png', 'image/webp'].includes(file.type)
  if (!isSupported) {
    message.error(`${file.name} 格式不支持，推荐 jpg / png / webp`)
    return false
  }
  if (file.size / 1024 / 1024 >= 10) {
    message.error(`${file.name} 超过 10M，无法上传`)
    return false
  }
  return true
}

/**
 * 选中文件：仅收集到待上传列表并生成本地预览，提交时才真正上传
 */
const addFiles = (file: any) => {
  if (fileList.value.length >= MAX_BATCH_COUNT) {
    message.warning(`单次最多上传 ${MAX_BATCH_COUNT} 张，请先提交或移除部分图片`)
    return false
  }
  if (!validateFile(file as File)) {
    return false
  }
  fileList.value.push({
    uid: ++uidSeed,
    kind: 'file',
    file: file as File,
    previewUrl: URL.createObjectURL(file as File),
    name: (file.name ?? '').replace(/\.[^.]+$/, ''),
    status: 'ready',
  })
  return false
}

/** 从链接中取文件名作为默认名称 */
const urlFileName = (url: string) => {
  const last = url.split('?')[0].split('#')[0].split('/').pop() ?? ''
  try {
    return decodeURIComponent(last).replace(/\.[^.]+$/, '') || '未命名图片'
  } catch (e) {
    return '未命名图片'
  }
}

/**
 * 批量导入图片链接：按行拆分，去空行、去重、校验协议
 */
const importUrls = () => {
  const lines = (urlText.value ?? '')
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
  if (!lines.length) {
    message.warning('请先粘贴图片链接，每行一个')
    return
  }
  const valid = lines.filter((line) => /^https?:\/\//i.test(line))
  const invalidCount = lines.length - valid.length
  if (invalidCount) {
    message.warning(`已跳过 ${invalidCount} 行非 http/https 链接`)
  }
  const existed = new Set(
    fileList.value.filter((item) => item.kind === 'url').map((item) => item.fileUrl),
  )
  let added = 0
  let overLimit = false
  for (const url of valid) {
    if (existed.has(url)) {
      continue
    }
    if (fileList.value.length >= MAX_BATCH_COUNT) {
      overLimit = true
      break
    }
    existed.add(url)
    fileList.value.push({
      uid: ++uidSeed,
      kind: 'url',
      fileUrl: url,
      previewUrl: url,
      name: urlFileName(url),
      status: 'ready',
    })
    added++
  }
  urlText.value = ''
  if (added) {
    message.success(`已导入 ${added} 个链接`)
  }
  if (overLimit) {
    message.warning(`单次最多上传 ${MAX_BATCH_COUNT} 张，超出的链接已跳过`)
  }
}

/** 释放本地预览地址 */
const revokeItem = (item: BatchItem) => {
  if (item.kind === 'file' && item.previewUrl.startsWith('blob:')) {
    URL.revokeObjectURL(item.previewUrl)
  }
}

const removeOne = (item: BatchItem) => {
  revokeItem(item)
  fileList.value = fileList.value.filter((one) => one.uid !== item.uid)
}

const clearAll = () => {
  fileList.value.forEach(revokeItem)
  fileList.value = []
}

const releaseAll = () => {
  fileList.value.forEach(revokeItem)
}

/**
 * 上传单张图片：上传成功后，若有统一信息再补一次编辑
 */
const uploadOne = async (item: BatchItem) => {
  item.status = 'uploading'
  item.error = ''
  try {
    const params: API.PictureUploadRequest = { picName: item.name || undefined }
    if (spaceId.value) {
      params.spaceId = spaceId.value as unknown as number
    }
    const res =
      item.kind === 'file'
        ? await uploadPictureUsingPost(params, {}, item.file as File, { timeout: 300000 })
        : await uploadPictureByUrlUsingPost({ ...params, fileUrl: item.fileUrl }, { timeout: 300000 })
    if (res.data.code !== 0 || !res.data.data) {
      item.status = 'failed'
      item.error = res.data.message || '上传失败'
      return
    }
    const uploaded = res.data.data
    // 统一信息：有填写才调用编辑接口
    if (meta.category || meta.tags?.length || meta.introduction) {
      const editRes = await editPictureUsingPost({
        id: uploaded.id,
        category: meta.category,
        tags: meta.tags,
        introduction: meta.introduction,
      })
      if (editRes.data.code !== 0) {
        item.status = 'failed'
        item.error = editRes.data.message || '分类标签保存失败'
        return
      }
    }
    item.status = 'done'
  } catch (error: any) {
    item.status = 'failed'
    item.error = error?.message ?? '上传失败'
  }
}

/** 单张重试 */
const retryOne = async (item: BatchItem) => {
  uploading.value = true
  await uploadOne(item)
  uploading.value = false
  if (item.status === 'done') {
    message.success(`${item.name || '图片'} 上传成功`)
  }
}

/** 提交：按并发数逐张上传，单张失败不影响其他图片 */
const handleSubmit = async () => {
  const pending = fileList.value.filter((item) => item.status !== 'done')
  if (!pending.length) {
    message.warning('请先选择要上传的图片')
    return
  }
  if (spaceId.value && space.value && remainCount.value < pending.length) {
    message.warning(`空间剩余额度不足，最多还能上传 ${remainCount.value} 张`)
    return
  }
  uploading.value = true
  let cursor = 0
  const workers = Array.from({ length: Math.min(CONCURRENCY, pending.length) }, async () => {
    while (cursor < pending.length) {
      const item = pending[cursor]
      cursor++
      await uploadOne(item)
    }
  })
  await Promise.all(workers)
  uploading.value = false
  const successCount = pending.filter((item) => item.status === 'done').length
  const failedCount = pending.length - successCount
  if (failedCount) {
    message.warning(`上传完成：成功 ${successCount} 张，失败 ${failedCount} 张，可对失败项单独重试`)
  } else {
    message.success(`已提交 ${successCount} 张图片${spaceId.value ? '' : '，审核通过后展示在首页'}`)
  }
}

const stateText = (item: BatchItem) => {
  switch (item.status) {
    case 'uploading':
      return '上传中...'
    case 'done':
      return '已上传'
    case 'failed':
      return '上传失败'
    default:
      return '待上传'
  }
}

// 空间信息：用于提交前的额度提示
const fetchSpace = async () => {
  if (!spaceId.value) {
    return
  }
  const res = await getSpaceVoByIdUsingGet({ id: spaceId.value as unknown as number })
  if (res.data.code === 0 && res.data.data) {
    space.value = res.data.data
  }
}

onMounted(() => {
  fetchSpace()
})

onUnmounted(() => {
  releaseAll()
})

// 上传中离开页面会中断本次批量上传，先确认
onBeforeRouteLeave(async () => {
  if (!uploading.value) {
    releaseAll()
    return true
  }
  return await new Promise<boolean>((resolve) => {
    Modal.confirm({
      title: '图片正在上传',
      content: '离开页面会中断本次批量上传，已上传的图片不受影响。确定要离开吗？',
      okText: '仍要离开',
      cancelText: '继续上传',
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    })
  })
})
</script>

<style scoped>
#addPictureBatchUploadPage {
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

/* ---------- 左栏：选择与列表 ---------- */
.stage-card {
  min-width: 0;
  padding: 18px 20px 20px;
  animation: rise 0.28s ease-out both;
}

.batch-drop :deep(.ant-upload) {
  display: block;
  width: 100% !important;
  height: auto !important;
  border: 1.5px dashed var(--border-color) !important;
  border-radius: 12px !important;
  background: var(--bg-body) !important;
  transition: border-color 0.2s ease, background 0.2s ease;
}

.batch-drop :deep(.ant-upload:hover) {
  border-color: var(--accent) !important;
}

.drop-inner {
  padding: 34px 20px;
  text-align: center;
}

.drop-icon {
  font-size: 38px;
  color: var(--accent);
}

.drop-text {
  margin-top: 12px;
  font-size: 15px;
  color: var(--text-primary-light);
}

.drop-sub {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-disabled);
}

/* URL 批量导入 */
.url-block {
  margin-top: 16px;
}

.url-block :deep(.ant-input) {
  padding: 8px 0 !important;
  border: none !important;
  background: transparent !important;
  box-shadow: none !important;
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-primary-light);
  resize: none;
}

.url-block :deep(.ant-input::placeholder) {
  color: var(--text-disabled);
}

.url-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 8px;
  border-top: 1px solid var(--border-color);
}

.url-hint {
  font-size: 12px;
  color: var(--text-disabled);
}

/* 待上传列表 */
.list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 18px;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--border-color);
}

.list-title {
  font-size: 13px;
  color: var(--text-secondary);
}

.file-list {
  max-height: 420px;
  overflow-y: auto;
}

.file-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--border-color);
}

.file-row:last-child {
  border-bottom: none;
}

.file-thumb {
  flex: 0 0 auto;
  width: 48px;
  height: 48px;
  border-radius: 9px;
  object-fit: cover;
  background: var(--bg-body);
}

.file-main {
  flex: 1;
  min-width: 0;
}

.file-name {
  width: 100%;
  padding: 0;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  color: var(--text-primary-light);
}

.file-name::placeholder {
  color: var(--text-disabled);
}

.file-name:disabled {
  color: var(--text-secondary);
}

.file-sub {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  font-size: 12px;
}

.file-state {
  color: var(--text-disabled);
}

.file-state.is-uploading {
  color: var(--accent);
}

.file-state.is-done {
  color: #389e0d;
}

.file-state.is-failed {
  color: #cf1322;
}

.file-error {
  overflow: hidden;
  color: #cf1322;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-remove {
  flex: 0 0 auto;
  color: var(--text-disabled);
  cursor: pointer;
  transition: color 0.15s ease;
}

.file-remove:hover {
  color: #cf1322;
}

.file-done {
  flex: 0 0 auto;
  color: #389e0d;
}

.list-empty {
  padding: 32px 0 12px;
  text-align: center;
  font-size: 13px;
  color: var(--text-disabled);
}

/* ---------- 右栏：统一信息 ---------- */
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

.info-form {
  display: flex;
  flex-direction: column;
  flex: 1;
}

.plain-row {
  padding: 14px 0;
  border-bottom: 1px solid var(--border-color);
}

/* 输入控件：无边框，仅靠占位文字与留白区分 */
.info-form :deep(.ant-input) {
  width: 100%;
  padding: 2px 0 !important;
  border: none !important;
  background: transparent !important;
  box-shadow: none !important;
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-primary-light);
  resize: none;
}

.info-form :deep(.ant-input::placeholder) {
  color: var(--text-disabled);
}

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

.info-note {
  margin-top: 12px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-disabled);
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

html.dark .info-form :deep(.ant-input),
html.dark .url-block :deep(.ant-input) {
  border: none !important;
  background: transparent !important;
}

/* 小屏单栏 */
@media (max-width: 1024px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>