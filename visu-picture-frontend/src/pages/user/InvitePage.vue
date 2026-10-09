<template>
  <div id="invitePage">
    <h2 class="page-title">会员中心</h2>

    <!-- 激活码兑换：默认收起，点击展开 -->
    <div class="exchange-toggle">
      <a-button type="link" class="toggle-btn" @click="showExchange = !showExchange">
        <GiftOutlined class="toggle-icon" />
        激活码兑换会员
        <DownOutlined class="toggle-arrow" :class="{ open: showExchange }" />
      </a-button>
    </div>
    <div class="exchange-wrap" :class="{ open: showExchange }">
      <div class="exchange-inner">
        <div class="invite-card exchange-card">
          <div class="card-label">激活码兑换会员</div>
          <div class="code-row exchange-row" :class="{ shake }" @animationend="shake = false">
            <a-input
              v-model:value="vipCode"
              class="exchange-input"
              placeholder="请输入 8 位激活码"
              :maxlength="8"
              allow-clear
              @pressEnter="handleExchange"
            />
            <a-button
              type="primary"
              class="copy-btn"
              :loading="exchanging"
              @click="handleExchange"
            >
              立即兑换
            </a-button>
          </div>
          <div class="card-hint">兑换成功即开通 12 个月会员；激活码由平台发放，每个仅可使用一次。</div>
        </div>
      </div>
    </div>

    <!-- 兑换成功权益弹窗 -->
    <a-modal
      v-model:open="successVisible"
      :footer="null"
      :width="400"
      class="vip-success-modal"
      centered
    >
      <div class="success-body">
        <div class="success-crown">
          <CrownFilled />
        </div>
        <div class="success-title">兑换成功！</div>
        <div class="success-desc">会员已开通 12 个月，以下权益立即生效</div>
        <ul class="success-perks">
          <li>
            <CheckCircleOutlined class="perk-icon" />
            <span>专业版团队空间自助创建</span>
          </li>
          <li>
            <CheckCircleOutlined class="perk-icon" />
            <span>空间容量与批量管理额度提升</span>
          </li>
          <li>
            <CheckCircleOutlined class="perk-icon" />
            <span>会员专属标识与邀请积分加持</span>
          </li>
        </ul>
        <a-button type="primary" block size="large" class="success-btn" @click="successVisible = false">
          开心收下
        </a-button>
      </div>
    </a-modal>

    <!-- 我的专属邀请码 -->
    <div class="invite-card">
      <div class="card-label">我的专属邀请码</div>
      <div class="code-row">
        <div class="invite-code">{{ info.inviteCode || '········' }}</div>
        <a-button type="primary" class="copy-btn" :loading="copying" @click="handleCopy">
          复制链接
        </a-button>
      </div>
      <div class="card-hint">每成功邀请 1 人得 12 个月会员，累计邀请 3 人升级永久会员。</div>
      <div class="progress-line">
        <template v-if="info.permanentMember">
          <CheckCircleOutlined class="ok-icon" />
          恭喜！已获得永久会员
        </template>
        <template v-else>
          <template v-if="info.memberUnlocked">
            <CheckCircleOutlined class="ok-icon" />
            会员已解锁<span v-if="info.vipExpireTime" class="expire">（有效期至 {{ formatDate(info.vipExpireTime) }}）</span>，
          </template>
          已成功邀请 <b>{{ info.successCount ?? 0 }}</b> 人，再邀请
          <b>{{ info.remainCount ?? info.unlockTarget ?? 3 }}</b> 人升级永久会员
        </template>
      </div>
    </div>

    <!-- 邀请明细 / 排行榜 -->
    <a-tabs v-model:activeKey="activeTab" centered class="invite-tabs">
      <a-tab-pane key="list" tab="邀请明细">
        <div class="list-card">
          <template v-if="records.length > 0">
            <div v-for="item in records" :key="item.userId" class="record-item">
              <a-avatar :src="item.userAvatar" size="36">
                <template #icon><UserOutlined /></template>
              </a-avatar>
              <div class="record-info">
                <div class="record-name">{{ item.userName || '视界用户' }}</div>
                <div class="record-time">{{ formatDate(item.createTime) }} 加入</div>
              </div>
              <div class="record-tag">+1</div>
            </div>
          </template>
          <div v-else class="empty-box">
            <CreditCardOutlined class="empty-icon" />
            <div class="empty-text">暂无邀请记录，快去邀请好友吧！</div>
          </div>
          <div class="list-footer">- 已经到底啦 -</div>
        </div>
      </a-tab-pane>
      <a-tab-pane key="rank" tab="排行榜">
        <div class="list-card">
          <template v-if="ranks.length > 0">
            <div v-for="(item, index) in ranks" :key="item.userId" class="record-item">
              <div class="rank-num" :class="`top-${index + 1}`">{{ index + 1 }}</div>
              <a-avatar :src="item.userAvatar" size="36">
                <template #icon><UserOutlined /></template>
              </a-avatar>
              <div class="record-info">
                <div class="record-name">{{ item.userName || '视界用户' }}</div>
              </div>
              <div class="rank-count">{{ item.count }} 人</div>
            </div>
          </template>
          <div v-else class="empty-box">
            <TrophyOutlined class="empty-icon" />
            <div class="empty-text">还没有人上榜，成为第一个邀请达人吧！</div>
          </div>
          <div class="list-footer">- 仅展示前 10 名 -</div>
        </div>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  CheckCircleOutlined,
  CreditCardOutlined,
  CrownFilled,
  DownOutlined,
  GiftOutlined,
  TrophyOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { copyText, getInviteInfo, getInviteRank } from '@/api/invite.ts'
