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
                    <LockOutlined /> 需联系管理员
                  </template>
                  <template v-else>可开通</template>
                </div>
              </div>
            </div>
          </a-form-item>

          <a-alert
            v-if="!isEdit"
            class="tip-alert"
            type="info"
            show-icon
            message="目前仅支持自助开通普通版，如需升级专业版 / 旗舰版，请联系管理员。"
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
            <a-tag v-else>需升级</a-tag>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  CloudOutlined,
  CrownOutlined,
  LockOutlined,
  RocketOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import {
  addSpaceUsingPost,
  getSpaceVoByIdUsingGet,
  listSpaceLevelUsingGet,
  updateSpaceUsingPost,
} from '@/api/spaceController.ts'
import { useRoute, useRouter } from 'vue-router'
import { SPACE_LEVEL_ENUM, SPACE_TYPE_ENUM, SPACE_TYPE_MAP } from '@/constants/space.ts'
import { formatSize } from '../utils'

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
// 空间类别，默认为私有空间
const spaceType = computed(() => {
  if (route.query?.type) {
    return Number(route.query.type)
  } else {
    return SPACE_TYPE_ENUM.PRIVATE
  }
})

// 是否为编辑模式
const isEdit = computed(() => !!route.query?.id)

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

// 级别是否被锁定（创建模式下仅普通版可自助开通；编辑模式不锁定）
const isLevelLocked = (level: API.SpaceLevel) => {
  return !isEdit.value && level.value !== SPACE_LEVEL_ENUM.COMMON
}

// 选择空间级别
const chooseLevel = (level: API.SpaceLevel) => {
  if (isLevelLocked(level)) {
    message.info('该版本暂不支持自助开通，如需升级请联系管理员')
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
 * 提交表单
 * @param values
 */
const handleSubmit = async (values: any) => {
  const spaceId = space.value?.id
  loading.value = true
  let res
  if (spaceId) {
    // 更新
    res = await updateSpaceUsingPost({
      id: spaceId,
      ...spaceForm,
    })
  } else {
    // 创建
    res = await addSpaceUsingPost({
      ...spaceForm,
      spaceType: spaceType.value,
    })
  }
  // 操作成功
  if (res.data.code === 0 && res.data.data) {
    message.success(isEdit.value ? '保存成功' : '空间创建成功')
    // 跳转到空间详情页
    router.push({
      path: `/space/${res.data.data}`,
    })
  } else {
    message.error('操作失败，' + res.data.message)
  }
  loading.value = false
}

// 获取老数据
const getOldSpace = async () => {
  // 获取到 id
  const id = route.query?.id
  if (id) {
    const res = await getSpaceVoByIdUsingGet({
      id: Number(id),
    })
    if (res.data.code === 0 && res.data.data) {
      const data = res.data.data
      space.value = data
      // 填充表单
      spaceForm.spaceName = data.spaceName
      spaceForm.spaceLevel = data.spaceLevel
    }
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
  background: linear-gradient(135deg, #3d5af5, #8b5cf6);
  color: #fff;
  font-size: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 20px rgba(61, 90, 245, 0.28);
  flex-shrink: 0;
}

.header-text h2 {
  margin-bottom: 4px;
}

.header-desc {
  color: rgba(35, 44, 86, 0.55);
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
  border: 1.5px solid #e4e8f2;
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
  box-shadow: 0 8px 20px rgba(37, 55, 120, 0.1);
}

.level-card.active {
  border-color: #3d5af5;
  background: rgba(61, 90, 245, 0.04);
  box-shadow: 0 6px 16px rgba(61, 90, 245, 0.15);
}

.level-card.locked {
  background: #f7f8fc;
}

.level-card.locked .level-icon,
.level-card.locked .level-name {
  opacity: 0.55;
}

.level-icon {
  font-size: 24px;
  color: #3d5af5;
  margin-bottom: 8px;
}

.level-card.locked .level-icon {
  color: rgba(35, 44, 86, 0.4);
}

.level-name {
  font-weight: 600;
  color: #26283a;
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
  color: rgba(35, 44, 86, 0.55);
  font-size: 12px;
  margin-top: 6px;
}

.level-state {
  margin-top: 8px;
  font-size: 12px;
  color: #3d5af5;
}

.level-card.locked .level-state {
  color: rgba(35, 44, 86, 0.45);
}

.tip-alert {
  margin-bottom: 20px;
}

.submit-item {
  margin-bottom: 0;
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
  border: 1px solid #e4e8f2;
  border-radius: 10px;
  padding: 12px;
}

.compare-row.active {
  border-color: #3d5af5;
  background: rgba(61, 90, 245, 0.04);
}

.compare-name {
  font-weight: 600;
  color: #26283a;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 88px;
}

.compare-icon {
  color: #3d5af5;
}

.compare-quota {
  flex: 1;
  display: flex;
  flex-direction: column;
  color: rgba(35, 44, 86, 0.65);
  font-size: 12px;
  line-height: 20px;
}
</style>
