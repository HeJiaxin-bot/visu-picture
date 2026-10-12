<template>
  <div>
    <a-modal
      v-model:open="visible"
      :title="title"
      :footer="null"
      :width="480"
      @cancel="closeModal"
    >
      <a-spin :spinning="loading">
        <!-- 有效期选择（仅在有 pictureId 时可创建/修改） -->
        <div v-if="pictureId != null" class="expire-row">
          <span class="expire-label">有效期</span>
          <a-radio-group v-model:value="expireDays" size="small" button-style="solid">
            <a-radio-button :value="7">7 天</a-radio-button>
            <a-radio-button :value="30">30 天</a-radio-button>
            <a-radio-button :value="0">永久</a-radio-button>
          </a-radio-group>
          <a-button
            v-if="!shareInfo"
            type="link"
            size="small"
            :loading="creating"
            @click="createLink"
          >
            生成链接
          </a-button>
        </div>

        <template v-if="displayLink">
          <h4>复制分享链接</h4>
          <a-typography-link copyable class="share-link">
            {{ displayLink }}
          </a-typography-link>

          <div v-if="shareInfo" class="share-meta">
            <a-tag v-if="shareInfo.expireTime" color="orange">
              有效期至 {{ formatDate(shareInfo.expireTime) }}
            </a-tag>
            <a-tag v-else color="green">永久有效</a-tag>
            <a-tag color="blue">已被访问 {{ shareInfo.viewCount ?? 0 }} 次</a-tag>
          </div>

          <div style="margin-bottom: 16px" />
          <h4>手机扫码查看</h4>
          <a-qrcode :value="displayLink" />

          <div v-if="pictureId != null" class="share-actions">
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

        <a-empty v-else-if="!loading" description="尚未生成分享链接" />
      </a-spin>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { message } from 'ant-design-vue'
import { createShare, getShareByPictureId, revokeShare } from '@/api/interaction.ts'
import type { PictureShareVO } from '@/api/interaction.ts'
import { firstQueryValue } from '@/utils'

interface Props {
  title?: string
  /** 图片 id：提供时启用「创建/撤销分享链接」（需登录且为所有者） */
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

/** 拉取已有分享链接 */
const loadExisting = async () => {
  const id = resolvedPictureId.value
  if (id == null) return
  loading.value = true
  try {
    const res = await getShareByPictureId(id)
    if (res.code === 0) {
      shareInfo.value = res.data ?? null
      if (res.data?.expireTime) {
        // 已过期时间不做反推，保持默认选项即可
        expireDays.value = expireDays.value
      }
    }
  } catch {
    // 未登录/无权限时静默失败：纯链接展示模式不依赖此接口
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
.expire-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--border-color);
}

.expire-label {
  font-size: 13px;
  color: var(--text-secondary);
  flex-shrink: 0;
}

.share-link {
  display: inline-block;
  max-width: 100%;
  word-break: break-all;
}

.share-meta {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.share-meta :deep(.ant-tag) {
  margin-inline-end: 0;
}

.share-actions {
  display: flex;
  gap: 8px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--border-color);
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