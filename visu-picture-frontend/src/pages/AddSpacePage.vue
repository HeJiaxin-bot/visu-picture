<template>
  <div id="addSpacePage">
    <!-- 页头：类型图标 + 标题 + 说明 -->
    <div class="page-header">
      <div class="header-icon">
        <TeamOutlined v-if="spaceType === SPACE_TYPE_ENUM.TEAM" />
        <UserOutlined v-else />
      </div>
      <div class="header-text">
        <h2>{{ isEdit ? '编辑' : '创建' }}{{ SPACE_TYPE_MAP[spaceType] }}</h2>
        <p class="header-desc">{{ headerDesc }}</p>
      </div>
    </div>

    <div class="page-body">
      <!-- 左侧：空间信息表单 -->
      <a-card class="form-card" :bordered="false">
        <a-form
          name="spaceForm"
          layout="vertical"
          :model="spaceForm"
          :rules="formRules"
          @finish="handleSubmit"
        >
          <a-form-item name="spaceName" label="空间名称">
            <a-input
              v-model:value="spaceForm.spaceName"
              placeholder="请输入空间名称，如：产品设计组素材库"
              allow-clear
              :maxlength="20"
              show-count
            />
          </a-form-item>
          <a-form-item name="coverPicture" label="空间封面">
            <div class="cover-picker">
              <a-upload
                :show-upload-list="false"
                accept="image/jpeg,image/png,image/webp"
                :before-upload="beforeCoverUpload"
              >
                <div class="cover-slot" :class="{ filled: coverPreview }">
                  <template v-if="coverPreview">
                    <img :src="coverPreview" class="cover-preview" alt="空间封面预览" />
                    <div class="cover-hover-mask">
                      <PictureOutlined class="mask-icon" />
                      {{ coverFile ? '点击更换封面' : '点击上传新封面' }}
                    </div>
                  </template>
                  <div v-else class="cover-empty">
                    <PictureOutlined class="cover-icon" />
                    <div class="cover-text">添加空间封面</div>
                    <div class="cover-hint">将展示在空间页顶部横幅，建议使用 16:9 横图</div>
                  </div>
                </div>
              </a-upload>
              <div v-if="coverFile" class="cover-remove-row">
                <a-button type="link" danger size="small" @click="removeCover">
                  <template #icon><DeleteOutlined /></template>
                  撤销选择
                </a-button>
              </div>
            </div>
          </a-form-item>
          <a-form-item name="spaceLevel" label="空间级别">
            <div class="level-grid">
              <div
                v-for="level in spaceLevelList"
                :key="level.value"
                class="level-card"
                :class="{ active: spaceForm.spaceLevel === level.value, locked: isLevelLocked(level) }"
                @click="chooseLevel(level)"
              >
                <div class="level-icon">
                  <component :is="LEVEL_ICONS[level.value ?? 0] ?? CloudOutlined" />
                </div>
                <div class="level-name">
                  {{ level.text }}
                  <a-tag v-if="level.value === SPACE_LEVEL_ENUM.COMMON && !isEdit" color="blue" class="rec-tag">
                    推荐
                  </a-tag>
                </div>
                <div class="level-quota">{{ formatSize(level.maxSize) }} / {{ level.maxCount }} 张</div>
                <div class="level-state">
                  <template v-if="isLevelLocked(level)">
                    <LockOutlined /> {{ levelLockText(level) }}
                  </template>
                  <template v-else>可开通</template>
                </div>
              </div>
            </div>
          </a-form-item>

          <a-alert
            v-if="!isEdit && !isVip && !isAdmin"
            class="tip-alert"
            type="info"
            show-icon
            message="普通版可直接创建；专业版为会员专享，开通会员后即可自助创建；旗舰版暂未开放，如需开通请联系管理员。"
          />

          <a-form-item class="submit-item">
            <a-button type="primary" html-type="submit" :loading="loading" size="large" block>
              {{ isEdit ? '保存修改' : '立即创建' }}
            </a-button>
          </a-form-item>
        </a-form>
      </a-card>

      <!-- 右侧：空间级别对比 -->
      <a-card class="compare-card" :bordered="false" title="空间级别对比">
        <div class="compare-list">
          <div
            v-for="level in spaceLevelList"
            :key="level.value"
            class="compare-row"
            :class="{ active: spaceForm.spaceLevel === level.value }"
          >
            <div class="compare-name">
              <component :is="LEVEL_ICONS[level.value ?? 0] ?? CloudOutlined" class="compare-icon" />
              {{ level.text }}
            </div>
            <div class="compare-quota">
              <span class="quota-item">{{ formatSize(level.maxSize) }}</span>
              <span class="quota-item">{{ level.maxCount }} 张</span>
            </div>
            <a-tag v-if="!isLevelLocked(level)" color="success">可开通</a-tag>
            <a-tag v-else>{{ levelLockText(level) }}</a-tag>
          </div>
        </div>
        <template #extra>
          <a-button type="link" size="small" @click="router.push('/my_space')">我的空间</a-button>
        </template>
      </a-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  CloudOutlined,
  CrownOutlined,
  DeleteOutlined,
  LockOutlined,
  PictureOutlined,
  RocketOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import {
  addSpaceUsingPost,
  getSpaceVoByIdUsingGet,
  listSpaceLevelUsingGet,
  updateSpaceUsingPost,
  uploadSpaceCoverUsingPost,
} from '@/api/spaceController.ts'
import { useRoute, useRouter } from 'vue-router'
import { SPACE_LEVEL_ENUM, SPACE_TYPE_ENUM, SPACE_TYPE_MAP } from '@/constants/space.ts'
import { formatSize } from '../utils'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

