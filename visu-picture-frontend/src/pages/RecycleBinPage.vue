<template>
  <div id="recycleBinPage">
    <div class="page-head">
      <h2 class="page-title">回收站</h2>
      <p class="page-sub">删除的图片会保留在这里，勾选后可批量恢复或彻底删除</p>
    </div>

    <!-- 批量操作工具栏：勾选驱动 -->
    <div class="batch-bar">
      <div class="batch-left">
        <a-checkbox
          :checked="isAllSelected"
          :indeterminate="selectedIds.length > 0 && !isAllSelected"
          @change="toggleSelectAll"
        >
          全选
        </a-checkbox>
        <span class="batch-count">共 {{ total }} 张<template v-if="selectedIds.length"> · 已选 <b>{{ selectedIds.length }}</b> 张</template></span>
      </div>
      <div class="batch-actions">
        <a-button size="small" type="primary" :disabled="!selectedIds.length" :loading="batchLoading" @click="doBatchRestore">
          恢复选中
        </a-button>
        <a-popconfirm
          title="彻底删除后无法恢复，确认删除选中的图片？"
          ok-text="彻底删除"
          cancel-text="取消"
          :disabled="!selectedIds.length"
          @confirm="doBatchDeleteForever"
        >
          <a-button size="small" danger :disabled="!selectedIds.length" :loading="batchLoading">
            彻底删除
          </a-button>
        </a-popconfirm>
      </div>
    </div>

    <a-spin :spinning="loading">
      <div v-if="pictures.length" class="recycle-grid">
        <div
          v-for="item in pictures"
          :key="item.id"
          class="recycle-card"
          :class="{ selected: selectedIds.includes(String(item.id)) }"
          @click="toggleSelect(item)"
        >
          <!-- 勾选态 -->
          <div class="check-overlay" @click.stop>
            <a-checkbox
              :checked="selectedIds.includes(String(item.id))"
              @change="toggleSelect(item)"
            />
          </div>
          <div class="thumb-wrap">
            <img :src="item.thumbnailUrl ?? item.url" :alt="item.name" loading="lazy" />
          </div>
          <div class="card-info">
            <div class="pic-name" :title="item.name">{{ item.name }}</div>
            <div class="pic-meta">
              {{ formatTime(item.updateTime) }}
              <template v-if="item.picSize"> · {{ formatPicSize(item.picSize) }}</template>
            </div>
          </div>
        </div>
      </div>
      <div v-else-if="!loading" class="empty-wrap">
        <EmptyState description="回收站是空的，删除的图片会出现在这里" />
      </div>
    </a-spin>

    <div v-if="total > pageSize" class="pager">
      <a-pagination
        v-model:current="current"
        :page-size="pageSize"
        :total="total"
        size="small"
        @change="onPageChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import EmptyState from '@/components/EmptyState.vue'
import { useRouter } from 'vue-router'
import {
  deleteRecycledBatch,
  deleteRecycledPicture,
  listMyRecycle,
  restoreRecycledBatch,
  restoreRecycledPicture,
} from '@/api/recycle.ts'
import type { RecyclePicture } from '@/api/recycle.ts'

const router = useRouter()

const pictures = ref<RecyclePicture[]>([])
const total = ref(0)
const current = ref(1)
const pageSize = 12
const loading = ref(false)
const batchLoading = ref(false)
// 勾选的图片 id（统一字符串形态，规避雪花 id 精度问题）
const selectedIds = ref<string[]>([])

const isAllSelected = computed(
  () => pictures.value.length > 0 && pictures.value.every((p) => selectedIds.value.includes(String(p.id))),
)

const toggleSelect = (item: RecyclePicture) => {
  const id = String(item.id)
  selectedIds.value = selectedIds.value.includes(id)
    ? selectedIds.value.filter((v) => v !== id)
    : [...selectedIds.value, id]
}

