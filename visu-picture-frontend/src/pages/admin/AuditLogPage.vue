<template>
  <div id="auditLogPage">
    <div class="page-head">
      <h2 class="page-title">操作审计日志</h2>
      <p class="page-sub">
        记录关键写操作的操作人、目标、IP 与执行结果，用于事后追溯与安全排查
      </p>
    </div>

    <!-- 筛选 -->
    <a-card class="filter-card" :bordered="false">
      <a-form layout="inline" :model="query">
        <a-form-item label="模块">
          <a-select
            v-model:value="query.module"
            style="width: 140px"
            allow-clear
            placeholder="全部"
            @change="reload"
          >
            <a-select-option v-for="m in MODULES" :key="m" :value="m">{{ m }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="操作">
          <a-select
            v-model:value="query.action"
            style="width: 170px"
            allow-clear
            placeholder="全部"
            @change="reload"
          >
            <a-select-option v-for="a in ACTIONS" :key="a" :value="a">{{ a }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="操作人 ID">
          <a-input
            v-model:value="query.userId"
            style="width: 150px"
            allow-clear
            placeholder="用户 id"
            @press-enter="reload"
          />
        </a-form-item>
        <a-form-item label="结果">
          <a-select
            v-model:value="query.success"
            style="width: 100px"
            allow-clear
            placeholder="全部"
            @change="reload"
          >
            <a-select-option :value="1">成功</a-select-option>
            <a-select-option :value="0">失败</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="reload">查询</a-button>
          <a-button style="margin-left: 8px" @click="resetQuery">重置</a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <a-card class="table-card" :bordered="false">
      <a-table
        :columns="columns"
        :data-source="records"
        :loading="loading"
        :pagination="pagination"
        row-key="id"
        size="middle"
        :scroll="{ x: 1200 }"
        @change="onTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'success'">
            <a-tag :color="record.success === 1 ? 'green' : 'red'">
              {{ record.success === 1 ? '成功' : '失败' }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'action'">
            <a-tag>{{ record.action }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'target'">
            <span v-if="record.targetId">
              {{ record.targetType || '-' }} #{{ record.targetId }}
            </span>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.dataIndex === 'detail'">
            <a-tooltip :title="record.detail">
              <span class="detail-cell">{{ record.detail }}</span>
            </a-tooltip>
          </template>
          <template v-else-if="column.dataIndex === 'costMs'">
            <span :class="{ slow: (record.costMs ?? 0) > 1000 }">{{ record.costMs ?? 0 }} ms</span>
          </template>
          <template v-else-if="column.dataIndex === 'createTime'">
            {{ formatTime(record.createTime) }}
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { pageAuditLogs } from '@/api/interaction.ts'
import type { AuditLogVO } from '@/api/interaction.ts'

/** 与后端 @AuditLog 注解中实际使用的值保持一致 */
const MODULES = ['picture', 'user', 'space', 'notification']
const ACTIONS = [
  'delete',
  'update',
  'edit',
  'review',
  'comment_add',
  'comment_delete',
  'share_create',
  'share_revoke',
  'view_history_clear',
]

const columns = [
  { title: '时间', dataIndex: 'createTime', key: 'createTime', width: 160 },
  { title: '操作人', dataIndex: 'userAccount', key: 'userAccount', width: 140 },
  { title: '模块', dataIndex: 'module', key: 'module', width: 100 },
  { title: '操作', dataIndex: 'action', key: 'action', width: 140 },
  { title: '目标', dataIndex: 'target', key: 'target', width: 180 },
  { title: '详情', dataIndex: 'detail', key: 'detail', width: 280, ellipsis: true },
  { title: 'IP', dataIndex: 'ip', key: 'ip', width: 130 },
  { title: '耗时', dataIndex: 'costMs', key: 'costMs', width: 90 },
  { title: '结果', dataIndex: 'success', key: 'success', width: 90 },
]

const records = ref<AuditLogVO[]>([])
const loading = ref(false)
const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
  showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条`,
})

const query = reactive<{
  module?: string
  action?: string
  userId?: string
  success?: number
}>({})

const load = async () => {
  loading.value = true
  try {
    // 只把有值的筛选条件传给后端，避免空字符串被当成有效条件
    const userIdNum = query.userId ? Number(query.userId) : undefined
    const res = await pageAuditLogs({
      module: query.module || undefined,
      action: query.action || undefined,
      userId: Number.isFinite(userIdNum) ? userIdNum : undefined,
      current: pagination.current,
      pageSize: pagination.pageSize,
    })
    if (res.code === 0) {
      let list = res.data?.records ?? []
      // success 是前端本地筛选（后端未提供该筛选参数），在当页数据上过滤
      if (query.success != null) {
        list = list.filter((r) => r.success === query.success)
      }
      records.value = list
      pagination.total = res.data?.total ?? 0
    } else {
      message.error(res.message ?? '查询失败')
    }
  } catch (e: any) {
    message.error('查询失败：' + (e?.message ?? ''))
  } finally {
    loading.value = false
  }
}

const reload = () => {
  pagination.current = 1
  load()
}

const resetQuery = () => {
  query.module = undefined
  query.action = undefined
  query.userId = undefined
  query.success = undefined
  reload()
}

const onTableChange = (pag: { current?: number; pageSize?: number }) => {
  pagination.current = pag.current ?? 1
  pagination.pageSize = pag.pageSize ?? 20
  load()
}

const formatTime = (time?: string) => {
  if (!time) return '-'
  let d = new Date(time)
  if (isNaN(d.getTime())) d = new Date(time.replace(/-/g, '/').replace('T', ' '))
  if (isNaN(d.getTime())) return '-'
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(
    d.getMinutes(),
  )}:${p(d.getSeconds())}`
}

onMounted(load)
</script>

<style scoped>
#auditLogPage {
  max-width: 1440px;
  margin: 0 auto 24px;
  padding: 0 16px;
}

.page-head {
  margin: 8px 0 16px;
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

.filter-card,
.table-card {
  border-radius: 14px;
  box-shadow: var(--card-shadow);
}

.filter-card {
  margin-bottom: 14px;
}

.filter-card :deep(.ant-form-item) {
  margin-bottom: 8px;
}

.detail-cell {
  display: inline-block;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}

.slow {
  color: #fa8c16;
  font-weight: 600;
}

html.dark .page-title {
  color: var(--text-primary);
}
</style>