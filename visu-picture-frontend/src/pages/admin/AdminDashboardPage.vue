<template>
  <div id="adminDashboardPage">
    <div class="page-head">
      <h2 class="page-title">数据看板</h2>
      <p class="page-sub">全站核心指标总览：规模、增量、审核压力与空间占用</p>
    </div>

    <a-spin :spinning="loading">
      <!-- 指标卡 -->
      <div class="stat-grid">
        <div class="stat-card">
          <div class="stat-label">今日新增图片</div>
          <div class="stat-value accent">{{ overview?.todayPictures ?? '-' }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">今日新增用户</div>
          <div class="stat-value accent">{{ overview?.todayUsers ?? '-' }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">图片总数</div>
          <div class="stat-value">{{ overview?.totalPictures ?? '-' }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">用户总数</div>
          <div class="stat-value">{{ overview?.totalUsers ?? '-' }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">待审核</div>
          <div class="stat-value warn">{{ overview?.pendingReviews ?? '-' }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">AI 生成图片</div>
          <div class="stat-value">{{ overview?.aiGeneratedCount ?? '-' }}</div>
        </div>
      </div>

      <!-- 近 7 天上传趋势 -->
      <div class="chart-card">
        <div class="chart-title">近 7 天上传趋势</div>
        <v-chart :option="trendOption" autoresize class="trend-chart" />
      </div>

      <!-- 空间占用排行 -->
      <div class="chart-card">
        <div class="chart-title">空间占用排行 Top 5</div>
        <div class="top-space-list">
          <div v-for="(space, index) in overview?.topSpaces ?? []" :key="space.id" class="top-space-row">
            <span class="rank" :class="`top-${index + 1}`">{{ index + 1 }}</span>
            <span class="space-name">{{ space.spaceName || '未命名空间' }}</span>
            <span class="space-count">{{ space.totalCount ?? 0 }} 张</span>
            <span class="space-size">{{ formatSize(space.totalSize ?? 0) }}</span>
          </div>
          <a-empty v-if="!(overview?.topSpaces?.length)" description="暂无空间数据" />
        </div>
      </div>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import VChart from 'vue-echarts'
import 'echarts'
import { getAdminDashboardOverview } from '@/api/dashboard.ts'
import type { DashboardOverview } from '@/api/dashboard.ts'
import { formatSize } from '../../utils'

const overview = ref<DashboardOverview>()
const loading = ref(false)

const fetchOverview = async () => {
  loading.value = true
  try {
    const res = await getAdminDashboardOverview()
    if (res.code === 0 && res.data) {
      overview.value = res.data
    } else {
      message.error(res.message ?? '加载看板数据失败')
    }
  } catch (e: any) {
    message.error('加载看板数据失败：' + (e?.message ?? ''))
  } finally {
    loading.value = false
  }
}

// 折线图配置：晨风浅蓝主色 + 渐变面积，坐标轴用中性灰适配明暗两套主题
const trendOption = computed(() => {
  const trend = overview.value?.uploadTrend ?? []
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 24, top: 24, bottom: 28 },
    xAxis: {
      type: 'category',
      data: trend.map((t) => t.date),
      axisLine: { lineStyle: { color: 'rgba(128, 128, 148, 0.35)' } },
      axisLabel: { color: '#8a8fa8' },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: '#8a8fa8' },
      splitLine: { lineStyle: { color: 'rgba(128, 128, 148, 0.15)' } },
    },
    series: [
      {
        type: 'line',
        smooth: true,
        symbolSize: 7,
        data: trend.map((t) => t.count),
        lineStyle: { color: '#40a9ff', width: 3 },
        itemStyle: { color: '#40a9ff' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(64, 169, 255, 0.28)' },
              { offset: 1, color: 'rgba(64, 169, 255, 0.02)' },
            ],
          },
        },
      },
    ],
  }
})

onMounted(() => {
  fetchOverview()
})
</script>

<style scoped>
#adminDashboardPage {
  max-width: 1152px;
  margin: 0 auto 24px;
  padding: 0 16px;
}

.page-head {
  margin: 12px 0 0;
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

/* ===== 指标卡 ===== */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
  margin-top: 16px;
}

@media (max-width: 1024px) {
  .stat-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 560px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

.stat-card {
  background: var(--bg-card);
  border-radius: var(--card-radius, 12px);
  box-shadow: var(--card-shadow);
  padding: 16px;
  text-align: center;
}

.stat-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.stat-value {
  margin-top: 8px;
  font-size: 26px;
  font-weight: 700;
  color: var(--text-primary-light);
  font-variant-numeric: tabular-nums;
}

.stat-value.accent {
  color: var(--accent);
}

.stat-value.warn {
  color: #faad14;
}

html.dark .stat-value {
  color: var(--text-primary);
}

/* ===== 图表卡 ===== */
.chart-card {
  background: var(--bg-card);
  border-radius: var(--card-radius, 12px);
  box-shadow: var(--card-shadow);
  padding: 16px 20px;
  margin-top: 16px;
}

.chart-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary-light);
  margin-bottom: 8px;
}

html.dark .chart-title {
  color: var(--text-primary);
}

.trend-chart {
  height: 300px;
}

/* ===== 空间排行 ===== */
.top-space-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 8px;
}

.top-space-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(127, 127, 127, 0.06);
}

.rank {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  background: rgba(127, 127, 127, 0.15);
  color: var(--text-secondary);
  flex-shrink: 0;
}

.rank.top-1 {
  background: #faad14;
  color: #fff;
}

.rank.top-2 {
  background: rgba(64, 169, 255, 0.6);
  color: #fff;
}

.rank.top-3 {
  background: rgba(255, 173, 20, 0.35);
  color: #fff;
}

.space-name {
  flex: 1;
  font-size: 13px;
  color: var(--text-primary-light);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

html.dark .space-name {
  color: var(--text-primary);
}

.space-count,
.space-size {
  font-size: 12px;
  color: var(--text-secondary);
  font-variant-numeric: tabular-nums;
}
</style>