// 空间级别图标
const LEVEL_ICONS: Record<number, any> = {
  0: CloudOutlined,
  1: RocketOutlined,
  2: CrownOutlined,
}

const space = ref<API.SpaceVO>()
// 表单数据（创建/编辑共用）
interface SpaceForm {
  spaceName?: string
  spaceLevel?: number
}
const spaceForm = reactive<SpaceForm>({})
const loading = ref(false)

const route = useRoute()
const router = useRouter()
// 空间类别，默认为私有空间；编辑模式下由接口数据回填
const spaceType = ref<number>(
  route.query?.type ? Number(route.query.type) : SPACE_TYPE_ENUM.PRIVATE,
)

// 是否为编辑模式
const isEdit = computed(() => !!route.query?.id)

// 管理员不受级别限制
const loginUserStore = useLoginUserStore()
const isAdmin = computed(() => loginUserStore.loginUser.userRole === 'admin')

// 是否为有效会员（与后端一致：以到期时间判断，无到期时间时回退到会员角色）
const isVip = computed(() => {
  const user = loginUserStore.loginUser
  const expireTime = user.vipExpireTime
  if (!expireTime) return user.userRole === 'vip'
  return new Date(expireTime).getTime() > Date.now()
})

// 页头说明文案
const headerDesc = computed(() => {
  if (isEdit.value) {
    return '修改空间信息，保存后立即生效'
  }
  return spaceType.value === SPACE_TYPE_ENUM.TEAM
    ? '创建团队空间，邀请成员共同管理图片素材，支持多人实时协同编辑'
    : '创建专属私有空间，仅自己可见，安全存放你的图片素材'
})

// 表单校验规则
const formRules = {
  spaceName: [
    { required: true, message: '请输入空间名称' },
    { max: 20, message: '空间名称不能超过 20 个字' },
  ],
}

// ----- 空间封面 -----
// 新选择的封面文件（本地预览，空间创建成功后再上传）
const coverFile = ref<File>()
// 新封面本地预览地址
const coverPreviewUrl = ref<string>()
// 编辑模式下服务端已有封面（仅展示，重新选择文件后才替换）
const serverCoverUrl = ref<string>()
// 展示用的封面预览：优先本地新选择的
const coverPreview = computed(() => coverPreviewUrl.value ?? serverCoverUrl.value)

/**
 * 选择封面文件：仅本地校验与预览，阻止 a-upload 自动上传
 */
const beforeCoverUpload = (file: File) => {
  const isSupported = ['image/jpeg', 'image/png', 'image/webp'].includes(file.type)
  if (!isSupported) {
    message.error('不支持该格式的封面，推荐 jpg / png / webp')
    return false
  }
  if (file.size / 1024 / 1024 >= 10) {
    message.error('封面图片不能超过 10M')
    return false
  }
  coverFile.value = file
  if (coverPreviewUrl.value) {
    URL.revokeObjectURL(coverPreviewUrl.value)
  }
  coverPreviewUrl.value = URL.createObjectURL(file)
  return false
}

// 撤销新选择的封面（回到原封面或空状态）
const removeCover = () => {
  if (coverPreviewUrl.value) {
    URL.revokeObjectURL(coverPreviewUrl.value)
  }
  coverFile.value = undefined
  coverPreviewUrl.value = undefined
}

onUnmounted(() => {
  if (coverPreviewUrl.value) {
    URL.revokeObjectURL(coverPreviewUrl.value)
  }
})

