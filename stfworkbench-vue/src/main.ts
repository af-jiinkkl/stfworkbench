import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'

import App from '@/App.vue'
import router from '@/router'
// 全站样式。**必须**排在 element-plus 的 CSS 之后 ——
// 里面靠同权重选择器的先后顺序覆盖 EP 的 --el-* 变量，提前引入就失效了。
import '@/styles/index.css'

const app = createApp(App)

// Pinia 必须在 router 之前注册：路由守卫里会用到 store
app.use(createPinia())
app.use(router)

// 全量引入 Element Plus 并指定中文语言包
// （分页、日期选择器等组件的内置文案默认是英文）
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
