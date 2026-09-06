<template>
  <div id="userCenterPage">
    <div class="center-container">
      <!-- 左侧：用户信息卡 -->
      <a-card class="profile-card">
        <div class="profile-top">
          <!-- 头像：点击上传更换 -->
          <a-upload
            :show-upload-list="false"
            :custom-request="handleAvatarUpload"
            :before-upload="beforeAvatarUpload"
            :disabled="avatarUploading"
          >
            <div class="avatar-wrapper" :class="{ uploading: avatarUploading }">
              <a-avatar :src="loginUser.userAvatar" :size="88" class="profile-avatar" />
              <div class="avatar-mask">
                <LoadingOutlined v-if="avatarUploading" spin />
                <CameraOutlined v-else />
                <span>{{ avatarUploading ? '上传中' : '更换头像' }}</span>
              </div>
            </div>
          </a-upload>
          <div class="profile-name-area">
            <div class="profile-name">
              {{ loginUser.userName ?? '视界用户' }}
              <a-tag v-if="loginUser.userRole === 'admin'" color="gold">管理员</a-tag>
              <a-tag v-if="isVip" color="purple">会员</a-tag>
              <a-tag v-if="loginUser.userRole !== 'admin' && !isVip" color="blue">普通用户</a-tag>
            </div>
            <div class="profile-account">视界号：{{ loginUser.id ?? '-' }}</div>
          </div>
        </div>
        <a-divider />
        <div class="profile-desc">
          <div class="desc-label">个性签名</div>
          <div class="desc-content">{{ loginUser.userProfile || '这个人很懒，什么都没有留下～' }}</div>
        </div>
        <div class="profile-desc">
          <div class="desc-label">加入时间</div>
          <div class="desc-content">{{ formatDate(loginUser.createTime) }}</div>
        </div>
        <a-divider />
        <!-- 积分与每日签到 -->
        <div class="points-row">
          <div class="points-info">
            <div class="desc-label">我的积分</div>
            <div class="points-value">
              <b>{{ loginUser.points ?? 0 }}</b> 分
            </div>
          </div>
          <a-button
            type="primary"
            size="small"
            :loading="signingIn"
            :disabled="signedToday"
            @click="handleSignIn"
          >
            {{ signedToday ? '已签到' : '签到' }}
          </a-button>
        </div>
      </a-card>

      <!-- 编辑资料弹窗 -->
      <a-modal v-model:open="editVisible" title="编辑个人资料" :confirm-loading="editSaving" @ok="handleEditSave">
        <a-form :model="editForm" layout="vertical">
          <a-form-item label="昵称">
            <a-input v-model:value="editForm.userName" :maxlength="20" placeholder="请输入昵称" />
          </a-form-item>
          <a-form-item label="个性签名">
            <a-textarea
              v-model:value="editForm.userProfile"
              :rows="3"
              :maxlength="80"
              placeholder="介绍一下自己"
            />
          </a-form-item>
        </a-form>
      </a-modal>

      <!-- 注销账号确认弹窗 -->
      <a-modal
        v-model:open="deregisterVisible"
        title="注销账号"
        :confirm-loading="deregistering"
        ok-text="确认注销"
        ok-type="danger"
        @ok="handleDeregister"
      >
        <a-alert
          type="warning"
          show-icon
          message="注销后账号将无法恢复"
          description="注销即视为您同意删除本账号下的个人信息，注销后您将无法登录本平台，且该账号无法再次注册找回。请谨慎操作。"
        />
      </a-modal>

      <!-- 右侧：快捷入口 -->
      <a-card class="entry-card" title="快捷入口">
        <div class="entry-grid">
          <div class="entry-item" @click="router.push('/')">
            <PictureOutlined class="entry-icon" style="color: #eb2f96" />
            <div class="entry-title">公共图库</div>
            <div class="entry-desc">发现海量优质图片</div>
          </div>
          <div class="entry-item" @click="router.push('/my_space')">
            <FolderOutlined class="entry-icon" style="color: #4f6bff" />
            <div class="entry-title">我的空间</div>
            <div class="entry-desc">管理我的私人空间</div>
          </div>
          <div class="entry-item" @click="router.push('/add_space?type=1')">
            <TeamOutlined class="entry-icon" style="color: #52c41a" />
            <div class="entry-title">创建团队</div>
            <div class="entry-desc">发起多人协作</div>
          </div>
          <div class="entry-item" @click="router.push('/user/invite')">
            <CrownOutlined class="entry-icon" style="color: #faad14" />
            <div class="entry-title">邀请好友</div>
            <div class="entry-desc">邀请 3 人得永久会员</div>
          </div>
          <!-- 编辑资料：弹出弹窗修改昵称与个性签名 -->
          <div class="entry-item" @click="openEditModal">
            <EditOutlined class="entry-icon" style="color: #13c2c2" />
            <div class="entry-title">编辑资料</div>
            <div class="entry-desc">修改昵称与个性签名</div>
          </div>
          <!-- 注销账号：危险操作，二次确认 -->
          <div class="entry-item" @click="deregisterVisible = true">
            <UserDeleteOutlined class="entry-icon" style="color: #ff4d4f" />
            <div class="entry-title">注销账号</div>
            <div class="entry-desc">删除账号且不可恢复</div>
          </div>
          
        </div>
      </a-card>

      <!-- 管理员功能（仅管理员可见） -->
      <a-card v-if="loginUser.userRole === 'admin'" class="entry-card admin-card" title="管理员功能">
        <div class="entry-grid">
          <div class="entry-item" @click="router.push('/admin/userManage')">
            <UserOutlined class="entry-icon" style="color: #4f6bff" />
            <div class="entry-title">用户管理</div>
            <div class="entry-desc">管理平台用户</div>
          </div>
          <div class="entry-item" @click="router.push('/admin/pictureManage')">
            <PictureOutlined class="entry-icon" style="color: #13c2c2" />
            <div class="entry-title">图片管理</div>
            <div class="entry-desc">审核与管理全站图片</div>
          </div>
          <div class="entry-item" @click="router.push('/admin/spaceManage')">
            <AppstoreOutlined class="entry-icon" style="color: #722ed1" />
            <div class="entry-title">空间管理</div>
            <div class="entry-desc">查看与管理所有空间</div>
          </div>
        </div>
      </a-card>

      <!-- 我的公共图库作品 -->
      <a-card class="my-pictures-card" title="我的公共图库作品">
        <template #extra>
          <div class="pic-extra">
            <span class="pic-count">已过审 {{ pictureTotal }} 张，正在公共图库展示</span>
            <a-button type="primary" size="small" @click="router.push('/add_picture')">
              上传图片
            </a-button>
          </div>
        </template>
        <PictureList
          v-if="pictureList.length > 0"
          :dataList="pictureList"
          :loading="picturesLoading"
          :finished="picturesFinished"
          :showOp="true"
          :canEdit="true"
          :canDelete="true"
          :onReload="reloadPictures"
          @load-more="loadMorePictures"
        />
        <a-spin v-else-if="picturesLoading" class="pic-loading" />
        <a-empty v-else description="还没有在公共图库上传过图片">
          <a-button type="primary" @click="router.push('/add_picture')">
            去上传第一张图片
          </a-button>
        </a-empty>
      </a-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  AppstoreOutlined,
  CameraOutlined,
  CrownOutlined,
  EditOutlined,
  FolderOutlined,
  LoadingOutlined,
  PictureOutlined,
  TeamOutlined,
  UserDeleteOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import PictureList from '@/components/PictureList.vue'
