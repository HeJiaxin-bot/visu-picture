<template>
  <div id="pictureManagePage">
    <AdminShell
      title="图片管理"
      desc="审核与维护全站图片，可按关键词、类型、标签、审核状态筛选"
      :icon="PictureOutlined"
      tint="#13c2c2"
      :meta="`共 ${total} 张图片`"
    >
      <template #actions>
        <a-button type="primary" href="/add_picture" target="_blank">
          <template #icon><PlusOutlined /></template>
          创建图片
        </a-button>
        <a-button class="ghost-btn" href="/add_picture/batch" target="_blank">
          <template #icon><CloudDownloadOutlined /></template>
          批量创建
        </a-button>
      </template>

      <!-- 搜索表单 -->
      <template #filters>
        <a-form layout="inline" :model="searchParams" @finish="doSearch">
          <a-form-item label="关键词">
            <a-input
              v-model:value="searchParams.searchText"
              placeholder="从名称和简介搜索"
              allow-clear
            />
          </a-form-item>
          <a-form-item label="类型">
            <a-input v-model:value="searchParams.category" placeholder="请输入类型" allow-clear />
          </a-form-item>
          <a-form-item label="标签">
            <a-select
              v-model:value="searchParams.tags"
              mode="tags"
              placeholder="请输入标签"
              style="min-width: 180px"
              allow-clear
            />
          </a-form-item>
          <a-form-item name="reviewStatus" label="审核状态">
            <a-select
              v-model:value="searchParams.reviewStatus"
              style="min-width: 160px"
              placeholder="请选择审核状态"
              :options="PIC_REVIEW_STATUS_OPTIONS"
              allow-clear
            />
          </a-form-item>
          <a-form-item>
            <a-button type="primary" html-type="submit" :loading="loading">
              <template #icon><SearchOutlined /></template>
              搜索
            </a-button>
          </a-form-item>
        </a-form>
      </template>

      <!-- 表格 -->
      <a-table
        row-key="id"
        :columns="columns"
        :data-source="dataList"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 1697 }"
        @change="doTableChange"
      >
        <template #bodyCell="{ column, record }">
          <!-- 缩略图 -->
          <template v-if="column.key === 'url'">
            <a-image :src="record.url" :width="56" :height="56" class="cell-thumb" />
          </template>
          <!-- 名称 + 类型 -->
          <template v-else-if="column.key === 'name'">
            <div class="name-cell">
              <div class="cell-name">{{ record.name || '未命名图片' }}</div>
              <div class="cell-tags">
                <span v-if="record.category" class="soft-chip">{{ record.category }}</span>
                <span v-else class="soft-chip">未分类</span>
              </div>
            </div>
          </template>
          <!-- 简介（可作为 tooltip） -->
          <template v-else-if="column.key === 'introduction'">
            <a-tooltip :title="record.introduction">
              <span class="muted-text intro-text">{{ record.introduction || '—' }}</span>
            </a-tooltip>
          </template>
          <!-- 标签 -->
          <template v-else-if="column.key === 'tags'">
            <div class="tag-list">
              <span v-for="tag in parseTags(record.tags)" :key="tag" class="tag-pill">{{ tag }}</span>
              <span v-if="parseTags(record.tags).length === 0" class="muted-text">—</span>
            </div>
          </template>
          <!-- 图片信息 -->
          <template v-else-if="column.key === 'picInfo'">
            <div class="tag-list">
              <span class="meta-chip">{{ record.picFormat }}</span>
              <span class="meta-chip">{{ record.picWidth }} × {{ record.picHeight }}</span>
              <span class="meta-chip">{{ record.picScale }}</span>
              <span class="meta-chip">{{ formatSize(record.picSize) }}</span>
            </div>
          </template>
          <!-- 用户 id -->
          <template v-else-if="column.key === 'userId'">
            <span class="mono-text">{{ record.userId ?? '—' }}</span>
          </template>
          <!-- 审核信息 -->
          <template v-else-if="column.key === 'reviewMessage'">
            <div class="review-cell">
              <span class="status-chip" :class="statusClass(record.reviewStatus)">
                <i class="status-dot" />
                {{ PIC_REVIEW_STATUS_MAP[record.reviewStatus] }}
              </span>
              <span class="cell-sub">{{ record.reviewMessage || '暂无审核备注' }}</span>
              <span class="cell-sub">
                审核人 {{ record.reviewerId ?? '—' }}
                <template v-if="record.reviewTime">
                  · {{ dayjs(record.reviewTime).format('MM-DD HH:mm') }}
                </template>
              </span>
            </div>
          </template>
          <!-- 创建 / 编辑时间 -->
          <template v-else-if="column.key === 'time'">
            <div class="time-cell">
              <div class="time-row">
                <span class="time-label">创建</span>
                {{ dayjs(record.createTime).format('YYYY-MM-DD') }}
              </div>
              <div class="time-row">
                <span class="time-label">编辑</span>
                {{ dayjs(record.editTime).format('YYYY-MM-DD') }}
              </div>
            </div>
          </template>
          <!-- 操作 -->
          <template v-else-if="column.key === 'action'">
            <a-space :size="6" wrap>
              <!-- 已通过：不再提供审核操作，只展示状态（不可点击） -->
              <a-button
                v-if="record.reviewStatus === PIC_REVIEW_STATUS_ENUM.PASS"
                class="row-btn btn-approve"
                disabled
              >
                <template #icon><CheckOutlined /></template>
                已通过
              </a-button>
              <template v-else>
                <a-button
                  class="row-btn btn-approve"
                  :loading="reviewingId === record.id"
                  @click="handleReview(record, PIC_REVIEW_STATUS_ENUM.PASS)"
                >
                  <template #icon><CheckOutlined /></template>
                  通过
                </a-button>
                <a-button
                  v-if="record.reviewStatus !== PIC_REVIEW_STATUS_ENUM.REJECT"
                  class="row-btn btn-reject"
                  :loading="reviewingId === record.id"
                  @click="handleReview(record, PIC_REVIEW_STATUS_ENUM.REJECT)"
                >
                  <template #icon><CloseOutlined /></template>
                  拒绝
                </a-button>
              </template>
              <a-button
                class="row-btn ghost-btn"
                :href="`/add_picture?id=${record.id}`"
                target="_blank"
              >
                <template #icon><EditOutlined /></template>
                编辑
              </a-button>
              <!-- 危险操作：与常规操作以分隔线隔开 -->
              <i class="btn-sep" aria-hidden="true" />
              <a-popconfirm
                title="确定删除该图片？"
                description="删除后无法恢复"
                ok-text="删除"
                cancel-text="取消"
                ok-type="danger"
                @confirm="doDelete(record.id)"
              >
                <a-button
                  danger
                  class="row-btn btn-danger-text"
                  :loading="deletingId === record.id"
                >
                  <template #icon><DeleteOutlined /></template>
                  删除
                </a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </AdminShell>
  </div>
