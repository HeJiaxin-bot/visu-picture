<template>
  <!-- 四季氛围粒子层：纯装饰，不响应任何交互 -->
  <div class="season-effects" :class="`fx-${themeStore.currentSeason}`" aria-hidden="true">
    <span v-for="i in 14" :key="i" class="particle" :class="`p${i}`"></span>
  </div>
</template>

<script setup lang="ts">
import { useThemeStore } from '@/stores/useThemeStore.ts'

const themeStore = useThemeStore()
</script>

<style scoped>
/* ===== 容器：全屏固定层，粒子仅飘在页面背景之上、内容之下 ===== */
.season-effects {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
}

.particle {
  position: absolute;
  top: -30px;
  left: var(--x);
  transform: translate3d(0, 0, 0);
  will-change: transform;
}

/* ===== 伪随机分布：每个粒子独立的水平位置 / 时长 / 负延迟（首屏即满空）/ 尺寸缩放 ===== */
.p1  { --x: 3%;  --d: 13s; --delay: -2s;  --s: 1;   }
.p2  { --x: 11%; --d: 17s; --delay: -9s;  --s: 0.7; }
.p3  { --x: 19%; --d: 11s; --delay: -5s;  --s: 1.2; }
.p4  { --x: 26%; --d: 15s; --delay: -12s; --s: 0.8; }
.p5  { --x: 34%; --d: 12s; --delay: -7s;  --s: 1;   }
.p6  { --x: 41%; --d: 18s; --delay: -3s;  --s: 0.6; }
.p7  { --x: 49%; --d: 14s; --delay: -11s; --s: 1.1; }
.p8  { --x: 57%; --d: 12s; --delay: -6s;  --s: 0.75;}
.p9  { --x: 64%; --d: 16s; --delay: -1s;  --s: 1;   }
.p10 { --x: 72%; --d: 13s; --delay: -10s; --s: 1.3; }
.p11 { --x: 79%; --d: 17s; --delay: -4s;  --s: 0.65;}
.p12 { --x: 87%; --d: 12s; --delay: -8s;  --s: 0.9; }
.p13 { --x: 93%; --d: 15s; --delay: -13s; --s: 1.05;}
.p14 { --x: 8%;  --d: 19s; --delay: -14s; --s: 0.55;}

/* ===== 春 · 樱粉花瓣：椭圆花瓣形，旋转摇摆飘落 ===== */
.fx-spring .particle {
  width: calc(11px * var(--s));
  height: calc(13px * var(--s));
  background: linear-gradient(135deg, #fbcfe3 0%, #f9a8c7 100%);
  border-radius: 150% 0 150% 0;
  opacity: 0.55;
  animation: sway-fall var(--d) linear var(--delay) infinite;
}

/* ===== 秋 · 金橙落叶：叶片形，飘落翻转 ===== */
.fx-autumn .particle {
  width: calc(12px * var(--s));
  height: calc(14px * var(--s));
  background: linear-gradient(135deg, #ffd591 0%, #fa8c16 100%);
  border-radius: 0 60% 0 60%;
  opacity: 0.5;
  animation: sway-fall var(--d) linear var(--delay) infinite;
}

/* ===== 冬 · 雪花：柔光白点，缓落横漂 ===== */
.fx-winter .particle {
  width: calc(7px * var(--s));
  height: calc(7px * var(--s));
  background: #fff;
  border-radius: 50%;
  box-shadow: 0 0 calc(6px * var(--s)) 1px rgba(255, 255, 255, 0.55);
  opacity: 0.65;
  animation: drift-fall var(--d) linear var(--delay) infinite;
}

/* ===== 夏 · 淡蓝光斑气泡：模糊光晕自下而上缓缓漂浮 ===== */
.fx-summer .particle {
  top: auto;
  bottom: -80px;
  width: calc(46px * var(--s));
  height: calc(46px * var(--s));
  background: radial-gradient(circle, rgba(64, 169, 255, 0.18) 0%, rgba(64, 169, 255, 0) 70%);
  border-radius: 50%;
  opacity: 0.9;
  animation: float-up var(--d) linear var(--delay) infinite;
}

/* 飘落摇摆（春/秋）：纵向下落 + 横向正弦摇摆 + 旋转 */
@keyframes sway-fall {
  0%   { transform: translate3d(0, -4vh, 0) rotate(0deg); }
  25%  { transform: translate3d(26px, 27vh, 0) rotate(95deg); }
  50%  { transform: translate3d(-18px, 54vh, 0) rotate(210deg); }
  75%  { transform: translate3d(22px, 82vh, 0) rotate(290deg); }
  100% { transform: translate3d(-6px, 110vh, 0) rotate(390deg); }
}

/* 缓落横漂（冬）：幅度更柔和，无旋转 */
@keyframes drift-fall {
  0%   { transform: translate3d(0, -4vh, 0); }
  30%  { transform: translate3d(18px, 32vh, 0); }
  60%  { transform: translate3d(-12px, 66vh, 0); }
  100% { transform: translate3d(10px, 110vh, 0); }
}

/* 上浮光斑（夏）：自底部升顶，两端淡入淡出 */
@keyframes float-up {
  0%   { transform: translate3d(0, 0, 0); opacity: 0; }
  10%  { opacity: 0.9; }
  90%  { opacity: 0.9; }
  100% { transform: translate3d(36px, calc(-110vh - 80px), 0); opacity: 0; }
}

/* ===== 深色模式：降低粒子存在感，避免干扰暗黑内容 ===== */
html.dark .season-effects.fx-spring .particle {
  background: linear-gradient(135deg, rgba(247, 89, 171, 0.4), rgba(235, 47, 150, 0.3));
  opacity: 0.4;
}

html.dark .season-effects.fx-autumn .particle {
  background: linear-gradient(135deg, rgba(255, 169, 64, 0.4), rgba(250, 140, 22, 0.3));
  opacity: 0.35;
}

html.dark .season-effects.fx-winter .particle {
  opacity: 0.35;
}

html.dark .season-effects.fx-summer .particle {
  background: radial-gradient(circle, rgba(64, 169, 255, 0.22) 0%, rgba(64, 169, 255, 0) 70%);
  opacity: 0.7;
}

/* ===== 无障碍：用户偏好减弱动效时整体停用 ===== */
@media (prefers-reduced-motion: reduce) {
  .season-effects {
    display: none;
  }
}
</style>
