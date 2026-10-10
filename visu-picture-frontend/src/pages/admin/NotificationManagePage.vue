<template>
  <div id="notificationManagePage">
    <h2 class="page-title">发布消息</h2>
    <p class="page-sub">向全部用户或指定用户推送站内消息，用户会在导航栏铃铛处收到提醒</p>

    <div class="publish-layout">
      <!-- 左侧：编辑表单 -->
      <div class="form-card">
        <div class="card-head">
          <BellOutlined class="head-icon" />
          <span>消息内容</span>
        </div>

        <a-form :model="form" layout="vertical" class="publish-form">
          <a-form-item label="消息标题" required>
            <a-input
              v-model:value="form.title"
              :maxlength="40"
              show-count
              placeholder="例如：会员权益升级公告"
              size="large"
            />
          </a-form-item>

          <a-form-item label="消息内容">
            <a-textarea
              v-model:value="form.content"
              :rows="5"
              :maxlength="200"
              show-count
              placeholder="请输入要通知用户的内容，例如：即日起签到积分翻倍，连续签到 7 天额外赠送会员天数。"
            />
          </a-form-item>

          <a-form-item label="接收对象">
            <a-radio-group v-model:value="targetType" button-style="solid">
              <a-radio-button value="all">全部用户</a-radio-button>
              <a-radio-button value="one">指定用户</a-radio-button>
            </a-radio-group>
          </a-form-item>

          <a-form-item v-if="targetType === 'one'" label="用户 ID（视界号）">
            <a-input
              v-model:value="form.targetUserId"
              placeholder="请输入接收用户的视界号，例如：2095099727417675778"
              allow-clear
            />
            <div class="field-hint">用户可在「用户中心」复制自己的视界号</div>
          </a-form-item>

          <a-form-item>
            <a-button
              type="primary"
              size="large"
              block
              class="send-btn"
              :loading="sending"
              @click="handleSend"
            >
              <SendOutlined />
              发布消息
            </a-button>
          </a-form-item>
        </a-form>
      </div>

      <!-- 右侧：实时预览 -->
      <div class="preview-card">
        <div class="card-head">
          <EyeOutlined class="head-icon" />
          <span>用户端预览</span>
        </div>
        <div class="preview-body">
          <!-- 铃铛面板预览 -->
          <div class="bell-preview">
            <div class="bell-head">
              <span class="bell-title">消息通知</span>
              <span class="bell-readall">全部已读</span>
            </div>
            <div class="bell-item unread">
              <div class="bell-thumb">
                <BellOutlined />
              </div>
              <div class="bell-body">
                <div class="bell-item-title">
                  {{ form.title || '消息标题' }}
                  <span class="bell-tag">系统</span>
                </div>
                <div class="bell-item-desc">{{ form.content || '消息内容将显示在这里' }}</div>
                <div class="bell-item-time">刚刚</div>
              </div>
              <span class="bell-dot"></span>
            </div>
            <div class="bell-foot">查看全部消息</div>
          </div>

          <div class="preview-hint">
            <InfoCircleOutlined />
            发布后将在接收用户的铃铛角标 +1，并在消息中心显示为「系统」类型
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  BellOutlined,
  EyeOutlined,
  InfoCircleOutlined,
  SendOutlined,
} from '@ant-design/icons-vue'
import { sendSystemNotification } from '@/api/notification.ts'

const form = reactive<{ title: string; content: string; targetUserId?: string }>({
  title: '',
  content: '',
  targetUserId: undefined,
})

const targetType = ref<'all' | 'one'>('all')
const sending = ref(false)

