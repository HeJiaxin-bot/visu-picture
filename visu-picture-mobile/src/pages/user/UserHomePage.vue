<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listPictureVoByPageUsingPost } from '@/api/pictureController.ts'
import { getUserVoByIdUsingGet } from '@/api/userController.ts'
import { showToast } from 'vant'
import PictureWaterfall from '@/components/PictureWaterfall.vue'

defineOptions({ name: 'UserHomePage' })

const route = useRoute()
const router = useRouter()

const userId = computed(() => route.params.id as string)

// 用户信息
const user = ref<API.UserVO>()
const userLoading = ref(true)

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

const finished = computed(() => !initLoading.value && dataList.value.length >= total.value)

const loadUser = async () => {
  try {
    const res = await getUserVoByIdUsingGet({ id: userId.value } as any)
    if (res.data.code === 0 && res.data.data) {
      const u = res.data.data
      user.value = u
      document.title = `${u.userName ?? '用户主页'} - 视界云图库`
    }
  } catch (e) {
    showToast('获取用户信息失败')
  } finally {
    userLoading.value = false
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await listPictureVoByPageUsingPost({
      ...searchParams,
      userId: userId.value,
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
  loadUser()
  fetchData()
})

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.replace('/')
  }
}
</script>

<template>
  <div class="user-home-page">
    <van-nav-bar left-arrow @click-left="goBack" />

    <van-loading v-if="userLoading" class="page-loading" size="24" vertical>加载中...</van-loading>

    <template v-else>
      <!-- 用户信息卡 -->
      <div class="user-card">
        <van-image round width="60" height="60" fit="cover" :src="user?.userAvatar">
          <template #error>
            <div class="avatar-fallback">{{ (user?.userName ?? '用')[0] }}</div>
          </template>
        </van-image>
        <div class="user-info">
          <div class="user-name">{{ user?.userName ?? '未知用户' }}</div>
          <div class="user-profile ellipsis-2">
            {{ user?.userProfile || '这个人很懒，什么都没有留下' }}
          </div>
        </div>
      </div>

      <div class="section-title">TA 的图片（{{ total }}）</div>

      <!-- 图片列表 -->
      <div class="pic-area">
        <van-list
          v-model:loading="loading"
          :finished="finished"
          finished-text="没有更多了"
          loading-text="加载中..."
          @load="onLoad"
        >
          <PictureWaterfall :pictures="dataList" />
          <van-empty v-if="!initLoading && dataList.length === 0" description="还没有公开的图片" />
        </van-list>
      </div>
    </template>
  </div>
</template>

<style scoped>
.user-home-page {
  min-height: 100vh;
  background: var(--bg-page);
  padding-bottom: calc(24px + var(--safe-bottom));
}

.user-home-page :deep(.van-nav-bar) {
  padding-top: var(--safe-top);
}

.page-loading {
  padding-top: 30vh;
  justify-content: center;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 12px 16px 0;
  padding: 20px 16px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
}

.avatar-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--brand-weak);
  color: var(--brand);
  font-size: 24px;
  font-weight: 600;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-name {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-1);
}

.user-profile {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-2);
}

.section-title {
  margin: 20px 20px 10px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-1);
}

.pic-area {
  padding: 0 16px;
}
</style>
