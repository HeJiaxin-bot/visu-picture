<template>
  <div id="notificationPage">
    <!-- 顶部页头：去掉大图标，标题 + 计数更清爽 -->
    <div class="page-header">
      <div class="title-line">
        <h2 class="page-title">消息中心</h2>
        <span v-if="unreadCount > 0" class="title-badge">{{ unreadCount }} 条未读</span>
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
        v-if="unreadCount > 0"
        class="read-all-btn"
        type="text"
        @click="handleReadAll"
      >
        <CheckOutlined />
        全部已读
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
            :class="{ unread: item.isRead === 0 }"
            @click="handleClick(item)"
          >
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
              </div>
            </div>
            <span v-if="item.isRead === 0" class="row-dot"></span>
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  AppstoreOutlined,
  AuditOutlined,
  BellOutlined,
  CheckOutlined,
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

/** 分页器切换 */
const handlePageChange = (page: number) => {
  fetchList(page)
}

/** 切换分类标签：回到第一页重新查询 */
const switchCategory = (key: CategoryKey) => {
  if (category.value === key) {
    return
  }
  category.value = key
  list.value = []
  total.value = 0
  fetchList(1)
}

// 点击消息：标记已读并跳转
// - audit（待审核提醒，发给管理员）→ 图片管理页处理审核
// - review（审核结果，发给作者）→ 图片详情页（带 from=notify，仅保留编辑）
const handleClick = async (item: NotificationVO) => {
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

/* 通知行 */
.notify-row {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 16px 12px;
  border-bottom: 1px solid var(--border-color);
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.18s ease;
}

.notify-row:last-of-type {
  border-bottom: none;
}

.notify-row:hover {
  background: rgba(64, 169, 255, 0.07);
}

/* 未读行：左侧蓝色竖条 + 淡蓝底，一眼可辨 */
.notify-row.unread {
  background: rgba(64, 169, 255, 0.045);
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
