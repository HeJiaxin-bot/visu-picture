<template>
  <div id="addPictureBatchPage">
    <div class="page-card">
      <!-- 页头 -->
      <div class="page-header">
        <div class="header-icon">
          <CloudDownloadOutlined />
        </div>
        <div class="header-text">
          <h2>批量抓取图片</h2>
          <p>输入关键词，自动抓取网络图片并上传至图库，抓取的图片均会进入审核流程</p>
        </div>
      </div>
      <!-- 图片信息表单 -->
      <a-form name="formData" layout="vertical" :model="formData" @finish="handleSubmit">
        <a-form-item name="searchText" label="搜索关键词">
          <a-input
            v-model:value="formData.searchText"
            size="large"
            placeholder="例如：风景、城市夜景、二次元壁纸"
            allow-clear
          >
            <template #prefix><SearchOutlined class="input-icon" /></template>
          </a-input>
          <div class="preset-tags">
            <a-tag
              v-for="word in presetKeywords"
              :key="word"
              class="preset-tag"
              :checked="formData.searchText === word"
              @click="formData.searchText = word"
            >
              {{ word }}
            </a-tag>
          </div>
        </a-form-item>
        <a-form-item name="count" label="抓取数量">
          <a-radio-group v-model:value="formData.count" class="count-group" button-style="solid">
            <a-radio-button v-for="n in [5, 10, 20, 30]" :key="n" :value="n">{{ n }} 张</a-radio-button>
          </a-radio-group>
        </a-form-item>
        <a-form-item name="namePrefix" label="名称前缀">
          <a-input
            v-model:value="formData.namePrefix"
            size="large"
            placeholder="不填则默认使用关键词，自动补充序号（开启 AI 配文后由 AI 自动命名）"
            allow-clear
          >
            <template #prefix><TagOutlined class="input-icon" /></template>
          </a-input>
        </a-form-item>
        <a-form-item name="aiEdit">
          <div class="ai-edit-row" @click="formData.aiEdit = !formData.aiEdit">
            <div class="ai-edit-info">
              <div class="ai-edit-label">
                <ThunderboltOutlined class="ai-edit-icon" />
                AI 智能配文
              </div>
              <div class="ai-edit-desc">为每张图片自动生成名称、简介、分类和标签，耗时略有增加</div>
            </div>
            <a-switch v-model:checked="formData.aiEdit" @click.stop />
          </div>
        </a-form-item>
        <a-form-item>
          <a-button
            type="primary"
            html-type="submit"
            size="large"
            class="submit-btn"
            :loading="loading"
            block
          >
            {{ loading ? '任务执行中…' : '开始抓取' }}
          </a-button>
        </a-form-item>
        <!-- 任务进度 -->
        <a-form-item v-if="loading && progress">
          <div class="progress-wrapper">
            <div class="progress-text">
              <LoadingOutlined spin />
              <span>正在抓取并上传图片：已完成 {{ progress.done }} / {{ progress.total }} 张</span>
              <b>{{ Math.round((progress.done / progress.total) * 100) }}%</b>
            </div>
            <a-progress
              :percent="Math.round((progress.done / progress.total) * 100)"
              :status="progress.done >= progress.total ? 'success' : 'active'"
              :show-info="false"
            />
          </div>
        </a-form-item>
      </a-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onUnmounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  CloudDownloadOutlined,
  LoadingOutlined,
  SearchOutlined,
  TagOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons-vue'
import {
  getBatchUploadProgressUsingGet,
  uploadPictureByBatchUsingPost,
} from '@/api/pictureController.ts'
import { useRouter } from 'vue-router'

// 快捷关键词，点击直接填入
const presetKeywords = ['风景', '城市夜景', '美食', '动物', '科技', '动漫壁纸']

const formData = reactive<API.PictureUploadByBatchRequest>({
  count: 10,
  aiEdit: true,
})
// 提交任务状态
const loading = ref(false)
// 任务进度
const progress = ref<{ done: number; total: number } | null>(null)
// 轮询定时器
let progressTimer: number | null = null

const router = useRouter()

/**
 * 开始轮询任务进度
 */
const startProgressPolling = () => {
  stopProgressPolling()
  progressTimer = window.setInterval(async () => {
    try {
      const res = await getBatchUploadProgressUsingGet()
      if (res.data.code === 0 && res.data.data) {
        const [done, total] = res.data.data.split('/').map(Number)
        if (!Number.isNaN(done) && !Number.isNaN(total) && total > 0) {
          progress.value = { done, total }
        }
      }
    } catch (error) {
      // 轮询失败暂时忽略，下一次重试
    }
  }, 800)
}

