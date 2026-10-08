<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  editPictureUsingPost,
  listPictureTagCategoryUsingGet,
  uploadPictureUsingPost,
} from '@/api/pictureController.ts'
import { getSpaceVoByIdUsingGet } from '@/api/spaceController.ts'
import { showToast } from 'vant'

defineOptions({ name: 'AddPictureBatchPage' })

const route = useRoute()
const router = useRouter()

const spaceId = computed(() => route.query?.spaceId as string | undefined)

const MAX_COUNT = 20
const CONCURRENCY = 3

// ----- 队列项 -----
interface BatchItem {
  file: File
  previewUrl: string
  status: 'pending' | 'uploading' | 'success' | 'failed'
  errorMsg?: string
  uploadedId?: string | number
  metaUpdated?: boolean
}

const items = ref<BatchItem[]>([])
const fileInputRef = ref<HTMLInputElement>()

// ----- 统一信息 -----
const namePrefix = ref('')
const metaForm = reactive<{ category?: string; tags: string[] }>({
  category: undefined,
  tags: [],
})

// 分类 / 标签选项
const categoryList = ref<string[]>([])
const tagList = ref<string[]>([])

const loadTagCategory = async () => {
  const res = await listPictureTagCategoryUsingGet()
  if (res.data.code === 0 && res.data.data) {
    categoryList.value = res.data.data.categoryList ?? []
    tagList.value = res.data.data.tagList ?? []
  }
}

// ----- 空间额度预检 -----
const spaceInfo = ref<API.SpaceVO>()
const quotaWarning = ref('')
// 分类 / 标签选择弹层
const showCategoryPicker = ref(false)
const showTagPicker = ref(false)

const checkQuota = async () => {
  if (!spaceId.value) return
  try {
    const res = await getSpaceVoByIdUsingGet({ id: spaceId.value } as any)
    if (res.data.code === 0 && res.data.data) {
      spaceInfo.value = res.data.data
      const { maxCount, totalCount } = res.data.data
      if (maxCount != null && totalCount != null) {
        const remaining = maxCount - totalCount
        if (remaining <= 0) {
          quotaWarning.value = '当前空间额度已用完，无法继续上传'
        } else if (remaining < MAX_COUNT) {
          quotaWarning.value = `当前空间剩余额度 ${remaining} 张`
        }
      }
    }
  } catch (e) {
    // 预检失败不阻塞上传
  }
}

onMounted(() => {
  loadTagCategory()
  checkQuota()
})

// 释放预览 URL
onBeforeUnmount(() => {
  items.value.forEach((item) => URL.revokeObjectURL(item.previewUrl))
})

// ----- 选择文件 -----
const doPickFiles = () => {
  fileInputRef.value?.click()
}

const onFilesChange = (e: Event) => {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  if (files.length === 0) return
  const remain = MAX_COUNT - items.value.length
  if (remain <= 0) {
    showToast(`最多上传 ${MAX_COUNT} 张`)
    return
  }
  if (files.length > remain) {
    showToast(`最多上传 ${MAX_COUNT} 张，已截取前 ${remain} 张`)
  }
  for (const file of files.slice(0, remain)) {
    items.value.push({
      file,
      previewUrl: URL.createObjectURL(file),
      status: 'pending',
    })
  }
}

// 移除队列项
const removeItem = (index: number) => {
  const item = items.value[index]
  if (!item || item.status === 'uploading') return
  URL.revokeObjectURL(item.previewUrl)
  items.value.splice(index, 1)
}

// ----- 并发上传 -----
const uploading = ref(false)
const doneCount = computed(
  () => items.value.filter((i) => i.status === 'success' || i.status === 'failed').length,
)

const uploadOne = async (item: BatchItem) => {
  item.status = 'uploading'
  item.errorMsg = undefined
  try {
    const picName = namePrefix.value
      ? `${namePrefix.value}-${items.value.indexOf(item) + 1}`
      : item.file.name
    const res = await uploadPictureUsingPost(
      { picName, spaceId: spaceId.value as any },
      {},
      item.file,
    )
    if (res.data.code === 0 && res.data.data) {
      item.status = 'success'
      item.uploadedId = res.data.data.id
    } else {
      item.status = 'failed'
      item.errorMsg = res.data.message ?? '上传失败'
    }
  } catch (e: any) {
    item.status = 'failed'
    item.errorMsg = e?.message ?? '上传失败'
  }
}

// 失败重试单张
const retryItem = async (item: BatchItem) => {
  if (item.status !== 'failed') return
  await uploadOne(item)
  // 重试成功后若统一信息已提交过，补一次元信息更新
  if ((item.status as string) === 'success' && metaSubmitted.value) {
    await updateMeta([item])
  }
}

