<template>
  <AnalyzeCard title="空间资源总览" :icon="DatabaseOutlined" tint="#1677ff">
    <div class="usage-panels">
      <!-- 存储空间 -->
      <div class="usage-panel">
        <div class="ring-wrap">
          <svg viewBox="0 0 120 120" class="ring-svg">
            <defs>
              <linearGradient id="usageGradSize" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0%" stop-color="#1677ff" />
                <stop offset="100%" stop-color="#69c0ff" />
              </linearGradient>
            </defs>
            <circle class="ring-track" cx="60" cy="60" r="52" />
            <circle
              class="ring-progress"
              cx="60"
              cy="60"
              r="52"
              stroke="url(#usageGradSize)"
              transform="rotate(-90 60 60)"
              :style="{ strokeDashoffset: sizeOffset }"
            />
          </svg>
          <div class="ring-center">
            <span class="ring-percent">{{ formatPercent(data.sizeUsageRatio) }}</span>
            <span class="ring-label">存储使用率</span>
          </div>
        </div>
        <div class="usage-stats">
          <div class="stat-label">已用存储</div>
          <div class="stat-value">{{ formatSize(data.usedSize) }}</div>
          <div class="stat-sub">上限 {{ data.maxSize ? formatSize(data.maxSize) : '无限制' }}</div>
        </div>
      </div>

      <div class="usage-divider" />

      <!-- 图片数量 -->
      <div class="usage-panel">
        <div class="ring-wrap">
          <svg viewBox="0 0 120 120" class="ring-svg">
            <defs>
              <linearGradient id="usageGradCount" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0%" stop-color="#14b8a6" />
                <stop offset="100%" stop-color="#5eead4" />
              </linearGradient>
            </defs>
            <circle class="ring-track" cx="60" cy="60" r="52" />
            <circle
              class="ring-progress"
              cx="60"
              cy="60"
              r="52"
              stroke="url(#usageGradCount)"
              transform="rotate(-90 60 60)"
              :style="{ strokeDashoffset: countOffset }"
            />
          </svg>
          <div class="ring-center">
            <span class="ring-percent">{{ formatPercent(data.countUsageRatio) }}</span>
            <span class="ring-label">数量使用率</span>
          </div>
        </div>
        <div class="usage-stats">
          <div class="stat-label">图片数量</div>
          <div class="stat-value">{{ data.usedCount ?? 0 }} <span class="stat-unit">张</span></div>
          <div class="stat-sub">上限 {{ data.maxCount ?? '无限制' }} 张</div>
        </div>
      </div>
    </div>
  </AnalyzeCard>
</template>

<script setup lang="ts">
import { computed, ref, watchEffect } from 'vue'
import { DatabaseOutlined } from '@ant-design/icons-vue'
import { getSpaceUsageAnalyzeUsingPost } from '@/api/spaceAnalyzeController.ts'
import { message } from 'ant-design-vue'
import { formatSize } from '@/utils'
import AnalyzeCard from './AnalyzeCard.vue'

interface Props {
  queryAll?: boolean
  queryPublic?: boolean
  spaceId?: number
}

const props = withDefaults(defineProps<Props>(), {
  queryAll: false,
  queryPublic: false,
})

// 图表数据
const data = ref<API.SpaceUsageAnalyzeResponse>({})
// 加载状态
const loading = ref(true)

// 获取数据
const fetchData = async () => {
  loading.value = true
  // 转换搜索参数
  const res = await getSpaceUsageAnalyzeUsingPost({
    queryAll: props.queryAll,
    queryPublic: props.queryPublic,
    spaceId: props.spaceId,
  })
  if (res.data.code === 0 && res.data.data) {
    data.value = res.data.data
  } else {
    message.error('获取数据失败，' + res.data.message)
  }
  loading.value = false
}

/**
 * 监听变量，参数改变时触发数据的重新加载
 */
watchEffect(() => {
  fetchData()
})

// 环形进度：半径 52，周长 ≈ 326.73
const RING_LEN = 2 * Math.PI * 52
const clampRatio = (p?: number) => Math.min(Math.max(p ?? 0, 0), 100)
const sizeOffset = computed(() => RING_LEN * (1 - clampRatio(data.value.sizeUsageRatio) / 100))
const countOffset = computed(() => RING_LEN * (1 - clampRatio(data.value.countUsageRatio) / 100))

// 百分比展示：去掉多余的 0，如 2.00 -> 2%、0.14 -> 0.14%
const formatPercent = (p?: number) => {
  if (p == null) return '0%'
  return `${parseFloat(p.toFixed(2))}%`
}
</script>

<style scoped>
.usage-panels {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 6px 4px;
  flex-wrap: wrap;
}

.usage-panel {
  flex: 1;
  min-width: 260px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 22px;
}

.usage-divider {
  width: 1px;
  align-self: stretch;
  margin: 6px 0;
  background: var(--border-color);
}

.ring-wrap {
  position: relative;
  width: 120px;
  height: 120px;
  flex-shrink: 0;
}

.ring-svg {
  width: 100%;
  height: 100%;
}

.ring-track {
  fill: none;
  stroke: rgba(128, 128, 128, 0.14);
  stroke-width: 10;
}

.ring-progress {
  fill: none;
  stroke-width: 10;
  stroke-linecap: round;
  stroke-dasharray: 326.73;
  transition: stroke-dashoffset 1s cubic-bezier(0.22, 1, 0.36, 1);
}

.ring-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.ring-percent {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary-light);
  font-variant-numeric: tabular-nums;
}

.ring-label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 2px;
}

.stat-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--text-primary-light);
  margin: 4px 0 2px;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.stat-unit {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary);
}

.stat-sub {
  font-size: 12px;
  color: var(--text-secondary);
  opacity: 0.85;
}

@media (max-width: 768px) {
  .usage-divider {
    display: none;
  }
}
</style>
