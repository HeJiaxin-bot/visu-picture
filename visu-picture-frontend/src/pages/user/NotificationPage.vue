<template>
  <div id="notificationPage">
    <!-- 页头：普通态=标题+未读徽标；选择态=全选 | 标题 | 取消（手机信息式） -->
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
      <p v-if="!selectMode" class="page-sub">你的创作动态与审核结果都在这里</p>
    </div>

    <!-- 分区卡片：白卡 + 彩色图标块 + 文字，选中态蓝描边 -->
    <div class="cat-grid">
      <div
        v-for="item in categories"
        :key="item.key"
        class="cat-card"
        :class="{ active: category === item.key }"
        @click="switchCategory(item.key)"
      >
        <span v-if="item.key === 'unread' && unreadCount > 0" class="cat-badge">
          {{ unreadCount > 99 ? '99+' : unreadCount }}
        </span>
        <div class="cat-icon" :class="item.tile">
          <component :is="item.icon" />
        </div>
        <div class="cat-label">{{ item.label }}</div>
      </div>
    </div>

    <!-- 聊天式面板：搜索 + 消息列表 -->
    <div class="msg-panel">
      <!-- 工具行：搜索 + 批量入口 -->
      <div class="panel-toolbar" v-if="!selectMode">
        <a-input
          v-model:value="keyword"
          class="search-input"
          placeholder="搜索消息"
          allow-clear
          @input="onKeywordInput"
        >
          <template #prefix><SearchOutlined class="search-icon" /></template>
        </a-input>
        <a-button v-if="unreadCount > 0" class="read-all-btn" type="text" @click="handleReadAll">
          <CheckOutlined />
          全部已读
        </a-button>
        <!-- 进入选择模式：打勾正方形图标（手机信息式） -->
        <a-button class="select-mode-btn" type="text" title="选择消息" @click="selectMode = true">
          <CheckSquareOutlined />
        </a-button>
      </div>
      <div v-else class="panel-toolbar selecting-tip">长按或点击勾选消息</div>

      <!-- 消息列表 -->
      <div class="panel-list">
        <a-spin :spinning="loading">
          <template v-if="list.length > 0">
            <div
              v-for="item in list"
              :key="item.id"
              class="notify-row"
              :class="{ unread: item.isRead === 0, selecting: selectMode }"
              @click="selectMode ? toggleSelect(item) : handleClick(item)"
              @pointerdown="onRowPointerDown($event, item)"
              @pointermove="onRowPointerMove"
              @pointerup="onRowPointerUp"
              @pointercancel="onRowPointerUp"
            >
              <!-- 选择模式：行首勾选框 -->
              <div v-if="selectMode" class="row-check" @click.stop>
                <a-checkbox
                  :checked="selectedIds.includes(String(item.id))"
                  @change="toggleSelect(item)"
                />
              </div>
              <!-- 头像：有缩略图用缩略图，否则类型图标 -->
              <div class="row-avatar" :class="iconClass(item)">
                <img v-if="item.picThumbnail" :src="item.picThumbnail" class="row-thumb-img" alt="" />
                <component v-else :is="iconComponent(item)" class="icon-inner" />
              </div>
              <div class="row-body">
                <div class="row-line1">
                  <span class="title-text">{{ item.title }}</span>
                  <span class="row-time">{{ formatTime(item.createTime) }}</span>
                </div>
                <div class="row-line2">
                  <span class="type-tag" :class="iconClass(item)">{{ typeText(item) }}</span>
                  <span class="row-desc">{{ item.content }}</span>
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

        <!-- 到底提示 -->
        <div v-if="list.length > 0 && reachedEnd" class="list-end">
          <span class="end-line" />
          已经到底啦
          <span class="end-line" />
        </div>

        <!-- 分页器 -->
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
import { computed, onMounted, onUnmounted, ref } from 'vue'
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
  MessageOutlined,
  PictureOutlined,
  SearchOutlined,
  SendOutlined,
  StarOutlined,
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

