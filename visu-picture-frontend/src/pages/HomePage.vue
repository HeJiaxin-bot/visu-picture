<template>
  <div id="homePage">
    <!-- 顶部 Hero 区域 -->
    <div class="hero">
      <h1 class="hero-title">视界云图库</h1>
      <p class="hero-desc">企业级智能协同云图库，海量图片素材免费获取</p>
      <!-- 搜索框：聚焦时下拉展示热门搜索与历史 -->
      <div class="search-bar">
        <a-input-search
          v-model:value="searchParams.searchText"
          placeholder="从海量图片中搜索"
          enter-button="搜索"
          size="large"
          @search="doSearch"
          @focus="searchPanelVisible = true"
          @blur="onSearchBlur"
        />
        <!-- 聚焦下拉：热门搜索 + 搜索历史（mousedown.prevent 防止点击前失焦） -->
        <div
          v-if="searchPanelVisible && (hotKeywords.length || searchHistory.length)"
          class="search-panel"
          @mousedown.prevent
        >
          <div v-if="hotKeywords.length" class="panel-section">
            <div class="panel-label"><span><FireOutlined /> 热门搜索</span></div>
            <div class="panel-pills">
              <span
                v-for="(kw, index) in hotKeywords"
                :key="`hot-${kw}`"
                class="panel-pill"
                @click="searchKeyword(kw)"
              >
                <i v-if="index < 3" class="hot-rank" :class="`top-${index + 1}`">{{ index + 1 }}</i>
                {{ kw }}
              </span>
            </div>
          </div>
          <div v-if="searchHistory.length" class="panel-section">
            <div class="panel-label">
              <span><HistoryOutlined /> 搜索历史</span>
              <span class="panel-clear" @click="clearHistory"><ClearOutlined /> 清空</span>
            </div>
            <div class="panel-pills">
              <span v-for="kw in searchHistory" :key="`his-${kw}`" class="panel-pill" @click="searchKeyword(kw)">
                {{ kw }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
    <!-- 分类筛选：推荐（默认）/最新 + 业务分类 -->
    <a-tabs v-model:active-key="selectedCategory" class="category-tabs" @change="doSearch">
      <a-tab-pane key="all" tab="推荐" />
      <a-tab-pane key="latest" tab="最新" />
      <a-tab-pane v-for="category in categoryList" :tab="category" :key="category" />
    </a-tabs>
    <!-- 图片列表（滚动加载）；空分类时展示空状态 -->
    <PictureList
      v-if="loading || dataList.length > 0"
      :dataList="dataList"
      :loading="loading"
      :finished="finished"
      @load-more="onLoadMore"
    />
    <a-empty v-else description="该分类下还没有图片，换个分类看看，或上传第一张" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ClearOutlined,
  FireOutlined,
  HistoryOutlined,
} from '@ant-design/icons-vue'
import {
  listPictureTagCategoryUsingGet,
  listPictureVoByPageUsingPost,
} from '@/api/pictureController.ts'
import { getHotSearchKeywords } from '@/api/search.ts'
import { message } from 'ant-design-vue'
import PictureList from '@/components/PictureList.vue' // 定义数据

// 定义数据
const dataList = ref<API.PictureVO[]>([])
const total = ref(0)
const loading = ref(true)

// 搜索条件（排序由选中 tab 决定：推荐=点赞降序，最新=时间降序）
const searchParams = reactive<API.PictureQueryRequest>({
  current: 1,
  pageSize: 12,
  sortField: 'likeCount',
  sortOrder: 'descend',
})

// 是否已加载全部数据
const finished = computed(() => !loading.value && dataList.value.length >= total.value)

