<template>
  <div id="notificationManagePage">
    <!-- 页头：左对齐，与其他管理页一致 -->
    <div class="page-head">
      <h2 class="page-title">发布消息</h2>
      <p class="page-sub">
        向全部用户或指定用户推送站内消息，用户会在导航栏铃铛处收到提醒
      </p>
    </div>

    <div class="publish-layout">
      <!-- 左侧：编辑表单 -->
      <div class="panel">
        <div class="panel-head">
          <span class="panel-title">消息内容</span>
        </div>

        <a-form :model="form" layout="vertical" class="publish-form">
          <a-form-item label="消息标题" required>
            <a-input
              v-model:value="form.title"
              :maxlength="40"
              show-count
              placeholder="例如：会员权益升级公告"
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
            <span class="target-hint">
              {{ targetType === 'all' ? '推送给平台所有注册用户' : '仅推送给一个指定用户' }}
            </span>
          </a-form-item>

          <a-form-item v-if="targetType === 'one'" label="用户视界号">
            <a-input
              v-model:value="form.targetUserId"
              placeholder="例如：2095099727417675778"
              allow-clear
            />
            <div class="field-hint">
              用户可在「用户中心」复制自己的视界号
            </div>
          </a-form-item>

          <div class="form-actions">
            <a-button
              type="primary"
              class="send-btn"
              :loading="sending"
              :disabled="!canSend"
              @click="handleSend"
            >
              <SendOutlined />
              发布消息
            </a-button>
            <span class="send-hint" v-if="!form.title.trim()">填写标题后可发布</span>
          </div>
        </a-form>
      </div>

      <!-- 右侧：真实铃铛面板结构预览 -->
      <div class="panel panel--preview">
        <div class="panel-head">
          <span class="panel-title">用户端效果</span>
          <span class="panel-note">发布后即时生效</span>
        </div>

        <!-- 与 GlobalHeader 铃铛面板同构：浅底 + 描边，不叠加阴影 -->
        <div class="bell">
          <div class="bell-head">
            <span class="bell-head-title">消息通知</span>
            <span class="bell-readall">全部已读</span>
          </div>

          <div class="bell-item">
            <div class="bell-thumb">
              <BellOutlined />
            </div>
            <div class="bell-body">
              <div class="bell-title-row">
                <span class="bell-item-title">{{ form.title || '消息标题' }}</span>
                <span class="bell-tag">系统</span>
              </div>
              <div class="bell-desc" :class="{ 'is-placeholder': !form.content }">
                {{ form.content || '消息内容将显示在这里' }}
              </div>
              <div class="bell-time">刚刚</div>
            </div>
            <span class="bell-dot" />
          </div>

          <div class="bell-foot">查看全部消息</div>
        </div>

        <dl class="effect-list">
          <div class="effect-row">
            <dt>铃铛角标</dt>
            <dd>未读数 +1</dd>
          </div>
          <div class="effect-row">
            <dt>消息中心</dt>
            <dd>归类为「系统通知」</dd>
          </div>
          <div class="effect-row">
            <dt>送达范围</dt>
            <dd>{{ targetType === 'all' ? '全部注册用户' : '指定用户' }}</dd>
          </div>
        </dl>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { BellOutlined, SendOutlined } from '@ant-design/icons-vue'
import { sendSystemNotification } from '@/api/notification.ts'

const form = reactive<{ title: string; content: string; targetUserId?: string }>({
  title: '',
  content: '',
  targetUserId: undefined,
})

const targetType = ref<'all' | 'one'>('all')
const sending = ref(false)

/** 标题必填：用它控制按钮可用态，比点击后弹警告更早反馈 */
const canSend = computed(() => {
  if (!form.title.trim()) return false
  if (targetType.value === 'one' && !(form.targetUserId ?? '').trim()) return false
  return true
})

const handleSend = async () => {
  const title = form.title.trim()
  if (!title) {
    message.warning('请填写消息标题')
    return
  }
  // 视界号即用户 id（雪花 id），必须保持字符串：
  // 它超出 Number.MAX_SAFE_INTEGER，Number() 会把末位抹掉，
  // 导致消息发给一个不存在的用户（或静默发给 0 人）。
  let targetUserId: string | undefined
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
    targetUserId = raw
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
  max-width: 1200px;
  margin: 0 auto 24px;
  padding: 0 16px;
}