</template>
<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  deletePictureUsingPost,
  doPictureReviewUsingPost,
  listPictureByPageUsingPost,
} from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import {
  PIC_REVIEW_STATUS_ENUM,
  PIC_REVIEW_STATUS_MAP,
  PIC_REVIEW_STATUS_OPTIONS,
} from '@/constants/picture.ts'
import dayjs from 'dayjs'
import {
  CheckOutlined,
  CloseOutlined,
  CloudDownloadOutlined,
  DeleteOutlined,
  EditOutlined,
  PictureOutlined,
  PlusOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import AdminShell from '@/components/admin/AdminShell.vue'
import { formatSize } from '@/utils'

const columns = [
  { title: '图片', key: 'url', width: 92 },
  { title: '名称', key: 'name', width: 200 },
  { title: '简介', key: 'introduction', width: 180 },
  { title: '标签', key: 'tags', width: 175 },
  { title: '图片信息', key: 'picInfo', width: 185 },
  { title: '用户 ID', key: 'userId', width: 185 },
  { title: '审核信息', key: 'reviewMessage', width: 200 },
  { title: '时间', key: 'time', width: 140 },
  { title: '操作', key: 'action', width: 340, fixed: 'right' },
]

// 定义数据
const dataList = ref<API.Picture[]>([])
const total = ref(0)
const loading = ref(false)

// 搜索条件
const searchParams = reactive<API.PictureQueryRequest>({
  current: 1,
  pageSize: 10,
  sortField: 'createTime',
  sortOrder: 'descend',
})

// 获取数据
const fetchData = async () => {
  loading.value = true
  try {
    const res = await listPictureByPageUsingPost({
      ...searchParams,
      nullSpaceId: true,
    })
    if (res.data.code === 0 && res.data.data) {
      dataList.value = res.data.data.records ?? []
      total.value = res.data.data.total ?? 0
    } else {
      message.error('获取数据失败，' + res.data.message)
    }
  } finally {
    loading.value = false
  }
}

// 页面加载时获取数据，请求一次
onMounted(() => {
  fetchData()
})

// 分页参数
const pagination = computed(() => {
  return {
    current: searchParams.current,
    pageSize: searchParams.pageSize,
    total: total.value,
    showSizeChanger: true,
    showTotal: (total: number) => `共 ${total} 条`,
  }
})

// 表格变化之后，重新获取数据
const doTableChange = (page: any) => {
  searchParams.current = page.current
  searchParams.pageSize = page.pageSize
  fetchData()
}

// 搜索数据
const doSearch = () => {
  // 重置页码
  searchParams.current = 1
  fetchData()
}

// 删除数据
const deletingId = ref<string>('')
const doDelete = async (id: string) => {
  if (!id) {
    return
  }
  deletingId.value = id
  try {
    const res = await deletePictureUsingPost({ id })
    if (res.data.code === 0) {
      message.success('删除成功')
      // 刷新数据
      await fetchData()
    } else {
      message.error('删除失败')
    }
  } finally {
    deletingId.value = ''
  }
}

// 审核图片（按行加锁，避免重复提交）
const reviewingId = ref<string>('')
const handleReview = async (record: API.Picture, reviewStatus: number) => {
  const reviewMessage =
    reviewStatus === PIC_REVIEW_STATUS_ENUM.PASS ? '管理员操作通过' : '管理员操作拒绝'
  reviewingId.value = record.id ?? ''
  try {
    const res = await doPictureReviewUsingPost({
      id: record.id,
      reviewStatus,
      reviewMessage,
    })
    if (res.data.code === 0) {
      message.success('审核操作成功')
      // 重新获取列表数据
      await fetchData()
    } else {
      message.error('审核操作失败，' + res.data.message)
    }
  } finally {
    reviewingId.value = ''
  }
}

// 解析标签 JSON，兼容空值与非法内容
const parseTags = (tags?: string): string[] => {
  try {
    const parsed = JSON.parse(tags || '[]')
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

// 审核状态样式：待审核 / 通过 / 拒绝
const statusClass = (status?: number) => {
  if (status === PIC_REVIEW_STATUS_ENUM.PASS) return 'status-pass'
  if (status === PIC_REVIEW_STATUS_ENUM.REJECT) return 'status-reject'
  return 'status-reviewing'
}
</script>

<style scoped>
.cell-thumb {
  border-radius: 10px;
  overflow: hidden;
}

.cell-thumb :deep(img) {
  border-radius: 10px;
  object-fit: cover;
}

.name-cell {
  min-width: 0;
}

.cell-name {
  font-weight: 600;
  color: var(--text-primary-light);
  line-height: 1.35;
}

.cell-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  flex-wrap: wrap;
}

.soft-chip {
  font-size: 11px;
  line-height: 1;
  padding: 3px 8px;
  border-radius: 999px;
  color: var(--text-secondary);
  background: rgba(128, 128, 128, 0.12);
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tag-pill {
  font-size: 11.5px;
  line-height: 1;
  padding: 4px 9px;
  border-radius: 999px;
  color: var(--link);
  background: rgba(22, 119, 255, 0.1);
}

.meta-chip {
  font-size: 11.5px;
  line-height: 1;
  padding: 4px 9px;
  border-radius: 8px;
  color: var(--text-secondary);
  background: rgba(128, 128, 128, 0.1);
  font-variant-numeric: tabular-nums;
}

.mono-text {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12.5px;
  color: var(--text-secondary);
}

.muted-text {
  color: var(--text-secondary);
}

.intro-text {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

.review-cell {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  align-self: flex-start;
  font-size: 11.5px;
  line-height: 1;
  padding: 4px 10px;
  border-radius: 999px;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.status-reviewing {
  color: #d48806;
  background: rgba(250, 173, 20, 0.16);
}

.status-pass {
  color: #389e0d;
  background: rgba(82, 196, 26, 0.16);
}

.status-reject {
  color: #cf1322;
  background: rgba(255, 77, 79, 0.14);
}

.cell-sub {
  font-size: 12px;
  color: var(--text-disabled);
  line-height: 1.4;
}

.time-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.time-row {
  font-size: 12.5px;
  color: var(--text-secondary);
  font-variant-numeric: tabular-nums;
}

.time-label {
  display: inline-block;
  width: 30px;
  font-size: 11px;
  color: var(--text-disabled);
}
</style>