<template>
  <div>
    <a-modal
      v-model:open="visible"
      :title="title"
      :footer="null"
      :width="440"
      centered
      @cancel="closeModal"
    >
      <a-spin :spinning="loading">
        <!-- 有效期（仅所有者/管理员可调整） -->
        <div v-if="pictureId != null && canManage" class="expire-row">
          <span class="expire-label">有效期</span>
          <div class="expire-pills">
            <button
              v-for="opt in expireOptions"
              :key="opt.value"
              class="expire-pill"
              :class="{ active: expireDays === opt.value }"
              @click="expireDays = opt.value"
            >
              {{ opt.label }}
            </button>
          </div>
        </div>

        <template v-if="displayLink">
          <!-- 链接框：等宽字体 + 一键复制 -->
          <div class="link-box">
            <span class="link-text">{{ displayLink }}</span>
            <button class="copy-btn" title="复制链接" @click="copyLink">
              <CopyOutlined />
            </button>
          </div>

          <div v-if="shareInfo" class="share-meta">
            <a-tag v-if="shareInfo.expireTime" color="orange">
              有效期至 {{ formatDate(shareInfo.expireTime) }}
            </a-tag>
            <a-tag v-else color="green">永久有效</a-tag>
            <a-tag color="blue">已被访问 {{ shareInfo.viewCount ?? 0 }} 次</a-tag>
          </div>

          <!-- 扫码：展厅式居中呈现 -->
          <div class="qr-block">
            <a-qrcode :value="displayLink" :size="148" />
            <span class="qr-caption">手机扫码查看</span>
          </div>

          <!-- 管理操作：仅所有者/管理员 -->
          <div v-if="pictureId != null && canManage" class="share-actions">
            <a-button size="small" :loading="creating" @click="createLink(true)">
              更新链接
            </a-button>
            <a-popconfirm
              title="撤销后此链接立即失效，确认撤销？"
              ok-text="撤销"
              cancel-text="取消"
              @confirm="revokeLink"
            >
              <a-button size="small" danger :loading="revoking">撤销分享</a-button>
            </a-popconfirm>
          </div>
        </template>

        <!-- 空状态：给出明确的主行动 -->
        <div v-else-if="!loading" class="empty-block">
          <template v-if="pictureId != null">
            <ShareAltOutlined class="empty-icon" />
            <p class="empty-text">生成公开链接，任何人凭链接或扫码即可查看这张图</p>
            <a-button type="primary" :loading="creating" @click="createLink(true)">
              生成分享链接
            </a-button>
          </template>
          <a-empty v-else description="暂无分享链接" />
        </div>
      </a-spin>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { message } from 'ant-design-vue'
import { CopyOutlined, ShareAltOutlined } from '@ant-design/icons-vue'
import { createShare, getShareByPictureId, revokeShare } from '@/api/interaction.ts'
import type { PictureShareVO } from '@/api/interaction.ts'
import { firstQueryValue } from '@/utils'

interface Props {
  title?: string
  /** 图片 id：提供时启用「创建/撤销分享链接」（需登录；公开图库已过审图片所有人可分享） */
  pictureId?: number | string
  /** 纯链接展示模式（不创建/撤销），如站内分享 */
  link?: string
}

const props = withDefaults(defineProps<Props>(), {
  title: '分享图片',
  link: '',
})

const visible = ref(false)
const loading = ref(false)
const creating = ref(false)
const revoking = ref(false)
const shareInfo = ref<PictureShareVO | null>(null)
const expireDays = ref(7)

// 有效期选项
const expireOptions = [
  { value: 7, label: '7 天' },
  { value: 30, label: '30 天' },
  { value: 0, label: '永久' },
]

// 是否可管理（更新有效期/撤销）：由后端按所有者/管理员判定
const canManage = computed(() => shareInfo.value?.canManage ?? false)

/**
 * 图片 id（保持字符串形态）
 *
 * ⚠️ 不能用 Number() 转换：雪花 id 超出 JS 安全整数，转换后末位被抹成 0，
 * 后端会认为图片不存在，分享链接创建失败。后端把 Long 序列化为 String 正是为此。
 */
const resolvedPictureId = computed<string | null>(
  () => firstQueryValue(props.pictureId) ?? null,
)

/**
 * 实际展示的链接：
 * 优先用后端返回的分享链接（带 shareCode，可公开访问）；
 * 后端在未配置 site-url 时返回相对路径，这里补成完整地址以便复制/扫码。
 */
const displayLink = computed(() => {
  const url = shareInfo.value?.shareUrl
  if (url) {
    return url.startsWith('http') ? url : `${window.location.origin}${url}`
  }
  return props.link || ''
})

