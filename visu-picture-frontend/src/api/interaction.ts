import myAxios from '@/request.ts'

/**
 * 实体 id 类型：number 或 string
 *
 * ⚠️ 后端 JsonConfig 把 Long 序列化为 String（规避 JS 精度丢失），
 * 因此从接口拿到的 id 实际是字符串形态，如 "2100942816594628609"。
 * 类型上同时接受 number 是为了兼容本地计算出的临时值与旧调用点，
 * 但**运行时一律按字符串透传，禁止 Number() 转换**（末位会失真）。
 */
export type EntityId = number | string

/** 评论视图对象 */
export interface PictureCommentVO {
  id?: EntityId
  pictureId?: EntityId
  userId?: EntityId
  content?: string
  createTime?: string
  user?: API.UserVO
  /** 当前登录用户是否有权删除该评论（本人或管理员） */
  canDelete?: boolean
}

/** 分享链接视图对象 */
export interface PictureShareVO {
  id?: EntityId
  shareCode?: string
  /** 完整分享地址（后端配了 site-url 则为绝对地址，否则为 /share/{code}） */
  shareUrl?: string
  pictureId?: EntityId
  expireTime?: string
  viewCount?: number
  createTime?: string
}

/**
 * 点赞 / 取消点赞（按用户去重，重复调用不会重复计数）
 * @returns 操作后的点赞数
 */
export async function likePicture(pictureId: EntityId, isLike: boolean) {
  return (await myAxios.post('/api/picture/like', { pictureId, isLike })).data as API.BaseResponseInt_
}

/**
 * 收藏 / 取消收藏
 * @returns 操作后的收藏数
 */
export async function favoritePicture(pictureId: EntityId, isFavorite: boolean) {
  return (await myAxios.post('/api/picture/favorite', { pictureId, isFavorite })).data as API.BaseResponseInt_
}

/**
 * 查询当前用户对某图片的点赞/收藏状态（未登录返回 false/false）
 */
export async function queryInteractionState(pictureId: EntityId) {
  return (await myAxios.get('/api/picture/interaction/state', { params: { pictureId } })).data as {
    code: number
    data?: { isLiked?: boolean; isFavorited?: boolean }
    message?: string
  }
}

/** 我的收藏（按收藏时间倒序） */
export async function pageMyFavorites(params: { current?: number; pageSize?: number }) {
  return (await myAxios.get('/api/picture/my/favorite', { params })).data as API.BaseResponsePagePictureVO_
}

/** 我的点赞（按点赞时间倒序） */
export async function pageMyLikes(params: { current?: number; pageSize?: number }) {
  return (await myAxios.get('/api/picture/my/like', { params })).data as API.BaseResponsePagePictureVO_
}

/**
 * 发表评论
 */
export async function addComment(body: { pictureId: EntityId; content: string }) {
  return (await myAxios.post('/api/picture/comment/add', body)).data as {
    code: number
    data?: PictureCommentVO
    message?: string
  }
}

/** 分页查询某图片的评论（无需登录） */
export async function pageComments(params: {
  pictureId: EntityId
  current?: number
  pageSize?: number
}) {
  return (await myAxios.get('/api/picture/comment/list', { params })).data as {
    code: number
    data?: { records?: PictureCommentVO[]; total?: number; current?: number; size?: number }
    message?: string
  }
}

/** 删除评论（仅本人或管理员） */
export async function deleteComment(id: EntityId) {
  return (await myAxios.post('/api/picture/comment/delete', { id })).data as API.BaseResponseBoolean_
}

/** 记录一次浏览（同一张图只保留一条记录） */
export async function recordView(pictureId: EntityId) {
  return (await myAxios.post('/api/picture/view/record', { pictureId })).data as API.BaseResponseBoolean_
}

/** 我的最近查看 */
export async function pageMyViewHistory(params: { current?: number; pageSize?: number }) {
  return (await myAxios.get('/api/picture/view/history', { params })).data as API.BaseResponsePagePictureVO_
}

/** 清空我的浏览历史 */
export async function clearMyViewHistory() {
  return (await myAxios.post('/api/picture/view/clear')).data as API.BaseResponseInt_
}

/**
 * 为图片创建分享链接（仅所有者或管理员）
 * @param expireDays 有效期天数；为空或 <=0 表示永久有效
 */
export async function createShare(body: { pictureId: EntityId; expireDays?: number }) {
  return (await myAxios.post('/api/share/create', body)).data as {
    code: number
    data?: PictureShareVO
    message?: string
  }
}

/** 查询某图片当前的分享链接（无有效链接时 data 为 null） */
export async function getShareByPictureId(pictureId: EntityId) {
  return (await myAxios.get('/api/share/get', { params: { pictureId } })).data as {
    code: number
    data?: PictureShareVO | null
    message?: string
  }
}

/** 撤销图片的分享链接 */
export async function revokeShare(pictureId: EntityId) {
  return (await myAxios.post('/api/share/revoke', { pictureId })).data as API.BaseResponseInt_
}

/** 我创建的全部分享链接 */
export async function listMyShares() {
  return (await myAxios.get('/api/share/my/list')).data as {
    code: number
    data?: PictureShareVO[]
    message?: string
  }
}

/**
 * 通过分享码公开访问图片（无需登录）
 */
export async function getSharedPicture(shareCode: string) {
  return (await myAxios.get(`/api/share/public/${encodeURIComponent(shareCode)}`)).data as API.BaseResponsePictureVO_
}

/** 分页查询操作审计日志（仅管理员） */
export async function pageAuditLogs(params: {
  module?: string
  action?: string
  userId?: number
  startTime?: string
  endTime?: string
  current?: number
  pageSize?: number
}) {
  return (await myAxios.get('/api/audit/list/page', { params })).data as {
    code: number
    data?: { records?: AuditLogVO[]; total?: number; current?: number; size?: number }
    message?: string
  }
}

/** 审计日志视图对象 */
export interface AuditLogVO {
  id?: EntityId
  userId?: number
  userAccount?: string
  module?: string
  action?: string
  targetType?: string
  targetId?: number
  detail?: string
  ip?: string
  userAgent?: string
  success?: number
  errorMessage?: string
  costMs?: number
  createTime?: string
}