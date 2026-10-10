<template>
  <div id="techStackPage">
    <!-- 页头 -->
    <header class="page-header">
      <h2 class="page-title">硬核技术栈全景图</h2>
      <p class="page-sub">
        基于 ECharts 力导向图驱动的全栈技术矩阵 · 共
        <b>{{ TECH_NODES.length }}</b> 项技术 / <b>{{ TECH_CATEGORIES.length }}</b> 大领域
      </p>
    </header>

    <!-- 星空画布 -->
    <div class="cosmos">
      <div class="stars" aria-hidden="true"></div>
      <div class="nebula nebula-a" aria-hidden="true"></div>
      <div class="nebula nebula-b" aria-hidden="true"></div>

      <!-- 分类筛选胶囊 -->
      <div class="cosmos-toolbar">
        <button
          class="pill"
          :class="{ active: activeCategory === 'all' }"
          @click="switchCategory('all')"
        >
          <AppstoreOutlined class="pill-icon" />
          全部星系
          <span class="pill-count">{{ TECH_NODES.length }}</span>
        </button>
        <button
          v-for="cat in TECH_CATEGORIES"
          :key="cat.key"
          class="pill"
          :class="{ active: activeCategory === cat.key }"
          @click="switchCategory(cat.key)"
        >
          <component :is="categoryIcon(cat.key)" class="pill-icon" />
          {{ cat.label }}
          <span class="pill-count">{{ countOf(cat.key) }}</span>
        </button>
      </div>

      <!-- 图谱：原生 force 布局 + 原生 adjacency 高亮（顺滑不卡顿） -->
      <v-chart
        class="tech-chart"
        :option="options"
        :style="{ height: '760px', width: '100%' }"
        autoresize
      />

      <!-- 操作提示 -->
      <div class="cosmos-hint">
        <span>拖拽节点可微调布局 · 滚轮缩放 · 悬停查看技术说明</span>
      </div>
    </div>

    <!-- 底部统计 -->
    <div class="stat-row">
      <div v-for="item in stats" :key="item.label" class="stat-card">
        <div class="stat-value">{{ item.value }}</div>
        <div class="stat-label">{{ item.label }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import VChart from 'vue-echarts'
import 'echarts'
import {
  AppstoreOutlined,
  BellOutlined,
  CloudServerOutlined,
  ExperimentOutlined,
  InteractionOutlined,
  PictureOutlined,
  RocketOutlined,
} from '@ant-design/icons-vue'
import { HUB_ID, TECH_CATEGORIES, TECH_LINKS, TECH_NODES } from '@/data/techStack.ts'

/** 当前选中的分类：all 或分类 key */
const activeCategory = ref<string>('all')

/** 分类图标 */
const categoryIcons: Record<string, any> = {
  frontend: InteractionOutlined,
  visual: PictureOutlined,
  backend: CloudServerOutlined,
  ai: ExperimentOutlined,
  ops: RocketOutlined,
}
const categoryIcon = (key: string) => categoryIcons[key] ?? BellOutlined

/** 各分类节点数 */
const countOf = (key: string) => TECH_NODES.filter((n) => n.category === key).length

/** 分类 key -> 配置 */
const categoryMap = Object.fromEntries(
  TECH_CATEGORIES.map((c) => [c.key, c]),
) as Record<string, (typeof TECH_CATEGORIES)[number]>

/** 底部统计 */
const stats = computed(() => [
  { value: String(TECH_NODES.length), label: '技术栈节点' },
  { value: String(TECH_CATEGORIES.length), label: '技术领域' },
  { value: '28+', label: '数据表与接口' },
  { value: '2', label: '明暗主题 / 四季配色' },
])

/**
 * 图表配置：ECharts 原生力导向图
 *
 * 性能与交互说明：
 * - 悬停高亮交给原生 emphasis.focus='adjacency'，不做 Vue 层的 option 重建，
 *   避免每次鼠标移动都重算 37 节点 + 73 连线导致卡顿
 * - 因此本 computed 只依赖 activeCategory（切换分类时才重算）
 * - 不使用 labelLayout.moveOverlap：它每次重绘都要跑一遍标签避让碰撞检测，是卡顿主因
 */
const options = computed(() => {
  const active = activeCategory.value

  const data = TECH_NODES.map((node, idx) => {
    const cat = categoryMap[node.category]
    const highlighted = active === 'all' || node.category === active
    const opacity = highlighted ? 1 : 0.1
    // 环形均匀起点：让力导向从中心对称展开，避免整体偏向一侧
    const total = TECH_NODES.length
    const angle = (idx / total) * Math.PI * 2 + hash01(node.id) * 0.4
    const radius = node.hub ? 0 : 90 + hash01(node.id + 'r') * 70

    return {
      id: node.id,
      name: node.name,
      desc: node.desc,
      category: TECH_CATEGORIES.findIndex((c) => c.key === node.category),
      symbolSize: node.size,
      // 环形起点（中枢在原点），力导向会在此基础上自然展开
      x: Math.round(Math.cos(angle) * radius),
      y: Math.round(Math.sin(angle) * radius * 0.8),
      itemStyle: {
        color: node.hub
          ? {
              type: 'radial',
              x: 0.4,
              y: 0.35,
              r: 0.7,
              colorStops: [
                { offset: 0, color: '#ffffff' },
                { offset: 0.35, color: '#69b9ff' },
                { offset: 1, color: '#1677ff' },
              ],
            }
          : {
              type: 'radial',
              x: 0.4,
              y: 0.35,
              r: 0.7,
              colorStops: [
                { offset: 0, color: '#ffffff' },
                { offset: 0.45, color: cat?.color ?? '#3b82f6' },
                { offset: 1, color: 'rgba(6,12,28,0.95)' },
              ],
            },
        borderColor: node.hub ? 'rgba(255,255,255,0.9)' : 'rgba(255,255,255,0.55)',
        borderWidth: node.hub ? 2 : 1,
        shadowBlur: highlighted ? (node.hub ? 42 : 18) : 0,
        shadowColor: cat?.color ?? '#3b82f6',
        opacity,
      },
      // 标签使用「直接属性」而不是 rich：
      // rich 内的样式会覆盖 blur 状态的 label.opacity，导致悬停时标签不变暗。
      // 每个节点是独立数据项，直接按节点类型生成样式即可。
      label: {
        show: true,
        position: 'bottom',
        distance: node.hub ? 14 : 8,
        formatter: node.name,
        // 分类筛选的淡出；悬停变暗由 blur 状态接管
        opacity,
        color: node.hub ? '#ffffff' : '#e8f1ff',
        fontSize: node.hub ? 15 : 12,
        fontWeight: node.hub ? 'bold' : 'normal',
        lineHeight: node.hub ? 24 : 19,
        padding: node.hub ? [6, 14] : [4, 9],
        backgroundColor: 'rgba(9,16,34,0.92)',
        borderColor: node.hub ? '#69b9ff' : (cat?.color ?? '#3b82f6'),
        borderWidth: node.hub ? 1.5 : 1,
        borderRadius: node.hub ? 8 : 6,
        ...(node.hub
          ? { shadowBlur: 18, shadowColor: 'rgba(22,119,255,0.8)' }
          : {}),
      },
    }
  })

  const links = TECH_LINKS.map((link) => {
    const targetNode = TECH_NODES.find((n) => n.id === link.target)
    const sourceNode = TECH_NODES.find((n) => n.id === link.source)
    const catOk =
      active === 'all' ||
      (!!sourceNode && sourceNode.category === active) ||
      (!!targetNode && targetNode.category === active)
    const cat = targetNode ? categoryMap[targetNode.category] : undefined

    return {
      source: link.source,
      target: link.target,
      // 每条边长度略有差异，让分布疏密有致而非规整圆环
      value: 60 + Math.round(hash01(link.source + '->' + link.target) * 120),
      lineStyle: {
        color: cat?.color ?? '#4b7bd6',
        width: link.source === HUB_ID ? 1.6 : 1,
        opacity: catOk ? (link.source === HUB_ID ? 0.5 : 0.34) : 0.04,
        curveness: 0.2,
      },
    }
  })

  return {
    backgroundColor: 'transparent',
    // 动画保持开启，但限制时长，兼顾顺滑与响应
    animation: true,
    animationDuration: 600,
    animationEasingUpdate: 'cubicOut',
    tooltip: {
      backgroundColor: 'rgba(11,18,38,0.95)',
      borderColor: 'rgba(105,185,255,0.35)',
      borderWidth: 1,
      padding: 0,
      textStyle: { color: '#e6edf7', fontSize: 12 },
      extraCssText: 'box-shadow: 0 12px 32px rgba(0,0,0,0.5); border-radius: 10px;',
      formatter: (params: any) => {
        if (params.dataType !== 'node') return ''
        const node = params.data as { name: string; desc: string; category: number }
        const cat = TECH_CATEGORIES[node.category]
        return `
          <div style="padding:10px 14px;min-width:180px;max-width:260px">
            <div style="font-size:13px;font-weight:700;color:#fff;margin-bottom:6px">${node.name}</div>
            <div style="font-size:12px;color:#a9b8d0;line-height:18px;margin-bottom:8px">${node.desc}</div>
            <div style="display:inline-block;font-size:11px;padding:1px 8px;border-radius:4px;
                        color:${cat?.color};background:${cat?.color}22;border:1px solid ${cat?.color}66">
              所属领域：${cat?.label}
            </div>
          </div>`
      },
    },
    series: [
      {
        type: 'graph',
        layout: 'force',
        roam: true,
        draggable: true,
        // 使用 ECharts 原生高亮：只影响渲染，不触发 Vue 侧重算，悬停顺滑
        emphasis: {
          focus: 'adjacency',
          scale: 1.15,
          itemStyle: { shadowBlur: 36, borderColor: '#ffffff', borderWidth: 1.8 },
          lineStyle: { width: 2.4, opacity: 0.9 },
          label: { opacity: 1 },
        },
        blur: {
          itemStyle: { opacity: 0.12 },
          lineStyle: { opacity: 0.05 },
          label: { opacity: 0.15 },
        },
        force: {
          // 斥力不能过大：中枢只连 6 条核心边，斥力过强会把节点甩出画布
          // 较强的向心力把整体收拢在画布内
          repulsion: [80, 220],
          edgeLength: [50, 115],
          gravity: 0.42,
          friction: 0.28,
          layoutAnimation: true,
        },
        categories: TECH_CATEGORIES.map((c) => ({
          name: c.label,
          itemStyle: { color: c.color },
        })),
        data,
        links,
        lineStyle: { curveness: 0.2 },
      },
    ],
  } as any
})

/** 稳定哈希：让初始位置在每次渲染保持一致 */
function hash01(seed: string) {
  let h = 2166136261
  for (let i = 0; i < seed.length; i++) {
    h ^= seed.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return ((h >>> 0) % 100000) / 100000
}

/** 切换分类 */
const switchCategory = (key: string) => {
  activeCategory.value = key
}
</script>

<style scoped>
#techStackPage {
  max-width: 1180px;
  margin: 0 auto;
  padding-bottom: 32px;
}

/* ---------- 页头 ---------- */
.page-header {
  margin-bottom: 20px;
  text-align: center;
}

.page-title {
  margin: 0;
  font-size: 28px;
  letter-spacing: 1px;
}

.page-sub {
  margin: 10px 0 0;
  font-size: 13.5px;
  color: var(--text-secondary);
}

.page-sub b {
  color: var(--accent);
  font-weight: 700;
}

/* ---------- 星空画布 ---------- */
.cosmos {
  position: relative;
  border-radius: 20px;
  overflow: hidden;
  background:
    radial-gradient(ellipse 60% 45% at 50% 0%, rgba(56, 106, 220, 0.28), transparent 70%),
    radial-gradient(ellipse 45% 40% at 12% 88%, rgba(139, 92, 246, 0.22), transparent 70%),
    radial-gradient(ellipse 40% 35% at 92% 78%, rgba(6, 182, 212, 0.16), transparent 70%),
    linear-gradient(180deg, #0b1226 0%, #070c1a 45%, #04060f 100%);
  box-shadow: 0 24px 60px rgba(8, 20, 48, 0.35);
  border: 1px solid rgba(105, 185, 255, 0.14);
}

/* 星点：多层 radial-gradient 平铺，纯 CSS 无额外资源 */
.stars {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    radial-gradient(1.6px 1.6px at 12% 18%, rgba(255, 255, 255, 0.9), transparent),
    radial-gradient(1.4px 1.4px at 28% 62%, rgba(255, 255, 255, 0.7), transparent),
    radial-gradient(1.8px 1.8px at 44% 12%, rgba(200, 225, 255, 0.85), transparent),
    radial-gradient(1.2px 1.2px at 58% 74%, rgba(255, 255, 255, 0.6), transparent),
    radial-gradient(1.6px 1.6px at 72% 28%, rgba(255, 255, 255, 0.8), transparent),
    radial-gradient(1.3px 1.3px at 86% 56%, rgba(200, 225, 255, 0.7), transparent),
    radial-gradient(1.5px 1.5px at 8% 82%, rgba(255, 255, 255, 0.55), transparent),
    radial-gradient(1.2px 1.2px at 36% 92%, rgba(255, 255, 255, 0.5), transparent),
    radial-gradient(1.7px 1.7px at 64% 8%, rgba(255, 255, 255, 0.75), transparent),
    radial-gradient(1.3px 1.3px at 92% 12%, rgba(255, 255, 255, 0.6), transparent),
    radial-gradient(1.1px 1.1px at 20% 44%, rgba(255, 255, 255, 0.45), transparent),
    radial-gradient(1.4px 1.4px at 78% 88%, rgba(255, 255, 255, 0.5), transparent);
  opacity: 0.85;
}

/* 星云光斑 */
.nebula {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  pointer-events: none;
  opacity: 0.5;
}

.nebula-a {
  width: 320px;
  height: 220px;
  top: -60px;
  left: -40px;
  background: rgba(59, 130, 246, 0.35);
}

.nebula-b {
  width: 280px;
  height: 200px;
  right: -50px;
  bottom: -40px;
  background: rgba(244, 114, 182, 0.22);
}

/* ---------- 分类筛选胶囊 ---------- */
.cosmos-toolbar {
  position: relative;
  z-index: 2;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  padding: 18px 20px 6px;
}

.pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 500;
  color: rgba(200, 215, 240, 0.82);
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(140, 175, 235, 0.2);
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.pill:hover {
  color: #fff;
  border-color: rgba(140, 175, 235, 0.5);
  background: rgba(255, 255, 255, 0.09);
}

.pill.active {
  color: #fff;
  background: linear-gradient(135deg, rgba(64, 169, 255, 0.9), rgba(22, 119, 255, 0.9));
  border-color: rgba(160, 210, 255, 0.7);
  box-shadow: 0 6px 20px rgba(22, 119, 255, 0.45);
}

.pill-icon {
  font-size: 14px;
  opacity: 0.9;
}

.pill-count {
  min-width: 20px;
  padding: 0 6px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 700;
  line-height: 18px;
  text-align: center;
  color: #dce8ff;
  background: rgba(255, 255, 255, 0.14);
}

.pill.active .pill-count {
  background: rgba(255, 255, 255, 0.28);
}

/* ---------- 图表 ---------- */
.tech-chart {
  position: relative;
  z-index: 1;
}

.cosmos-hint {
  position: relative;
  z-index: 2;
  padding: 0 20px 16px;
  text-align: center;
  font-size: 12px;
  color: rgba(160, 180, 215, 0.6);
}

/* ---------- 底部统计 ---------- */
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-top: 20px;
}

.stat-card {
  padding: 18px 16px;
  border-radius: 14px;
  text-align: center;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  box-shadow: var(--card-shadow);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--card-shadow-hover);
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--accent);
  line-height: 1.2;
}

.stat-label {
  margin-top: 6px;
  font-size: 12.5px;
  color: var(--text-secondary);
}

/* ---------- 响应式 ---------- */
@media (max-width: 900px) {
  .stat-row {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 640px) {
  .cosmos-toolbar {
    gap: 6px;
    padding: 14px 12px 4px;
  }

  .pill {
    padding: 6px 11px;
    font-size: 12px;
  }
}
</style>
