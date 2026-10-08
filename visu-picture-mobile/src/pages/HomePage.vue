<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  listPictureTagCategoryUsingGet,
  listPictureVoByPageUsingPost,
} from '@/api/pictureController.ts'
import { showToast } from 'vant'
import PictureWaterfall from '@/components/PictureWaterfall.vue'

defineOptions({ name: 'HomePage' })

const router = useRouter()

// 数据列表
const dataList = ref<API.PictureVO[]>([])
const total = ref(0)
const loading = ref(false)
const initLoading = ref(true) // 首屏骨架屏

// 搜索条件（排序由选中 tab 决定：推荐=点赞降序，最新=时间降序）
const searchParams = reactive<API.PictureQueryRequest>({
  current: 1,
  pageSize: 10,
  sortField: 'likeCount',
  sortOrder: 'descend',
})

// 是否已加载全部数据
const finished = computed(() => dataList.value.length >= total.value && !initLoading.value)

// 获取数据（滚动加载模式：第 1 页替换列表，之后追加）
const fetchData = async () => {
  loading.value = true
  const params = { ...searchParams }
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
      showToast('获取数据失败，' + res.data.message)
    }
  } catch (e) {
    rollbackPage()
    showToast('获取数据失败，请稍后重试')
  } finally {
    loading.value = false
    initLoading.value = false
  }
}

// 加载失败时回滚页码，避免滚动加载重复请求同一页
const rollbackPage = () => {
  if ((searchParams.current ?? 1) > 1) {
    searchParams.current = (searchParams.current ?? 1) - 1
  }
}

// van-list 触底加载下一页
const onLoad = () => {
  if (finished.value) return
  searchParams.current = (searchParams.current ?? 1) + 1
  fetchData()
}

// 切换分类
const doSelectCategory = (key: string) => {
  selectedCategory.value = key
  searchParams.current = 1
  initLoading.value = true
  fetchData()
  // 回到顶部
  window.scrollTo({ top: 0 })
}

// 分类列表
const categoryList = ref<string[]>([])
const selectedCategory = ref<string>('all')
// 分类选项（推荐 / 最新 + 业务分类）
const categoryTabs = computed(() => ['all', 'latest', ...categoryList.value])
const categoryLabel = (key: string) => {
  if (key === 'all') return '推荐'
  if (key === 'latest') return '最新'
  return key
}

const getTagCategoryOptions = async () => {
  const res = await listPictureTagCategoryUsingGet()
  if (res.data.code === 0 && res.data.data) {
    categoryList.value = res.data.data.categoryList ?? []
  }
}

onMounted(() => {
  getTagCategoryOptions()
  fetchData()
})

// 跳转搜索页
const goSearch = () => {
  router.push('/search')
}

// 跳转图片详情
const doEditPicture = (picture: API.PictureVO) => {
  router.push({ path: '/add_picture', query: { id: picture.id, spaceId: picture.spaceId } })
}
</script>

<template>
  <div class="home-page">
    <!-- 顶部：标题 + 搜索入口 -->
    <header class="home-header">
      <div class="app-title">视界云图库</div>
      <div class="search-entry" @click="goSearch">
        <van-icon name="search" />
        <span>搜索图片 / 作者 / 标签</span>
      </div>
    </header>

    <!-- 分类 chips：横向滚动 -->
    <div class="category-chips">
      <div
        v-for="key in categoryTabs"
        :key="key"
        class="chip"
        :class="{ active: selectedCategory === key }"
        @click="doSelectCategory(key)"
      >
        {{ categoryLabel(key) }}
      </div>
    </div>

    <!-- 首屏骨架屏 -->
    <div v-if="initLoading" class="skeleton-wrap">
      <div class="skeleton-col">
        <div v-for="i in 3" :key="'l' + i" class="skeleton-card">
          <div class="skeleton-img" style="height: 180px" />
          <div class="skeleton-line" style="width: 70%" />
          <div class="skeleton-line" style="width: 40%" />
        </div>
      </div>
      <div class="skeleton-col">
        <div v-for="i in 3" :key="'r' + i" class="skeleton-card">
          <div class="skeleton-img" style="height: 220px" />
          <div class="skeleton-line" style="width: 60%" />
          <div class="skeleton-line" style="width: 45%" />
        </div>
      </div>
    </div>

    <!-- 图片瀑布流（无限滚动） -->
    <template v-else>
      <van-list
        v-model:loading="loading"
        :finished="finished"
        finished-text="没有更多了"
        loading-text="加载中..."
        @load="onLoad"
      >
        <PictureWaterfall :pictures="dataList" @edit="doEditPicture" />
        <!-- 空状态 -->
        <van-empty
          v-if="dataList.length === 0"
          description="暂无图片，快来发布第一张吧"
        />
      </van-list>
    </template>
  </div>
</template>

<style scoped>
.home-page {
  padding-bottom: calc(70px + var(--safe-bottom));
  min-height: 100vh;
}

/* 顶部：毛玻璃白底，标题 + 搜索入口 */
.home-header {
  background: rgba(255, 255, 255, 0.72);
  -webkit-backdrop-filter: blur(16px) saturate(1.4);
  backdrop-filter: blur(16px) saturate(1.4);
  padding: calc(var(--safe-top) + 12px) 16px 4px;
  position: sticky;
  top: 0;
  z-index: 10;
}

.app-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-1);
  letter-spacing: 0.5px;
}

.search-entry {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  padding: 9px 14px;
  border-radius: var(--radius-chip);
  background: var(--bg-page);
  color: var(--text-2);
  font-size: 14px;
  cursor: pointer;
}

/* 分类 chips：横向滚动 */
.category-chips {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  overflow-x: auto;
  scrollbar-width: none;
  position: sticky;
  top: calc(var(--safe-top) + 78px);
  z-index: 9;
  background: linear-gradient(to bottom, var(--bg-page) 82%, transparent);
}

.category-chips::-webkit-scrollbar {
  display: none;
}

.chip {
  flex-shrink: 0;
  padding: 5px 14px;
  border-radius: var(--radius-chip);
  font-size: 13px;
  color: var(--text-2);
  background: var(--bg-card);
  cursor: pointer;
  transition: all 0.2s ease;
}

.chip.active {
  color: #fff;
  background: var(--brand);
  font-weight: 600;
}

/* 骨架屏 */
.skeleton-wrap {
  display: flex;
  gap: 10px;
  padding: 0 16px;
}

.skeleton-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
</style>
