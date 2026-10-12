<template>
  <div class="cmt" :class="{ 'cmt--locked': !loginUser.id }">
    <!-- 头部：总数 + 排序切换 -->
    <div v-if="showHeader" class="cmt__head">
      <span class="cmt__head-total">评论 <b>{{ total }}</b></span>
      <div class="cmt__sort">
        <button
          type="button"
          class="cmt__sort-btn"
          :class="{ 'is-active': sort === 'hot' }"
          @click="switchSort('hot')"
        >
          最热
        </button>
        <span class="cmt__sort-sep" />
        <button
          type="button"
          class="cmt__sort-btn"
          :class="{ 'is-active': sort === 'new' }"
          @click="switchSort('new')"
        >
          最新
        </button>
      </div>
    </div>

    <!-- 评论流 -->
    <a-spin :spinning="loading">
      <div v-if="comments.length" class="cmt__list">
        <div v-for="item in comments" :key="String(item.id)" class="cmt__item">
          <a-avatar
            :size="40"
            :src="item.user?.userAvatar"
            class="cmt__avatar"
            @click="goUserPage(item)"
          >
            <template #icon><UserOutlined /></template>
          </a-avatar>

          <div class="cmt__bubble">
            <!-- 气泡头部：昵称 + 作者标识 + 操作 -->
            <div class="cmt__bubble-head">
              <span class="cmt__name" @click="goUserPage(item)">
                {{ item.user?.userName ?? '匿名用户' }}
              </span>
              <span v-if="isPictureAuthor(item)" class="cmt__badge">作者</span>
              <div class="cmt__tools">
                <a-dropdown :trigger="['click']">
                  <button type="button" class="cmt__icon-btn" title="更多">
                    <EllipsisOutlined />
                  </button>
                  <template #overlay>
                    <a-menu>
                      <a-menu-item v-if="item.canDelete" key="delete" danger @click="confirmRemove(item)">
                        <DeleteOutlined /> 删除评论
                      </a-menu-item>
                      <a-menu-item v-else key="report" disabled>
                        <StopOutlined /> 举报
                      </a-menu-item>
                    </a-menu>
                  </template>
                </a-dropdown>
              </div>
            </div>

            <!-- 正文 -->
            <div class="cmt__text">{{ item.content }}</div>

            <!-- 气泡底部：时间 + 点赞 + 回复 -->
            <div class="cmt__foot">
              <span class="cmt__time">{{ formatTime(item.createTime) }}</span>
              <span class="cmt__foot-gap" />
              <button
                type="button"
                class="cmt__like"
                :class="{ 'is-liked': item.isLiked }"
                @click="toggleLike(item)"
              >
                <HeartFilled v-if="item.isLiked" />
                <HeartOutlined v-else />
                <i v-if="item.likeCount">{{ formatLikeCount(item.likeCount) }}</i>
              </button>
              <button type="button" class="cmt__reply-btn" @click="startReply(item)">
                回复
              </button>
            </div>

            <!-- 内联回复输入框 -->
            <div v-if="replyingId === item.id" class="cmt__inline-editor">
              <a-textarea
                ref="replyInputRef"
                v-model:value="replyDraft"
                :auto-size="{ minRows: 1, maxRows: 3 }"
                :maxlength="COMMENT_MAX"
                :placeholder="`回复 @${item.user?.userName ?? '匿名用户'}：`"
                class="cmt__inline-input"
                @press-enter="onReplyEnter(item)"
              />
              <div class="cmt__inline-foot">
                <a-button size="small" type="text" @click="cancelReply">取消</a-button>
                <a-button
                  size="small"
                  type="primary"
                  :loading="replying"
                  :disabled="!replyDraft.trim()"
                  @click="sendReply(item)"
                >
                  回复
                </a-button>
              </div>
            </div>
          </div>
        </div>

        <div v-if="!hasMore" class="cmt__end">
          <span class="cmt__end-line" />
          <span>已经到底啦</span>
          <span class="cmt__end-line" />
        </div>
      </div>

      <div v-else-if="!loading" class="cmt__empty">
        <img class="cmt__empty-img" :src="EMPTY_SVG" alt="" />
        <p class="cmt__empty-title">还没有评论</p>
        <p class="cmt__empty-sub">来抢第一个沙发吧</p>
      </div>
    </a-spin>

    <!-- 分页 -->
    <div v-if="total > pageSize" class="cmt__pager">
      <a-pagination
        v-model:current="current"
        :total="total"
        :page-size="pageSize"
        size="small"
        :show-size-changer="false"
        @change="load"
      />
    </div>

    <!-- 主输入区 -->
    <div class="cmt__composer">
      <a-avatar :size="38" :src="loginUser.userAvatar" class="cmt__avatar">
        <template #icon><UserOutlined /></template>
      </a-avatar>
      <div v-if="loginUser.id" class="cmt__composer-body">
        <div class="cmt__composer-box" :class="{ 'is-focus': composerFocus }">
          <textarea
            ref="composerRef"
            v-model="draft"
            class="cmt__textarea"
            rows="1"
            :maxlength="COMMENT_MAX"
            placeholder="写下你的评论…"
            :disabled="submitting"
            @focus="composerFocus = true"
            @blur="composerFocus = false"
            @keydown="onComposerKeydown"
            @input="autoGrow"
          />
        </div>
        <div class="cmt__composer-foot">
          <span class="cmt__composer-tip">Ctrl + Enter 发送</span>
          <span
            v-if="draft.length"
            class="cmt__char"
            :class="{ 'is-warn': draft.length > COMMENT_MAX * 0.9 }"
          >{{ draft.length }}/{{ COMMENT_MAX }}</span>
          <a-button
            type="primary"
            class="cmt__send"
            :loading="submitting"
            :disabled="!draft.trim()"
            @click="submit"
          >
            发 送
          </a-button>
        </div>
      </div>
      <!-- 未登录：展示输入框样式，点击引导登录 -->
      <div v-else class="cmt__composer-body" @click="promptLogin">
        <div class="cmt__composer-box cmt__composer-box--locked">
          <span class="cmt__locked-placeholder">登录后发表评论…</span>
        </div>
        <div class="cmt__composer-foot">
          <a-button type="primary" size="small" class="cmt__send">发 送</a-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { Modal, message } from 'ant-design-vue'
