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
 * 分页获取我的通知（带总数，供消息中心分页器使用）
 *
 * @param category all-全部 / review-审核通知 / system-系统通知 / unread-未读
 */
export async function pageMyNotifications(params: {
  current?: number
  pageSize?: number
  category?: 'all' | 'review' | 'system' | 'unread'
}): Promise<API.BaseResponseNotificationPage> {
  return (await myAxios.get('/api/notification/list/page', { params })).data
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
 * 删除我的通知（像删除手机短信一样从列表移除，仅本人通知）
 */
export async function deleteNotification(id: number): Promise<API.BaseResponseBoolean_> {
  return (await myAxios.post('/api/notification/delete', { id })).data
}

/**
 * 批量标记已读（选择模式勾选的通知，返回实际标记条数）
 */
export async function markReadBatch(ids: number[]) {
  return (await myAxios.post('/api/notification/batch/read', ids)).data as {
    code: number
    data?: number
    message?: string
  }
}

/**
 * 批量删除通知（选择模式勾选的通知，返回实际删除条数）
 */
export async function deleteBatch(ids: number[]) {
  return (await myAxios.post('/api/notification/batch/delete', ids)).data as {
    code: number
    data?: number
    message?: string
  }
}

/**
 * 管理员发布系统消息（targetUserId 为空表示全员）
 *
 * targetUserId 同时接受 string：用户 id 是雪花 id，超出 JS 安全整数范围，
 * 必须以字符串传输，否则末位会被 Number() 抹掉而发给错误的用户。
 */
export async function sendSystemNotification(body: {
  title: string
  content?: string
  targetUserId?: number | string
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
    /** MyBatis-Plus 分页对象：records + total */
    type NotificationPage = {
      records?: NotificationVO[]
      total?: number
      current?: number
      size?: number
    }
    type BaseResponseNotificationPage = {
      code: number
      data?: NotificationPage
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
