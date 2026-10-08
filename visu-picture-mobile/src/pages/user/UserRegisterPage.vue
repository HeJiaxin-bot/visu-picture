<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { sendEmailVerifyCodeUsingPost, userRegisterUsingPost } from '@/api/userController.ts'
import { showToast } from 'vant'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

defineOptions({ name: 'UserRegisterPage' })

const router = useRouter()
const loginUserStore = useLoginUserStore()

const formData = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
  email: '',
  captcha: '',
})

const submitting = ref(false)

// ----- 邮箱验证码倒计时 -----
const countdown = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

const sendCode = async () => {
  if (!formData.email) {
    showToast('请先填写邮箱')
    return
  }
  if (countdown.value > 0) return
  try {
    const res = await sendEmailVerifyCodeUsingPost({ email: formData.email })
    if (res.data.code === 0 && res.data.data) {
      showToast({ type: 'success', message: '验证码已发送' })
      countdown.value = 60
      timer = setInterval(() => {
        countdown.value--
        if (countdown.value <= 0 && timer) {
          clearInterval(timer)
          timer = null
        }
      }, 1000)
    } else {
      showToast('发送失败，' + res.data.message)
    }
  } catch (e) {
    showToast('发送失败，请稍后重试')
  }
}

const doSubmit = async () => {
  if (
    !formData.userAccount ||
    !formData.userPassword ||
    !formData.checkPassword ||
    !formData.email ||
    !formData.captcha
  ) {
    showToast('请填写完整注册信息')
    return
  }
  if (formData.userPassword !== formData.checkPassword) {
    showToast('两次输入的密码不一致')
    return
  }
  if (!submitting.value) {
    submitting.value = true
    try {
      const res = await userRegisterUsingPost(formData)
      if (res.data.code === 0 && res.data.data) {
        showToast({ type: 'success', message: '注册成功' })
        // 注册成功后自动登录由后端完成，此处直接回首页
        await loginUserStore.fetchLoginUser()
        router.replace('/')
      } else {
        showToast('注册失败，' + res.data.message)
      }
    } catch (e) {
      showToast('注册失败，请稍后重试')
    } finally {
      submitting.value = false
    }
  }
}

const goLogin = () => {
  router.push('/user/login')
}
</script>

<template>
  <div class="register-page">
    <div class="brand-area">
      <div class="brand-name">创建账号</div>
      <div class="brand-desc">加入视界云图库，开启图片创作之旅</div>
    </div>

    <van-form @submit="doSubmit">
      <van-cell-group inset>
        <van-field
          v-model="formData.userAccount"
          label="账号"
          placeholder="请输入账号"
          clearable
        />
        <van-field
          v-model="formData.userPassword"
          type="password"
          label="密码"
          placeholder="请输入密码（至少 8 位）"
        />
        <van-field
          v-model="formData.checkPassword"
          type="password"
          label="确认密码"
          placeholder="请再次输入密码"
        />
        <van-field v-model="formData.email" label="邮箱" placeholder="用于接收验证码" clearable>
          <template #button>
            <van-button
              size="small"
              plain
              round
              type="primary"
              :disabled="countdown > 0"
              @click="sendCode"
            >
              {{ countdown > 0 ? `${countdown}s` : '发送验证码' }}
            </van-button>
          </template>
        </van-field>
        <van-field v-model="formData.captcha" label="验证码" placeholder="请输入邮箱验证码" />
      </van-cell-group>
      <div class="submit-area">
        <van-button
          round
          block
          type="primary"
          native-type="submit"
          :loading="submitting"
          loading-text="注册中..."
        >
          注册
        </van-button>
        <div class="to-login" @click="goLogin">已有账号？去登录</div>
      </div>
    </van-form>
  </div>
</template>

<style scoped>
.register-page {
  min-height: 100vh;
  padding: 0 8px;
  box-sizing: border-box;
  background: var(--bg-page);
}

.brand-area {
  padding: calc(var(--safe-top) + 56px) 24px 32px;
}

.brand-name {
  font-size: 26px;
  font-weight: 700;
  color: var(--text-1);
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

.to-login {
  margin-top: 16px;
  text-align: center;
  color: var(--brand);
  font-size: 14px;
  cursor: pointer;
}
</style>
