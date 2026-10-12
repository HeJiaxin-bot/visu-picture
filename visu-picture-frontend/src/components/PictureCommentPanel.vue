<template>
  <div class="cmt">
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
        <div v-for="root in comments" :key="String(root.id)" class="cmt__thread">
          <!-- ===== 顶层评论 ===== -->
          <div class="cmt__item">
            <a-avatar :size="40" :src="root.user?.userAvatar" class="cmt__avatar" @click="goUserPage(root)">
              <template #icon><UserOutlined /></template>
            </a-avatar>
            <div class="cmt__main">
              <div class="cmt__bubble">
                <div class="cmt__bubble-head">
                  <span class="cmt__name" @click="goUserPage(root)">
                    {{ root.user?.userName ?? '匿名用户' }}
                  </span>
                  <span v-if="isPictureAuthor(root)" class="cmt__badge">作者</span>
                  <div class="cmt__tools">
                    <a-dropdown :trigger="['click']">
                      <button type="button" class="cmt__icon-btn" title="更多">
                        <EllipsisOutlined />
                      </button>
                      <template #overlay>
                        <a-menu>
                          <a-menu-item v-if="root.canDelete" key="delete" danger @click="confirmRemove(root)">
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
                <div class="cmt__text">{{ root.content }}</div>
                <div class="cmt__foot">
                  <span class="cmt__time">{{ formatTime(root.createTime) }}</span>
                  <span v-if="root.location" class="cmt__loc">{{ root.location }}</span>
                  <span class="cmt__foot-gap" />
                  <button
                    type="button"
                    class="cmt__like"
                    :class="{ 'is-liked': root.isLiked }"
                    @click="toggleLike(root)"
                  >
                    <HeartFilled v-if="root.isLiked" />
                    <HeartOutlined v-else />
                    <i v-if="root.likeCount">{{ formatLikeCount(root.likeCount) }}</i>
                  </button>
                  <button type="button" class="cmt__reply-btn" @click="startReply(root, root)">
                    回复
                  </button>
                </div>

                <!-- 顶层评论的内联回复框 -->
                <div v-if="replyingId === String(root.id)" class="cmt__inline-editor">
                  <div class="cmt__inline-row">
                    <a-avatar :size="26" :src="loginUser.userAvatar">
                      <template #icon><UserOutlined /></template>
                    </a-avatar>
                    <a-textarea
                      ref="inlineInputRef"
                      v-model:value="replyDraft"
                      :auto-size="{ minRows: 1, maxRows: 3 }"
                      :maxlength="COMMENT_MAX"
                      :placeholder="`回复 @${replyTargetName}：`"
                      class="cmt__inline-input"
                      @press-enter="onReplyEnter"
                    />
                  </div>
                  <div class="cmt__inline-foot">
                    <a-button size="small" type="text" @click="cancelReply">取消</a-button>
                    <a-button
                      size="small"
                      type="primary"
                      :loading="replying"
                      :disabled="!replyDraft.trim()"
                      @click="sendReply()"
                    >
                      回复
                    </a-button>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- ===== 楼中楼回复 ===== -->
          <div v-if="root.replies?.length || (root.replyCount ?? 0) > 0" class="cmt__replies">
            <div
              v-for="reply in root.replies"
              :key="String(reply.id)"
              class="cmt__item cmt__item--reply"
            >
              <a-avatar :size="28" :src="reply.user?.userAvatar" class="cmt__avatar cmt__avatar--sm" @click="goUserPage(reply)">
                <template #icon><UserOutlined /></template>
              </a-avatar>
              <div class="cmt__main cmt__main--reply">
                <div class="cmt__bubble cmt__bubble--reply">
                  <div class="cmt__bubble-head">
                    <span class="cmt__name cmt__name--sm" @click="goUserPage(reply)">
                      {{ reply.user?.userName ?? '匿名用户' }}
                    </span>
                    <span v-if="isPictureAuthor(reply)" class="cmt__badge">作者</span>
                    <div class="cmt__tools">
                      <a-dropdown :trigger="['click']">
                        <button type="button" class="cmt__icon-btn" title="更多">
                          <EllipsisOutlined />
                        </button>
                        <template #overlay>
                          <a-menu>
                            <a-menu-item v-if="reply.canDelete" key="delete" danger @click="confirmRemove(reply)">
                              <DeleteOutlined /> 删除回复
                            </a-menu-item>
                            <a-menu-item v-else key="report" disabled>
                              <StopOutlined /> 举报
                            </a-menu-item>
                          </a-menu>
                        </template>
                      </a-dropdown>
                    </div>
                  </div>
                  <div class="cmt__text cmt__text--reply">
                    <!-- 回复"回复"时显示蓝色 @昵称（对齐参考图） -->
                    <template v-if="reply.replyToUserName">
                      <span class="cmt__at" @click="goUserPage(reply)">@{{ reply.replyToUserName }}</span>：
                    </template>{{ reply.content }}
                  </div>
                  <div class="cmt__foot">
                    <span class="cmt__time">{{ formatTime(reply.createTime) }}</span>
                    <span v-if="reply.location" class="cmt__loc">{{ reply.location }}</span>
                    <span class="cmt__foot-gap" />
                    <button
                      type="button"
                      class="cmt__like cmt__like--sm"
                      :class="{ 'is-liked': reply.isLiked }"
                      @click="toggleLike(reply, root)"
                    >
                      <HeartFilled v-if="reply.isLiked" />
                      <HeartOutlined v-else />
                      <i v-if="reply.likeCount">{{ formatLikeCount(reply.likeCount) }}</i>
                    </button>
                    <button type="button" class="cmt__reply-btn" @click="startReply(reply, root)">
                      回复
                    </button>
                  </div>

                  <!-- 回复的内联回复框 -->
                  <div v-if="replyingId === String(reply.id)" class="cmt__inline-editor">
                    <div class="cmt__inline-row">
                      <a-avatar :size="26" :src="loginUser.userAvatar">
                        <template #icon><UserOutlined /></template>
                      </a-avatar>
                      <a-textarea
                        v-model:value="replyDraft"
                        :auto-size="{ minRows: 1, maxRows: 3 }"
                        :maxlength="COMMENT_MAX"
                        :placeholder="`回复 @${replyTargetName}：`"
                        class="cmt__inline-input"
                        @press-enter="onReplyEnter"
                      />
                    </div>
                    <div class="cmt__inline-foot">
                      <a-button size="small" type="text" @click="cancelReply">取消</a-button>
                      <a-button
                        size="small"
                        type="primary"
                        :loading="replying"
                        :disabled="!replyDraft.trim()"
                        @click="sendReply()"
                      >
                        回复
                      </a-button>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 展开 / 收起楼中楼 -->
            <button
              v-if="(root.replyCount ?? 0) > (root.replies?.length ?? 0) && !expanded[root.id!]"
              type="button"
              class="cmt__expand"
              @click="expandReplies(root)"
            >
              <CaretDownOutlined />
              展开 {{ (root.replyCount ?? 0) - (root.replies?.length ?? 0) }} 条回复
            </button>
            <button
              v-else-if="expanded[root.id!]"
              type="button"
              class="cmt__expand"
              @click="collapseReplies(root)"
            >
              <CaretUpOutlined />
              收起回复
            </button>
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
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { Modal, message } from 'ant-design-vue'
import {
  CaretDownOutlined,
  CaretUpOutlined,
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
  pageCommentReplies,
  pageComments,
} from '@/api/interaction.ts'
import type { PictureCommentVO } from '@/api/interaction.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { firstQueryValue } from '@/utils'

