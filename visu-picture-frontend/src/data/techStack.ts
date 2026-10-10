/**
 * 技术栈全景图数据
 * 与项目实际依赖保持一致（见 README 与 package.json / pom.xml）
 */

export interface TechCategory {
  key: string
  label: string
  color: string
}

export interface TechNode {
  /** 唯一 id（连线用） */
  id: string
  /** 展示名称 */
  name: string
  /** 所属分类 key */
  category: string
  /** 悬浮提示中的说明 */
  desc: string
  /** 节点尺寸 */
  size: number
  /** 是否为中枢节点 */
  hub?: boolean
}

export interface TechLink {
  source: string
  target: string
}

/** 中枢节点 id */
export const HUB_ID = 'visu'

/** 五大分类（配色对应胶囊按钮与节点标签描边） */
export const TECH_CATEGORIES: TechCategory[] = [
  { key: 'frontend', label: '前端交互', color: '#3b82f6' },
  { key: 'visual', label: '可视化与媒体', color: '#06b6d4' },
  { key: 'backend', label: '后端与高并发', color: '#8b5cf6' },
  { key: 'ai', label: 'AI 与云服务', color: '#f472b6' },
  { key: 'ops', label: '工程与体验', color: '#f59e0b' },
]

/** 技术栈节点 */
export const TECH_NODES: TechNode[] = [
  // ---------- 中枢 ----------
  {
    id: HUB_ID,
    name: 'Visu 全栈引擎',
    category: 'frontend',
    desc: '视界云图库 · 一站式图片管理与协作平台',
    size: 76,
    hub: true,
  },

  // ---------- 前端交互 ----------
  { id: 'vue', name: 'Vue 3.5', category: 'frontend', desc: '组合式 API + <script setup>，全站 SPA 基座', size: 34 },
  { id: 'vite', name: 'Vite 5', category: 'frontend', desc: '秒级冷启动与热更新，路由级懒加载分包', size: 30 },
  { id: 'ts', name: 'TypeScript 5', category: 'frontend', desc: '全量类型约束，接口类型由 OpenAPI 自动生成', size: 30 },
  { id: 'router', name: 'Vue Router 4', category: 'frontend', desc: '路由懒加载 + 导航守卫', size: 26 },
  { id: 'pinia', name: 'Pinia', category: 'frontend', desc: '登录态 / 主题 / 季节全局状态管理', size: 26 },
  { id: 'antd', name: 'Ant Design Vue', category: 'frontend', desc: '企业级组件库，按需自动引入', size: 30 },
  { id: 'vueuse', name: 'VueUse', category: 'frontend', desc: '组合式工具函数集', size: 24 },
  { id: 'axios', name: 'Axios', category: 'frontend', desc: '统一请求封装：拦截器 / 超时 / 凭证携带', size: 26 },

  // ---------- 可视化与媒体 ----------
  { id: 'echarts', name: 'ECharts 5', category: 'visual', desc: '空间分析图表引擎（趋势 / 占比 / 排行）', size: 30 },
  { id: 'vecharts', name: 'vue-echarts', category: 'visual', desc: '图表组件化封装，随主题自动重绘', size: 26 },
  { id: 'wordcloud', name: 'ECharts 词云', category: 'visual', desc: '标签热度可视化（echarts-wordcloud）', size: 24 },
  { id: 'cropper', name: 'vue-cropper 裁剪', category: 'visual', desc: '上传前图片裁剪与构图调整', size: 24 },
  { id: 'colorpicker', name: 'vue3-colorpicker', category: 'visual', desc: '主题与配色取色器', size: 22 },
  { id: 'justified', name: '瀑布流图片墙', category: 'visual', desc: '自适应等高排版，滚动加载不跳动', size: 24 },

  // ---------- 后端与高并发 ----------
  { id: 'spring', name: 'Spring Boot 2.7', category: 'backend', desc: '分层架构、自动装配与全局异常处理', size: 34 },
  { id: 'mp', name: 'MyBatis-Plus', category: 'backend', desc: 'ORM 增强：分页插件、逻辑删除、条件构造器', size: 30 },
  { id: 'satoken', name: 'Sa-Token', category: 'backend', desc: '登录鉴权 + 空间 RBAC 细粒度权限', size: 30 },
  { id: 'mysql', name: 'MySQL 8', category: 'backend', desc: '业务主库：索引优化、逻辑删除、JSON 标签', size: 30 },
  { id: 'redis', name: 'Redis', category: 'backend', desc: '会话存储 / 热点缓存 / 签到与任务进度', size: 28 },
  { id: 'sharding', name: 'ShardingSphere', category: 'backend', desc: '图片表按空间维度分表', size: 26 },
  { id: 'ws', name: 'WebSocket 协同', category: 'backend', desc: '多人实时协作编辑同一张图片', size: 28 },
  { id: 'disruptor', name: 'Disruptor 队列', category: 'backend', desc: '无锁环形队列削峰，异步处理编辑事件', size: 26 },
  { id: 'aop', name: 'AOP 注解鉴权', category: 'backend', desc: '切面式权限校验，业务代码零侵入', size: 24 },

  // ---------- AI 与云服务 ----------
  { id: 'dashscope', name: '阿里云百炼', category: 'ai', desc: 'DashScope AI 扩图（out-painting）异步任务', size: 32 },
  { id: 'aiedit', name: 'AI 智能配文', category: 'ai', desc: '自动生成名称 / 简介 / 分类 / 标签', size: 28 },
  { id: 'cos', name: '腾讯云 COS', category: 'ai', desc: '对象存储 + 缩略图生成 + 主色调提取', size: 30 },
  { id: 'pexels', name: 'Pexels 图库', category: 'ai', desc: '高清素材抓取源，批量入库', size: 24 },
  { id: 'bing', name: '必应图片抓取', category: 'ai', desc: 'Jsoup 解析搜索结果，进度 Redis 轮询', size: 24 },
  { id: 'mail', name: '邮箱验证码', category: 'ai', desc: 'Spring Mail 注册验证，60 秒防刷', size: 22 },

  // ---------- 工程与体验 ----------
  { id: 'docker', name: 'Docker', category: 'ops', desc: '一键拉起 MySQL / Redis 依赖', size: 24 },
  { id: 'nginx', name: 'Nginx', category: 'ops', desc: '静态资源托管 + 后端反向代理', size: 24 },
  { id: 'compress', name: 'Gzip / Brotli', category: 'ops', desc: '构建期预压缩，首屏体积大幅下降', size: 22 },
  { id: 'capacitor', name: 'Capacitor', category: 'ops', desc: 'Web 应用打包为安卓 App', size: 24 },
  { id: 'season', name: '四季主题', category: 'ops', desc: '春夏秋冬四套设计令牌随季节切换', size: 24 },
  { id: 'dark', name: '深色模式', category: 'ops', desc: '全站明暗双主题，图表同步适配', size: 24 },
  { id: 'notify', name: '站内通知', category: 'ops', desc: '审核结果 / 系统公告实时推送', size: 24 },
]

