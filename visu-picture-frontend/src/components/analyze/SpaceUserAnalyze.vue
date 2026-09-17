<template>
  <AnalyzeCard title="用户上传趋势" :icon="LineChartOutlined" tint="#3b82f6">
    <template #extra>
      <a-space :size="10">
        <a-segmented v-model:value="timeDimension" :options="timeDimensionOptions" size="small" />
        <a-input-search
          placeholder="请输入用户 id"
          enter-button="搜索"
          size="small"
          style="width: 190px"
          @search="doSearch"
        />
      </a-space>
    </template>
    <v-chart :option="options" style="height: 320px; width: 100%" :loading="loading" autoresize />
  </AnalyzeCard>
</template>

<script setup lang="ts">
import VChart from 'vue-echarts'
import 'echarts'
import { computed, ref, watchEffect } from 'vue'
import { LineChartOutlined } from '@ant-design/icons-vue'
import { getSpaceUserAnalyzeUsingPost } from '@/api/spaceAnalyzeController.ts'
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

// 时间维度选项
const timeDimension = ref<'day' | 'week' | 'month'>('day')
// 分段选择器组件的选项
const timeDimensionOptions = [
  {
    label: '日',
    value: 'day',
  },
  {
    label: '周',
    value: 'week',
  },
  {
    label: '月',
    value: 'month',
  },
]
// 用户选项
const userId = ref<string>()
const doSearch = (value: string) => {
  userId.value = value
}

// 图表数据
const dataList = ref<API.SpaceCategoryAnalyzeResponse>([])
// 加载状态
const loading = ref(true)

// 图表主题（跟随深浅模式）
const chartTheme = useChartTheme()

// 获取数据
const fetchData = async () => {
  loading.value = true
  // 转换搜索参数
  const res = await getSpaceUserAnalyzeUsingPost({
    queryAll: props.queryAll,
    queryPublic: props.queryPublic,
    spaceId: props.spaceId,
    timeDimension: timeDimension.value,
    userId: userId.value,
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
  const periods = dataList.value.map((item) => item.period) // 时间区间
  const counts = dataList.value.map((item) => item.count) // 上传数量

  return {
    tooltip: {
      ...t.tooltip,
      trigger: 'axis',
      axisPointer: { type: 'line', lineStyle: { color: 'rgba(59,130,246,0.3)' } },
    },
    grid: { left: 4, right: 12, top: 30, bottom: 0, containLabel: true },
    xAxis: {
      type: 'category',
      data: periods,
      boundaryGap: false,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: t.axisLineColor } },
      axisLabel: { color: t.textColor, fontSize: 12 },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: t.textColor },
      splitLine: { lineStyle: { color: t.splitLineColor } },
    },
    series: [
      {
        name: '上传数量',
        type: 'line',
        data: counts,
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        lineStyle: { width: 3, color: '#3b82f6' },
        itemStyle: { color: '#3b82f6', borderColor: t.cardBg, borderWidth: 2 },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(59,130,246,0.22)' },
              { offset: 1, color: 'rgba(59,130,246,0)' },
            ],
          },
        },
        emphasis: {
          focus: 'series',
        },
      },
    ],
  }
})
</script>
