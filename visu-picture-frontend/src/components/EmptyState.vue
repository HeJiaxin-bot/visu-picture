<template>
  <div class="empty-state">
    <!-- 插画风空状态：相框 + 散落照片的意象，纯内联 SVG -->
    <svg class="empty-illustration" viewBox="0 0 160 120" fill="none" xmlns="http://www.w3.org/2000/svg">
      <!-- 背景光斑 -->
      <circle cx="128" cy="26" r="14" fill="currentColor" opacity="0.08" />
      <circle cx="24" cy="90" r="10" fill="currentColor" opacity="0.06" />
      <!-- 主相框 -->
      <rect x="42" y="22" width="76" height="66" rx="8" fill="currentColor" opacity="0.1" />
      <rect x="46" y="26" width="68" height="58" rx="6" fill="var(--bg-card, #fff)" />
      <!-- 相框内的山与太阳 -->
      <circle cx="98" cy="40" r="6" fill="currentColor" opacity="0.35" />
      <path d="M50 76 L66 56 L76 66 L88 50 L110 76 Z" fill="currentColor" opacity="0.25" />
      <!-- 斜靠的小相框 -->
      <rect
        x="108" y="58" width="34" height="40" rx="6"
        fill="currentColor" opacity="0.14"
        transform="rotate(8 125 78)"
      />
      <path
        d="M113 90 L122 76 L129 84 L136 74 L141 90 Z"
        fill="currentColor" opacity="0.2"
        transform="rotate(8 125 78)"
      />
      <!-- 底部投影 -->
      <ellipse cx="80" cy="102" rx="52" ry="5" fill="currentColor" opacity="0.08" />
    </svg>
    <p class="empty-text">{{ description }}</p>
    <div class="empty-action">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    description?: string
  }>(),
  { description: '这里空空如也' },
)
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 16px 56px;
}

.empty-illustration {
  width: 160px;
  height: 120px;
  color: var(--accent);
}

.empty-text {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.empty-action {
  margin-top: 14px;
}
</style>
