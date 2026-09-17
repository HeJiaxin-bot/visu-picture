<template>
  <div id="spaceAnalyzePage">
    <!-- 页面头部 -->
    <header class="analyze-header">
      <button class="back-btn" title="返回" @click="goBack">
        <ArrowLeftOutlined />
      </button>
      <div class="header-text">
        <div class="title-row">
          <h1 class="page-title">空间图库分析</h1>
          <span v-if="queryAll" class="scope-badge">全部空间</span>
          <span v-else-if="queryPublic" class="scope-badge">公共图库</span>
        </div>
        <p class="page-subtitle">
          <template v-if="spaceName">
            <a :href="`/space/${spaceId}`" target="_blank" class="space-link">{{ spaceName }}</a>
            <span class="dot">·</span>
          </template>
          数据概览与内容洞察
        </p>
      </div>
    </header>

    <!-- 用量总览 -->
    <div class="overview-wrap">
      <SpaceUsageAnalyze :spaceId="spaceId" :queryAll="queryAll" :queryPublic="queryPublic" />
    </div>

    <!-- 图表网格 -->
    <div class="chart-grid">
      <SpaceCategoryAnalyze
        class="span-14"
        style="animation-delay: 0.06s"
        :spaceId="spaceId"
        :queryAll="queryAll"
        :queryPublic="queryPublic"
      />
      <SpaceSizeAnalyze
        class="span-10"
        style="animation-delay: 0.12s"
        :spaceId="spaceId"
        :queryAll="queryAll"
        :queryPublic="queryPublic"
      />
      <SpaceTagAnalyze
        class="span-10"
        style="animation-delay: 0.18s"
        :spaceId="spaceId"
        :queryAll="queryAll"
        :queryPublic="queryPublic"
      />
      <SpaceUserAnalyze
        class="span-14"
        style="animation-delay: 0.24s"
        :spaceId="spaceId"
        :queryAll="queryAll"
        :queryPublic="queryPublic"
      />
      <SpaceRankAnalyze
        v-if="isAdmin"
        class="span-24"
        style="animation-delay: 0.3s"
        :spaceId="spaceId"
        :queryAll="queryAll"
        :queryPublic="queryPublic"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import SpaceUsageAnalyze from '@/components/analyze/SpaceUsageAnalyze.vue'
import SpaceCategoryAnalyze from '@/components/analyze/SpaceCategoryAnalyze.vue'
import SpaceTagAnalyze from '@/components/analyze/SpaceTagAnalyze.vue'
import SpaceSizeAnalyze from '@/components/analyze/SpaceSizeAnalyze.vue'
import SpaceUserAnalyze from '@/components/analyze/SpaceUserAnalyze.vue'
import SpaceRankAnalyze from '@/components/analyze/SpaceRankAnalyze.vue'
import { useRoute, useRouter } from 'vue-router'
import { computed, onMounted, ref } from 'vue'
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import { getSpaceVoByIdUsingGet } from '@/api/spaceController.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

const route = useRoute()
const router = useRouter()

// 空间 id
const spaceId = computed(() => {
  return route.query?.spaceId as string
})

// 是否查询所有空间
const queryAll = computed(() => {
  return !!route.query?.queryAll
})

// 是否查询公共空间
const queryPublic = computed(() => {
  return !!route.query?.queryPublic
})

// 判断用户是否为管理员
const loginUserStore = useLoginUserStore()
const loginUser = loginUserStore.loginUser
const isAdmin = computed(() => {
  return loginUser.userRole === 'admin'
})

// 返回：单空间分析回空间详情，否则回上一页
const goBack = () => {
  if (spaceId.value) {
    router.push(`/space/${spaceId.value}`)
  } else {
    router.back()
  }
}

// 单空间分析时获取空间名称，用于副标题展示与跳转
const spaceName = ref('')
const fetchSpaceName = async () => {
  if (!spaceId.value || queryAll.value || queryPublic.value) return
  const res = await getSpaceVoByIdUsingGet({ id: spaceId.value })
  if (res.data.code === 0 && res.data.data) {
    spaceName.value = res.data.data.spaceName ?? ''
  }
}

onMounted(() => {
  fetchSpaceName()
})
</script>

<style scoped>
#spaceAnalyzePage {
  margin-bottom: 24px;
}

/* ===== 页面头部 ===== */
.analyze-header {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 4px 0 20px;
}

.back-btn {
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  border-radius: 12px;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  color: var(--text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  box-shadow: var(--card-shadow);
  transition:
    color 0.25s ease,
    border-color 0.25s ease,
    transform 0.25s ease;
}

.back-btn:hover {
  color: var(--accent);
  border-color: var(--accent);
  transform: translateX(-2px);
}

.header-text {
  min-width: 0;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  margin: 0;
  color: var(--text-primary-light);
}

.scope-badge {
  font-size: 12px;
  font-weight: 500;
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--link);
}

.page-subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.space-link {
  color: var(--link);
  font-weight: 500;
}

.dot {
  margin: 0 6px;
}

/* ===== 布局 ===== */
.overview-wrap {
  margin-bottom: 18px;
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(24, 1fr);
  gap: 18px;
}

.span-14 {
  grid-column: span 14;
}

.span-10 {
  grid-column: span 10;
}

.span-24 {
  grid-column: span 24;
}

@media (max-width: 1200px) {
  .span-14,
  .span-10 {
    grid-column: span 24;
  }
}
</style>
