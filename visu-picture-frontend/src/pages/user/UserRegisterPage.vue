<template>
  <div id="userRegisterPage">
    <div class="auth-card">
      <div class="brand">
        <img class="logo" src="../../assets/logo-full.svg" alt="visu 视界云图库" />
      </div>
      <h2 class="title">创建账户</h2>
      <div class="desc">加入视界云图库</div>
      <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
        <a-form-item
          name="email"
          :rules="[
            { required: true, message: '请输入邮箱' },
            { type: 'email', message: '邮箱格式不正确' },
          ]"
        >
          <a-input v-model:value="formState.email" size="large" placeholder="请输入邮箱" />
        </a-form-item>
        <a-form-item
          name="captcha"
          :rules="[{ required: true, message: '请输入验证码' }]"
        >
          <a-input-group compact>
            <a-input
              v-model:value="formState.captcha"
              size="large"
              style="width: calc(100% - 130px)"
              placeholder="邮箱验证码"
              maxlength="6"
            />
            <a-button
              size="large"
              type="default"
              :disabled="sending || countdown > 0"
              style="width: 130px"
              @click="handleSendCode"
            >
              {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
            </a-button>
          </a-input-group>
        </a-form-item>
        <a-form-item
          name="userPassword"
          :rules="[
            { required: true, message: '请输入密码' },
            { min: 8, message: '密码长度不能小于 8 位' },
          ]"
        >
          <a-input-password
            v-model:value="formState.userPassword"
            size="large"
            placeholder="请输入密码"
          />
        </a-form-item>
        <a-form-item
          name="checkPassword"
          :rules="[
            { required: true, message: '请输入确认密码' },
            { min: 8, message: '确认密码长度不能小于 8 位' },
          ]"
        >
          <a-input-password
            v-model:value="formState.checkPassword"
            size="large"
            placeholder="请再次输入密码"
          />
        </a-form-item>
        <a-form-item name="inviteCode">
          <a-input
            v-model:value="formState.inviteCode"
            size="large"
            placeholder="邀请码（选填）"
            allow-clear
          />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" size="large" style="width: 100%">
            注册
          </a-button>
        </a-form-item>
      </a-form>
      <div class="tips">
        已有账户？
        <RouterLink to="/user/login">立即登录</RouterLink>
      </div>
    </div>
  </div>
</template>
<script lang="ts" setup>
import { onUnmounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  sendEmailVerifyCodeUsingPost,
  userRegisterUsingPost,
} from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import router from '@/router' // 用于接受表单输入的值

const route = useRoute()

// 用于接受表单输入的值（邀请链接携带 ?invite=xxx 时自动填入）
const formState = reactive<API.UserRegisterRequest>({
  email: '',
  captcha: '',
  userPassword: '',
  checkPassword: '',
  inviteCode: (route.query.invite as string) || '',
})

// 协议勾选（注册前必须勾选）
const agree = ref(false)

// 发送验证码 / 倒计时
const sending = ref(false)
const countdown = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

// 邮箱格式校验（与后端一致）
const EMAIL_REGEX = /^[\w.%+-]+@[\w.-]+\.[A-Za-z]{2,}$/

const handleSendCode = async () => {
  const email = formState.email?.trim()
  if (!email) {
    message.warning('请先输入邮箱')
    return
  }
  if (!EMAIL_REGEX.test(email)) {
    message.warning('邮箱格式不正确')
    return
  }
  sending.value = true
  try {
    const res = await sendEmailVerifyCodeUsingPost({ email })
    if (res.data.code === 0) {
      message.success('验证码已发送，请查收邮箱')
      countdown.value = 60
      if (timer) clearInterval(timer)
      timer = setInterval(() => {
        countdown.value--
        if (countdown.value <= 0 && timer) {
          clearInterval(timer)
          timer = null
        }
      }, 1000)
    } else {
      message.error(res.data.message ?? '验证码发送失败')
    }
  } catch (e: any) {
    message.error('验证码发送失败，' + (e?.message ?? '请稍后重试'))
  } finally {
    sending.value = false
  }
}

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

/**
 * 提交表单
 * @param values
 */
const handleSubmit = async (values: any) => {
  // 校验两次输入的密码是否一致
  if (values.userPassword !== values.checkPassword) {
    message.error('两次输入的密码不一致')
    return
  }
  const res = await userRegisterUsingPost({
    email: values.email?.trim(),
    captcha: values.captcha?.trim(),
    userPassword: values.userPassword,
    checkPassword: values.checkPassword,
    inviteCode: formState.inviteCode?.trim() || undefined,
  })
  // 注册成功，跳转到登录页面
  if (res.data.code === 0 && res.data.data) {
    message.success('注册成功')
    router.push({
      path: '/user/login',
      replace: true,
    })
  } else {
    message.error('注册失败，' + res.data.message)
  }
}
</script>

<style scoped>
#userRegisterPage {
  display: flex;
  justify-content: center;
  align-items: center;
  /* 视口高度减去顶栏(64) + 底栏(约52) + 内容区上下留白(84)，让卡片垂直居中于可视区域 */
  min-height: calc(100vh - 200px);
  padding: 24px 0;
}

.auth-card {
  width: 400px;
  max-width: 100%;
  background: #fff;
  border-radius: 16px;
  border: 1px solid #eceff7;
  box-shadow: 0 14px 40px rgba(37, 55, 120, 0.1);
  padding: 40px 36px 28px;
}

.brand {
  text-align: center;
  margin-bottom: 16px;
}

.brand .logo {
  height: 56px;
}

.title {
  text-align: center;
  margin-bottom: 8px;
  font-size: 24px;
}

.desc {
  text-align: center;
  color: rgba(35, 44, 86, 0.55);
  margin-bottom: 28px;
}

.tips {
  color: rgba(35, 44, 86, 0.55);
  text-align: center;
  font-size: 13px;
  margin-bottom: 8px;
}
</style>
