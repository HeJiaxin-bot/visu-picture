<template>
  <div id="notificationPage">
    <!-- 顶部页头：普通态=标题+未读徽标；选择态=全选 | 标题 | 取消（手机信息式） -->
    <div class="page-header">
      <div class="title-line" :class="{ selecting: selectMode }">
        <template v-if="selectMode">
          <a-button type="link" class="mode-btn" @click="selectAll">
            {{ isAllSelected ? '取消全选' : '全选' }}
          </a-button>
          <h2 class="page-title">消息中心</h2>
          <a-button type="link" class="mode-btn" @click="exitSelectMode">取消</a-button>
        </template>
        <template v-else>
          <h2 class="page-title">消息中心</h2>
          <span v-if="unreadCount > 0" class="title-badge">{{ unreadCount }} 条未读</span>
        </template>
      </div>
      <p class="page-sub">你的创作动态与审核结果都在这里</p>
    </div>

    <!-- 分类标签：全部 / 审核通知 / 系统通知 / 未读 -->
    <div class="tab-bar">
      <div
        v-for="item in categories"
        :key="item.key"
        class="tab-pill"
        :class="{ active: category === item.key }"
        @click="switchCategory(item.key)"
      >
        <component :is="item.icon" class="tab-icon" />
        {{ item.label }}
        <span v-if="item.key === 'unread' && unreadCount > 0" class="tab-count">
          {{ unreadCount }}
        </span>
      </div>
      <a-button
        v-if="unreadCount > 0 && !selectMode"
        class="read-all-btn"
        type="text"
        @click="handleReadAll"
      >
        <CheckOutlined />
        全部已读
      </a-button>
      <!-- 进入选择模式：打勾正方形图标（手机信息式） -->
      <a-button
        v-if="!selectMode"
        class="select-mode-btn"
        type="text"
        title="选择消息"
        @click="selectMode = true"
      >
        <CheckSquareOutlined />
      </a-button>
    </div>

    <!-- 通知列表卡片 -->
    <div class="notify-card">
      <a-spin :spinning="loading">
        <template v-if="list.length > 0">
          <div
            v-for="item in list"
            :key="item.id"
            class="notify-row"
            :class="{ unread: item.isRead === 0, selecting: selectMode }"
            @click="selectMode ? toggleSelect(item) : handleClick(item)"
          >
            <!-- 选择模式：行首勾选框 -->
            <div v-if="selectMode" class="row-check" @click.stop>
              <a-checkbox
                :checked="selectedIds.includes(String(item.id))"
                @change="toggleSelect(item)"
              />
            </div>
            <!-- 左侧类型图标 / 缩略图 -->
            <div class="row-icon" :class="iconClass(item)">
              <img v-if="item.picThumbnail" :src="item.picThumbnail" class="row-thumb-img" alt="" />
              <component v-else :is="iconComponent(item)" class="icon-inner" />
            </div>
            <div class="row-body">
              <div class="row-title">
                <span class="title-text">{{ item.title }}</span>
                <span class="type-tag" :class="iconClass(item)">{{ typeText(item) }}</span>
              </div>
              <div class="row-desc">{{ item.content }}</div>
              <div class="row-meta">
                <span class="row-time">{{ formatTime(item.createTime) }}</span>
                <span v-if="item.picId" class="row-link">
                  <RightOutlined />
                  查看图片
                </span>
                <span
                  v-if="!selectMode"
                  class="row-del"
                  title="删除这条消息"
                  @click.stop="handleDelete(item)"
                >
                  <DeleteOutlined />
                </span>
              </div>
            </div>
            <span v-if="item.isRead === 0 && !selectMode" class="row-dot"></span>
          </div>
        </template>
        <a-empty v-else-if="!loading" description="暂无消息" />
      </a-spin>

      <!-- 加载更多 -->
      <div v-if="total > PAGE_SIZE" class="pager-box">
        <a-pagination
          :current="current"
          :page-size="PAGE_SIZE"
          :total="total"
          :show-size-changer="false"
          :show-total="(t: number) => `共 ${t} 条`"
          @change="handlePageChange"
        />
      </div>
    </div>

    <!-- 选择模式：底部悬浮操作条（手机信息式） -->
    <div v-if="selectMode" class="select-bar">
      <div class="select-bar-inner">
        <button class="bar-action" :disabled="!selectedIds.length" @click="doBatchRead">
          <CheckSquareOutlined class="bar-icon" />
          <span>标记已读</span>
        </button>
        <span class="bar-divider" />
        <button class="bar-action danger" :disabled="!selectedIds.length" @click="doBatchDelete">
          <DeleteOutlined class="bar-icon" />
          <span>删除</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  AppstoreOutlined,
  AuditOutlined,
  BellOutlined,
  CheckOutlined,
  CheckSquareOutlined,
  DeleteOutlined,
  GiftOutlined,
  HeartOutlined,
  MailOutlined,
  PictureOutlined,
  RightOutlined,
} from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import {
  pageMyNotifications,
  countMyUnread,
  markNotificationRead,
  markAllNotificationRead,
  deleteNotification,
  markReadBatch,
  deleteBatch,
} from '@/api/notification.ts'
import type { NotificationVO } from '@/api/notification.ts'