import {
  DeleteOutlined,
  EllipsisOutlined,
  HeartFilled,
  HeartOutlined,
  StopOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import {
  addComment,
  deleteComment,
  likeComment,
  pageComments,
} from '@/api/interaction.ts'
import type { PictureCommentVO } from '@/api/interaction.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { firstQueryValue } from '@/utils'

interface Props {
  pictureId?: number | string
  /** 图片作者用户 id：命中时在昵称旁显示「作者」标识 */
  authorId?: number | string
  /**
   * 是否展示头部。
   * 头部承载「评论 N + 最热/最新排序」，关闭它排序也会消失——
   * 仅在宿主自备了同等排序控件的场合才传 false。
   */
  showHeader?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  showHeader: true,
})

/** 与后端 COMMENT_MAX_LENGTH 保持一致 */
const COMMENT_MAX = 500

// 空态插画：内联 SVG data-uri，不引外部资源
const EMPTY_SVG =
  'data:image/svg+xml;utf8,' +
  encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" width="72" height="72" viewBox="0 0 72 72"><g fill="none" fill-rule="evenodd"><rect x="10" y="12" width="52" height="38" rx="8" fill="#EDF4FF"/><path d="M22 50l-5 9 14-9" fill="#EDF4FF"/><circle cx="28" cy="30" r="3.2" fill="#B7D3FF"/><circle cx="44" cy="30" r="3.2" fill="#B7D3FF"/><path d="M28 39c2.2 2.6 5.2 3.9 8 3.9s5.8-1.3 8-3.9" stroke="#B7D3FF" stroke-width="2.6" stroke-linecap="round"/></g></svg>`,
  )

