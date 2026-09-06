<template>
  <a-modal
    class="image-out-painting"
    v-model:visible="visible"
    title="AI 扩图"
    :footer="false"
    @cancel="closeModal"
  >
    <a-row gutter="16">
      <a-col span="12">
        <h4>原始图片</h4>
        <img :src="picture?.url" :alt="picture?.name" style="max-width: 100%" />
      </a-col>
      <a-col span="12">
        <h4>扩图结果</h4>
        <img
          v-if="resultImageUrl"
          :src="resultImageUrl"
          :alt="picture?.name"
          style="max-width: 100%"
        />
      </a-col>
    </a-row>
    <div style="margin-bottom: 16px" />
    <a-form layout="inline" style="margin-bottom: 16px">
      <a-form-item label="扩图模型">
        <a-select v-model:value="model" style="width: 240px">
          <a-select-option value="image-out-painting">专用扩图</a-select-option>
          <a-select-option value="wanx2.1-imageedit">万相编辑</a-select-option>
        </a-select>
      </a-form-item>
      <template v-if="model === 'image-out-painting'">
        <a-form-item label="横向比例">
          <a-input-number v-model:value="xScale" :min="1" :max="3" :step="0.1" />
        </a-form-item>
        <a-form-item label="纵向比例">
          <a-input-number v-model:value="yScale" :min="1" :max="3" :step="0.1" />
        </a-form-item>
      </template>
      <template v-else>
        <a-form-item label="扩展比例">
          <a-input-number v-model:value="expandScale" :min="1" :max="2" :step="0.1" />
        </a-form-item>
        <a-form-item required label="提示词">
          <a-input v-model:value="prompt" placeholder="请输入提示词，引导扩图内容" style="width: 200px" />
        </a-form-item>
      </template>
    </a-form>
    <div v-if="taskId" style="max-width: 420px; margin: 0 auto 12px">
      <a-progress :percent="progress" status="active" />
    </div>
    <!-- 积分提示：剩余积分与本次消耗 -->
    <div class="points-tip">
      剩余积分：<b>{{ loginUserStore.loginUser.points ?? 0 }}</b> 分，本次扩图消耗 20 分
    </div>
    <a-flex justify="center" gap="16">
      <a-button
        type="primary"
        :loading="!!taskId"
        ghost
        :disabled="model === 'wanx2.1-imageedit' && !prompt.trim()"
        @click="createTask"
      >
        生成图片（-20 积分）
      </a-button>
      <a-button v-if="resultImageUrl" type="primary" :loading="uploadLoading" @click="handleUpload">
        应用结果
      </a-button>
    </a-flex>
  </a-modal>
</template>

<script lang="ts" setup>
import { ref } from 'vue'
import {
  createPictureOutPaintingTaskUsingPost,
  getPictureOutPaintingTaskUsingGet,
  uploadPictureByUrlUsingPost,
} from '@/api/pictureController.ts'
import { message, Modal } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

const loginUserStore = useLoginUserStore()

interface Props {
  picture?: API.PictureVO
  spaceId?: number
  onSuccess?: (newPicture: API.PictureVO) => void
}

const props = defineProps<Props>()

const resultImageUrl = ref<string>('')

// 扩图模型：image-out-painting（专用扩图）/ wanx2.1-imageedit（万相通用图像编辑）
const model = ref<string>('image-out-painting')
// 专用扩图模型参数：横纵向扩展比例
const xScale = ref<number>(1.3)
const yScale = ref<number>(1.3)
// 万相编辑模型参数：四方向统一扩展比例与提示词
const expandScale = ref<number>(1.3)
const prompt = ref<string>('')

// 任务 id
const taskId = ref<string>()

// 模拟进度：阿里云无百分比进度，按预估耗时用减速曲线估算，封顶 99%，任务完成跳 100%
const progress = ref<number>(0)
const ESTIMATED_MS = 12000
let progressTimer: number | null = null

const startProgress = () => {
  stopProgress()
  progress.value = 0
  const startTime = Date.now()
  progressTimer = window.setInterval(() => {
    const ratio = (Date.now() - startTime) / ESTIMATED_MS
    // 前快后慢：12 秒约 92%，24 秒后卡在 99% 等任务结束
    progress.value = Math.min(99, Math.round(100 * (1 - Math.exp(-2.5 * ratio))))
  }, 200)
}

const stopProgress = () => {
  if (progressTimer) {
    clearInterval(progressTimer)
    progressTimer = null
  }
}

