<template>
  <div id="spaceManagePage">
    <AdminShell
      title="空间管理"
      desc="查看与管理全站空间，可按名称、级别、类别、所属用户筛选"
      :icon="AppstoreOutlined"
      tint="#722ed1"
      :meta="`共 ${total} 个空间`"
    >
      <template #actions>
        <a-button type="primary" href="/add_space" target="_blank">
          <template #icon><PlusOutlined /></template>
          创建空间
        </a-button>
        <a-button class="ghost-btn" href="/space_analyze?queryPublic=1" target="_blank">
          <template #icon><FundOutlined /></template>
          分析公共图库
        </a-button>
        <a-button class="ghost-btn" href="/space_analyze?queryAll=1" target="_blank">
          <template #icon><ClusterOutlined /></template>
          分析全部空间
        </a-button>
      </template>

      <!-- 搜索表单 -->
      <template #filters>
        <a-form layout="inline" :model="searchParams" @finish="doSearch">
          <a-form-item label="空间名称">
            <a-input v-model:value="searchParams.spaceName" placeholder="请输入空间名称" allow-clear />
          </a-form-item>
          <a-form-item name="spaceLevel" label="空间级别">
            <a-select
              v-model:value="searchParams.spaceLevel"
              style="min-width: 160px"
              placeholder="请选择空间级别"
              :options="SPACE_LEVEL_OPTIONS"
              allow-clear
            />
          </a-form-item>
          <a-form-item label="空间类别" name="spaceType">
            <a-select
              v-model:value="searchParams.spaceType"
              :options="SPACE_TYPE_OPTIONS"
              placeholder="请输入空间类别"
              style="min-width: 160px"
              allow-clear
            />
          </a-form-item>
          <a-form-item label="用户 id">
            <a-input v-model:value="searchParams.userId" placeholder="请输入用户 id" allow-clear />
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
        :scroll="{ x: 1540 }"
        @change="doTableChange"
      >
        <template #bodyCell="{ column, record }">
          <!-- 空间：封面缩略图 / 首字头像 + 名称 + 类别 -->
          <template v-if="column.key === 'space'">
            <div class="space-cell">
              <img v-if="record.coverPicture" class="space-thumb" :src="record.coverPicture" alt="" />
              <span v-else class="space-avatar" aria-hidden="true">
                {{ (record.spaceName ?? '空').slice(0, 1) }}
              </span>
              <div class="cell-main">
                <div class="cell-name">{{ record.spaceName || '未命名空间' }}</div>
                <div class="cell-tags">
                  <span
                    class="soft-chip"
                    :class="record.spaceType === SPACE_TYPE_ENUM.TEAM ? 'chip-team' : ''"
                  >
                    {{ SPACE_TYPE_MAP[record.spaceType] }}
                  </span>
                </div>
              </div>
            </div>
          </template>
          <!-- 空间 id：等宽字体 + 快捷复制 -->
          <template v-else-if="column.key === 'id'">
            <span class="mono-text">{{ record.id }}</span>
            <button
              class="mini-copy"
              type="button"
              title="复制空间 id"
              aria-label="复制空间 id"
              @click="copyId(record.id, '空间 id')"
            >
              <CopyOutlined />
            </button>
          </template>
          <!-- 空间级别 -->
          <template v-else-if="column.key === 'spaceLevel'">
            <span class="level-chip" :class="levelClass(record.spaceLevel)">
              {{ SPACE_LEVEL_MAP[record.spaceLevel] }}
            </span>
          </template>
          <!-- 使用情况：容量条 + 数量条 -->
          <template v-else-if="column.key === 'spaceUseInfo'">
            <div class="usage-cell">
              <div class="usage-item">
                <div class="usage-head">
                  <span class="usage-label">存储</span>
                  <span class="usage-num">
                    {{ formatSize(record.totalSize) }} / {{ formatSize(record.maxSize) }}
                  </span>
                </div>
                <a-progress
                  :percent="usageRatio(record.totalSize, record.maxSize)"
                  :show-info="false"
                  size="small"
                  :stroke-color="barColor(usageRatio(record.totalSize, record.maxSize))"
                />
              </div>
              <div class="usage-item">
                <div class="usage-head">
                  <span class="usage-label">数量</span>
                  <span class="usage-num">{{ record.totalCount ?? 0 }} / {{ record.maxCount ?? '∞' }}</span>
                </div>
                <a-progress
                  :percent="usageRatio(record.totalCount, record.maxCount)"
                  :show-info="false"
                  size="small"
                  :stroke-color="barColor(usageRatio(record.totalCount, record.maxCount))"
                />
              </div>
            </div>
          </template>
          <!-- 用户 id -->
          <template v-else-if="column.key === 'userId'">
            <span class="mono-text">{{ record.userId ?? '—' }}</span>
            <button
              class="mini-copy"
              type="button"
              title="复制用户 id"
              aria-label="复制用户 id"
              @click="copyId(record.userId, '用户 id')"
            >
              <CopyOutlined />
            </button>
          </template>
          <!-- 创建时间 -->
          <template v-else-if="column.key === 'createTime'">
            <div class="time-cell">
              <span>{{ dayjs(record.createTime).format('YYYY-MM-DD') }}</span>
              <span class="time-sub">{{ dayjs(record.createTime).format('HH:mm') }}</span>
            </div>
          </template>
          <!-- 编辑时间 -->
          <template v-else-if="column.key === 'editTime'">
            <div class="time-cell">
              <span>{{ dayjs(record.editTime).format('YYYY-MM-DD') }}</span>
              <span class="time-sub">{{ dayjs(record.editTime).format('HH:mm') }}</span>
            </div>
          </template>
          <!-- 操作 -->
          <template v-else-if="column.key === 'action'">
            <a-space :size="6" wrap>
              <a-button
                class="row-btn ghost-btn"
                :href="`/space_analyze?spaceId=${record.id}`"
                target="_blank"
              >
                <template #icon><BarChartOutlined /></template>
                空间分析
              </a-button>
              <a-button
                class="row-btn ghost-btn"
                :href="`/add_space?id=${record.id}`"
                target="_blank"
              >
                <template #icon><EditOutlined /></template>
                编辑
              </a-button>
              <!-- 危险操作：与常规操作以分隔线隔开 -->
              <i class="btn-sep" aria-hidden="true" />
              <a-popconfirm
                title="确定删除该空间？"
                description="空间内的图片将一并失去归属，操作不可恢复"
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
import { deleteSpaceUsingPost, listSpaceByPageUsingPost } from '@/api/spaceController.ts'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import {
  AppstoreOutlined,
  BarChartOutlined,
  ClusterOutlined,
  CopyOutlined,
  DeleteOutlined,
  EditOutlined,
  FundOutlined,
  PlusOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import {
  SPACE_LEVEL_ENUM,
  SPACE_LEVEL_MAP,
  SPACE_LEVEL_OPTIONS,
  SPACE_TYPE_ENUM,
  SPACE_TYPE_MAP,
  SPACE_TYPE_OPTIONS,
} from '@/constants/space.ts'
import { formatSize } from '@/utils'
import AdminShell from '@/components/admin/AdminShell.vue'

const columns = [
  { title: '空间', key: 'space', width: 250 },
  { title: '空间 ID', key: 'id', width: 210 },
  { title: '级别', key: 'spaceLevel', width: 110 },
  { title: '使用情况', key: 'spaceUseInfo', width: 230 },
  { title: '用户 ID', key: 'userId', width: 200 },
  { title: '创建时间', key: 'createTime', width: 120 },
  { title: '编辑时间', key: 'editTime', width: 120 },
  { title: '操作', key: 'action', width: 300, fixed: 'right' },
]

// 定义数据
const dataList = ref<API.Space[]>([])
const total = ref(0)
const loading = ref(false)

// 搜索条件
const searchParams = reactive<API.SpaceQueryRequest>({
  current: 1,
  pageSize: 10,
  sortField: 'createTime',
  sortOrder: 'descend',
})

// 获取数据
const fetchData = async () => {
  loading.value = true
  try {
    const res = await listSpaceByPageUsingPost({
      ...searchParams,
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
    const res = await deleteSpaceUsingPost({ id })
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

// 复制 id
const copyId = async (id?: string | number, label = 'id') => {
  if (id == null) return
  try {
    await navigator.clipboard.writeText(String(id))
    message.success(`${label} 已复制`)
  } catch {
    message.error('复制失败，请手动选择复制')
  }
}

// 使用率：无上限时视为 0，超出取 100 封顶
const usageRatio = (used?: number, max?: number) => {
  if (!max || !used) return 0
  return Math.min(Math.round((used / max) * 100), 100)
}

// 容量条配色：紧张时转为橙 / 红
const barColor = (percent: number) => {
  if (percent >= 90) return { from: '#ff7875', to: '#cf1322' }
  if (percent >= 70) return { from: '#ffc069', to: '#fa8c16' }
  return { from: '#69c0ff', to: '#1677ff' }
}

// 空间级别配色
const levelClass = (level?: number) => {
  if (level === SPACE_LEVEL_ENUM.FLAGSHIP) return 'level-flagship'
  if (level === SPACE_LEVEL_ENUM.PROFESSIONAL) return 'level-pro'
  return 'level-common'
}
</script>

<style scoped>
.space-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.space-thumb {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border-radius: 12px;
  object-fit: cover;
  background: rgba(128, 128, 128, 0.12);
}

.space-avatar {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 17px;
  font-weight: 600;
  color: #722ed1;
  background: rgba(114, 46, 209, 0.12);
}

.cell-main {
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

.chip-team {
  color: #722ed1;
  background: rgba(114, 46, 209, 0.12);
}

.level-chip {
  font-size: 11.5px;
  line-height: 1;
  padding: 4px 10px;
  border-radius: 999px;
  white-space: nowrap;
}

.level-common {
  color: var(--text-secondary);
  background: rgba(128, 128, 128, 0.12);
}

.level-pro {
  color: #08979c;
  background: rgba(19, 194, 194, 0.14);
}

.level-flagship {
  color: #d48806;
  background: rgba(250, 173, 20, 0.16);
}

.usage-cell {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.usage-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  font-size: 11.5px;
}

.usage-label {
  color: var(--text-disabled);
}

.usage-num {
  color: var(--text-secondary);
  font-variant-numeric: tabular-nums;
}

.mono-text {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12.5px;
  color: var(--text-secondary);
}

.mini-copy {
  margin-left: 6px;
  border: none;
  background: transparent;
  color: var(--text-disabled);
  cursor: pointer;
  border-radius: 7px;
  /* 加大图标按钮的可点区域，避免过小的点击目标 */
  padding: 5px 7px;
  line-height: 1;
  transition:
    color 0.2s ease,
    background 0.2s ease;
}

.mini-copy:hover {
  color: var(--accent);
  background: rgba(22, 119, 255, 0.1);
}

.time-cell {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}

.time-sub {
  font-size: 12px;
  color: var(--text-disabled);
}
</style>