import type { InviteInfoVO, InviteRankVO, InviteRecordVO } from '@/api/invite.ts'
import { exchangeVipUsingPost } from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

const loginUserStore = useLoginUserStore()

const info = reactive<InviteInfoVO>({})
const records = ref<InviteRecordVO[]>([])
const ranks = ref<InviteRankVO[]>([])
const activeTab = ref<'list' | 'rank'>('list')

const copying = ref(false)

// ----- 激活码兑换 -----
const showExchange = ref(false)
const vipCode = ref('')
const exchanging = ref(false)
// 校验失败时触发输入框抖动动画
const shake = ref(false)
// 兑换成功后的权益确认弹窗
const successVisible = ref(false)

// 激活码格式：8 位字母数字（与平台发放规则一致）
const CODE_PATTERN = /^[A-Za-z0-9]{8}$/

const triggerShake = () => {
  shake.value = false
  // 下一帧重置以便重复触发动画
  requestAnimationFrame(() => {
    shake.value = true
  })
}

const handleExchange = async () => {
  const code = vipCode.value.trim()
  if (!code) {
    message.warning('请先输入激活码')
    triggerShake()
    return
  }
  if (!CODE_PATTERN.test(code)) {
    message.error('激活码格式不正确，应为 8 位字母数字组合')
    triggerShake()
    return
  }
  exchanging.value = true
  try {
    const res = await exchangeVipUsingPost({ vipCode: code })
    if (res.data.code === 0 && res.data.data) {
      vipCode.value = ''
      // 刷新登录态，同步导航栏与个人主页的会员标识
      await loginUserStore.fetchLoginUser()
      // 弹出权益确认弹窗，比纯 message 提示更有仪式感
      successVisible.value = true
    } else {
      message.error(res.data.message ?? '兑换失败，请核对激活码')
      triggerShake()
    }
  } catch (e: any) {
    message.error('兑换失败，' + (e?.message ?? '请稍后重试'))
    triggerShake()
  } finally {
    exchanging.value = false
  }
}

const formatDate = (value?: string) => {
  if (!value) return '-'
  const d = new Date(value)
  if (isNaN(d.getTime())) return '-'
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

const fetchInfo = async () => {
  try {
    const res = await getInviteInfo()
    if (res.code === 0 && res.data) {
      Object.assign(info, res.data)
      records.value = res.data.inviteList ?? []
    }
  } catch {
    message.error('邀请信息加载失败，请稍后重试')
  }
}

const fetchRank = async () => {
  try {
    const res = await getInviteRank()
    if (res.code === 0 && res.data) {
      ranks.value = res.data
    }
  } catch {
    // 排行榜加载失败不打断页面
  }
}

const handleCopy = async () => {
  if (!info.inviteCode) return
  copying.value = true
  try {
    const link = `${location.origin}/user/register?invite=${info.inviteCode}`
    const ok = await copyText(link)
    if (ok) {
      message.success('邀请链接已复制，快去分享吧')
    } else {
      message.warning('复制失败，请手动复制：' + info.inviteCode)
    }
  } finally {
    copying.value = false
  }
}

onMounted(() => {
  fetchInfo()
  fetchRank()
})
</script>

<style scoped>
#invitePage {
  max-width: 760px;
  margin: 0 auto;
}

