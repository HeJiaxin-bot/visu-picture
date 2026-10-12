# Visu · 视界云图库 🖼️

> 一个基于 **Spring Boot 2.7 + Vue 3** 的企业级云图库协作平台：AI 扩图、团队空间协作、站内消息通知、积分签到、邀请解锁会员，一站式图片管理与创作。

<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-2.7.6-green" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/Vue-3.5-blue" alt="Vue 3" />
  <img src="https://img.shields.io/badge/Ant%20Design%20Vue-4.x-brightgreen" alt="Ant Design Vue" />
  <img src="https://img.shields.io/badge/ECharts-5.5-orange" alt="ECharts" />
  <img src="https://img.shields.io/badge/license-MIT-lightgrey" alt="License" />
</p>

<p align="center">
  <a href="https://github.com/HeJiaxin-bot/visu-picture">GitHub</a> ·
  <a href="https://gitee.com/whirlwind-nanny/visu-picture">Gitee</a>
</p>

---

## 📸 项目预览

![首页 - 公共图库](docs/screenshots/home.png)

| 邀请计划 | 用户中心 |
|:---:|:---:|
| ![邀请计划](docs/screenshots/invite.png) | ![用户中心](docs/screenshots/usercenter.png) |
| **我的空间** | **图片详情（点赞 / 收藏 / 评论）** |
| ![我的空间](docs/screenshots/mySpace.png) | ![图片详情](docs/screenshots/picture-detail.png) |
| **我的收藏** | **公开分享页** |
| ![我的收藏](docs/screenshots/my-interaction.png) | ![公开分享](docs/screenshots/public-share.png) |
| **登录页** | **注册页** |
| ![登录页](docs/screenshots/login.png) | ![注册页](docs/screenshots/register.png) |
| **操作审计日志** | **用户协议** |
| ![审计日志](docs/screenshots/audit-log.png) | ![用户协议](docs/screenshots/agreement.png) |

> 💡 访问站内 `/tech-stack` 可查看 **ECharts 力导向驱动的技术栈全景图**（含分类筛选与悬停高亮）。

---

## ✨ 功能特性

### 图片与创作
- 🖼️ **图片管理** — 上传、URL 导入、批量抓取入库、关键词搜索、以图搜图、以颜色搜图、标签分类、审核流转
- 🤖 **AI 扩图** — 接入阿里云百炼（DashScope）out-painting，异步任务轮询，结果自动打上「AI 生成」标识
- ✍️ **AI 智能配文** — 一次调用自动生成名称、简介、分类与标签
- 📦 **批量操作** — 批量抓取（必应 / Pexels 双源）、批量重命名、批量编辑分类标签、批量上传

### 团队协作
- 👥 **空间体系** — 私人空间 + 团队空间，三级空间级别（普通 / 专业 / 旗舰）
- 🔐 **RBAC 权限** — 空间成员细粒度权限（查看 / 上传 / 编辑 / 删除 / 管理），注解式鉴权切面
- ⚡ **实时协同** — WebSocket + Disruptor 无锁队列，多人同时编辑同一张图片
- 📊 **空间分析** — ECharts 可视化：使用趋势、容量占比、成员活跃排行、标签词云

### 会员与激励
- 💎 **会员体系** — `isVip` 字段与用户角色解耦，管理员兑换会员不再丢失管理权限
- 🎫 **邀请解锁** — 专属邀请码，每邀 1 人得 12 个月会员，累计 3 人升级**永久会员**，附邀请排行榜
- 🔑 **激活码兑换** — 激活码兑换会员，8 位校验 + 错误抖动 + 权益确认弹窗
- 🪙 **积分签到** — 每日签到攒积分（50 初始 / 5 每日 / 20 每次 AI 扩图）

### 互动与收藏
- ❤️ **点赞去重** — 独立 `picture_like` 记录表 + 唯一索引 + `INSERT IGNORE`，连点 / 脚本 / 并发都无法刷高计数
- ⭐ **收藏夹** — `picture_favorite` 关联表，「我的收藏 / 我的点赞 / 最近查看」三合一页面，按时间倒序
- 💬 **图片评论** — 500 字上限、服务端分页、本人或管理员可删，匿名可读、登录可发
- 🕘 **浏览历史** — `ON DUPLICATE KEY UPDATE` 单语句幂等写入，同图只留一条，超 200 条自动清理最旧
- 🔗 **公开分享链接** — 16 位随机分享码 + 有效期（7 天 / 30 天 / 永久）+ 可撤销，分享页无需登录
- 🔍 **重复图片检测** — 上传时计算原始文件 SHA-256，同一空间内字节级重复直接拦截并提示已有图片

