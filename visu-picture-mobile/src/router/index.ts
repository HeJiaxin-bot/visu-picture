import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  // ---- 底部 Tab 主页面（共用 TabLayout） ----
  {
    path: '/',
    component: () => import('@/layouts/TabLayout.vue'),
    children: [
      { path: '', name: 'home', component: () => import('@/pages/HomePage.vue'), meta: { title: '视界云图库' } },
      { path: 'search', name: 'search', component: () => import('@/pages/SearchPage.vue'), meta: { title: '搜索' } },
      { path: 'space', name: 'space', component: () => import('@/pages/space/MySpacePage.vue'), meta: { title: '我的空间' } },
      { path: 'mine', name: 'mine', component: () => import('@/pages/mine/MinePage.vue'), meta: { title: '我的' } },
    ],
  },
  // ---- 图片 ----
  {
    path: '/picture/:id',
    name: 'pictureDetail',
    component: () => import('@/pages/PictureDetailPage.vue'),
    meta: { title: '图片详情' },
  },
  // ---- 发布 ----
  {
    path: '/publish',
    name: 'publish',
    component: () => import('@/pages/publish/PublishPage.vue'),
    meta: { title: '发布图片' },
  },
  {
    path: '/add_picture',
    name: 'addPicture',
    component: () => import('@/pages/publish/AddPicturePage.vue'),
    meta: { title: '上传图片' },
  },
  {
    path: '/add_picture/url',
    name: 'addPictureUrl',
    component: () => import('@/pages/publish/AddPictureUrlPage.vue'),
    meta: { title: 'URL 上传' },
  },
  {
    path: '/add_picture/batch',
    name: 'addPictureBatch',
    component: () => import('@/pages/publish/AddPictureBatchPage.vue'),
    meta: { title: '批量上传' },
  },
  // ---- 空间 ----
  {
    path: '/space/:id',
    name: 'spaceDetail',
    component: () => import('@/pages/space/SpaceDetailPage.vue'),
    meta: { title: '空间详情' },
  },
  {
    path: '/space/add',
    name: 'spaceAdd',
    component: () => import('@/pages/space/AddSpacePage.vue'),
    meta: { title: '创建空间' },
  },
  // ---- 用户 ----
  {
    path: '/user/login',
    name: 'userLogin',
    component: () => import('@/pages/user/UserLoginPage.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/user/register',
    name: 'userRegister',
    component: () => import('@/pages/user/UserRegisterPage.vue'),
    meta: { title: '注册' },
  },
  {
    path: '/user/profile',
    name: 'userProfile',
    component: () => import('@/pages/user/UserProfilePage.vue'),
    meta: { title: '个人资料' },
  },
  {
    path: '/user/settings',
    name: 'userSettings',
    component: () => import('@/pages/user/UserSettingsPage.vue'),
    meta: { title: '设置' },
  },
  {
    path: '/user/:id',
    name: 'userHome',
    component: () => import('@/pages/user/UserHomePage.vue'),
    meta: { title: '用户主页' },
  },
  // ---- 我的发布 ----
  {
    path: '/my/pictures',
    name: 'myPictures',
    component: () => import('@/pages/mine/MyPicturesPage.vue'),
    meta: { title: '我的发布' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由后置守卫：设置页面标题
router.afterEach((to) => {
  const title = to.meta?.title as string | undefined
  document.title = title ? `${title} - 视界云图库` : '视界云图库'
})

export default router
