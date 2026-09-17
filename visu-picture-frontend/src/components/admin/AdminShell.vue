<template>
  <div class="admin-shell">
    <!-- 页头：图标 + 标题 + 描述 + 右侧操作 -->
    <header class="admin-head">
      <span class="head-chip" :style="{ background: tint + '1f', color: tint }">
        <component :is="icon" />
      </span>
      <div class="head-text">
        <h1 class="head-title">{{ title }}</h1>
        <p class="head-desc">
          {{ desc }}
          <template v-if="meta">
            <span class="head-dot">·</span>
            <span class="head-meta">{{ meta }}</span>
          </template>
        </p>
      </div>
      <div v-if="$slots.actions" class="head-actions">
        <slot name="actions" />
      </div>
    </header>
    <div class="head-rule" :style="{ background: `linear-gradient(90deg, ${tint}59, transparent)` }" />

    <!-- 筛选区 -->
    <section v-if="$slots.filters" class="admin-filters">
      <slot name="filters" />
    </section>

    <!-- 表格区 -->
    <section class="admin-table-card">
      <slot />
    </section>
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'

interface Props {
  title: string
  desc: string
  /** 页头图标 */
  icon: Component
  /** 页头主题色（hex） */
  tint: string
  /** 附在描述后的补充信息，如「共 128 位用户」 */
  meta?: string
}

defineProps<Props>()
</script>

<style scoped>
.admin-shell {
  --field-bg: rgba(22, 119, 255, 0.05);
  max-width: 1440px;
  margin: 0 auto;
}

html.dark .admin-shell {
  --field-bg: rgba(255, 255, 255, 0.06);
}

/* ===== 页头 ===== */
.admin-head {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.head-chip {
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  border-radius: 13px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 19px;
}

.head-text {
  min-width: 0;
}

.head-title {
  margin: 0;
  font-size: 21px;
  font-weight: 700;
  letter-spacing: 0.01em;
  color: var(--text-primary-light);
}

.head-desc {
  margin: 4px 0 0;
  font-size: 12.5px;
  color: var(--text-secondary);
}

.head-dot {
  margin: 0 6px;
  opacity: 0.5;
}

.head-meta {
  font-variant-numeric: tabular-nums;
}

.head-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.head-rule {
  height: 2px;
  border-radius: 2px;
  margin: 14px 0 18px;
}

/* 统一三个管理页的按钮风格：圆角 + 次级按钮走描边 */
.admin-shell :deep(.ant-btn) {
  border-radius: 9px;
}

.admin-shell :deep(.ghost-btn) {
  background: transparent;
  border-color: var(--border-color);
  color: var(--text-secondary);
}

.admin-shell :deep(.ghost-btn:hover) {
  color: var(--accent);
  border-color: var(--accent);
  background: rgba(22, 119, 255, 0.06);
}

/* ===== 筛选卡片 ===== */
.admin-filters {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  box-shadow: var(--card-shadow);
  padding: 18px 20px 6px;
  margin-bottom: 18px;
  animation: admin-in 0.5s ease both;
}

.admin-filters :deep(.ant-form-item) {
  margin-bottom: 12px;
}

.admin-filters :deep(.ant-form-item-label > label) {
  font-size: 12.5px;
  color: var(--text-secondary);
}

.admin-filters :deep(.ant-input),
.admin-filters :deep(.ant-input-affix-wrapper),
.admin-filters :deep(.ant-select .ant-select-selector) {
  background: var(--field-bg);
  border-color: var(--border-color);
  border-radius: 9px;
}

.admin-filters :deep(.ant-input:hover),
.admin-filters :deep(.ant-input-affix-wrapper:hover),
.admin-filters :deep(.ant-select:hover .ant-select-selector) {
  border-color: var(--accent);
}

.admin-filters :deep(.ant-select-multiple .ant-select-selection-item) {
  border-radius: 999px;
}

/* ===== 表格卡片 ===== */
.admin-table-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  box-shadow: var(--card-shadow);
  padding: 6px 8px 8px;
  overflow: hidden;
  animation: admin-in 0.5s 0.08s ease both;
}

.admin-table-card :deep(.ant-table) {
  background: transparent;
}

.admin-table-card :deep(.ant-table-thead > tr > th) {
  background: transparent;
  border-bottom: 1px solid var(--border-color);
  padding: 12px 14px;
  color: var(--text-disabled);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  white-space: nowrap;
}

/* 去掉表头列之间的竖分隔线 */
.admin-table-card :deep(.ant-table-thead > tr > th::before) {
  display: none;
}

.admin-table-card :deep(.ant-table-tbody > tr > td) {
  padding: 14px;
  border-bottom: 1px solid var(--border-color);
  color: var(--text-primary-light);
  font-size: 13px;
  vertical-align: middle;
}

.admin-table-card :deep(.ant-table-tbody > tr:last-child > td) {
  border-bottom: none;
}

.admin-table-card :deep(.ant-table-tbody > tr > td.ant-table-cell-fix-right) {
  background: var(--bg-card);
}

.admin-table-card :deep(.ant-table-tbody > tr:hover > td),
.admin-table-card :deep(.ant-table-tbody > tr:hover > td.ant-table-cell-fix-right) {
  background: rgba(22, 119, 255, 0.05);
}

html.dark .admin-table-card :deep(.ant-table-tbody > tr:hover > td),
html.dark .admin-table-card :deep(.ant-table-tbody > tr:hover > td.ant-table-cell-fix-right) {
  background: rgba(64, 169, 255, 0.1);
}

.admin-table-card :deep(.ant-table-tbody > tr > td.ant-table-cell-fix-right-first::after),
.admin-table-card :deep(.ant-table-thead > tr > th.ant-table-cell-fix-right-first::after) {
  box-shadow: none;
}

.admin-table-card :deep(.ant-table-placeholder .ant-table-cell) {
  border-bottom: none;
}

.admin-table-card :deep(.ant-empty) {
  padding: 32px 0;
}

.admin-table-card :deep(.ant-empty-description) {
  color: var(--text-disabled);
  font-size: 13px;
}

.admin-table-card :deep(.ant-tag) {
  border-radius: 999px;
  font-size: 12px;
  margin-inline-end: 0;
}

.admin-table-card :deep(.ant-pagination) {
  margin: 16px 8px 8px;
}

.admin-table-card :deep(.ant-pagination-total-text) {
  color: var(--text-secondary);
  font-size: 12.5px;
}

@keyframes admin-in {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>