const router = useRouter()
const loginUserStore = useLoginUserStore()
const loginUser = loginUserStore.loginUser

const comments = ref<PictureCommentVO[]>([])
const total = ref(0)
const current = ref(1)
const pageSize = 10
const sort = ref<'hot' | 'new'>('hot')
const loading = ref(false)
const submitting = ref(false)
const draft = ref('')
const composerFocus = ref(false)
const composerRef = ref<HTMLTextAreaElement>()

// 回复态
const replyingId = ref<string | number | null>(null)
const replyDraft = ref('')
const replying = ref(false)

/** 是否还有下一页 */
const hasMore = computed(() => current.value * pageSize < total.value)

/**
 * 取图片 id（保持字符串形态）
 *
 * ⚠️ 绝不能用 Number() 转换：雪花 id（如 2100942816594628609）超出 JS 安全整数，
 * Number() 会把末位抹成 0，后端随即返回「图片不存在」，评论区会静默显示为空。
 */
const resolvePictureId = (): string | null => firstQueryValue(props.pictureId) ?? null

const load = async () => {
  const id = resolvePictureId()
  if (id == null) return
  loading.value = true
  try {
    const res = await pageComments({
      pictureId: id,
      current: current.value,
      pageSize,
      sort: sort.value,
    })
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

const switchSort = (s: 'hot' | 'new') => {
  if (sort.value === s) return
  sort.value = s
  current.value = 1
  load()
}

const submit = async () => {
  const id = resolvePictureId()
  if (id == null) return
  if (!loginUser.id) {
    promptLogin()
    return
  }
  const content = draft.value.trim()
  if (!content) return
  submitting.value = true
  try {
    const res = await addComment({ pictureId: id, content })
    if (res.code === 0) {
      draft.value = ''
      message.success('评论成功')
      // 「最新」排序下新评论在第一页顶部；「最热」排序下同样回到首页查看
      current.value = 1
      sort.value = 'new'
      await load()
      autoGrowAfterClear()
    } else {
      message.error(res.message ?? '评论失败')
    }
  } catch (e: any) {
    message.error('评论失败：' + (e?.message ?? ''))
  } finally {
    submitting.value = false
  }
}

const onComposerKeydown = (e: KeyboardEvent) => {
  if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
    e.preventDefault()
    submit()
  }
}

/** 输入框高度自适应（原生 textarea 无 auto-size，这里手动撑开） */
const autoGrow = () => {
  const el = composerRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 132) + 'px'
}
const autoGrowAfterClear = () => {
  nextTick(() => {
    const el = composerRef.value
    if (el) el.style.height = 'auto'
  })
}

