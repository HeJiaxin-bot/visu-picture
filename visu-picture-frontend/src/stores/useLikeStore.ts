import { defineStore } from 'pinia'
import { likePictureUsingPost } from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'

const STORAGE_KEY = 'picture-liked-ids'

/**
 * 图片点赞状态：localStorage 记录本人点赞过的图片 id，乐观更新
 */
export const useLikeStore = defineStore('pictureLikes', {
  state: () => ({
    likedIds: JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]') as (string | number)[],
  }),
  getters: {
    isLiked: (state) => (id?: string | number) => {
      if (id == null) return false
      return state.likedIds.some((v) => String(v) === String(id))
    },
  },
  actions: {
    persist() {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.likedIds))
    },
    /** 切换点赞状态，返回点赞后的数量；未登录报错时抛出异常由调用方处理 */
    async toggle(picture: API.PictureVO): Promise<number | null> {
      const id = picture.id
      if (id == null) return null
      const liked = this.isLiked(id)
      const baseCount = picture.likeCount ?? 0
      // 乐观更新
      picture.likeCount = liked ? Math.max(baseCount - 1, 0) : baseCount + 1
      this.likedIds = liked
        ? this.likedIds.filter((v) => String(v) !== String(id))
        : [...this.likedIds, id]
      this.persist()
      try {
        const res = await likePictureUsingPost({ pictureId: id, isLike: !liked })
        if (res.data.code === 0 && res.data.data != null) {
          picture.likeCount = res.data.data
          return res.data.data
        }
        // 后端拒绝：回滚
        picture.likeCount = baseCount
        this.likedIds = liked ? [...this.likedIds, id] : this.likedIds.filter((v) => String(v) !== String(id))
        this.persist()
        message.error(res.data.message ?? '操作失败')
        return null
      } catch (e: any) {
        // 回滚
        picture.likeCount = baseCount
        this.likedIds = liked ? [...this.likedIds, id] : this.likedIds.filter((v) => String(v) !== String(id))
        this.persist()
        message.error(e?.message ?? '请先登录后再点赞')
        return null
      }
    },
  },
})
