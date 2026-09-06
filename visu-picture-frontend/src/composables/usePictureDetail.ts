import { computed, ref } from 'vue'
import { message } from 'ant-design-vue'
import { getPictureVoByIdUsingGet } from '@/api/pictureController.ts'
import { SPACE_PERMISSION_ENUM } from '@/constants/space.ts'
import { downloadImage } from '@/utils'

/**
 * 图片详情逻辑（数据拉取 + 权限判断 + 下载/分享）
 * 供图片详情弹窗 PictureDetailModal 与分享直达页 PictureDetailPage 复用，避免逻辑两份。
 */
export function usePictureDetail() {
  const loading = ref(false)
  const picture = ref<API.PictureVO>({})

  // 权限判定（基于后端下发的 permissionList）
  const canEdit = computed(() =>
    (picture.value.permissionList ?? []).includes(SPACE_PERMISSION_ENUM.PICTURE_EDIT),
  )
  const canDelete = computed(() =>
    (picture.value.permissionList ?? []).includes(SPACE_PERMISSION_ENUM.PICTURE_DELETE),
  )

  /**
   * 拉取图片详情，成功返回 true，失败返回 false
   */
  const fetchDetail = async (id: string | number) => {
    loading.value = true
    try {
      const res = await getPictureVoByIdUsingGet({ id })
      if (res.data.code === 0 && res.data.data) {
        picture.value = res.data.data
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

  // 分享链接（指向详情页，供分享模块使用）
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