const spaceLevelList = ref<API.SpaceLevel[]>([])

// 获取空间级别
const fetchSpaceLevelList = async () => {
  const res = await listSpaceLevelUsingGet()
  if (res.data.code === 0 && res.data.data) {
    spaceLevelList.value = res.data.data
  } else {
    message.error('获取空间级别失败，' + res.data.message)
  }
}

// 级别是否被锁定：普通版全员可开通；专业版仅会员；旗舰版暂不开放自助开通
const isLevelLocked = (level: API.SpaceLevel) => {
  // 管理员不受限制
  if (isAdmin.value) return false
  // 普通版：所有人可自助开通
  if (level.value === SPACE_LEVEL_ENUM.COMMON) return false
  // 编辑时已选中的级别不锁，避免会员到期后连名称都改不了
  if (isEdit.value && spaceForm.spaceLevel === level.value) return false
  // 专业版：仅会员可开通
  if (level.value === SPACE_LEVEL_ENUM.PROFESSIONAL) return !isVip.value
  // 旗舰版：暂不开放
  return true
}

// 锁定原因文案
const levelLockText = (level: API.SpaceLevel) => {
  if (level.value === SPACE_LEVEL_ENUM.PROFESSIONAL) return '会员专享'
  return '暂未开放'
}

// 选择空间级别
const chooseLevel = (level: API.SpaceLevel) => {
  if (isLevelLocked(level)) {
    if (level.value === SPACE_LEVEL_ENUM.PROFESSIONAL) {
      message.info('专业版空间为会员专享，开通会员后即可创建')
    } else {
      message.info('旗舰版暂未开放自助开通，如需开通请联系管理员')
    }
    return
  }
  spaceForm.spaceLevel = level.value
}

onMounted(() => {
  fetchSpaceLevelList()
  // 创建模式默认选中普通版
  if (!isEdit.value) {
    spaceForm.spaceLevel = SPACE_LEVEL_ENUM.COMMON
  }
})

/**
 * 提交表单：创建/更新空间；若有新选择的封面，在空间就绪后补传
 */
const handleSubmit = async (values: any) => {
  loading.value = true
  try {
    // 编辑模式直接用已有空间 id；创建模式在创建成功后拿到新 id
    let targetSpaceId: number | string | undefined = space.value?.id
    // 编辑模式但没拿到空间数据时，禁止退回「创建」，否则会误建一个新空间
    if (isEdit.value && !targetSpaceId) {
      message.error('未获取到空间信息，无法保存，请返回列表重试')
      return
    }
    let res
    if (targetSpaceId) {
      // 更新
      res = await updateSpaceUsingPost({
        id: targetSpaceId,
        ...spaceForm,
      })
    } else {
      // 创建
      res = await addSpaceUsingPost({
        ...spaceForm,
        spaceType: spaceType.value,
      })
    }
    if (res.data.code === 0 && res.data.data) {
      if (!targetSpaceId) {
        targetSpaceId = res.data.data
      }
      // 上传封面（失败不阻断主流程）
      if (coverFile.value && targetSpaceId) {
        try {
          const coverRes = await uploadSpaceCoverUsingPost(
            { spaceId: targetSpaceId as number },
            coverFile.value,
          )
          if (coverRes.data.code !== 0) {
            message.warning('封面上传失败，' + coverRes.data.message)
          }
        } catch (e: any) {
          message.warning('封面上传失败，' + e.message)
        }
      }
      message.success(isEdit.value ? '保存成功' : '空间创建成功')
      // 跳转到空间详情页
      router.push(`/space/${targetSpaceId}`)
    } else {
      message.error('操作失败，' + res.data.message)
    }
  } catch (e: any) {
    message.error('操作失败，' + e.message)
  } finally {
    loading.value = false
  }
}

// 获取老数据
const getOldSpace = async () => {
  // 获取到 id（雪花 id 超出 JS 安全整数范围，必须按字符串原样传给后端，不能 Number 转换）
  const id = route.query?.id as string | undefined
  if (!id) return
  loading.value = true
  try {
    const res = await getSpaceVoByIdUsingGet({ id } as unknown as API.getSpaceVOByIdUsingGETParams)
    if (res.data.code === 0 && res.data.data) {
      const data = res.data.data
      space.value = data
      // 填充表单
      spaceForm.spaceName = data.spaceName
      spaceForm.spaceLevel = data.spaceLevel
      // 回填空间类型（团队空间在编辑时不应显示为私有空间）
      if (data.spaceType != null) {
        spaceType.value = data.spaceType
      }
      // 展示已有封面（仅编辑模式）
      serverCoverUrl.value = data.coverPicture
    } else {
      message.error('获取空间信息失败：' + res.data.message)
    }
  } catch (e: any) {
    message.error('获取空间信息失败：' + e.message)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  getOldSpace()
})
</script>

