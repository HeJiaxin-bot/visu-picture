<template>
  <div id="invitePage">
    <h2 class="page-title">邀请计划</h2>

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
  TrophyOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { copyText, getInviteInfo, getInviteRank } from '@/api/invite.ts'
import type { InviteInfoVO, InviteRankVO, InviteRecordVO } from '@/api/invite.ts'

const info = reactive<InviteInfoVO>({})
const records = ref<InviteRecordVO[]>([])
const ranks = ref<InviteRankVO[]>([])
const activeTab = ref<'list' | 'rank'>('list')

const copying = ref(false)

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
  background: #fff;
  border: 1px solid #eceff7;
  border-radius: 16px;
  box-shadow: 0 6px 24px rgba(37, 55, 120, 0.08);
  padding: 28px 32px;
  text-align: center;
}

.card-label {
  color: rgba(35, 44, 86, 0.6);
  font-size: 15px;
  margin-bottom: 16px;
}

.code-row {
  display: flex;
  align-items: center;
  gap: 14px;
  background: rgba(61, 90, 245, 0.06);
  border: 1px dashed rgba(61, 90, 245, 0.35);
  border-radius: 12px;
  padding: 14px 18px;
}

.invite-code {
  flex: 1;
  text-align: left;
  font-size: 26px;
  font-weight: 800;
  letter-spacing: 5px;
  color: #3d5af5;
  font-family: 'Segoe UI', 'PingFang SC', sans-serif;
}

.copy-btn {
  border-radius: 999px;
  padding-inline: 22px;
}

.card-hint {
  color: rgba(35, 44, 86, 0.55);
  font-size: 13px;
  margin-top: 14px;
}

.progress-line {
  margin-top: 10px;
  font-size: 14px;
  color: rgba(35, 44, 86, 0.8);
}

.progress-line b {
  color: #3d5af5;
}

.progress-line .ok-icon {
  color: #52c41a;
  margin-right: 4px;
}

.progress-line .expire {
  color: rgba(35, 44, 86, 0.55);
  font-size: 13px;
}

/* Tabs 与列表卡片 */
.invite-tabs {
  margin-top: 24px;
}

.list-card {
  background: #fff;
  border: 1px solid #eceff7;
  border-radius: 16px;
  box-shadow: 0 6px 24px rgba(37, 55, 120, 0.06);
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
  border-bottom: 1px solid rgba(35, 44, 86, 0.06);
}

.record-info {
  flex: 1;
}

.record-name {
  font-weight: 600;
  color: #26283a;
}

.record-time {
  font-size: 12px;
  color: rgba(35, 44, 86, 0.45);
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
  color: rgba(35, 44, 86, 0.5);
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
  color: #3d5af5;
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
  color: rgba(35, 44, 86, 0.25);
}

.empty-text {
  color: rgba(35, 44, 86, 0.5);
}

.list-footer {
  text-align: center;
  color: rgba(35, 44, 86, 0.35);
  font-size: 12px;
  padding: 12px 0 6px;
}

/* ===== 深色模式 ===== */
html.dark .invite-card,
html.dark .list-card {
  background: rgba(30, 36, 56, 0.9);
  border-color: rgba(255, 255, 255, 0.08);
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.35);
}

html.dark .card-label,
html.dark .card-hint {
  color: rgba(200, 208, 240, 0.6);
}

html.dark .code-row {
  background: rgba(61, 90, 245, 0.12);
  border-color: rgba(93, 120, 255, 0.45);
}

html.dark .invite-code {
  color: #7d92ff;
}

html.dark .progress-line {
  color: rgba(200, 208, 240, 0.85);
}

html.dark .progress-line b {
  color: #7d92ff;
}

html.dark .progress-line .expire {
  color: rgba(200, 208, 240, 0.55);
}

html.dark .record-item {
  border-bottom-color: rgba(255, 255, 255, 0.06);
}

html.dark .record-name {
  color: #e8eaf6;
}

html.dark .record-time {
  color: rgba(200, 208, 240, 0.45);
}

html.dark .rank-num {
  color: rgba(200, 208, 240, 0.5);
}

html.dark .empty-icon {
  color: rgba(200, 208, 240, 0.25);
}

html.dark .empty-text {
  color: rgba(200, 208, 240, 0.5);
}

html.dark .list-footer {
  color: rgba(200, 208, 240, 0.35);
}
</style>
