<template>
  <div id="settingsPage">
    <div class="settings-container">
      <!-- 页头 -->
      <div class="page-header">
        <a-button type="text" class="back-btn" @click="router.back()">
          <template #icon><ArrowLeftOutlined /></template>
        </a-button>
        <h2 class="page-title">设置</h2>
      </div>

      <!-- 常用功能 -->
      <div class="settings-section">
        <div class="section-label">常用功能</div>
        <div class="settings-card">
          <!-- 修改密码：点击展开表单 -->
          <div class="settings-item" @click="pwdExpanded = !pwdExpanded">
            <LockOutlined class="item-icon" style="background: #e6f4ff; color: #1677ff" />
            <div class="item-body">
              <div class="item-title">修改密码</div>
              <div class="item-desc">定期修改密码可以让账号更安全</div>
            </div>
            <CaretRightOutlined class="item-arrow" :class="{ expanded: pwdExpanded }" />
          </div>
          <!-- 修改密码表单（展开显示） -->
          <div v-if="pwdExpanded" class="pwd-form-wrap">
            <a-form :model="pwdForm" layout="vertical" @finish="handleChangePassword">
              <a-form-item label="原密码" required>
                <a-input-password v-model:value="pwdForm.userPassword" placeholder="请输入原密码" />
              </a-form-item>
              <a-form-item label="新密码" required extra="至少 8 位，建议字母 + 数字组合">
                <a-input-password v-model:value="pwdForm.newPassword" placeholder="请输入新密码" />
              </a-form-item>
              <a-form-item label="确认新密码" required>
                <a-input-password v-model:value="pwdForm.checkPassword" placeholder="请再次输入新密码" />
              </a-form-item>
              <a-space>
                <a-button type="primary" html-type="submit" :loading="pwdSaving">确认修改</a-button>
                <a-button @click="pwdExpanded = false">收起</a-button>
              </a-space>
            </a-form>
          </div>

          <!-- 深色模式：跟随全局主题 -->
          <div class="settings-item">
            <BulbOutlined class="item-icon" style="background: #f9f0ff; color: #722ed1" />
            <div class="item-body">
              <div class="item-title">深色模式</div>
              <div class="item-desc">降低屏幕亮度，夜间浏览更舒适</div>
            </div>
            <a-switch
              :checked="themeStore.isDark"
              @click="themeStore.toggleTheme()"
              aria-label="深色模式开关"
            />
          </div>
        </div>
      </div>

      <!-- 账号安全 -->
      <div class="settings-section">
        <div class="section-label">账号安全</div>
        <div class="settings-card">
          <!-- 退出登录 -->
          <div class="settings-item" @click="logoutVisible = true">
            <LogoutOutlined class="item-icon" style="background: #fff7e6; color: #fa8c16" />
            <div class="item-body">
              <div class="item-title">退出登录</div>
              <div class="item-desc">退出当前设备上的登录状态</div>
            </div>
            <RightOutlined class="item-arrow" />
          </div>
        </div>
      </div>
    </div>

    <!-- 退出登录二次确认 -->
    <a-modal v-model:open="logoutVisible" title="退出登录" :width="360" @ok="doLogout">
      <p style="margin: 0">确定要退出当前账号吗？</p>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  BulbOutlined,
  CaretRightOutlined,
  LockOutlined,
  LogoutOutlined,
  RightOutlined,
} from '@ant-design/icons-vue'
import { changePasswordUsingPost, userLogoutUsingPost } from '@/api/userController.ts'
import { useThemeStore } from '@/stores/useThemeStore'
import { useLoginUserStore } from '@/stores/useLoginUserStore'

const router = useRouter()
const themeStore = useThemeStore()
const loginUserStore = useLoginUserStore()

// ----- 修改密码（点击行展开表单） -----
const pwdExpanded = ref(false)
const pwdSaving = ref(false)
const pwdForm = reactive<API.UserChangePasswordRequest>({
  userPassword: '',
  newPassword: '',
  checkPassword: '',
})

