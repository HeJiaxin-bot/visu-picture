<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { userLoginUsingPost } from '@/api/userController.ts'
import { showToast } from 'vant'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

defineOptions({ name: 'UserLoginPage' })

const router = useRouter()
const route = useRoute()
const loginUserStore = useLoginUserStore()

const formData = reactive<API.UserLoginRequest>({
  userAccount: '',
  userPassword: '',
})

const submitting = ref(false)

const doSubmit = async () => {
  if (!formData.userAccount || !formData.userPassword) {
    showToast('请填写账号和密码')
    return
  }
  if (!submitting.value) {
    submitting.value = true
    try {
      const res = await userLoginUsingPost(formData)
      if (res.data.code === 0 && res.data.data) {
        loginUserStore.setLoginUser(res.data.data)
        showToast({ type: 'success', message: '登录成功' })
        // 回跳来源页；无来源则返回上一页
        const redirect = route.query.redirect as string | undefined
        if (redirect) {
          router.replace(decodeURIComponent(redirect))
        } else {
          router.replace('/')
        }
      } else {
        showToast('登录失败，' + res.data.message)
      }
    } catch (e) {
      showToast('登录失败，请稍后重试')
    } finally {
      submitting.value = false
    }
  }
}

const goRegister = () => {
  router.push('/user/register')
}
</script>

<template>
  <div class="login-page">
    <div class="brand-area">
      <div class="brand-name">视界云图库</div>
      <div class="brand-desc">登录后即可上传和管理你的图片</div>
    </div>

    <van-form class="login-form" @submit="doSubmit">
      <van-cell-group inset>
        <van-field
          v-model="formData.userAccount"
          name="userAccount"
          label="账号"
          placeholder="请输入账号"
          :rules="[{ required: true, message: '请输入账号' }]"
          clearable
        />
        <van-field
          v-model="formData.userPassword"
          type="password"
          name="userPassword"
          label="密码"
          placeholder="请输入密码"
          :rules="[{ required: true, message: '请输入密码' }]"
        />
      </van-cell-group>
      <div class="submit-area">
        <van-button
          round
          block
          type="primary"
          native-type="submit"
          :loading="submitting"
          loading-text="登录中..."
        >
          登录
        </van-button>
        <div class="to-register" @click="goRegister">没有账号？去注册</div>
      </div>
    </van-form>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  padding: 0 8px;
  box-sizing: border-box;
  background: var(--bg-page);
}

.brand-area {
  padding: calc(var(--safe-top) + 72px) 24px 40px;
}

.brand-name {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-1);
  letter-spacing: 1px;
}

.brand-desc {
  margin-top: 8px;
  font-size: 14px;
  color: var(--text-2);
}

.submit-area {
  margin-top: 24px;
  padding: 0 16px;
}

.submit-area :deep(.van-button) {
  height: 44px;
  font-size: 16px;
}

.to-register {
  margin-top: 16px;
  text-align: center;
  color: var(--brand);
  font-size: 14px;
  cursor: pointer;
}
</style>
