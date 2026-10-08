<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { updateMyInfoUsingPost, uploadAvatarUsingPost } from '@/api/userController.ts'
import { showToast } from 'vant'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

defineOptions({ name: 'UserProfilePage' })

const router = useRouter()
const loginUserStore = useLoginUserStore()

const formData = reactive({
  userName: '',
  userProfile: '',
})

onMounted(async () => {
  if (!loginUserStore.loginUser.id) {
    await loginUserStore.fetchLoginUser()
  }
  if (!loginUserStore.loginUser.id) {
    showToast('请先登录')
    router.replace('/user/login?redirect=' + encodeURIComponent('/user/profile'))
    return
  }
  formData.userName = loginUserStore.loginUser.userName ?? ''
  formData.userProfile = loginUserStore.loginUser.userProfile ?? ''
})

// ----- 头像上传 -----
const avatarInputRef = ref<HTMLInputElement>()
const avatarUploading = ref(false)

const doPickAvatar = () => {
  avatarInputRef.value?.click()
}

const onAvatarChange = async (e: Event) => {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  avatarUploading.value = true
  try {
    const res = await uploadAvatarUsingPost({}, file)
    if (res.data.code === 0 && res.data.data) {
      loginUserStore.setLoginUser(res.data.data)
      showToast({ type: 'success', message: '头像已更新' })
    } else {
      showToast('头像上传失败，' + res.data.message)
    }
  } catch (err) {
    showToast('头像上传失败，请稍后重试')
  } finally {
    avatarUploading.value = false
  }
}

// ----- 保存资料 -----
const submitting = ref(false)

const doSave = async () => {
  if (!formData.userName) {
    showToast('请输入昵称')
    return
  }
  if (submitting.value) return
  submitting.value = true
  try {
    const res = await updateMyInfoUsingPost({
      userName: formData.userName,
      userProfile: formData.userProfile,
    })
    if (res.data.code === 0 && res.data.data) {
      showToast({ type: 'success', message: '保存成功' })
      await loginUserStore.fetchLoginUser()
      setTimeout(() => router.back(), 800)
    } else {
      showToast('保存失败，' + res.data.message)
    }
  } catch (err) {
    showToast('保存失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.replace('/mine')
  }
}
</script>

<template>
  <div class="profile-page">
    <van-nav-bar title="个人资料" left-arrow @click-left="goBack" />

    <!-- 头像 -->
    <div class="avatar-row" @click="doPickAvatar">
      <span class="avatar-label">头像</span>
      <div class="avatar-right">
        <van-image round width="56" height="56" fit="cover" :src="loginUserStore.loginUser.userAvatar">
          <template #error>
            <div class="avatar-fallback">{{ (loginUserStore.loginUser.userName ?? '用')[0] }}</div>
          </template>
          <template #loading>
            <van-loading v-if="avatarUploading" size="20" />
          </template>
        </van-image>
        <van-icon name="arrow" color="#94a3b8" />
      </div>
      <input
        ref="avatarInputRef"
        type="file"
        accept="image/*"
        style="display: none"
        @change="onAvatarChange"
      />
    </div>

    <!-- 资料表单 -->
    <van-cell-group inset class="form-group">
      <van-field
        v-model="formData.userName"
        label="昵称"
        placeholder="请输入昵称"
        required
        maxlength="20"
        clearable
      />
      <van-field
        v-model="formData.userProfile"
        label="个性签名"
        type="textarea"
        rows="2"
        autosize
        maxlength="100"
        placeholder="介绍一下自己"
      />
      <van-field label="账号" :model-value="loginUserStore.loginUser.userAccount ?? '-'" readonly />
    </van-cell-group>

    <div class="submit-area">
      <van-button round block type="primary" :loading="submitting" loading-text="保存中..." @click="doSave">
        保存
      </van-button>
    </div>
  </div>
</template>

<style scoped>
.profile-page {
  min-height: 100vh;
  background: var(--bg-page);
  padding-bottom: calc(32px + var(--safe-bottom));
}

.profile-page :deep(.van-nav-bar) {
  padding-top: var(--safe-top);
}

.avatar-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 12px 16px 0;
  padding: 12px 16px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  cursor: pointer;
}

.avatar-label {
  font-size: 15px;
  color: var(--text-1);
}

.avatar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.avatar-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--brand-weak);
  color: var(--brand);
  font-size: 20px;
  font-weight: 600;
}

.form-group {
  margin-top: 12px;
  border-radius: var(--radius-card);
  overflow: hidden;
}

.submit-area {
  margin-top: 24px;
  padding: 0 16px;
}

.submit-area :deep(.van-button) {
  height: 44px;
  font-size: 16px;
}
</style>
