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
            <!-- 视界号（用户 id）：快捷复制 -->
            <div class="profile-vid">
              <span class="vid-label">视界号</span>
              <span class="vid-value">{{ profile.id ?? '-' }}</span>
              <button
                class="vid-copy-btn"
                type="button"
                aria-label="复制视界号"
                title="点击复制"
                @click="copyVid"
              >
                <CopyOutlined />
              </button>
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
import { CopyOutlined, UserOutlined } from '@ant-design/icons-vue'
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

// 视界号快捷复制
const copyVid = async () => {
  if (profile.id == null) return
  try {
    await navigator.clipboard.writeText(String(profile.id))
    message.success('视界号已复制')
  } catch {
    message.error('复制失败，请手动复制')
  }
}

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
  max-width: 1000px;
  width: 100%;
  margin: 0 auto;
  padding: 24px 16px;
  box-sizing: border-box;
}

.profile-card {
  background: var(--bg-card);
  border-radius: 16px;
  padding: 32px;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.08);
  margin-bottom: 20px;
}

.profile-top {
  display: flex;
  align-items: center;
  gap: 24px;
}

.profile-avatar {
  background: rgba(64, 169, 255, 0.1);
  flex-shrink: 0;
}

.profile-name {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary-light);
  display: flex;
  align-items: center;
  gap: 10px;
}

/* 视界号行：胶囊样式 + 复制按钮 */
.profile-vid {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding: 2px 10px 2px 12px;
  border-radius: 999px;
  background: rgba(64, 169, 255, 0.07);
  width: fit-content;
  font-size: 13px;
}

.vid-label {
  color: var(--text-secondary);
}

.vid-value {
  color: var(--accent);
  font-weight: 600;
  letter-spacing: 0.5px;
}

.vid-copy-btn {
  border: none;
  background: transparent;
  color: var(--text-disabled);
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 4px;
  font-size: 13px;
  line-height: 1;
  transition:
    color 0.2s ease,
    background 0.2s ease;
}

.vid-copy-btn:hover {
  color: var(--accent);
  background: rgba(64, 169, 255, 0.12);
}

.profile-desc {
  margin-top: 8px;
  color: var(--text-secondary);
  font-size: 14px;
}

.profile-extra {
  margin-top: 6px;
  color: var(--text-disabled);
  font-size: 13px;
}

.works-card {
  border-radius: 16px;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.08);
}

.works-total {
  margin-left: 10px;
  font-size: 13px;
  font-weight: 400;
  color: var(--text-disabled);
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
  color: var(--text-primary);
}

html.dark .profile-desc {
  color: rgba(179, 179, 179, 0.65);
}

html.dark .profile-vid {
  background: rgba(64, 169, 255, 0.18);
}

html.dark .vid-label {
  color: rgba(179, 179, 179, 0.5);
}

html.dark .vid-value {
  color: var(--accent);
}

html.dark .vid-copy-btn {
  color: rgba(179, 179, 179, 0.4);
}

html.dark .vid-copy-btn:hover {
  color: var(--accent);
  background: rgba(64, 169, 255, 0.25);
}

html.dark .profile-extra,
html.dark .works-total,
html.dark .works-more-hint,
html.dark .works-all-loaded {
  color: rgba(179, 179, 179, 0.45);
}
</style>
