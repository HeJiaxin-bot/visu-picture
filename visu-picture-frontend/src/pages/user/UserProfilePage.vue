<template>
  <div id="userProfilePage">
    <!-- 用户信息卡 -->
    <div class="profile-card">
      <a-spin :spinning="loading">
        <div class="profile-top">
          <a-avatar :size="88" :src="profile.userAvatar" class="profile-avatar">
            <template #icon><UserOutlined /></template>
          </a-avatar>
          <div class="profile-info">
            <div class="profile-name">
              {{ profile.userName ?? '未知用户' }}
              <a-tag v-if="isSelf" color="blue">这是我</a-tag>
            </div>
            <div class="profile-desc">{{ profile.userProfile || '这个人很懒，什么都没有留下～' }}</div>
            <div class="profile-extra">加入时间：{{ formatDate(profile.createTime) }}</div>
          </div>
        </div>
      </a-spin>
    </div>

    <!-- 公开作品 -->
    <a-card class="works-card">
      <template #title>
        <span>TA 的公共图库作品</span>
        <span v-if="total > 0" class="works-total">共 {{ total }} 张</span>
      </template>
      <PictureList
        v-if="pictureList.length > 0"
        :dataList="pictureList"
        :loading="picturesLoading"
        :finished="picturesFinished"
      />
      <a-spin v-else-if="picturesLoading" class="works-loading" />
      <a-empty v-else description="TA 还没有在公共图库上传过图片" />
      <!-- 手动查看更多 -->
      <div v-if="pictureList.length > 0 && !picturesFinished" class="works-more">
        <a-button size="large" :loading="picturesLoading" @click="loadMore">
          查看更多
          <span class="works-more-hint">已展示 {{ pictureList.length }} / {{ total }} 张</span>
        </a-button>
      </div>
      <div v-else-if="pictureList.length > PAGE_SIZE" class="works-more">
        <span class="works-all-loaded">已展示全部 {{ total }} 张作品</span>
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { UserOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import PictureList from '@/components/PictureList.vue'
import { getUserVoByIdUsingGet } from '@/api/userController.ts'
import { listPictureVoByPageUsingPost } from '@/api/pictureController.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

const route = useRoute()
const loginUserStore = useLoginUserStore()

// 用户信息
const loading = ref(true)
const profile = reactive<API.UserVO>({})

const isSelf = computed(
  () =>
    loginUserStore.loginUser?.id != null &&
    String(loginUserStore.loginUser.id) === String(route.params.id),
)

const fetchUserProfile = async () => {
  const id = route.params.id
  if (!id) return
  loading.value = true
  try {
    const res = await getUserVoByIdUsingGet({ id: id as unknown as number })
    if (res.data.code === 0 && res.data.data) {
      Object.assign(profile, res.data.data)
    } else {
      message.error('获取用户信息失败，' + res.data.message)
    }
  } catch (e) {
    message.error('获取用户信息失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

// 公开作品列表（分页 + 手动查看更多）
const PAGE_SIZE = 8
const pictureList = ref<API.PictureVO[]>([])
const picturesLoading = ref(false)
const picturesFinished = ref(false)
const pictureCurrent = ref(1)
const total = ref(0)

const fetchPictures = async () => {
  const id = route.params.id
  if (!id || picturesLoading.value || picturesFinished.value) return
  picturesLoading.value = true
  try {
    const res = await listPictureVoByPageUsingPost({
      userId: id as unknown as number,
      current: pictureCurrent.value,
      pageSize: PAGE_SIZE,
      sortField: 'editTime',
      sortOrder: 'descend',
    })
    if (res.data.code === 0 && res.data.data) {
      const records = res.data.data.records ?? []
      pictureList.value.push(...records)
      total.value = Number(res.data.data.total ?? 0)
      if (pictureList.value.length >= total.value || records.length < PAGE_SIZE) {
        picturesFinished.value = true
      } else {
        pictureCurrent.value += 1
      }
    } else {
      message.error('加载作品失败，' + res.data.message)
      picturesFinished.value = true
    }
  } catch (e) {
    picturesFinished.value = true
  } finally {
    picturesLoading.value = false
  }
}

const loadMore = () => fetchPictures()

// 路由参数变化时重新加载（同页面切换不同用户）
watch(
  () => route.params.id,
  (id) => {
    if (!id) return
    // 重置作品列表
    pictureList.value = []
    pictureCurrent.value = 1
    picturesFinished.value = false
    total.value = 0
    fetchUserProfile()
    fetchPictures()
  },
  { immediate: true },
)

// 时间格式化
const formatDate = (time?: string) => {
  if (!time) return '-'
  let d = new Date(time)
  if (isNaN(d.getTime())) {
    d = new Date(time.replace(/-/g, '/').replace('T', ' '))
  }
  if (isNaN(d.getTime())) return '-'
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
</script>

<style scoped>
#userProfilePage {
  max-width: 1600px;
  margin: 0 auto 16px;
  padding: 24px 32px;
  box-sizing: border-box;
}

.profile-card {
  background: #fff;
  border-radius: 16px;
  padding: 32px;
  box-shadow: 0 6px 24px rgba(31, 45, 92, 0.06);
  margin-bottom: 20px;
}

.profile-top {
  display: flex;
  align-items: center;
  gap: 24px;
}

.profile-avatar {
  background: rgba(61, 90, 245, 0.1);
  flex-shrink: 0;
}

.profile-name {
  font-size: 22px;
  font-weight: 700;
  color: #171a2b;
  display: flex;
  align-items: center;
  gap: 10px;
}

.profile-desc {
  margin-top: 8px;
  color: rgba(35, 44, 86, 0.65);
  font-size: 14px;
}

.profile-extra {
  margin-top: 6px;
  color: rgba(35, 44, 86, 0.4);
  font-size: 13px;
}

.works-card {
  border-radius: 16px;
  box-shadow: 0 6px 24px rgba(31, 45, 92, 0.06);
}

.works-total {
  margin-left: 10px;
  font-size: 13px;
  font-weight: 400;
  color: rgba(35, 44, 86, 0.45);
}

.works-loading {
  display: block;
  margin: 60px auto;
}

.works-more {
  display: flex;
  justify-content: center;
  padding-top: 16px;
}

.works-more-hint {
  margin-left: 8px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}

.works-all-loaded {
  color: rgba(0, 0, 0, 0.45);
  font-size: 13px;
}

/* 深色模式 */
html.dark .profile-card {
  background: rgba(255, 255, 255, 0.06);
}

html.dark .profile-name {
  color: #e8eaf6;
}

html.dark .profile-desc {
  color: rgba(200, 208, 240, 0.65);
}

html.dark .profile-extra,
html.dark .works-total,
html.dark .works-more-hint,
html.dark .works-all-loaded {
  color: rgba(200, 208, 240, 0.45);
}
</style>
