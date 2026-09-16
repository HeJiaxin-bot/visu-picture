<template>
  <div id="spaceDetailPage">
    <!-- 左侧空间栏：新建空间 + 我的空间 + 团队空间 + 容量 -->
    <aside class="space-sidebar">
      <a-button type="primary" block class="create-space-btn" @click="router.push('/add_space')">
        <template #icon><PlusOutlined /></template>
        新建空间
      </a-button>

      <!-- 我的空间（个人） -->
      <div class="sidebar-group">
        <div class="sidebar-label">我的空间</div>
        <div
          v-for="item in mySpaceList"
          :key="item.id"
          class="space-item"
          :class="{ active: item.id == props.id }"
          @click="selectSpace(item.id!)"
        >
          <span class="space-avatar" aria-hidden="true">{{ (item.spaceName ?? '空').slice(0, 1) }}</span>
          <div class="space-meta">
            <div class="space-name">{{ item.spaceName }}</div>
            <div class="space-count">{{ item.totalCount ?? 0 }} 张图片</div>
          </div>
          <span class="space-badge badge-private">私有</span>
        </div>
        <div v-if="mySpaceList.length === 0" class="sidebar-empty">还没有个人空间</div>
      </div>

      <!-- 团队空间 -->
      <div v-if="teamSpaceList.length > 0" class="sidebar-group">
        <div class="sidebar-label">团队空间</div>
        <div
          v-for="item in teamSpaceList"
          :key="item.spaceId"
          class="space-item"
          :class="{ active: item.spaceId == props.id }"
          @click="selectSpace(item.spaceId!)"
        >
          <span class="space-avatar avatar-team" aria-hidden="true">
            {{ (item.space?.spaceName ?? '团').slice(0, 1) }}
          </span>
          <div class="space-meta">
            <div class="space-name">{{ item.space?.spaceName ?? '未命名团队' }}</div>
            <div class="space-count">
              {{ item.space?.totalCount != null ? item.space.totalCount + ' 张图片' : '团队空间' }}
            </div>
          </div>
          <span class="space-badge badge-team">团队</span>
        </div>
      </div>

      <div v-if="mySpaceList.length > 0" class="sidebar-loaded">已加载全部空间</div>

      <!-- 当前空间容量 -->
      <div v-if="space.maxSize" class="storage-bar">
        <a-progress :percent="storagePercent" :show-info="false" size="small" />
        <div class="storage-text">空间 {{ formatSize(space.totalSize) }} / {{ formatSize(space.maxSize) }}</div>
      </div>
    </aside>

    <!-- 主内容区 -->
    <main class="space-main">
      <!-- 空间头部：头像 + 名称 + 类型徽标 -->
      <div class="space-header">
        <span class="space-header-avatar" aria-hidden="true">{{ (space.spaceName ?? '空').slice(0, 1) }}</span>
        <div class="space-header-info">
          <div class="space-header-name">
            {{ space.spaceName }}
            <span
              class="space-badge"
              :class="space.spaceType === SPACE_TYPE_ENUM.TEAM ? 'badge-team' : 'badge-private'"
            >
              <span class="badge-dot" />
              {{ SPACE_TYPE_MAP[space.spaceType] }}
            </span>
          </div>
          <div class="space-header-meta">
            共 {{ space.totalCount ?? 0 }} 张图片 · 已用 {{ formatSize(space.totalSize) }}
          </div>
        </div>
      </div>

      <!-- 操作按钮（仅展示有权限的功能） -->
      <div class="space-actions">
        <a-button
          v-if="canUploadPicture"
          type="primary"
          @click="router.push(`/add_picture?spaceId=${id}`)"
        >
          + 创建图片
        </a-button>
        <a-button
          v-if="canManageSpaceUser && space.spaceType === SPACE_TYPE_ENUM.TEAM"
          :icon="h(TeamOutlined)"
          @click="router.push(`/spaceUserManage/${id}`)"
        >
          成员管理
        </a-button>
        <a-button
          v-if="canManageSpaceUser"
          :icon="h(BarChartOutlined)"
          @click="router.push(`/space_analyze?spaceId=${id}`)"
        >
          空间分析
        </a-button>
        <a-button v-if="canEditPicture" :icon="h(EditOutlined)" @click="doBatchEdit"> 批量编辑</a-button>
        <a-button v-if="canQuitTeam" danger :icon="h(LogoutOutlined)" @click="doQuitTeam">
          退出团队
        </a-button>
      </div>

      <!-- 搜索区：关键词搜索 + 颜色搜索，收进同一张卡片 -->
      <div class="search-card">
        <PictureSearchForm :onSearch="onSearch" />
        <div class="color-search-row">
          <span class="color-label">按颜色搜索</span>
          <color-picker format="hex" @pureColorChange="onColorChange" />
        </div>
      </div>

      <!-- 图片列表（滚动加载） -->
      <PictureList
        :dataList="dataList"
        :loading="loading"
        :finished="finished"
        :showOp="true"
        :canEdit="canEditPicture"
        :canDelete="canDeletePicture"
        :onReload="resetFetch"
        @load-more="onLoadMore"
      />
      <div v-if="finished && dataList.length > 0" class="list-end-text">- 已经到底了 -</div>
      <BatchEditPictureModal
        ref="batchEditPictureModalRef"
        :spaceId="id"
        :pictureList="dataList"
        :onSuccess="onBatchEditPictureSuccess"
      />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  getSpaceVoByIdUsingGet,
  listSpaceVoByPageUsingPost,
} from '@/api/spaceController.ts'
import { listMyTeamSpaceUsingPost } from '@/api/spaceUserController.ts'
import { message, Modal } from 'ant-design-vue'
import {
  listPictureVoByPageUsingPost,
  searchPictureByColorUsingPost,
} from '@/api/pictureController.ts'
import { formatSize } from '@/utils'
import PictureList from '@/components/PictureList.vue'
import PictureSearchForm from '@/components/PictureSearchForm.vue'
import { ColorPicker } from 'vue3-colorpicker'
import 'vue3-colorpicker/style.css'
import BatchEditPictureModal from '@/components/BatchEditPictureModal.vue'
import {
  BarChartOutlined,
  EditOutlined,
  LogoutOutlined,
  PlusOutlined,
  TeamOutlined,
} from '@ant-design/icons-vue'
import { SPACE_PERMISSION_ENUM, SPACE_TYPE_ENUM, SPACE_TYPE_MAP } from '../constants/space.ts'
import { quitSpaceUserUsingPost } from '@/api/spaceUserController.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