/**
 * 连线结构：以语义关联为主，中枢只连各领域核心节点。
 *
 * 关键设计：中枢【不】直连全部节点 —— 否则悬停中枢时 ECharts 的
 * adjacency 高亮会把所有节点都算作"相邻"，等于没有高亮效果。
 * 各领域内部靠语义边连成有机网状，力导向会自然形成疏密有致的星云。
 */
export const TECH_LINKS: TechLink[] = [
  // 中枢 -> 各领域核心节点
  { source: HUB_ID, target: 'vue' },
  { source: HUB_ID, target: 'spring' },
  { source: HUB_ID, target: 'echarts' },
  { source: HUB_ID, target: 'dashscope' },
  { source: HUB_ID, target: 'cos' },
  { source: HUB_ID, target: 'docker' },

  // 前端交互链
  { source: 'vue', target: 'router' },
  { source: 'vue', target: 'pinia' },
  { source: 'vue', target: 'antd' },
  { source: 'vue', target: 'vueuse' },
  { source: 'vue', target: 'ts' },
  { source: 'vite', target: 'vue' },
  { source: 'vite', target: 'ts' },
  { source: 'vite', target: 'compress' },
  { source: 'ts', target: 'axios' },
  { source: 'axios', target: 'antd' },

  // 可视化与媒体链
  { source: 'echarts', target: 'vecharts' },
  { source: 'echarts', target: 'wordcloud' },
  { source: 'echarts', target: 'justified' },
  { source: 'cropper', target: 'cos' },
  { source: 'colorpicker', target: 'antd' },

  // 后端与高并发链
  { source: 'spring', target: 'mp' },
  { source: 'spring', target: 'satoken' },
  { source: 'spring', target: 'mail' },
  { source: 'spring', target: 'aop' },
  { source: 'mp', target: 'mysql' },
  { source: 'mp', target: 'sharding' },
  { source: 'satoken', target: 'redis' },
  { source: 'satoken', target: 'aop' },
  { source: 'ws', target: 'disruptor' },
  { source: 'ws', target: 'spring' },
  { source: 'redis', target: 'mysql' },
  { source: 'sharding', target: 'mysql' },

  // AI 与云服务链
  { source: 'dashscope', target: 'aiedit' },
  { source: 'cos', target: 'mail' },
  { source: 'pexels', target: 'bing' },
  { source: 'bing', target: 'dashscope' },
  { source: 'pexels', target: 'justified' },

  // 工程与体验链
  { source: 'docker', target: 'nginx' },
  { source: 'docker', target: 'mysql' },
  { source: 'docker', target: 'redis' },
  { source: 'nginx', target: 'compress' },
  { source: 'dark', target: 'season' },
  { source: 'dark', target: 'colorpicker' },
  { source: 'notify', target: 'ws' },
  { source: 'notify', target: 'mail' },
  { source: 'capacitor', target: 'vue' },
  { source: 'justified', target: 'cropper' },
]
