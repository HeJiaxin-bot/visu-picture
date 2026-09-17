<template>
  <AnalyzeCard title="标签词云" :icon="TagsOutlined" tint="#14b8a6">
    <div v-if="!loading && dataList.length === 0" class="chart-empty">
      <TagsOutlined class="empty-icon" />
      <span>暂无标签数据</span>
    </div>
    <v-chart
      v-else
      :option="options"
      style="height: 320px; width: 100%"
      :loading="loading"
      autoresize
    />
  </AnalyzeCard>
</template>

<script setup lang="ts">
import VChart from 'vue-echarts'
import 'echarts'
import 'echarts-wordcloud'
import { computed, ref, watchEffect } from 'vue'
import { TagsOutlined } from '@ant-design/icons-vue'
import { getSpaceTagAnalyzeUsingPost } from '@/api/spaceAnalyzeController.ts'
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
const dataList = ref<API.SpaceCategoryAnalyzeResponse>([])
// 加载状态
const loading = ref(true)

// 图表主题（跟随深浅模式）
const chartTheme = useChartTheme()

// 获取数据
const fetchData = async () => {
  loading.value = true
  // 转换搜索参数
  const res = await getSpaceTagAnalyzeUsingPost({
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
  // 词云配色从统一色板循环取色，替代原来的随机色
  const tagData = dataList.value.map((item, index) => ({
    name: item.tag,
    value: item.count,
    textStyle: { color: CHART_PALETTE[index % CHART_PALETTE.length] },
  }))

  return {
    tooltip: {
      ...t.tooltip,
      trigger: 'item',
      formatter: (params: any) => `${params.name}：${params.value} 次`,
    },
    series: [
      {
        type: 'wordCloud',
        shape: 'circle',
        gridSize: 6,
        sizeRange: [14, 46],
        rotationRange: [0, 0],
        textStyle: { fontWeight: 600 },
        emphasis: {
          textStyle: {
            textShadowBlur: 8,
            textShadowColor: 'rgba(22,119,255,0.35)',
          },
        },
        data: tagData,
      },
    ],
  }
})
</script>

<style scoped>
.chart-empty {
  height: 320px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--text-disabled);
  font-size: 13px;
}

.empty-icon {
  font-size: 28px;
}
</style>