const handleSend = async () => {
  const title = form.title.trim()
  if (!title) {
    message.warning('请填写消息标题')
    return
  }
  let targetUserId: number | undefined
  if (targetType.value === 'one') {
    const raw = (form.targetUserId ?? '').trim()
    if (!raw) {
      message.warning('请填写接收用户的视界号')
      return
    }
    if (!/^\d+$/.test(raw)) {
      message.warning('视界号格式不正确，应为纯数字')
      return
    }
    targetUserId = Number(raw)
  }
  sending.value = true
  try {
    const res = await sendSystemNotification({
      title,
      content: form.content.trim(),
      targetUserId,
    })
    if (res.code === 0) {
      const count = res.data ?? 0
      message.success(`发布成功，已送达 ${count} 位用户`)
      form.title = ''
      form.content = ''
    } else {
      message.error(res.message ?? '发布失败')
    }
  } catch (e: any) {
    message.error('发布失败，' + (e?.message ?? '请稍后重试'))
  } finally {
    sending.value = false
  }
}
</script>

<style scoped>
#notificationManagePage {
  max-width: 1100px;
  margin: 0 auto;
}

.page-title {
  text-align: center;
  margin: 8px 0 6px;
  font-size: 26px;
}

.page-sub {
  text-align: center;
  margin: 0 0 24px;
  font-size: 13px;
  color: var(--text-secondary);
}

.publish-layout {
  display: grid;
  grid-template-columns: 1.35fr 1fr;
  gap: 20px;
  align-items: start;
}

/* 卡片通用 */
.form-card,
.preview-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  box-shadow: var(--card-shadow);
  padding: 20px 24px;
}

.card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 14px;
  margin-bottom: 18px;
  border-bottom: 1px solid var(--border-color);
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary-light);
}

.head-icon {
  color: var(--accent);
}

.field-hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-disabled);
}

.send-btn {
  border: none;
  background: linear-gradient(135deg, #40a9ff, #1677ff);
  box-shadow: 0 6px 18px rgba(22, 119, 255, 0.35);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.send-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 26px rgba(22, 119, 255, 0.45);
}

/* 预览区 */
.preview-body {
  padding-top: 4px;
}

.bell-preview {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  overflow: hidden;
  background: var(--bg-elevated);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
}

.bell-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid var(--border-color);
}

.bell-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary-light);
}

.bell-readall {
  font-size: 12px;
  color: var(--accent);
}

.bell-item {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px;
}

.bell-item.unread {
  background: rgba(64, 169, 255, 0.05);
}

.bell-thumb {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 17px;
  color: #722ed1;
  background: linear-gradient(135deg, rgba(114, 46, 209, 0.12), rgba(114, 46, 209, 0.2));
}

.bell-body {
  flex: 1;
  min-width: 0;
}

.bell-item-title {
  font-size: 13.5px;
  font-weight: 600;
  color: #1677ff;
  line-height: 19px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bell-tag {
  display: inline-block;
  margin-left: 6px;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 600;
  line-height: 15px;
  color: #722ed1;
  background: rgba(114, 46, 209, 0.1);
  vertical-align: 1px;
}

.bell-item-desc {
  margin-top: 3px;
  font-size: 12px;
  line-height: 17px;
  color: var(--text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}

.bell-item-time {
  margin-top: 4px;
  font-size: 11px;
  color: var(--text-disabled);
}

.bell-dot {
  position: absolute;
  top: 14px;
  right: 12px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ff4d4f;
}

.bell-foot {
  padding: 9px 0;
  text-align: center;
  font-size: 13px;
  color: var(--accent);
  border-top: 1px solid var(--border-color);
}

.preview-hint {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 14px;
  font-size: 12px;
  line-height: 18px;
  color: var(--text-disabled);
}

/* 窄屏：预览置底 */
@media (max-width: 900px) {
  .publish-layout {
    grid-template-columns: 1fr;
  }
}

/* 深色模式 */
html.dark .form-card,
html.dark .preview-card {
  box-shadow: var(--card-shadow);
}

html.dark .bell-preview {
  background: rgba(45, 45, 45, 0.9);
}

html.dark .card-head,
html.dark .bell-title,
html.dark .bell-item-title {
  color: var(--text-primary);
}

html.dark .bell-item-title {
  color: #69b9ff;
}
</style>