const confirmRemove = (item: PictureCommentVO) => {
  Modal.confirm({
    title: '删除这条评论？',
    content: item.content?.slice(0, 60),
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: () => remove(item),
  })
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

// ----- 评论点赞（乐观更新 + 服务端对账） -----
const toggleLike = async (item: PictureCommentVO) => {
  if (!loginUser.id) {
    promptLogin()
    return
  }
  if (item.id == null) return
  const liked = !!item.isLiked
  const base = item.likeCount ?? 0
  // 乐观更新
  item.isLiked = !liked
  item.likeCount = liked ? Math.max(base - 1, 0) : base + 1
  try {
    const res = await likeComment(item.id, !liked)
    if (res.code === 0 && res.data != null) {
      item.likeCount = res.data
      item.isLiked = !liked
    } else {
      item.isLiked = liked
      item.likeCount = base
      message.error(res.message ?? '操作失败')
    }
  } catch (e: any) {
    item.isLiked = liked
    item.likeCount = base
    message.error(e?.message ?? '请先登录')
  }
}

// ----- 回复 -----
const startReply = (item: PictureCommentVO) => {
  if (!loginUser.id) {
    promptLogin()
    return
  }
  replyingId.value = item.id ?? null
  replyDraft.value = ''
  nextTick(() => {
    document.querySelector<HTMLTextAreaElement>('.cmt__inline-input')?.focus()
  })
}
const cancelReply = () => {
  replyingId.value = null
  replyDraft.value = ''
}
const onReplyEnter = (item: PictureCommentVO) => {
  // ant-design-vue 的 pressEnter 回调携带原生 KeyboardEvent（挂在 window.event 上）
  const native = window.event as KeyboardEvent | undefined
  if (native?.ctrlKey || native?.metaKey) sendReply(item)
}
/**
 * 回复目前落到同一评论流（后端为一级平铺模型）：
 * 内容以「回复 @昵称：」前缀保留语境，是当前数据结构下最诚实的实现。
 */
const sendReply = async (item: PictureCommentVO) => {
  const id = resolvePictureId()
  if (id == null) return
  const content = replyDraft.value.trim()
  if (!content) return
  replying.value = true
  try {
    const full = `回复 @${item.user?.userName ?? '匿名用户'}：${content}`
    const res = await addComment({ pictureId: id, content: full })
    if (res.code === 0) {
      message.success('回复成功')
      cancelReply()
      current.value = 1
      sort.value = 'new'
      await load()
    } else {
      message.error(res.message ?? '回复失败')
    }
  } catch (e: any) {
    message.error('回复失败：' + (e?.message ?? ''))
  } finally {
    replying.value = false
  }
}

const goUserPage = (item: PictureCommentVO) => {
  const uid = item.user?.id ?? item.userId
  if (uid != null) router.push(`/user/${uid}`)
}

const isPictureAuthor = (item: PictureCommentVO) => {
  const author = firstQueryValue(props.authorId)
  const uid = firstQueryValue(item.userId)
  return author != null && uid != null && author === uid
}

const promptLogin = () => {
  message.warning('请先登录后再参与评论')
  router.push({ path: '/user/login', query: { redirect: window.location.href } })
}

/** 点赞数格式化：超 1 万显示 x.x w */
const formatLikeCount = (n: number) =>
  n >= 10000 ? (n / 10000).toFixed(1).replace(/\.0$/, '') + 'w' : String(n)

const formatTime = (time?: string) => {
  if (!time) return ''
  let d = new Date(time)
  if (isNaN(d.getTime())) d = new Date(time.replace(/-/g, '/').replace('T', ' '))
  if (isNaN(d.getTime())) return ''
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/**
 * 监听图片 id：父组件异步加载详情后 id 才有值，必须 watch + immediate。
 * 详情弹窗左右键切图（id 变化）时自动重载评论。
 */
watch(
  () => props.pictureId,
  (newId) => {
    if (firstQueryValue(newId)) {
      current.value = 1
      replyingId.value = null
      load()
    }
  },
  { immediate: true },
)

defineExpose({ reload: load })
</script>

<style scoped>
.cmt {
  --cmt-accent: var(--accent, #40a9ff);
  --cmt-name: #4e6b8a;
  --cmt-text: #303133;
  --cmt-meta: #9aa4b2;
  --cmt-bubble: #f5f7fa;
  --cmt-bubble-hover: #eef2f7;
  --cmt-bubble-radius: 14px;
  display: flex;
  flex-direction: column;
  gap: 16px;

  /* 主题化浏览器默认外观：文本选中色与滚动条，避免系统蓝/灰裸奔 */
  &::selection {
    background: rgba(64, 169, 255, 0.22);
  }

  & *::selection {
    background: rgba(64, 169, 255, 0.22);
  }
}

/* ===== 头部 ===== */
.cmt__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.cmt__head-total {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary-light);
}

.cmt__head-total b {
  margin-left: 4px;
  font-weight: 700;
}

/* 排序：胶囊分段控件 */
.cmt__sort {
  display: inline-flex;
  align-items: center;
  padding: 2px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.04);
}

.cmt__sort-btn {
  padding: 3px 14px;
  border: none;
  border-radius: 999px;
  background: transparent;
  font-size: 12.5px;
  color: var(--cmt-meta);
  cursor: pointer;
  transition: all 0.18s ease;
}

.cmt__sort-btn:hover {
  color: var(--cmt-text);
}

.cmt__sort-btn.is-active {
  background: #fff;
  color: var(--cmt-text);
  font-weight: 600;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
}

.cmt__sort-sep {
  width: 1px;
  height: 10px;
  background: rgba(0, 0, 0, 0.08);
  margin: 0 2px;
}

/* ===== 评论流 ===== */
.cmt__list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.cmt__item {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.cmt__avatar {
  flex-shrink: 0;
  cursor: pointer;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  transition: transform 0.18s ease;
}

.cmt__avatar:hover {
  transform: scale(1.06);
}

.cmt__bubble {
  flex: 1;
  min-width: 0;
  background: var(--cmt-bubble);
  border-radius: var(--cmt-bubble-radius);
  border-top-left-radius: 4px; /* 指向头像的对话气泡尖角 */
  padding: 12px 16px 10px;
  transition: background 0.18s ease;
}

.cmt__item:hover .cmt__bubble {
  background: var(--cmt-bubble-hover);
}

.cmt__bubble-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cmt__name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--cmt-name);
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.15s ease;
}

