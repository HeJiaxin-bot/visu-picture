<template>
  <div id="userLoginPage">
    <div class="auth-card">
      <div class="brand">
        <img class="logo" src="../../assets/logo-full.svg" alt="visu 视界云图库" />
      </div>
      <h2 class="title">用户登录</h2>
      <div class="desc">加入视界云图库，开启你的创作之旅</div>
      <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
        <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入邮箱' }]">
          <a-input v-model:value="formState.userAccount" size="large" placeholder="请输入邮箱" />
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
        <a-form-item>
          <a-checkbox v-model:checked="agree">
            我已阅读并同意
            <RouterLink to="/agreement?type=user" target="_blank">《用户协议》</RouterLink>
            和
            <RouterLink to="/agreement?type=privacy" target="_blank">《隐私政策》</RouterLink>
          </a-checkbox>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" size="large" style="width: 100%">
            登录
          </a-button>
        </a-form-item>
      </a-form>
      <div class="tips">
        没有账号？
        <RouterLink to="/user/register">去注册</RouterLink>
      </div>
    </div>
  </div>
</template>
<script lang="ts" setup>
import { reactive, ref } from 'vue'
import { userLoginUsingPost } from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { message } from 'ant-design-vue'
import router from '@/router' // 用于接受表单输入的值

// 用于接受表单输入的值
const formState = reactive<API.UserLoginRequest>({
  userAccount: '',
  userPassword: '',
})

// 协议勾选（登录前必须勾选）
const agree = ref(false)

const loginUserStore = useLoginUserStore()

/**
 * 提交表单
 * @param values
 */
const handleSubmit = async (values: any) => {
  // 校验协议勾选
  if (!agree.value) {
    message.warning('请先阅读并同意《用户协议》和《隐私政策》')
    return
  }
  const res = await userLoginUsingPost(values)
  // 登录成功，把登录态保存到全局状态中
  if (res.data.code === 0 && res.data.data) {
    await loginUserStore.fetchLoginUser()
    message.success('登录成功')
    router.push({
      path: '/',
      replace: true,
    })
  } else {
    message.error('登录失败，' + res.data.message)
  }
}
</script>

<style scoped>
#userLoginPage {
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
  background: var(--bg-card);
  border-radius: 16px;
  border: 1px solid var(--border-color);
  box-shadow: var(--card-shadow);
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
  color: var(--text-secondary);
  margin-bottom: 28px;
}

.tips {
  color: var(--text-secondary);
  text-align: center;
  font-size: 13px;
  margin-bottom: 8px;
}
</style>
