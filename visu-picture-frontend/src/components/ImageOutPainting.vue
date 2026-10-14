<template>
  <a-modal
    class="image-out-painting"
    v-model:visible="visible"
    title="AI 扩图"
    :footer="false"
    :width="720"
    centered
    @cancel="closeModal"
  >
    <!-- 展陈区：原图 → 结果 -->
    <div class="exhibit">
      <div class="exhibit-frame">
        <div class="exhibit-label"><span class="label-dot" />原图</div>
        <div class="frame-media">
          <img :src="picture?.url" :alt="picture?.name" />
        </div>
        <div class="frame-caption">{{ picture?.name }}</div>
      </div>
      <div class="exhibit-arrow">
        <ArrowRightOutlined />
      </div>
      <div class="exhibit-frame">
        <div class="exhibit-label"><span class="label-dot accent" />扩图结果</div>
        <div class="frame-media">
          <img v-if="resultImageUrl" :src="resultImageUrl" alt="扩图结果" />
          <div v-else-if="taskId" class="frame-state">
            <a-spin size="large" />
            <span>展厅布展中，请稍候…</span>
          </div>
          <div v-else class="frame-state">
            <PictureOutlined class="state-icon" />
            <span>生成结果将在此呈现</span>
          </div>
        </div>
        <div class="frame-caption">{{ resultImageUrl ? '点击「应用结果」保存到空间' : '等待生成' }}</div>
      </div>
    </div>

    <!-- 控制区 -->
    <div class="controls">
      <div class="control-row">
        <span class="control-label">扩展比例</span>
        <a-slider
          v-model:value="expandScale"
          class="scale-slider"
          :min="1"
          :max="2"
          :step="0.1"
          :tooltip="{ open: false }"
        />
        <span class="scale-value">{{ expandScale.toFixed(1) }}×</span>
      </div>
      <div class="control-row">
        <span class="control-label">提示词</span>
        <a-textarea
          v-model:value="prompt"
          class="prompt-input"
          placeholder="选填，描述扩展区域的画面，留空使用默认"
          :auto-size="{ minRows: 1, maxRows: 3 }"
        />
      </div>
      <div class="preset-row">
        <span class="preset-hint">灵感</span>
        <span v-for="p in presets" :key="p" class="preset-pill" @click="prompt = p">{{ p }}</span>
      </div>
    </div>

    <!-- 进度 -->
    <div v-if="taskId" class="progress-wrap">
      <a-progress :percent="progress" status="active" />
    </div>

    <!-- 积分条 -->
    <div class="cost-strip">
      <span class="cost-item">剩余积分 <b class="mono">{{ loginUserStore.loginUser.points ?? 0 }}</b> 分</span>
      <span class="cost-divider" />
      <span class="cost-item">本次消耗 <b class="mono cost-num">20</b> 积分</span>
    </div>

    <!-- 操作区 -->
    <a-flex justify="center" gap="12" class="actions">
      <a-button
        v-if="resultImageUrl"
        type="primary"
        :loading="uploadLoading"
        @click="handleUpload"
      >
        应用结果
      </a-button>
      <a-button
        :type="resultImageUrl ? 'default' : 'primary'"
        :loading="!!taskId"
        @click="createTask"
      >
        {{ resultImageUrl ? '再生成一次' : '生成图片' }}
        <span class="btn-cost">-20 积分</span>
      </a-button>
    </a-flex>
  </a-modal>
</template>

<script lang="ts" setup>
import { ref } from 'vue'
import { ArrowRightOutlined, PictureOutlined } from '@ant-design/icons-vue'
import {
  createPictureOutPaintingTaskUsingPost,
  getPictureOutPaintingTaskUsingGet,
  uploadPictureByUrlUsingPost,
} from '@/api/pictureController.ts'
import { message, Modal } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

const loginUserStore = useLoginUserStore()

// 提示词灵感预设：点击填入
const presets = ['自然延伸画面', '向两侧展开辽阔视野', '延续光影与氛围', '扩展为宽阔构图']

interface Props {
  picture?: API.PictureVO
  /** 雪花 id 需保持字符串以免精度丢失（见 utils/firstQueryValue），故两种形态都接受 */
  spaceId?: number | string
  onSuccess?: (newPicture: API.PictureVO) => void
}

const props = defineProps<Props>()

const resultImageUrl = ref<string>('')

// 扩图统一使用万相编辑模型（wanx2.1-imageedit，function = expand）：
// 专用扩图模型 image-out-painting 已下架（实测 Model not exist），故移除模型选择。
// 参数：四方向统一扩展比例与提示词（提示词留空时后端使用默认值）
const expandScale = ref<number>(1.3)
const prompt = ref<string>('')

