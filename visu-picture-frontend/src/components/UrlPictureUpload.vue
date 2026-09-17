<template>
  <div class="url-picture-upload">
    <div class="url-row">
      <a-input
        v-model:value="fileUrl"
        class="url-input"
        placeholder="请粘贴或输入图片 URL 地址..."
        allow-clear
        @pressEnter="handleUpload"
      />
      <a-button type="primary" class="url-btn" :loading="loading" @click="handleUpload">
        抓取
      </a-button>
    </div>
    <div class="url-hint">支持 http/https 协议的公开图片地址</div>
  </div>
</template>
<script lang="ts" setup>
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { uploadPictureByUrlUsingPost } from '@/api/pictureController.ts'

interface Props {
  picture?: API.PictureVO
  spaceId?: number
  onSuccess?: (newPicture: API.PictureVO) => void
  /** 延迟上传：点击抓取只做本地预览，父组件调用 upload() 时才真正抓取上传 */
  deferUpload?: boolean
}

const props = defineProps<Props>()
const fileUrl = ref<string>()
const loading = ref<boolean>(false)
// 延迟上传模式下待抓取的图片地址
const pendingUrl = ref<string>()

/**
 * 点击抓取：延迟上传模式只做本地预览，否则立即抓取上传
 */
const handleUpload = async () => {
  const url = fileUrl.value?.trim()
  if (!url) {
    message.warning('请输入图片地址')
    return
  }
  if (!/^https?:\/\//i.test(url)) {
    message.error('请输入 http/https 协议的图片地址')
    return
  }
  if (props.deferUpload) {
    pendingUrl.value = url
    // 用远程地址构造占位图片，让父组件展示预览与表单（此时尚未抓取上传）
    props.onSuccess?.({
      id: props.picture?.id,
      url,
      name: url.split('/').pop()?.replace(/\.[^.]+$/, '') ?? '',
    } as API.PictureVO)
    return
  }
  pendingUrl.value = url
  await upload()
}

/**
 * 真正抓取并上传图片（延迟上传模式下由父组件在提交或执行 AI 操作前调用）
 * @returns 上传成功后的图片信息
 */
const upload = async (): Promise<API.PictureVO | undefined> => {
  if (!pendingUrl.value) {
    return undefined
  }
  loading.value = true
  try {
    const params: API.PictureUploadRequest = { fileUrl: pendingUrl.value }
    params.spaceId = props.spaceId;
    if (props.picture?.id) {
      params.id = props.picture.id
    }
    const res = await uploadPictureByUrlUsingPost(params)
    if (res.data.code === 0 && res.data.data) {
      message.success('图片上传成功')
      pendingUrl.value = undefined
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

/** 是否存在尚未抓取上传的图片地址 */
const hasPendingUrl = () => !!pendingUrl.value

defineExpose({ upload, hasPendingUrl })
</script>
<style scoped>
.url-picture-upload .url-row {
  display: flex;
  gap: 10px;
}

.url-picture-upload .url-input {
  flex: 1;
  min-width: 0;
  height: 42px;
  border-radius: 10px;
}

.url-picture-upload .url-btn {
  flex-shrink: 0;
  width: 96px;
  height: 42px;
  border-radius: 10px;
}

.url-picture-upload .url-hint {
  margin-top: 8px;
  color: #98a4c5;
  font-size: 12px;
  text-align: center;
}
</style>
