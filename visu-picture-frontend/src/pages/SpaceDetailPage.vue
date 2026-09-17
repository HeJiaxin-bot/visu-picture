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
          <img v-if="item.coverPicture" class="space-thumb" :src="item.coverPicture" alt="" />
          <span v-else class="space-avatar" aria-hidden="true">{{ (item.spaceName ?? '空').slice(0, 1) }}</span>
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
          <img v-if="item.space?.coverPicture" class="space-thumb" :src="item.space.coverPicture" alt="" />
          <span v-else class="space-avatar avatar-team" aria-hidden="true">
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

      <!-- 当前空间容量 -->
      <div v-if="space.maxSize" class="storage-bar">
        <a-progress :percent="storagePercent" :show-info="false" size="small" />
        <div class="storage-text">空间 {{ formatSize(space.totalSize) }} / {{ formatSize(space.maxSize) }}</div>
      </div>
    </aside>

    <!-- 主内容区 -->
    <main class="space-main">
      <!-- Hero 封面横幅：空间封面 + 名称 + 统计 + 操作 -->
      <div
        class="space-hero"
        :class="{ 'no-cover': !space.coverPicture, 'cover-editable': canChangeCover }"
        :style="heroStyle"
      >
        <div class="hero-scrim" />
        <!-- 封面编辑层：仅创建者/管理员可见，点击横幅空白处即可更换封面 -->
        <div
          v-if="canChangeCover"
          class="hero-cover-edit"
          :class="{ uploading: heroCoverUploading }"
          @click="triggerCoverUpload"
        >
          <span class="cover-edit-chip">
            <LoadingOutlined v-if="heroCoverUploading" spin />
            <CameraOutlined v-else />
            {{ space.coverPicture ? '更换封面' : '添加封面' }}
          </span>
        </div>
        <input
          ref="coverInputRef"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          class="hero-cover-input"
          @change="onHeroCoverFileChange"
        />
        <div class="hero-content">
          <div class="hero-info">
            <div class="hero-name-row">
              <h1 class="hero-name">{{ space.spaceName }}</h1>
              <span
                class="hero-badge"
                :class="space.spaceType === SPACE_TYPE_ENUM.TEAM ? 'badge-team' : 'badge-private'"
              >
                <span class="badge-dot" />
                {{ SPACE_TYPE_MAP[space.spaceType] }}
              </span>
              <!-- 空间级别 -->
              <span v-if="space.spaceLevel != null" class="hero-badge" :class="levelClass(space.spaceLevel)">
                <CrownOutlined class="badge-icon" />
                {{ SPACE_LEVEL_MAP[space.spaceLevel] }}
              </span>
            </div>
            <div class="hero-meta">
              共 {{ space.totalCount ?? 0 }} 张图片 · 已用 {{ formatSize(space.totalSize) }}
            </div>
          </div>
          <div class="hero-actions">
            <a-button
              v-if="canUploadPicture"
              class="hero-btn hero-btn-primary"
              @click="router.push(`/add_picture?spaceId=${id}`)"
            >
              <template #icon><PlusOutlined /></template>
              创建图片
            </a-button>
            <a-button
              v-if="canManageSpaceUser && space.spaceType === SPACE_TYPE_ENUM.TEAM"
              class="hero-btn"
              :icon="h(TeamOutlined)"
              @click="router.push(`/spaceUserManage/${id}`)"
            >
              成员管理
            </a-button>
            <a-button
              v-if="canManageSpaceUser"
              class="hero-btn"
              :icon="h(BarChartOutlined)"
              @click="router.push(`/space_analyze?spaceId=${id}`)"
            >
              空间分析
            </a-button>
            <a-button v-if="canEditPicture" class="hero-btn" :icon="h(EditOutlined)" @click="doBatchEdit">
              批量编辑
            </a-button>
            <a-button
              v-if="canQuitTeam"
              class="hero-btn hero-btn-danger"
              :icon="h(LogoutOutlined)"
              @click="doQuitTeam"
            >
              退出团队
            </a-button>
          </div>
        </div>
      </div>

      <!-- 紧凑搜索区：快速关键词栏 + 可展开的高级筛选面板 -->
      <div class="search-dock">
        <div class="search-pill">
          <SearchOutlined class="pill-icon" />
          <input
            v-model="quickSearchText"
            class="pill-input"
            type="text"
            placeholder="搜索空间内的图片，回车搜索"
            @keydown.enter="doQuickSearch"
          />
          <span v-if="quickSearchText" class="pill-clear" title="清空" @click="clearQuickSearch">
            <CloseOutlined />
          </span>
          <button
            class="filter-toggle"
            :class="{ active: advancedOpen || hasAdvancedFilter }"
            @click="advancedOpen = !advancedOpen"
          >
            <FilterOutlined />
            <span>筛选</span>
            <i v-if="hasAdvancedFilter" class="filter-dot" />
          </button>
        </div>
        <div class="advanced-panel" :class="{ open: advancedOpen }">
          <div class="advanced-inner">
            <div class="advanced-card">
              <PictureSearchForm :onSearch="onSearch" />
              <div class="color-search-row">
                <span class="color-label">按颜色搜索</span>
                <color-picker format="hex" @pureColorChange="onColorChange" />
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 图片列表（滚动加载，紧凑小卡片） -->
      <PictureList
        size="compact"
        :dataList="dataList"
        :loading="loading"
        :finished="finished"
        :showOp="true"
        :canEdit="canEditPicture"
        :canDelete="canDeletePicture"
        :onReload="resetFetch"
        @load-more="onLoadMore"
      />

      <!-- 空状态 -->
      <div v-if="!loading && finished && dataList.length === 0" class="empty-state">
        <div class="empty-icon"><PictureOutlined /></div>
        <div class="empty-title">空间里还没有图片</div>
        <div class="empty-desc">创建第一张图片，开始填充你的图库吧</div>
      </div>
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
  uploadSpaceCoverUsingPost,
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
  CameraOutlined,
  CloseOutlined,
  CrownOutlined,
  EditOutlined,
  FilterOutlined,
  LoadingOutlined,
  LogoutOutlined,
  PictureOutlined,
  PlusOutlined,
  SearchOutlined,
  TeamOutlined,
} from '@ant-design/icons-vue'
import {
  SPACE_LEVEL_ENUM,
  SPACE_LEVEL_MAP,
  SPACE_PERMISSION_ENUM,
  SPACE_TYPE_ENUM,
  SPACE_TYPE_MAP,
} from '../constants/space.ts'
import { quitSpaceUserUsingPost } from '@/api/spaceUserController.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