/** 分区定义（category 与后端 NotificationTypeEnum 对齐，评论/回复统一归互动分区） */
type CategoryKey = 'all' | 'interact' | 'review' | 'system' | 'unread'
const categories: { key: CategoryKey; label: string; icon: any; tile: string }[] = [
  { key: 'all', label: '全部', icon: AppstoreOutlined, tile: 'tile-all' },
  { key: 'interact', label: '评论与回复', icon: MessageOutlined, tile: 'tile-interact' },
  { key: 'review', label: '审核通知', icon: PictureOutlined, tile: 'tile-review' },
  { key: 'system', label: '系统通知', icon: BellOutlined, tile: 'tile-system' },
  { key: 'unread', label: '未读', icon: MailOutlined, tile: 'tile-unread' },
]

const router = useRouter()
const category = ref<CategoryKey>('all')
const list = ref<NotificationVO[]>([])
const loading = ref(false)
const current = ref(1)
const total = ref(0)
const unreadCount = ref(0)
const keyword = ref('')

/** 是否已加载到最后一页（显示"已经到底啦"） */
const reachedEnd = computed(() => current.value * PAGE_SIZE >= total.value)

// 类型映射：从标题模板识别互动子类，图标 + 颜色 + 文案
const typeMeta: Record<string, { icon: any; cls: string; text: string }> = {
  comment: { icon: MessageOutlined, cls: 'type-comment', text: '评论' },
  reply: { icon: SendOutlined, cls: 'type-reply', text: '回复' },
  like: { icon: HeartOutlined, cls: 'type-like', text: '点赞' },
  favorite: { icon: StarOutlined, cls: 'type-favorite', text: '收藏' },
  review: { icon: PictureOutlined, cls: 'type-review', text: '审核' },
  audit: { icon: AuditOutlined, cls: 'type-audit', text: '待审核' },
  system: { icon: BellOutlined, cls: 'type-system', text: '系统' },
  interact: { icon: MessageOutlined, cls: 'type-interact', text: '互动' },
}
const defaultMeta = { icon: GiftOutlined, cls: 'type-default', text: '通知' }

/** 互动通知的子类型没有落库，从标题模板（"XX 评论了/回复了你的图片"）识别 */
const detectSubType = (item: NotificationVO): string => {
  const t = item.title ?? ''
  if (t.includes('回复了')) return 'reply'
  if (t.includes('评论了')) return 'comment'
  if (t.includes('点赞了')) return 'like'
  if (t.includes('收藏了')) return 'favorite'
  return item.type ?? 'system'
}

const metaOf = (item: NotificationVO) => typeMeta[detectSubType(item)] ?? defaultMeta
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
      keyword: keyword.value.trim() || undefined,
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

/** 切换分区：回到第一页重新查询 */
const switchCategory = (key: CategoryKey) => {
  if (category.value === key) {
    return
  }
  category.value = key
  resetAndFetch()
}

/** 搜索关键词防抖：停顿 400ms 后回到第一页重新查询 */
let keywordTimer: number | null = null
const onKeywordInput = () => {
  if (keywordTimer != null) {
    clearTimeout(keywordTimer)
  }
  keywordTimer = window.setTimeout(() => {
    keywordTimer = null
    resetAndFetch()
  }, 400)
}

const resetAndFetch = () => {
  selectedIds.value = []
  list.value = []
  total.value = 0
  fetchList(1)
}

onUnmounted(() => {
  if (keywordTimer != null) {
    clearTimeout(keywordTimer)
  }
})

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
    // 当前分区下刷新列表（未读分区会随之清空）
    list.value = []
    total.value = 0
    fetchList(1)
  } else {
    message.error(res.message ?? '操作失败')
  }
}

