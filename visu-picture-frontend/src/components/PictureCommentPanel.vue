<template>
  <div class="comment-panel">
    <!-- 头部：评论总数（宿主编排已有同等标题时可关闭，避免重复） -->
    <div v-if="showHeader" class="cmt-header">
      <span class="cmt-total">共 <b>{{ total }}</b> 条评论</span>
    </div>

    <!-- 评论列表 -->
    <a-spin :spinning="loading">
      <div v-if="comments.length" class="cmt-list">
        <div v-for="item in comments" :key="item.id" class="cmt-item">
          <a-avatar :size="38" :src="item.user?.userAvatar" class="cmt-avatar">
            <template #icon><UserOutlined /></template>
          </a-avatar>
          <div class="cmt-main">
            <!-- 昵称 + 悬停操作：同一行，操作靠右 -->
            <div class="cmt-head">
              <span class="cmt-name">{{ item.user?.userName ?? '匿名用户' }}</span>
              <a-popconfirm
                v-if="item.canDelete"
                title="确认删除这条评论？"
                ok-text="删除"
                cancel-text="取消"
                @confirm="remove(item)"
              >
                <button type="button" class="cmt-act cmt-act--danger">
                  <DeleteOutlined />
                  <span>删除</span>
                </button>
              </a-popconfirm>
            </div>
            <div class="cmt-meta">{{ formatTime(item.createTime) }}</div>
            <div class="cmt-text">{{ item.content }}</div>
          </div>
        </div>

        <!-- 到底提示：仅在已加载完全部评论时出现 -->
        <div v-if="!hasMore" class="cmt-end">已经到底啦 ~</div>
      </div>

      <div v-else-if="!loading" class="cmt-empty">
        <a-empty :image="Empty.PRESENTED_IMAGE_SIMPLE" description="还没有评论，来抢沙发 ~" />
      </div>
    </a-spin>

    <!-- 分页（保持原有服务端分页行为） -->
    <div v-if="total > pageSize" class="cmt-pager">
      <a-pagination
        v-model:current="current"
        :total="total"
        :page-size="pageSize"
        size="small"
        :show-size-changer="false"
        @change="load"
      />
    </div>

    <!-- 发表评论：置于底部，与评论流形成「列表 → 输入」的自然顺序 -->
    <div class="cmt-editor">
      <a-avatar :size="36" :src="loginUser.userAvatar" class="cmt-avatar">
        <template #icon><UserOutlined /></template>
      </a-avatar>
      <div class="cmt-editor-body">
        <a-textarea
          v-model:value="draft"
          :maxlength="COMMENT_MAX"
          :auto-size="{ minRows: 1, maxRows: 4 }"
          placeholder="添加评论…"
          :disabled="submitting"
          class="cmt-input"
          @press-enter="onPressEnter"
        />
        <div class="cmt-editor-foot">
          <span class="cmt-tip">Ctrl + Enter 发送</span>
          <!-- 字数仅在输入后出现，避免空态噪音；接近上限时变色预警 -->
          <span
            v-if="draft.length"
            class="cmt-count"
            :class="{ 'cmt-count--warn': draft.length > COMMENT_MAX * 0.9 }"
          >
            {{ draft.length }}/{{ COMMENT_MAX }}
          </span>
          <a-button
            type="primary"
            size="small"
            class="cmt-send"
            :loading="submitting"
            :disabled="!draft.trim()"
            @click="submit"
          >
            <SendOutlined />
            <span>发送</span>
          </a-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Avatar as AAvatar, Empty, message } from 'ant-design-vue'
import { DeleteOutlined, SendOutlined, UserOutlined } from '@ant-design/icons-vue'
import { addComment, deleteComment, pageComments } from '@/api/interaction.ts'
import type { PictureCommentVO } from '@/api/interaction.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { firstQueryValue } from '@/utils'

interface Props {
  pictureId?: number | string
  /**
   * 是否展示面板自带的「共 N 条评论」头部。
   * 详情弹窗已在分隔线上写了「评论」标题，传 false 可避免两层标题重复。
   */
  showHeader?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  showHeader: true,
})