/** 每页条数 */
const PAGE_SIZE = 10

/** 分类标签定义（与后端 NotificationCategoryEnum 对应） */
type CategoryKey = 'all' | 'review' | 'system' | 'unread'
const categories: { key: CategoryKey; label: string; icon: any }[] = [
  { key: 'all', label: '全部', icon: AppstoreOutlined },
  { key: 'review', label: '审核通知', icon: AuditOutlined },
  { key: 'system', label: '系统通知', icon: BellOutlined },
  { key: 'unread', label: '未读', icon: MailOutlined },
]

const router = useRouter()
const category = ref<CategoryKey>('all')
const list = ref<NotificationVO[]>([])
const loading = ref(false)
const current = ref(1)
const total = ref(0)
const unreadCount = ref(0)

// 类型映射：图标 + 颜色 + 文案
const typeMeta: Record<string, { icon: any; cls: string; text: string }> = {
  review: { icon: PictureOutlined, cls: 'type-review', text: '审核' },
  audit: { icon: AuditOutlined, cls: 'type-audit', text: '待审核' },
  system: { icon: BellOutlined, cls: 'type-system', text: '系统' },
  interact: { icon: HeartOutlined, cls: 'type-interact', text: '互动' },
}
const defaultMeta = { icon: GiftOutlined, cls: 'type-default', text: '通知' }

const metaOf = (item: NotificationVO) => typeMeta[item.type ?? ''] ?? defaultMeta
const iconComponent = (item: NotificationVO) => metaOf(item).icon
const iconClass = (item: NotificationVO) => metaOf(item).cls
const typeText = (item: NotificationVO) => metaOf(item).text

const refreshUnread = async () => {
  try {
    const res = await countMyUnread()
    if (res.code === 0 && res.data != null) {
      unreadCount.value = res.data
    }
  } catch {
    // 静默
  }
}

