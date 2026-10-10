import myAxios from '@/request.ts'

export interface NotificationVO {
  id?: number
  /** 通知类型：review-审核结果 / system-系统公告 / interact-互动 */
  type?: string
  title?: string
  content?: string
  /** 关联图片 id（可跳转图片详情） */
  picId?: number
  picThumbnail?: string
  /** 是否已读：0-未读 1-已读 */
  isRead?: number
  createTime?: string
}

/**
 * 分页获取我的通知
 */
export async function listMyNotifications(params: {
  current?: number
  pageSize?: number
  onlyUnread?: boolean
}): Promise<API.BaseResponseNotificationList> {
  return (await myAxios.get('/api/notification/list', { params })).data
}

/**
 * 我的未读通知数量
 */
export async function countMyUnread(): Promise<API.BaseResponseLong> {
  return (await myAxios.get('/api/notification/unread/count')).data
}

/**
 * 标记单条通知为已读
 */
export async function markNotificationRead(id: number): Promise<API.BaseResponseBoolean_> {
  return (await myAxios.post('/api/notification/read', { id })).data
}

/**
 * 全部标记已读
 */
export async function markAllNotificationRead(): Promise<API.BaseResponseBoolean_> {
  return (await myAxios.post('/api/notification/read/all')).data
}

/**
 * 管理员发布系统消息（targetUserId 为空表示全员）
 */
export async function sendSystemNotification(body: {
  title: string
  content?: string
  targetUserId?: number
}): Promise<API.BaseResponseInteger> {
  return (await myAxios.post('/api/notification/send', body)).data
}

// 与后端 BaseResponse 结构对齐
declare global {
  namespace API {
    type BaseResponseNotificationList = {
      code: number
      data?: NotificationVO[]
      message?: string
    }
    type BaseResponseLong = {
      code: number
      data?: number
      message?: string
    }
    type BaseResponseInteger = {
      code: number
      data?: number
      message?: string
    }
  }
}
