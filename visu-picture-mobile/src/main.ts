import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import request, { setAuthRedirectHandler } from './request'
import { useLoginUserStore } from './stores/useLoginUserStore'
import './styles/main.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')

// 未登录时由 axios 拦截器触发路由跳转（避免 request -> router 循环依赖，此处注入）
setAuthRedirectHandler((redirect) => {
  router.push(`/user/login?redirect=${redirect}`)
})

// 全局挂载请求实例，便于调试
// eslint-disable-next-line @typescript-eslint/no-explicit-any
;(window as any).axios = request

// 初始化登录用户信息
const loginUserStore = useLoginUserStore()
loginUserStore.fetchLoginUser()