/** 拉取当前页数据（服务端分页，每页 PAGE_SIZE 条） */
const fetchList = async (page = current.value) => {
  loading.value = true
  try {
    const res = await pageMyNotifications({
      current: page,
      pageSize: PAGE_SIZE,
      category: category.value,
    })
    if (res.code === 0 && res.data) {
      list.value = res.data.records ?? []
      total.value = Number(res.data.total ?? 0)
      current.value = page
      // 当前页被清空（如标记已读后）时回退到上一页
      if (list.value.length === 0 && page > 1) {
        await fetchList(page - 1)
      }
    } else {
      message.error(res.message ?? '加载消息失败')
    }
  } catch {
    message.error('加载消息失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

/** 分页器切换（翻页清空勾选，避免跨页误删） */
const handlePageChange = (page: number) => {
  selectedIds.value = []
  fetchList(page)
}

/** 切换分类标签：回到第一页重新查询 */
const switchCategory = (key: CategoryKey) => {
  if (category.value === key) {
    return
  }
  category.value = key
  selectedIds.value = []
  list.value = []
  total.value = 0
  fetchList(1)
}

// 点击消息：普通态=标记已读并跳转；长按触发后的那次点击已抑制
// - audit（待审核提醒，发给管理员）→ 图片管理页处理审核
// - review（审核结果，发给作者）→ 图片详情页（带 from=notify，仅保留编辑）
const handleClick = async (item: NotificationVO) => {
  if (suppressNextClick) {
    suppressNextClick = false
    return
  }
  if (item.isRead === 0 && item.id != null) {
    await markNotificationRead(item.id)
    item.isRead = 1
    unreadCount.value = Math.max(unreadCount.value - 1, 0)
  }
  if (item.type === 'audit') {
    router.push('/admin/pictureManage')
  } else if (item.picId) {
    router.push({ path: `/picture/${item.picId}`, query: { from: 'notify' } })
  }
}

// ==================== 选择模式（长按消息或右上角勾选图标进入，手机信息式） ====================
const selectMode = ref(false)
const selectedIds = ref<string[]>([])
const isAllSelected = computed(
  () => list.value.length > 0 && list.value.every((p) => selectedIds.value.includes(String(p.id))),
)

const exitSelectMode = () => {
  selectMode.value = false
  selectedIds.value = []
}

const toggleSelect = (item: NotificationVO) => {
  // 长按触发后的那次点击会被抑制，这里只做纯粹的勾选切换
  if (suppressNextClick) {
    suppressNextClick = false
    return
  }
  const id = String(item.id)
  selectedIds.value = selectedIds.value.includes(id)
    ? selectedIds.value.filter((v) => v !== id)
    : [...selectedIds.value, id]
}

const selectAll = () => {
  if (isAllSelected.value) {
    selectedIds.value = []
  } else {
    selectedIds.value = list.value.map((p) => String(p.id))
  }
}

// 长按 500ms 进入选择模式并选中该条（移动端习惯）
let pressTimer: number | null = null
let pressStart = { x: 0, y: 0 }
let suppressNextClick = false

const onRowPointerDown = (e: PointerEvent, item: NotificationVO) => {
  if (selectMode.value) return
  pressStart = { x: e.clientX, y: e.clientY }
  pressTimer = window.setTimeout(() => {
    pressTimer = null
    suppressNextClick = true
    selectMode.value = true
    const id = String(item.id)
    if (!selectedIds.value.includes(id)) {
      selectedIds.value = [...selectedIds.value, id]
    }
  }, 500)
}

const onRowPointerMove = (e: PointerEvent) => {
  if (pressTimer == null) return
  // 移动超过 10px 视为滚动，取消长按
  if (Math.abs(e.clientX - pressStart.x) > 10 || Math.abs(e.clientY - pressStart.y) > 10) {
    clearTimeout(pressTimer)
    pressTimer = null
  }
}

const onRowPointerUp = () => {
  if (pressTimer != null) {
    clearTimeout(pressTimer)
    pressTimer = null
  }
}

// 批量标记已读
const doBatchRead = async () => {
  if (!selectedIds.value.length) return
  const res = await markReadBatch(selectedIds.value.map(Number))
  if (res.code === 0) {
    message.success(`已将 ${res.data ?? 0} 条消息标为已读`)
    list.value = list.value.map((n) =>
      selectedIds.value.includes(String(n.id)) ? { ...n, isRead: 1 } : n,
    )
    unreadCount.value = Math.max(unreadCount.value - (res.data ?? 0), 0)
  } else {
    message.error(res.message ?? '操作失败')
  }
}

// 批量删除
const doBatchDelete = async () => {
  if (!selectedIds.value.length) return
  const res = await deleteBatch(selectedIds.value.map(Number))
  if (res.code === 0) {
    message.success(`已删除 ${res.data ?? 0} 条消息`)
    await refreshUnread()
    selectedIds.value = []
    list.value = []
    total.value = 0
    fetchList(current.value)
  } else {
    message.error(res.message ?? '删除失败')
  }
}

/** 删除单条通知：本地移除 + 未读数联动 */
const handleDelete = async (item: NotificationVO) => {
  if (item.id == null) return
  const res = await deleteNotification(item.id)
  if (res.code === 0) {
    message.success('已删除')
    if (item.isRead === 0) {
      unreadCount.value = Math.max(unreadCount.value - 1, 0)
    }
    list.value = list.value.filter((n) => n.id !== item.id)
    total.value = Math.max(total.value - 1, 0)
    // 当前页被删空时回退一页
    if (list.value.length === 0 && current.value > 1) {
      fetchList(current.value - 1)
    }
  } else {
    message.error(res.message ?? '删除失败')
  }
}

const handleReadAll = async () => {
  const res = await markAllNotificationRead()
  if (res.code === 0) {
    message.success('已全部标记为已读')
    unreadCount.value = 0
    // 当前分类下刷新列表（未读分类会随之清空）
    list.value = []
    total.value = 0
    fetchList(1)
  } else {
    message.error(res.message ?? '操作失败')
  }
}

// 相对时间
const formatTime = (value?: string) => {
  if (!value) return ''
  const d = new Date(value)
  if (isNaN(d.getTime())) return ''
  const diff = Date.now() - d.getTime()
  const min = Math.floor(diff / 60000)
  if (min < 1) return '刚刚'
  if (min < 60) return `${min} 分钟前`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour} 小时前`
  const day = Math.floor(hour / 24)
  if (day < 7) return `${day} 天前`
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

onMounted(() => {
  fetchList()
  refreshUnread()
})
</script>

<style scoped>
#notificationPage {
  max-width: 720px;
  margin: 0 auto;
}

/* ===== 页头 ===== */
.page-header {
  text-align: center;
  margin-bottom: 24px;
}

/* ===== 页头：标题 + 未读数徽标（无大图标） ===== */
.title-line {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.page-title {
  margin: 0;
  font-size: 24px;
  letter-spacing: 0.5px;
}

.title-badge {
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: #1677ff;
  background: rgba(22, 119, 255, 0.1);
  border: 1px solid rgba(22, 119, 255, 0.2);
}

.page-sub {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--text-secondary);
}

/* ===== 分类标签：全部 / 审核通知 / 系统通知 / 未读 ===== */
.tab-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.tab-pill {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 16px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;
  white-space: nowrap;
}

.tab-icon {
  font-size: 14px;
  opacity: 0.75;
}

.tab-pill:hover {
  border-color: var(--accent);
  color: var(--accent);
  transform: translateY(-1px);
}

.tab-pill:hover .tab-icon {
  opacity: 1;
}

.tab-pill.active {
  color: #fff;
  background: linear-gradient(135deg, #40a9ff, #1677ff);
  border-color: transparent;
  box-shadow: 0 4px 14px rgba(22, 119, 255, 0.35);
}

.tab-pill.active .tab-icon {
  opacity: 1;
}

.tab-count {
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  background: rgba(255, 255, 255, 0.25);
}

.tab-pill:not(.active) .tab-count {
  color: #ff4d4f;
  background: rgba(255, 77, 79, 0.12);
}

.read-all-btn {
  margin-left: auto;
  font-size: 13px;
  color: var(--text-secondary);
}

.read-all-btn:hover {
  color: var(--accent);
}

/* ===== 通知卡片 ===== */
.notify-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  box-shadow: var(--card-shadow);
  padding: 6px 18px;
  min-height: 300px;
}

/* 通知行：选择模式下行首为勾选框，长按消息可进入选择模式 */
.notify-row {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 16px 12px;
  border-bottom: 1px solid var(--border-color);
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.18s ease;
}

.notify-row.selecting {
  padding-left: 6px;
}

/* 选择模式：行首勾选框 */
.row-check {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding-top: 14px;
}

.notify-row:last-of-type {
  border-bottom: none;
}

.notify-row:hover {
  background: rgba(64, 169, 255, 0.07);
}

/* 未读行：左侧蓝色竖条 + 淡蓝底（叠在不透明卡片底色上，保证左滑遮挡） */
.notify-row.unread {
  background-image: linear-gradient(rgba(64, 169, 255, 0.045), rgba(64, 169, 255, 0.045));
}

.notify-row.unread::before {
  content: '';
  position: absolute;
  left: 2px;
  top: 14px;
  bottom: 14px;
  width: 3px;
  border-radius: 2px;
  background: linear-gradient(180deg, #40a9ff, #1677ff);
}

/* 左侧类型图标 / 缩略图 */
.row-icon {
  flex-shrink: 0;
  width: 52px;
  height: 52px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  overflow: hidden;
  background: var(--bg-body, #f0f2f5);
}

.row-thumb-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.icon-inner {
  color: currentColor;
}

/* 类型配色 */
.row-icon.type-review {
  color: #1677ff;
  background: linear-gradient(135deg, rgba(22, 119, 255, 0.12), rgba(64, 169, 255, 0.18));
}

.row-icon.type-system {
  color: #722ed1;
  background: linear-gradient(135deg, rgba(114, 46, 209, 0.12), rgba(114, 46, 209, 0.18));
}

.row-icon.type-audit {
  color: #fa8c16;
  background: linear-gradient(135deg, rgba(250, 140, 22, 0.14), rgba(250, 140, 22, 0.2));
}

.row-icon.type-interact {
  color: #eb2f96;
  background: linear-gradient(135deg, rgba(235, 47, 150, 0.12), rgba(235, 47, 150, 0.18));
}

.row-icon.type-default {
  color: #fa8c16;
  background: linear-gradient(135deg, rgba(250, 140, 22, 0.12), rgba(250, 140, 22, 0.18));
}

.row-body {
  flex: 1;
  min-width: 0;
}

.row-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary-light);
}

.title-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.type-tag {
  flex-shrink: 0;
  padding: 1px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  line-height: 16px;
}

.type-tag.type-review {
  color: #1677ff;
  background: rgba(22, 119, 255, 0.1);
}

.type-tag.type-system {
  color: #722ed1;
  background: rgba(114, 46, 209, 0.1);
}

.type-tag.type-audit {
  color: #fa8c16;
  background: rgba(250, 140, 22, 0.12);
}

.type-tag.type-interact {
  color: #eb2f96;
  background: rgba(235, 47, 150, 0.1);
}

.type-tag.type-default {
  color: #fa8c16;
  background: rgba(250, 140, 22, 0.1);
}

.row-desc {
  margin-top: 5px;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 19px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  /* 长串无空格文本（如连续数字）也能强制换行，避免撑破卡片 */
  word-break: break-word;
  overflow-wrap: anywhere;
}

.row-meta {
  margin-top: 7px;
  display: flex;
  align-items: center;
  gap: 14px;
}

/* 桌面悬浮删除：悬停行时淡入 */
.row-del {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 8px;
  font-size: 13px;
  color: var(--text-secondary);
  opacity: 0;
  transition:
    opacity 0.18s ease,
    color 0.18s ease,
    background 0.18s ease;
}

.notify-row:hover .row-del {
  opacity: 1;
}

.row-del:hover {
  color: #fff;
  background: #ff4d4f;
}

.row-time {
  font-size: 12px;
  color: var(--text-disabled);
}

.row-link {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 12px;
  color: var(--accent);
}

.row-dot {
  position: absolute;
  top: 19px;
  right: 8px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ff4d4f;
  box-shadow: 0 0 0 3px rgba(255, 77, 79, 0.15);
}

/* 分页器：右对齐，与卡片底部留白 */
.pager-box {
  display: flex;
  justify-content: flex-end;
  padding: 16px 6px 6px;
  border-top: 1px solid var(--border-color);
  margin-top: 4px;
}

/* ===== 选择模式：底部悬浮操作条 ===== */
.select-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 28px;
  display: flex;
  justify-content: center;
  z-index: 50;
  pointer-events: none;
}

.select-bar-inner {
  pointer-events: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 26px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 999px;
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.35);
}

.bar-action {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  border: none;
  background: transparent;
  color: var(--text-primary-light);
  font-size: 12px;
  font-family: inherit;
  cursor: pointer;
  padding: 2px 18px;
  border-radius: 12px;
  transition: color 0.2s ease, background 0.2s ease;
}

.bar-action:hover:not(:disabled) {
  background: rgba(64, 169, 255, 0.08);
  color: var(--accent);
}

.bar-action:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.bar-action.danger:hover:not(:disabled) {
  color: #ff4d4f;
  background: rgba(255, 77, 79, 0.08);
}

.bar-icon {
  font-size: 20px;
}

.bar-divider {
  width: 1px;
  height: 30px;
  background: var(--border-color);
}

.mode-btn {
  padding-inline: 8px;
  font-size: 14px;
}

.title-line.selecting {
  display: flex;
  justify-content: space-between;
  width: 100%;
}

html.dark .bar-action {
  color: var(--text-primary);
}

html.dark .select-bar-inner {
  background: #2d2d2d;
}

/* ===== 深色模式 ===== */
html.dark .notify-card {
  background: rgba(45, 45, 45, 0.9);
  box-shadow: var(--card-shadow);
}

html.dark .notify-row {
  border-bottom-color: rgba(255, 255, 255, 0.06);
}

html.dark .notify-row.unread {
  background: rgba(64, 169, 255, 0.08);
}

html.dark .row-title {
  color: var(--text-primary);
}

html.dark .tab-pill {
  background: rgba(45, 45, 45, 0.9);
}

html.dark .tab-pill:not(.active) .tab-count {
  background: rgba(255, 77, 79, 0.2);
}
</style>
