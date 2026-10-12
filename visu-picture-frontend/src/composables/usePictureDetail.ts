import { computed, ref } from 'vue'
import { message } from 'ant-design-vue'
import { getPictureVoByIdUsingGet } from '@/api/pictureController.ts'
import { SPACE_PERMISSION_ENUM } from '@/constants/space.ts'
import { downloadImage, firstQueryValue } from '@/utils'
import { useLikeStore } from '@/stores/useLikeStore.ts'

/**
 * 图片详情逻辑（数据拉取 + 权限判断 + 下载/分享）
 * 供图片详情弹窗 PictureDetailModal 与分享直达页 PictureDetailPage 复用，避免逻辑两份。
 */
export function usePictureDetail() {
  const loading = ref(false)
  const picture = ref<API.PictureVO>({})
  const likeStore = useLikeStore()

  // 权限判定（基于后端下发的 permissionList）
  const canEdit = computed(() =>
    (picture.value.permissionList ?? []).includes(SPACE_PERMISSION_ENUM.PICTURE_EDIT),
  )
  const canDelete = computed(() =>
    (picture.value.permissionList ?? []).includes(SPACE_PERMISSION_ENUM.PICTURE_DELETE),
  )

  /**
   * 拉取图片详情，成功返回 true，失败返回 false
   *
   * ⚠️ id 必须原样传递（字符串），**不能**转成 Number：
   * 后端 JsonConfig 把 Long 序列化为 String 正是为了规避 JS 精度丢失
   *（雪花 id 如 2100942816594628609 超出 Number.MAX_SAFE_INTEGER，
   *  Number("2100942816594628609") === 2100942816594628600，末位被抹掉，接口会返回 404）。
   * 这里只做「数组取值 + 空值判断」，保持原始字符串形态。
   */
  const fetchDetail = async (id: string | number) => {
    loading.value = true
    try {
      const pictureId = firstQueryValue(id)
      if (!pictureId) {
        message.error('图片 id 不合法')
        return false
      }
      const res = await getPictureVoByIdUsingGet({ id: pictureId })
      if (res.data.code === 0 && res.data.data) {
        picture.value = res.data.data
        // 用服务端下发的 isLiked / isFavorited 校准本地状态，
        // 避免换设备或清缓存后点赞/收藏图标显示错误
        likeStore.syncFromServer([res.data.data])
        return true
      }
      message.error('获取图片详情失败，' + res.data.message)
      return false
    } catch (e: any) {
      message.error('获取图片详情失败：' + e.message)
      return false
    } finally {
      loading.value = false
    }
  }

  // 下载原图
  const download = () => {
    downloadImage(picture.value.url)
  }

  // 分享链接（指向详情页，供不需要后端分享码的场景复用）
  const shareLink = ref<string>()
  const setShareLink = () => {
    shareLink.value = `${window.location.protocol}//${window.location.host}/picture/${picture.value.id}`
  }

  return {
    loading,
    picture,
    canEdit,
    canDelete,
    fetchDetail,
    download,
    shareLink,
    setShareLink,
  }
}