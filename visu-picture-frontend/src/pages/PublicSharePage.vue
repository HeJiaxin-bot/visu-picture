<template>
  <div id="publicSharePage">
    <a-spin :spinning="loading">
      <!-- 加载失败 / 链接失效 -->
      <a-result
        v-if="error"
        status="warning"
        title="无法查看这张图片"
        :sub-title="error"
      >
        <template #extra>
          <a-button type="primary" @click="$router.push('/')">返回首页</a-button>
        </template>
      </a-result>

      <template v-else-if="picture.id">
        <!-- 顶栏：来源提示 + 操作 -->
        <div class="share-head">
          <div class="share-brand">
            <ShareAltOutlined />
            <span>来自「视界」的分享</span>
          </div>
          <div class="share-head-actions">
            <a-button size="small" @click="download">
              <DownloadOutlined /> 下载
            </a-button>
            <a-button size="small" @click="copyLink">
              <CopyOutlined /> 复制链接
            </a-button>
            <a-button type="primary" size="small" @click="$router.push('/')">
              探索更多
            </a-button>
          </div>
        </div>

        <a-row :gutter="[24, 24]">
          <!-- 图片预览 -->
          <a-col :sm="24" :md="16" :xl="18">
            <div class="preview-card">
              <div class="preview-body">
                <a-image :src="picture.url" class="preview-image" />
              </div>
            </div>
          </a-col>

          <!-- 信息栏（只展示公开信息） -->
          <a-col :sm="24" :md="8" :xl="6">
            <div class="info-card">
              <div class="info-author">
                <a-avatar :size="40" :src="picture.user?.userAvatar">
                  <template #icon><UserOutlined /></template>
                </a-avatar>
                <div class="author-meta">
                  <div class="author-name">{{ picture.user?.userName ?? '视界用户' }}</div>
                  <div class="author-hint">分享者</div>
                </div>
              </div>

              <div class="info-name">{{ picture.name ?? '未命名' }}</div>
              <p class="info-intro">{{ picture.introduction || '暂无简介' }}</p>

              <div class="info-tags-row">
                <a-tag color="blue">{{ picture.category || '默认' }}</a-tag>
                <a-tag v-for="tag in picture.tags" :key="tag">{{ tag }}</a-tag>
              </div>

              <a-divider class="info-divider" />

              <div class="param-grid">
                <div class="param-item">
                  <div class="param-label">格式</div>
                  <div class="param-value">{{ picture.picFormat ?? '-' }}</div>
                </div>
                <div class="param-item">
                  <div class="param-label">尺寸</div>
                  <div class="param-value">
                    {{ picture.picWidth ?? '-' }} × {{ picture.picHeight ?? '-' }}
                  </div>
                </div>
                <div class="param-item">
                  <div class="param-label">大小</div>
                  <div class="param-value">{{ formatSize(picture.picSize) }}</div>
                </div>
                <div class="param-item">
                  <div class="param-label">点赞</div>
                  <div class="param-value">{{ picture.likeCount ?? 0 }}</div>
                </div>
              </div>

              <a-button type="primary" size="large" block class="download-btn" @click="download">
                <DownloadOutlined /> 下载原图
              </a-button>
              <p class="share-note">
                你正在通过分享链接浏览，登录后可点赞、收藏与评论
              </p>
            </div>
          </a-col>
        </a-row>
      </template>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { CopyOutlined, DownloadOutlined, ShareAltOutlined, UserOutlined } from '@ant-design/icons-vue'
import { getSharedPicture } from '@/api/interaction.ts'
import { downloadImage, formatSize } from '@/utils'

interface Props {
  shareCode: string
}

const props = defineProps<Props>()

const loading = ref(true)
const error = ref('')
const picture = ref<API.PictureVO>({})

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const res = await getSharedPicture(props.shareCode)
    if (res.code === 0 && res.data) {
      picture.value = res.data
      // 分享页标题跟随图片名，便于转发时辨识
      document.title = `${res.data.name ?? '图片分享'} - 视界`
    } else {
      error.value = res.message ?? '分享链接不存在或已失效'
    }
  } catch (e: any) {
    error.value = e?.message ?? '网络异常，请稍后再试'
  } finally {
    loading.value = false
  }
}

const download = () => {
  downloadImage(picture.value.url)
}

const copyLink = async () => {
  try {
    await navigator.clipboard.writeText(window.location.href)
    message.success('链接已复制')
  } catch {
    message.error('复制失败，请手动复制地址栏链接')
  }
}

onMounted(load)
</script>

<style scoped>
#publicSharePage {
  max-width: 1440px;
  margin: 0 auto 16px;
  padding: 0 12px;
}

.share-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
  padding: 10px 16px;
  border-radius: 12px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
}

.share-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--accent);
}

.share-head-actions {
  display: flex;
  gap: 8px;
}

.preview-card {
  background: var(--bg-card);
  border-radius: 16px;
  border: 1px solid var(--border-color);
  box-shadow: var(--card-shadow);
  overflow: hidden;
}

.preview-body {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 280px;
  padding: 32px;
  background:
    radial-gradient(ellipse at 30% 20%, rgba(64, 169, 255, 0.06), transparent 60%),
    radial-gradient(ellipse at 70% 80%, rgba(22, 119, 255, 0.05), transparent 60%),
    var(--bg-body);
}

.preview-image {
  min-width: 0;
  max-width: 100%;
  line-height: 0;
}

.preview-image :deep(img) {
  display: block;
  width: auto;
  height: auto;
  max-width: 100%;
  max-height: min(62vh, 560px);
  object-fit: contain;
  border-radius: 12px;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.18);
}

.info-card {
  background: var(--bg-card);
  border-radius: 16px;
  border: 1px solid var(--border-color);
  box-shadow: var(--card-shadow);
  padding: 20px;
}

.info-author {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  margin: -6px -8px 14px;
  border-radius: 12px;
}

.author-meta {
  flex: 1;
  min-width: 0;
}

.author-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary-light);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.author-hint {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-disabled);
}

.info-name {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary-light);
  line-height: 26px;
}

.info-intro {
  margin: 8px 0 14px;
  font-size: 13px;
  line-height: 20px;
  color: var(--text-secondary);
}

.info-tags-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.info-tags-row :deep(.ant-tag) {
  margin-inline-end: 0;
}

.info-divider {
  margin: 16px 0;
}

.param-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 12px;
}

.param-label {
  font-size: 12px;
  color: var(--text-disabled);
  margin-bottom: 3px;
}

.param-value {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-primary-light);
}

.download-btn {
  margin-top: 20px;
  border: none;
  background: linear-gradient(135deg, #40a9ff, #1677ff);
}

.share-note {
  margin: 10px 0 0;
  text-align: center;
  font-size: 12px;
  color: var(--text-disabled);
}

html.dark .info-name,
html.dark .author-name,
html.dark .param-value {
  color: var(--text-primary);
}
</style>