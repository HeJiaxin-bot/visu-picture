<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  deletePictureUsingPost,
  listPictureVoByPageUsingPost,
} from '@/api/pictureController.ts'
import { getSpaceVoByIdUsingGet } from '@/api/spaceController.ts'
import { showToast, showConfirmDialog } from 'vant'
import PictureWaterfall from '@/components/PictureWaterfall.vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

defineOptions({ name: 'SpaceDetailPage' })

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const spaceId = computed(() => route.params.id as string)

// 空间信息
const space = ref<API.SpaceVO>()
const spaceLoading = ref(true)

// 图片列表
const dataList = ref<API.PictureVO[]>([])
const total = ref(0)
const loading = ref(false)
const initLoading = ref(true)

const searchParams = reactive<API.PictureQueryRequest>({
  current: 1,
  pageSize: 10,
  sortField: 'createTime',
  sortOrder: 'descend',
})

const finished = computed(() => dataList.value.length >= total.value && !initLoading.value)

// 加载空间信息
const loadSpace = async () => {
  try {
    const res = await getSpaceVoByIdUsingGet({ id: spaceId.value } as any)
    if (res.data.code === 0 && res.data.data) {
      space.value = res.data.data
    } else {
      showToast('获取空间信息失败，' + res.data.message)
    }
  } catch (e) {
    showToast('获取空间信息失败，请稍后重试')
  } finally {
    spaceLoading.value = false
  }
}

// 加载图片列表
const fetchData = async () => {
  loading.value = true
  try {
    const res = await listPictureVoByPageUsingPost({
      ...searchParams,
      spaceId: spaceId.value,
    } as any)
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
      showToast('获取图片失败，' + res.data.message)
    }
  } catch (e) {
    rollbackPage()
    showToast('获取图片失败，请稍后重试')
  } finally {
    loading.value = false
    initLoading.value = false
  }
}

const rollbackPage = () => {
  if ((searchParams.current ?? 1) > 1) {
    searchParams.current = (searchParams.current ?? 1) - 1
  }
}

const onLoad = () => {
  if (finished.value) return
  searchParams.current = (searchParams.current ?? 1) + 1
  fetchData()
}

onMounted(() => {
  loadSpace()
  fetchData()
})

// ----- 操作 -----
// 上传到该空间
const goUpload = () => {
  router.push(`/publish?spaceId=${spaceId.value}`)
}

// 编辑
const doEditPicture = (picture: API.PictureVO) => {
  router.push({
    path: '/add_picture',
    query: { id: picture.id, spaceId: picture.spaceId ?? spaceId.value },
  })
}

// 删除
const doDeletePicture = async (picture: API.PictureVO) => {
  try {
    await showConfirmDialog({ title: '删除图片', message: '删除后不可恢复，确定删除吗？' })
  } catch (e) {
    return
  }
  if (!picture.id) return
  const res = await deletePictureUsingPost({ id: picture.id })
  if (res.data.code === 0) {
    showToast({ type: 'success', message: '删除成功' })
    // 刷新列表与额度
    searchParams.current = 1
    initLoading.value = true
    fetchData()
    loadSpace()
  } else {
    showToast('删除失败，' + res.data.message)
  }
}

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.replace('/space')
  }
}

// 容量文本
const sizeText = (size?: string | number) => {
  const n = Number(size ?? 0)
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} MB`
  return `${(n / 1024 / 1024 / 1024).toFixed(2)} GB`
}
</script>

<template>
  <div class="sd-page">
    <van-nav-bar :title="space?.spaceName ?? '空间详情'" left-arrow @click-left="goBack" />

    <van-loading v-if="spaceLoading" class="sd-loading" size="24" vertical>加载中...</van-loading>

    <template v-else>
      <!-- 空间信息卡 -->
      <div class="space-card">
        <div class="space-count">
          <span class="count-num">{{ space?.totalCount ?? total }}</span>
          <span class="count-unit">张图片</span>
        </div>
        <div class="space-size">
          已用 {{ sizeText(space?.totalSize) }}
          <template v-if="space?.maxSize"> / {{ sizeText(space?.maxSize) }}</template>
        </div>
      </div>

      <!-- 图片列表 -->
      <div class="pic-area">
        <van-list
          v-model:loading="loading"
          :finished="finished"
          finished-text="没有更多了"
          loading-text="加载中..."
          @load="onLoad"
        >
          <PictureWaterfall :pictures="dataList" show-op @edit="doEditPicture" @delete="doDeletePicture" />
          <van-empty v-if="dataList.length === 0" description="空间里还没有图片">
            <van-button round type="primary" size="small" @click="goUpload">去上传</van-button>
          </van-empty>
        </van-list>
      </div>

      <!-- 底部上传按钮 -->
      <div class="upload-bar">
        <van-button round block type="primary" icon="photograph" @click="goUpload">
          上传图片到该空间
        </van-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.sd-page {
  min-height: 100vh;
  background: var(--bg-page);
  padding-bottom: calc(88px + var(--safe-bottom));
}

.sd-page :deep(.van-nav-bar) {
  padding-top: var(--safe-top);
}

.sd-loading {
  padding-top: 30vh;
  justify-content: center;
}

/* 空间信息卡 */
.space-card {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin: 12px 16px 0;
  padding: 16px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
}

.count-num {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-1);
}

.count-unit {
  margin-left: 4px;
  font-size: 13px;
  color: var(--text-2);
}

.space-size {
  font-size: 12px;
  color: var(--text-2);
}

.pic-area {
  padding: 12px 16px 0;
}

/* 底部上传按钮 */
.upload-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 10px 16px calc(10px + var(--safe-bottom));
  background: var(--bg-card);
  box-shadow: 0 -1px 4px rgba(0, 0, 0, 0.06);
  z-index: 100;
}

.upload-bar :deep(.van-button) {
  height: 44px;
  font-size: 16px;
}
</style>
