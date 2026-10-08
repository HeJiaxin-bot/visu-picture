<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { deletePictureUsingPost, listPictureVoByPageUsingPost } from '@/api/pictureController.ts'
import { showToast, showConfirmDialog } from 'vant'
import PictureWaterfall from '@/components/PictureWaterfall.vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

defineOptions({ name: 'MyPicturesPage' })

const router = useRouter()
const loginUserStore = useLoginUserStore()

const dataList = ref<API.PictureVO[]>([])
const total = ref(0)
const loading = ref(false)
const initLoading = ref(true)

const searchParams = reactive<API.PictureQueryRequest>({
  current: 1,
  pageSize: 10,
  sortField: 'editTime',
  sortOrder: 'descend',
})

const finished = computed(
  () => !initLoading.value && dataList.value.length >= total.value,
)

const fetchData = async () => {
  if (!loginUserStore.loginUser.id) {
    // 未登录先取登录态
    await loginUserStore.fetchLoginUser()
  }
  if (!loginUserStore.loginUser.id) {
    initLoading.value = false
    showToast('请先登录')
    router.replace('/user/login?redirect=' + encodeURIComponent('/my/pictures'))
    return
  }
  loading.value = true
  try {
    const res = await listPictureVoByPageUsingPost({
      ...searchParams,
      userId: loginUserStore.loginUser.id,
    })
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
  fetchData()
})

// 编辑 / 删除
const doEditPicture = (picture: API.PictureVO) => {
  router.push({ path: '/add_picture', query: { id: picture.id, spaceId: picture.spaceId } })
}

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
    searchParams.current = 1
    initLoading.value = true
    fetchData()
  } else {
    showToast('删除失败，' + res.data.message)
  }
}

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.replace('/mine')
  }
}
</script>

<template>
  <div class="my-pics-page">
    <van-nav-bar title="我的发布" left-arrow @click-left="goBack" />

    <div class="pic-area">
      <van-list
        v-model:loading="loading"
        :finished="finished"
        finished-text="没有更多了"
        loading-text="加载中..."
        @load="onLoad"
      >
        <PictureWaterfall :pictures="dataList" show-op @edit="doEditPicture" @delete="doDeletePicture" />
        <van-empty v-if="!initLoading && dataList.length === 0" description="还没有发布过图片">
          <van-button round type="primary" size="small" @click="router.replace('/publish')">
            去发布
          </van-button>
        </van-empty>
      </van-list>
    </div>
  </div>
</template>

<style scoped>
.my-pics-page {
  min-height: 100vh;
  background: var(--bg-page);
  padding-bottom: calc(24px + var(--safe-bottom));
}

.my-pics-page :deep(.van-nav-bar) {
  padding-top: var(--safe-top);
}

.pic-area {
  padding: 12px 16px 0;
}
</style>