const runPool = async (pool: BatchItem[]) => {
  const queue = [...pool]
  const workers = Array.from({ length: Math.min(CONCURRENCY, queue.length) }, async () => {
    while (queue.length > 0) {
      const item = queue.shift()
      if (item) {
        await uploadOne(item)
      }
    }
  })
  await Promise.all(workers)
}

// ----- 统一元信息更新 -----
const metaSubmitted = ref(false)

const updateMeta = async (successItems: BatchItem[]) => {
  const needUpdate = successItems.filter(
    (i) => i.uploadedId && (metaForm.category || metaForm.tags.length > 0),
  )
  if (needUpdate.length === 0) return
  const queue = [...needUpdate]
  const workers = Array.from({ length: Math.min(CONCURRENCY, queue.length) }, async () => {
    while (queue.length > 0) {
      const item = queue.shift()
      if (!item?.uploadedId) continue
      try {
        const res = await editPictureUsingPost({
          id: item.uploadedId,
          category: metaForm.category,
          tags: metaForm.tags.length > 0 ? metaForm.tags : undefined,
        } as any)
        item.metaUpdated = res.data.code === 0
      } catch (e) {
        item.metaUpdated = false
      }
    }
  })
  await Promise.all(workers)
}

// ----- 开始上传 -----
const doStart = async () => {
  const pending = items.value.filter((i) => i.status === 'pending' || i.status === 'failed')
  if (pending.length === 0) {
    showToast('请先选择图片')
    return
  }
  if (quotaWarning.value.includes('已用完')) {
    showToast(quotaWarning.value)
    return
  }
  if (uploading.value) return
  uploading.value = true
  try {
    // 1. 并发上传全部待传/失败项
    await runPool(pending)
    // 2. 统一元信息
    metaSubmitted.value = true
    const successItems = items.value.filter((i) => i.status === 'success')
    await updateMeta(successItems)
    // 3. 结果提示
    const successCount = successItems.length
    const failCount = items.value.filter((i) => i.status === 'failed').length
    showToast({
      type: successCount > 0 ? 'success' : 'fail',
      message: `成功 ${successCount} 张${failCount > 0 ? `，失败 ${failCount} 张` : ''}`,
    })
    // 全部成功则返回
    if (failCount === 0) {
      setTimeout(() => {
        if (spaceId.value) {
          router.replace(`/space/${spaceId.value}`)
        } else {
          router.replace('/')
        }
      }, 1200)
    }
  } finally {
    uploading.value = false
  }
}

// 状态文案与颜色
const statusText = (status: BatchItem['status']) => {
  return { pending: '等待', uploading: '上传中', success: '成功', failed: '失败' }[status]
}

const goBack = () => {
  if (uploading.value) {
    showToast('正在上传，请稍候')
    return
  }
  if (window.history.length > 1) {
    router.back()
  } else {
    router.replace('/')
  }
}
</script>