.page-title {
  text-align: center;
  margin: 8px 0 24px;
  font-size: 26px;
}

/* 邀请码卡片 */
.invite-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.08);
  padding: 28px 32px;
  text-align: center;
}

.card-label {
  color: var(--text-secondary);
  font-size: 15px;
  margin-bottom: 16px;
}

/* 激活码兑换：隐藏式入口 + 平滑展开 */
.exchange-toggle {
  text-align: center;
  margin-bottom: 16px;
}

.toggle-btn {
  font-size: 14px;
  padding: 0 10px;
}

.toggle-icon {
  margin-right: 6px;
  color: var(--accent);
}

.toggle-arrow {
  margin-left: 6px;
  font-size: 11px;
  color: var(--text-secondary);
  transition: transform 0.25s ease;
}

.toggle-arrow.open {
  transform: rotate(180deg);
}

.exchange-wrap {
  display: grid;
  grid-template-rows: 0fr;
  opacity: 0;
  overflow: hidden;
  transition:
    grid-template-rows 0.3s ease,
    opacity 0.25s ease,
    margin-bottom 0.3s ease;
  margin-bottom: 0;
}

.exchange-wrap.open {
  grid-template-rows: 1fr;
  opacity: 1;
  margin-bottom: 20px;
}

.exchange-inner {
  min-height: 0;
  overflow: hidden;
}

/* 激活码兑换卡片 */
.exchange-card {
  margin-bottom: 0;
}

.exchange-row {
  gap: 12px;
}

.exchange-input {
  flex: 1;
  height: 44px;
  font-size: 16px;
  letter-spacing: 1px;
}

/* 校验失败抖动动画 */
@keyframes code-shake {
  0%, 100% { transform: translateX(0); }
  15% { transform: translateX(-9px); }
  30% { transform: translateX(8px); }
  45% { transform: translateX(-6px); }
  60% { transform: translateX(5px); }
  75% { transform: translateX(-3px); }
  90% { transform: translateX(2px); }
}

.exchange-row.shake {
  animation: code-shake 0.5s ease;
}

.code-row {
  display: flex;
  align-items: center;
  gap: 14px;
  background: rgba(64, 169, 255, 0.06);
  border: 1px dashed rgba(64, 169, 255, 0.35);
  border-radius: 12px;
  padding: 14px 18px;
}

.invite-code {
  flex: 1;
  text-align: left;
  font-size: 26px;
  font-weight: 800;
  letter-spacing: 5px;
  color: var(--accent);
  font-family: 'Segoe UI', 'PingFang SC', sans-serif;
}

.copy-btn {
  border-radius: 999px;
  padding-inline: 22px;
}

.card-hint {
  color: var(--text-secondary);
  font-size: 13px;
  margin-top: 14px;
}

.progress-line {
  margin-top: 10px;
  font-size: 14px;
  color: var(--text-secondary);
}

.progress-line b {
  color: var(--accent);
}

.progress-line .ok-icon {
  color: #52c41a;
  margin-right: 4px;
}

.progress-line .expire {
  color: var(--text-secondary);
  font-size: 13px;
}

/* Tabs 与列表卡片 */
.invite-tabs {
  margin-top: 24px;
}

.list-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.08);
  padding: 10px 20px;
  min-height: 260px;
  display: flex;
  flex-direction: column;
}

.record-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 4px;
  border-bottom: 1px solid var(--border-color);
}

.record-info {
  flex: 1;
}

.record-name {
  font-weight: 600;
  color: var(--text-primary-light);
}

.record-time {
  font-size: 12px;
  color: var(--text-disabled);
  margin-top: 2px;
}

.record-tag {
  color: #52c41a;
  font-weight: 700;
}

.rank-num {
  width: 24px;
  text-align: center;
  font-weight: 800;
  color: var(--text-secondary);
}

.rank-num.top-1 {
  color: #faad14;
}