const router = useRouter()
const loginUserStore = useLoginUserStore()

interface Props {
  id: string | number
}

const props = defineProps<Props>()
const space = ref<API.SpaceVO>({})

// Hero 封面背景（无封面时走渐变兜底样式）
const heroStyle = computed(() =>
  space.value.coverPicture ? { backgroundImage: `url(${space.value.coverPicture})` } : undefined,
)

// 空间级别徽标配色（横幅上使用，需在深色封面上保持可读）
const levelClass = (level?: number) => {
  if (level === SPACE_LEVEL_ENUM.FLAGSHIP) return 'level-flagship'
  if (level === SPACE_LEVEL_ENUM.PROFESSIONAL) return 'level-pro'
  return 'level-common'
}

// ----- 更换空间封面（点击横幅触发，仅创建者/管理员可用） -----
const canChangeCover = computed(() => {
  const loginUser = loginUserStore.loginUser
  if (!loginUser?.id) return false
  return space.value.userId === loginUser.id || loginUser.userRole === 'admin'
})

const coverInputRef = ref<HTMLInputElement>()
const heroCoverUploading = ref(false)

// 打开文件选择框
const triggerCoverUpload = () => {
  if (heroCoverUploading.value) return
  coverInputRef.value?.click()
}

// 选中封面文件后直接上传
const onHeroCoverFileChange = async (e: Event) => {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  // 清空 value，允许重复选择同一文件
  input.value = ''
  if (!file) return
  const isSupported = ['image/jpeg', 'image/png', 'image/webp'].includes(file.type)
  if (!isSupported) {
    message.error('不支持该格式的封面，推荐 jpg / png / webp')
    return
  }
  if (file.size / 1024 / 1024 >= 10) {
    message.error('封面图片不能超过 10M')
    return
  }
  heroCoverUploading.value = true
  try {
    const res = await uploadSpaceCoverUsingPost({ spaceId: props.id as number }, file)
    if (res.data.code === 0) {
      message.success('封面已更新')
      // 刷新横幅与侧栏缩略图
      fetchSpaceDetail()
      fetchMySpaces()
      fetchTeamSpaces()
    } else {
      message.error('封面上传失败，' + res.data.message)
    }
  } catch (err: any) {
    message.error('封面上传失败，' + err.message)
  } finally {
    heroCoverUploading.value = false
  }
}

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