// 获取数据（滚动加载模式：第 1 页替换列表，之后追加）
const fetchData = async () => {
  loading.value = true
  // 转换搜索参数：推荐=点赞降序；最新=创建时间降序
  const params = {
    ...searchParams,
  }
  if (selectedCategory.value === 'latest') {
    params.sortField = 'createTime'
    params.sortOrder = 'descend'
  } else {
    params.sortField = 'likeCount'
    params.sortOrder = 'descend'
    if (selectedCategory.value !== 'all') {
      params.category = selectedCategory.value
    }
  }
  try {
    const res = await listPictureVoByPageUsingPost(params)
    if (res.data.code === 0 && res.data.data) {
      const records = res.data.data.records ?? []
      if ((searchParams.current ?? 1) <= 1) {
        dataList.value = records
      } else {
        dataList.value = [...dataList.value, ...records]
      }
      total.value = res.data.data.total ?? 0
    } else {
      rollbackPage()
      message.error('获取数据失败，' + res.data.message)
    }
  } catch (e) {
    rollbackPage()
    message.error('获取数据失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

// 加载失败时回滚页码，避免滚动加载重复请求同一页
const rollbackPage = () => {
  if ((searchParams.current ?? 1) > 1) {
    searchParams.current = (searchParams.current ?? 1) - 1
  }
}

// 页面加载时获取数据，请求一次
onMounted(() => {
  fetchData()
})

// 滚动到底部时加载下一页
const onLoadMore = () => {
  if (loading.value || finished.value) return
  searchParams.current = (searchParams.current ?? 1) + 1
  fetchData()
}

// 搜索
const doSearch = () => {
  // 重置搜索条件
  searchParams.current = 1
  // 记录搜索历史（本地）与热门统计（后端 Redis ZSet）
  const kw = (searchParams.searchText ?? '').trim()
  if (kw) pushHistory(kw)
  searchPanelVisible.value = false
  fetchData()
}

// ==================== 搜索历史 + 热门搜索 ====================
const HISTORY_KEY = 'visu_search_history'
const searchPanelVisible = ref(false)
const searchHistory = ref<string[]>(JSON.parse(localStorage.getItem(HISTORY_KEY) ?? '[]'))
const hotKeywords = ref<string[]>([])

// 失焦延迟收起：给面板点击留出时间窗口
const onSearchBlur = () => {
  setTimeout(() => {
    searchPanelVisible.value = false
  }, 150)
}

const pushHistory = (keyword: string) => {
  const kw = keyword.trim()
  if (!kw) return
  // 去重置顶，最多保留 10 条
  searchHistory.value = [kw, ...searchHistory.value.filter((k) => k !== kw)].slice(0, 10)
  localStorage.setItem(HISTORY_KEY, JSON.stringify(searchHistory.value))
}

const clearHistory = () => {
  searchHistory.value = []
  localStorage.removeItem(HISTORY_KEY)
}

const searchKeyword = (keyword: string) => {
  searchParams.searchText = keyword
  searchPanelVisible.value = false
  doSearch()
}

onMounted(async () => {
  const res = await getHotSearchKeywords(8)
  if (res.code === 0 && res.data) {
    hotKeywords.value = res.data
  }
})

// 分类列表
const categoryList = ref<string[]>([])
const selectedCategory = ref<string>('all')

/**
 * 获取分类选项
 * @param values
 */
const getTagCategoryOptions = async () => {
  const res = await listPictureTagCategoryUsingGet()
  if (res.data.code === 0 && res.data.data) {
    categoryList.value = res.data.data.categoryList ?? []
  } else {
    message.error('获取分类列表失败，' + res.data.message)
  }
}

onMounted(() => {
  getTagCategoryOptions()
})
</script>

<style scoped>
/* 内容区约 1344px 居中（悦目布局规格） */
#homePage {
  max-width: 1344px;
  margin: 0 auto 16px;
  padding: 0 16px;
  box-sizing: border-box;
}

/* Hero 区域：无卡片容器，标题/搜索直接融入季节渐变背景；光晕随季节变色 */
#homePage .hero {
  position: relative;
  text-align: center;
  padding: 48px 32px 40px;
  margin-bottom: 8px;
}

/* 季节光晕：radial 渐变自然淡出，无边界感（不用 overflow 裁剪避免生硬切边） */
#homePage .hero::before,
#homePage .hero::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}

#homePage .hero::before {
  top: -60px;
  right: 6%;
  width: 340px;
  height: 340px;
  background: radial-gradient(circle, var(--glow-a) 0%, transparent 70%);
}

