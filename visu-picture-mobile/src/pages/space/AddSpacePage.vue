<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { addSpaceUsingPost, listSpaceLevelUsingGet } from '@/api/spaceController.ts'
import { showToast } from 'vant'
import { SPACE_TYPE_MAP, SPACE_TYPE_OPTIONS } from '@/constants/space.ts'

defineOptions({ name: 'AddSpacePage' })

const router = useRouter()

const formData = reactive<API.SpaceAddRequest>({
  spaceName: '',
  spaceType: 0,
  spaceLevel: 0,
})

// 空间等级列表
const levelList = ref<API.SpaceLevel[]>([])

const loadLevels = async () => {
  try {
    const res = await listSpaceLevelUsingGet()
    if (res.data.code === 0 && res.data.data) {
      levelList.value = res.data.data ?? []
    }
  } catch (e) {
    // 加载失败时使用默认等级
  }
}

onMounted(() => {
  loadLevels()
})

const currentLevel = computed(() =>
  levelList.value.find((l) => l.value === formData.spaceLevel),
)

const submitting = ref(false)

const doSubmit = async () => {
  if (!formData.spaceName) {
    showToast('请输入空间名称')
    return
  }
  if (submitting.value) return
  submitting.value = true
  try {
    const res = await addSpaceUsingPost(formData)
    if (res.data.code === 0 && res.data.data) {
      showToast({ type: 'success', message: '空间创建成功' })
      router.replace(`/space/${res.data.data}`)
    } else {
      showToast('创建失败，' + res.data.message)
    }
  } catch (e) {
    showToast('创建失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.replace('/space')
  }
}
</script>

<template>
  <div class="add-space-page">
    <van-nav-bar title="创建空间" left-arrow @click-left="goBack" />

    <van-form @submit="doSubmit">
      <van-cell-group inset>
        <van-field
          v-model="formData.spaceName"
          label="空间名称"
          placeholder="请输入空间名称"
          required
          maxlength="20"
          clearable
        />
      </van-cell-group>

      <!-- 空间类型 -->
      <div class="section-title">空间类型</div>
      <div class="type-row">
        <div
          v-for="opt in SPACE_TYPE_OPTIONS"
          :key="opt.value"
          class="type-card"
          :class="{ active: formData.spaceType === opt.value }"
          @click="formData.spaceType = opt.value"
        >
          <van-icon
            :name="opt.value === 0 ? 'lock' : 'friends-o'"
            size="22"
          />
          <div class="type-name">{{ opt.label }}</div>
          <div class="type-desc">{{ opt.value === 0 ? '仅自己可见' : '成员共享协作' }}</div>
        </div>
      </div>

      <!-- 空间级别 -->
      <div class="section-title">空间级别</div>
      <van-cell-group inset>
        <van-cell
          v-for="level in levelList"
          :key="level.value"
          :title="level.text"
          clickable
          @click="formData.spaceLevel = level.value ?? 0"
        >
          <template #right-icon>
            <van-icon
              :name="formData.spaceLevel === level.value ? 'checked' : 'circle'"
              :color="formData.spaceLevel === level.value ? '#40a9ff' : '#c9cdd4'"
              size="20"
            />
          </template>
          <template #label>
            <span class="level-desc">
              {{ Number(level.maxSize ?? 0) / 1024 / 1024 }} MB · {{ level.maxCount ?? 0 }} 张
            </span>
          </template>
        </van-cell>
      </van-cell-group>

      <div class="submit-area">
        <van-button round block type="primary" native-type="submit" :loading="submitting">
          创建空间
        </van-button>
      </div>
    </van-form>
  </div>
</template>

<style scoped>
.add-space-page {
  min-height: 100vh;
  background: var(--bg-page);
  padding-bottom: calc(32px + var(--safe-bottom));
}

.add-space-page :deep(.van-nav-bar) {
  padding-top: var(--safe-top);
}

.section-title {
  margin: 16px 20px 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
}

.type-row {
  display: flex;
  gap: 12px;
  padding: 0 16px;
}

.type-card {
  flex: 1;
  padding: 16px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  text-align: center;
  cursor: pointer;
  border: 2px solid transparent;
  transition: all 0.2s ease;
}

.type-card.active {
  border-color: var(--brand);
  background: var(--brand-weak);
}

.type-name {
  margin-top: 6px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-1);
}

.type-desc {
  margin-top: 4px;
  font-size: 11px;
  color: var(--text-2);
}

.level-desc {
  font-size: 11px;
  color: var(--text-2);
}

.submit-area {
  margin-top: 24px;
  padding: 0 16px;
}

.submit-area :deep(.van-button) {
  height: 44px;
  font-size: 16px;
}
</style>