// --------- 获取图片列表 ---------

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

// ----- 快速搜索与高级筛选 -----
// 快速关键词（搜索栏），高级筛选面板中不再重复放关键词字段
const quickSearchText = ref('')
// 高级筛选面板展开状态
const advancedOpen = ref(false)

// 组合搜索：合并高级筛选参数与快速关键词（关键词以快速搜索栏为准）
const onSearch = (newSearchParams: API.PictureQueryRequest) => {
  searchParams.value = {
    ...searchParams.value,
    ...newSearchParams,
    searchText: quickSearchText.value || undefined,
    current: 1,
  }
  fetchData()
}

// 快速搜索栏回车触发
const doQuickSearch = () => onSearch({})

// 清空快速关键词并重新搜索
const clearQuickSearch = () => {
  quickSearchText.value = ''
  doQuickSearch()
}

// 是否设置了高级筛选（用于“筛选”按钮的红点提示）
const ADVANCED_KEYS: (keyof API.PictureQueryRequest)[] = [
  'category',
  'tags',
  'name',
  'introduction',
  'picWidth',
  'picHeight',
  'picFormat',
  'startEditTime',
  'endEditTime',
]
const hasAdvancedFilter = computed(() =>
  ADVANCED_KEYS.some((key) => {
    const value = searchParams.value[key]
    if (value == null) return false
    if (Array.isArray(value)) return value.length > 0
    return true
  }),
)

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
  gap: 16px;
  margin: 0 auto 16px;
  max-width: 1360px;
  padding: 8px 32px 0;
}

/* ---------- 页面载入动画 ---------- */
@keyframes fadeUp {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ---------- 左侧空间栏（通栏融合样式，无卡片底） ---------- */
.space-sidebar {
  flex-shrink: 0;
  width: 248px;
  position: sticky;
  top: 84px;
  height: calc(100vh - 104px);
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow-y: auto;
  padding: 10px 10px 12px 4px;
  background: transparent;
}

.create-space-btn {
  border-radius: 999px;
  height: 40px;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(64, 169, 255, 0.25);
}

.sidebar-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sidebar-label {
  font-size: 12px;
  color: var(--text-disabled);
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
  background: rgba(64, 169, 255, 0.06);
}

.space-item.active {
  background: rgba(64, 169, 255, 0.1);
}

.space-item.active .space-name {
  color: var(--accent);
}

/* 空间封面缩略图（有封面时替代首字头像） */
.space-thumb {
  flex-shrink: 0;
  width: 38px;
  height: 38px;
  border-radius: 10px;
  object-fit: cover;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.12);
}

/* 空间首字头像（无封面时兜底） */
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
  background: linear-gradient(135deg, var(--accent), #69c0ff);
}

.space-avatar.avatar-team {
  background: linear-gradient(135deg, #13c2c2, var(--accent));
}

.space-meta {
  flex: 1;
  min-width: 0;
}

.space-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary-light);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.space-count {
  font-size: 12px;
  color: var(--text-disabled);
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
  color: var(--accent);
  background: rgba(64, 169, 255, 0.1);
}

.sidebar-empty {
  font-size: 12px;
  color: var(--text-disabled);
  text-align: center;
  padding: 6px 0;
}

/* 容量条（贴合图二：底部纯文本 + 细进度线） */
.storage-bar {
  margin-top: auto;
  padding: 10px 10px 4px;
}

