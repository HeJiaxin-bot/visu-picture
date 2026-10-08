<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  aiEditPictureUsingPost,
  editPictureUsingPost,
  getPictureVoByIdUsingGet,
  listPictureTagCategoryUsingGet,
  uploadPictureByUrlUsingPost,
} from '@/api/pictureController.ts'
import { showToast } from 'vant'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

defineOptions({ name: 'AddPictureUrlPage' })

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const spaceId = computed(() => route.query?.spaceId as string | undefined)

// URL 输入
const picUrl = ref('')
const fetching = ref(false)

// 图片状态
const picture = ref<API.PictureVO>({})

// 表单
const pictureForm = reactive<API.PictureEditRequest>({
  name: '',
  introduction: '',
  category: undefined,
  tags: [],
})

// ----- 分类 / 标签选项 -----
const categoryList = ref<string[]>([])
const tagList = ref<string[]>([])

const loadTagCategory = async () => {
  const res = await listPictureTagCategoryUsingGet()
  if (res.data.code === 0 && res.data.data) {
    categoryList.value = res.data.data.categoryList ?? []
    tagList.value = res.data.data.tagList ?? []
  }
}

onMounted(() => {
  loadTagCategory()
})

// ----- 抓取图片 -----
const doFetch = async () => {
  const url = picUrl.value.trim()
  if (!url) {
    showToast('请输入图片链接')
    return
  }
  if (fetching.value) return
  fetching.value = true
  try {
    const res = await uploadPictureByUrlUsingPost({
      fileUrl: url,
      spaceId: spaceId.value as any,
    })
    if (res.data.code === 0 && res.data.data) {
      picture.value = res.data.data
      if (!pictureForm.name) {
        pictureForm.name = res.data.data.name
      }
      showToast({ type: 'success', message: '抓取成功，请补充信息' })
    } else {
      showToast('抓取失败，' + res.data.message)
    }
  } catch (e) {
    showToast('抓取失败，请检查链接是否可访问')
  } finally {
    fetching.value = false
  }
}

// ----- 分类 / 标签选择弹层 -----
const showCategoryPicker = ref(false)
const showTagPicker = ref(false)

const doPickCategory = (c: string) => {
  pictureForm.category = pictureForm.category === c ? undefined : c
}

const toggleTag = (t: string) => {
  const tags = pictureForm.tags ?? []
  pictureForm.tags = tags.includes(t) ? tags.filter((v) => v !== t) : [...tags, t]
}

// ----- AI 配文 -----
const aiEditLoading = ref(false)

const doAiEdit = async () => {
  const pictureId = picture.value?.id
  if (!pictureId) {
    showToast('请先抓取图片')
    return
  }
  if (aiEditLoading.value) return
  aiEditLoading.value = true
  try {
    const res = await aiEditPictureUsingPost({ pictureId }, { timeout: 60000 })
    if (res.data.code === 0 && res.data.data) {
      const { name, introduction, category, tags } = res.data.data
      if (name) pictureForm.name = name
      if (introduction) pictureForm.introduction = introduction
      if (category) pictureForm.category = category
      if (tags?.length) pictureForm.tags = tags
      showToast({ type: 'success', message: 'AI 配文完成，请确认后提交' })
    } else {
      showToast('AI 配文失败，' + res.data.message)
    }
  } catch (e) {
    showToast('AI 配文失败，请稍后重试')
  } finally {
    aiEditLoading.value = false
  }
}

// ----- 提交 -----
const submitting = ref(false)

