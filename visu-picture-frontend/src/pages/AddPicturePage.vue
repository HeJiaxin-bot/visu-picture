<template>
  <div id="addPicturePage">
    <!-- 页头 -->
    <div class="page-header">
      <h2 class="page-title">{{ route.query?.id ? '修改图片' : '创建图片' }}</h2>
      <p v-if="spaceId" class="page-sub">
        保存至空间：<router-link :to="`/space/${spaceId}`">
          {{ space?.spaceName || spaceId }}
        </router-link>
      </p>
    </div>

    <div class="layout">
      <!-- 左栏：上传 / 预览 / 编辑 -->
      <div class="card upload-card">
        <a-tabs v-model:activeKey="uploadType" class="upload-tabs">
          <a-tab-pane key="file" tab="文件上传">
            <PictureUpload :picture="picture" :spaceId="spaceId" :onSuccess="onSuccess" />
          </a-tab-pane>
          <a-tab-pane key="url" tab="URL 上传" force-render>
            <UrlPictureUpload :picture="picture" :spaceId="spaceId" :onSuccess="onSuccess" />
          </a-tab-pane>
        </a-tabs>
        <!-- 图片编辑 -->
        <div v-if="picture" class="edit-bar">
          <a-button :icon="h(EditOutlined)" @click="doEditPicture">编辑图片</a-button>
          <a-button type="primary" :icon="h(FullscreenOutlined)" @click="doImagePainting">
            AI 扩图
          </a-button>
        </div>
        <ImageCropper
          ref="imageCropperRef"
          :imageUrl="picture?.url"
          :picture="picture"
          :spaceId="spaceId"
          :space="space"
          :onSuccess="onCropSuccess"
        />
        <ImageOutPainting
          ref="imageOutPaintingRef"
          :picture="picture"
          :spaceId="spaceId"
          :onSuccess="onImageOutPaintingSuccess"
        />
      </div>

      <!-- 右栏：图片信息 -->
      <div class="card info-card">
        <div class="info-title">图片信息</div>
        <a-form
          v-if="picture"
          name="pictureForm"
          layout="vertical"
          :model="pictureForm"
          @finish="handleSubmit"
        >
          <a-form-item name="name" label="名称">
            <a-input v-model:value="pictureForm.name" placeholder="请输入名称" allow-clear />
          </a-form-item>
          <a-form-item name="introduction" label="简介">
            <a-textarea
              v-model:value="pictureForm.introduction"
              placeholder="请输入简介"
              :auto-size="{ minRows: 2, maxRows: 5 }"
              allow-clear
            />
          </a-form-item>
          <a-form-item name="category" label="分类">
            <a-auto-complete
              v-model:value="pictureForm.category"
              placeholder="请输入分类"
              :options="categoryOptions"
              allow-clear
            />
          </a-form-item>
          <a-form-item name="tags" label="标签">
            <a-select
              v-model:value="pictureForm.tags"
              mode="tags"
              placeholder="请输入标签"
              :options="tagOptions"
              allow-clear
            />
          </a-form-item>
          <a-form-item>
            <a-button type="primary" html-type="submit" class="submit-btn">
              {{ route.query?.id ? '保存修改' : '创建' }}
            </a-button>
          </a-form-item>
        </a-form>
        <a-empty
          v-else
          :image="Empty.PRESENTED_IMAGE_SIMPLE"
          description="上传图片后填写信息"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import PictureUpload from '@/components/PictureUpload.vue'
import { computed, h, onMounted, reactive, ref, watchEffect } from 'vue'
import { message, Modal, Empty } from 'ant-design-vue'
import {
  editPictureUsingPost,
  getPictureVoByIdUsingGet,
  listPictureTagCategoryUsingGet,
} from '@/api/pictureController.ts'
import { useRoute, useRouter } from 'vue-router'
import UrlPictureUpload from '@/components/UrlPictureUpload.vue'
import ImageCropper from '@/components/ImageCropper.vue'
import { EditOutlined, FullscreenOutlined } from '@ant-design/icons-vue'
import ImageOutPainting from '@/components/ImageOutPainting.vue'
import { getSpaceVoByIdUsingGet } from '@/api/spaceController.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

const router = useRouter()
const route = useRoute()

const picture = ref<API.PictureVO>()
const pictureForm = reactive<API.PictureEditRequest>({})
const uploadType = ref<'file' | 'url'>('file')
// 空间 id
const spaceId = computed(() => {
  return route.query?.spaceId
})

/**
 * 图片上传成功
 * @param newPicture
 */
const onSuccess = (newPicture: API.PictureVO) => {
  picture.value = newPicture
  pictureForm.name = newPicture.name
}

/**
 * 提交表单
 * @param values
 */
