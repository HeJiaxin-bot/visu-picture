<template>
  <div id="globalHeader">
    <div class="header-inner">
      <!-- 左侧 Logo（点击回首页） -->
      <router-link to="/" class="brand-link">
        <div class="title-bar">
          <img class="logo" src="../assets/logo-full.svg" alt="visu 视界云图库" />
          <div class="title">视界云图库</div>
        </div>
      </router-link>
      <!-- 右侧：主导航组（页面入口） + 工具操作组（积分/发布/用户） -->
      <nav class="nav-wrap" aria-label="主导航">
        <!-- 主导航组：低频展示、页面级入口 -->
        <div class="nav-main">
          <!-- 一级入口：首页（内容发现主入口） -->
          <router-link to="/" class="nav-link" aria-label="回到首页">首页</router-link>
          <!-- 我的团队：深色下拉面板（含团队列表与创建入口） -->
          <a-dropdown
            v-if="loginUserStore.loginUser.id"
            trigger="['hover', 'click']"
            @open-change="(v: boolean) => (teamDropdownOpen = v)"
          >
            <div class="team-trigger">
              <TeamOutlined class="team-icon" />
              <span>我的团队</span>
              <DownOutlined class="team-trigger-arrow" :class="{ open: teamDropdownOpen }" />
            </div>
            <template #overlay>
              <div class="team-panel">
                <div
                  v-for="spaceUser in teamSpaceList"
                  :key="spaceUser.spaceId"
                  class="team-panel-item"
                  @click="goTeamSpace(spaceUser.spaceId)"
                >
                  <div class="item-title">{{ spaceUser.space?.spaceName ?? '未命名团队' }}</div>
                  <div class="item-desc">团队空间 · {{ roleText(spaceUser.spaceRole) }}</div>
                </div>
                <div v-if="teamSpaceList.length > 0" class="team-panel-divider"></div>
                <div class="team-panel-item" @click="goCreateTeam">
                  <div class="item-title">＋ 创建团队</div>
                  <div class="item-desc">发起多人协作</div>
                </div>
              </div>
            </template>
          </a-dropdown>

          <!-- 系统管理下拉：仅平台管理员可见（低频管理功能收纳） -->
          <a-dropdown
            v-if="isAdmin"
            trigger="['hover', 'click']"
            @open-change="(v: boolean) => (adminDropdownOpen = v)"
          >
            <div class="team-trigger">
              <SettingOutlined class="team-icon" />
              <span>系统管理</span>
              <DownOutlined class="team-trigger-arrow" :class="{ open: adminDropdownOpen }" />
            </div>
            <template #overlay>
              <div class="team-panel">
                <div class="team-panel-item" @click="router.push('/admin/userManage')">
                  <div class="item-title">用户管理</div>
                  <div class="item-desc">账号与角色维护</div>
                </div>
                <div class="team-panel-item" @click="router.push('/admin/pictureManage')">
                  <div class="item-title">图片管理</div>
                  <div class="item-desc">图片审核与清理</div>
                </div>
                <div class="team-panel-item" @click="router.push('/admin/spaceManage')">
                  <div class="item-title">空间管理</div>
                  <div class="item-desc">全部空间治理</div>
                </div>
                <div class="team-panel-divider"></div>
                <div class="team-panel-item" @click="router.push('/admin/notification')">
                  <div class="item-title">发布消息</div>
                  <div class="item-desc">向用户推送站内通知</div>
                </div>
              </div>
            </template>
          </a-dropdown>
        </div>

        <!-- 工具操作组：高频、个性化元素靠右聚集 -->
        <div class="nav-actions">
          <!-- 消息通知铃铛：未读角标 + 悬停预览面板 -->
          <a-dropdown
            v-if="loginUserStore.loginUser.id"
            trigger="['hover', 'click']"
            @open-change="(v: boolean) => (notifyOpen = v)"
          >
            <div class="notify-trigger">
              <BellOutlined class="notify-icon" />
              <span v-if="unreadCount > 0" class="notify-badge">{{ badgeText }}</span>
            </div>
            <template #overlay>
              <div class="team-panel notify-panel">
                <div class="notify-head">
                  <span class="notify-head-title">消息通知</span>
                  <a-button
                    v-if="unreadCount > 0"
                    type="link"
                    size="small"
                    class="notify-readall"
                    @click="handleReadAll"
                  >
                    全部已读
                  </a-button>
                </div>
                <template v-if="notifyList.length > 0">
                  <div
                    v-for="item in notifyList"
                    :key="item.id"
                    class="notify-item"
                    :class="{ unread: item.isRead === 0 }"
                    @click="handleNotifyClick(item)"
                  >
                    <img
                      v-if="item.picThumbnail"
                      :src="item.picThumbnail"
                      class="notify-thumb"
                      alt=""
                    />
                    <div v-else class="notify-thumb notify-thumb--icon">
                      <PictureOutlined />
                    </div>
                    <div class="notify-body">
                      <div class="notify-item-title">
                        {{ item.title }}
                        <span
                          v-if="item.type === 'audit'"
                          class="notify-type-tag notify-type-tag--audit"
                        >待审核</span>
                        <span
                          v-else-if="item.type === 'system'"
                          class="notify-type-tag notify-type-tag--system"
                        >系统</span>
                      </div>
                      <div class="notify-item-desc">{{ item.content }}</div>
                      <div class="notify-item-time">{{ formatNotifyTime(item.createTime) }}</div>
                    </div>
                    <span v-if="item.isRead === 0" class="notify-dot"></span>
                  </div>
                </template>
                <div v-else class="notify-empty">暂无消息，快去发布图片吧～</div>
                <div class="notify-foot" @click="router.push('/user/notifications')">
                  查看全部消息
                </div>
              </div>
            </template>
          </a-dropdown>

          <!-- 剩余积分徽章（点击去用户中心签到） -->
          <a-tooltip v-if="loginUserStore.loginUser.id" title="点击签到获取积分">
            <div class="points-badge" @click="router.push('/user/center')">
              <svg class="points-icon" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z" />
                <path d="M19 3l.6 1.6L21 5.2l-1.4.6L19 7.4l-.6-1.6L17 5.2l1.4-.6z" />
              </svg>
              <span class="points-num">{{ loginUserStore.loginUser.points ?? 0 }}</span>
            </div>
          </a-tooltip>

          <!-- 发布按钮（原"创建图片"入口） -->
          <div class="publish-btn" @click="router.push('/add_picture')">
            <PlusOutlined class="publish-icon" />
            <span>发布</span>
          </div>

          <!-- 用户信息展示栏 -->
          <div class="user-login-status">
            <div v-if="loginUserStore.loginUser.id">
              <a-dropdown trigger="['hover', 'click']">
                <a-space class="user-info">
                  <a-avatar :src="loginUserStore.loginUser.userAvatar" :size="30" />
                  {{ loginUserStore.loginUser.userName ?? '视界用户' }}
                </a-space>
                <template #overlay>
                  <div class="team-panel user-panel">
                    <!-- 顶部：用户名 + 身份 -->
                    <div class="user-panel-head">
                      <div class="item-title">
                        {{ loginUserStore.loginUser.userAccount ?? '视界用户' }}
                        <span v-if="isVip" class="vip-chip">
                          <CrownFilled class="vip-chip-icon" />
                          会员
                        </span>
                      </div>
                      <div class="item-desc">
                        {{ loginUserStore.loginUser.userRole === 'admin' ? '管理员' : '普通用户' }}
                      </div>
                    </div>
                    <div class="team-panel-divider"></div>
                    <!-- 菜单项 -->
                    <div class="team-panel-item user-panel-item" @click="router.push('/user/center')">
                      <IdcardOutlined class="item-icon" />
                      <span>用户中心</span>
                    </div>
                    <div class="team-panel-item user-panel-item" @click="router.push('/my_space')">
                      <FolderOutlined class="item-icon" />
                      <span>我的空间</span>
                    </div>
                    <div
                      class="team-panel-item user-panel-item"
                      @click="router.push('/user/settings')"
                    >
                      <SettingOutlined class="item-icon" />
                      <span>设置</span>
                    </div>
                    <div class="team-panel-item user-panel-item" @click="router.push('/user/invite')">
                      <GiftOutlined class="item-icon" />
                      <span>会员中心</span>
                    </div>
                    <div class="team-panel-divider"></div>
                    <div class="team-panel-item user-panel-item" @click="doLogout">
                      <LogoutOutlined class="item-icon" />
                      <span>退出登录</span>
                    </div>
                  </div>
                </template>
              </a-dropdown>
            </div>
            <div v-else>
              <a-space>
                <a-button href="/user/login">登录</a-button>
                <a-button type="primary" href="/user/register">注册</a-button>
              </a-space>
            </div>
          </div>
        </div>
      </nav>
      <!-- 移动端汉堡按钮：中屏隐藏下拉项后出现 -->
      <button
        class="mobile-menu-btn"
        aria-label="打开菜单"
        aria-expanded="false"
        @click="drawerOpen = true"
      >
        <MenuOutlined />
      </button>
    </div>

    <!-- 移动端抽屉导航：与桌面下拉同源的功能分组 -->
    <a-drawer
      v-model:open="drawerOpen"
      placement="right"
      :width="300"
      title="视界云图库"
      class="mobile-drawer"
    >
      <div class="drawer-group">
        <div class="drawer-group-title">浏览</div>
        <div class="drawer-item" @click="go('/')">
          <HomeOutlined class="drawer-icon" />
          <span>首页</span>
        </div>
      </div>
      <template v-if="loginUserStore.loginUser.id">
        <div class="drawer-group">
          <div class="drawer-group-title">创作</div>
          <div class="drawer-item" @click="go('/add_picture')">
            <PlusOutlined class="drawer-icon" />
            <span>发布图片</span>
          </div>
          <div class="drawer-item" @click="go('/my_space')">
            <FolderOutlined class="drawer-icon" />
            <span>我的空间</span>
          </div>
          <div
            v-for="spaceUser in teamSpaceList"
            :key="spaceUser.spaceId"
            class="drawer-item drawer-item--sub"
            @click="goTeamSpace(spaceUser.spaceId)"
          >
            <TeamOutlined class="drawer-icon" />
            <span>{{ spaceUser.space?.spaceName ?? '未命名团队' }}</span>
          </div>
          <div class="drawer-item drawer-item--sub" @click="goCreateTeam">
            <PlusOutlined class="drawer-icon" />
            <span>创建团队</span>
          </div>
        </div>
        <div class="drawer-group">
          <div class="drawer-group-title">账户</div>
          <div class="drawer-item" @click="go('/user/center')">
            <IdcardOutlined class="drawer-icon" />
            <span>用户中心</span>
          </div>
          <div class="drawer-item" @click="go('/user/invite')">
            <GiftOutlined class="drawer-icon" />
            <span>会员中心</span>
          </div>
          <div class="drawer-item" @click="doLogout">
            <LogoutOutlined class="drawer-icon" />
            <span>退出登录</span>
          </div>
        </div>
        <div v-if="isAdmin" class="drawer-group">
          <div class="drawer-group-title">系统管理</div>
          <div class="drawer-item" @click="go('/admin/userManage')">
            <UserOutlined class="drawer-icon" />
            <span>用户管理</span>
          </div>
          <div class="drawer-item" @click="go('/admin/pictureManage')">
            <PictureOutlined class="drawer-icon" />
            <span>图片管理</span>
          </div>
          <div class="drawer-item" @click="go('/admin/spaceManage')">
            <AppstoreOutlined class="drawer-icon" />
            <span>空间管理</span>
          </div>
          <div class="drawer-item" @click="go('/admin/notification')">
            <BellOutlined class="drawer-icon" />
            <span>发布消息</span>
          </div>
        </div>
      </template>
      <template v-else>
        <div class="drawer-group">
          <div class="drawer-item" @click="go('/user/login')">
            <LoginOutlined class="drawer-icon" />
            <span>登录</span>
          </div>
          <div class="drawer-item" @click="go('/user/register')">
            <UserAddOutlined class="drawer-icon" />
            <span>注册</span>
          </div>
        </div>
      </template>
    </a-drawer>
  </div>
