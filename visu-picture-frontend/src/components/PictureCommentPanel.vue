<template>
  <div class="cmt">
    <!-- 头部：总数 + 排序切换 -->
    <div v-if="showHeader" class="cmt__head">
      <span class="cmt__head-total">共 <b>{{ total }}</b> 条评论</span>
      <div class="cmt__sort">
        <button
          type="button"
          class="cmt__sort-btn"
          :class="{ 'is-active': sort === 'hot' }"
          @click="switchSort('hot')"
        >
          最热
        </button>
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

    <!-- 滚动区：仅评论列表可滚动，输入区固定在下方 -->
    <div class="cmt__scroll">
      <a-spin :spinning="loading">
        <div v-if="comments.length" class="cmt__list">
          <div v-for="root in comments" :key="String(root.id)" class="cmt__thread">
            <!-- ===== 顶层评论（平铺，无气泡） ===== -->
            <div class="cmt__item">
              <a-avatar
                :size="40"
                :src="root.user?.userAvatar"
                class="cmt__avatar"
                @click="goUserPage(root)"
              >
                <template #icon><UserOutlined /></template>
              </a-avatar>
              <div class="cmt__main">
                <div class="cmt__name">
                  {{ root.user?.userName ?? '匿名用户' }}
                  <span v-if="isPictureAuthor(root)" class="cmt__badge">作者</span>
                </div>
                <div class="cmt__meta">
                  <span>{{ formatTime(root.createTime) }}</span>
                  <span v-if="root.location" class="cmt__loc">{{ root.location }}</span>
                </div>
                <div class="cmt__text">{{ root.content }}</div>
                <div class="cmt__foot">
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

                <!-- 顶层评论的内联回复框 -->
                <div v-if="replyingId === String(root.id)" class="cmt__inline-editor">
                  <a-textarea
                    v-model:value="replyDraft"
                    :auto-size="{ minRows: 1, maxRows: 3 }"
                    :maxlength="COMMENT_MAX"
                    :placeholder="`回复 @${replyTargetName}`"
                    class="cmt__inline-input"
                    @press-enter="onReplyEnter"
                  />
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

            <!-- ===== 楼中楼回复：默认收起为一行入口，展开后平铺 ===== -->
            <div v-if="(root.replyCount ?? 0) > 0" class="cmt__replies">
              <button
                v-if="!expanded[String(root.id)]"
                type="button"
                class="cmt__expand"
                @click="expandReplies(root)"
              >
                <CaretDownOutlined />
                @{{ root.user?.userName ?? '匿名用户' }} · {{ root.replyCount }}
              </button>

              <template v-else>
                <div
                  v-for="reply in root.replies"
                  :key="String(reply.id)"
                  class="cmt__item cmt__item--reply"
                >
                  <a-avatar
                    :size="28"
                    :src="reply.user?.userAvatar"
                    class="cmt__avatar cmt__avatar--sm"
                    @click="goUserPage(reply)"
                  >
                    <template #icon><UserOutlined /></template>
                  </a-avatar>
                  <div class="cmt__main">
                    <div class="cmt__name cmt__name--sm">
                      {{ reply.user?.userName ?? '匿名用户' }}
                      <span v-if="isPictureAuthor(reply)" class="cmt__badge">作者</span>
                    </div>
                    <div class="cmt__meta">
                      <span>{{ formatTime(reply.createTime) }}</span>
                      <span v-if="reply.location" class="cmt__loc">{{ reply.location }}</span>
                    </div>
                    <div class="cmt__text cmt__text--reply">
                      <span v-if="reply.replyToUserName" class="cmt__at">@{{ reply.replyToUserName }}</span>
                      {{ reply.content }}
                    </div>
                    <div class="cmt__foot">
                      <button
                        type="button"
                        class="cmt__like"
                        :class="{ 'is-liked': reply.isLiked }"
                        @click="toggleLike(reply)"
                      >
                        <HeartFilled v-if="reply.isLiked" />
                        <HeartOutlined v-else />
                        <i v-if="reply.likeCount">{{ formatLikeCount(reply.likeCount) }}</i>
                      </button>
                      <button type="button" class="cmt__reply-btn" @click="startReply(reply, root)">
                        回复
                      </button>
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

                    <div v-if="replyingId === String(reply.id)" class="cmt__inline-editor">
                      <a-textarea
                        v-model:value="replyDraft"
                        :auto-size="{ minRows: 1, maxRows: 3 }"
                        :maxlength="COMMENT_MAX"
                        :placeholder="`回复 @${replyTargetName}`"
                        class="cmt__inline-input"
                        @press-enter="onReplyEnter"
                      />
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

                <button
                  v-if="(root.replyCount ?? 0) > (root.replies?.length ?? 0)"
                  type="button"
                  class="cmt__expand"
                  @click="expandReplies(root)"
                >
                  <CaretDownOutlined />
                  展开更多回复
                </button>
                <button v-else type="button" class="cmt__expand" @click="collapseReplies(root)">
                  <CaretUpOutlined />
                  收起
                </button>
              </template>
            </div>
          </div>

          <div v-if="!hasMore" class="cmt__end">已经到底啦 ~</div>
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
    </div>

    <!-- ===== 底部输入区：固定在面板底部，输入框与发送按钮同一行 ===== -->
    <div class="cmt__composer">
      <a-avatar :size="32" :src="loginUser.userAvatar" class="cmt__composer-avatar">
        <template #icon><UserOutlined /></template>
      </a-avatar>

      <template v-if="loginUser.id">
        <div class="cmt__composer-box" :class="{ 'is-focus': composerFocus }">
          <textarea
            ref="composerRef"
            v-model="draft"
            class="cmt__textarea"
            rows="1"
            :maxlength="COMMENT_MAX"
            placeholder="添加评论…"
            :disabled="submitting"
            @focus="composerFocus = true"
            @blur="composerFocus = false"
            @keydown="onComposerKeydown"
            @input="autoGrow"
          />
        </div>
        <button
          type="button"
          class="cmt__send"
          :disabled="!draft.trim() || submitting"
          @click="submit"
        >
          {{ submitting ? '发送中' : '发送' }}
        </button>
      </template>

      <template v-else>
        <div class="cmt__composer-box cmt__composer-box--locked" @click="promptLogin">
          <span class="cmt__locked-placeholder">登录后发表评论…</span>
        </div>
        <button type="button" class="cmt__send" @click="promptLogin">发送</button>
      </template>
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