/** 与后端 COMMENT_MAX_LENGTH 保持一致 */
const COMMENT_MAX = 500

const loginUserStore = useLoginUserStore()
const loginUser = loginUserStore.loginUser

const comments = ref<PictureCommentVO[]>([])
const total = ref(0)
const current = ref(1)
const pageSize = 10
const loading = ref(false)
const submitting = ref(false)
const draft = ref('')

/** 是否还有下一页（用于决定「已经到底啦」是否展示） */
const hasMore = computed(() => current.value * pageSize < total.value)

/**
 * 取图片 id（保持字符串形态）
 *
 * ⚠️ 绝不能用 Number() 转换：雪花 id（如 2100942816594628609）超出 JS 安全整数，
 * Number() 会把末位抹成 0，后端随即返回「图片不存在」，评论区会静默显示为空。
 * 后端 JsonConfig 把 Long 序列化为 String 正是为此。
 */
const resolvePictureId = (): string | null => firstQueryValue(props.pictureId) ?? null

const load = async () => {
  const id = resolvePictureId()
  if (id == null) return
  loading.value = true
  try {
    const res = await pageComments({ pictureId: id, current: current.value, pageSize })
    if (res.code === 0) {
      comments.value = res.data?.records ?? []
      total.value = res.data?.total ?? 0
    } else {
      message.error(res.message ?? '评论加载失败')
    }
  } catch (e: any) {
    message.error('评论加载失败：' + (e?.message ?? ''))
  } finally {
    loading.value = false
  }
}

const submit = async () => {
  const id = resolvePictureId()
  if (id == null) return
  const content = draft.value.trim()
  if (!content) {
    message.warning('评论内容不能为空')
    return
  }
  submitting.value = true
  try {
    const res = await addComment({ pictureId: id, content })
    if (res.code === 0) {
      draft.value = ''
      message.success('评论成功')
      // 回到第一页看到自己的新评论（按时间倒序）
      current.value = 1
      await load()
    } else {
      message.error(res.message ?? '评论失败')
    }
  } catch (e: any) {
    message.error('评论失败：' + (e?.message ?? ''))
  } finally {
    submitting.value = false
  }
}

// Ctrl/Cmd + Enter 提交；单独 Enter 保留换行
const onPressEnter = (e: KeyboardEvent) => {
  if (e.ctrlKey || e.metaKey) {
    e.preventDefault()
    submit()
  }
}

const remove = async (item: PictureCommentVO) => {
  if (item.id == null) return
  try {
    const res = await deleteComment(item.id)
    if (res.code === 0) {
      message.success('已删除')
      await load()
    } else {
      message.error(res.message ?? '删除失败')
    }
  } catch (e: any) {
    message.error('删除失败：' + (e?.message ?? ''))
  }
}

const formatTime = (time?: string) => {
  if (!time) return ''
  let d = new Date(time)
  if (isNaN(d.getTime())) d = new Date(time.replace(/-/g, '/').replace('T', ' '))
  if (isNaN(d.getTime())) return ''
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/**
 * 监听图片 id：
 *
 * 不能只用 onMounted(load) —— 父组件首次渲染时 picture 往往还是空对象（{}），
 * 此时 props.pictureId 为 undefined，load() 会直接 return，
 * 等详情异步返回后没有任何东西再触发加载，评论区就永远显示"还没有评论"。
 *
 * 因此改为 watch + immediate：
 *   - 首次拿到有效 id 时立即加载；
 *   - 在详情弹窗里用左右方向键切换图片时（id 变化）自动重新加载对应评论。
 */
watch(
  () => props.pictureId,
  (newId) => {
    if (firstQueryValue(newId)) {
      // 切换图片时回到第一页，避免停留在上一张图的页码上
      current.value = 1
      load()
    }
  },
  { immediate: true },
)

defineExpose({ reload: load })
</script>

<style scoped>
.comment-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* ===== 头部：总数 ===== */
.cmt-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.cmt-total {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary-light);
}

