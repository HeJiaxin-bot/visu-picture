<script setup lang="ts">
// 应用根组件：仅承载路由视图
</script>

<template>
  <router-view v-slot="{ Component }">
    <keep-alive :include="['HomePage', 'SearchPage', 'MySpacePage', 'MinePage']">
      <component :is="Component" />
    </keep-alive>
  </router-view>
</template>

<style>
/* 全局样式已在 main.css 中定义 */
</style>