// 任务 id
const taskId = ref<string | undefined>()

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
    expandScale: expandScale.value,
    prompt: prompt.value || undefined,
  }
  const res = await createPictureOutPaintingTaskUsingPost(params)
  if (res.data.code === 0 && res.data.data) {
    message.success('创建任务成功，请耐心等待，不要退出界面')
    // output 在类型上可选（服务端异常时可能缺失），取值前先判空，避免运行时 undefined 崩溃
    const taskOutput = res.data.data.output
    if (!taskOutput?.taskId) {
      Modal.error({
        title: '创建扩图任务失败',
        content: '服务端未返回任务 id，请稍后重试',
        centered: true,
      })
      return
    }
    console.log(taskOutput.taskId)
    taskId.value = taskOutput.taskId
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
// 用 ReturnType<typeof setInterval> 而非 NodeJS.Timeout：
// 后者需要 @types/node 的全局命名空间，在浏览器端代码里引用会报 TS2503
let pollingTimer: ReturnType<typeof setInterval> | null = null

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
        // output 类型上可选：缺失时直接跳过本轮，等下一次轮询
        if (!taskResult) {
          return
        }
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
    } catch (error: any) {
      console.error('扩图任务轮询失败', error)
      message.error('扩图任务轮询失败，' + (error?.message ?? ''))
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
    taskId.value = undefined
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
  } catch (error: any) {
    console.error('图片上传失败', error)
    message.error('图片上传失败，' + (error?.message ?? ''))
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
  text-align: left;
}

/* ===== 展陈区：原图 → 结果 ===== */
.image-out-painting .exhibit {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 16px;
  align-items: stretch;
  margin-bottom: 20px;
}

.image-out-painting .exhibit-frame {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.image-out-painting .exhibit-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.image-out-painting .label-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--border-color);
}

.image-out-painting .label-dot.accent {
  background: var(--accent);
  box-shadow: 0 0 6px var(--glow-ring);
}

.image-out-painting .frame-media {
  position: relative;
  height: 200px;
  border-radius: var(--card-radius);
  background: rgba(127, 127, 127, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.image-out-painting .frame-media img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  display: block;
}

.image-out-painting .frame-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  color: var(--text-secondary);
  font-size: 13px;
}

.image-out-painting .frame-state .state-icon {
  font-size: 28px;
  opacity: 0.5;
}

.image-out-painting .frame-caption {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.image-out-painting .exhibit-arrow {
  align-self: center;
  color: var(--text-secondary);
  font-size: 18px;
  opacity: 0.6;
}

/* ===== 控制区 ===== */
.image-out-painting .controls {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}

.image-out-painting .control-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.image-out-painting .control-label {
  flex-shrink: 0;
  width: 60px;
  text-align: right;
  font-size: 13px;
  color: var(--text-secondary);
}

.image-out-painting .scale-slider {
  flex: 1;
  margin: 0 4px;
}

.image-out-painting .scale-value {
  flex-shrink: 0;
  min-width: 44px;
  text-align: center;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 13px;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: 999px;
  padding: 2px 8px;
}

.image-out-painting .prompt-input {
  flex: 1;
}

/* ===== 灵感预设药丸 ===== */
.image-out-painting .preset-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-left: 72px;
  flex-wrap: wrap;
}

.image-out-painting .preset-hint {
  font-size: 12px;
  color: var(--text-secondary);
  opacity: 0.7;
}

.image-out-painting .preset-pill {
  font-size: 12px;
  color: var(--text-secondary);
  border: 1px solid var(--border-color);
  border-radius: 999px;
  padding: 2px 10px;
  cursor: pointer;
  transition:
    color 0.2s ease,
    border-color 0.2s ease;
}

.image-out-painting .preset-pill:hover {
  color: var(--accent);
  border-color: var(--accent-soft);
}

/* ===== 进度 / 积分条 ===== */
.image-out-painting .progress-wrap {
  max-width: 420px;
  margin: 0 auto 12px;
}

.image-out-painting .cost-strip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-bottom: 16px;
  font-size: 13px;
  color: var(--text-secondary);
}

.image-out-painting .cost-strip .mono {
  font-family: 'JetBrains Mono', Consolas, monospace;
}

.image-out-painting .cost-strip b {
  color: var(--accent);
  font-weight: 600;
  margin: 0 2px;
}

.image-out-painting .cost-strip .cost-num {
  color: var(--accent);
}

.image-out-painting .cost-divider {
  width: 1px;
  height: 12px;
  background: var(--border-color);
}

/* ===== 操作区 ===== */
.image-out-painting .actions {
  margin-bottom: 4px;
}

.image-out-painting .btn-cost {
  margin-left: 6px;
  font-size: 12px;
  opacity: 0.75;
}

/* ===== 移动端：展陈纵向堆叠 ===== */
@media (max-width: 640px) {
  .image-out-painting .exhibit {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .image-out-painting .exhibit-arrow {
    transform: rotate(90deg);
    justify-self: center;
  }

  .image-out-painting .frame-media {
    height: 180px;
  }

  .image-out-painting .preset-row {
    padding-left: 0;
  }

  .image-out-painting .control-label {
    width: auto;
    text-align: left;
  }
}
</style>
