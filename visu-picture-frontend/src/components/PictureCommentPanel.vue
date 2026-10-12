<template>
  <div class="comment-panel">
    <!-- 发表评论 -->
    <div class="comment-editor">
      <a-avatar :size="32" :src="loginUser.userAvatar">
        <template #icon><UserOutlined /></template>
      </a-avatar>
      <div class="editor-main">
        <a-textarea
          v-model:value="draft"
          :rows="2"
          :maxlength="COMMENT_MAX"
          show-count
          placeholder="说点什么吧…"
          :disabled="submitting"
          @press-enter="onPressEnter"
        />
        <div class="editor-actions">
          <span class="editor-tip">Ctrl + Enter 快速发送</span>
          <a-button
            type="primary"
            size="small"
            :loading="submitting"
            :disabled="!draft.trim()"
            @click="submit"
          >
            发表
          </a-button>
        </div>
      </div>
    </div>

    <!-- 评论列表 -->
    <a-spin :spinning="loading">
      <div v-if="comments.length" class="comment-list">
        <div v-for="item in comments" :key="item.id" class="comment-item">
          <a-avatar :size="32" :src="item.user?.userAvatar">
            <template #icon><UserOutlined /></template>
          </a-avatar>
          <div class="comment-body">
            <div class="comment-head">
              <span class="comment-name">{{ item.user?.userName ?? '匿名用户' }}</span>
              <span class="comment-time">{{ formatTime(item.createTime) }}</span>
              <a-popconfirm
                v-if="item.canDelete"
                title="确认删除这条评论？"
                ok-text="删除"
                cancel-text="取消"
                @confirm="remove(item)"
              >
                <span class="comment-delete"><DeleteOutlined /></span>
              </a-popconfirm>
            </div>
            <div class="comment-content">{{ item.content }}</div>
          </div>
        </div>
      </div>
      <a-empty v-else-if="!loading" description="还没有评论，来抢沙发～" :image="Empty.PRESENTED_IMAGE_SIMPLE" />
    </a-spin>

    <!-- 分页 -->
    <div v-if="total > pageSize" class="comment-pager">
      <a-pagination
        v-model:current="current"
        :total="total"
        :page-size="pageSize"
        size="small"
        :show-size-changer="false"
        @change="load"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Avatar as AAvatar, Empty, message } from 'ant-design-vue'
import { DeleteOutlined, UserOutlined } from '@ant-design/icons-vue'
import { addComment, deleteComment, pageComments } from '@/api/interaction.ts'
import type { PictureCommentVO } from '@/api/interaction.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { firstQueryValue } from '@/utils'

interface Props {
  pictureId?: number | string
}

const props = defineProps<Props>()

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

/* 发表区 */
.comment-editor {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.editor-main {
  flex: 1;
  min-width: 0;
}

.editor-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.editor-tip {
  font-size: 12px;
  color: var(--text-disabled);
}

/* 列表 */
.comment-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.comment-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.comment-body {
  flex: 1;
  min-width: 0;
}

.comment-head {
  display: flex;
  align-items: center;
  gap: 8px;
  line-height: 1.2;
}

.comment-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary-light);
}

.comment-time {
  font-size: 12px;
  color: var(--text-disabled);
}

.comment-delete {
  margin-left: auto;
  font-size: 13px;
  color: var(--text-disabled);
  cursor: pointer;
  transition: color 0.2s;
}

.comment-delete:hover {
  color: #ff4d4f;
}

.comment-content {
  margin-top: 4px;
  font-size: 13px;
  line-height: 1.65;
  color: var(--text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
}

.comment-pager {
  display: flex;
  justify-content: center;
}

html.dark .comment-name {
  color: var(--text-primary);
}
</style>