/**
 * 停止轮询任务进度
 */
const stopProgressPolling = () => {
  if (progressTimer) {
    clearInterval(progressTimer)
    progressTimer = null
  }
}

onUnmounted(() => {
  stopProgressPolling()
})

/**
 * 提交表单
 * @param values
 */
const handleSubmit = async (values: any) => {
  loading.value = true
  progress.value = null
  // 开始轮询进度
  startProgressPolling()
  let res
  try {
    // 开启 AI 配文时每张图片需额外调用一次大模型，延长超时时间
    res = await uploadPictureByBatchUsingPost({ ...formData }, { timeout: 600000 })
  } finally {
    stopProgressPolling()
    loading.value = false
  }
  // 操作成功
  if (res.data.code === 0 && res.data.data) {
    message.success(`创建成功，共 ${res.data.data} 条`)
    // 跳转到主页
    router.push({
      path: `/`,
    })
  } else {
    message.error('创建失败，' + res.data.message)
  }
}
</script>

<style scoped>
#addPictureBatchPage {
  max-width: 720px;
  margin: 0 auto;
  padding: 24px 0;
}

.page-card {
  background: #fff;
  border-radius: 16px;
  padding: 32px;
  box-shadow: 0 6px 24px rgba(31, 45, 92, 0.06);
}

.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 28px;
}

.header-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  color: #fff;
  background: linear-gradient(135deg, #3d5af1 0%, #6a82f7 100%);
  flex-shrink: 0;
}

.header-text h2 {
  margin: 0 0 4px;
  font-size: 20px;
  color: #232c56;
}

.header-text p {
  margin: 0;
  font-size: 13px;
  color: rgba(35, 44, 86, 0.55);
}

.input-icon {
  color: rgba(35, 44, 86, 0.35);
}

.preset-tags {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.preset-tag {
  cursor: pointer;
  padding: 2px 12px;
  border-radius: 999px;
  border: 1px solid #dfe5f5;
  background: #f6f8ff;
  color: #4f6bff;
  transition: all 0.2s;
}

.preset-tag:hover {
  border-color: #4f6bff;
  transform: translateY(-1px);
}

.count-group {
  display: flex;
  gap: 0;
}

.ai-edit-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
  padding: 14px 18px;
  border: 1px solid #e7ebf6;
  border-radius: 12px;
  background: rgba(61, 90, 245, 0.04);
  cursor: pointer;
  transition: all 0.2s;
}

.ai-edit-row:hover {
  border-color: rgba(61, 90, 245, 0.45);
}

.ai-edit-label {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #232c56;
  font-size: 14px;
  font-weight: 500;
}

.ai-edit-icon {
  color: #4f6bff;
}

.ai-edit-desc {
  margin-top: 2px;
  color: rgba(35, 44, 86, 0.55);
  font-size: 12px;
}

.submit-btn {
  height: 44px;
  border-radius: 10px;
  font-size: 15px;
}

.progress-wrapper {
  padding: 14px 18px;
  border: 1px solid #e7ebf6;
  border-radius: 12px;
  background: rgba(61, 90, 245, 0.04);
}

.progress-text {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  color: rgba(35, 44, 86, 0.7);
  font-size: 14px;
}

.progress-text b {
  margin-left: auto;
  color: #4f6bff;
}

/* 深色模式适配 */
html.dark .page-card {
  background: rgba(255, 255, 255, 0.06);
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.3);
}

html.dark .header-text h2 {
  color: #e8eaf2;
}

html.dark .header-text p {
  color: rgba(232, 234, 242, 0.55);
}

html.dark .input-icon {
  color: rgba(232, 234, 242, 0.35);
}

html.dark .preset-tag {
  background: rgba(79, 107, 255, 0.14);
  border-color: rgba(79, 107, 255, 0.35);
  color: #8fa4ff;
}

html.dark .progress-wrapper {
  border-color: rgba(255, 255, 255, 0.12);
  background: rgba(79, 107, 255, 0.1);
}

html.dark .progress-text {
  color: rgba(232, 234, 242, 0.7);
}

html.dark .ai-edit-row {
  border-color: rgba(255, 255, 255, 0.12);
  background: rgba(79, 107, 255, 0.1);
}

html.dark .ai-edit-label {
  color: #e8eaf2;
}

html.dark .ai-edit-desc {
  color: rgba(232, 234, 242, 0.55);
}
</style>
