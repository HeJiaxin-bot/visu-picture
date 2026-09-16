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
      <!-- 右侧：首页 + 我的团队 + 系统管理 + 发布 + 用户信息 -->
      <nav class="nav-wrap" aria-label="主导航">
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
                <a-avatar :src="loginUserStore.loginUser.userAvatar" />
                {{ loginUserStore.loginUser.userName ?? '视界用户' }}
              </a-space>
              <template #overlay>
                <div class="team-panel user-panel">
                  <!-- 顶部：用户名 + 身份 -->
                  <div class="user-panel-head">
                    <div class="item-title">{{ loginUserStore.loginUser.userAccount ?? '视界用户' }}</div>
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
                  <div class="team-panel-item user-panel-item" @click="router.push('/user/invite')">
                    <GiftOutlined class="item-icon" />
                    <span>邀请好友</span>
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
            <span>邀请好友</span>
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
import { ref, computed, watchEffect } from 'vue'
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
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { useRouter, useRoute } from 'vue-router'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { userLogoutUsingPost } from '@/api/userController.ts'
import { SPACE_TYPE_ENUM } from '@/constants/space.ts'
import { listMyTeamSpaceUsingPost } from '@/api/spaceUserController.ts'

const loginUserStore = useLoginUserStore()

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
  gap: 14px;
}

/* 一级导航链接：与下拉触发器风格一致 */
#globalHeader .nav-link {
  padding: 0 16px;
  height: 40px;
  display: flex;
  align-items: center;
  color: #26283a;
  font-size: 15px;
  white-space: nowrap;
  border-radius: 8px;
  transition: color 0.2s ease;
}

#globalHeader .nav-link:hover,
#globalHeader .nav-link.router-link-exact-active {
  color: #1890ff;
}

/* 移动端汉堡按钮：桌面隐藏 */
#globalHeader .mobile-menu-btn {
  display: none;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  font-size: 18px;
  color: #26283a;
  background: transparent;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s ease;
}

#globalHeader .mobile-menu-btn:hover {
  background: rgba(61, 90, 245, 0.08);
  color: #3d5af5;
}

/* 中屏：隐藏下拉类入口与积分徽章，仅保留首页/发布/用户 + 汉堡 */
@media (max-width: 992px) {
  #globalHeader .team-trigger,
  #globalHeader .points-badge {
    display: none;
  }

  #globalHeader .mobile-menu-btn {
    display: flex;
  }
}

/* 小屏：发布按钮只留图标，首页链接隐藏 */
@media (max-width: 640px) {
  #globalHeader .nav-link {
    display: none;
  }

  #globalHeader .publish-btn {
    padding: 0 12px;
  }

  #globalHeader .publish-btn span {
    display: none;
  }
}

/* 剩余积分徽章：药丸样式跟随主题（浅色：白底蓝光；深色：黑底紫光） */
#globalHeader .points-badge {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  color: #3d5af5;
  font-size: 14px;
  font-weight: 600;
  background: #ffffff;
  border: 1.5px solid rgba(61, 90, 245, 0.4);
  box-shadow: 0 2px 10px rgba(61, 90, 245, 0.18);
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
  border-color: #3d5af5;
  box-shadow: 0 4px 16px rgba(61, 90, 245, 0.35);
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

/* 深色主题：黑底 + 紫色光晕描边 */
html.dark #globalHeader .points-badge {
  color: #e8eaf6;
  background: rgba(16, 18, 32, 0.92);
  border-color: rgba(124, 150, 255, 0.85);
  box-shadow: 0 0 10px rgba(124, 150, 255, 0.35);
}

html.dark #globalHeader .points-badge:hover {
  border-color: #7c96ff;
  box-shadow: 0 0 16px rgba(124, 150, 255, 0.6);
}