.storage-text {
  font-size: 12px;
  color: var(--text-disabled);
  margin-top: 2px;
  text-align: center;
}

/* ---------- 主内容区 ---------- */
.space-main {
  flex: 1;
  min-width: 0;
}

/* ---------- Hero 封面横幅 ---------- */
.space-hero {
  position: relative;
  min-height: 210px;
  border-radius: 20px;
  overflow: hidden;
  display: flex;
  align-items: flex-end;
  margin-bottom: 18px;
  background-size: cover;
  background-position: center;
  box-shadow: var(--card-shadow);
  animation: fadeUp 0.5s ease both;
}

/* 无封面：深海蓝渐变兜底 + 光斑装饰 */
.space-hero.no-cover {
  background:
    radial-gradient(120% 180% at 85% -20%, rgba(105, 192, 255, 0.55), transparent 55%),
    radial-gradient(90% 140% at 8% 110%, rgba(19, 194, 194, 0.38), transparent 52%),
    linear-gradient(125deg, #0e3a6d 0%, #155e9e 52%, #1b7fd4 100%);
}

.space-hero.no-cover::before,
.space-hero.no-cover::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  filter: blur(46px);
  opacity: 0.5;
  pointer-events: none;
}

.space-hero.no-cover::before {
  width: 260px;
  height: 260px;
  background: #69c0ff;
  top: -110px;
  right: 60px;
}

.space-hero.no-cover::after {
  width: 220px;
  height: 220px;
  background: #13c2c2;
  bottom: -120px;
  left: 26%;
}

/* 压暗遮罩：保证白色文字可读 */
.hero-scrim {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    180deg,
    rgba(8, 25, 48, 0.08) 0%,
    rgba(8, 25, 48, 0.05) 40%,
    rgba(8, 25, 48, 0.62) 100%
  );
}

/* ---------- 封面编辑层：悬停横幅时出现，点击更换封面 ---------- */
.hero-cover-edit {
  position: absolute;
  inset: 0;
  z-index: 1;
  display: flex;
  justify-content: flex-end;
  align-items: flex-start;
  padding: 14px 16px;
  cursor: pointer;
  opacity: 0;
  background: rgba(8, 25, 48, 0);
  transition:
    opacity 0.2s ease,
    background 0.2s ease;
}

/* hero-content 同为 z-index:1 且靠后渲染，按钮点击不受编辑层影响 */
.space-hero:hover .hero-cover-edit {
  opacity: 1;
  background: rgba(8, 25, 48, 0.18);
}

.cover-edit-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  background: rgba(255, 255, 255, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.4);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  transition: background 0.2s ease;
}

.hero-cover-edit:hover .cover-edit-chip {
  background: rgba(255, 255, 255, 0.34);
}

/* 触屏设备无 hover：常显提示 */
@media (hover: none) {
  .hero-cover-edit {
    opacity: 1;
  }
}

.hero-cover-input {
  display: none;
}

/* 可编辑封面时：横幅正文不拦截点击（按钮除外），使整个横幅空白处都能触发更换封面 */
.space-hero.cover-editable .hero-content {
  pointer-events: none;
}

.space-hero.cover-editable .hero-content .hero-btn {
  pointer-events: auto;
}

.hero-content {
  position: relative;
  z-index: 1;
  width: 100%;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
  padding: 22px 24px;
}

.hero-name-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.hero-name {
  margin: 0;
  color: #fff;
  font-size: 26px;
  font-weight: 800;
  letter-spacing: 0.5px;
  text-shadow: 0 2px 12px rgba(0, 0, 0, 0.35);
}

/* 横幅上的类型徽标：白色磨砂风 */
.hero-badge {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  line-height: 20px;
  padding: 0 10px;
  border-radius: 999px;
  color: #fff;
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.35);
  backdrop-filter: blur(6px);
}

.hero-badge.badge-private {
  color: #ffd8a8;
  background: rgba(250, 140, 22, 0.3);
  border-color: rgba(255, 216, 168, 0.4);
}