interface Props {
  pictureId?: number | string
  /** 图片作者用户 id：命中时在昵称旁显示「作者」标识 */
  authorId?: number | string
  /** 是否展示头部（含排序切换） */
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

// 回复态：replyingId 是被回复评论的 id；replyRootId 是所在线程根（新回复插入位置）
const replyingId = ref<string | null>(null)
const replyRootId = ref<string | null>(null)
const replyTargetName = ref('')
const replyDraft = ref('')
const replying = ref(false)

/** 已展开楼中楼的线程（rootId -> true） */
const expanded = reactive<Record<string, boolean>>({})
/**
 * 一次性标记：下一个 load() 是否保留展开态。
 * 回复成功后重载列表时置 true（用户正盯着这个楼中楼，不能收起）；
 * 分页/排序切换时保持 false（旧展开无意义）。
 */
let keepExpandedOnLoad = false

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
      // 分页/排序变化后旧的展开态无意义，清空；
      // 但回复成功后的重载要保留当前线程的展开（见 sendReply 的 keepExpanded）
      if (!keepExpandedOnLoad) {
        Object.keys(expanded).forEach((k) => delete expanded[k])
      }
      keepExpandedOnLoad = false
      // 恢复展开态：重新拉取对应线程的全部回复
      const expandIds = Object.keys(expanded)
      if (expandIds.length) {
        await Promise.all(expandIds.map(async (rootId) => {
          const root = comments.value.find((c) => String(c.id) === rootId)
          if (root && (root.replyCount ?? 0) > (root.replies?.length ?? 0)) {
            const r = await pageCommentReplies({ rootId, pageSize: 100 })
            if (r.code === 0) root.replies = r.data?.records ?? []
          }
        }))
      }
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

/** 输入框高度自适应（原生 textarea 手动撑开） */
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
    title: item.parentId == null ? '删除这条评论及其全部回复？' : '删除这条回复？',
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
const toggleLike = async (item: PictureCommentVO, root?: PictureCommentVO) => {
  if (!loginUser.id) {
    promptLogin()
    return
  }
  if (item.id == null) return
  const liked = !!item.isLiked
  const base = item.likeCount ?? 0
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
/**
 * 打开内联回复框。
 * @param target 被回复的评论（顶层或回复均可）
 * @param root   所在线程的顶层评论（决定新回复的插入位置）
 */
const startReply = (target: PictureCommentVO, root: PictureCommentVO) => {
  if (!loginUser.id) {
    promptLogin()
    return
  }
  replyingId.value = target.id != null ? String(target.id) : null
  replyRootId.value = root.id != null ? String(root.id) : null
  replyTargetName.value = target.user?.userName ?? '匿名用户'
  replyDraft.value = ''
  nextTick(() => {
    document.querySelector<HTMLTextAreaElement>('.cmt__inline-input')?.focus()
  })
}
const cancelReply = () => {
  replyingId.value = null
  replyRootId.value = null
  replyTargetName.value = ''
  replyDraft.value = ''
}
const onReplyEnter = (e?: KeyboardEvent) => {
  const native = (e ?? (window.event as KeyboardEvent | undefined))
  if (native?.ctrlKey || native?.metaKey) sendReply()
}
const sendReply = async () => {
  const id = resolvePictureId()
  const rootId = replyRootId.value
  const parentId = replyingId.value
  if (id == null || rootId == null || parentId == null) return
  const content = replyDraft.value.trim()
  if (!content) return
  replying.value = true
  try {
    const res = await addComment({ pictureId: id, parentId, content })
    if (res.code === 0) {
      message.success('回复成功')
      cancelReply()
      // 保持该线程展开：否则重载后楼中楼收起，用户看不到刚发的回复
      keepExpandedOnLoad = true
      expanded[rootId] = true
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

// ----- 展开楼中楼（拉全量回复） -----
const expandReplies = async (root: PictureCommentVO) => {
  if (root.id == null) return
  const key = String(root.id)
  try {
    const res = await pageCommentReplies({ rootId: root.id, pageSize: 100 })
    if (res.code === 0) {
      root.replies = res.data?.records ?? []
      expanded[key] = true
    } else {
      message.error(res.message ?? '回复加载失败')
    }
  } catch (e: any) {
    message.error('回复加载失败：' + (e?.message ?? ''))
  }
}
const collapseReplies = (root: PictureCommentVO) => {
  const key = String(root.id)
  expanded[key] = false
  // 收起后恢复为首屏预览条数
  if (root.replies && root.replies.length > 2) {
    root.replies = root.replies.slice(0, 2)
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
      cancelReply()
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
  --cmt-heart: #ff4d6a;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.cmt *::selection {
  background: rgba(64, 169, 255, 0.22);
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

/* ===== 线程 ===== */
.cmt__list {
  display: flex;
  flex-direction: column;
  gap: 22px;
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

.cmt__avatar--sm {
  box-shadow: none;
}

.cmt__main {
  flex: 1;
  min-width: 0;
}

/* ===== 气泡 ===== */
.cmt__bubble {
  background: var(--cmt-bubble);
  border-radius: var(--cmt-bubble-radius);
  border-top-left-radius: 4px;
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

/* 蓝色 @昵称（回复语境，对齐参考图） */
.cmt__at {
  color: var(--cmt-accent);
  font-weight: 600;
  cursor: pointer;
}

.cmt__at:hover {
  text-decoration: underline;
}

/* ===== 底部操作行 ===== */
.cmt__foot {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
}

.cmt__time {
  font-size: 12px;
  color: var(--cmt-meta);
  font-variant-numeric: tabular-nums;
}

.cmt__loc {
  font-size: 12px;
  color: var(--cmt-meta);
}

.cmt__loc::before {
  content: '·';
  margin-right: 6px;
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
  color: var(--cmt-heart);
  background: rgba(255, 77, 106, 0.08);
  transform: scale(1.04);
}

.cmt__like.is-liked {
  color: var(--cmt-heart);
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

/* ===== 楼中楼 ===== */
.cmt__replies {
  margin-top: 10px;
  margin-left: 52px; /* 顶层头像 40 + 间距 12，与顶层正文对齐 */
  padding-left: 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  /* 竖向连接线：贯穿整个回复区 */
  border-left: 2px solid rgba(0, 0, 0, 0.05);
}

.cmt__item--reply .cmt__bubble {
  background: transparent;
  border-radius: 0;
  padding: 0;
}

.cmt__item--reply:hover .cmt__bubble {
  background: transparent;
}

.cmt__item--reply .cmt__tools {
  /* 回复区更紧凑，操作常驻但弱化 */
  opacity: 0;
}

.cmt__text--reply {
  font-size: 13.5px;
  margin-top: 3px;
}

.cmt__like--sm {
  font-size: 12.5px;
}

/* 展开/收起 */
.cmt__expand {
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-left: 14px;
  border: none;
  background: transparent;
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--cmt-accent);
  cursor: pointer;
  transition: all 0.15s ease;
}

.cmt__expand:hover {
  background: rgba(64, 169, 255, 0.08);
}

/* 内联回复框 */
.cmt__inline-editor {
  margin-top: 10px;
}

.cmt__inline-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.cmt__inline-input {
  flex: 1;
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
  --cmt-bubble-hover: rgba(255, 255, 255, 0.09);
}

html.dark .cmt__replies {
  border-left-color: rgba(255, 255, 255, 0.08);
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

@media (hover: none) {
  .cmt__tools {
    opacity: 1;
  }
}
</style>