const formatDate = (time?: string) => {
  if (!time) return ''
  const d = new Date(time)
  if (isNaN(d.getTime())) return ''
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/** 复制链接 */
const copyLink = async () => {
  try {
    await navigator.clipboard.writeText(displayLink.value)
    message.success('链接已复制')
  } catch {
    message.error('复制失败，请手动选择链接复制')
  }
}

/** 拉取已有分享链接 */
const loadExisting = async () => {
  const id = resolvedPictureId.value
  if (id == null) return
  loading.value = true
  try {
    const res = await getShareByPictureId(id)
    if (res.code === 0) {
      shareInfo.value = res.data ?? null
    }
  } catch {
    // 未登录等异常时静默失败：纯链接展示模式不依赖此接口
    shareInfo.value = null
  } finally {
    loading.value = false
  }
}

/** 创建或续期分享链接 */
const createLink = async (explicit = false) => {
  const id = resolvedPictureId.value
  if (id == null) return
  creating.value = true
  try {
    const res = await createShare({ pictureId: id, expireDays: expireDays.value })
    if (res.code === 0 && res.data) {
      shareInfo.value = res.data
      if (explicit) message.success('已更新分享链接')
    } else {
      message.error(res.message ?? '生成分享链接失败')
    }
  } catch (e: any) {
    message.error(e?.message ?? '生成分享链接失败')
  } finally {
    creating.value = false
  }
}

/** 撤销分享链接 */
const revokeLink = async () => {
  const id = resolvedPictureId.value
  if (id == null) return
  revoking.value = true
  try {
    const res = await revokeShare(id)
    if (res.code === 0) {
      shareInfo.value = null
      message.success('已撤销分享，链接立即失效')
    } else {
      message.error(res.message ?? '撤销失败')
    }
  } catch (e: any) {
    message.error(e?.message ?? '撤销失败')
  } finally {
    revoking.value = false
  }
}

/** 打开弹窗并在需要时创建分享链接 */
const openModal = async () => {
  visible.value = true
  if (resolvedPictureId.value == null) return
  await loadExisting()
  if (!shareInfo.value) {
    await createLink()
  }
}

const closeModal = () => {
  visible.value = false
}

defineExpose({ openModal })
</script>

<style scoped>
/* ===== 有效期药丸组（仅管理者可见） ===== */
.expire-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--border-color);
}

.expire-label {
  font-size: 13px;
  color: var(--text-secondary);
  flex-shrink: 0;
}

.expire-pills {
  display: flex;
  gap: 6px;
}

.expire-pill {
  border: 1px solid var(--border-color);
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  font-family: inherit;
  border-radius: 999px;
  padding: 3px 14px;
  cursor: pointer;
  transition:
    color 0.2s ease,
    border-color 0.2s ease,
    background 0.2s ease;
}

.expire-pill:hover {
  color: var(--accent);
  border-color: var(--accent-soft);
}

.expire-pill.active {
  background: var(--accent-soft);
  color: var(--accent);
  border-color: rgba(64, 169, 255, 0.45);
  font-weight: 600;
}

/* ===== 链接框 ===== */
.link-box {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--border-color);
  border-radius: var(--card-radius, 12px);
  padding: 10px 12px;
  background: rgba(127, 127, 127, 0.06);
}

.link-text {
  flex: 1;
  min-width: 0;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
  color: var(--text-secondary);
  word-break: break-all;
  line-height: 1.5;
}

.copy-btn {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 8px;
  background: var(--accent-soft);
  color: var(--accent);
  cursor: pointer;
  transition: background 0.2s ease;
}

.copy-btn:hover {
  background: rgba(64, 169, 255, 0.35);
}

.share-meta {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.share-meta :deep(.ant-tag) {
  margin-inline-end: 0;
}

/* ===== 扫码 ===== */
.qr-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
  padding: 16px 0;
  border-top: 1px solid var(--border-color);
}

.qr-caption {
  font-size: 12px;
  color: var(--text-secondary);
}

/* ===== 管理操作 ===== */
.share-actions {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 14px;
}

/* ===== 空状态 ===== */
.empty-block {
  padding: 24px 0 12px;
}

.empty-block .empty-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.empty-icon {
  font-size: 36px;
  color: var(--accent);
  opacity: 0.55;
}

.empty-text {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
  max-width: 280px;
  line-height: 1.6;
}

h4 {
  margin: 8px 0 6px;
  font-size: 14px;
  color: var(--text-primary-light);
}

html.dark h4 {
  color: var(--text-primary);
}
</style>
