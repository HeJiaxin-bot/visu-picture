import myAxios from '@/request.ts'

/** 管理端全局数据看板总览 */
export interface DashboardOverview {
  totalPictures?: number
  totalUsers?: number
  totalSpaces?: number
  todayPictures?: number
  todayUsers?: number
  pendingReviews?: number
  aiGeneratedCount?: number
  uploadTrend?: Array<{ date: string; count: number }>
  topSpaces?: Array<{
    id?: string
    spaceName?: string
    totalCount?: number
    totalSize?: number
  }>
}

/** 看板总览（仅管理员） */
export async function getAdminDashboardOverview() {
  return (await myAxios.get('/api/admin/dashboard/overview')).data as {
    code: number
    data?: DashboardOverview
    message?: string
  }
}