.rank-num.top-2 {
  color: #8c8c8c;
}

.rank-num.top-3 {
  color: #d48806;
}

.rank-count {
  color: var(--accent);
  font-weight: 600;
}

/* 空状态 */
.empty-box {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 30px 0;
}

.empty-icon {
  font-size: 40px;
  color: var(--text-disabled);
}

.empty-text {
  color: var(--text-secondary);
}

.list-footer {
  text-align: center;
  color: var(--text-disabled);
  font-size: 12px;
  padding: 12px 0 6px;
}

/* ===== 深色模式 ===== */
html.dark .invite-card,
html.dark .list-card {
  background: rgba(45, 45, 45, 0.9);
  border-color: var(--border-color);
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.35);
}

html.dark .card-label,
html.dark .card-hint {
  color: rgba(179, 179, 179, 0.6);
}

html.dark .code-row {
  background: rgba(64, 169, 255, 0.12);
  border-color: rgba(64, 169, 255, 0.45);
}

html.dark .exchange-input {
  background: rgba(255, 255, 255, 0.06);
}

html.dark .invite-code {
  color: var(--accent);
}

html.dark .progress-line {
  color: rgba(179, 179, 179, 0.85);
}

html.dark .progress-line b {
  color: var(--accent);
}

html.dark .progress-line .expire {
  color: rgba(179, 179, 179, 0.55);
}

html.dark .record-item {
  border-bottom-color: rgba(255, 255, 255, 0.06);
}

html.dark .record-name {
  color: var(--text-primary);
}

html.dark .record-time {
  color: rgba(179, 179, 179, 0.45);
}

html.dark .rank-num {
  color: rgba(179, 179, 179, 0.5);
}

html.dark .empty-icon {
  color: rgba(179, 179, 179, 0.25);
}

html.dark .empty-text {
  color: rgba(179, 179, 179, 0.5);
}

html.dark .list-footer {
  color: rgba(179, 179, 179, 0.35);
}
</style>

<!-- 兑换成功弹窗：Modal 渲染在 body 下，scoped 不生效，需全局块 -->
<style>
.vip-success-modal .success-body {
  text-align: center;
  padding: 12px 8px 4px;
}

.vip-success-modal .success-crown {
  width: 72px;
  height: 72px;
  margin: 0 auto 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 34px;
  color: #d48806;
  background: linear-gradient(135deg, #fffbe6, #ffe58f);
  box-shadow: 0 8px 24px rgba(250, 173, 20, 0.35);
  animation: crown-pop 0.45s cubic-bezier(0.34, 1.56, 0.64, 1) both;
}

@keyframes crown-pop {
  from {
    opacity: 0;
    transform: scale(0.4) rotate(-20deg);
  }
  to {
    opacity: 1;
    transform: scale(1) rotate(0);
  }
}

.vip-success-modal .success-title {
  font-size: 22px;
  font-weight: 700;
  color: #212121;
}

.vip-success-modal .success-desc {
  margin-top: 6px;
  font-size: 13px;
  color: #6b6b6b;
}

.vip-success-modal .success-perks {
  list-style: none;
  margin: 20px 0 24px;
  padding: 16px 18px;
  text-align: left;
  background: #fafafa;
  border-radius: 12px;
}

.vip-success-modal .success-perks li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 5px 0;
  font-size: 14px;
  color: #333;
}

.vip-success-modal .perk-icon {
  color: #52c41a;
}

.vip-success-modal .success-btn {
  border: none;
  background: linear-gradient(135deg, #40a9ff, #1677ff);
  box-shadow: 0 6px 16px rgba(22, 119, 255, 0.35);
}

/* 深色模式 */
html.dark .vip-success-modal .success-crown {
  color: #ffd666;
  background: linear-gradient(135deg, rgba(250, 173, 20, 0.2), rgba(250, 173, 20, 0.35));
  box-shadow: 0 8px 24px rgba(250, 173, 20, 0.25);
}

html.dark .vip-success-modal .success-title {
  color: #f0f0f0;
}

html.dark .vip-success-modal .success-desc {
  color: #b3b3b3;
}

html.dark .vip-success-modal .success-perks {
  background: rgba(255, 255, 255, 0.06);
}

html.dark .vip-success-modal .success-perks li {
  color: #e0e0e0;
}
</style>
