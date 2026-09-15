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
}

const props = defineProps<Props>()
const fileUrl = ref<string>()
const loading = ref<boolean>(false)

/**
 * 上传图片
 * @param file
 */
const handleUpload = async () => {
  loading.value = true
  try {
    const params: API.PictureUploadRequest = { fileUrl: fileUrl.value }
    params.spaceId = props.spaceId;
    if (props.picture) {
      params.id = props.picture.id
    }
    const res = await uploadPictureByUrlUsingPost(params)
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