/** 与后端 COMMENT_MAX_LENGTH 保持一致（输入框仍受此上限约束，只是不再显示计数） */
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

/** 输入框高度自适应（原生 textarea 手动撑开，最多 3 行左右） */
const autoGrow = () => {
  const el = composerRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 84) + 'px'
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
const toggleLike = async (item: PictureCommentVO) => {
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
/*
  整体结构：头部（固定）+ 滚动区（flex:1，唯一滚动容器）+ 输入区（固定底部）。
  这样无论评论多少，输入框始终贴在面板底部，不会被列表顶走。
*/
.cmt {
  --cmt-accent: var(--accent, #40a9ff);
  --cmt-name: #4e6b8a;
  --cmt-text: #303133;
  --cmt-meta: #9aa4b2;
  --cmt-heart: #ff4d6a;
  display: flex;
  flex-direction: column;
  /* 面板自身高度上限：列表在内部滚动，输入区固定在底部。
     不设 height:100% 是因为宿主（弹窗侧栏 / 详情卡片）高度不确定，
     用 max-height 才能保证"输入框始终在最下方"且不撑破容器。 */
  max-height: min(68vh, 680px);
  min-height: 0;
}

.cmt *::selection {
  background: rgba(64, 169, 255, 0.22);
}

/* ===== 头部 ===== */
.cmt__head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
}

.cmt__head-total {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary-light);
}

.cmt__head-total b {
  margin: 0 2px;
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

/* ===== 滚动区 ===== */
.cmt__scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}

/* 主题化滚动条：默认的系统滚动条与整体风格不符 */
.cmt__scroll::-webkit-scrollbar {
  width: 6px;
}

.cmt__scroll::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.12);
  border-radius: 3px;
}

.cmt__scroll::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 0, 0, 0.2);
}

.cmt__scroll::-webkit-scrollbar-track {
  background: transparent;
}

/* ===== 列表（平铺，无气泡） ===== */
.cmt__list {
  display: flex;
  flex-direction: column;
  gap: 22px;
  padding-right: 4px;
}

.cmt__item {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.cmt__avatar {
  flex-shrink: 0;
  cursor: pointer;
  transition: transform 0.18s ease;
}

.cmt__avatar:hover {
  transform: scale(1.06);
}

.cmt__avatar--sm {
  margin-top: 2px;
}

.cmt__main {
  flex: 1;
  min-width: 0;
}

/* 昵称：主色 + 加粗，与灰色时间形成层次 */
.cmt__name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--cmt-name);
  line-height: 1.3;
  cursor: pointer;
}

.cmt__name:hover {
  color: var(--cmt-accent);
}

.cmt__name--sm {
  font-size: 13px;
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

/* 时间 · 属地 */
.cmt__meta {
  margin-top: 3px;
  font-size: 12px;
  color: var(--cmt-meta);
  line-height: 1.4;
}

.cmt__loc::before {
  content: '·';
  margin: 0 6px;
}

.cmt__text {
  margin-top: 7px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--cmt-text);
  white-space: pre-wrap;
  word-break: break-word;
}