/** 聊天式相对时间：今天显示时刻，今年显示月-日，更早显示完整日期 */
const formatTime = (value?: string) => {
  if (!value) return ''
  let d = new Date(value)
  if (isNaN(d.getTime())) {
    d = new Date(value.replace(/-/g, '/').replace('T', ' '))
  }
  if (isNaN(d.getTime())) return ''
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  const sameDay =
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate()
  if (sameDay) {
    return `${pad(d.getHours())}:${pad(d.getMinutes())}`
  }
  if (d.getFullYear() === now.getFullYear()) {
    return `${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
  }
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
  margin-bottom: 18px;
}

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

/* ===== 聊天式面板 ===== */
.msg-panel {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 20px;
  box-shadow: var(--card-shadow);
  padding: 14px 16px 8px;
  min-height: 420px;
  overflow: hidden;
}

/* ===== 分区卡片：白卡 + 彩色图标块 + 文字，选中态蓝描边 ===== */
.cat-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 14px;
  margin-bottom: 16px;
}

.cat-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 18px 10px 14px;
  background: var(--bg-card);
  border: 2px solid transparent;
  border-radius: 14px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  cursor: pointer;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    border-color 0.2s ease,
    background 0.2s ease;
  user-select: none;
}

.cat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 22px rgba(0, 0, 0, 0.09);
}

.cat-card:active {
  transform: translateY(0) scale(0.98);
}

.cat-card.active {
  border-color: #1677ff;
  background: rgba(22, 119, 255, 0.04);
}

.cat-icon {
  width: 46px;
  height: 46px;
  border-radius: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 21px;
  transition: transform 0.2s ease;
}

.cat-card.active .cat-icon {
  transform: scale(1.06);
}

.cat-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary-light);
  white-space: nowrap;
}

.cat-card.active .cat-label {
  color: #1677ff;
}

/* 未读角标：卡片右上角小红点数字 */
.cat-badge {
  position: absolute;
  top: -7px;
  right: -7px;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  color: #fff;
  background: #ff4d4f;
  box-shadow: 0 0 0 3px var(--bg-card);
}

/* 图标块配色（参考图：蓝/橙/紫/红 彩底） */
.cat-icon.tile-all {
  color: #1677ff;
  background: rgba(22, 119, 255, 0.09);
}

.cat-icon.tile-interact {
  color: #1677ff;
  background: rgba(22, 119, 255, 0.12);
}

.cat-icon.tile-review {
  color: #fa8c16;
  background: rgba(250, 140, 22, 0.13);
}

.cat-icon.tile-system {
  color: #722ed1;
  background: rgba(114, 46, 209, 0.11);
}

.cat-icon.tile-unread {
  color: #ff4d4f;
  background: rgba(255, 77, 79, 0.1);
}

/* 工具行：搜索 + 批量入口 */
.panel-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.panel-toolbar.selecting-tip {
  justify-content: center;
  font-size: 12px;
  color: var(--text-disabled);
  padding: 6px 0 10px;
}

.search-input {
  flex: 1;
  border-radius: 999px;
}

.search-icon {
  color: var(--text-disabled);
}

.read-all-btn {
  flex-shrink: 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.read-all-btn:hover {
  color: var(--accent);
}

.select-mode-btn {
  flex-shrink: 0;
  font-size: 16px;
  color: var(--text-secondary);
}

.select-mode-btn:hover {
  color: var(--accent);
}

/* ===== 消息行（聊天式） ===== */
.notify-row {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 13px 10px;
  border-radius: 14px;
  cursor: pointer;
  transition: background 0.18s ease;
}

.notify-row.selecting {
  padding-left: 4px;
}

.row-check {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding-top: 12px;
}

.notify-row:hover {
  background: rgba(64, 169, 255, 0.07);
}

/* 未读行：左侧蓝色竖条 + 淡蓝底 */
.notify-row.unread {
  background-image: linear-gradient(rgba(64, 169, 255, 0.05), rgba(64, 169, 255, 0.05));
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

/* 头像：圆形，有缩略图用缩略图，否则类型图标 */
.row-avatar {
  flex-shrink: 0;
  width: 46px;
  height: 46px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
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

/* 头像类型配色 */
.row-avatar.type-comment {
  color: #1677ff;
  background: linear-gradient(135deg, rgba(22, 119, 255, 0.12), rgba(64, 169, 255, 0.18));
}

.row-avatar.type-reply {
  color: #13c2c2;
  background: linear-gradient(135deg, rgba(19, 194, 194, 0.12), rgba(19, 194, 194, 0.18));
}

.row-avatar.type-like {
  color: #eb2f96;
  background: linear-gradient(135deg, rgba(235, 47, 150, 0.12), rgba(235, 47, 150, 0.18));
}

.row-avatar.type-favorite {
  color: #faad14;
  background: linear-gradient(135deg, rgba(250, 173, 20, 0.12), rgba(250, 173, 20, 0.18));
}

.row-avatar.type-review {
  color: #1677ff;
  background: linear-gradient(135deg, rgba(22, 119, 255, 0.12), rgba(64, 169, 255, 0.18));
}

.row-avatar.type-system {
  color: #722ed1;
  background: linear-gradient(135deg, rgba(114, 46, 209, 0.12), rgba(114, 46, 209, 0.18));
}

.row-avatar.type-audit {
  color: #fa8c16;
  background: linear-gradient(135deg, rgba(250, 140, 22, 0.14), rgba(250, 140, 22, 0.2));
}

.row-avatar.type-interact {
  color: #eb2f96;
  background: linear-gradient(135deg, rgba(235, 47, 150, 0.12), rgba(235, 47, 150, 0.18));
}

.row-avatar.type-default {
  color: #fa8c16;
  background: linear-gradient(135deg, rgba(250, 140, 22, 0.12), rgba(250, 140, 22, 0.18));
}

.row-body {
  flex: 1;
  min-width: 0;
}

/* 第一行：标题 + 时间 */
.row-line1 {
  display: flex;
  align-items: center;
  gap: 10px;
}

.title-text {
  flex: 1;
  min-width: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary-light);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-time {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--text-disabled);
  font-variant-numeric: tabular-nums;
}

/* 第二行：类型徽标 + 内容预览（单行截断，聊天风格） */
.row-line2 {
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.type-tag {
  flex-shrink: 0;
  padding: 1px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  line-height: 16px;
}

.type-tag.type-comment {
  color: #1677ff;
  background: rgba(22, 119, 255, 0.1);
}

.type-tag.type-reply {
  color: #13c2c2;
  background: rgba(19, 194, 194, 0.1);
}

.type-tag.type-like {
  color: #eb2f96;
  background: rgba(235, 47, 150, 0.1);
}

.type-tag.type-favorite {
  color: #faad14;
  background: rgba(250, 173, 20, 0.12);
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
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 19px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 桌面悬浮删除：悬停行时淡入 */
.row-del {
  flex-shrink: 0;
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

.row-dot {
  position: absolute;
  top: 16px;
  right: 6px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ff4d4f;
  box-shadow: 0 0 0 3px rgba(255, 77, 79, 0.15);
}

/* ===== 到底提示 ===== */
.list-end {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 18px 0 10px;
  font-size: 12px;
  color: var(--text-disabled);
}

.end-line {
  width: 48px;
  height: 1px;
  background: var(--border-color);
}

/* 分页器：右对齐 */
.pager-box {
  display: flex;
  justify-content: flex-end;
  padding: 12px 4px 6px;
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
html.dark .msg-panel {
  background: rgba(45, 45, 45, 0.9);
  box-shadow: var(--card-shadow);
}

html.dark .cat-card {
  background: rgba(45, 45, 45, 0.9);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.3);
}

html.dark .cat-card.active {
  background: rgba(22, 119, 255, 0.08);
}

html.dark .cat-badge {
  box-shadow: 0 0 0 3px #2d2d2d;
}

html.dark .row-title,
html.dark .title-text {
  color: var(--text-primary);
}

/* ===== 移动端 ===== */
@media (max-width: 768px) {
  .page-header {
    margin-bottom: 12px;
  }

  .page-title {
    font-size: 20px;
  }

  .page-sub {
    display: none;
  }

  /* 分区卡片：单行横向滑动（一行放不下时左右滑） */
  .cat-grid {
    grid-template-columns: none;
    grid-auto-flow: column;
    grid-auto-columns: 96px;
    gap: 10px;
    overflow-x: auto;
    scrollbar-width: none;
    padding-bottom: 4px;
    scroll-snap-type: x proximity;
  }

  .cat-grid::-webkit-scrollbar {
    display: none;
  }

  .cat-card {
    padding: 12px 4px 10px;
    gap: 8px;
    scroll-snap-align: start;
  }

  .cat-icon {
    width: 40px;
    height: 40px;
    border-radius: 11px;
    font-size: 18px;
  }

  .cat-label {
    font-size: 12px;
  }

  .msg-panel {
    padding: 12px 10px 6px;
    border-radius: 18px;
  }

  .notify-row {
    padding: 12px 6px;
  }

  .row-avatar {
    width: 42px;
    height: 42px;
  }

  /* 底部操作条避开移动端底部 Tabbar */
  .select-bar {
    bottom: calc(64px + env(safe-area-inset-bottom, 0px));
  }

  .select-bar-inner {
    padding: 8px 18px;
  }
}
</style>