/* 发布按钮（原"创建图片"入口）：蓝色药丸 + 悬停动效 */
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
  background: linear-gradient(135deg, #4f6bff, #3d5af5);
  box-shadow: 0 4px 14px rgba(61, 90, 245, 0.35);
  cursor: pointer;
  white-space: nowrap;
  user-select: none;
  transition:
    transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1),
    box-shadow 0.25s ease;
}

#globalHeader .publish-btn:hover {
  transform: translateY(-2px) scale(1.04);
  box-shadow: 0 8px 22px rgba(61, 90, 245, 0.5);
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
  color: #26283a;
  font-size: 15px;
  white-space: nowrap;
  border-radius: 8px;
  transition: color 0.2s ease;
}

#globalHeader .team-trigger:hover {
  color: #1890ff;
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
  color: #171a2b;
  font-size: 17px;
  font-weight: 700;
  margin-left: 12px;
  padding-left: 12px;
  border-left: 1px solid #e4e8f2;
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
  color: #26283a;
}
</style>

<style>
/* 团队下拉面板：渲染在 body 下，需全局样式（浅色主题面板） */
.team-panel {
  min-width: 216px;
  padding: 8px;
  background: rgba(255, 255, 255, 0.98);
  border-radius: 12px;
  border: 1px solid #e4e8f2;
  box-shadow: 0 12px 32px rgba(37, 55, 120, 0.12);
  backdrop-filter: blur(8px);
}

.team-panel .team-panel-item {
  padding: 8px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.team-panel .team-panel-item:hover {
  background: #eef2ff;
}

.team-panel .item-title {
  color: #26283a;
  font-size: 14px;
  font-weight: 600;
  line-height: 22px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.team-panel .item-desc {
  color: rgba(35, 44, 86, 0.55);
  font-size: 12px;
  line-height: 18px;
  margin-top: 1px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.team-panel .team-panel-item:hover .item-desc {
  color: #4f6bff;
}

.team-panel .team-panel-divider {
  height: 1px;
  margin: 6px 8px;
  background: #e4e8f2;
}

/* 头像用户面板：顶部用户名区 + 图标菜单项 */
.user-panel {
  min-width: 200px;
}

.user-panel .user-panel-head {
  padding: 8px 12px 10px;
}

.user-panel .user-panel-head .item-title {
  font-size: 15px;
}

.user-panel .user-panel-item {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #26283a;
  font-size: 14px;
  line-height: 22px;
}

.user-panel .item-icon {
  font-size: 15px;
  color: rgba(35, 44, 86, 0.6);
  transition: color 0.15s ease;
}

.user-panel .team-panel-item:hover .item-icon {
  color: #4f6bff;
}

/* 移动端抽屉菜单（Drawer 渲染在 body 下，需全局样式） */
.mobile-drawer .drawer-group {
  margin-bottom: 18px;
}

.mobile-drawer .drawer-group-title {
  font-size: 12px;
  color: rgba(35, 44, 86, 0.45);
  letter-spacing: 1px;
  margin-bottom: 6px;
}

.mobile-drawer .drawer-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  color: #26283a;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.mobile-drawer .drawer-item:hover {
  background: #eef2ff;
}

.mobile-drawer .drawer-item--sub {
  padding-left: 28px;
  font-size: 13px;
  color: rgba(35, 44, 86, 0.75);
}

.mobile-drawer .drawer-icon {
  font-size: 15px;
  color: rgba(35, 44, 86, 0.55);
}

.mobile-drawer .drawer-item:hover .drawer-icon {
  color: #3d5af5;
}

html.dark .mobile-drawer .drawer-group-title {
  color: rgba(232, 234, 242, 0.45);
}

html.dark .mobile-drawer .drawer-item {
  color: #e8eaf2;
}

html.dark .mobile-drawer .drawer-item:hover {
  background: rgba(79, 107, 255, 0.2);
}

html.dark .mobile-drawer .drawer-item--sub {
  color: rgba(232, 234, 242, 0.75);
}

html.dark .mobile-drawer .drawer-icon {
  color: rgba(232, 234, 242, 0.55);
}
</style>
