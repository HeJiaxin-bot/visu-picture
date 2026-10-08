import myAxios from '@/request.ts'

export interface InviteRecordVO {
  userId?: number
  userName?: string
  userAvatar?: string
  createTime?: string
}

export interface InviteRankVO {
  userId?: number
  userName?: string
  userAvatar?: string
  count?: number
}

export interface InviteInfoVO {
  inviteCode?: string
  inviteList?: InviteRecordVO[]
  successCount?: number
  unlockTarget?: number
  remainCount?: number
  vipExpireTime?: string
  memberUnlocked?: boolean
  permanentMember?: boolean
}

/**
 * 获取我的邀请计划信息
 */
export async function getInviteInfo(): Promise<API.BaseResponseInviteInfo> {
  return (await myAxios.get('/api/user/invite/info')).data
}

/**
 * 邀请排行榜
 */
export async function getInviteRank(): Promise<API.BaseResponseInviteRank> {
  return (await myAxios.get('/api/user/invite/rank')).data
}

// 与后端 BaseResponse 结构对齐
declare global {
  namespace API {
    type BaseResponseInviteInfo = {
      code: number
      data?: InviteInfoVO
      message?: string
    }
    type BaseResponseInviteRank = {
      code: number
      data?: InviteRankVO[]
      message?: string
    }
  }
}

/**
 * 复制文本到剪贴板（兼容非 HTTPS 环境：http 站点下无 navigator.clipboard）
 */
export async function copyText(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
      return true
    }
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(textarea)
    return ok
  } catch {
    return false
  }
}
