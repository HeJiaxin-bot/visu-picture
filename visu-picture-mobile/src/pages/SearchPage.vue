<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  listPictureTagCategoryUsingGet,
  listPictureVoByPageUsingPost,
} from '@/api/pictureController.ts'
import { showToast } from 'vant'
import PictureWaterfall from '@/components/PictureWaterfall.vue'

defineOptions({ name: 'SearchPage' })

const router = useRouter()

// 关键词
const searchText = ref('')

// 数据
const dataList = ref<API.PictureVO[]>([])
const total = ref(0)
const loading = ref(false)
const searched = ref(false) // 是否已执行过搜索（控制空状态展示）

// 筛选条件
const selectedCategory = ref<string>()
const selectedTags = ref<string[]>([])
const sortField = ref<'likeCount' | 'createTime'>('likeCount')

// 下拉菜单引用（用于选择后收起面板）
const categoryMenuRef = ref()
const tagMenuRef = ref()
const sortMenuRef = ref()

const searchParams = reactive<API.PictureQueryRequest>({
  current: 1,
  pageSize: 10,
})

const finished = computed(() => searched.value && dataList.value.length >= total.value)

// 组装搜索参数并请求（第 1 页替换，之后追加）
const fetchData = async () => {
  loading.value = true
  const params: API.PictureQueryRequest = {
    ...searchParams,
    searchText: searchText.value || undefined,
    category: selectedCategory.value || undefined,
    tags: selectedTags.value.length > 0 ? selectedTags.value : undefined,
    sortField: sortField.value,
    sortOrder: 'descend',
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
      searched.value = true
    } else {
      rollbackPage()
      showToast('搜索失败，' + res.data.message)
    }
  } catch (e) {
    rollbackPage()
    showToast('搜索失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const rollbackPage = () => {
  if ((searchParams.current ?? 1) > 1) {
    searchParams.current = (searchParams.current ?? 1) - 1
  }
}

// 触发搜索（重置分页与列表）
const doSearch = (val?: string) => {
  if (typeof val === 'string') {
    searchText.value = val
  }
  searchParams.current = 1
  dataList.value = []
  searched.value = false
  fetchData()
  window.scrollTo({ top: 0 })
}

// van-list 触底加载
const onLoad = () => {
  if (finished.value) return
  searchParams.current = (searchParams.current ?? 1) + 1
  fetchData()
}

// 选择分类
const doPickCategory = (c: string) => {
  selectedCategory.value = selectedCategory.value === c ? undefined : c
  categoryMenuRef.value?.toggle(false)
  doSearch()
}

// 确定（标签）
const doConfirmTags = () => {
  tagMenuRef.value?.toggle(false)
  doSearch()
}

// 切换排序
const doPickSort = (field: 'likeCount' | 'createTime') => {
  sortField.value = field
  sortMenuRef.value?.toggle(false)
  doSearch()
}

// 分类与标签选项
const categoryList = ref<string[]>([])
const tagList = ref<string[]>([])

const getTagCategoryOptions = async () => {
  const res = await listPictureTagCategoryUsingGet()
  if (res.data.code === 0 && res.data.data) {
    categoryList.value = res.data.data.categoryList ?? []
    tagList.value = res.data.data.tagList ?? []
  }
}

onMounted(() => {
  getTagCategoryOptions()
})

// 各下拉项标题
const categoryTitle = computed(() => selectedCategory.value ?? '分类')
const tagTitle = computed(() =>
  selectedTags.value.length > 0 ? `标签(${selectedTags.value.length})` : '标签',
)
const sortTitle = computed(() => (sortField.value === 'likeCount' ? '最热' : '最新'))

// 编辑跳转
const doEditPicture = (picture: API.PictureVO) => {
  router.push({ path: '/add_picture', query: { id: picture.id, spaceId: picture.spaceId } })
}
</script>

<template>
  <div class="search-page">
    <!-- 搜索框 -->
    <van-search
      v-model="searchText"
      placeholder="搜索图片名称 / 简介"
      show-action
      @search="doSearch"
    >
      <template #action>
        <div class="search-btn" @click="doSearch()">搜索</div>
      </template>
    </van-search>

    <!-- 筛选：分类 / 标签 / 排序 -->
    <van-dropdown-menu>
      <van-dropdown-item ref="categoryMenuRef" :title="categoryTitle">
        <div class="filter-panel">
          <div
            v-for="c in categoryList"
            :key="c"
            class="filter-option"
            :class="{ active: selectedCategory === c }"
            @click="doPickCategory(c)"
          >
            {{ c }}
          </div>
        </div>
      </van-dropdown-item>
      <van-dropdown-item ref="tagMenuRef" :title="tagTitle">
        <div class="filter-panel">
          <div
            v-for="t in tagList"
            :key="t"
            class="filter-option"
            :class="{ active: selectedTags.includes(t) }"
            @click="
              selectedTags.includes(t)
                ? (selectedTags = selectedTags.filter((v) => v !== t))
                : selectedTags.push(t)
            "
          >
            {{ t }}
          </div>
          <div class="filter-confirm">
            <van-button type="primary" size="small" round block @click="doConfirmTags">
              确定
            </van-button>
          </div>
        </div>
      </van-dropdown-item>
      <van-dropdown-item ref="sortMenuRef" :title="sortTitle">
        <van-cell title="最热" clickable @click="doPickSort('likeCount')" />
        <van-cell title="最新" clickable @click="doPickSort('createTime')" />
      </van-dropdown-item>
    </van-dropdown-menu>

    <!-- 结果瀑布流 -->
    <div class="result-area">
      <van-list
        v-model:loading="loading"
        :finished="finished"
        finished-text="没有更多了"
        loading-text="加载中..."
        @load="onLoad"
      >
        <PictureWaterfall :pictures="dataList" @edit="doEditPicture" />
        <van-empty
          v-if="searched && dataList.length === 0"
          description="未找到相关图片，换个关键词试试"
        />
      </van-list>
    </div>
  </div>
</template>

<style scoped>
.search-page {
  padding-bottom: calc(70px + var(--safe-bottom));
  min-height: 100vh;
}

.search-page :deep(.van-search) {
  background: var(--bg-card);
  padding-top: calc(var(--safe-top) + 8px);
}

.search-btn {
  color: var(--brand);
  font-size: 14px;
}

/* 筛选面板：chips 布局 */
.filter-panel {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 16px 16px;
  max-height: 300px;
  overflow-y: auto;
}

.filter-option {
  padding: 5px 14px;
  border-radius: var(--radius-chip);
  font-size: 13px;
  color: var(--text-2);
  background: var(--bg-page);
  cursor: pointer;
}

.filter-option.active {
  color: #fff;
  background: var(--brand);
  font-weight: 600;
}

.filter-confirm {
  width: 100%;
  margin-top: 8px;
}

.result-area {
  padding: 12px 16px 0;
}
</style>