.cmt__name:hover {
  color: var(--cmt-accent);
}

/* 作者标识：小圆角胶囊 */
.cmt__badge {
  flex-shrink: 0;
  padding: 0 6px;
  height: 17px;
  line-height: 17px;
  border-radius: 4px;
  background: linear-gradient(135deg, #40a9ff, #1677ff);
  color: #fff;
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 1px;
}

.cmt__tools {
  margin-left: auto;
  opacity: 0;
  transition: opacity 0.18s ease;
}

.cmt__item:hover .cmt__tools {
  opacity: 1;
}

.cmt__icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--cmt-meta);
  cursor: pointer;
  transition: all 0.15s ease;
}

.cmt__icon-btn:hover {
  background: rgba(0, 0, 0, 0.06);
  color: var(--cmt-text);
}

.cmt__text {
  margin-top: 6px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--cmt-text);
  white-space: pre-wrap;
  word-break: break-word;
}

/* 气泡底部操作行 */
.cmt__foot {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 8px;
}

.cmt__time {
  font-size: 12px;
  color: var(--cmt-meta);
  font-variant-numeric: tabular-nums;
}

.cmt__foot-gap {
  flex: 1;
}

.cmt__like {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: transparent;
  padding: 2px 6px;
  border-radius: 6px;
  font-size: 13px;
  color: var(--cmt-meta);
  cursor: pointer;
  transition: all 0.18s ease;
}

.cmt__like i {
  font-style: normal;
  font-size: 12.5px;
  font-variant-numeric: tabular-nums;
}

.cmt__like:hover {
  color: #ff4d6a;
  background: rgba(255, 77, 106, 0.08);
  transform: scale(1.04);
}

.cmt__like.is-liked {
  color: #ff4d6a;
}

.cmt__like.is-liked i {
  font-weight: 600;
}

.cmt__reply-btn {
  border: none;
  background: transparent;
  padding: 2px 6px;
  border-radius: 6px;
  font-size: 12.5px;
  color: var(--cmt-meta);
  cursor: pointer;
  transition: all 0.18s ease;
}

.cmt__reply-btn:hover {
  color: var(--cmt-accent);
  background: rgba(64, 169, 255, 0.08);
}

/* 内联回复框 */
.cmt__inline-editor {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed rgba(0, 0, 0, 0.08);
}

.cmt__inline-input {
  border-radius: 10px;
  background: #fff;
  font-size: 13.5px;
}

.cmt__inline-foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

/* ===== 到底提示 ===== */
.cmt__end {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 6px 0 0;
  font-size: 12px;
  color: var(--cmt-meta);
}

.cmt__end-line {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(0, 0, 0, 0.08), transparent);
}

