<template>
  <div class="picture-upload">
    <a-upload
      list-type="picture-card"
      :show-upload-list="false"
      :custom-request="handleUpload"
      :before-upload="beforeUpload"
    >
      <div v-if="picture?.url" class="preview-box">
        <img :src="picture?.url" alt="avatar" />
        <div class="preview-tip">点击图片可重新上传替换</div>
      </div>
      <div v-else class="empty-box">
        <loading-outlined v-if="loading" class="upload-icon" spin />
        <cloud-upload-outlined v-else class="upload-icon" />
        <div class="upload-text">点击或拖拽上传图片</div>
        <div class="format-tags">
          <span class="format-tag">JPG</span>
          <span class="format-tag">PNG</span>
          <span class="format-tag">WEBP</span>
        </div>
      </div>
    </a-upload>
  </div>
</template>
<script lang="ts" setup>
import { ref } from 'vue'
import { CloudUploadOutlined, LoadingOutlined } from '@ant-design/icons-vue'
import type { UploadProps } from 'ant-design-vue'
import { message } from 'ant-design-vue'
import { uploadPictureUsingPost } from '@/api/pictureController.ts'

interface Props {
  picture?: API.PictureVO
  spaceId?: number
  onSuccess?: (newPicture: API.PictureVO) => void
}

const props = defineProps<Props>()

/**
 * 上传图片
 * @param file
 */
const handleUpload = async ({ file }: any) => {
  loading.value = true
  try {
    const params: API.PictureUploadRequest = props.picture ? { id: props.picture.id } : {}
    params.spaceId = props.spaceId;
    const res = await uploadPictureUsingPost(params, {}, file, { timeout: 40000 })
    if (res.data.code === 0 && res.data.data) {
      message.success('图片上传成功')
      // 将上传成功的图片信息传递给父组件
      props.onSuccess?.(res.data.data)
    } else {
      message.error('图片上传失败，' + res.data.message)
    }
  } catch (error) {
    console.error('图片上传失败', error)
    message.error('图片上传失败，' + error.message)
  }
  loading.value = false
}

const loading = ref<boolean>(false)

/**
 * 上传前的校验
 * @param file
 */
const beforeUpload = (file: UploadProps['fileList'][number]) => {
  // 校验图片格式（与后端允许的后缀保持一致：jpeg / png / jpg / webp）
  const isSupported = ['image/jpeg', 'image/png', 'image/webp'].includes(file.type)
  if (!isSupported) {
    message.error('不支持上传该格式的图片，推荐 jpg / png / webp')
  }
  // 校验图片大小（上限 10MB，与后端 FilePictureUpload 保持一致）
  const isLt10M = file.size / 1024 / 1024 < 10
  if (!isLt10M) {
    message.error('不能上传超过 10M 的图片')
  }
  return isSupported && isLt10M
}
</script>
<style scoped>
.picture-upload :deep(.ant-upload) {
  width: 100% !important;
  height: auto !important;
  min-height: 240px;
  border-radius: 14px !important;
  transition: all 0.25s;
}

/* 未上传：虚线引导区 */
.picture-upload :deep(.ant-upload:not(:has(img))) {
  border: 1.5px dashed #b9c6ff !important;
  background: rgba(61, 90, 245, 0.03) !important;
}

.picture-upload :deep(.ant-upload:not(:has(img)):hover) {
  border-color: #4f6bff !important;
  background: rgba(61, 90, 245, 0.07) !important;
}

/* 已上传：白底实线展示 */
.picture-upload :deep(.ant-upload:has(img)) {
  border: 1px solid #e3e8f5 !important;
  background: #fafbff !important;
}

.empty-box {
  padding: 64px 0;
  text-align: center;
}

.upload-icon {
  font-size: 56px;
  color: #4f6bff;
}

.upload-text {
  margin-top: 16px;
  color: #232c56;
  font-size: 15px;
}

.format-tags {
  margin-top: 12px;
  display: flex;
  justify-content: center;
  gap: 8px;
}

.format-tag {
  padding: 2px 10px;
  border-radius: 6px;
  background: rgba(61, 90, 245, 0.08);
  color: #5a6a94;
  font-size: 12px;
  font-weight: 500;
}

.preview-box {
  width: 100%;
  text-align: center;
}

.preview-box img {
  max-width: 100%;
  max-height: 420px;
  border-radius: 10px;
}

.preview-tip {
  margin-top: 10px;
  color: #98a4c5;
  font-size: 12px;
}

/* 深色模式适配 */
html.dark .picture-upload :deep(.ant-upload:not(:has(img))) {
  border-color: rgba(79, 107, 255, 0.4) !important;
  background: rgba(79, 107, 255, 0.08) !important;
}

html.dark .picture-upload :deep(.ant-upload:has(img)) {
  border-color: rgba(255, 255, 255, 0.12) !important;
  background: rgba(255, 255, 255, 0.04) !important;
}

html.dark .upload-text {
  color: #e8eaf2;
}

html.dark .format-tag {
  background: rgba(79, 107, 255, 0.15);
  color: rgba(232, 234, 242, 0.6);
}

html.dark .preview-tip {
  color: rgba(232, 234, 242, 0.45);
}
</style>