<style scoped>
#addSpacePage {
  max-width: 960px;
  margin: 0 auto;
  padding-top: 8px;
}

/* 页头 */
.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.header-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  background: linear-gradient(135deg, var(--accent), #69c0ff);
  color: #fff;
  font-size: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 20px rgba(64, 169, 255, 0.28);
  flex-shrink: 0;
}

.header-text h2 {
  margin-bottom: 4px;
}

.header-desc {
  color: var(--text-secondary);
  font-size: 13px;
  margin: 0;
}

/* 双栏布局 */
.page-body {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 16px;
  align-items: start;
}

@media (max-width: 860px) {
  .page-body {
    grid-template-columns: 1fr;
  }
}

/* 空间级别选择卡片 */
.level-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

@media (max-width: 560px) {
  .level-grid {
    grid-template-columns: 1fr;
  }
}

.level-card {
  position: relative;
  border: 1.5px solid var(--border-color);
  border-radius: 12px;
  padding: 16px 12px;
  text-align: center;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.level-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.15);
}

.level-card.active {
  border-color: var(--accent);
  background: rgba(64, 169, 255, 0.04);
  box-shadow: 0 6px 16px rgba(64, 169, 255, 0.15);
}

.level-card.locked {
  background: var(--bg-body);
}

.level-card.locked .level-icon,
.level-card.locked .level-name {
  opacity: 0.55;
}

.level-icon {
  font-size: 24px;
  color: var(--accent);
  margin-bottom: 8px;
}

.level-card.locked .level-icon {
  color: var(--text-disabled);
}

.level-name {
  font-weight: 600;
  color: var(--text-primary-light);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.rec-tag {
  margin-inline-end: 0;
  font-size: 11px;
  line-height: 18px;
}

.level-quota {
  color: var(--text-secondary);
  font-size: 12px;
  margin-top: 6px;
}

.level-state {
  margin-top: 8px;
  font-size: 12px;
  color: var(--accent);
}

.level-card.locked .level-state {
  color: var(--text-disabled);
}

.tip-alert {
  margin-bottom: 20px;
}

.submit-item {
  margin-bottom: 0;
}

/* ---------- 空间封面选择器 ---------- */
.cover-picker :deep(.ant-upload) {
  width: 100% !important;
  display: block !important;
}

.cover-slot {
  position: relative;
  width: 100%;
  height: 150px;
  border-radius: 14px;
  overflow: hidden;
  cursor: pointer;
  transition: box-shadow 0.25s ease;
}

/* 未选择：虚线引导区 */
.cover-slot:not(.filled) {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1.5px dashed rgba(64, 169, 255, 0.5);
  background:
    radial-gradient(ellipse 60% 90% at 15% 10%, rgba(64, 169, 255, 0.08), transparent),
    var(--bg-body);
}

.cover-slot:not(.filled):hover {
  border-color: var(--accent);
  box-shadow: 0 6px 18px rgba(64, 169, 255, 0.12);
}

.cover-empty {
  text-align: center;
}

.cover-icon {
  font-size: 30px;
  color: var(--accent);
}

.cover-text {
  margin-top: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary-light);
}

.cover-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-disabled);
}

/* 已选择：图片铺满 + 悬浮遮罩 */
.cover-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.cover-hover-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  background: rgba(15, 35, 62, 0.45);
  opacity: 0;
  transition: opacity 0.2s ease;
}

.cover-slot:hover .cover-hover-mask {
  opacity: 1;
}

.mask-icon {
  font-size: 16px;
}

.cover-remove-row {
  margin-top: 4px;
  text-align: right;
}

/* 右侧级别对比 */
.compare-card :deep(.ant-card-head) {
  border-bottom: none;
  padding-bottom: 0;
}

.compare-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 8px;
}

.compare-row {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 12px;
}

.compare-row.active {
  border-color: var(--accent);
  background: rgba(64, 169, 255, 0.04);
}

.compare-name {
  font-weight: 600;
  color: var(--text-primary-light);
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 88px;
}

.compare-icon {
  color: var(--accent);
}

.compare-quota {
  flex: 1;
  display: flex;
  flex-direction: column;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 20px;
}
</style>