import { listPictureVoByPageUsingPost } from '@/api/pictureController.ts'
import {
  deregisterAccountUsingPost,
  signInUsingPost,
  updateMyInfoUsingPost,
  uploadAvatarUsingPost,
} from '@/api/userController.ts'
import type { UploadProps } from 'ant-design-vue'

const router = useRouter()
const loginUserStore = useLoginUserStore()
const loginUser = computed(() => loginUserStore.loginUser)

// ----- 编辑个人资料弹窗 -----
const editVisible = ref(false)
const editSaving = ref(false)
const editForm = reactive<API.UserUpdateRequest>({
  userName: '',
  userProfile: '',
})
const openEditModal = () => {
  editForm.userName = loginUser.value.userName ?? ''
  editForm.userProfile = loginUser.value.userProfile ?? ''
  editVisible.value = true
}

// ----- 注销账号 -----
const deregisterVisible = ref(false)
const deregistering = ref(false)

const handleDeregister = async () => {
  deregistering.value = true
  try {
    const res = await deregisterAccountUsingPost()
    if (res.data.code === 0 && res.data.data) {
      message.success('账号已注销，感谢您曾经的使用')
      deregisterVisible.value = false
      loginUserStore.setLoginUser({ userName: '未登录' })
      router.push('/')
    } else {
      message.error(res.data.message ?? '注销失败')
    }
  } catch {
    message.error('注销失败，请稍后重试')
  } finally {
    deregistering.value = false
  }
}
const handleEditSave = async () => {
  const name = editForm.userName?.trim() ?? ''
  if (!name) {
    message.warning('昵称不能为空')
    return
  }
  editSaving.value = true
  try {
    const res = await updateMyInfoUsingPost({
      id: loginUser.value.id,
      userName: name,
      userProfile: editForm.userProfile?.trim() ?? '',
    })
    if (res.data.code === 0) {
      message.success('保存成功')
      editVisible.value = false
      // 拉取最新用户信息，同步侧边栏等处的展示
      await loginUserStore.fetchLoginUser()
    } else {
      message.error(res.data.message ?? '保存失败')
    }
  } catch (e: any) {
    message.error('保存失败，' + (e?.message ?? ''))
  } finally {
    editSaving.value = false
  }
}