</template>
<script lang="ts" setup>
import { ref, computed, watchEffect, onUnmounted } from 'vue'
import {
  LogoutOutlined,
  TeamOutlined,
  DownOutlined,
  IdcardOutlined,
  FolderOutlined,
  PlusOutlined,
  SettingOutlined,
  MenuOutlined,
  HomeOutlined,
  GiftOutlined,
  UserOutlined,
  PictureOutlined,
  AppstoreOutlined,
  LoginOutlined,
  UserAddOutlined,
  CrownFilled,
  BellOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { useRouter, useRoute } from 'vue-router'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { userLogoutUsingPost } from '@/api/userController.ts'
import { SPACE_TYPE_ENUM } from '@/constants/space.ts'
import { listMyTeamSpaceUsingPost } from '@/api/spaceUserController.ts'
import {
  listMyNotifications,
  countMyUnread,
  markNotificationRead,
  markAllNotificationRead,
} from '@/api/notification.ts'
import type { NotificationVO } from '@/api/notification.ts'

const loginUserStore = useLoginUserStore()

// ----- 消息通知（铃铛 + 未读角标 + 预览面板） -----
const notifyOpen = ref(false)
const notifyList = ref<NotificationVO[]>([])
const unreadCount = ref(0)
let unreadTimer: number | undefined

// 角标文案：99+ 封顶
const badgeText = computed(() => (unreadCount.value > 99 ? '99+' : String(unreadCount.value)))

const refreshUnreadCount = async () => {
  if (!loginUserStore.loginUser.id) return
  try {
    const res = await countMyUnread()
    if (res.code === 0 && res.data != null) {
      unreadCount.value = res.data
    }
  } catch {
    // 静默失败，不打扰用户
  }
}

// 悬停/点开铃铛时加载最近 5 条预览
const fetchNotifyPreview = async () => {
  try {
    const res = await listMyNotifications({ current: 1, pageSize: 5 })
    if (res.code === 0 && res.data) {
      notifyList.value = res.data
    }
  } catch {
    // 静默失败
  }
}

// 相对时间文案
const formatNotifyTime = (value?: string) => {
  if (!value) return ''
  const d = new Date(value)
  if (isNaN(d.getTime())) return ''
  const diff = Date.now() - d.getTime()
  const min = Math.floor(diff / 60000)
  if (min < 1) return '刚刚'
  if (min < 60) return `${min} 分钟前`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour} 小时前`
  const day = Math.floor(hour / 24)
  if (day < 7) return `${day} 天前`
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

// 点击消息：跳转目标随类型而定，同时标记已读
// audit（待审核提醒）→ 图片管理页；其余 → 图片详情页（from=notify 时仅保留编辑）
const handleNotifyClick = async (item: NotificationVO) => {
  if (item.isRead === 0 && item.id != null) {
    await markNotificationRead(item.id)
    refreshUnreadCount()
  }
  if (item.type === 'audit') {
    router.push('/admin/pictureManage')
  } else if (item.picId) {
    router.push({ path: `/picture/${item.picId}`, query: { from: 'notify' } })
  } else {
    router.push('/user/center')
  }
}

// 全部已读
const handleReadAll = async () => {
  const res = await markAllNotificationRead()
  if (res.code === 0) {
    message.success('已全部标记为已读')
    unreadCount.value = 0
    fetchNotifyPreview()
  } else {
    message.error(res.message ?? '操作失败')
  }
}

// 登录后轮询未读数（60s 一次，路由变化时也即时刷新）
watchEffect(() => {
  if (loginUserStore.loginUser.id) {
    refreshUnreadCount()
    route.path
  }
})

const startUnreadPolling = () => {
  unreadTimer = window.setInterval(refreshUnreadCount, 60_000)
}

startUnreadPolling()
onUnmounted(() => {
  if (unreadTimer) window.clearInterval(unreadTimer)
})

// 铃铛面板展开时才拉预览列表
watchEffect(() => {
  if (notifyOpen.value) {
    fetchNotifyPreview()
  }
})

// 会员状态：以 isVip 字段为准；无该字段时回退按到期时间判断（与用户中心一致）
const isVip = computed(() => {
  const user = loginUserStore.loginUser
  if (user.isVip != null) {
    return user.isVip === 1
  }
  const expire = user.vipExpireTime
  return !!expire && new Date(expire).getTime() > Date.now()
})

// ----- 我的团队下拉面板 -----
// 角色文案
const roleText = (role?: string) => {
  if (role === 'admin') return '管理员'
  if (role === 'editor') return '编辑者'
  return '成员'
}
// 进入团队空间
const goTeamSpace = (spaceId?: string | number) => {
  if (!spaceId) return
  router.push(`/space/${spaceId}`)
}
// 创建团队
const goCreateTeam = () => {
  router.push('/add_space?type=' + SPACE_TYPE_ENUM.TEAM)
}

// 团队空间列表（原侧边栏的“我的团队”菜单合并到这里）
const teamSpaceList = ref<API.SpaceUserVO[]>([])
const route = useRoute()

// 加载团队空间列表
const fetchTeamSpaceList = async () => {
  const res = await listMyTeamSpaceUsingPost()
  if (res.data.code === 0 && res.data.data) {
    const next = res.data.data ?? []
    // 内容没变化时不更新引用，避免菜单 items 频繁重建导致悬停弹出层失效
    if (JSON.stringify(next) !== JSON.stringify(teamSpaceList.value)) {
      teamSpaceList.value = next
    }
  } else {
    message.error('加载我的团队空间失败，' + res.data.message)
  }
}

/**
 * 监听变量，改变时触发数据的重新加载
 */
watchEffect(() => {
  // 登录才加载；同时依赖路由路径，创建/加入团队后跳转会自动刷新列表
  if (loginUserStore.loginUser.id) {
    route.path
    fetchTeamSpaceList()
  }
})

const router = useRouter()

// 系统管理下拉展开状态（控制箭头旋转）
const adminDropdownOpen = ref(false)
// 是否平台管理员（可见系统管理入口）
const isAdmin = computed(() => loginUserStore.loginUser.userRole === 'admin')

// ----- 移动端抽屉导航 -----
const drawerOpen = ref(false)
// 抽屉内跳转：先关抽屉再路由
const go = (path: string) => {
  drawerOpen.value = false
  router.push(path)
}

// 团队下拉面板展开状态（控制箭头旋转）
const teamDropdownOpen = ref(false)

// 用户注销（抽屉内触发时一并关闭抽屉）
const doLogout = async () => {
  drawerOpen.value = false
  const res = await userLogoutUsingPost()
  if (res.data.code === 0) {
    loginUserStore.setLoginUser({
      userName: '未登录',
    })
    message.success('退出登录成功')
    await router.push('/user/login')
  } else {
    message.error('退出登录失败，' + res.data.message)
  }
}
</script>

<style scoped>
/* 头部整体布局：左 Logo，右侧功能区政府，消除固定列宽造成的空隙 */
#globalHeader .header-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

#globalHeader .brand-link {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

#globalHeader .nav-wrap {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
  gap: 16px;
}

/* 主导航组：紧跟品牌区，页面级入口 */
#globalHeader .nav-main {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

/* 工具操作组：靠右聚集，与主导航之间用弹性空隙隔开 */
#globalHeader .nav-actions {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-left: auto;
  flex-shrink: 0;
}

/* 一级导航链接：与下拉触发器风格一致 */
#globalHeader .nav-link {
  padding: 0 16px;
  height: 40px;
  display: flex;
  align-items: center;
  color: var(--text-primary-light);
  font-size: 15px;
  white-space: nowrap;
  border-radius: 8px;
  transition: color 0.2s ease;
}

#globalHeader .nav-link:hover,
#globalHeader .nav-link.router-link-exact-active {
  color: var(--accent);
}

/* 移动端汉堡按钮：桌面隐藏 */
#globalHeader .mobile-menu-btn {
  display: none;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  font-size: 18px;
  color: var(--text-primary-light);
  background: transparent;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s ease;
}

#globalHeader .mobile-menu-btn:hover {
  background: rgba(64, 169, 255, 0.12);
  color: var(--accent);
}

/* 中屏：主导航组（下拉入口）与积分徽章收进抽屉，仅保留发布/用户 + 汉堡 */
@media (max-width: 992px) {
  #globalHeader .nav-main,
  #globalHeader .points-badge {
    display: none;
  }

  #globalHeader .mobile-menu-btn {
    display: flex;
  }
}

/* 小屏：发布按钮只留图标，导航组隐藏（组内首页链接一并隐藏） */
@media (max-width: 640px) {
  #globalHeader .nav-main {
    display: none;
  }

  #globalHeader .publish-btn {
    padding: 0 12px;
  }

  #globalHeader .publish-btn span {
    display: none;
  }
}

/* ===== 消息通知铃铛 ===== */
#globalHeader .notify-trigger {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  color: var(--text-primary-light);
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}

#globalHeader .notify-trigger:hover {
  background: rgba(64, 169, 255, 0.1);
  color: var(--accent);
}

#globalHeader .notify-icon {
  font-size: 18px;
}

#globalHeader .notify-badge {
  position: absolute;
  top: -2px;
  right: -6px;
  min-width: 17px;
  height: 17px;
  padding: 0 4px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  line-height: 17px;
  text-align: center;
  color: #fff;
  background: #ff4d4f;
  box-shadow: 0 2px 6px rgba(255, 77, 79, 0.4);
}

/* 剩余积分徽章：药丸样式跟随主题（亮蓝光晕），高度与发布按钮对齐 */
#globalHeader .points-badge {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 38px;
  padding: 0 14px;
  border-radius: 999px;
  color: var(--accent);
  font-size: 14px;
  font-weight: 600;
  background: var(--bg-card);
  border: 1.5px solid rgba(64, 169, 255, 0.45);
  box-shadow: 0 2px 10px rgba(64, 169, 255, 0.18);
  cursor: pointer;
  white-space: nowrap;
  user-select: none;
  transition:
    transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1),
    box-shadow 0.25s ease,
    border-color 0.25s ease,
    background 0.25s ease,
    color 0.25s ease;
}

#globalHeader .points-badge:hover {
  transform: translateY(-2px) scale(1.05);
  border-color: var(--accent);
  box-shadow: 0 4px 16px rgba(64, 169, 255, 0.35);
}

#globalHeader .points-badge:active {
  transform: translateY(0) scale(0.98);
}

#globalHeader .points-icon {
  color: currentcolor;
}

#globalHeader .points-num {
  line-height: 1;
}

/* 深色主题：暗底 + 亮蓝光晕描边 */
html.dark #globalHeader .points-badge {
  color: #40a9ff;
  background: rgba(45, 45, 45, 0.92);
  border-color: rgba(64, 169, 255, 0.6);
  box-shadow: 0 0 10px rgba(64, 169, 255, 0.25);
}

html.dark #globalHeader .points-badge:hover {
  border-color: #40a9ff;
  box-shadow: 0 0 16px rgba(64, 169, 255, 0.5);
}

/* 发布按钮（原"创建图片"入口）：亮蓝药丸 + 悬停动效 */
#globalHeader .publish-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 38px;
  padding: 0 20px;
  border-radius: 999px;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  background: linear-gradient(135deg, #40a9ff, #1677ff);
  box-shadow: 0 4px 14px rgba(22, 119, 255, 0.35);
  cursor: pointer;
  white-space: nowrap;
  user-select: none;
  transition:
    transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1),
    box-shadow 0.25s ease;
}

#globalHeader .publish-btn:hover {
  transform: translateY(-2px) scale(1.04);
  box-shadow: 0 8px 22px rgba(22, 119, 255, 0.5);
}

#globalHeader .publish-btn:active {
  transform: translateY(0) scale(0.98);
}

#globalHeader .publish-icon {
  font-size: 14px;
  transition: transform 0.25s ease;
}

#globalHeader .publish-btn:hover .publish-icon {
  transform: rotate(90deg);
}

/* 我的团队触发器：与导航菜单文字风格一致 */
#globalHeader .team-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 16px;
  height: 40px;
  cursor: pointer;
  color: var(--text-primary-light);
  font-size: 15px;
  white-space: nowrap;
  border-radius: 8px;
  transition: color 0.2s ease;
}

#globalHeader .team-trigger:hover {
  color: var(--accent);
}

.team-trigger-arrow {
  font-size: 10px;
  opacity: 0.55;
  transition: transform 0.25s ease;
}

.team-trigger-arrow.open {
  transform: rotate(180deg);
}

/* hover 时图标掉落弹跳动画（仿悦目菜单物品掉落效果） */
@keyframes icon-drop-bounce {
  0% {
    transform: translateY(-16px) rotate(-8deg);
    opacity: 0;
  }
  55% {
    transform: translateY(0) rotate(0deg);
    opacity: 1;
  }
  70% {
    transform: translateY(-5px);
  }
  85% {
    transform: translateY(0);
  }
  92% {
    transform: translateY(-2px);
  }
  100% {
    transform: translateY(0);
  }
}

#globalHeader .team-trigger:hover .team-icon {
  animation: icon-drop-bounce 0.55s cubic-bezier(0.3, 0.6, 0.4, 1);
}

#globalHeader .title-bar {
  display: flex;
  align-items: center;
}

.title {
  color: var(--text-primary-light);
  font-size: 17px;
  font-weight: 700;
  margin-left: 12px;
  padding-left: 12px;
  border-left: 1px solid var(--border-color);
  line-height: 26px;
  white-space: nowrap;
}

.logo {
  height: 42px;
}

#globalHeader .user-login-status {
  display: flex;
  justify-content: flex-end;
  align-items: center;
}

#globalHeader .user-info {
  cursor: pointer;
  color: var(--text-primary-light);
  padding: 5px 10px 5px 5px;
  border-radius: 999px;
  transition: background 0.2s ease;
}

/* 头像触发区悬停底色，与其他触发器可点击感一致 */
#globalHeader .user-info:hover {
  background: rgba(64, 169, 255, 0.08);
}

/* 窄屏：用户名隐藏只留头像（头像即识别符） */
@media (max-width: 768px) {
  #globalHeader .user-info {
    padding: 3px;
  }

  #globalHeader .user-info > span:last-child {
    display: none;
  }
}
</style>

<style>
/* 团队下拉面板：渲染在 body 下，需全局样式（浅色主题面板，随季节染色） */
.team-panel {
  min-width: 216px;
  padding: 8px;
  background: var(--bg-elevated);
  border-radius: 12px;
  border: 1px solid var(--border-color);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.1);
  backdrop-filter: blur(8px);
}

.team-panel .team-panel-item {
  padding: 8px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.team-panel .team-panel-item:hover {
  background: rgba(64, 169, 255, 0.1);
}

.team-panel .item-title {
  color: #212121;
  font-size: 14px;
  font-weight: 600;
  line-height: 22px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.team-panel .item-desc {
  color: rgba(107, 107, 107, 0.75);
  font-size: 12px;
  line-height: 18px;
  margin-top: 1px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.team-panel .team-panel-item:hover .item-desc {
  color: var(--accent);
}

.team-panel .team-panel-divider {
  height: 1px;
  margin: 6px 8px;
  background: var(--border-color);
}

/* 头像用户面板：顶部用户名区 + 图标菜单项 */
.user-panel {
  min-width: 200px;
}

/* ===== 消息通知面板 ===== */
.notify-panel {
  width: 340px;
}

.notify-panel .notify-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px 8px;
  border-bottom: 1px solid var(--border-color);
}

.notify-panel .notify-head-title {
  font-size: 14px;
  font-weight: 700;
  color: #212121;
}

.notify-panel .notify-readall {
  padding: 0;
  font-size: 12px;
}

.notify-panel .notify-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.notify-panel .notify-item:hover {
  background: rgba(64, 169, 255, 0.1);
}

.notify-panel .notify-item.unread {
  background: rgba(64, 169, 255, 0.05);
}

.notify-panel .notify-thumb {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  border-radius: 8px;
  object-fit: cover;
  background: #f0f2f5;
}

.notify-panel .notify-thumb--icon {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: rgba(107, 107, 107, 0.4);
}

.notify-panel .notify-body {
  flex: 1;
  min-width: 0;
}

.notify-panel .notify-item-title {
  font-size: 13.5px;
  font-weight: 600;
  color: #212121;
  line-height: 19px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notify-panel .notify-item.unread .notify-item-title {
  color: #1677ff;
}

.notify-panel .notify-item-desc {
  margin-top: 2px;
  font-size: 12px;
  color: rgba(107, 107, 107, 0.8);
  line-height: 17px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 面板内类型标签：待审核 / 系统 */
.notify-panel .notify-type-tag {
  display: inline-block;
  margin-left: 6px;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 600;
  line-height: 15px;
  vertical-align: 1px;
}

.notify-panel .notify-type-tag--audit {
  color: #fa8c16;
  background: rgba(250, 140, 22, 0.12);
}

.notify-panel .notify-type-tag--system {
  color: #722ed1;
  background: rgba(114, 46, 209, 0.1);
}

.notify-panel .notify-item-time {
  margin-top: 3px;
  font-size: 11px;
  color: rgba(107, 107, 107, 0.5);
}

.notify-panel .notify-dot {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  margin-top: 6px;
  border-radius: 50%;
  background: #ff4d4f;
}

.notify-panel .notify-empty {
  padding: 28px 0;
  text-align: center;
  font-size: 13px;
  color: rgba(107, 107, 107, 0.6);
}

.notify-panel .notify-foot {
  padding: 9px 0;
  margin-top: 4px;
  text-align: center;
  font-size: 13px;
  color: var(--accent);
  border-top: 1px solid var(--border-color);
  cursor: pointer;
}

.notify-panel .notify-foot:hover {
  opacity: 0.8;
}

/* 深色模式：通知面板 */
html.dark .notify-panel .notify-head {
  border-bottom-color: rgba(255, 255, 255, 0.1);
}

html.dark .notify-panel .notify-head-title {
  color: var(--text-primary);
}

html.dark .notify-panel .notify-item.unread {
  background: rgba(64, 169, 255, 0.1);
}

html.dark .notify-panel .notify-item-title {
  color: var(--text-primary);
}

html.dark .notify-panel .notify-item.unread .notify-item-title {
  color: #69b9ff;
}

html.dark .notify-panel .notify-item-desc {
  color: rgba(179, 179, 179, 0.8);
}

html.dark .notify-panel .notify-item-time {
  color: rgba(179, 179, 179, 0.5);
}

html.dark .notify-panel .notify-thumb {
  background: #3a3a3e;
}

html.dark .notify-panel .notify-thumb--icon {
  color: rgba(179, 179, 179, 0.4);
}

html.dark .notify-panel .notify-empty {
  color: rgba(179, 179, 179, 0.55);
}

.user-panel .user-panel-head {
  padding: 8px 12px 10px;
}

/* 用户名旁的会员小徽章 */
.vip-chip {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-left: 6px;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  line-height: 16px;
  vertical-align: 1px;
  color: #d48806;
  background: linear-gradient(135deg, #fffbe6, #fff1b8);
  border: 1px solid #ffe58f;
}

.vip-chip-icon {
  font-size: 10px;
}

html.dark .vip-chip {
  color: #ffd666;
  background: rgba(250, 173, 20, 0.14);
  border-color: rgba(250, 173, 20, 0.4);
}

.user-panel .user-panel-head .item-title {
  font-size: 15px;
}

.user-panel .user-panel-item {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #212121;
  font-size: 14px;
  line-height: 22px;
}

.user-panel .item-icon {
  font-size: 15px;
  color: rgba(107, 107, 107, 0.6);
  transition: color 0.15s ease;
}

.user-panel .team-panel-item:hover .item-icon {
  color: var(--accent);
}

/* 移动端抽屉菜单（Drawer 渲染在 body 下，需全局样式） */
.mobile-drawer .drawer-group {
  margin-bottom: 18px;
}

.mobile-drawer .drawer-group-title {
  font-size: 12px;
  color: rgba(107, 107, 107, 0.55);
  letter-spacing: 1px;
  margin-bottom: 6px;
}

.mobile-drawer .drawer-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  color: #212121;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.mobile-drawer .drawer-item:hover {
  background: rgba(64, 169, 255, 0.1);
}

.mobile-drawer .drawer-item--sub {
  padding-left: 28px;
  font-size: 13px;
  color: rgba(107, 107, 107, 0.8);
}

.mobile-drawer .drawer-icon {
  font-size: 15px;
  color: rgba(107, 107, 107, 0.6);
}

.mobile-drawer .drawer-item:hover .drawer-icon {
  color: var(--accent);
}

html.dark .mobile-drawer .drawer-group-title {
  color: rgba(179, 179, 179, 0.5);
}

html.dark .mobile-drawer .drawer-item {
  color: #f0f0f0;
}

html.dark .mobile-drawer .drawer-item:hover {
  background: rgba(64, 169, 255, 0.18);
}

html.dark .mobile-drawer .drawer-item--sub {
  color: rgba(240, 240, 240, 0.7);
}

html.dark .mobile-drawer .drawer-icon {
  color: rgba(179, 179, 179, 0.55);
}
</style>