const toggleSelectAll = () => {
  if (isAllSelected.value) {
    selectedIds.value = []
  } else {
    selectedIds.value = pictures.value.map((p) => String(p.id))
  }
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await listMyRecycle(current.value, pageSize)
    if (res.code === 0 && res.data) {
      pictures.value = res.data.records ?? []
      total.value = res.data.total ?? 0
      // 翻页后清理已不在当前页的勾选
      const alive = new Set(pictures.value.map((p) => String(p.id)))
      selectedIds.value = selectedIds.value.filter((id) => alive.has(id))
    } else {
      message.error(res.message ?? '加载回收站失败')
    }
  } catch (e: any) {
    message.error('加载回收站失败：' + (e?.message ?? ''))
  } finally {
    loading.value = false
  }
}

const onPageChange = () => {
  fetchList()
}

/** 批量恢复 */
const doBatchRestore = async () => {
  if (!selectedIds.value.length) return
  batchLoading.value = true
  try {
    const res = await restoreRecycledBatch(selectedIds.value)
    if (res.code === 0) {
      message.success(`已恢复 ${res.data ?? 0} 张图片`)
      await fetchList()
    } else {
      message.error(res.message ?? '批量恢复失败')
    }
  } catch (e: any) {
    message.error('批量恢复失败：' + (e?.message ?? ''))
  } finally {
    batchLoading.value = false
  }
}

/** 批量彻底删除 */
const doBatchDeleteForever = async () => {
  if (!selectedIds.value.length) return
  batchLoading.value = true
  try {
    const res = await deleteRecycledBatch(selectedIds.value)
    if (res.code === 0) {
      message.success(`已彻底删除 ${res.data ?? 0} 张图片`)
      await fetchList()
    } else {
      message.error(res.message ?? '批量删除失败')
    }
  } catch (e: any) {
    message.error('批量删除失败：' + (e?.message ?? ''))
  } finally {
    batchLoading.value = false
  }
}

const formatTime = (time?: string) => (time ? String(time).replace('T', ' ').slice(0, 16) : '')

/** 字节 → 可读大小（与 utils/formatSize 口径一致，避免跨目录引用） */
const formatPicSize = (size: number) => {
  if (!size) return ''
  const kb = size / 1024
  if (kb < 1024) return `${kb.toFixed(1)} KB`
  return `${(kb / 1024).toFixed(2)} MB`
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped>
#recycleBinPage {
  max-width: 1152px;
  margin: 0 auto 24px;
  padding: 0 16px;
}

.page-head {
  margin: 12px 0 0;
}

.page-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary-light);
}

.page-sub {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--text-secondary);
}

/* ===== 批量操作工具栏 ===== */
.batch-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin: 16px 0 12px;
  padding: 10px 16px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--card-radius, 12px);
  box-shadow: var(--card-shadow);
}

.batch-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.batch-count {
  font-size: 13px;
  color: var(--text-secondary);
}

.batch-count b {
  color: var(--accent);
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-weight: 600;
}

.batch-actions {
  display: flex;
  gap: 8px;
}

/* ===== 卡片网格 ===== */
.recycle-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
}

.recycle-card {
  position: relative;
  background: var(--bg-card);
  border-radius: var(--card-radius, 12px);
  box-shadow: var(--card-shadow);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  cursor: pointer;
  border: 1.5px solid transparent;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    border-color 0.2s ease;
}

.recycle-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--card-shadow-hover);
}

.recycle-card.selected {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgba(64, 169, 255, 0.18), var(--card-shadow);
}

.check-overlay {
  position: absolute;
  top: 8px;
  left: 8px;
  z-index: 2;
  background: rgba(0, 0, 0, 0.35);
  border-radius: 8px;
  padding: 3px 5px;
  backdrop-filter: blur(4px);
}

.thumb-wrap {
  height: 130px;
  background: rgba(127, 127, 127, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.thumb-wrap img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.card-info {
  padding: 10px 12px 12px;
}

.pic-name {
  font-size: 13px;
  color: var(--text-primary-light);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.pic-meta {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.empty-wrap {
  padding: 48px 0 64px;
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

html.dark .pic-name {
  color: var(--text-primary);
}
</style>
