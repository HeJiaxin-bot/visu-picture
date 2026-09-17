<template>
  <AnalyzeCard title="空间使用排行" :icon="TrophyOutlined" tint="#f43f5e">
    <v-chart :option="options" style="height: 340px; width: 100%" :loading="loading" autoresize />
  </AnalyzeCard>
</template>

<script setup lang="ts">
import VChart from 'vue-echarts'
import 'echarts'
import { computed, ref, watchEffect } from 'vue'
import { TrophyOutlined } from '@ant-design/icons-vue'
import { getSpaceRankAnalyzeUsingPost } from '@/api/spaceAnalyzeController.ts'
import { message } from 'ant-design-vue'
import AnalyzeCard from './AnalyzeCard.vue'
import { useChartTheme } from './chartTheme.ts'

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
const dataList = ref<API.Space[]>([])
// 加载状态
const loading = ref(true)

// 图表主题（跟随深浅模式）
const chartTheme = useChartTheme()

// 获取数据
const fetchData = async () => {
  loading.value = true
  // 转换搜索参数
  const res = await getSpaceRankAnalyzeUsingPost({
    queryAll: props.queryAll,
    queryPublic: props.queryPublic,
    spaceId: props.spaceId,
    topN: 10, // 后端默认是 10
  })
  if (res.data.code === 0 && res.data.data) {
    dataList.value = res.data.data ?? []
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

// 图表选项
const options = computed(() => {
  const t = chartTheme.value
  const spaceNames = dataList.value.map((item) => item.spaceName)
  const usageData = dataList.value.map((item) => (item.totalSize / (1024 * 1024)).toFixed(2)) // 转为 MB

  return {
    tooltip: {
      ...t.tooltip,
      trigger: 'axis',
      axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(22,119,255,0.06)' } },
      valueFormatter: (value: number) => `${value} MB`,
    },
    grid: { left: 4, right: 56, top: 20, bottom: 0, containLabel: true },
    xAxis: {
      type: 'value',
      axisLabel: { color: t.textColor },
      splitLine: { lineStyle: { color: t.splitLineColor } },
    },
    yAxis: {
      type: 'category',
      data: spaceNames,
      inverse: true,
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: {
        color: t.textColor,
        fontSize: 12,
        width: 110,
        overflow: 'truncate',
      },
    },
    series: [
      {
        name: '空间使用量 (MB)',
        type: 'bar',
        data: usageData,
        barMaxWidth: 18,
        itemStyle: {
          borderRadius: [0, 9, 9, 0],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 1,
            y2: 0,
            colorStops: [
              { offset: 0, color: '#2563eb' },
              { offset: 1, color: '#69c0ff' },
            ],
          },
        },
        label: {
          show: true,
          position: 'right',
          color: t.textColor,
          fontSize: 12,
          formatter: '{c} MB',
        },
      },
    ],
  }
})
</script>