/* 级别徽标：横幅上的玻璃质感，按级别区分色调 */
.badge-icon {
  margin-right: 5px;
  font-size: 12px;
}

.hero-badge.level-common {
  color: rgba(255, 255, 255, 0.92);
  background: rgba(255, 255, 255, 0.16);
  border-color: rgba(255, 255, 255, 0.32);
}

.hero-badge.level-pro {
  color: #b5f5ec;
  background: rgba(19, 194, 194, 0.34);
  border-color: rgba(181, 245, 236, 0.45);
}

.hero-badge.level-flagship {
  color: #ffe58f;
  background: rgba(250, 173, 20, 0.34);
  border-color: rgba(255, 229, 143, 0.5);
}

.badge-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  margin-right: 5px;
  display: inline-block;
}

.hero-meta {
  margin-top: 6px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.88);
  text-shadow: 0 1px 6px rgba(0, 0, 0, 0.3);
}

/* 横幅操作按钮：玻璃磨砂风 */
.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.hero-actions .hero-btn {
  height: 38px;
  padding: 0 16px;
  border-radius: 999px;
  font-weight: 600;
  color: #fff;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.4);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  box-shadow: none;
  transition:
    background 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease,
    transform 0.15s ease;
}

.hero-actions .hero-btn:hover,
.hero-actions .hero-btn:focus-visible {
  background: rgba(255, 255, 255, 0.3);
  border-color: rgba(255, 255, 255, 0.6);
  color: #fff;
}

.hero-actions .hero-btn-primary {
  background: #fff;
  border-color: #fff;
  color: #13548f;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.18);
}

.hero-actions .hero-btn-primary:hover,
.hero-actions .hero-btn-primary:focus-visible {
  background: rgba(255, 255, 255, 0.92);
  border-color: rgba(255, 255, 255, 0.92);
  color: #0e3a6d;
}

.hero-actions .hero-btn-danger:hover,
.hero-actions .hero-btn-danger:focus-visible {
  background: rgba(255, 77, 79, 0.85);
  border-color: transparent;
  color: #fff;
}

/* ---------- 紧凑搜索区 ---------- */
.search-dock {
  margin-bottom: 18px;
  animation: fadeUp 0.5s 0.06s ease both;
}

.search-pill {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px 6px 16px;
  background: var(--bg-card);
  border-radius: 999px;
  border: 1px solid transparent;
  box-shadow: 0 4px 20px rgba(30, 70, 140, 0.08);
  transition:
    box-shadow 0.2s ease,
    border-color 0.2s ease;
}

.search-pill:focus-within {
  border-color: rgba(64, 169, 255, 0.55);
  box-shadow: 0 6px 24px rgba(64, 169, 255, 0.16);
}

.pill-icon {
  flex-shrink: 0;
  color: var(--text-disabled);
  font-size: 15px;
}

.pill-input {
  flex: 1;
  min-width: 0;
  height: 36px;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  color: var(--text-primary-light);
}

.pill-input::placeholder {
  color: var(--text-disabled);
}

.pill-clear {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  font-size: 11px;
  color: var(--text-disabled);
  cursor: pointer;
  transition:
    color 0.15s,
    background 0.15s;
}

.pill-clear:hover {
  color: var(--text-secondary);
  background: rgba(0, 0, 0, 0.05);
}

/* 筛选开关 */
.filter-toggle {
  position: relative;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 16px;
  border-radius: 999px;
  border: 1px solid var(--border-color);
  background: var(--bg-body);
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition:
    color 0.2s,
    border-color 0.2s,
    background 0.2s;
}

.filter-toggle:hover {
  color: var(--accent);
  border-color: rgba(64, 169, 255, 0.5);
}

.filter-toggle.active {
  color: var(--accent);
  border-color: rgba(64, 169, 255, 0.45);
  background: rgba(64, 169, 255, 0.12);
}

.filter-dot {
  position: absolute;
  top: -3px;
  right: -3px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #ff4d4f;
  border: 2px solid var(--bg-card);
}

