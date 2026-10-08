<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useLikeStore } from '@/stores/useLikeStore.ts'
import { PIC_REVIEW_STATUS_ENUM, PIC_REVIEW_STATUS_MAP } from '@/constants/picture.ts'

interface Props {
  pictures: API.PictureVO[]
  /** 是否展示编辑/删除操作（我的发布、空间管理） */
  showOp?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  showOp: false,
})

const emit = defineEmits<{
  (e: 'edit', picture: API.PictureVO): void
  (e: 'delete', picture: API.PictureVO): void
}>()

const router = useRouter()
const likeStore = useLikeStore()

// 宽高比（w/h），数据缺失时按 3:2 兜底
const getRatio = (picture: API.PictureVO) => {
  const w = picture.picWidth
  const h = picture.picHeight
  if (!w || !h || w <= 0 || h <= 0) return 3 / 2
  return w / h
}

// 双列瀑布流：按累计高度贪心分配到较矮的一列
const columns = computed(() => {
  const cols: API.PictureVO[][] = [[], []]
  const heights = [0, 0]
  for (const picture of props.pictures) {
    // 图片相对高度 + 信息区折算高度
    const cost = 1 / getRatio(picture) + 0.38
    const idx = heights[0] <= heights[1] ? 0 : 1
    cols[idx].push(picture)
    heights[idx] += cost
  }
  return cols
})

// 点击进入详情
const goDetail = (picture: API.PictureVO) => {
  if (picture.id != null) {
    router.push(`/picture/${picture.id}`)
  }
}

// 查看作者主页
const goUserPage = (picture: API.PictureVO) => {
  const userId = picture.user?.id ?? picture.userId
  if (userId != null) {
    router.push(`/user/${userId}`)
  }
}

// 点赞 / 取消点赞
const doLike = (picture: API.PictureVO) => {
  likeStore.toggle(picture)
}

// 点赞数格式化：超过 1 万显示为 x.x w
const formatCount = (count: number) => {
  if (count >= 10000) {
    return (count / 10000).toFixed(1).replace(/\.0$/, '') + 'w'
  }
  return String(count)
}

// 编辑 / 删除
const doEdit = (picture: API.PictureVO) => {
  emit('edit', picture)
}

const doDelete = (picture: API.PictureVO) => {
  emit('delete', picture)
}
</script>

<template>
  <div class="waterfall">
    <div v-for="(col, colIndex) in columns" :key="colIndex" class="waterfall-col">
      <div
        v-for="picture in col"
        :key="picture.id"
        class="pic-card"
        @click="goDetail(picture)"
      >
        <div class="pic-img" :style="{ aspectRatio: getRatio(picture) }">
          <img
            :alt="picture.name"
            :src="picture.thumbnailUrl ?? picture.url"
            loading="lazy"
            decoding="async"
          />
          <!-- AI 生成内容标识 -->
          <div v-if="picture.isAiGenerated === 1" class="ai-badge">AI</div>
          <!-- 审核状态标识 -->
          <div
            v-if="
              picture.reviewStatus != null &&
              picture.reviewStatus !== PIC_REVIEW_STATUS_ENUM.PASS
            "
            class="review-badge"
            :class="{ 'is-reject': picture.reviewStatus === PIC_REVIEW_STATUS_ENUM.REJECT }"
          >
            {{ PIC_REVIEW_STATUS_MAP[picture.reviewStatus] }}
          </div>
        </div>
        <div class="pic-info">
          <div class="pic-name ellipsis">{{ picture.name ?? '未命名图片' }}</div>
          <div class="pic-meta">
            <div class="pic-author" @click.stop="goUserPage(picture)">
              <van-image
                round
                width="16"
                height="16"
                fit="cover"
                :src="picture.user?.userAvatar"
              >
                <template #error>
                  <div class="avatar-fallback">{{ (picture.user?.userName ?? '匿')[0] }}</div>
                </template>
              </van-image>
              <span class="author-name ellipsis">{{ picture.user?.userName ?? '未知用户' }}</span>
            </div>
            <div class="pic-like" :class="{ liked: likeStore.isLiked(picture.id) }" @click.stop="doLike(picture)">
              <van-icon :name="likeStore.isLiked(picture.id) ? 'good-job' : 'good-job-o'" />
              <span>{{ formatCount(picture.likeCount ?? 0) }}</span>
            </div>
          </div>
          <!-- 操作按钮（我的发布/空间管理） -->
          <div v-if="showOp" class="pic-actions" @click.stop>
            <van-button size="small" plain round icon="edit" @click="doEdit(picture)">编辑</van-button>
            <van-button size="small" plain round type="danger" icon="delete-o" @click="doDelete(picture)">
              删除
            </van-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.waterfall {
  display: flex;
  gap: 10px;
}

.waterfall-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.pic-card {
  background: var(--bg-card);
  border-radius: var(--radius-card);
  overflow: hidden;
  cursor: pointer;
  /* 触摸反馈 */
  transition: transform 0.15s ease;
}

.pic-card:active {
  transform: scale(0.98);
}

.pic-img {
  position: relative;
  width: 100%;
  background: #f2f3f5;
  overflow: hidden;
}

.pic-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* AI 生成内容标识：右上角常显 */
.ai-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  padding: 1px 6px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  color: #fff;
  background: rgba(111, 66, 193, 0.75);
  pointer-events: none;
}

/* 审核状态标识：左上角常显 */
.review-badge {
  position: absolute;
  top: 8px;
  left: 8px;
  padding: 1px 6px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  color: #fff;
  background: rgba(250, 173, 20, 0.9);
  pointer-events: none;
}

.review-badge.is-reject {
  background: rgba(245, 63, 63, 0.9);
}

.pic-info {
  padding: 8px 10px 10px;
}

.pic-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-1);
}

.pic-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 6px;
}

.pic-author {
  display: flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
  color: var(--text-2);
}

.author-name {
  font-size: 11px;
  max-width: 72px;
}

.avatar-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--brand-weak);
  color: var(--brand);
  font-size: 10px;
  font-weight: 600;
}

.pic-like {
  display: flex;
  align-items: center;
  gap: 3px;
  flex-shrink: 0;
  font-size: 11px;
  color: var(--text-2);
}

.pic-like.liked {
  color: #ff4d6a;
  font-weight: 600;
}

.pic-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.pic-actions :deep(.van-button) {
  flex: 1;
  height: 28px;
}
</style>