/* ===== 空态 ===== */
.cmt__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 22px 0 10px;
}

.cmt__empty-img {
  width: 72px;
  height: 72px;
  opacity: 0.9;
}

.cmt__empty-title {
  margin: 10px 0 0;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-secondary);
}

.cmt__empty-sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--cmt-meta);
}

/* ===== 分页 ===== */
.cmt__pager {
  display: flex;
  justify-content: center;
}

/* ===== 主输入区 ===== */
.cmt__composer {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding-top: 16px;
  border-top: 1px solid var(--border-color);
}

.cmt__composer-body {
  flex: 1;
  min-width: 0;
}

/* 输入容器：白底描边卡片，聚焦时主色光晕 */
.cmt__composer-box {
  background: var(--bg-card);
  border: 1.5px solid var(--border-color);
  border-radius: 14px;
  padding: 4px 4px 4px 14px;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.cmt__composer-box.is-focus {
  border-color: var(--cmt-accent);
  box-shadow: 0 0 0 3px rgba(64, 169, 255, 0.13);
}

.cmt__composer-box--locked {
  cursor: pointer;
  display: flex;
  align-items: center;
  min-height: 42px;
}

.cmt__locked-placeholder {
  font-size: 13.5px;
  color: var(--cmt-meta);
}

.cmt__textarea {
  display: block;
  width: 100%;
  border: none;
  outline: none;
  resize: none;
  background: transparent;
  padding: 8px 0;
  font-size: 14px;
  line-height: 1.6;
  font-family: inherit;
  color: var(--cmt-text);
  max-height: 132px;
}

.cmt__textarea::placeholder {
  color: var(--cmt-meta);
}

.cmt__composer-foot {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
}

.cmt__composer-tip {
  font-size: 12px;
  color: var(--cmt-meta);
}

.cmt__char {
  margin-left: auto;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--cmt-meta);
  transition: color 0.18s ease;
}

.cmt__char.is-warn {
  color: #fa8c16;
  font-weight: 600;
}

.cmt__send {
  min-width: 76px;
  border-radius: 999px;
  font-weight: 600;
  letter-spacing: 2px;
  background: linear-gradient(135deg, #40a9ff, #1677ff);
  border: none;
  box-shadow: 0 3px 10px rgba(22, 119, 255, 0.28);
  transition: all 0.2s ease;
}

.cmt__send:not(:disabled):hover {
  transform: translateY(-1px);
  box-shadow: 0 5px 14px rgba(22, 119, 255, 0.36);
}

.cmt__send:disabled {
  background: rgba(0, 0, 0, 0.12);
  box-shadow: none;
}

/* ===== 深色模式 ===== */
html.dark .cmt {
  --cmt-name: #8fb8dd;
  --cmt-text: #e8e8e8;
  --cmt-meta: #6e7681;
  --cmt-bubble: rgba(255, 255, 255, 0.055);
}

html.dark .cmt__item:hover .cmt__bubble {
  background: rgba(255, 255, 255, 0.09);
}

html.dark .cmt__head-total {
  color: var(--text-primary);
}

html.dark .cmt__sort {
  background: rgba(255, 255, 255, 0.07);
}

html.dark .cmt__sort-btn.is-active {
  background: rgba(255, 255, 255, 0.14);
  color: #f0f0f0;
}

html.dark .cmt__icon-btn:hover {
  background: rgba(255, 255, 255, 0.1);
}

html.dark .cmt__inline-input {
  background: rgba(255, 255, 255, 0.08);
}

html.dark .cmt__composer-box {
  background: rgba(255, 255, 255, 0.045);
}

html.dark .cmt__composer-box.is-focus {
  background: rgba(255, 255, 255, 0.06);
}

html.dark .cmt__send:disabled {
  background: rgba(255, 255, 255, 0.1);
}

/* 触屏：操作常显 */
@media (hover: none) {
  .cmt__tools {
    opacity: 1;
  }
}
</style>