const router = useRouter()
const loginUserStore = useLoginUserStore()

interface Props {
  id: string | number
}

const props = defineProps<Props>()
const space = ref<API.SpaceVO>({})

// 通用权限检查函数
function createPermissionChecker(permission: string) {
  return computed(() => {
    return (space.value.permissionList ?? []).includes(permission)
  })
}

// 定义权限检查
const canManageSpaceUser = createPermissionChecker(SPACE_PERMISSION_ENUM.SPACE_USER_MANAGE)
const canUploadPicture = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_UPLOAD)
const canEditPicture = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_EDIT)
const canDeletePicture = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_DELETE)

// 退出团队：仅团队空间且当前用户是成员（有查看权限）时显示
const canQuitTeam = computed(
  () => space.value.spaceType === SPACE_TYPE_ENUM.TEAM && canViewPicture.value,
)
const canViewPicture = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_VIEW)

// -------- 左侧栏：空间列表 --------
const mySpaceList = ref<API.SpaceVO[]>([])
// 团队空间：listMyTeamSpaceUsingPost 返回的是成员关系记录（SpaceUserVO），空间信息在 item.space 中
const teamSpaceList = ref<API.SpaceUserVO[]>([])

// 获取我的个人空间列表
const fetchMySpaces = async () => {
  const loginUser = loginUserStore.loginUser
  if (!loginUser?.id) return
  try {
    // pageSize 上限为 20（后端防爬限制），超过会直接报参数错误
    const res = await listSpaceVoByPageUsingPost({
      userId: loginUser.id,
      current: 1,
      pageSize: 20,
      spaceType: SPACE_TYPE_ENUM.PRIVATE,
    })
    if (res.data.code === 0) {
      mySpaceList.value = res.data.data?.records ?? []
    } else {
      console.warn('获取我的空间列表失败：' + res.data.message)
    }
  } catch (e) {
    console.warn('获取我的空间列表异常', e)
  }
}

