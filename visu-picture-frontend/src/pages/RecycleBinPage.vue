<template>
  <div id="recycleBinPage">
    <div class="page-head">
      <h2 class="page-title">回收站</h2>
      <p class="page-sub">删除的图片会保留在这里，可随时恢复或彻底删除</p>
    </div>

    <div class="toolbar">
      <span class="toolbar-count">共 {{ total }} 张</span>
    </div>

    <a-spin :spinning="loading">
      <div v-if="pictures.length" class="recycle-grid">
        <div v-for="item in pictures" :key="item.id" class="recycle-card">
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
          <div class="card-actions">
            <a-button size="small" type="primary" @click="doRestore(item)">恢复</a-button>
            <a-popconfirm
              title="彻底删除后无法恢复，确认删除？"
              ok-text="彻底删除"
              cancel-text="取消"
              @confirm="doDeleteForever(item)"
            >
              <a-button size="small" danger>彻底删除</a-button>
            </a-popconfirm>
          </div>
        </div>
      </div>
      <div v-else-if="!loading" class="empty-wrap">
        <a-empty description="回收站是空的，删除的图片会出现在这里">
          <a-button type="primary" @click="router.push('/')">去图库逛逛</a-button>
        </a-empty>
      </div>
    </a-spin>

    <div v-if="total > pageSize" class="pager">
      <a-pagination
        v-model:current="current"
        :page-size="pageSize"
        :total="total"
        size="small"
        @change="fetchList"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import {
  deleteRecycledPicture,
  listMyRecycle,
  restoreRecycledPicture,
} from '@/api/recycle.ts'
import type { RecyclePicture } from '@/api/recycle.ts'

const router = useRouter()

const pictures = ref<RecyclePicture[]>([])
const total = ref(0)
const current = ref(1)
const pageSize = 12
const loading = ref(false)

const fetchList = async () => {
  loading.value = true
  try {
    const res = await listMyRecycle(current.value, pageSize)
    if (res.code === 0 && res.data) {
      pictures.value = res.data.records ?? []
      total.value = res.data.total ?? 0
    } else {
      message.error(res.message ?? '加载回收站失败')
    }
  } catch (e: any) {
    message.error('加载回收站失败：' + (e?.message ?? ''))
  } finally {
    loading.value = false
  }
}

/** 恢复图片 */
const doRestore = async (item: RecyclePicture) => {
  const res = await restoreRecycledPicture(item.id)
  if (res.code === 0) {
    message.success(`已恢复「${item.name}」`)
    await fetchList()
  } else {
    message.error(res.message ?? '恢复失败')
  }
}

/** 彻底删除 */
const doDeleteForever = async (item: RecyclePicture) => {
  const res = await deleteRecycledPicture(item.id)
  if (res.code === 0) {
    message.success('已彻底删除')
    await fetchList()
  } else {
    message.error(res.message ?? '删除失败')
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

.toolbar {
  margin: 16px 0 12px;
}

.toolbar-count {
  font-size: 13px;
  color: var(--text-secondary);
}

.recycle-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
}

.recycle-card {
  background: var(--bg-card);
  border-radius: var(--card-radius, 12px);
  box-shadow: var(--card-shadow);
  overflow: hidden;
  display: flex;
  flex-direction: column;
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
  padding: 10px 12px 4px;
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

.card-actions {
  display: flex;
  gap: 8px;
  padding: 10px 12px 12px;
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
