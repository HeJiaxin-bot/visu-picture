<template>
  <div id="notificationPage">
    <!-- 顶部页头 -->
    <div class="page-header">
      <div class="header-badge">
        <BellOutlined />
      </div>
      <h2 class="page-title">消息中心</h2>
      <p class="page-sub">你的创作动态与审核结果都在这里</p>
    </div>

    <!-- 全部/未读切换：药丸组 -->
    <div class="tab-bar">
      <div
        class="tab-pill"
        :class="{ active: tab === 'all' }"
        @click="switchTab('all')"
      >
        全部
      </div>
      <div
        class="tab-pill"
        :class="{ active: tab === 'unread' }"
        @click="switchTab('unread')"
      >
        未读
        <span v-if="unreadCount > 0" class="tab-count">{{ unreadCount }}</span>
      </div>
      <a-button
        v-if="unreadCount > 0"
        class="read-all-btn"
        type="link"
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
      <div v-if="hasMore" class="more-box">
        <a-button :loading="loading" @click="loadMore">加载更多</a-button>
      </div>
      <div v-else-if="list.length > 0" class="all-loaded">- 已经到底啦 -</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  BellOutlined,
  CheckOutlined,
  GiftOutlined,
  HeartOutlined,
  PictureOutlined,
  RightOutlined,
} from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import {
  listMyNotifications,
  countMyUnread,
  markNotificationRead,
  markAllNotificationRead,
} from '@/api/notification.ts'
import type { NotificationVO } from '@/api/notification.ts'

const PAGE_SIZE = 15

const router = useRouter()
const tab = ref<'all' | 'unread'>('all')
const list = ref<NotificationVO[]>([])
const loading = ref(false)
const current = ref(1)
const hasMore = ref(false)
const unreadCount = ref(0)

// 类型映射：图标 + 颜色 + 文案
const typeMeta: Record<string, { icon: any; cls: string; text: string }> = {
  review: { icon: PictureOutlined, cls: 'type-review', text: '审核' },
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

const fetchList = async () => {
  loading.value = true
  try {
    const res = await listMyNotifications({
      current: current.value,
      pageSize: PAGE_SIZE,
      onlyUnread: tab.value === 'unread',
    })
    if (res.code === 0 && res.data) {
      list.value.push(...res.data)
      hasMore.value = res.data.length >= PAGE_SIZE
    } else {
      message.error(res.message ?? '加载消息失败')
    }
  } catch {
    message.error('加载消息失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const reload = () => {
  list.value = []
  current.value = 1
  hasMore.value = false
  fetchList()
}

const switchTab = (t: 'all' | 'unread') => {
  if (tab.value === t) return
  tab.value = t
  reload()
}

const loadMore = () => {
  current.value += 1
  fetchList()
}

// 点击消息：标记已读并跳转（带 from=notify，详情页据此只保留「编辑」操作）
const handleClick = async (item: NotificationVO) => {
  if (item.isRead === 0 && item.id != null) {
    await markNotificationRead(item.id)
    item.isRead = 1
    unreadCount.value = Math.max(unreadCount.value - 1, 0)
  }
  if (item.picId) {
    router.push({ path: `/picture/${item.picId}`, query: { from: 'notify' } })
  }
}

const handleReadAll = async () => {
  const res = await markAllNotificationRead()
  if (res.code === 0) {
    message.success('已全部标记为已读')
    unreadCount.value = 0
    reload()
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

.header-badge {
  width: 56px;
  height: 56px;
  margin: 0 auto 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 26px;
  color: #fff;
  background: linear-gradient(135deg, #40a9ff, #1677ff);
  box-shadow: 0 10px 26px rgba(22, 119, 255, 0.35);
}

.page-title {
  margin: 0 0 6px;
  font-size: 26px;
}

.page-sub {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
}

/* ===== 全部/未读切换 ===== */
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
  padding: 7px 18px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;
}

.tab-pill:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.tab-pill.active {
  color: #fff;
  background: linear-gradient(135deg, #40a9ff, #1677ff);
  border-color: transparent;
  box-shadow: 0 4px 14px rgba(22, 119, 255, 0.35);
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
}

/* ===== 通知卡片 ===== */
.notify-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  box-shadow: var(--card-shadow);
  padding: 10px 18px;
  min-height: 300px;
}

/* 通知行 */
.notify-row {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 15px 10px;
  border-bottom: 1px solid var(--border-color);
  cursor: pointer;
  transition: background 0.15s ease, transform 0.15s ease;
}

.notify-row:last-of-type {
  border-bottom: none;
}

.notify-row:hover {
  background: rgba(64, 169, 255, 0.06);
}

.notify-row.unread {
  background: rgba(64, 169, 255, 0.04);
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

.more-box {
  display: flex;
  justify-content: center;
  padding-top: 16px;
}

.all-loaded {
  text-align: center;
  color: var(--text-disabled);
  font-size: 12px;
  padding: 16px 0 6px;
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