/* 高级筛选面板：grid-rows 展开动画 */
.advanced-panel {
  display: grid;
  grid-template-rows: 0fr;
  visibility: hidden;
  transition:
    grid-template-rows 0.3s cubic-bezier(0.4, 0, 0.2, 1),
    visibility 0.3s;
}

.advanced-panel.open {
  grid-template-rows: 1fr;
  visibility: visible;
}

.advanced-inner {
  overflow: hidden;
  min-height: 0;
}

.advanced-card {
  margin-top: 10px;
  background: var(--bg-card);
  border-radius: 16px;
  padding: 8px 16px 6px;
  box-shadow: 0 4px 20px rgba(30, 70, 140, 0.06);
}

.advanced-card :deep(.ant-form-item) {
  margin-top: 12px;
  margin-bottom: 12px;
}

.advanced-card :deep(.ant-form-item-label > label) {
  color: var(--text-secondary);
}

.color-search-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0 12px;
  border-top: 1px dashed var(--border-color);
}

.color-label {
  font-size: 13px;
  color: var(--text-secondary);
  flex-shrink: 0;
}

/* 空状态 */
.empty-state {
  text-align: center;
  padding: 70px 0 40px;
}

.empty-icon {
  width: 74px;
  height: 74px;
  margin: 0 auto;
  border-radius: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  color: var(--accent);
  background: rgba(64, 169, 255, 0.1);
  box-shadow: inset 0 0 0 1px rgba(64, 169, 255, 0.18);
}

.empty-title {
  margin-top: 16px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary-light);
}

.empty-desc {
  margin-top: 6px;
  font-size: 13px;
  color: var(--text-disabled);
}

/* 到底提示 */
.list-end-text {
  text-align: center;
  color: var(--text-disabled);
  font-size: 13px;
  padding: 16px 0;
}

/* ---------- 深色模式 ---------- */
html.dark .sidebar-label,
html.dark .space-count,
html.dark .storage-text {
  color: rgba(240, 240, 240, 0.45);
}

html.dark .space-item:hover {
  background: rgba(64, 169, 255, 0.12);
}

html.dark .space-item.active {
  background: rgba(64, 169, 255, 0.2);
}

html.dark .space-item.active .space-name {
  color: var(--accent);
}

/* 深色模式：搜索区 */
html.dark .search-pill {
  background: #2d2d2d;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
}

html.dark .pill-input {
  color: #f0f0f0;
}

html.dark .pill-clear:hover {
  color: rgba(240, 240, 240, 0.7);
  background: rgba(255, 255, 255, 0.08);
}

html.dark .filter-toggle {
  background: rgba(255, 255, 255, 0.05);
  border-color: rgba(255, 255, 255, 0.12);
  color: rgba(240, 240, 240, 0.6);
}

html.dark .filter-dot {
  border-color: #2d2d2d;
}

html.dark .advanced-card {
  background: #2d2d2d;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
}

html.dark .color-search-row {
  border-top-color: rgba(240, 240, 240, 0.08);
}

html.dark .color-label,
html.dark .advanced-card :deep(.ant-form-item-label > label) {
  color: rgba(240, 240, 240, 0.55);
}

/* 深色模式：空状态 */
html.dark .empty-title {
  color: var(--text-primary);
}

html.dark .empty-state .empty-icon {
  background: rgba(64, 169, 255, 0.15);
}

html.dark .sidebar-empty,
html.dark .list-end-text {
  color: rgba(240, 240, 240, 0.35);
}

/* ---------- 响应式：窄屏侧栏转横向滚动条 ---------- */
@media (max-width: 900px) {
  #spaceDetailPage {
    flex-direction: column;
  }

  .space-sidebar {
    position: static;
    width: 100%;
    height: auto;
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
  .sidebar-empty {
    display: none;
  }

  .space-item {
    flex-shrink: 0;
  }

  .storage-bar {
    margin-top: 0;
    padding: 0 8px;
    min-width: 160px;
    flex-shrink: 0;
  }

  .space-hero {
    min-height: 176px;
  }

  .hero-content {
    padding: 16px;
  }

  .hero-name {
    font-size: 21px;
  }

  .hero-actions {
    width: 100%;
  }
}
</style>