// 获取我的团队空间列表
const fetchTeamSpaces = async () => {
  try {
    const res = await listMyTeamSpaceUsingPost()
    if (res.data.code === 0) {
      teamSpaceList.value = res.data.data ?? []
    } else {
      console.warn('获取团队空间列表失败：' + res.data.message)
    }
  } catch (e) {
    console.warn('获取团队空间列表异常', e)
  }
}

// 切换空间（同页面内切换，路由复用触发 watch 重新加载）
const selectSpace = (spaceId: string | number) => {
  if (spaceId == props.id) return
  router.replace(`/space/${spaceId}`)
}

// 当前空间容量百分比（防除零 + 上限 100）
const storagePercent = computed(() => {
  if (!space.value.maxSize || space.value.maxSize <= 0) return 0
  return Math.min(100, Number(((space.value.totalSize * 100) / space.value.maxSize).toFixed(1)))
})

// 退出团队
const doQuitTeam = () => {
  const isOwner = space.value.userId === loginUserStore.loginUser.id
  Modal.confirm({
    title: '退出团队',
    centered: true,
    okText: isOwner ? '确定解散团队' : '确定退出',
    okType: 'danger',
    cancelText: '取消',
    content: isOwner
      ? '您是该团队的创建人，退出后该团队空间将被解散，所有成员的关联记录将一并删除，且不可恢复，确定要解散吗？'
      : '退出后将无法查看和操作该团队空间的图片，确定要退出吗？',
    onOk: async () => {
      try {
        const res = await quitSpaceUserUsingPost({ spaceId: props.id as unknown as number })
        if (res.data.code === 0) {
          message.success('已退出团队')
          // 回到侧栏第一个可用空间，空间不存在则回首页
          const first =
            mySpaceList.value.find((s) => s.id != props.id) ?? teamSpaceList.value[0]?.space
          if (first?.id) {
            selectSpace(first.id)
            fetchTeamSpaces()
          } else {
            router.push('/')
          }
        } else {
          message.error('退出失败，' + res.data.message)
        }
      } catch (e: any) {
        message.error('退出失败，' + e.message)
      }
    },
  })
}

// -------- 获取空间详情 --------
const fetchSpaceDetail = async () => {
  try {
    const res = await getSpaceVoByIdUsingGet({
      id: props.id,
    })
    if (res.data.code === 0 && res.data.data) {
      space.value = res.data.data
    } else {
      message.error('获取空间详情失败，' + res.data.message)
    }
  } catch (e: any) {
    message.error('获取空间详情失败：' + e.message)
  }
}

// --------- 获取图片列表 --------

// 定义数据
const dataList = ref<API.PictureVO[]>([])
const total = ref(0)
const loading = ref(true)

// 搜索条件
const searchParams = ref<API.PictureQueryRequest>({
  current: 1,
  pageSize: 12,
  sortField: 'createTime',
  sortOrder: 'descend',
})

// 是否已加载全部数据
const finished = computed(() => !loading.value && dataList.value.length >= total.value)