.cmt-total b {
  margin: 0 2px;
  font-weight: 700;
  color: var(--accent);
}

/* ===== 列表 ===== */
.cmt-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.cmt-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  /* 悬停整行时操作按钮渐显，避免常驻图标造成视觉噪音 */
}

.cmt-item:hover .cmt-act {
  opacity: 1;
}

.cmt-avatar {
  flex-shrink: 0;
}

.cmt-main {
  flex: 1;
  min-width: 0;
}

.cmt-head {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 20px;
}

/* 昵称用品牌主色：与灰色时间、深色正文形成三级层次（对齐参考示例的彩色昵称） */
.cmt-name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--accent);
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cmt-meta {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-disabled);
  line-height: 1.4;
}

.cmt-text {
  margin-top: 6px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-primary-light);
  white-space: pre-wrap;
  word-break: break-word;
}

/* ===== 操作（悬停渐显） ===== */
.cmt-act {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
  padding: 2px 6px;
  border: none;
  border-radius: 6px;
  background: transparent;
  font-size: 12px;
  color: var(--text-disabled);
  cursor: pointer;
  opacity: 0;
  transition:
    opacity 0.18s ease,
    color 0.18s ease,
    background 0.18s ease;
}

.cmt-act--danger:hover {
  color: #ff4d4f;
  background: rgba(255, 77, 79, 0.08);
}

/* 触屏设备没有 hover，操作常显，否则无法删除 */
@media (hover: none) {
  .cmt-act {
    opacity: 1;
  }
}

/* ===== 到底提示 ===== */
.cmt-end {
  padding: 4px 0 2px;
  text-align: center;
  font-size: 12px;
  color: var(--text-disabled);
}

/* ===== 空态 ===== */
.cmt-empty {
  padding: 12px 0 4px;
}

.cmt-empty :deep(.ant-empty) {
  margin: 0;
}

.cmt-empty :deep(.ant-empty-description) {
  font-size: 13px;
  color: var(--text-disabled);
}

/* ===== 分页 ===== */
.cmt-pager {
  display: flex;
  justify-content: center;
}

/* ===== 输入区 ===== */
.cmt-editor {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding-top: 14px;
  border-top: 1px solid var(--border-color);
}

.cmt-editor-body {
  flex: 1;
  min-width: 0;
}

/* 输入框做成圆角胶囊：浅底、无边框，聚焦时才出现主色描边 */
.cmt-input {
  border-radius: 18px;
  resize: none;
  padding: 8px 14px;
  background: rgba(0, 0, 0, 0.025);
  border-color: transparent;
  transition:
    background 0.2s ease,
    border-color 0.2s ease;
}

.cmt-input:hover {
  background: rgba(0, 0, 0, 0.04);
}

.cmt-input:focus,
.cmt-input:focus-within {
  background: var(--bg-card);
  border-color: var(--accent);
  box-shadow: 0 0 0 2px rgba(64, 169, 255, 0.12);
}

.cmt-editor-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.cmt-tip {
  font-size: 12px;
  color: var(--text-disabled);
}

.cmt-count {
  margin-left: auto;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--text-disabled);
}

.cmt-count--warn {
  color: #fa8c16;
  font-weight: 600;
}

.cmt-send {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

/* ===== 深色模式 ===== */
html.dark .cmt-text {
  color: var(--text-primary);
}

html.dark .cmt-total {
  color: var(--text-primary);
}

html.dark .cmt-input {
  background: rgba(255, 255, 255, 0.05);
}

html.dark .cmt-input:hover {
  background: rgba(255, 255, 255, 0.08);
}

html.dark .cmt-input:focus,
html.dark .cmt-input:focus-within {
  background: rgba(255, 255, 255, 0.06);
}

html.dark .cmt-act--danger:hover {
  background: rgba(255, 77, 79, 0.14);
}
</style>
