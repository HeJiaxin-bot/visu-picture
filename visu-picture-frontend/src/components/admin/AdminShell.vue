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

/* 统一三个管理页的按钮风格：圆角 + 过渡 + 按压反馈 + 键盘焦点环 */
.admin-shell :deep(.ant-btn) {
  border-radius: 9px;
  transition:
    color 0.2s ease,
    background 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.12s ease;
}

.admin-shell :deep(.ant-btn:not(:disabled):active) {
  transform: scale(0.975);
}

.admin-shell :deep(.ant-btn:focus-visible) {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.admin-shell :deep(.ant-btn:disabled) {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 次级按钮：描边风格，视觉从属于主按钮 */
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

/* ===== 表格行内按钮 ===== */
/* 高度 32px、图标与文字成组，保证可点区域与可读性 */
.admin-shell :deep(.row-btn) {
  height: 32px;
  padding: 0 12px;
  font-size: 12.5px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

/* 审核按钮：通过 = 成功绿，拒绝 = 危险红，避免表格里一片蓝 */
.admin-shell :deep(.btn-approve) {
  color: #389e0d;
  border-color: rgba(82, 196, 26, 0.4);
  background: rgba(82, 196, 26, 0.1);
}

.admin-shell :deep(.btn-approve:hover) {
  color: #237804;
  border-color: #52c41a;
  background: rgba(82, 196, 26, 0.18);
}

.admin-shell :deep(.btn-reject) {
  color: #cf1322;
  border-color: rgba(255, 77, 79, 0.4);
  background: rgba(255, 77, 79, 0.08);
}

.admin-shell :deep(.btn-reject:hover) {
  color: #a8071a;
  border-color: #ff4d4f;
  background: rgba(255, 77, 79, 0.16);
}

html.dark .admin-shell :deep(.btn-approve) {
  color: #95de64;
  border-color: rgba(149, 222, 100, 0.32);
  background: rgba(82, 196, 26, 0.16);
}

html.dark .admin-shell :deep(.btn-reject) {
  color: #ff7875;
  border-color: rgba(255, 120, 117, 0.32);
  background: rgba(255, 77, 79, 0.16);
}

/* 删除：销毁性操作用危险色常显，悬停加深 */
.admin-shell :deep(.btn-danger-text) {
  color: #cf1322;
  border-color: transparent;
  background: transparent;
}

.admin-shell :deep(.btn-danger-text:hover) {
  color: #a8071a;
  background: rgba(255, 77, 79, 0.1);
}

html.dark .admin-shell :deep(.btn-danger-text) {
  color: #ff7875;
}

html.dark .admin-shell :deep(.btn-danger-text:hover) {
  color: #ffa39e;
  background: rgba(255, 77, 79, 0.16);
}

/* 危险操作前的分隔线：与常规操作在视觉上拉开 */
.admin-shell :deep(.btn-sep) {
  display: inline-block;
  width: 1px;
  height: 16px;
  margin: 0 2px;
  background: var(--border-color);
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

/* 固定列（fixed）必须使用不透明底色，否则横向滚动 / 悬浮时下层列的内容会透出来，看起来像文字重叠 */
.admin-table-card :deep(.ant-table-thead > tr > th.ant-table-cell-fix-right),
.admin-table-card :deep(.ant-table-tbody > tr > td.ant-table-cell-fix-right) {
  background: var(--bg-card);
}

/* 固定列左侧划一条分隔线，替代默认投影，明确「下面还有内容」 */
.admin-table-card :deep(.ant-table-thead > tr > th.ant-table-cell-fix-right-first),
.admin-table-card :deep(.ant-table-tbody > tr > td.ant-table-cell-fix-right-first) {
  border-left: 1px solid var(--border-color);
}

.admin-table-card :deep(.ant-table-tbody > tr > td.ant-table-cell-fix-right-first::after),
.admin-table-card :deep(.ant-table-thead > tr > th.ant-table-cell-fix-right-first::after) {
  box-shadow: none;
}

/* 行悬浮：统一用不透明底色（半透明会透出被覆盖的固定列 / 下层内容） */
.admin-table-card :deep(.ant-table-tbody > tr:hover > td) {
  background: #f2f8ff;
}

html.dark .admin-table-card :deep(.ant-table-tbody > tr:hover > td) {
  background: #2f3942;
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