.cmt__text--reply {
  font-size: 13.5px;
  margin-top: 5px;
}

/* 蓝色 @昵称（回复语境） */
.cmt__at {
  color: var(--cmt-accent);
  font-weight: 600;
  margin-right: 2px;
  cursor: pointer;
}

.cmt__at:hover {
  text-decoration: underline;
}

/* ===== 操作行：点赞 / 回复 / 更多 ===== */
.cmt__foot {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-top: 9px;
}

.cmt__like {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: transparent;
  padding: 0;
  font-size: 13px;
  color: var(--cmt-meta);
  cursor: pointer;
  transition: color 0.18s ease;
}

.cmt__like i {
  font-style: normal;
  font-size: 12.5px;
  font-variant-numeric: tabular-nums;
}

.cmt__like:hover {
  color: var(--cmt-heart);
}

.cmt__like.is-liked {
  color: var(--cmt-heart);
}

.cmt__reply-btn {
  border: none;
  background: transparent;
  padding: 0;
  font-size: 12.5px;
  color: var(--cmt-meta);
  cursor: pointer;
  transition: color 0.18s ease;
}

.cmt__reply-btn:hover {
  color: var(--cmt-accent);
}

.cmt__icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  padding: 0;
  font-size: 14px;
  color: var(--cmt-meta);
  cursor: pointer;
  transition: color 0.15s ease;
}

.cmt__icon-btn:hover {
  color: var(--cmt-text);
}

/* ===== 楼中楼 ===== */
.cmt__replies {
  margin-top: 12px;
  /* 与顶层正文左对齐：头像 40 + 间距 12 */
  margin-left: 52px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.cmt__item--reply {
  gap: 10px;
}

/* 展开 / 收起入口：与参考一致的「@昵称 · N」形式 */
.cmt__expand {
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: transparent;
  padding: 0;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--cmt-accent);
  cursor: pointer;
  transition: opacity 0.15s ease;
}

.cmt__expand:hover {
  opacity: 0.75;
}

/* 内联回复框 */
.cmt__inline-editor {
  margin-top: 10px;
}

.cmt__inline-input {
  border-radius: 10px;
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
  padding: 18px 0 4px;
  text-align: center;
  font-size: 12px;
  color: var(--cmt-meta);
}

/* ===== 空态 ===== */
.cmt__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 0 24px;
}

.cmt__empty-img {
  width: 72px;
  height: 72px;
  opacity: 0.9;
}

.cmt__empty-title {
  margin: 12px 0 0;
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
  padding: 12px 0 4px;
}

/* ===== 底部输入区（固定） ===== */
.cmt__composer {
  flex-shrink: 0;
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding-top: 12px;
  margin-top: 4px;
  border-top: 1px solid var(--border-color);
}

.cmt__composer-avatar {
  flex-shrink: 0;
  margin-bottom: 3px;
}

/* 输入容器：白底描边，聚焦时主色光晕 */
.cmt__composer-box {
  flex: 1;
  min-width: 0;
  background: var(--bg-card);
  border: 1.5px solid var(--border-color);
  border-radius: 20px;
  padding: 6px 14px;
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
  min-height: 38px;
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
  padding: 0;
  font-size: 14px;
  line-height: 1.6;
  font-family: inherit;
  color: var(--cmt-text);
  max-height: 84px;
}

.cmt__textarea::placeholder {
  color: var(--cmt-meta);
}

/* 发送按钮：与输入框同一行，胶囊样式 */
.cmt__send {
  flex-shrink: 0;
  height: 38px;
  padding: 0 20px;
  border: none;
  border-radius: 19px;
  background: linear-gradient(135deg, #40a9ff, #1677ff);
  color: #fff;
  font-size: 13.5px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.cmt__send:not(:disabled):hover {
  filter: brightness(1.06);
}

.cmt__send:disabled {
  background: rgba(0, 0, 0, 0.1);
  color: rgba(0, 0, 0, 0.3);
  cursor: not-allowed;
}

/* ===== 深色模式 ===== */
html.dark .cmt {
  --cmt-name: #8fb8dd;
  --cmt-text: #e8e8e8;
  --cmt-meta: #6e7681;
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
  color: var(--text-primary);
}

html.dark .cmt__scroll::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.16);
}

html.dark .cmt__inline-input {
  background: rgba(255, 255, 255, 0.06);
}

html.dark .cmt__composer-box {
  background: rgba(255, 255, 255, 0.045);
}

html.dark .cmt__composer-box.is-focus {
  background: rgba(255, 255, 255, 0.06);
}

html.dark .cmt__send:disabled {
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.3);
}
</style>
