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
  /** 延迟上传：选中文件后只在本地预览，父组件调用 upload() 时才真正上传（避免未提交就产生 COS 文件） */
  deferUpload?: boolean
}

const props = defineProps<Props>()

const loading = ref<boolean>(false)
// 延迟上传模式下待上传的本地文件与其本地预览地址
const localFile = ref<File>()
const localPreviewUrl = ref<string>()

/**
 * 选中文件：延迟上传模式只做本地预览，否则立即上传
 * @param file
 */
const handleUpload = async ({ file }: any) => {
  if (props.deferUpload) {
    // 释放上一次的本地预览地址
    if (localPreviewUrl.value) {
      URL.revokeObjectURL(localPreviewUrl.value)
    }
    localFile.value = file as File
    localPreviewUrl.value = URL.createObjectURL(file)
    // 用本地预览构造占位图片，让父组件展示预览与表单（此时尚未上传到对象存储）
    props.onSuccess?.({
      id: props.picture?.id,
      url: localPreviewUrl.value,
      name: (file.name ?? '').replace(/\.[^.]+$/, ''),
    } as API.PictureVO)
    return
  }
  localFile.value = file as File
  await upload()
}

/**
 * 真正上传图片（延迟上传模式下由父组件在提交或执行 AI 操作前调用）
 * @returns 上传成功后的图片信息
 */
const upload = async (): Promise<API.PictureVO | undefined> => {
  const file = localFile.value
  if (!file) {
    return undefined
  }
  loading.value = true
  try {
    const params: API.PictureUploadRequest = props.picture?.id ? { id: props.picture.id } : {}
    params.spaceId = props.spaceId;
    const res = await uploadPictureUsingPost(params, {}, file, { timeout: 40000 })
    if (res.data.code === 0 && res.data.data) {
      message.success('图片上传成功')
      // 清理本地预览状态
      if (localPreviewUrl.value) {
        URL.revokeObjectURL(localPreviewUrl.value)
        localPreviewUrl.value = undefined
      }
      localFile.value = undefined
      // 将上传成功的图片信息传递给父组件
      props.onSuccess?.(res.data.data)
      return res.data.data
    }
    message.error('图片上传失败，' + res.data.message)
  } catch (error: any) {
    console.error('图片上传失败', error)
    message.error('图片上传失败，' + error.message)
  } finally {
    loading.value = false
  }
  return undefined
}

/** 是否存在尚未上传的本地文件 */
const hasPendingFile = () => !!localFile.value

defineExpose({ upload, hasPendingFile })

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
  border: 1.5px dashed rgba(64, 169, 255, 0.45) !important;
  background: rgba(64, 169, 255, 0.03) !important;
}

.picture-upload :deep(.ant-upload:not(:has(img)):hover) {
  border-color: var(--accent) !important;
  background: rgba(64, 169, 255, 0.07) !important;
}

/* 已上传：白底实线展示 */
.picture-upload :deep(.ant-upload:has(img)) {
  border: 1px solid var(--border-color) !important;
  background: var(--bg-body) !important;
}

.empty-box {
  padding: 64px 0;
  text-align: center;
}

.upload-icon {
  font-size: 56px;
  color: var(--accent);
}

.upload-text {
  margin-top: 16px;
  color: var(--text-primary-light);
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
  background: rgba(64, 169, 255, 0.08);
  color: var(--text-secondary);
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
  color: var(--text-secondary);
  font-size: 12px;
}

/* 深色模式适配 */
html.dark .picture-upload :deep(.ant-upload:not(:has(img))) {
  border-color: rgba(64, 169, 255, 0.4) !important;
  background: rgba(64, 169, 255, 0.08) !important;
}

html.dark .picture-upload :deep(.ant-upload:has(img)) {
  border-color: rgba(255, 255, 255, 0.12) !important;
  background: rgba(255, 255, 255, 0.04) !important;
}

html.dark .format-tag {
  background: rgba(64, 169, 255, 0.15);
  color: rgba(240, 240, 240, 0.6);
}

html.dark .preview-tip {
  color: rgba(240, 240, 240, 0.45);
}
</style>
