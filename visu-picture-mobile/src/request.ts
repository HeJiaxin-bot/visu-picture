import axios from 'axios'
import { showToast } from 'vant'

// 区分开发和生产环境
const DEV_BASE_URL = 'http://localhost:8123'
// APP 打包（Capacitor）时由 .env.app 注入公网 API 地址；
// 网页生产环境走同源（接口路径本身含 /api 前缀），由 Nginx 反向代理转发到后端
const PROD_BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined) || ''

// 未登录跳转处理器：由 main.ts 注入 router（避免 request -> router 循环依赖）
let authRedirect: ((redirect: string) => void) | null = null

export function setAuthRedirectHandler(fn: (redirect: string) => void) {
  authRedirect = fn
}

// 创建 Axios 实例
const myAxios = axios.create({
  baseURL: import.meta.env.DEV ? DEV_BASE_URL : PROD_BASE_URL,
  timeout: 300000,
  withCredentials: true,
})

// 全局请求拦截器
myAxios.interceptors.request.use(
  function (config) {
    return config
  },
  function (error) {
    return Promise.reject(error)
  },
)

// 全局响应拦截器
myAxios.interceptors.response.use(
  function (response) {
    const { data } = response
    // 未登录：跳转登录页并携带回跳地址
    if (data.code === 40100) {
      const url = window.location.href
      if (!url.includes('/user/login')) {
        showToast('请先登录')
        const redirect = encodeURIComponent(url)
        if (authRedirect) {
          authRedirect(redirect)
        } else {
          window.location.href = `/user/login?redirect=${redirect}`
        }
      }
    }
    return response
  },
  function (error) {
    return Promise.reject(error)
  },
)

export default myAxios