/**
 * 创建任务
 */
const createTask = async () => {
  if (!props.picture?.id) {
    return
  }
  const params: API.CreatePictureOutPaintingTaskRequest = {
    pictureId: props.picture.id,
    model: model.value,
  }
  if (model.value === 'image-out-painting') {
    params.parameters = {
      xScale: xScale.value,
      yScale: yScale.value,
    }
  } else {
    params.expandScale = expandScale.value
    params.prompt = prompt.value || undefined
  }
  const res = await createPictureOutPaintingTaskUsingPost(params)
  if (res.data.code === 0 && res.data.data) {
    message.success('创建任务成功，请耐心等待，不要退出界面')
    console.log(res.data.data.output.taskId)
    taskId.value = res.data.data.output.taskId
    // 扣了积分，刷新登录用户信息以更新剩余积分
    loginUserStore.fetchLoginUser()
    // 启动模拟进度
    startProgress()
    // 开启轮询
    startPolling()
  } else {
    Modal.error({
      title: '创建扩图任务失败',
      content: res.data.message ?? '未知原因，请稍后重试',
      centered: true,
    })
  }
}

// 轮询定时器
let pollingTimer: NodeJS.Timeout = null

// 开始轮询
const startPolling = () => {
  if (!taskId.value) {
    return
  }

  pollingTimer = setInterval(async () => {
    try {
      const res = await getPictureOutPaintingTaskUsingGet({
        taskId: taskId.value,
      })
      if (res.data.code === 0 && res.data.data) {
        const taskResult = res.data.data.output
        if (taskResult.taskStatus === 'SUCCEEDED') {
          message.success('扩图任务执行成功')
          // 专用扩图模型返回 outputImageUrl，万相编辑模型返回 results[0].url
          resultImageUrl.value =
            taskResult.outputImageUrl ?? taskResult.results?.[0]?.url ?? ''
          progress.value = 100
          // 清理轮询
          clearPolling()
        } else if (taskResult.taskStatus === 'FAILED') {
          // 清理轮询
          clearPolling()
          // 展示具体失败原因
          const reason = taskResult.message
            ? `${taskResult.message}（错误码：${taskResult.code ?? '未知'}）`
            : '未知原因，请稍后重试'
          Modal.error({
            title: '扩图任务执行失败',
            content: `失败原因：${reason}`,
            centered: true,
          })
        }
      }
    } catch (error) {
      console.error('扩图任务轮询失败', error)
      message.error('扩图任务轮询失败，' + error.message)
      // 清理轮询
      clearPolling()
    }
  }, 3000) // 每 3 秒轮询一次
}

// 清理轮询
const clearPolling = () => {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
    taskId.value = null
  }
  stopProgress()
}

// 是否正在上传
const uploadLoading = ref(false)

/**
 * 上传图片
 * @param file
 */
const handleUpload = async () => {
  uploadLoading.value = true
  try {
    const params: API.PictureUploadRequest = {
      fileUrl: resultImageUrl.value,
      spaceId: props.spaceId,
      // AI 扩图生成的内容，按规范打上 AI 生成标识
      isAiGenerated: 1,
    }
    if (props.picture) {
      params.id = props.picture.id
    }
    const res = await uploadPictureByUrlUsingPost(params)
    if (res.data.code === 0 && res.data.data) {
      message.success('图片上传成功')
      // 将上传成功的图片信息传递给父组件
      props.onSuccess?.(res.data.data)
      // 关闭弹窗
      closeModal()
    } else {
      message.error('图片上传失败，' + res.data.message)
    }
  } catch (error) {
    console.error('图片上传失败', error)
    message.error('图片上传失败，' + error.message)
  }
  uploadLoading.value = false
}

// 是否可见
const visible = ref(false)

// 打开弹窗
const openModal = () => {
  visible.value = true
}

// 关闭弹窗
const closeModal = () => {
  visible.value = false
}

// 暴露函数给父组件
defineExpose({
  openModal,
})
</script>

<style>
.image-out-painting {
  text-align: center;
}

/* 剩余积分提示 */
.points-tip {
  margin: 0 auto 16px;
  font-size: 13px;
  color: rgba(35, 44, 86, 0.55);
}

.points-tip b {
  color: #3d5af5;
  font-size: 15px;
  margin: 0 2px;
}

/* 深色模式 */
html.dark .points-tip {
  color: rgba(200, 208, 240, 0.6);
}

html.dark .points-tip b {
  color: #7c96ff;
}
</style>