#homePage .hero::after {
  bottom: -80px;
  left: 4%;
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, var(--glow-b) 0%, transparent 70%);
}

#homePage .hero-title {
  font-size: 40px;
  margin-bottom: 12px;
  letter-spacing: 1px;
  color: #f0f0f0;
  position: relative;
  z-index: 1;
}

#homePage .hero-desc {
  color: #b3b3b3;
  font-size: 15px;
  margin-bottom: 28px;
  position: relative;
  z-index: 1;
}

#homePage .search-bar {
  max-width: 640px;
  margin: 0 auto;
  background: var(--bg-card);
  padding: 6px 6px 6px 20px;
  border-radius: 999px;
  border: 1px solid var(--border-color);
  box-shadow: var(--card-shadow);
  position: relative;
  z-index: 1;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

/* 聚焦时光晕环：呼应 DESIGN.md 搜索厅签名组件 */
#homePage .search-bar:focus-within {
  border-color: rgba(64, 169, 255, 0.45);
  box-shadow:
    var(--card-shadow),
    0 0 0 3px rgba(64, 169, 255, 0.18);
}

/* 占位符对比度 ≥4.5:1（暗色/浅色分别覆盖 antd 默认值） */
#homePage .search-bar :deep(.ant-input)::placeholder {
  color: rgba(255, 255, 255, 0.45);
}

html:not(.dark) #homePage .search-bar :deep(.ant-input)::placeholder {
  color: rgba(0, 0, 0, 0.45);
}

#homePage .search-bar :deep(.ant-input) {
  border: none;
  box-shadow: none !important;
  background: transparent;
}

#homePage .search-bar :deep(.ant-input-group-addon) {
  background: transparent;
}

#homePage .search-bar :deep(.ant-input-search-button) {
  border-radius: 999px;
}

/* ===== 聚焦下拉面板：热门搜索 + 历史 ===== */
.search-panel {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  right: 0;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.35);
  padding: 14px 16px;
  text-align: left;
  z-index: 20;
  max-height: 320px;
  overflow-y: auto;
}

.panel-section + .panel-section {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--border-color);
}

.panel-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-secondary);
}

.panel-clear {
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  opacity: 0.7;
  transition: color 0.2s ease;
}

.panel-clear:hover {
  color: #ff4d4f;
  opacity: 1;
}

.panel-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.panel-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--text-secondary);
  border: 1px solid var(--border-color);
  border-radius: 999px;
  padding: 3px 12px;
  cursor: pointer;
  transition:
    color 0.2s ease,
    border-color 0.2s ease;
}

.panel-pill:hover {
  color: var(--accent);
  border-color: var(--accent-soft);
}

.hot-rank {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  border-radius: 4px;
  font-size: 10px;
  font-style: normal;
  font-weight: 700;
  color: #fff;
  background: rgba(127, 127, 127, 0.4);
}

.hot-rank.top-1 {
  background: #ff4d4f;
}

.hot-rank.top-2 {
  background: #fa8c16;
}

.hot-rank.top-3 {
  background: #faad14;
}

/* ===== 热门搜索 / 搜索历史（旧常驻样式，已被下拉面板取代） ===== */

/* 浅色模式文字色适配（Hero 已无卡片背景） */
html:not(.dark) #homePage .hero-title {
  color: #212121;
}

html:not(.dark) #homePage .hero-desc {
  color: #6b6b6b;
}

/* 分类 Tab 居中 */
#homePage .category-tabs :deep(.ant-tabs-nav)::before {
  border-bottom: none;
}

#homePage .category-tabs :deep(.ant-tabs-nav-list) {
  margin: 0 auto;
  margin-bottom: 20px;
}

/* 移动端：压缩 hero 与搜索栏，保持药丸形态 */
@media (max-width: 560px) {
  #homePage .hero {
    padding: 36px 16px 32px;
  }

  #homePage .hero-title {
    font-size: 30px;
  }

  #homePage .hero-desc {
    margin-bottom: 20px;
  }

  #homePage .search-bar {
    padding: 4px 4px 4px 16px;
  }

  #homePage .search-bar :deep(.ant-input-search-button) {
    padding-inline: 14px;
  }
}
</style>
