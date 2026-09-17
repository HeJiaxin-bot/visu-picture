<template>
  <div id="homePage">
    <!-- 顶部 Hero 区域 -->
    <div class="hero">
      <h1 class="hero-title">视界云图库</h1>
      <p class="hero-desc">企业级智能协同云图库，海量图片素材免费获取</p>
      <!-- 搜索框 -->
      <div class="search-bar">
        <a-input-search
          v-model:value="searchParams.searchText"
          placeholder="从海量图片中搜索"
          enter-button="搜索"
          size="large"
          @search="doSearch"
        />
      </div>
    </div>
    <!-- 分类筛选 -->
    <a-tabs v-model:active-key="selectedCategory" class="category-tabs" @change="doSearch">
      <a-tab-pane key="all" tab="全部" />
      <a-tab-pane v-for="category in categoryList" :tab="category" :key="category" />
    </a-tabs>
    <!-- 图片列表（滚动加载） -->
    <PictureList
      :dataList="dataList"
      :loading="loading"
      :finished="finished"
      @load-more="onLoadMore"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  listPictureTagCategoryUsingGet,
  listPictureVoByPageUsingPost,
} from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import PictureList from '@/components/PictureList.vue' // 定义数据

// 定义数据
const dataList = ref<API.PictureVO[]>([])
const total = ref(0)
const loading = ref(true)

// 搜索条件（首页按点赞数从高到低展示）
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
  // 转换搜索参数
  const params = {
    ...searchParams,
  }
  if (selectedCategory.value !== 'all') {
    params.category = selectedCategory.value
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
  fetchData()
}

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

/* Hero 区域：无卡片容器，标题/搜索直接融入页面浅蓝渐变背景 */
#homePage .hero {
  text-align: center;
  padding: 48px 32px 40px;
  margin-bottom: 8px;
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
  padding: 8px;
  border-radius: 16px;
  border: 1px solid var(--border-color);
  box-shadow: var(--card-shadow);
  position: relative;
  z-index: 1;
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
  border-radius: 10px;
}

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
</style>