### 消息与通知
- 🔔 **站内通知** — 导航栏铃铛 + 未读角标，悬停预览最近消息，点击直达消息中心
- 📋 **消息中心** — 四类筛选（全部 / 审核通知 / 系统通知 / 未读），服务端分页每页 10 条
- 🔄 **审核双向通知** — 用户上传图片后**通知全部管理员**待审核；管理员审核后**通知作者**结果与理由
- 💌 **互动通知** — 有人评论你的图片时自动提醒（自己评论自己不打扰）
- 📢 **管理员发布消息** — 向全员或指定用户推送站内公告，带实时预览
- 📧 **邮箱验证码** — Spring Mail 驱动，60 秒防刷倒计时

### 安全与治理
- 🚦 **接口限流** — `@RateLimit` 注解 + Redis 固定窗口计数，按 IP / 用户 / 双维度限流
- 📝 **操作审计日志** — `@AuditLog` 注解自动留痕：操作人、模块、动作、目标、IP、耗时、成败
- 🛡️ **纵深防御** — SSRF 防护、排序字段白名单、归属校验兜底、CORS 按环境隔离

### 平台与体验
- 🌗 **主题系统** — 深色模式 + 四季配色（春樱 / 夏蓝 / 秋金 / 冬青）设计令牌
- 🔐 **合规三件套** — 用户协议/隐私政策页、注册登录协议勾选、账号自助注销
- 📱 **移动端适配** — 响应式布局 + 移动端抽屉导航 + Capacitor 打包安卓 App
- 🎨 **技术栈全景图** — ECharts 力导向关系图，5 大领域 36 项技术可视化
- ✅ **类型安全** — 前端 `vue-tsc` 类型检查 **0 错误**，生产构建零告警通过

---

## 🛠️ 技术栈

| 层次 | 技术 |
|:---|:---|
| **前端** | Vue 3.5 · Vite 5 · TypeScript（`vue-tsc` 0 错误）· Pinia · Vue Router 4 · Ant Design Vue 4 · ECharts 5 · Axios |
| **后端** | Spring Boot 2.7.6 · MyBatis-Plus · Sa-Token（登录鉴权 / RBAC）· AOP 注解鉴权 / 限流 / 审计 |
| **数据** | MySQL 8（11 张表）· Redis（会话 / 缓存 / 限流计数 / 任务进度）· ShardingSphere |
| **存储** | 腾讯云 COS（对象存储 + 缩略图 + 主色调提取） |
| **AI** | 阿里云百炼 DashScope（AI 扩图 / 智能配文） |
| **实时** | WebSocket · Disruptor 无锁队列 · Spring Session + Redis |
| **互动** | 点赞去重 · 收藏夹 · 图片评论 · 浏览历史 · 公开分享链接 · 重复图片检测 |
| **治理** | 接口限流（固定窗口）· 操作审计日志 · SSRF 防护 · 排序字段白名单 |
| **媒体** | vue-cropper 裁剪 · 瀑布流布局 · vue3-colorpicker |
| **消息** | Spring Mail（邮箱验证码）· 站内通知中心 |
| **部署** | Nginx · Docker · Capacitor（Android） |

---

## 🏗️ 系统架构

```mermaid
graph TD
    U[🖥️ 用户端<br/>Vue 3 SPA] -->|HTTP / HTTPS| N[Nginx<br/>静态资源 + 反向代理]
    N -->|HTTP :8123| C[Controller 层<br/>10 个 Controller]
    C -->|AOP 切面| X[鉴权 / 限流 / 审计]
    C -->|接口调用| S[Service 层<br/>业务逻辑 / 权限校验]
    S -->|Mapper 接口| D[Dao 层<br/>MyBatis-Plus]
    D -->|JDBC| DB[(MySQL 8<br/>11 张表)]
    S -->|Redis Protocol| R[(Redis<br/>会话 / 缓存 / 限流计数 / 进度)]
    S -->|COS SDK| O[(腾讯云 COS<br/>图片对象存储)]
    S -->|DashScope SDK| A[阿里云百炼<br/>AI 扩图异步任务]
    S -->|WebSocket| W[协同编辑<br/>Disruptor 队列]
    S -->|SMTP| M[Spring Mail<br/>邮箱验证码]
```

