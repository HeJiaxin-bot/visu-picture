<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import {
  aiEditPictureUsingPost,
  deletePictureUsingPost,
  editPictureUsingPost,
  getPictureVoByIdUsingGet,
  listPictureTagCategoryUsingGet,
  uploadPictureUsingPost,
} from '@/api/pictureController.ts'
import { showToast } from 'vant'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

defineOptions({ name: 'AddPicturePage' })

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

// 是否编辑模式（带图片 id 进入）
const isEditMode = computed(() => !!route.query?.id)

// 图片状态
const picture = ref<API.PictureVO>({})
const uploading = ref(false)
// 本地上传后、尚未提交的图片 id（退出时清理）
const uploadedPictureId = ref<string | number>()

// 空间 id
const spaceId = computed(() => route.query?.spaceId as string | undefined)

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

// ----- 选图上传 -----
const fileInputRef = ref<HTMLInputElement>()

const doPickFile = () => {
  fileInputRef.value?.click()
}

const onFileChange = async (e: Event) => {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  // 允许重复选择同一文件
  input.value = ''
  if (!file) return
  await doUploadFile(file)
}

const doUploadFile = async (file: File) => {
  uploading.value = true
  try {
    const res = await uploadPictureUsingPost(
      { picName: file.name, spaceId: spaceId.value as any },
      {},
      file,
    )
    if (res.data.code === 0 && res.data.data) {
      // 记录本次上传的图片，用于离开时清理孤儿数据
      uploadedPictureId.value = res.data.data.id
      picture.value = res.data.data
      if (!pictureForm.name) {
        pictureForm.name = res.data.data.name
      }
      showToast({ type: 'success', message: '上传成功' })
    } else {
      showToast('上传失败，' + res.data.message)
    }
  } catch (e) {
    showToast('上传失败，请稍后重试')
  } finally {
    uploading.value = false
  }
}

// ----- 编辑模式回填 -----
const loadOldPicture = async () => {
  const id = route.query?.id
  if (!id) return
  const res = await getPictureVoByIdUsingGet({ id } as any)
  if (res.data.code === 0 && res.data.data) {
    picture.value = res.data.data
    pictureForm.name = res.data.data.name
    pictureForm.introduction = res.data.data.introduction
    pictureForm.category = res.data.data.category
    pictureForm.tags = res.data.data.tags ?? []
  } else {
    showToast('获取图片信息失败，' + res.data.message)
  }
}

onMounted(() => {
  loadOldPicture()
})

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
    showToast('请先上传图片')
    return
  }
  // 防连点
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
    showToast('请先选择图片')
    return
  }
  if (!pictureForm.name) {
    showToast('请填写图片名称')
    return
  }
  if (aiEditLoading.value) {
    return
  }
  if (submitting.value) return
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
      // 提交成功后无需清理
      uploadedPictureId.value = undefined
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

// ----- 离开清理未提交的图片 -----
const cleanupUploadedPicture = () => {
  const id = uploadedPictureId.value
  if (!id) return
  uploadedPictureId.value = undefined
  deletePictureUsingPost({ id: id as number }).catch(() => {})
}

onBeforeRouteLeave(() => {
  cleanupUploadedPicture()
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
  <div class="add-page">
    <van-nav-bar
      :title="isEditMode ? '编辑图片' : '上传图片'"
      left-arrow
      @click-left="goBack"
    />

    <!-- 图片区 -->
    <div class="pic-stage">
      <!-- 已有图片：预览 -->
      <div v-if="picture.url || picture.thumbnailUrl" class="pic-preview">
        <img :src="picture.thumbnailUrl ?? picture.url" :alt="picture.name" />
        <!-- 非编辑模式允许换图 -->
        <div v-if="!isEditMode" class="pic-change" @click="doPickFile">重新选择</div>
      </div>
      <!-- 空态：选择图片 -->
      <div v-else class="pic-empty" @click="doPickFile">
        <van-icon name="photograph" size="40" color="#c9cdd4" />
        <div class="pic-empty-text">点击选择图片</div>
        <div class="pic-empty-sub">支持 JPG / PNG / WebP，拍摄或从相册选择</div>
      </div>
      <!-- 上传中遮罩 -->
      <div v-if="uploading" class="upload-mask">
        <van-loading size="28" vertical>上传中...</van-loading>
      </div>
      <input
        ref="fileInputRef"
        type="file"
        accept="image/*"
        style="display: none"
        @change="onFileChange"
      />
    </div>

    <!-- AI 配文入口 -->
    <div class="ai-bar" @click="doAiEdit">
      <van-icon name="magic" class="ai-icon" :class="{ spinning: aiEditLoading }" />
      <div class="ai-text">
        <div class="ai-title">AI 智能配文</div>
        <div class="ai-desc">自动生成简介、分类与标签</div>
      </div>
      <van-loading v-if="aiEditLoading" size="18" />
      <van-icon v-else name="arrow" color="#c9cdd4" />
    </div>

    <!-- 表单 -->
    <van-form class="meta-form" @submit="doSubmit">
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
          :disabled="aiEditLoading || uploading"
          loading-text="提交中..."
        >
          {{ isEditMode ? '保存' : '提交发布' }}
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
.add-page {
  min-height: 100vh;
  background: var(--bg-page);
  padding-bottom: calc(32px + var(--safe-bottom));
}

.add-page :deep(.van-nav-bar) {
  padding-top: var(--safe-top);
}

/* 图片区 */
.pic-stage {
  position: relative;
  margin: 12px 16px 0;
  border-radius: var(--radius-card);
  overflow: hidden;
}

.pic-preview {
  position: relative;
  background: #000;
}

.pic-preview img {
  width: 100%;
  max-height: 320px;
  object-fit: contain;
}

.pic-change {
  position: absolute;
  right: 12px;
  bottom: 12px;
  padding: 6px 14px;
  border-radius: var(--radius-chip);
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  font-size: 12px;
}

.pic-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 56px 24px;
  background: var(--bg-card);
  cursor: pointer;
}

.pic-empty-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-1);
}

.pic-empty-sub {
  font-size: 12px;
  color: var(--text-2);
}

.upload-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.8);
  z-index: 2;
}

/* AI 配文条 */
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

/* 表单 */
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

/* 弹层 */
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