/* ===== 页头：左对齐（与审计日志等管理页一致） ===== */
.page-head {
  margin: 8px 0 20px;
}

.page-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary-light);
}

.page-sub {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--text-secondary);
}

/* ===== 布局：表单为主，预览为辅 ===== */
.publish-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
  gap: 24px;
  align-items: start;
}

/* ===== 面板：无阴影，靠描边与留白分隔 ===== */
.panel {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 14px;
  padding: 20px 24px 24px;
  /* 两栏等高：右栏内容较少，拉伸填满避免底部参差 */
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.panel-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 14px;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}

/* 预览列：说明表贴到面板底部 */
.panel--preview .effect-list {
  margin-top: auto;
  padding-top: 18px;
}

.panel-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary-light);
}

.panel-note {
  font-size: 12px;
  color: var(--text-disabled);
}

/* ===== 表单 ===== */
.publish-form :deep(.ant-form-item-label > label) {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary-light);
}

.publish-form :deep(.ant-form-item) {
  margin-bottom: 18px;
}

.target-hint {
  margin-left: 12px;
  font-size: 12px;
  color: var(--text-disabled);
}

.field-hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-disabled);
}

.form-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 4px;
}

/* 主按钮：实色，无渐变无投影 */
.send-btn {
  border: none;
  background: var(--link);
  font-weight: 600;
}

.send-btn:not(:disabled):hover {
  background: var(--accent);
}

.send-hint {
  font-size: 12px;
  color: var(--text-disabled);
}

/* ===== 预览：与真实铃铛面板同构 ===== */
.bell {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  overflow: hidden;
  background: var(--bg-body);
}

.bell-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid var(--border-color);
}

.bell-head-title {
  font-size: 13.5px;
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

.bell-thumb {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  color: #722ed1;
  background: rgba(114, 46, 209, 0.1);
}

.bell-body {
  flex: 1;
  min-width: 0;
}

.bell-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.bell-item-title {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-primary-light);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bell-tag {
  flex-shrink: 0;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 600;
  line-height: 16px;
  color: #722ed1;
  background: rgba(114, 46, 209, 0.1);
}

.bell-desc {
  margin-top: 4px;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}

/* 未填写内容时用更浅的灰，明确这是占位而非真实内容 */
.bell-desc.is-placeholder {
  color: var(--text-disabled);
  font-style: italic;
}

.bell-time {
  margin-top: 4px;
  font-size: 11px;
  color: var(--text-disabled);
}

.bell-dot {
  position: absolute;
  top: 16px;
  right: 14px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #ff4d4f;
}

.bell-foot {
  padding: 9px 0;
  text-align: center;
  font-size: 12.5px;
  color: var(--accent);
  border-top: 1px solid var(--border-color);
}

/* ===== 效果说明：规格表形式 ===== */
.effect-list {
  margin: 18px 0 0;
  display: flex;
  flex-direction: column;
}

.effect-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 0;
  border-bottom: 1px solid var(--border-color);
}

.effect-row:last-child {
  border-bottom: none;
}

.effect-row dt {
  font-size: 12.5px;
  color: var(--text-disabled);
}

.effect-row dd {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary-light);
}

/* ===== 窄屏：预览置底 ===== */
@media (max-width: 900px) {
  .publish-layout {
    grid-template-columns: 1fr;
  }
}

/* ===== 深色模式 ===== */
html.dark .page-title,
html.dark .panel-title,
html.dark .bell-head-title,
html.dark .bell-item-title {
  color: var(--text-primary);
}

html.dark .bell {
  background: rgba(0, 0, 0, 0.18);
}

html.dark .bell-thumb {
  background: rgba(114, 46, 209, 0.2);
  color: #b794f6;
}

html.dark .bell-tag {
  background: rgba(114, 46, 209, 0.2);
  color: #b794f6;
}
</style>
