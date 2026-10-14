import myAxios from '@/request.ts'

/** 回收站图片（软删除状态） */
export interface RecyclePicture {
  id: string | number
  url?: string
  thumbnailUrl?: string
  name?: string
  picSize?: number
  picWidth?: number
  picHeight?: number
  /** 软删除时间：MyBatis-Plus 逻辑删除会刷新 updateTime，此处即删除时间 */
  updateTime?: string
  spaceId?: string | null
}

export interface RecyclePageResult {
  records: RecyclePicture[]
  total: number
}

/** 回收站：分页查询当前用户已删除的图片 */
export async function listMyRecycle(current: number, pageSize: number) {
  return (await myAxios.get('/api/picture/recycle/list', { params: { current, pageSize } })).data as {
    code: number
    data?: RecyclePageResult
    message?: string
  }
}

/** 回收站：恢复图片 */
export async function restoreRecycledPicture(id: string | number) {
  return (await myAxios.post('/api/picture/recycle/restore', { id })).data as {
    code: number
    data?: boolean
    message?: string
  }
}

/** 回收站：彻底删除（物理删除，不可恢复） */
export async function deleteRecycledPicture(id: string | number) {
  return (await myAxios.post('/api/picture/recycle/delete', { id })).data as {
    code: number
    data?: boolean
    message?: string
  }
}

/** 回收站：批量恢复（返回成功数量） */
export async function restoreRecycledBatch(ids: Array<string | number>) {
  return (await myAxios.post('/api/picture/recycle/batch/restore', ids)).data as {
    code: number
    data?: number
    message?: string
  }
}

/** 回收站：批量彻底删除（返回成功数量） */
export async function deleteRecycledBatch(ids: Array<string | number>) {
  return (await myAxios.post('/api/picture/recycle/batch/delete', ids)).data as {
    code: number
    data?: number
    message?: string
  }
}