// 获取数据（滚动加载模式：第 1 页替换列表，之后追加）
const fetchData = async () => {
  loading.value = true
  // 转换搜索参数
  const params = {
    spaceId: props.id,
    ...searchParams.value,
  }
  try {
    const res = await listPictureVoByPageUsingPost(params)
    if (res.data.code === 0 && res.data.data) {
      const records = res.data.data.records ?? []
      if ((searchParams.value.current ?? 1) <= 1) {
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
  if ((searchParams.value.current ?? 1) > 1) {
    searchParams.value.current = (searchParams.value.current ?? 1) - 1
  }
}

// 页面加载时获取数据，请求一次
onMounted(() => {
  fetchSpaceDetail()
  fetchData()
  fetchMySpaces()
  fetchTeamSpaces()
})

// 滚动到底部时加载下一页
const onLoadMore = () => {
  if (loading.value || finished.value) return
  searchParams.value.current = (searchParams.value.current ?? 1) + 1
  fetchData()
}

// 重置到第一页并重新加载（删除、批量编辑等操作后使用）
const resetFetch = () => {
  searchParams.value.current = 1
  fetchData()
}

// 搜索
const onSearch = (newSearchParams: API.PictureQueryRequest) => {
  searchParams.value = {
    ...searchParams.value,
    ...newSearchParams,
    current: 1,
  }
  fetchData()
}

// 按照颜色搜索
const onColorChange = async (color: string) => {
  loading.value = true
  const res = await searchPictureByColorUsingPost({
    picColor: color,
    spaceId: props.id,
  })
  if (res.data.code === 0 && res.data.data) {
    const data = res.data.data ?? []
    dataList.value = data
    total.value = data.length
  } else {
    message.error('获取数据失败，' + res.data.message)
  }
  loading.value = false
}

// ---- 批量编辑图片 -----
const batchEditPictureModalRef = ref()

// 批量编辑图片成功
const onBatchEditPictureSuccess = () => {
  resetFetch()
}

// 打开批量编辑图片弹窗
const doBatchEdit = () => {
  if (batchEditPictureModalRef.value) {
    batchEditPictureModalRef.value.openModal()
  }
}

// 空间 id 改变时，必须重新获取数据（侧栏切换空间共用同一页面组件）
watch(
  () => props.id,
  () => {
    fetchSpaceDetail()
    resetFetch()
  },
)
</script>

<style scoped>
#spaceDetailPage {
  display: flex;
  align-items: flex-start;
  gap: 24px;
  margin: 0 auto 16px;
  max-width: 1360px;
  padding: 8px 32px 0;
}

/* ---------- 左侧空间栏 ---------- */
.space-sidebar {
  flex-shrink: 0;
  width: 248px;
  position: sticky;
  top: 84px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-height: calc(100vh - 104px);
  overflow-y: auto;
  padding: 14px 12px;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 2px 12px rgba(23, 26, 43, 0.06);
}

.create-space-btn {
  border-radius: 999px;
  height: 40px;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(22, 119, 255, 0.25);
}

.sidebar-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sidebar-label {
  font-size: 12px;
  color: rgba(23, 26, 43, 0.45);
  margin: 4px 4px 2px;
}

/* 空间条目 */
.space-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.2s;
}

.space-item:hover {
  background: rgba(22, 119, 255, 0.06);
}

.space-item.active {
  background: rgba(22, 119, 255, 0.1);
}

.space-item.active .space-name {
  color: #1677ff;
}

/* 空间首字头像 */
.space-avatar {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #4f6bff, #7a5cff);
}

.space-avatar.avatar-team {
  background: linear-gradient(135deg, #13c2c2, #4f6bff);
}

.space-meta {
  flex: 1;
  min-width: 0;
}

.space-name {
  font-size: 14px;
  font-weight: 600;
  color: #171a2b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.space-count {
  font-size: 12px;
  color: rgba(23, 26, 43, 0.45);
  margin-top: 1px;
}

/* 私有 / 团队 徽标 */
.space-badge {
  flex-shrink: 0;
  font-size: 11px;
  line-height: 18px;
  padding: 0 8px;
  border-radius: 999px;
}

.badge-private {
  color: #fa8c16;
  background: rgba(250, 140, 22, 0.12);
}

.badge-team {
  color: #1677ff;
  background: rgba(22, 119, 255, 0.1);
}

.sidebar-empty,
.sidebar-loaded {
  font-size: 12px;
  color: rgba(23, 26, 43, 0.35);
  text-align: center;
  padding: 6px 0;
}

/* 容量条 */
.storage-bar {
  margin-top: auto;
  padding: 10px 10px 4px;
  border-top: 1px solid rgba(23, 26, 43, 0.06);
}

.storage-text {
  font-size: 12px;
  color: rgba(23, 26, 43, 0.45);
  margin-top: 2px;
  text-align: center;
}

/* ---------- 主内容区 ---------- */
.space-main {
  flex: 1;
  min-width: 0;
}

/* 空间头部 */
.space-header {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 16px;
}

.space-header-avatar {
  flex-shrink: 0;
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #7a9bff, #a58bff);
  box-shadow: 0 4px 14px rgba(122, 155, 255, 0.28);
}

.space-header-name {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 20px;
  font-weight: 700;
  color: #171a2b;
}

.space-header-meta {
  font-size: 13px;
  color: rgba(23, 26, 43, 0.5);
  margin-top: 3px;
}

/* 徽标内的状态圆点 */
.badge-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  margin-right: 5px;
  display: inline-block;
  vertical-align: 1px;
}

/* 操作按钮行 */
.space-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
}

