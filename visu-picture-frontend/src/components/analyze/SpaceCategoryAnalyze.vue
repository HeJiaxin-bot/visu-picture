<template>
  <AnalyzeCard title="分类分布" :icon="AppstoreOutlined" tint="#8b5cf6">
    <v-chart :option="options" style="height: 320px; width: 100%" :loading="loading" autoresize />
  </AnalyzeCard>
</template>

<script setup lang="ts">
import VChart from 'vue-echarts'
import 'echarts'
import { computed, ref, watchEffect } from 'vue'
import { AppstoreOutlined } from '@ant-design/icons-vue'
import { getSpaceCategoryAnalyzeUsingPost } from '@/api/spaceAnalyzeController.ts'
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
const dataList = ref<API.SpaceCategoryAnalyzeResponse>([])
// 加载状态
const loading = ref(true)

// 图表主题（跟随深浅模式）
const chartTheme = useChartTheme()

// 获取数据
const fetchData = async () => {
  loading.value = true
  // 转换搜索参数
  const res = await getSpaceCategoryAnalyzeUsingPost({
    queryAll: props.queryAll,
    queryPublic: props.queryPublic,
    spaceId: props.spaceId,
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
  const categories = dataList.value.map((item) => item.category)
  const countData = dataList.value.map((item) => item.count)
  const sizeData = dataList.value.map((item) => (item.totalSize / (1024 * 1024)).toFixed(2)) // 转为 MB

  return {
    tooltip: {
      ...t.tooltip,
      trigger: 'axis',
      axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(22,119,255,0.06)' } },
    },
    legend: {
      data: ['图片数量', '图片总大小'],
      top: 0,
      right: 0,
      icon: 'roundRect',
      itemWidth: 12,
      itemHeight: 12,
      itemGap: 18,
      textStyle: { color: t.textColor, fontSize: 12 },
    },
    grid: { left: 4, right: 4, top: 44, bottom: 0, containLabel: true },
    xAxis: {
      type: 'category',
      data: categories,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: t.axisLineColor } },
      axisLabel: { color: t.textColor, fontSize: 12 },
    },
    yAxis: [
      {
        type: 'value',
        name: '数量',
        minInterval: 1,
        nameTextStyle: { color: t.textColor, fontSize: 12 },
        axisLabel: { color: t.textColor },
        splitLine: { lineStyle: { color: t.splitLineColor } },
      },
      {
        type: 'value',
        name: '大小 (MB)',
        position: 'right',
        nameTextStyle: { color: t.textColor, fontSize: 12 },
        axisLabel: { color: t.textColor },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: '图片数量',
        type: 'bar',
        data: countData,
        yAxisIndex: 0,
        barMaxWidth: 34,
        itemStyle: {
          borderRadius: [7, 7, 0, 0],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: '#60a5fa' },
              { offset: 1, color: '#2563eb' },
            ],
          },
        },
      },
      {
        name: '图片总大小',
        type: 'line',
        data: sizeData,
        yAxisIndex: 1,
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        lineStyle: { width: 3, color: '#14b8a6' },
        itemStyle: { color: '#14b8a6', borderColor: t.cardBg, borderWidth: 2 },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(20,184,166,0.18)' },
              { offset: 1, color: 'rgba(20,184,166,0)' },
            ],
          },
        },
      },
    ],
  }
})
</script>