> **数据流向**：用户请求经 Nginx 转发至 Spring Boot，经鉴权 / 限流 / 审计三道 AOP 切面后由 Service 处理业务——图片文件写入 COS、热点数据与会话进 Redis、业务数据落 MySQL，AI 扩图提交百炼异步任务并轮询结果，协同编辑经 WebSocket 推送并由 Disruptor 削峰；互动数据（点赞 / 收藏 / 评论 / 浏览 / 分享）与审计日志写入各自的关联表。

### 核心数据模型

```mermaid
erDiagram
    user ||--o{ picture : "上传"
    user ||--o{ space : "创建"
    user ||--o{ space_user : "加入"
    user ||--o{ notification : "接收"
    user ||--o{ picture_like : "点赞"
    user ||--o{ picture_favorite : "收藏"
    user ||--o{ picture_comment : "评论"
    user ||--o{ picture_view_history : "浏览"
    user ||--o{ picture_share : "分享"
    user ||--o{ audit_log : "操作留痕"
    space ||--o{ picture : "包含"
    space ||--o{ space_user : "拥有成员"
    picture ||--o{ notification : "触发"
    picture ||--o{ picture_like : "被点赞"
    picture ||--o{ picture_favorite : "被收藏"
    picture ||--o{ picture_comment : "被评论"
    picture ||--o{ picture_share : "被分享"
```

| 表 | 说明 |
|:---|:---|
| `user` | 用户（含 `isVip` 会员标记、`points` 积分、`inviteCode` 邀请码） |
| `picture` | 图片（审核状态、缩略图、主色调、`picHash` 去重摘要、`likeCount`/`favoriteCount` 计数） |
| `space` | 空间（类型、级别、容量与数量额度、封面） |
| `space_user` | 空间成员与角色（RBAC 权限） |
| `notification` | 站内通知（审核结果 / 系统公告 / 互动） |
| `picture_like` | 点赞记录（唯一索引 `(pictureId, userId)` 实现去重） |
| `picture_favorite` | 收藏记录（同上） |
| `picture_comment` | 图片评论（逻辑删除） |
| `picture_view_history` | 浏览历史（唯一索引 `(userId, pictureId)`，同图只留一条） |
| `picture_share` | 分享链接（随机 `shareCode` + 有效期 + 可撤销） |
| `audit_log` | 操作审计日志（操作人 / 模块 / 动作 / IP / 耗时 / 成败） |

> **计数器与记录分离**：`picture.likeCount` / `favoriteCount` 是展示用**计数器**（列表页直接读取，免 COUNT）；「谁赞过哪张图」是**多对多记录**，落在 `picture_like` / `picture_favorite`。两者在同一事务内更新，保证不漂移。

---

## 🚀 快速开始

### 环境要求

| 依赖 | 版本 | 说明 |
|:---|:---|:---|
| JDK | 11+ | 后端运行时 |
| Maven | 3.8+ | 后端构建 |
| Node.js | 18+ | 前端构建 |
| MySQL | 8.x | 业务数据库 |
| Redis | 6.x+ | 缓存与会话 |

### 1️⃣ 克隆项目

```bash
# Gitee（国内推荐）
git clone https://gitee.com/whirlwind-nanny/visu-picture.git

# 或 GitHub
git clone https://github.com/HeJiaxin-bot/visu-picture.git

cd visu-picture
```

### 2️⃣ 初始化数据库

```bash
# 登录 MySQL 后执行建表脚本（含全部 11 张表的结构与索引）
mysql -uroot -p
source visu-picture-backend/sql/create_table.sql

# 若已有库，按需增量补建（两个脚本都做了幂等处理，可重复执行）
source visu-picture-backend/sql/add_notification_table.sql   # 站内通知表
source visu-picture-backend/sql/add_interaction_tables.sql   # 互动 / 分享 / 审计表 + picture 增量字段
```