const handleSubmit = async (values: any) => {
  console.log(values)
  const pictureId = picture.value.id
  if (!pictureId) {
    return
  }
  const res = await editPictureUsingPost({
    id: pictureId,
    spaceId: spaceId.value,
    ...values,
  })
  // 操作成功
  if (res.data.code === 0 && res.data.data) {
    if (spaceId.value) {
      // 上传到空间（私人/团队空间）：直接返回对应空间详情页
      message.success('创建成功')
      router.push(`/space/${spaceId.value}`)
      return
    }
    const isAdmin = useLoginUserStore().loginUser.userRole === 'admin'
    if (isAdmin) {
      // 管理员上传直接过审
      message.success('创建成功')
    } else {
      // 普通用户上传进入待审核状态
      Modal.success({
        title: '上传完成',
        content: '待管理员审核通过后在首页展示',
        centered: true,
        okText: '知道了',
      })
    }
    // 跳转到公共图库首页
    router.push('/')
  } else {
    message.error('创建失败，' + res.data.message)
  }
}

const categoryOptions = ref<string[]>([])
const tagOptions = ref<string[]>([])

/**
 * 获取标签和分类选项
 * @param values
 */
const getTagCategoryOptions = async () => {
  const res = await listPictureTagCategoryUsingGet()
  if (res.data.code === 0 && res.data.data) {
    tagOptions.value = (res.data.data.tagList ?? []).map((data: string) => {
      return {
        value: data,
        label: data,
      }
    })
    categoryOptions.value = (res.data.data.categoryList ?? []).map((data: string) => {
      return {
        value: data,
        label: data,
      }
    })
  } else {
    message.error('获取标签分类列表失败，' + res.data.message)
  }
}

onMounted(() => {
  getTagCategoryOptions()
})

// 获取老数据
const getOldPicture = async () => {
  // 获取到 id
  const id = route.query?.id
  if (id) {
    const res = await getPictureVoByIdUsingGet({
      id,
    })
    if (res.data.code === 0 && res.data.data) {
      const data = res.data.data
      picture.value = data
      pictureForm.name = data.name
      pictureForm.introduction = data.introduction
      pictureForm.category = data.category
      pictureForm.tags = data.tags
    }
  }
}

onMounted(() => {
  getOldPicture()
})

// ----- 图片编辑器引用 ------
const imageCropperRef = ref()

// 编辑图片
const doEditPicture = async () => {
  imageCropperRef.value?.openModal()
}

// 编辑成功事件
const onCropSuccess = (newPicture: API.PictureVO) => {
  picture.value = newPicture
}

// ----- AI 扩图引用 -----
const imageOutPaintingRef = ref()

// 打开 AI 扩图弹窗
const doImagePainting = async () => {
  imageOutPaintingRef.value?.openModal()
}

// AI 扩图保存事件
const onImageOutPaintingSuccess = (newPicture: API.PictureVO) => {
  picture.value = newPicture
}

// 获取空间信息
const space = ref<API.SpaceVO>()

// 获取空间信息
const fetchSpace = async () => {
  // 获取数据
  if (spaceId.value) {
    const res = await getSpaceVoByIdUsingGet({
      id: spaceId.value,
    })
    if (res.data.code === 0 && res.data.data) {
      space.value = res.data.data
    }
  }
}

watchEffect(() => {
  fetchSpace()
})
</script>

<style scoped>
#addPicturePage {
  max-width: 1150px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 20px;
}

.page-title {
  margin: 0 0 6px;
  font-size: 24px;
  font-weight: 700;
  color: #232c56;
}

.page-sub {
  margin: 0;
  color: #8593ad;
  font-size: 14px;
}

.layout {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.card {
  background: #fff;
  border-radius: 16px;
  border: 1px solid rgba(61, 90, 245, 0.08);
  box-shadow: 0 4px 24px rgba(61, 90, 245, 0.06);
}

.upload-card {
  flex: 1;
  min-width: 0;
  padding: 8px 20px 20px;
}

.info-card {
  width: 380px;
  flex-shrink: 0;
  padding: 20px;
}

.info-title {
  font-weight: 600;
  font-size: 16px;
  color: #232c56;
  margin-bottom: 16px;
}

.edit-bar {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 12px;
  padding-top: 16px;
  border-top: 1px dashed #e3e8f5;
}

.submit-btn {
  width: 100%;
  height: 44px;
  border-radius: 10px;
  font-size: 15px;
}

/* 深色模式适配 */
html.dark .page-title {
  color: #e8eaf2;
}

html.dark .page-sub {
  color: rgba(232, 234, 242, 0.55);
}

html.dark .card {
  background: rgba(255, 255, 255, 0.06);
  border-color: rgba(255, 255, 255, 0.1);
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.3);
}

html.dark .info-title {
  color: #e8eaf2;
}

html.dark .edit-bar {
  border-top-color: rgba(255, 255, 255, 0.12);
}

/* 小屏单栏 */
@media (max-width: 960px) {
  .layout {
    flex-direction: column;
  }

  .info-card {
    width: 100%;
  }
}
</style>
