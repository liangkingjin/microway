import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import './styles/main.css'
import './styles/header.css'

const app = createApp(App)

// 全局注册 Element Plus 图标组件：模板中可直接 <el-icon><MapLocation /></el-icon> 使用
// （原 Element UI 的 <i class="el-icon-xxx"> 写法按迁移规范替换为图标组件）
for (const [name, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