const doSubmit = async () => {
  const pictureId = picture.value?.id
  if (!pictureId) {
    showToast('请先抓取图片')
    return
  }
  if (!pictureForm.name) {
    showToast('请填写图片名称')
    return
  }
  if (aiEditLoading.value || submitting.value) return
  submitting.value = true
  try {
    const res = await editPictureUsingPost({
      id: pictureId,
      spaceId: spaceId.value,
      name: pictureForm.name,
      introduction: pictureForm.introduction,
      category: pictureForm.category,
      tags: pictureForm.tags,
    } as any)
    if (res.data.code === 0 && res.data.data) {
      const isAdmin = loginUserStore.loginUser.userRole === 'admin'
      showToast({
        type: 'success',
        message: isAdmin ? '创建成功' : '上传完成，待审核通过后展示',
      })
      if (spaceId.value) {
        router.replace(`/space/${spaceId.value}`)
      } else {
        router.replace('/')
      }
    } else {
      showToast('提交失败，' + res.data.message)
    }
  } catch (e) {
    showToast('提交失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.replace('/')
  }
}
</script>

<template>
  <div class="url-page">
    <van-nav-bar title="URL 导入" left-arrow @click-left="goBack" />

    <!-- URL 输入 -->
    <div class="url-card">
      <van-field
        v-model="picUrl"
        type="textarea"
        rows="2"
        autosize
        placeholder="粘贴图片链接，如 https://example.com/pic.jpg"
        clearable
      />
      <van-button
        type="primary"
        block
        round
        class="fetch-btn"
        :loading="fetching"
        :disabled="!!picture.id"
        loading-text="抓取中..."
        @click="doFetch"
      >
        抓取图片
      </van-button>
    </div>

    <!-- 抓取结果预览 -->
    <div v-if="picture.url || picture.thumbnailUrl" class="pic-preview">
      <img :src="picture.thumbnailUrl ?? picture.url" :alt="picture.name" />
    </div>

    <!-- AI 配文入口（抓取成功后显示） -->
    <div v-if="picture.id" class="ai-bar" @click="doAiEdit">
      <van-icon name="magic" class="ai-icon" :class="{ spinning: aiEditLoading }" />
      <div class="ai-text">
        <div class="ai-title">AI 智能配文</div>
        <div class="ai-desc">自动生成简介、分类与标签</div>
      </div>
      <van-loading v-if="aiEditLoading" size="18" />
      <van-icon v-else name="arrow" color="#94a3b8" />
    </div>

    <!-- 表单（抓取成功后显示） -->
    <van-form v-if="picture.id" class="meta-form" @submit="doSubmit">
      <van-cell-group inset>
        <van-field
          v-model="pictureForm.name"
          label="名称"
          placeholder="请输入图片名称"
          required
          maxlength="40"
          clearable
        />
        <van-field
          v-model="pictureForm.introduction"
          label="简介"
          type="textarea"
          rows="2"
          autosize
          maxlength="200"
          show-word-limit
          placeholder="介绍一下这张图片"
        />
        <van-field
          :model-value="pictureForm.category"
          label="分类"
          placeholder="选择分类（可选）"
          readonly
          is-link
          @click="showCategoryPicker = true"
        />
        <van-field
          :model-value="(pictureForm.tags ?? []).join('、')"
          label="标签"
          placeholder="选择标签（可多选）"
          readonly
          is-link
          @click="showTagPicker = true"
        />
      </van-cell-group>
      <div class="submit-area">
        <van-button
          round
          block
          type="primary"
          native-type="submit"
          :loading="submitting"
          :disabled="aiEditLoading || fetching"
          loading-text="提交中..."
        >
          提交发布
        </van-button>
      </div>
    </van-form>

    <!-- 分类选择弹层 -->
    <van-popup v-model:show="showCategoryPicker" position="bottom" round>
      <div class="picker-header">选择分类</div>
      <div class="picker-chips">
        <div
          v-for="c in categoryList"
          :key="c"
          class="filter-option"
          :class="{ active: pictureForm.category === c }"
          @click="doPickCategory(c)"
        >
          {{ c }}
        </div>
      </div>
      <div class="picker-footer">
        <van-button round block type="primary" @click="showCategoryPicker = false">确定</van-button>
      </div>
    </van-popup>

    <!-- 标签选择弹层 -->
    <van-popup v-model:show="showTagPicker" position="bottom" round>
      <div class="picker-header">选择标签（可多选）</div>
      <div class="picker-chips">
        <div
          v-for="t in tagList"
          :key="t"
          class="filter-option"
          :class="{ active: (pictureForm.tags ?? []).includes(t) }"
          @click="toggleTag(t)"
        >
          {{ t }}
        </div>
      </div>
      <div class="picker-footer">
        <van-button round block type="primary" @click="showTagPicker = false">确定</van-button>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.url-page {
  min-height: 100vh;
  background: var(--bg-page);
  padding-bottom: calc(32px + var(--safe-bottom));
}

.url-page :deep(.van-nav-bar) {
  padding-top: var(--safe-top);
}

.url-card {
  margin: 12px 16px 0;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  overflow: hidden;
}

.fetch-btn {
  margin: 12px 16px;
  width: calc(100% - 32px);
}

.pic-preview {
  margin: 12px 16px 0;
  border-radius: var(--radius-card);
  overflow: hidden;
  background: #000;
}

.pic-preview img {
  width: 100%;
  max-height: 320px;
  object-fit: contain;
}

.ai-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 12px 16px 0;
  padding: 14px 16px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  cursor: pointer;
}

.ai-icon {
  font-size: 24px;
  color: var(--brand);
}

.ai-icon.spinning {
  animation: ai-spin 1s linear infinite;
}

@keyframes ai-spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.ai-text {
  flex: 1;
  min-width: 0;
}

.ai-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-1);
}

.ai-desc {
  font-size: 12px;
  color: var(--text-2);
  margin-top: 2px;
}

.meta-form {
  margin-top: 12px;
}

.submit-area {
  margin-top: 20px;
  padding: 0 16px;
}

.submit-area :deep(.van-button) {
  height: 44px;
  font-size: 16px;
}

.picker-header {
  padding: 16px;
  text-align: center;
  font-size: 16px;
  font-weight: 600;
}

.picker-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 0 16px 16px;
  max-height: 40vh;
  overflow-y: auto;
}

.picker-footer {
  padding: 0 16px calc(16px + var(--safe-bottom));
}

.filter-option {
  padding: 6px 16px;
  border-radius: var(--radius-chip);
  font-size: 14px;
  color: var(--text-2);
  background: var(--bg-page);
  cursor: pointer;
}

.filter-option.active {
  color: #fff;
  background: var(--brand);
  font-weight: 600;
}
</style>