// ----- 我的公共图库作品（仅展示已过审图片，后端对公开查询自动过滤） -----
const PAGE_SIZE = 20
const pictureList = ref<API.PictureVO[]>([])
const picturesLoading = ref(false)
const picturesFinished = ref(false)
const pictureCurrent = ref(1)
const pictureTotal = ref(0)

const fetchMyPublicPictures = async () => {
  if (!loginUser.value.id || picturesLoading.value || picturesFinished.value) return
  picturesLoading.value = true
  try {
    const res = await listPictureVoByPageUsingPost({
      userId: loginUser.value.id,
      current: pictureCurrent.value,
      pageSize: PAGE_SIZE,
      sortField: 'editTime',
      sortOrder: 'descend',
    })
    if (res.data.code === 0 && res.data.data) {
      const records = res.data.data.records ?? []
      pictureList.value.push(...records)
      pictureTotal.value = Number(res.data.data.total ?? 0)
      if (pictureList.value.length >= pictureTotal.value || records.length < PAGE_SIZE) {
        picturesFinished.value = true
      } else {
        pictureCurrent.value += 1
      }
    } else {
      message.error('加载我的作品失败，' + res.data.message)
      picturesFinished.value = true
    }
  } catch (error) {
    picturesFinished.value = true
  } finally {
    picturesLoading.value = false
  }
}

const loadMorePictures = () => fetchMyPublicPictures()

// 删除/编辑后重置并重新加载列表
const reloadPictures = () => {
  pictureList.value = []
  pictureCurrent.value = 1
  picturesFinished.value = false
  fetchMyPublicPictures()
}

// 登录态就绪后加载（登录用户信息为异步获取）
watch(
  () => loginUser.value.id,
  (id) => {
    if (id) fetchMyPublicPictures()
  },
  { immediate: true },
)

// 日期解析：兼容 ISO 带 T（"2026-09-05T14:58:59.000+00:00"）与空格分隔（"2026-09-05 22:58:59"）两种格式
// 直接对 ISO 字符串替换 '-' 会得到 "2026/09/05T..." 混合格式导致 Invalid Date
const parseDate = (time?: string): Date | null => {
  if (!time) return null
  let d = new Date(time)
  if (isNaN(d.getTime())) {
    d = new Date(time.replace(/-/g, '/').replace('T', ' '))
  }
  return isNaN(d.getTime()) ? null : d
}

// VIP 有效期是否未过（未过期即 VIP 用户）
const isVip = computed(() => {
  const expire = parseDate(loginUser.value.vipExpireTime)
  return !!expire && expire.getTime() > Date.now()
})

// ----- 每日签到 -----
const signingIn = ref(false)

// 最近签到时间是否在今天（用于按钮置灰）
const signedToday = computed(() => {
  const lastDate = parseDate(loginUser.value.lastSignInTime)
  if (!lastDate) return false
  const now = new Date()
  return (
    lastDate.getFullYear() === now.getFullYear() &&
    lastDate.getMonth() === now.getMonth() &&
    lastDate.getDate() === now.getDate()
  )
})

const handleSignIn = async () => {
  signingIn.value = true
  try {
    const res = await signInUsingPost()
    if (res.data.code === 0) {
      const newPoints = res.data.data ?? (loginUser.value.points ?? 0) + 5
      message.success(`签到成功，获得 5 积分，当前积分：${newPoints}`)
      // 立即更新本地登录态：按钮马上变"已签到"，导航积分徽章同步
      loginUserStore.setLoginUser({
        ...loginUser.value,
        points: newPoints,
        lastSignInTime: new Date().toLocaleString('sv-SE'),
      })
      // 再从服务端刷新一次兜底
      loginUserStore.fetchLoginUser()
    } else {
      message.error(res.data.message ?? '签到失败')
    }
  } catch (error: any) {
    message.error('签到失败，' + (error?.message ?? '请稍后重试'))
  } finally {
    signingIn.value = false
  }
}