<template>
  <div class="batch-page">
    <van-nav-bar title="批量上传" left-arrow @click-left="goBack" />

    <!-- 额度提示 -->
    <van-notice-bar
      v-if="quotaWarning"
      :text="quotaWarning"
      :color="quotaWarning.includes('已用完') ? '#ef4444' : '#f59e0b'"
      background="#fff7e8"
      left-icon="info-o"
    />

    <!-- 选择图片 -->
    <div class="pick-card" @click="doPickFiles">
      <van-icon name="photograph" size="28" color="#2563eb" />
      <span>点击选择图片（最多 {{ MAX_COUNT }} 张）</span>
      <span class="pick-count">{{ items.length }}/{{ MAX_COUNT }}</span>
    </div>
    <input
      ref="fileInputRef"
      type="file"
      accept="image/*"
      multiple
      style="display: none"
      @change="onFilesChange"
    />

    <!-- 队列列表 -->
    <div v-if="items.length > 0" class="queue">
      <div v-for="(item, index) in items" :key="item.previewUrl" class="queue-item">
        <img class="queue-thumb" :src="item.previewUrl" :alt="item.file.name" />
        <div class="queue-info">
          <div class="queue-name ellipsis">{{ item.file.name }}</div>
          <div class="queue-status" :class="item.status">
            {{ statusText(item.status) }}{{ item.errorMsg ? ' · ' + item.errorMsg : '' }}
          </div>
        </div>
        <van-icon
          v-if="item.status !== 'uploading' && !uploading"
          name="cross"
          color="#94a3b8"
          class="queue-remove"
          @click="removeItem(index)"
        />
        <van-button
          v-else-if="item.status === 'failed' && !uploading"
          size="mini"
          plain
          round
          type="primary"
          @click="retryItem(item)"
        >
          重试
        </van-button>
        <van-loading v-if="item.status === 'uploading'" size="16" />
      </div>
    </div>

    <!-- 统一信息 -->
    <van-cell-group inset class="meta-card" title="统一信息（可选）">
      <van-field
        v-model="namePrefix"
        label="名称前缀"
        placeholder="如 风景照，将生成 风景照-1、风景照-2"
        maxlength="20"
        clearable
      />
      <van-field
        :model-value="metaForm.category"
        label="分类"
        placeholder="统一分类（可选）"
        readonly
        is-link
        @click="showCategoryPicker = true"
      />
      <van-field
        :model-value="metaForm.tags.join('、')"
        label="标签"
        placeholder="统一标签（可多选）"
        readonly
        is-link
        @click="showTagPicker = true"
      />
    </van-cell-group>

    <!-- 开始上传 -->
    <div class="submit-area">
      <van-button
        round
        block
        type="primary"
        :loading="uploading"
        :disabled="items.length === 0"
        loading-text="上传中，请勿离开..."
        @click="doStart"
      >
        开始上传（{{ doneCount }}/{{ items.length }}）
      </van-button>
    </div>

    <!-- 分类选择弹层 -->
    <van-popup v-model:show="showCategoryPicker" position="bottom" round>
      <div class="picker-header">选择分类</div>
      <div class="picker-chips">
        <div
          v-for="c in categoryList"
          :key="c"
          class="filter-option"
          :class="{ active: metaForm.category === c }"
          @click="metaForm.category = metaForm.category === c ? undefined : c"
        >
          {{ c }}
        </div>
      </div>
      <div class="picker-footer">
        <van-button round block type="primary" @click="showCategoryPicker = false">确定</van-button>
      </div>
    </van-popup>

    <!-- 标签选择弹层 -->
    <van-popup v-model:show="showTagPicker" position="bottom" round>
      <div class="picker-header">选择标签（可多选）</div>
      <div class="picker-chips">
        <div
          v-for="t in tagList"
          :key="t"
          class="filter-option"
          :class="{ active: metaForm.tags.includes(t) }"
          @click="
            metaForm.tags.includes(t)
              ? (metaForm.tags = metaForm.tags.filter((v) => v !== t))
              : metaForm.tags.push(t)
          "
        >
          {{ t }}
        </div>
      </div>
      <div class="picker-footer">
        <van-button round block type="primary" @click="showTagPicker = false">确定</van-button>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.batch-page {
  min-height: 100vh;
  background: var(--bg-page);
  padding-bottom: calc(32px + var(--safe-bottom));
}

.batch-page :deep(.van-nav-bar) {
  padding-top: var(--safe-top);
}

.pick-card {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 12px 16px 0;
  padding: 16px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  cursor: pointer;
  font-size: 14px;
  color: var(--text-1);
}

.pick-count {
  margin-left: auto;
  color: var(--text-2);
  font-size: 13px;
}

/* 队列 */
.queue {
  margin: 12px 16px 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.queue-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
}

.queue-thumb {
  width: 44px;
  height: 44px;
  border-radius: 8px;
  object-fit: cover;
  flex-shrink: 0;
}

.queue-info {
  flex: 1;
  min-width: 0;
}

.queue-name {
  font-size: 13px;
  color: var(--text-1);
}

.queue-status {
  font-size: 12px;
  margin-top: 2px;
}

.queue-status.pending {
  color: var(--text-2);
}

.queue-status.uploading {
  color: var(--brand);
}

.queue-status.success {
  color: var(--success);
}

.queue-status.failed {
  color: var(--danger);
}

.queue-remove {
  cursor: pointer;
}

.meta-card {
  margin-top: 16px;
}

.submit-area {
  margin-top: 20px;
  padding: 0 16px;
}

.submit-area :deep(.van-button) {
  height: 44px;
  font-size: 16px;
}

.picker-header {
  padding: 16px;
  text-align: center;
  font-size: 16px;
  font-weight: 600;
}

.picker-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 0 16px 16px;
  max-height: 40vh;
  overflow-y: auto;
}

.picker-footer {
  padding: 0 16px calc(16px + var(--safe-bottom));
}

.filter-option {
  padding: 6px 16px;
  border-radius: var(--radius-chip);
  font-size: 14px;
  color: var(--text-2);
  background: var(--bg-page);
  cursor: pointer;
}

.filter-option.active {
  color: #fff;
  background: var(--brand);
  font-weight: 600;
}
</style>
