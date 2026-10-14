<template>
  <div id="myInteractionPage">
    <div class="page-head">
      <h2 class="page-title">{{ activeMeta.title }}</h2>
      <p class="page-sub">{{ activeMeta.sub }}</p>
    </div>

    <!-- 分段药丸切换（The Pill Language Rule） -->
    <div class="seg-bar" role="tablist">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="seg-item"
        :class="{ active: activeTab === tab.key }"
        role="tab"
        :aria-selected="activeTab === tab.key"
        @click="switchTab(tab.key)"
      >
        <component :is="activeTab === tab.key ? tab.activeIcon : tab.icon" />
        <span class="seg-text">{{ tab.label }}</span>
        <span v-if="counts[tab.key] > 0" class="seg-count">{{ counts[tab.key] }}</span>
      </button>
    </div>

    <div class="toolbar">
      <span class="toolbar-count">共 {{ total }} 条</span>
      <a-popconfirm
        v-if="activeTab === 'history' && total > 0"
        title="确认清空全部浏览历史？"
        ok-text="清空"
        cancel-text="取消"
        @confirm="clearHistory"
      >
        <a-button size="small" danger :loading="clearing">
          <DeleteOutlined /> 清空历史
        </a-button>
      </a-popconfirm>
    </div>

    <a-spin :spinning="loading">
      <PictureList
        v-if="pictures.length"
        :data-list="pictures"
        :loading="loading"
        :finished="finished"
        show-op
        @load-more="loadMore"
      />
      <div v-else-if="!loading" class="empty-wrap">
        <a-empty :description="activeMeta.empty">
          <a-button type="primary" @click="router.push('/')">{{ activeMeta.cta }}</a-button>
        </a-empty>
      </div>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  DeleteOutlined,
  HeartFilled,
  HeartOutlined,
  HistoryOutlined,
  StarFilled,
  StarOutlined,
} from '@ant-design/icons-vue'
import PictureList from '@/components/PictureList.vue'
import {
  clearMyViewHistory,
  pageMyFavorites,
  pageMyLikes,
  pageMyViewHistory,
} from '@/api/interaction.ts'
import { useRoute, useRouter } from 'vue-router'

type TabKey = 'favorite' | 'like' | 'history'

// 分段药丸配置（图标区分选中态）
const tabs: Array<{
  key: TabKey
  label: string
  icon: any
  activeIcon: any
}> = [
  { key: 'favorite', label: '我的收藏', icon: StarOutlined, activeIcon: StarFilled },
  { key: 'like', label: '我的点赞', icon: HeartOutlined, activeIcon: HeartFilled },
  { key: 'history', label: '最近查看', icon: HistoryOutlined, activeIcon: HistoryOutlined },
]

const route = useRoute()
const router = useRouter()

const TAB_META: Record<TabKey, { title: string; sub: string; empty: string; cta: string }> = {
  favorite: {
    title: '我的收藏',
    sub: '你收藏过的图片都会出现在这里，方便随时回看',
    empty: '还没有收藏任何图片',
    cta: '去图库逛逛',
  },
  like: {
    title: '我的点赞',
    sub: '你点赞过的图片',
    empty: '还没有点赞任何图片',
    cta: '去图库逛逛',
  },
  history: {
    title: '最近查看',
    sub: '按查看时间倒序，同一张图只保留最近一次',
    empty: '还没有浏览记录',
    cta: '先去逛几张图',
  },
}

const activeTab = ref<TabKey>('favorite')
const pictures = ref<API.PictureVO[]>([])
const total = ref(0)
const loading = ref(false)
const clearing = ref(false)
const finished = ref(false)
const counts = reactive({ favorite: 0, like: 0, history: 0 })

const PAGE_SIZE = 20
let current = 1

const activeMeta = computed(() => TAB_META[activeTab.value])

const fetcher = (tab: TabKey, page: number) => {
  const params = { current: page, pageSize: PAGE_SIZE }
  if (tab === 'favorite') return pageMyFavorites(params)
  if (tab === 'like') return pageMyLikes(params)
  return pageMyViewHistory(params)
}

const load = async (reset = false) => {
  if (reset) {
    current = 1
    pictures.value = []
    finished.value = false
  }
  if (finished.value) return
  loading.value = true
  try {
    const res = await fetcher(activeTab.value, current)
    if (res.code === 0) {
      const records = res.data?.records ?? []
      pictures.value = current === 1 ? records : [...pictures.value, ...records]
      total.value = res.data?.total ?? 0
      // 记录各分类数量（仅在首页加载时刷新当前分类）
      counts[activeTab.value] = total.value
      finished.value = pictures.value.length >= total.value
    } else {
      message.error(res.message ?? '加载失败')
      finished.value = true
    }
  } catch (e: any) {
    message.error('加载失败：' + (e?.message ?? ''))
    finished.value = true
  } finally {
    loading.value = false
  }
}

const loadMore = () => {
  if (loading.value || finished.value) return
  current += 1
  load()
}

// 切换标签：同步到 URL，便于分享/刷新后停留在同一标签
const switchTab = (key: TabKey) => {
  if (key === activeTab.value) return
  activeTab.value = key
  router.replace({ query: { ...route.query, tab: key } })
  load(true)
}

const clearHistory = async () => {
  clearing.value = true
  try {
    const res = await clearMyViewHistory()
    if (res.code === 0) {
      message.success(`已清空 ${res.data ?? 0} 条浏览历史`)
      pictures.value = []
      total.value = 0
      counts.history = 0
      finished.value = true
    } else {
      message.error(res.message ?? '清空失败')
    }
  } catch (e: any) {
    message.error('清空失败：' + (e?.message ?? ''))
  } finally {
    clearing.value = false
  }
}

watch(
  () => route.query.tab,
  (tab) => {
    const key = (tab as TabKey) || 'favorite'
    if (['favorite', 'like', 'history'].includes(key) && key !== activeTab.value) {
      activeTab.value = key
      load(true)
    }
  },
)

onMounted(() => {
  const key = (route.query.tab as TabKey) || 'favorite'
  if (['favorite', 'like', 'history'].includes(key)) {
    activeTab.value = key
  }
  load(true)
})
</script>

<style scoped>
#myInteractionPage {
  max-width: 1440px;
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

/* ===== 分段药丸切换器 ===== */
.seg-bar {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  margin-top: 16px;
  max-width: 100%;
  overflow-x: auto;
  scrollbar-width: none;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 999px;
}

.seg-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 13px;
  font-family: inherit;
  color: var(--text-secondary);
  white-space: nowrap;
  transition:
    color 0.2s ease,
    background 0.2s ease;
}

.seg-item:hover {
  color: var(--accent);
}

.seg-item.active {
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 600;
}

.seg-count {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11px;
  line-height: 1;
  padding: 3px 6px;
  border-radius: 999px;
  background: rgba(127, 127, 127, 0.12);
}

.seg-item.active .seg-count {
  background: rgba(64, 169, 255, 0.18);
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 16px 0 12px;
  min-height: 28px;
}

.toolbar-count {
  font-size: 13px;
  color: var(--text-secondary);
}

.empty-wrap {
  padding: 40px 0 64px;
}

html.dark .page-title {
  color: var(--text-primary);
}
</style>