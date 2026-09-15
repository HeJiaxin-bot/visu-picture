<template>
  <div class="url-picture-upload">
    <div class="url-row">
      <a-input
        v-model:value="fileUrl"
        class="url-input"
        placeholder="请输入图片地址"
        allow-clear
        @pressEnter="handleUpload"
      />
      <a-button type="primary" class="url-btn" :loading="loading" @click="handleUpload">
        提交
      </a-button>
    </div>
    <div class="img-wrapper">
      <img v-if="picture?.url" :src="picture?.url" alt="avatar" />
      <div v-else class="placeholder">输入图片地址后点击提交，抓取结果将在此预览</div>
    </div>
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
  width: 110px;
  height: 42px;
  border-radius: 10px;
}

.url-picture-upload .img-wrapper {
  margin-top: 16px;
  min-height: 220px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1.5px dashed #b9c6ff;
  border-radius: 14px;
  background: rgba(61, 90, 245, 0.03);
}

.url-picture-upload .img-wrapper img {
  max-width: 100%;
  max-height: 420px;
  border-radius: 10px;
}

.url-picture-upload .placeholder {
  color: #98a4c5;
  font-size: 13px;
}

/* 深色模式适配 */
html.dark .url-picture-upload .img-wrapper {
  border-color: rgba(79, 107, 255, 0.4);
  background: rgba(79, 107, 255, 0.08);
}

html.dark .url-picture-upload .placeholder {
  color: rgba(232, 234, 242, 0.45);
}
</style>
