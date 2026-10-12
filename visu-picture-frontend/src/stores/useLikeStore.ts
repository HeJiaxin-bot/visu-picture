import { defineStore } from 'pinia'
import { favoritePicture, likePicture } from '@/api/interaction.ts'
import { message } from 'ant-design-vue'

const LIKE_STORAGE_KEY = 'picture-liked-ids'
const FAVORITE_STORAGE_KEY = 'picture-favorited-ids'

/**
 * 读取本地缓存（仅作为后端状态尚未返回时的乐观占位）
 *
 * 注意：这里缓存的是「上一次已知的点赞/收藏结果」，属于**展示层的降级手段**，
 * 不再作为唯一事实来源。服务端会在图片 VO 上直接下发 isLiked / isFavorited，
 * 前端收到后用服务端值覆盖本地缓存 —— 这样换设备、清缓存、多标签页都不会状态错乱。
 */
function readLocal(key: string): (string | number)[] {
  try {
    const raw = localStorage.getItem(key)
    return raw ? (JSON.parse(raw) as (string | number)[]) : []
  } catch {
    return []
  }
}

/**
 * 图片互动状态：点赞 + 收藏
 *
 * 设计要点：
 * - 后端是唯一事实来源（VO 上的 isLiked / isFavorited）；
 * - 点击时先乐观更新 UI，请求返回后用服务端计数纠正；
 * - 请求失败回滚到点击前的状态；
 * - 后端已做按用户去重，因此重复点击是安全的（不会把计数刷高）。
 */
export const useLikeStore = defineStore('pictureLikes', {
  state: () => ({
    likedIds: readLocal(LIKE_STORAGE_KEY),
    favoritedIds: readLocal(FAVORITE_STORAGE_KEY),
  }),
  getters: {
    isLiked: (state) => (id?: string | number) => {
      if (id == null) return false
      return state.likedIds.some((v) => String(v) === String(id))
    },
    isFavorited: (state) => (id?: string | number) => {
      if (id == null) return false
      return state.favoritedIds.some((v) => String(v) === String(id))
    },
  },
  actions: {
    persistLikes() {
      localStorage.setItem(LIKE_STORAGE_KEY, JSON.stringify(this.likedIds))
    },
    persistFavorites() {
      localStorage.setItem(FAVORITE_STORAGE_KEY, JSON.stringify(this.favoritedIds))
    },
    /**
     * 用服务端下发的状态同步本地：列表/详情接口返回的 VO 带 isLiked / isFavorited
     */
    syncFromServer(pictures: API.PictureVO[] | undefined) {
      if (!pictures?.length) return
      let likedChanged = false
      let favoriteChanged = false
      for (const p of pictures) {
        if (p.id == null) continue
        if (p.isLiked != null) {
          const has = this.isLiked(p.id)
          if (p.isLiked && !has) {
            this.likedIds = [...this.likedIds, p.id]
            likedChanged = true
          } else if (!p.isLiked && has) {
            this.likedIds = this.likedIds.filter((v) => String(v) !== String(p.id))
            likedChanged = true
          }
        }
        if (p.isFavorited != null) {
          const has = this.isFavorited(p.id)
          if (p.isFavorited && !has) {
            this.favoritedIds = [...this.favoritedIds, p.id]
            favoriteChanged = true
          } else if (!p.isFavorited && has) {
            this.favoritedIds = this.favoritedIds.filter((v) => String(v) !== String(p.id))
            favoriteChanged = true
          }
        }
      }
      if (likedChanged) this.persistLikes()
      if (favoriteChanged) this.persistFavorites()
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
      this.persistLikes()
      try {
        const res = await likePicture(id, !liked)
        if (res.code === 0 && res.data != null) {
          picture.likeCount = res.data
          picture.isLiked = !liked
          return res.data
        }
        // 后端拒绝：回滚
        picture.likeCount = baseCount
        this.likedIds = liked
          ? [...this.likedIds, id]
          : this.likedIds.filter((v) => String(v) !== String(id))
        this.persistLikes()
        message.error(res.message ?? '操作失败')
        return null
      } catch (e: any) {
        // 回滚
        picture.likeCount = baseCount
        this.likedIds = liked
          ? [...this.likedIds, id]
          : this.likedIds.filter((v) => String(v) !== String(id))
        this.persistLikes()
        message.error(e?.message ?? '请先登录后再点赞')
        return null
      }
    },
    /** 切换收藏状态，返回收藏后的数量 */
    async toggleFavorite(picture: API.PictureVO): Promise<number | null> {
      const id = picture.id
      if (id == null) return null
      const favorited = this.isFavorited(id)
      const baseCount = picture.favoriteCount ?? 0
      picture.favoriteCount = favorited ? Math.max(baseCount - 1, 0) : baseCount + 1
      this.favoritedIds = favorited
        ? this.favoritedIds.filter((v) => String(v) !== String(id))
        : [...this.favoritedIds, id]
      this.persistFavorites()
      try {
        const res = await favoritePicture(id, !favorited)
        if (res.code === 0 && res.data != null) {
          picture.favoriteCount = res.data
          picture.isFavorited = !favorited
          return res.data
        }
        picture.favoriteCount = baseCount
        this.favoritedIds = favorited
          ? [...this.favoritedIds, id]
          : this.favoritedIds.filter((v) => String(v) !== String(id))
        this.persistFavorites()
        message.error(res.message ?? '操作失败')
        return null
      } catch (e: any) {
        picture.favoriteCount = baseCount
        this.favoritedIds = favorited
          ? [...this.favoritedIds, id]
          : this.favoritedIds.filter((v) => String(v) !== String(id))
        this.persistFavorites()
        message.error(e?.message ?? '请先登录后再收藏')
        return null
      }
    },
    /** 登出时清空本地缓存，避免下一个登录用户看到上一个用户的状态 */
    reset() {
      this.likedIds = []
      this.favoritedIds = []
      localStorage.removeItem(LIKE_STORAGE_KEY)
      localStorage.removeItem(FAVORITE_STORAGE_KEY)
    },
  },
})