> `add_interaction_tables.sql` 使用 `information_schema` 判断后再执行 `ALTER`，
> 因此对已存在的列与索引会自动跳过，不会因重复执行而报错。

### 3️⃣ 配置后端

编辑 `visu-picture-backend/src/main/resources/application-local.yml`，填入你的真实配置：

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/visu?useUnicode=true&serverTimezone=Asia/Shanghai
    username: root        # 数据库用户名
    password: "你的密码"   # 数据库密码
  redis:
    database: 0
    host: localhost
    port: 6379

# 腾讯云 COS 对象存储（用于图片上传）
cos:
  client:
    host: https://你的桶名.cos.你的地域.myqcloud.com
    secretId: 你的SecretId
    secretKey: 你的SecretKey
    region: ap-shanghai
    bucket: 你的桶名

# 阿里云百炼 AI 扩图 / 智能配文
aliyun:
  ai:
    apiKey: 你的DashScopeApiKey

# 邮箱验证码（QQ 邮箱需用授权码）
spring:
  mail:
    host: smtp.qq.com
    username: 你的邮箱
    password: 你的授权码
```

> ⚠️ **安全提示**：`application-local.yml` 含真实密钥，请勿提交到公开仓库。建议改用环境变量：`${COS_SECRET_KEY}` 形式注入。

### 4️⃣ 启动后端

```bash
cd visu-picture-backend
mvn spring-boot:run
# 后端运行在 http://localhost:8123
# 接口文档 http://localhost:8123/api/doc.html
```

### 5️⃣ 启动前端

```bash
cd visu-picture-frontend
npm install
npm run dev
# 访问 http://localhost:5173
```

### 🐳 Docker 一键拉起依赖

不想本机装 MySQL / Redis？两条命令拉起：

```bash
docker run -d --name visu-mysql -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=123456 -e MYSQL_DATABASE=visu mysql:8

