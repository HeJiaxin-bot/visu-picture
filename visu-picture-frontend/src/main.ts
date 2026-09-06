import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

// ant-design-vue 已按需引入（见 vite.config.ts），此处仅引入重置样式
import 'ant-design-vue/dist/reset.css'
import '@/assets/main.css'
import '@/access.ts'

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')