const handleChangePassword = async () => {
  if (!pwdForm.userPassword || !pwdForm.newPassword || !pwdForm.checkPassword) {
    message.warning('请填写完整密码信息')
    return
  }
  if (pwdForm.newPassword.length < 8) {
    message.warning('新密码不能少于 8 位')
    return
  }
  if (pwdForm.newPassword !== pwdForm.checkPassword) {
    message.warning('两次输入的新密码不一致')
    return
  }
  pwdSaving.value = true
  try {
    const res = await changePasswordUsingPost({ ...pwdForm })
    if (res.data.code === 0) {
      message.success('密码修改成功')
      pwdExpanded.value = false
      pwdForm.userPassword = ''
      pwdForm.newPassword = ''
      pwdForm.checkPassword = ''
    } else {
      message.error(res.data.message ?? '密码修改失败')
    }
  } catch (e: any) {
    message.error('密码修改失败，' + (e?.message ?? ''))
  } finally {
    pwdSaving.value = false
  }
}

// ----- 退出登录 -----
const logoutVisible = ref(false)

const doLogout = async () => {
  const res = await userLogoutUsingPost()
  if (res.data.code === 0) {
    loginUserStore.setLoginUser({ userName: '未登录' })
    message.success('退出登录成功')
    await router.push('/user/login')
  } else {
    message.error('退出登录失败，' + res.data.message)
  }
}
</script>

<style scoped>
#settingsPage {
  padding: 24px 16px 40px;
}

.settings-container {
  max-width: 640px;
  margin: 0 auto;
}

/* 页头 */
.page-header {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 20px;
}

.page-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary, #171a2b);
}

html.dark .page-title {
  color: #e8eaf6;
}

/* 分组 */
.settings-section {
  margin-bottom: 24px;
}

.section-label {
  font-size: 13px;
  color: rgba(23, 26, 43, 0.45);
  margin: 0 4px 8px;
}

html.dark .section-label {
  color: rgba(232, 234, 246, 0.45);
}

.settings-card {
  background: var(--card-bg, #fff);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(23, 26, 43, 0.06);
}

html.dark .settings-card {
  background: #1f2338;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.3);
}

/* 功能行 */
.settings-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  cursor: pointer;
  transition: background 0.2s;
}

.settings-item + .settings-item {
  border-top: 1px solid rgba(23, 26, 43, 0.06);
}

html.dark .settings-item + .settings-item {
  border-top-color: rgba(232, 234, 246, 0.08);
}

.settings-item:hover {
  background: rgba(22, 119, 255, 0.04);
}

html.dark .settings-item:hover {
  background: rgba(22, 119, 255, 0.1);
}

.item-icon {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.item-body {
  flex: 1;
  min-width: 0;
}

.item-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary, #171a2b);
}

html.dark .item-title {
  color: #e8eaf6;
}

.item-desc {
  font-size: 12px;
  color: rgba(23, 26, 43, 0.45);
  margin-top: 2px;
}

html.dark .item-desc {
  color: rgba(232, 234, 246, 0.45);
}

.item-arrow {
  color: rgba(23, 26, 43, 0.25);
  font-size: 12px;
  transition: transform 0.2s;
}

html.dark .item-arrow {
  color: rgba(232, 234, 246, 0.25);
}

/* 展开态箭头旋转 */
.item-arrow.expanded {
  transform: rotate(90deg);
}

/* 修改密码表单区 */
.pwd-form-wrap {
  padding: 4px 18px 18px 72px;
  border-top: 1px dashed rgba(23, 26, 43, 0.08);
  background: rgba(22, 119, 255, 0.02);
}

html.dark .pwd-form-wrap {
  border-top-color: rgba(232, 234, 246, 0.08);
  background: rgba(22, 119, 255, 0.05);
}

/* 移动端：表单缩进收窄 */
@media (max-width: 640px) {
  .pwd-form-wrap {
    padding-left: 18px;
  }
}
</style>
