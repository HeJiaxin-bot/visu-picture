<template>
  <div class="analyze-card">
    <div class="card-head">
      <div class="card-title">
        <span class="title-icon" :style="{ background: tint + '1a', color: tint }">
          <component :is="icon" />
        </span>
        <span>{{ title }}</span>
      </div>
      <div v-if="$slots.extra" class="card-extra">
        <slot name="extra" />
      </div>
    </div>
    <div class="card-body">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'

interface Props {
  title: string
  /** 标题前的图标组件 */
  icon: Component
  /** 图标底色的主题色（hex），如 #1677ff */
  tint?: string
}

withDefaults(defineProps<Props>(), {
  tint: '#1677ff',
})
</script>

<style scoped>
.analyze-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  box-shadow: var(--card-shadow);
  padding: 20px 22px;
  height: 100%;
  box-sizing: border-box;
  transition:
    box-shadow 0.3s ease,
    transform 0.3s ease;
  animation: card-in 0.5s ease both;
}

.analyze-card:hover {
  box-shadow: var(--card-shadow-hover);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary-light);
}

.title-icon {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
}

.card-extra {
  display: flex;
  align-items: center;
}

.card-body {
  margin-top: 12px;
}

@keyframes card-in {
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
