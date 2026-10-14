import myAxios from '@/request.ts'

/** 热门搜索词（公开图库搜索统计，Redis ZSet） */
export async function getHotSearchKeywords(limit = 10) {
  return (await myAxios.get('/api/picture/search/hot', { params: { limit } })).data as {
    code: number
    data?: string[]
    message?: string
  }
}