// ----- 头像上传 -----
const avatarUploading = ref(false)

/**
 * 上传前校验（与图片上传一致的格式与大小限制）
 */
const beforeAvatarUpload = (file: UploadProps['fileList'][number]) => {
  const isImage = ['image/jpeg', 'image/png', 'image/webp'].includes(file.type)
  if (!isImage) {
    message.error('不支持上传该格式的图片，推荐 jpg 或 png')
  }
  const isLt2M = file.size / 1024 / 1024 < 2
  if (!isLt2M) {
    message.error('头像图片不能超过 2MB')
  }
  return isImage && isLt2M
}

/**
 * 上传头像，成功后直接用返回的最新用户信息更新登录态（导航栏头像同步更新）
 */
const handleAvatarUpload = async ({ file }: any) => {
  avatarUploading.value = true
  try {
    const res = await uploadAvatarUsingPost({}, file)
    if (res.data.code === 0 && res.data.data) {
      message.success('头像更新成功')
      loginUserStore.setLoginUser(res.data.data)
    } else {
      message.error('头像更新失败，' + res.data.message)
    }
  } catch (error: any) {
    message.error('头像更新失败，' + (error?.message ?? '请稍后重试'))
  } finally {
    avatarUploading.value = false
  }
}

// 注册时间格式化
const formatDate = (time?: string) => {
  if (!time) return '-'
  return time.replace('T', ' ').slice(0, 16)
}
</script>

<style scoped>
#userCenterPage {
  max-width: 1000px;
  margin: 0 auto;
  padding: 24px 16px;
  width: 100%;
}

.center-container {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 16px;
}

/* 管理员功能卡片独占一行 */
.admin-card {
  grid-column: 1 / -1;
}

/* 我的公共图库作品独占一行 */
.my-pictures-card {
  grid-column: 1 / -1;
}

.pic-extra {
  display: flex;
  align-items: center;
  gap: 12px;
}

.pic-count {
  color: rgba(35, 44, 86, 0.55);
  font-size: 12px;
}

.pic-loading {
  display: block;
  margin: 40px auto;
}

@media (max-width: 860px) {
  .center-container {
    grid-template-columns: 1fr;
  }
}

.profile-top {
  display: flex;
  align-items: center;
  gap: 20px;
}

.profile-avatar {
  background: #eef2ff;
  flex-shrink: 0;
}

/* 头像上传：悬停遮罩 */
.avatar-wrapper {
  position: relative;
  width: 88px;
  height: 88px;
  border-radius: 50%;
  overflow: hidden;
  cursor: pointer;
  flex-shrink: 0;
}

.avatar-wrapper .avatar-mask {
  position: absolute;
  inset: auto 0 0 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 4px 0;
  font-size: 12px;
  color: #fff;
  background: rgba(23, 26, 43, 0.55);
  opacity: 0;
  transform: translateY(100%);
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}

.avatar-wrapper:hover .avatar-mask {
  opacity: 1;
  transform: translateY(0);
}

.avatar-wrapper.uploading .avatar-mask {
  opacity: 1;
  transform: translateY(0);
}

.profile-name {
  font-size: 18px;
  font-weight: 700;
  color: #26283a;
  display: flex;
  align-items: center;
  gap: 8px;
}

.profile-account {
  color: rgba(35, 44, 86, 0.55);
  font-size: 13px;
  margin-top: 6px;
}

.profile-desc {
  margin-bottom: 14px;
}

.desc-label {
  color: rgba(35, 44, 86, 0.45);
  font-size: 12px;
  margin-bottom: 4px;
}

.desc-content {
  color: #26283a;
  font-size: 14px;
}

/* 积分与每日签到 */
.points-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
}

.points-value {
  color: #26283a;
  font-size: 14px;
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.points-value b {
  color: #3d5af5;
  font-size: 20px;
}

.entry-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.entry-item {
  border: 1px solid #e4e8f2;
  border-radius: 10px;
  padding: 16px;
  cursor: pointer;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.entry-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(37, 55, 120, 0.1);
}

.entry-icon {
  font-size: 26px;
}

.entry-title {
  font-weight: 600;
  color: #26283a;
  margin-top: 8px;
}

.entry-desc {
  color: rgba(35, 44, 86, 0.55);
  font-size: 12px;
  margin-top: 2px;
}
</style>