/* 搜索区卡片 */
.search-card {
  background: #fff;
  border-radius: 16px;
  padding: 16px 16px 6px;
  margin-bottom: 18px;
  box-shadow: 0 2px 12px rgba(23, 26, 43, 0.05);
}

.search-card :deep(.ant-form-item) {
  margin-bottom: 12px;
}

.search-card :deep(.ant-form-item-label > label) {
  color: rgba(23, 26, 43, 0.55);
}

.color-search-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0 12px;
  border-top: 1px dashed rgba(23, 26, 43, 0.08);
}

.color-label {
  font-size: 13px;
  color: rgba(23, 26, 43, 0.55);
  flex-shrink: 0;
}

/* 到底提示 */
.list-end-text {
  text-align: center;
  color: rgba(23, 26, 43, 0.35);
  font-size: 13px;
  padding: 16px 0;
}

/* ---------- 深色模式 ---------- */
html.dark .space-sidebar {
  background: #1f2338;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.3);
}

html.dark .sidebar-label,
html.dark .space-count,
html.dark .storage-text {
  color: rgba(232, 234, 246, 0.45);
}

html.dark .space-item:hover {
  background: rgba(22, 119, 255, 0.12);
}

html.dark .space-item.active {
  background: rgba(22, 119, 255, 0.2);
}

html.dark .space-item.active .space-name {
  color: #8fa4ff;
}

html.dark .space-name,
html.dark .space-header-name {
  color: #e8eaf6;
}

html.dark .space-header-meta {
  color: rgba(232, 234, 246, 0.5);
}

/* 深色模式：搜索卡片 */
html.dark .search-card {
  background: #1f2338;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.3);
}

html.dark .color-search-row {
  border-top-color: rgba(232, 234, 246, 0.08);
}

html.dark .color-label,
html.dark .search-card :deep(.ant-form-item-label > label) {
  color: rgba(232, 234, 246, 0.55);
}

html.dark .storage-bar {
  border-top-color: rgba(232, 234, 246, 0.08);
}

html.dark .sidebar-empty,
html.dark .sidebar-loaded,
html.dark .list-end-text {
  color: rgba(232, 234, 246, 0.35);
}

/* ---------- 响应式：窄屏侧栏转横向滚动条 ---------- */
@media (max-width: 900px) {
  #spaceDetailPage {
    flex-direction: column;
  }

  .space-sidebar {
    position: static;
    width: 100%;
    max-height: none;
    flex-direction: row;
    align-items: center;
    overflow-x: auto;
    overflow-y: hidden;
    gap: 10px;
    padding: 10px 12px;
  }

  .create-space-btn {
    width: auto;
    flex-shrink: 0;
  }

  .sidebar-group {
    flex-direction: row;
    align-items: center;
  }

  .sidebar-label,
  .sidebar-loaded {
    display: none;
  }

  .space-item {
    flex-shrink: 0;
  }

  .storage-bar {
    margin-top: 0;
    border-top: none;
    padding: 0 8px;
    min-width: 160px;
    flex-shrink: 0;
  }
}
</style>
