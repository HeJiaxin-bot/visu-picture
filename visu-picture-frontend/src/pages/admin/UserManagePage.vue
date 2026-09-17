<template>
  <div id="userManagePage">
    <AdminShell
      title="用户管理"
      desc="查看平台注册用户，支持按 id、账号、用户名检索"
      :icon="TeamOutlined"
      tint="#1677ff"
      :meta="`共 ${total} 位用户`"
    >
      <template #actions>
        <a-button class="ghost-btn" @click="fetchData">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
      </template>

      <!-- 搜索表单 -->
      <template #filters>
        <a-form layout="inline" :model="searchParams" @finish="doSearch">
          <a-form-item label="用户 id">
            <a-input v-model:value="searchParams.id" placeholder="请输入用户 id" allow-clear />
          </a-form-item>
          <a-form-item label="账号">
            <a-input v-model:value="searchParams.userAccount" placeholder="输入账号" allow-clear />
          </a-form-item>
          <a-form-item label="用户名">
            <a-input v-model:value="searchParams.userName" placeholder="输入用户名" allow-clear />
          </a-form-item>
          <a-form-item>
            <a-button type="primary" html-type="submit">
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
        :scroll="{ x: 1060 }"
        @change="doTableChange"
      >
        <template #bodyCell="{ column, record }">
          <!-- 用户：头像 + 昵称 + 角色标识 -->
          <template v-if="column.key === 'user'">
            <div class="user-cell">
              <a-avatar :src="record.userAvatar" :size="40" class="cell-avatar">
                <template #icon><UserOutlined /></template>
              </a-avatar>
              <div class="cell-main">
                <div class="cell-name">{{ record.userName || '未命名用户' }}</div>
                <div class="cell-tags">
                  <span v-if="record.userRole === 'admin'" class="role-chip role-admin">管理员</span>
                  <span v-else class="role-chip">普通用户</span>
                  <span v-if="isVip(record)" class="role-chip role-vip">会员</span>
                </div>
              </div>
            </div>
          </template>
          <!-- 账号 -->
          <template v-else-if="column.key === 'userAccount'">
            <a-tag class="account-chip">{{ record.userAccount }}</a-tag>
          </template>
          <!-- 用户 id：等宽字体 + 快捷复制 -->
          <template v-else-if="column.key === 'id'">
            <span class="mono-text">{{ record.id }}</span>
            <button class="mini-copy" type="button" title="复制 id" @click="copyId(record.id)">
              <CopyOutlined />
            </button>
          </template>
          <!-- 简介 -->
          <template v-else-if="column.key === 'userProfile'">
            <span class="muted-text">{{ record.userProfile || '—' }}</span>
          </template>
          <!-- 创建时间 -->
          <template v-else-if="column.key === 'createTime'">
            <div class="time-cell">
              <span>{{ dayjs(record.createTime).format('YYYY-MM-DD') }}</span>
              <span class="time-sub">{{ dayjs(record.createTime).format('HH:mm:ss') }}</span>
            </div>
          </template>
          <!-- 操作 -->
          <template v-else-if="column.key === 'action'">
            <a-popconfirm
              title="确定删除该用户？"
              description="删除后该账号将无法登录，操作不可恢复"
              ok-text="删除"
              cancel-text="取消"
              ok-type="danger"
              @confirm="doDelete(record.id)"
            >
              <a-button danger size="small" type="text" class="row-btn">
                <template #icon><DeleteOutlined /></template>
                删除
              </a-button>
            </a-popconfirm>
          </template>
        </template>
      </a-table>
    </AdminShell>
  </div>
</template>
<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { deleteUserUsingPost, listUserVoByPageUsingPost } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import {
  CopyOutlined,
  DeleteOutlined,
  ReloadOutlined,
  SearchOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import AdminShell from '@/components/admin/AdminShell.vue'

const columns = [
  { title: '用户', key: 'user', width: 230 },
  { title: '账号', key: 'userAccount', width: 160 },
  { title: '用户 ID', key: 'id', width: 210 },
  { title: '简介', key: 'userProfile', ellipsis: true },
  { title: '创建时间', key: 'createTime', width: 130 },
  { title: '操作', key: 'action', width: 100, fixed: 'right' },
]

// 定义数据
const dataList = ref<API.UserVO[]>([])
const total = ref(0)
const loading = ref(false)

// 搜索条件
const searchParams = reactive<API.UserQueryRequest>({
  current: 1,
  pageSize: 10,
  sortField: 'createTime',
  sortOrder: 'ascend',
})

// 获取数据
const fetchData = async () => {
  loading.value = true
  try {
    const res = await listUserVoByPageUsingPost({
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
const doDelete = async (id: string) => {
  if (!id) {
    return
  }
  const res = await deleteUserUsingPost({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
    // 刷新数据
    fetchData()
  } else {
    message.error('删除失败')
  }
}

// 复制用户 id
const copyId = async (id?: string | number) => {
  if (id == null) return
  try {
    await navigator.clipboard.writeText(String(id))
    message.success('用户 id 已复制')
  } catch {
    message.error('复制失败，请手动选择复制')
  }
}

// 会员有效期是否未过
const isVip = (record: API.UserVO) => {
  if (!record.vipExpireTime) return false
  const expire = new Date(String(record.vipExpireTime).replace(/-/g, '/').replace('T', ' '))
  return !isNaN(expire.getTime()) && expire.getTime() > Date.now()
}
</script>

<style scoped>
.user-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cell-avatar {
  flex-shrink: 0;
  background: rgba(22, 119, 255, 0.1);
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
  margin-top: 4px;
  flex-wrap: wrap;
}

.role-chip {
  font-size: 11px;
  line-height: 1;
  padding: 3px 8px;
  border-radius: 999px;
  color: var(--text-secondary);
  background: rgba(128, 128, 128, 0.12);
}

.role-admin {
  color: #d48806;
  background: rgba(250, 173, 20, 0.16);
}

.role-vip {
  color: #722ed1;
  background: rgba(114, 46, 209, 0.14);
}

.account-chip {
  font-family: 'JetBrains Mono', Consolas, monospace;
  border-radius: 999px;
  padding: 2px 10px;
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
  border-radius: 6px;
  padding: 3px 5px;
  line-height: 1;
  transition:
    color 0.2s ease,
    background 0.2s ease;
}

.mini-copy:hover {
  color: var(--accent);
  background: rgba(22, 119, 255, 0.1);
}

.muted-text {
  color: var(--text-secondary);
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

.row-btn {
  border-radius: 8px;
}
</style>