docker run -d --name visu-redis -p 6379:6379 redis:6
```

---

## 📁 项目结构

```text
visu-picture
├── visu-picture-backend              # 后端服务 (Spring Boot)
│   ├── src/main/java/com/visupicture
│   │   ├── annotation                # 自定义注解（@AuthCheck 角色 / @RateLimit 限流 / @AuditLog 审计）
│   │   ├── aop                       # 切面（鉴权 / 限流 / 审计）
│   │   ├── api/aliyunai              # 阿里云百炼 AI 扩图 / 配文封装
│   │   ├── common                    # 通用响应体、分页请求
│   │   ├── config                    # 配置类（COS、Sa-Token、CORS、MyBatis-Plus、请求包装）
│   │   ├── constant                  # 常量（用户角色 / 积分 / 邀请规则 / 排序字段白名单）
│   │   ├── controller                # 接口层（10 个 Controller）
│   │   ├── exception                 # 业务异常与全局异常处理
│   │   ├── manager                   # COS 管理器 · 分表 · WebSocket 协同 · 空间鉴权 · 上传模板
│   │   ├── mapper                    # MyBatis-Plus Mapper（11 个）
│   │   ├── model
│   │   │   ├── dto                   # 请求对象
│   │   │   ├── entity                # 数据库实体（11 张表）
│   │   │   ├── enums                 # 状态 / 角色 / 通知类型枚举
│   │   │   └── vo                    # 视图对象（脱敏）
│   │   ├── service                   # 业务逻辑层 + impl 实现
│   │   └── utils                     # 颜色相似度 / SSRF 防护 / IP 提取 / 图片摘要
│   ├── src/main/resources
│   │   ├── biz/vipCode.json          # 会员激活码
│   │   └── mapper/*.xml              # MyBatis 映射文件
│   └── sql
│       ├── create_table.sql          # 完整建表脚本（11 张表 + 索引）
│       ├── add_notification_table.sql# 通知表增量脚本
│       └── add_interaction_tables.sql# 互动 / 分享 / 审计增量脚本（含幂等 ALTER）
│
└── visu-picture-frontend             # 前端应用 (Vue 3 + Vite)
    └── src
        ├── api                       # OpenAPI 生成 + 手写接口客户端（含 interaction.ts）
        ├── assets                    # 全局样式与设计令牌（四季配色）
        ├── components                # 通用组件（图片列表 / 上传 / 裁剪 / 扩图 / 评论 / 分享）
        ├── composables               # 组合式函数（图片详情逻辑复用）
        ├── constants                 # 前端常量
        ├── data                      # 技术栈全景图数据
        ├── layouts                   # 全局布局（顶栏 / 页脚 / 悬浮操作）
        ├── pages                     # 页面（29 个 Vue 页面）
        ├── router                    # 路由（懒加载分包）
        ├── stores                    # Pinia 全局状态（登录态 / 主题 / 点赞收藏）
        ├── types                     # 第三方库类型补充声明
        └── utils                     # 工具函数（WebSocket 封装 / id 归一化等）
```

---

## 🔌 API 接口概览

### 用户与鉴权

| 方法 | 路径 | 说明 |
|:---:|:---|:---|
| `POST` | `/api/user/register` | 邮箱 + 验证码注册，支持携带邀请码 |
| `POST` | `/api/user/login` | 账号密码登录（Spring Session + Redis） |
| `POST` | `/api/user/exchange/vip` | 激活码兑换会员 |
| `POST` | `/api/user/sign_in` | 每日签到获取积分 |
| `GET` | `/api/user/invite/info` | 我的邀请信息（邀请码 / 进度 / 会员状态） |
| `GET` | `/api/user/invite/rank` | 邀请排行榜 |

### 图片

| 方法 | 路径 | 说明 |
|:---:|:---|:---|
| `POST` | `/api/picture/upload` | 上传图片文件 |
| `POST` | `/api/picture/upload/url` | 按 URL 上传（AI 扩图结果携带 `isAiGenerated`） |
| `POST` | `/api/picture/upload/batch` | 按关键词批量抓取入库（必应 / Pexels） |
| `POST` | `/api/picture/review` | 管理员审核（通过 / 拒绝 + 理由） |
| `POST` | `/api/picture/ai_edit` | AI 智能配文 |
| `POST` | `/api/picture/out_painting/create_task` | 创建 AI 扩图异步任务 |
| `POST` | `/api/picture/search/picture` | 以图搜图 |
| `POST` | `/api/picture/search/color` | 以颜色搜图 |

### 空间与分析

| 方法 | 路径 | 说明 |
|:---:|:---|:---|
| `POST` | `/api/space/add` | 创建空间（私人 / 团队） |
| `POST` | `/api/space/list/page/vo` | 分页查询空间 |
| `GET` | `/api/space/analyze/usage` | 空间使用分析（供 ECharts 渲染） |
| `GET` | `/api/space/analyze/tag` | 标签词云数据 |

### 站内通知

| 方法 | 路径 | 说明 |
|:---:|:---|:---|
| `GET` | `/api/notification/list` | 最近通知（顶栏铃铛预览） |
| `GET` | `/api/notification/list/page` | 分页查询（`category`: all / review / system / unread） |
| `GET` | `/api/notification/unread/count` | 未读数量（角标轮询） |
| `POST` | `/api/notification/read` | 标记单条已读 |
| `POST` | `/api/notification/read/all` | 全部标记已读 |
| `POST` | `/api/notification/send` | **管理员**发布系统消息（全员 / 指定用户） |

### 互动（点赞 / 收藏 / 评论 / 浏览）

| 方法 | 路径 | 说明 |
|:---:|:---|:---|
| `POST` | `/api/picture/like` | 点赞 / 取消（幂等，重复调用不重复计数） |
| `POST` | `/api/picture/favorite` | 收藏 / 取消（幂等） |
| `GET` | `/api/picture/interaction/state` | 查询当前用户的点赞 / 收藏状态 |
| `GET` | `/api/picture/my/favorite` | 我的收藏（按收藏时间倒序） |
| `GET` | `/api/picture/my/like` | 我的点赞（按点赞时间倒序） |
| `POST` | `/api/picture/comment/add` | 发表评论（≤500 字，限流 5 次/分钟） |
| `GET` | `/api/picture/comment/list` | 评论分页（无需登录） |
| `POST` | `/api/picture/comment/delete` | 删除评论（本人或管理员） |
| `POST` | `/api/picture/view/record` | 记录浏览（同图只留一条） |
| `GET` | `/api/picture/view/history` | 我的最近查看 |
| `POST` | `/api/picture/view/clear` | 清空浏览历史 |

### 分享链接（公开访问）

| 方法 | 路径 | 说明 |
|:---:|:---|:---|
| `POST` | `/api/share/create` | 创建分享链接（仅所有者 / 管理员，可设有效期） |
| `GET` | `/api/share/get` | 查询图片当前的分享链接 |
| `POST` | `/api/share/revoke` | 撤销分享（链接立即失效） |
| `GET` | `/api/share/my/list` | 我创建的全部分享链接 |
| `GET` | `/api/share/public/{shareCode}` | **无需登录**公开访问分享的图片 |

### 审计（仅管理员）

| 方法 | 路径 | 说明 |
|:---:|:---|:---|
| `GET` | `/api/audit/list/page` | 分页查询操作审计日志（支持模块 / 动作 / 操作人 / 时间筛选） |

---

## 🎯 关键设计

### 会员状态与角色解耦

早期实现中，兑换会员会直接改写 `userRole` 为 `vip`，导致**管理员兑换后丢失管理权限**。现改为独立字段：

```sql
isVip tinyint NOT NULL DEFAULT 0 COMMENT '是否会员：0-否 1-是'
```

兑换会员 / 邀请奖励只写 `vipExpireTime` 与 `isVip`，**永不改动 `userRole`**，管理员兑换后仍保留 admin 权限。所有会员判断统一走 `isVip` 字段（旧数据回退按到期时间判断）。

### 审核流程双向通知

```
用户上传 → 图片落库（reviewStatus=0 待审核）
         → 通知全部管理员「有新的图片待审核」（批量上传聚合为一条，避免刷屏）

管理员审核 → 更新 reviewStatus + reviewerId + reviewMessage
          → 通知作者「已通过」或「未通过 + 审核意见」
          → 通知发送失败仅记日志，不影响审核主流程
```

### 前端防重复提交

所有提交类操作均采用「同步置位 + 立即判断」的双重防护：

```ts
if (submitting.value) return      // 同步判断，连点直接拦截
submitting.value = true           // 在第一个 await 之前锁住
```

配合上传去重（共享 Promise，并发触发只上传一次），避免连点生成多条待审核记录。

### 点赞去重的幂等设计

**问题**：原实现直接 `likeCount = likeCount + 1`，没有任何「谁赞过」的记录，同一用户连点按钮、写脚本或并发请求都能把计数无限刷高。

**方案**：计数是否变更**由记录表的写入结果决定**，而不是由前端传参决定。

```sql
CREATE TABLE picture_like (
  pictureId bigint NOT NULL,
  userId    bigint NOT NULL,
  UNIQUE KEY uk_pictureId_userId (pictureId, userId)   -- 去重的根本保证
);
```

```java
// 点赞：INSERT IGNORE 命中唯一索引返回 0（已赞过）→ 不动计数
int inserted = pictureLikeMapper.insertIgnore(pictureId, userId);
if (inserted == 1) {                       // 仅"首次点赞"才 +1
    update("likeCount = likeCount + 1");
}
// 取消：DELETE 返回 0（本来没赞）→ 不动计数；用 GREATEST 兜底不为负
```

记录与计数在**同一事务**内提交，不会出现「记录成功但计数没加」的漂移。实测：连续 8 次点赞计数不变，12 请求并发点赞只 +1。

### 浏览历史的单语句幂等

```sql
INSERT INTO picture_view_history (userId, pictureId, viewTime, viewCount)
VALUES (?, ?, NOW(), 1)
ON DUPLICATE KEY UPDATE viewTime = NOW(), viewCount = viewCount + 1
```

一条 SQL 完成「首次插入 / 重复刷新时间并累加次数」，无需先查后判断，因此**没有竞态**，同一张图也永远不会重复占位。配合唯一索引 `(userId, pictureId)` 作为约束基础。

### 接口限流：IP 与用户阈值必须分开

`@RateLimit` 基于 Redis 固定窗口计数，`INCR` 原子自增并在返回 1 时设置过期（计数与过期只在窗口首个请求发生一次，无「先读后写」竞态）。

**关键取舍**：`IP_AND_USER` 双维度限流时，两个维度**不能共用同一阈值**。同一出口 IP 后面可能有大量正常用户（校园网、公司 NAT、运营商 CGNAT），若都用「每人每分钟 5 条评论」，实际会退化成「这个 IP 后面所有人加起来每分钟 5 条」——一个活跃用户就能误伤整个网段。因此：

- **用户维度**承载精细控制（如评论 5 次/分钟）
- **IP 维度**只做兜底防批量小号，阈值放宽（`ipCount`，默认 `count × 6`）

Redis 不可用时默认**拒绝**（安全优先），可通过 `failOpen = true` 改为放行并记日志。

### 审计日志：旁路能力，绝不影响业务

`@AuditLog` 切面记录操作人、模块、动作、目标、IP、耗时与成败，业务抛异常时也会留痕（`success=0` + `errorMessage`）后原样抛出，不改变异常语义。

所有落库委托给 `recordQuietly()`，内部吞掉全部异常 —— 审计是旁路能力，任何审计故障都不应导致业务失败。审计日志查询接口本身**不再打审计**，避免「管理员看审计」产生自引用噪音。

### 两类必须避开的精度陷阱

**1. 雪花 ID 不能被 `Number()` 转换**

后端 `JsonConfig` 把 `Long` 序列化为 **String**，正是为了规避 JS 精度丢失：

```js
Number("2100942816594628609")  // → 2100942816594628600  ❌ 末位被抹掉 → 接口 404
```

因此所有实体 id 在前后端之间**一律以字符串传递**。前端提供 `firstQueryValue()`（保留字符串）与 `toNumberOrUndefined()`（仅用于分页等真正数值参数）两个工具，并在注释中明确区分用途。

**2. 请求体必须显式按 UTF-8 解码**

`RequestWrapper` 用 `new InputStreamReader(inputStream)` 与 `body.getBytes()` 时，两者都依赖 **JVM 平台默认字符集**。在中文 Windows（GBK）下会把 UTF-8 字节按 GBK 解码再编码，无法映射的字符退化成 `?`（0x3F），Jackson 随即抛 `Invalid UTF-8 middle byte 0x3f`。

后果是：凡经过该过滤器的 JSON 请求，只要载荷含中文（图片名、简介、评论）就可能被静默破坏，且是否出错取决于字节序列能否在 GBK 下无损往返 —— 表现为**时好时坏**，极难排查。修复方式：读写两端显式指定 `StandardCharsets.UTF_8`。

### 重复图片检测的能力边界

上传时对**原始文件字节流**计算 SHA-256 存入 `picture.picHash`，同一空间内哈希相同即拦截（跨空间不比对，避免通过「能否上传成功」探测他人是否传过同一张图）。

必须明确其边界 —— 本方案只识别**字节级重复**：

| 能识别 | 不能识别 |
|:---|:---|
| 同一文件重复上传 | 重新编码（转格式、改质量、加水印、调尺寸） |
| 同一直链重复抓取 | 同画面裁剪出的小图 |
| | 视觉相似但像素不同的图 |

要覆盖右列需要感知哈希（pHash/dHash）或向量检索，属于该字段职责之外，因此命名为 `picHash` 而非「相似度」，避免高估能力。

---

## 🤝 贡献指南

1. **Fork** 本仓库，从 `master` 切出功能分支：`git checkout -b feature/your-feature`
2. 提交遵循 Conventional Commits：`feat: 新增xx` / `fix: 修复xx` / `refactor: 重构xx`
3. 推送分支并发起 **Pull Request**，描述清楚改动动机与验证方式
4. 通过审查后由维护者合并回 `master`

### 开发提示

- 前端 API 客户端由 OpenAPI 自动生成：先启动后端，再执行 `npm run openapi`
- 后端配置分环境：`application.yml`（公共）+ `application-local.yml`（本地密钥）
- 遇到 git 推送被重置，检查 hosts 是否被代理工具篡改（如 `127.0.0.1 github.com`）

---

## 📄 许可证

本项目基于 [MIT License](LICENSE) 开源，可自由使用、修改与分发。

---

<p align="center">
  <sub>Built with ❤️ using Spring Boot & Vue 3</sub>
</p>
