<template>
  <AnalyzeCard title="大小分布" :icon="PieChartOutlined" tint="#f59e0b">
    <v-chart :option="options" style="height: 320px; width: 100%" :loading="loading" autoresize />
  </AnalyzeCard>
</template>

<script setup lang="ts">
import VChart from 'vue-echarts'
import 'echarts'
import { computed, ref, watchEffect } from 'vue'
import { PieChartOutlined } from '@ant-design/icons-vue'
import { getSpaceSizeAnalyzeUsingPost } from '@/api/spaceAnalyzeController.ts'
import { message } from 'ant-design-vue'
import AnalyzeCard from './AnalyzeCard.vue'
import { CHART_PALETTE, useChartTheme } from './chartTheme.ts'

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
const dataList = ref<API.SpaceSizeAnalyzeResponse>([])
// 加载状态
const loading = ref(true)

// 图表主题（跟随深浅模式）
const chartTheme = useChartTheme()

// 获取数据
const fetchData = async () => {
  loading.value = true
  // 转换搜索参数
  const res = await getSpaceSizeAnalyzeUsingPost({
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
  const pieData = dataList.value.map((item) => ({
    name: item.sizeRange,
    value: item.count,
  }))
  const total = pieData.reduce((sum, item) => sum + (item.value ?? 0), 0)

  return {
    color: CHART_PALETTE,
    tooltip: {
      ...t.tooltip,
      trigger: 'item',
      formatter: (params: any) => `${params.name}<br/>${params.value} 张（${params.percent}%）`,
    },
    legend: {
      bottom: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      itemGap: 16,
      textStyle: { color: t.textColor, fontSize: 12 },
    },
    title: {
      text: String(total),
      subtext: '图片总数',
      left: 'center',
      top: '33%',
      itemGap: 4,
      textStyle: {
        fontSize: 26,
        fontWeight: 700,
        color: t.dark ? '#f0f0f0' : '#212121',
      },
      subtextStyle: { fontSize: 12, color: t.textColor },
    },
    series: [
      {
        name: '图片大小',
        type: 'pie',
        radius: ['52%', '72%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: true,
        itemStyle: {
          borderRadius: 8,
          borderColor: t.cardBg,
          borderWidth: 3,
        },
        label: { show: false },
        emphasis: {
          scale: true,
          scaleSize: 6,
          label: { show: false },
        },
        data: pieData,
      },
    ],
  }
})
</script>
